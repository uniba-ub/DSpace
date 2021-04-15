/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 * 
 * http://www.dspace.org/license/
 */

package org.dspace.app.cris.rdf.conversion;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.dspace.app.cris.model.ACrisObject;
import org.dspace.app.cris.model.CrisConstants;
import org.dspace.app.cris.model.OrganizationUnit;
import org.dspace.app.cris.model.Project;
import org.dspace.app.cris.model.ResearchObject;
import org.dspace.app.cris.model.ResearcherPage;
import org.dspace.app.cris.model.jdyna.ACrisNestedObject;
import org.dspace.app.cris.model.jdyna.DynamicNestedObject;
import org.dspace.app.cris.model.jdyna.DynamicNestedProperty;
import org.dspace.app.cris.rdf.RDFUtil;
import org.dspace.app.cris.service.ApplicationService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.discovery.SearchService;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;

import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.reasoner.ReasonerRegistry;
import com.hp.hpl.jena.reasoner.ValidityReport;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.util.FileUtils;
import com.hp.hpl.jena.vocabulary.RDF;

import it.cilea.osd.jdyna.model.ANestedProperty;
import it.cilea.osd.jdyna.model.ATypeNestedObject;
import it.cilea.osd.jdyna.model.Property;
import it.cilea.osd.jdyna.value.PointerValue;

/**
 * This class converts CRIS Nested Properties to RDF-Tripels.
 * 
 * The procedure is similar to the MetadataConverterPlugin.
 * Nested Properties are considered different than Properties.
 * An Node with hastag is generated for every nested object. 
 * This Node can be only resolved by the belonging entity and not for it alone.
 * The Converter works only on ACrisObjects and uses an own mapping file.
 *   
 * @see MetadataConverterPlugin
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
public class CrisNPropertyConverterPlugin implements ConverterPlugin
{
    public final static String METADATA_MAPPING_PATH_KEY = "rdf.nproperty.mappings";
    public final static String METADATA_SCHEMA_URL_KEY = "rdf.nproperty.schema";
    public final static String METADATA_PREFIXES_KEY = "rdf.nproperty.prefixes";
    public final static String METADATA_IDENTIFIER_KEY = "rdf.nproperty.identifier";
    
    private final static Logger log = Logger.getLogger(CrisNPropertyConverterPlugin.class);
    protected ConfigurationService configurationService;
    
    @Override
    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
 
    @Override
    public Model convert(Context context, DSpaceObject dso)
            throws SQLException, AuthorizeException {
        String uri = RDFUtil.generateIdentifier(context, dso);
        if (uri == null)
        {
            log.error("Cannot create URI for " + dso.getTypeText() + " " 
                    + dso.getID() + " stopping conversion.");
            return null;
        }

        Model convertedData = ModelFactory.createDefaultModel();
        String prefixesPath = configurationService.getProperty(METADATA_PREFIXES_KEY);
        if (!StringUtils.isEmpty(prefixesPath))
        {	
            InputStream is = FileManager.get().open(prefixesPath);
            if (is == null)
            {
                log.warn("Cannot find file '" + prefixesPath + "', ignoring...");
            } else {
                convertedData.read(is, null, FileUtils.guessLang(prefixesPath));
                try {
                    is.close();
                }
                catch (IOException ex)
                {
                    // nothing to do here.
                }
            }
        }
        
        Model config = loadConfiguration();
        if (config == null)
        {
            log.error("Cannot load CrisNPropertyConverterPlugin configuration, "
                    + "skipping this plugin.");
            return null;
        }
        /*
        if (log.isDebugEnabled())
        {
            StringWriter sw = new StringWriter();
            sw.append("Inferenced the following model:\n");
            config.write(sw, "TURTLE");
            sw.append("\n");
            log.debug(sw.toString());
            try {
                sw.close();
            } catch (IOException ex) {
                // nothing to do here
            }
        }
        */

        ResIterator mappingIter = 
                config.listSubjectsWithProperty(RDF.type, DMRM.DSpaceMetadataRDFMapping);
        if (!mappingIter.hasNext())
        {
            log.warn("No NestedProperties mappings found, returning null.");
            return null;
        }
        
        //fetch identifiertype for nested-objects
        String nprop_identifierkey = configurationService.getProperty(METADATA_IDENTIFIER_KEY);
		
		if(nprop_identifierkey == null) {
			nprop_identifierkey = "id";
		}
        
        List<MetadataRDFMapping> mappings = new ArrayList<>();
        while (mappingIter.hasNext())
        {
            MetadataRDFMapping mapping = MetadataRDFMapping.getMetadataRDFMapping(
                    mappingIter.nextResource(), uri);
            if (mapping != null) mappings.add(mapping);
        }
        
        if (!((dso instanceof ResearcherPage) 
        		|| (dso instanceof Project) 
        		|| (dso instanceof OrganizationUnit) 
        		|| (dso instanceof ResearchObject))) //RO for dynamic types
        {
            log.error("This DspaceObject (" + dso.getTypeText() + " " 
                    + dso.getID() + ") should not have bin submitted to this "
                    + "plugin, as it supports CRIS-Entities only!");
            return null;
        }
        log.debug("Collecting Nested Proprties for Cris-Object " + dso.getHandle());
        /*
         * Step: Fetch nested Properties for the Cris-Object
         */
       ApplicationService applicationService = new DSpace().getServiceManager().getServiceByName(
                "applicationService", ApplicationService.class);
		applicationService.init();
       	ACrisObject aco = (ACrisObject) dso;

       	//the Map of Nestedproperties being converted. <shortname, Properties> 
        Map<String, List<Property>> acoProp = new HashMap<String, List<Property>>();    
    	/*
         * Special Operations on Entity Type before fetching properties, e.g. check condition on entities
         * */
        if(aco instanceof ResearcherPage) { 	
        	ResearcherPage item = (ResearcherPage) aco;	        	

        	try {
        	//Uniba Requirement: Check if at least one Publication is published by this author
        	//We decided to use some solr query
        	SearchService searchService = new DSpace().getSingletonService(SearchService.class);
 	        SolrQuery query = new SolrQuery("{!field f=search.resourcetype}" + Constants.ITEM);
 	        query.addFilterQuery("author_authority:"+item.getCrisID()	);
 	        query.setRows(1);
	        QueryResponse response = searchService.search(query);
	        SolrDocumentList resultList = response.getResults();
	        if(resultList.size() == 0){
	           	   convertedData.close();
	           	   log.debug(item.getCrisID() + " has no publications. Skipping this entry.");
	                return null;
	        }
        	}catch(Exception e) {
        		log.debug(e);
        	
        	}
        }/*else if(aco instanceof Project) {
        	Project item = (Project) aco;
        	
     	}else if(aco instanceof OrganizationUnit) {
        	OrganizationUnit item = (OrganizationUnit) aco;

    	}else if(aco instanceof ResearchObject) {
	    	ResearchObject item = (ResearchObject) dso;
			if(item.getAuthorityPrefix().contentEquals("events")) {
			
			}
		}
                */
        
        List<? extends ATypeNestedObject> ntypes2 = applicationService.getList(aco.getClassTypeNested());
    	for(ATypeNestedObject atne : ntypes2) {
    		acoProp.put(atne.getShortName(), applicationService.getNestedObjectsByParentIDAndShortname(aco.getId(), atne.getShortName(), aco.getClassNested()));
    	}
   	   
        /*
         * Step: Loop through previously fetched NestedEntities and compare, if mapping matches
         * */
        Iterator it = acoProp.entrySet().iterator();
        String crisprefix = aco.getAuthorityPrefix();
        while(it.hasNext()) {
        	 Map.Entry mapentry = (Map.Entry)it.next();
             //mapentry: Key = shortname || Value = List<Property>  
        	 List<ACrisNestedObject> mapvalue =  (List<ACrisNestedObject>) mapentry.getValue();
        for(ACrisNestedObject entry :  mapvalue) {
        	{
        		//Use internal id for nested entities. This could also be the uuid
        		String nprop_identifiervalue = "";
        		if(nprop_identifierkey.contentEquals("id")) {
        			nprop_identifiervalue = entry.getIdentifyingValue().toString();
        		}else if(nprop_identifierkey.contentEquals("uuid")) {
        			nprop_identifiervalue = entry.getUuid();
        		}
        		
          	  String neuri = uri + "/" + mapentry.getKey() + "#" + nprop_identifiervalue ;
          	  
        		if (!entry.getStatus())
                {
                    log.debug(mapentry.getKey() + " is a hidden property, won't convert it.");
                    continue;
                }
            	  {
               		 //create link between uri-resource of main entity and later generated nested entities
                     	Map<String,String> value_map = new HashMap<String,String>();
                     	String fieldname = "cris" + crisprefix + ".link." + mapentry.getKey().toString();
                     	value_map.put("DSpaceAuthority", neuri);
                     	value_map.put("DSpaceValue", "");
       				  Iterator<MetadataRDFMapping> iter = mappings.iterator();
                         while (iter.hasNext()){
                       	  MetadataRDFMapping mapping = iter.next();
                                 if (mapping.matchesName(fieldname)){
                                 //no language information in properties found, thus keeping empty
                                 	mapping.convert(value_map, "", uri, convertedData);
                                 }
                         }	 
               	  }
               	{
            		 // for every nested-entity: add creation-timestamp
                  	Map<String,String> value_map = new HashMap<String,String>();
                  	String fieldname = "cris.creationdate";
                    String date = OffsetDateTime.now(ZoneId.of("Europe/Berlin")).toString();
                  	value_map.put("DSpaceValue", date);
         			  Iterator<MetadataRDFMapping> iter = mappings.iterator();
                      while (iter.hasNext()){
                    	  MetadataRDFMapping mapping = iter.next();
                              if (mapping.matchesName(fieldname)){
                              //no language information in properties found, thus keeping empty
                              	mapping.convert(value_map, "", neuri, convertedData);

                              }
                      	}	 
               		}
               	{
            		 // for every nested-entity: add type of entity (if available)
               		//this works similiar to the staticsdsoconverterplugin, value is not processed
                  	Map<String,String> value_map = new HashMap<String,String>();
                  	String fieldname = "cris" + crisprefix + "." + mapentry.getKey();
                  	value_map.put("DSpaceValue", "type");
         			  Iterator<MetadataRDFMapping> iter = mappings.iterator();
                      while (iter.hasNext()){
                    	  MetadataRDFMapping mapping = iter.next();
                              if (mapping.matchesName(fieldname)){
                              //no language information in properties found, thus keeping empty
                              	mapping.convert(value_map, "", neuri, convertedData);

                              }
                      	}	 
               		} 
               	
        		//Loop through all Properties of the Nested Object
               	try {
               		Map<String, List<ANestedProperty>> submap = entry.getAnagrafica4view();
               		if(submap == null) continue;
               		
                Iterator it_sub = submap.entrySet().iterator();
                while(it_sub.hasNext()) {
        		//for(Property subentry : (List<Property>) ) {
               	 Map.Entry submapentry = (Map.Entry)it_sub.next();
            	 for(Property subentry : (List<ANestedProperty>) submapentry.getValue()) {
                String fieldname = "cris" + crisprefix + "." + mapentry.getKey() + "." + subentry.getTypo().getShortName();
              	String value_string = subentry.toString();
              	String authorityURIvalue = null;
              	String lang = "";
              	//language or additional informations via scopedef
              	if(entry.getScopeDef() != null) {
              		lang = entry.getScopeDef().getLabel();
              	}
              	
                  if (subentry.getVisibility() == 0)
                  {
                      log.debug(fieldname + " is a hidden property, won't convert it.");
                      continue;
                  }
                  boolean converted = false;
                  	Map<String,String> value_map = new HashMap<String,String>();
                  	//Set Values
                  	value_map.put("DSpaceValue", value_string);
                  	
                  	if(subentry.getValue() instanceof PointerValue) {
              		  //set second Argument for Pointer
              		  PointerValue pv = (PointerValue) subentry.getValue();
              		  ACrisObject aco_ref= (ACrisObject) pv.getObject();
              		  if(aco_ref != null) {
              			  authorityURIvalue = RDFUtil.generateIdentifier(context, aco_ref);
              			  if(authorityURIvalue != null && !authorityURIvalue.contentEquals("")) {
              				  value_map.put("DSpaceAuthority", authorityURIvalue);
              			  }
              		  }
              	  }
                  	
        			//Loop through Mapping Rules
                      Iterator<MetadataRDFMapping> iter = mappings.iterator();
                      while (iter.hasNext()){
                    	  MetadataRDFMapping mapping = iter.next();
                              if (mapping.matchesName(fieldname) && (mapping.fulfills(value_string) && mapping.fulfillsAuth(authorityURIvalue))){
                              	mapping.convert(value_map, lang, neuri, convertedData);
                              	converted = true;
                              }
                      }
            	 
                  if (!converted){
                      log.debug("Did not convert " + fieldname + ". Found no corresponding mapping.");
                      /*System.out.println("Did not convert " + fieldname + " with value "+ value_map.get("DSpaceValue") +" and " + value_map.get("DSpaceAuthority") + ". Found no "
                             + "corresponding mapping.");*/
                  }
            	 }
              }
               	}catch(Exception e) {
                    log.debug("Error fetching or  looping through nested entities. Check Config. Skipping Properties");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
           	 }
              
                
        	}
        	
    		
             
        //it.remove(); // avoids a ConcurrentModificationException
        }
        }

        config.close();
        if (convertedData.isEmpty())
        {
            convertedData.close();
            return null;
        }
        return convertedData;
    }

    @Override
    public boolean supports(int type) {
        // should be changed, if Communities and Collections have metadata as well.
        return (type == CrisConstants.RP_TYPE_ID || type == CrisConstants.PROJECT_TYPE_ID || type == CrisConstants.OU_TYPE_ID || type >= CrisConstants.CRIS_DYNAMIC_TYPE_ID_START  ); //support for all dynamic objects
    }
    
    protected Model loadConfiguration()
    {
        String mappingPathes = configurationService.getProperty(METADATA_MAPPING_PATH_KEY);
        if (StringUtils.isEmpty(mappingPathes))
        {
            return null;
        }
        String[] mappings = mappingPathes.split(",\\s*");        
        if (mappings == null || mappings.length == 0)
        {
            log.error("Cannot find metadata mappings (looking for "
                    + "property " + METADATA_MAPPING_PATH_KEY + ")!");
            return null;
        }
        
        InputStream is = null;
        Model config = ModelFactory.createDefaultModel();
        for (String mappingPath : mappings)
        {
            is = FileManager.get().open(mappingPath);
            if (is == null)
            {
                log.warn("Cannot find file '" + mappingPath + "', ignoring...");
            }
            config.read(is, "file://" + mappingPath, FileUtils.guessLang(mappingPath));
            try {
                is.close();
            }
            catch (IOException ex)
            {
                // nothing to do here.
            }
        }
        if (config.isEmpty())
        {
            config.close();
            log.warn("Metadata RDF Mapping did not contain any triples!");
            return null;
        }
        
        String schemaURL = configurationService.getProperty(METADATA_SCHEMA_URL_KEY);
        if (schemaURL == null)
        {
            log.error("Cannot find metadata rdf mapping schema (looking for "
                    + "property " + METADATA_SCHEMA_URL_KEY + ")!");
        }
        if (!StringUtils.isEmpty(schemaURL))
        {
            log.debug("Going to inference over the rdf metadata mapping.");
            // Inferencing over the configuration data let us detect some rdf:type
            // properties out of rdfs:domain and rdfs:range properties
            // A simple rdfs reasoner is enough for this task.
            Model schema = ModelFactory.createDefaultModel();
            schema.read(schemaURL);
            Reasoner reasoner = ReasonerRegistry.getRDFSSimpleReasoner().bindSchema(schema);
            InfModel inf = ModelFactory.createInfModel(reasoner, config);

            // If w)e do inferencing, we can easily check for consistency.
            ValidityReport reports = inf.validate();
            if (!reports.isValid())
            {
                StringBuilder sb = new StringBuilder();
                sb.append("The configuration of the MetadataConverterPlugin is ");
                sb.append("not valid regarding the schema (");
                sb.append(DMRM.getURI());
                sb.append(").\nThe following problems were encountered:\n");
                for (Iterator<ValidityReport.Report> iter = reports.getReports();
                        iter.hasNext() ; )
                {
                    ValidityReport.Report report = iter.next();
                    if (report.isError)
                    {
                        sb.append(" - " + iter.next() + "\n");
                    }
                }
                log.error(sb.toString());
                return null;
            }
            return inf;
        }
        return config;
    }
    
}
