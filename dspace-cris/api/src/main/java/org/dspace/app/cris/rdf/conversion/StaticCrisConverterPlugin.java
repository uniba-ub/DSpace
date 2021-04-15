package org.dspace.app.cris.rdf.conversion;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.apache.log4j.Logger;
import org.dspace.app.cris.model.CrisConstants;
import org.dspace.app.cris.rdf.RDFUtil;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.util.FileUtils;

/**
* Almost similiar to StaticDSOConverterPlugin, but just for CRIS objects.
* Keep CRIS- and Dspace separated 
* @see StaticDSOConverterPlugin
* @adapted florian.gantner@uni-bamberg.de
* @author Pascal-Nicolas Becker (dspace -at- pascal -hyphen- becker -dot- de)
*/
public class StaticCrisConverterPlugin implements ConverterPlugin
 {
private static final Logger log = Logger.getLogger(StaticCrisConverterPlugin.class);
    
    public static final String CONSTANT_DATA_FILENAME_KEY_PREFIX = "rdf.constantcris.data.";
    public static final String CONSTANT_DATA_GENERAL_KEY_SUFFIX = "GENERAL";

    protected ConfigurationService configurationService;
    
    @Override
    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    @Override
    public Model convert(Context context, DSpaceObject dso) 
            throws SQLException
    {
        // As we do not use data of any DSpaceObject, we do not have to check
        // permissions here. We provide only static data out of configuration
        // files.
        
        Model general = this.readFile(CONSTANT_DATA_GENERAL_KEY_SUFFIX,
                RDFUtil.generateIdentifier(context, dso));
        Model typeSpecific = this.readFile(dso.getTypeText(), 
                RDFUtil.generateIdentifier(context, dso));
        
        if (general == null)
            return typeSpecific;
        if (typeSpecific == null)
            return general;
        typeSpecific.setNsPrefixes(general);
        typeSpecific.add(general);
        general.close();
        return typeSpecific;
    }
    
    protected Model readFile(String fileSuffix, String base)
    {
        String path = configurationService.getProperty(
                CONSTANT_DATA_FILENAME_KEY_PREFIX + fileSuffix);
        if (path == null)
        {
            log.error("Cannot find dspace-rdf configuration (looking for "
                    + "property " + CONSTANT_DATA_FILENAME_KEY_PREFIX 
                    + fileSuffix + ")!");
            
            throw new RuntimeException("Cannot find dspace-rdf configuration "
                    + "(looking for property " + 
                    CONSTANT_DATA_FILENAME_KEY_PREFIX + fileSuffix + ")!");
        }
        
        log.debug("Going to read static data from file '" + path + "'.");
        InputStream is = null;
        Model staticDataModel = null;
        try {
            is = FileManager.get().open(path);
            if (is == null)
            {
                log.warn("StaticCrisConverterPlugin cannot find file '" + path 
                        + "', ignoring...");
                return null;
            }

            staticDataModel = ModelFactory.createDefaultModel();
            staticDataModel.read(is, base, FileUtils.guessLang(path));
        } finally {
            if (is != null)
            {
                try {
                    is.close();
                }
                catch (IOException ex)
                {
                    // nothing to do here.
                }
            }
        }
        if (staticDataModel.isEmpty())
        {
            staticDataModel.close();
            return null;
        }
        return staticDataModel;
    }
    
    @Override
    public boolean supports(int type)
    { 
    	//support for Cris-Objects
    	if(type >= CrisConstants.CRIS_DYNAMIC_TYPE_ID_START) {
    		//Workaround for own defined entities
    		return true;
    	}
    	
        switch (type)
        {
            case (CrisConstants.RP_TYPE_ID) :
            	return true;
            case (CrisConstants.OU_TYPE_ID) :
            	return true;
            case (CrisConstants.PROJECT_TYPE_ID) :
            	return true;
            default :
                return false;
        }
    }

}
