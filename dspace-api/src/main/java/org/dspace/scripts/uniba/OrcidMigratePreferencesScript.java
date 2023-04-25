package org.dspace.scripts.uniba;

import org.apache.commons.cli.ParseException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This script converts dspace 5.10 orcid preferences settings to Dspace7 orcid preference settings
 * For testing purposes its configuration contains some dryrun mode and statistics as well as general errors are also printed on the handler log.
 * see migrate-orcid.cfg for settings
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 * */
public class OrcidMigratePreferencesScript extends DSpaceRunnable<OrcidMigratePreferencesScriptConfiguration<OrcidMigratePreferencesScript>> {

    private ItemService itemService;

    private MetadataFieldService metadataFieldService;

    protected ConfigurationService configurationService;

    private boolean dryrun;

    private boolean deletemdfield;

    private boolean clearsourcemdfield;

    private boolean append;

    private Context context;

    private static final Logger log = LoggerFactory.getLogger(OrcidMigratePreferencesScript.class);

    //sourcefield, targetfield
    private final Map<String, String> mappingfields = new HashMap<>();

    // sourcefield, Mapping
    private final Map<String, OrcidMigratePreferencesMapping> mappingvalues = new HashMap<>();

    @Override
    public void setup() throws ParseException {
        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.metadataFieldService = ContentServiceFactory.getInstance().getMetadataFieldService();
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        this.dryrun = commandLine.hasOption('n');
        this.deletemdfield = commandLine.hasOption('d');
        this.clearsourcemdfield = commandLine.hasOption('c');
        this.append = commandLine.hasOption('a');
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();
        readConfig();
        if(dryrun){
            log.info("Dryrun is activated");
            handler.logInfo("Dryrun is activated");
        }
        processTransformation(context);
        if(deletemdfield){
            deleteMD(context);
        }
        context.turnOffAuthorisationSystem();
        try {
            context.complete();
        } catch (Exception e) {
            handler.handleException("An error occurs during notification. The process is aborted", e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() throws SQLException {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OrcidMigratePreferencesScriptConfiguration<OrcidMigratePreferencesScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("migrate-orcid-preferences",
                OrcidMigratePreferencesScriptConfiguration.class);
    }

    private void processTransformation(Context context) {
        //for each field, update the values
        if(!mappingfields.isEmpty()){

            for(Map.Entry<String, String> entry : mappingfields.entrySet()){
                int counter_created = 0, counter_novalue = 0, counter_cleared = 0, counter_error = 0;
                String target = entry.getValue();
                String source = entry.getKey();
                String[] mdsource = source.split("\\.");
                String[] mdtarget = target.split("\\.");
                // Check for each metadata, not for each orcid value
                // items might be checked several times
                // so we also get the persons who have their orcid already deleted and no metadafafields should remain
                try {
                    Iterator<Item> items = itemService.findUnfilteredByMetadataField(context, mdsource[0], mdsource[1], (mdsource.length == 3) ? mdsource[2] : null, Item.ANY);
                    while (items.hasNext()) {
                        try {
                            Item item = items.next();
                            // get source values
                            List<MetadataValue> vals = itemService.getMetadata(item, mdsource[0], mdsource[1], (mdsource.length == 3) ? mdsource[2] : null, Item.ANY);

                            // check, if some target field already exist. If so, remove sourcefields
                            // if append mode is activated, skip this step. this allows multiple fields written to one target metadata field!
                            if (itemService.getMetadata(item, target) != null && !append) {
                                if (clearsourcemdfield && vals != null && !vals.isEmpty()) {
                                    if (!dryrun) {
                                        itemService.removeMetadataValues(context, item, vals);
                                    }
                                    counter_cleared++;
                                }
                                continue;
                            }
                            // check mapping
                            if (!mappingvalues.isEmpty()) {
                                if (mappingvalues.containsKey(source)) {
                                    OrcidMigratePreferencesMapping mapping = mappingvalues.get(source);
                                    // loop through existing values
                                    for (MetadataValue existingsourceval : vals) {
                                        boolean anymatch = false;
                                        for (Map.Entry<String, List<String>> targetentrymapping : mapping.getValues().entrySet()) {
                                            // check, which key matches with the value
                                            if (existingsourceval.getValue().equalsIgnoreCase(targetentrymapping.getKey())) {
                                                // for every list entry generate value
                                                if (!dryrun) itemService.addMetadata(context, item, mdtarget[0], mdtarget[1], (mdtarget.length == 3) ? mdtarget[2] : null, Item.ANY, targetentrymapping.getValue());
                                                counter_created += targetentrymapping.getValue().size();
                                                anymatch = true;
                                            }
                                        }
                                        if(!anymatch) counter_novalue++;
                                    }
                                }
                            }

                            // remove existing source values
                            if (clearsourcemdfield && vals != null && !vals.isEmpty()) {
                                // remove existing value
                                if (!dryrun) itemService.removeMetadataValues(context, item, vals);
                                counter_cleared++;

                            }
                            if(!dryrun) itemService.update(context, item);
                    } catch(SQLException | AuthorizeException e){
                        counter_error++;
                    }
                    }
                } catch (SQLException | AuthorizeException | IllegalArgumentException e) {
                    log.error(e.getMessage(), e);
                    handler.logError(e.getMessage(), e);
                }
                log.info("Action Create for " + target + " | total created " + counter_created + " | cleared " + counter_cleared + " | no matching value" + counter_novalue + " | error " + counter_error);
                handler.logInfo("Action Create for " + target + " | total created " + counter_created + " | cleared " + counter_cleared + " | no matching value" + counter_novalue + " | error " + counter_error);
            }

        }else{
            log.info("No metadatafields to transform. Skipping step");
            handler.logInfo("No metadatafields to transform. Skipping step");
        }
    }

    private void deleteMD(Context context) {
            if(!mappingfields.isEmpty()){
                for(String mdname : mappingfields.keySet()){
                    int counter = 0, counter_error = 0;
                    try {
                        MetadataField mdf  = metadataFieldService.findByString(context, mdname, '.');
                        if(mdf != null){
                            if(!dryrun){
                                metadataFieldService.delete(context, mdf);
                            }else{
                                log.info("Would have deleted mdfield" + mdname);
                                handler.logInfo("Would have deleted mdfield" + mdname);
                            }
                            counter++;
                        }else{
                            log.info("metadatafield to delete " + mdname + " does not exist");
                            handler.logInfo("metadatafield to delete " + mdname + " does not exist");
                        }
                    } catch (SQLException | AuthorizeException e) {
                        //possible some value still exist
                        log.error(e.getMessage(), e);
                        handler.logError(e.getMessage() ,e);
                        counter_error++;
                    }
                    log.info("Action Delete for " + mdname + " | total deleted " + counter + " | error " + counter_error);
                    handler.logInfo("Action Delete for " + mdname + " | total deleted " + counter + " | error " + counter_error);
                }
            }else{
                log.info("No metadatafields to delete");
                handler.logInfo("No metadatafields to delete");
            }
    }

    public void readConfig(){
        List<String> fields = configurationService.getPropertyKeys("orcidmigration.target.");
        String separator = configurationService.getProperty("orcidmigration.separator");
        for(String field : fields){
            OrcidMigratePreferencesMapping mapping;
            String targetfield = configurationService.getProperty(field);
            field = field.replace("orcidmigration.target.", "");
            if(targetfield != null && !targetfield.isEmpty() && !field.isEmpty()){
                try{
                    List<String> mappingfields = Arrays.stream(configurationService.getArrayProperty("orcidmigration.value." + field)).collect(Collectors.toList());
                    mapping = new OrcidMigratePreferencesMapping(field, targetfield);
                    //split by separator
                    for(String mappingvalue : mappingfields){
                        if(mappingvalue != null && separator != null && mappingvalue.contains(separator)){
                            String[] valuefieldsplit = mappingvalue.split(separator);
                            if(valuefieldsplit.length == 2 && valuefieldsplit[0] != null && valuefieldsplit[1] != null && !valuefieldsplit[0].isEmpty() && !valuefieldsplit[1].isEmpty()){
                                mapping.addMappingValueToMap(valuefieldsplit[0], valuefieldsplit[1]);
                            }
                        }
                    }
                    if(!mapping.values.isEmpty()){
                        mappingvalues.put(field, mapping);
                    }
                }catch (Exception e){
                    //
                    log.error(e.getMessage(), e);
                    handler.logError(e.getMessage(), e);
                }
            }
            mappingfields.put(field, targetfield);
        }
    }

    class OrcidMigratePreferencesMapping {

    String targetfield;
    String sourcefield;

    OrcidMigratePreferencesMapping(String source, String target){
        this.sourcefield = source;
        this.targetfield = target;
    }
    Map<String,List<String>> values = new HashMap<>();


        public Map<String, List<String>> getValues() {
            return values;
        }

        void addMappingValueToMap(String key, String value){
        if(!values.containsKey(key)){
            ArrayList<String> list = new ArrayList<>();
            list.add(value);
            values.put(key, list);
        }else{
            List<String> list = values.get(key);
            if(!list.contains(value)){
                list.add(value);
                values.put(key, list);
            }
        }
    }

    }

}
