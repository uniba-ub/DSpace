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
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.cris.rdf.RDFUtil;
import org.dspace.app.util.MetadataExposure;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Metadatum;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.app.cris.rdf.conversion.ConverterPlugin;
import org.dspace.app.cris.rdf.conversion.DMRM;
import org.dspace.app.cris.rdf.conversion.MetadataConverterPlugin;
import org.dspace.app.cris.rdf.conversion.MetadataRDFMapping;
import org.dspace.app.cris.service.ApplicationService;
import org.dspace.services.ConfigurationService;
import org.dspace.app.cris.model.CrisConstants;
import org.dspace.app.cris.model.ResearcherPage;
import org.dspace.app.cris.model.ResearchObject;
import org.dspace.app.cris.model.Project;
import org.dspace.app.cris.model.OrganizationUnit;

/**
 * 
 * @author Pascal-Nicolas Becker (dspace -at- pascal -hyphen- becker -dot- de)
 */
/**
 * Note to CRIS-Entities: Dspace Items might point to Cris-Objects as authority values,
 * therefore we include the applicationService for identification etc...
 * For Transformation of CRIS-Entities we recommend the use of the other Converter Plugins.
 * @commentor: Florian Gantner (florian.gantner@uni-bamberg.de)
 * */
public class MetadataConverterPlugin implements ConverterPlugin
{
    public final static String METADATA_MAPPING_PATH_KEY = "rdf.metadata.mappings";
    public final static String METADATA_SCHEMA_URL_KEY = "rdf.metadata.schema";
    public final static String METADATA_PREFIXES_KEY = "rdf.metadata.prefixes";
    
    private final static Logger log = Logger.getLogger(MetadataConverterPlugin.class);
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
            log.error("Cannot load MetadataConverterPlugin configuration, "
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
            log.warn("No metadata mappings found, returning null.");
            return null;
        }
        
        List<MetadataRDFMapping> mappings = new ArrayList<>();
        while (mappingIter.hasNext())
        {
            MetadataRDFMapping mapping = MetadataRDFMapping.getMetadataRDFMapping(
                    mappingIter.nextResource(), uri);
            if (mapping != null) mappings.add(mapping);
        }
        
        // should be changed, if Communities and Collections have metadata as well.
        if (!((dso instanceof Item) 
        		|| (dso instanceof Community) 
        		|| (dso instanceof Collection) 
        		|| (dso instanceof ResearcherPage) 
        		|| (dso instanceof Project) 
        		|| (dso instanceof OrganizationUnit) 
        		|| (dso instanceof ResearchObject))) //RO for dynamic types
        {
            log.error("This DspaceObject (" + dso.getTypeText() + " " 
                    + dso.getID() + ") should not have bin submitted to this "
                    + "plugin, as it supports Items only!");
            return null;
        }
        log.debug("Collecting Metadata for Object " + dso.getHandle());
       
        //init ApplicationService for CRIS-ID Lookup before mapping
        ApplicationService applicationService = new DSpace().getServiceManager().getServiceByName(
                "applicationService", ApplicationService.class);
		applicationService.init();
        
        ArrayList<Metadatum> metadata_values = new ArrayList<>();
        
        if(dso instanceof Item) {
            Item item = (Item) dso;
        	Collections.addAll(metadata_values, item.getMetadata(Item.ANY, Item.ANY, Item.ANY, Item.ANY));
        }else if(dso instanceof Collection) {
        	Collection item = (Collection) dso;
        	Collections.addAll(metadata_values, item.getMetadata(Item.ANY, Item.ANY, Item.ANY, Item.ANY));
        }else if(dso instanceof Community) {
        	Community item = (Community) dso;
        	Collections.addAll(metadata_values, item.getMetadata(Item.ANY, Item.ANY, Item.ANY, Item.ANY));
        }
        /*
         * To use the MetadataConverter with CRIS-Objects, every field has to be specified in the following way.
         * Pointers to other entities have to be specified via the qualifier element, wildcard is not working properly on this case
         *
        if(dso instanceof Project) {
        	Project item = (Project) dso;
        	/*
           	Collections.addAll(metadata_values, item.getMetadata("crisproject", "abstract", "*", "*"));
           	Collections.addAll(metadata_values, item.getMetadata("crisproject", "abstract_en", "*", "*"));
           	Collections.addAll(metadata_values, item.getMetadata("crisproject", "acronym", "*", "*"));
        	Collections.addAll(metadata_values, item.getMetadata("crisproject", "code", "*", "*"));
        	Collections.addAll(metadata_values, item.getMetadata("crisproject", "coinvestigators", "fullName", "*")); //link to RP
          	Collections.addAll(metadata_values, item.getMetadata("crisproject", "description", "*", "*"));
        	Collections.addAll(metadata_values, item.getMetadata("crisproject", "deptproject", "name", "*")); //link to OU
        	log.debug(metadata_values.size() + " metadata for " + item.getCrisID() + "found.");		
        }
        */
        log.debug(metadata_values.size() + " metadata values found");
       
        {
   		 // for every handle-entity: add creation-timestamp in ISO 8601 Formats =LiteralType (=xsd:datetime)
         	Map<String,String> value_map = new HashMap<String,String>();
         	String fieldname = "metadata.creationdate";
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

        
        for (Metadatum value : metadata_values)
        {
        	
            String fieldname = value.schema + "." + value.element;
        	String value_string = value.value;
        	String authorityURIvalue = "";
        	Map<String,String> value_map = new HashMap<String,String>();
        	value_map.put("DSpaceValue", value_string); 	
        	if(value.authority != null && value.authority != "") {
        		//lookup CrisObject from applicationService by ID
        		DSpaceObject aco = applicationService.getEntityByCrisId(value.authority);
        	    if(aco != null) {
        	    	authorityURIvalue = RDFUtil.generateIdentifier(context, aco); 	
            		if( authorityURIvalue != null && !authorityURIvalue.equals("")) {
            			value_map.put("DSpaceAuthority", authorityURIvalue);
                	}	
        	    }
        	}
        	
            if (value.qualifier != null) 
            {
                fieldname = fieldname + "." + value.qualifier;
            }
            if (MetadataExposure.isHidden(context, value.schema, value.element,
                    value.qualifier))
            {
                log.debug(fieldname + " is a hidden metadata field, won't "
                        + "convert it.");
                continue;
            }
            boolean converted = false;
           
            
            if (value.qualifier != null)
            {
                Iterator<MetadataRDFMapping> iter = mappings.iterator();
                while (iter.hasNext())
                {
                	
                    MetadataRDFMapping mapping = iter.next();
                        if (mapping.matchesName(fieldname) &&
                        	(mapping.fulfills(value_string) &&
                        			mapping.fulfillsAuth(authorityURIvalue)))
                        {
                        	mapping.convert(value_map, value.language, uri, convertedData);
                        	converted = true;
                        }
                }
            }
            if (!converted)
            {
            	String name = value.schema + "." + value.element;
                Iterator<MetadataRDFMapping> iter = mappings.iterator();
                while (iter.hasNext() && !converted)
                {
                    	MetadataRDFMapping mapping = iter.next();
                if (mapping.matchesName(name) && (mapping.fulfills(value_string) && mapping.fulfillsAuth(authorityURIvalue)))
                        {
                        	mapping.convert(value_map, value.language, uri, convertedData);
                        	converted = true;
                        }
                }
            }
            if (!converted)
            {
                log.debug("Did not convert " + fieldname + " with value "+ value_map.get("DSpaceValue") +" and " + value_map.get("DSpaceAuthority") + ". Found no "
                        + "corresponding mapping.");
                /*System.out.println("Did not convert " + fieldname + " with value "+ value_map.get("DSpaceValue") +" and " + value_map.get("DSpaceAuthority") + ". Found no "
                       + "corresponding mapping.");*/
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
    	// should be changed, if Cris-Objects schould be converted by this Converter
        //return (type == Constants.ITEM || type == Constants.COLLECTION || type == Constants.COMMUNITY || type == CrisConstants.RP_TYPE_ID || type == CrisConstants.PROJECT_TYPE_ID || type == CrisConstants.OU_TYPE_ID || type >= CrisConstants.CRIS_DYNAMIC_TYPE_ID_START  );
    	return (type == Constants.ITEM || type == Constants.COLLECTION || type == Constants.COMMUNITY);
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
