/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 * 
 * http://www.dspace.org/license/
 */

package org.dspace.app.cris.rdf.conversion;
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

import it.cilea.osd.jdyna.model.Property;
import it.cilea.osd.jdyna.value.PointerValue;

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
import org.dspace.app.cris.rdf.RDFUtil;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.app.cris.rdf.conversion.ConverterPlugin;
import org.dspace.app.cris.rdf.conversion.DMRM;
import org.dspace.app.cris.rdf.conversion.MetadataRDFMapping;
import org.dspace.app.cris.service.ApplicationService;
import org.dspace.services.ConfigurationService;
import org.dspace.app.cris.model.ACrisObject;
import org.dspace.app.cris.model.CrisConstants;
import org.dspace.app.cris.model.ResearcherPage;
import org.dspace.app.cris.model.ResearchObject;
import org.dspace.app.cris.model.Project;
import org.dspace.app.cris.model.RelationPreference;
import org.dspace.app.cris.model.OrganizationUnit;

/**
 * This class converts CRIS Properties to RDF-Tripels.
 * 
 * The procedure is similar to the MetadataConverterPlugin, 
 * which has some weaks handling Cris-Properties as Dspace-Metadata, 
 * e.g. visibility of Properties is not being considered,
 * and there is no *ANY. Selector so every property has to be specified in code.   
 * The Converter works only on ACrisObjects and uses an own mapping file.  
 * 
 * @see MetadataConverterPlugin
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
public class CrisPropertyConverterPlugin implements ConverterPlugin
{
    public final static String METADATA_MAPPING_PATH_KEY = "rdf.property.mappings";
    public final static String METADATA_SCHEMA_URL_KEY = "rdf.property.schema";
    public final static String METADATA_PREFIXES_KEY = "rdf.property.prefixes";
    
    private final static Logger log = Logger.getLogger(CrisPropertyConverterPlugin.class);
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
            log.error("Cannot load CrisPropertyConverterPlugin configuration, "
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
            log.warn("No CrisProperty mappings found, returning null.");
            return null;
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
        log.debug("Collecting Properties for Cris-Object " + dso.getHandle());
        
        /*
         * Step: convert dso to specific entity type and perform type-dependent actions.
         * f.e. restrictions on relations, check condition on entities
         * */
       ApplicationService applicationService = new DSpace().getServiceManager().getServiceByName(
                "applicationService", ApplicationService.class);
		applicationService.init();
       		ACrisObject aco = (ACrisObject) dso;
	    
        if(aco instanceof ResearcherPage) { 	
        	ResearcherPage item = (ResearcherPage) aco;	
        	
        	try {
        	//Check if at least one Publication is published by this author
        	//Because of missing or not found methods in applicationService for this case, we deciced to use an solr query
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
        	
        }else if(aco instanceof Project) {
        	Project item = (Project) aco;
        }else if(aco instanceof OrganizationUnit) {
		OrganizationUnit item = (OrganizationUnit) aco;

		}else if(aco instanceof ResearchObject) {
		ResearchObject item = (ResearchObject) dso;
		
		if(item.getAuthorityPrefix().contentEquals("events")) {
		
		}
        }
/*
 * Step: add global informations to every cris-object
 * The following blocks are specific informations added from the ACrisObject, 
 * which are not being hold in the properties. These infos could also be moved to an seperate converter with static informations for every cris-object?
 * */
  	  {
   		 // for every cris-entity: add url to jspui representation of Resource
         	Map<String,String> value_map = new HashMap<String,String>();
         	String fieldname = "cris" + aco.getAuthorityPrefix() + ".cris-url";
            String instance_url = configurationService.getProperty("dspace.url") + "/cris/" + aco.getPublicPath() + "/" + aco.getCrisID() + "/";
         	value_map.put("DSpaceAuthority", instance_url);
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
    		 // for every cris-entity: add cris-id as internal-identifier
          	Map<String,String> value_map = new HashMap<String,String>();
          	String fieldname = "cris" + aco.getAuthorityPrefix() + ".cris-id";
          	value_map.put("DSpaceValue", aco.getCrisID());
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
    		 // for every cris-entity: add creation-timestamp in ISO 8601 Formats =LiteralType (=xsd:datetime)
          	Map<String,String> value_map = new HashMap<String,String>();
          	String fieldname = "cris.creationdate";
          	String date = OffsetDateTime.now(ZoneId.of("Europe/Berlin")).toString();
          	value_map.put("DSpaceValue", date);
 			  Iterator<MetadataRDFMapping> iter = mappings.iterator();
              while (iter.hasNext()){
            	  MetadataRDFMapping mapping = iter.next();
                      if (mapping.matchesName(fieldname)){
                      //no language information in properties found, thus keeping empty
                      	mapping.convert(value_map, "", uri, convertedData);

                      }
              }	 
    	  }
        
  	  /*
       * Step: Fetch Properties and Loop through them to compare, if mapping matches
       * */
  	  Map<String, List<Property>> acoProp = aco.getAnagrafica4view();
        Iterator it = acoProp.entrySet().iterator();
        String crisprefix = aco.getAuthorityPrefix();
        while(it.hasNext()) {
        	 Map.Entry mapentry = (Map.Entry)it.next();
             //mapentry: Key = shortname || Value = List<Property>
         
             //Loop through Properties
        for(Property entry : (List<Property>) mapentry.getValue()) {
        	{
        		
                String fieldname = "cris" + crisprefix + "." + mapentry.getKey();
              	String value_string = entry.getValue().toString();
              	String authorityURIvalue = null;
                //language or additional information in dspace-cris seems to be seldom used
              	String lang = "";
              	if(entry.getScopeDef() != null) {
              		lang = entry.getScopeDef().getLabel();
              	}
              	
                  if (entry.getVisibility() == 0)
                  {
                      log.debug(fieldname + " is a hidden property, won't convert it.");
                      continue;
                  }
                  
                  boolean converted = false;
                  	Map<String,String> value_map = new HashMap<String,String>();
                  	//Set Values
                  	value_map.put("DSpaceValue", value_string);
                  	
                  	if(entry.getValue() instanceof PointerValue) {
              		  //set second Argument for Pointer
              		  PointerValue pv = (PointerValue) entry.getValue();
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
                              	mapping.convert(value_map, lang, uri, convertedData);
                              	converted = true;
                              }
                      }
               
                  if (!converted){
                      log.debug("Did not convert " + fieldname + ". Found no corresponding mapping.");
                      /*System.out.println("Did not convert " + fieldname + " with value "+ value_map.get("DSpaceValue") +" and " + value_map.get("DSpaceAuthority") + ". Found no "
                             + "corresponding mapping.");*/
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

            // If we do inferencing, we can easily check for consistency.
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
