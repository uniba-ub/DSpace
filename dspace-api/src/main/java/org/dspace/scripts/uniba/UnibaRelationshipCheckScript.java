package org.dspace.scripts.uniba;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.dspace.content.Relationship;
import org.dspace.content.RelationshipType;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.RelationshipService;
import org.dspace.content.service.RelationshipTypeService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
*  Print out total number of relationships and belonging relations.
 * Replaces null values in leftware/rightward type in relationships by the configured relationshiptype configuration.
* @author Florian Gantner (florian.gantner@uni-bamberg.de)
* */
public class UnibaRelationshipCheckScript
    extends DSpaceRunnable<UnibaRelationshipCheckScriptConfiguration<UnibaRelationshipCheckScript>> {

   protected RelationshipService relationshipService;

   protected RelationshipTypeService relationshipTypeService;

   private boolean dryrun;

   private boolean verbose;

   private boolean fix;

   private Context context;

   private static final Logger log = LoggerFactory.getLogger(UnibaRelationshipCheckScript.class);

   @Override
   public void setup() throws ParseException {
       this.relationshipService = ContentServiceFactory.getInstance().getRelationshipService();
       this.relationshipTypeService = ContentServiceFactory.getInstance().getRelationshipTypeService();
       this.dryrun = commandLine.hasOption('n');
       this.verbose = commandLine.hasOption('v');
       this.fix = commandLine.hasOption('f');
   }

   @Override
   public void internalRun() throws Exception {
       context = new Context();
       assignCurrentUserInContext();
       assignSpecialGroupsInContext();
       try {
           if (dryrun) {
               handler.logInfo("Dryrun is activated");
               context.setMode(Context.Mode.READ_ONLY);
           }
           if (verbose) {
               handler.logInfo("Verbose is activated. Additional Infos about actions performed will be logged in the process.");
           }
           int cnt_changed_rels = 0;
           int cnt_rels_total = 0;
           List<Relationship> rels = this.relationshipService.findAll(context);
           HashMap<Integer, Integer> type_cnt = new HashMap<>();
           for (Relationship rel : rels) {
               cnt_rels_total++;
               RelationshipType type = rel.getRelationshipType();
               boolean change = false;
               if (type_cnt.containsKey(type.getID())) {
                   type_cnt.put(type.getID(), type_cnt.get(type.getID()) + 1);
               } else {
                   type_cnt.put(type.getID(), 1);
               }
               if (fix && Objects.isNull(rel.getLeftwardValue()) && Objects.nonNull(type.getLeftwardType())){
                   if (verbose) {
                       handler.logInfo("Added leftwardValue " + type.getLeftwardType() + " to " + rel.toString());
                   }
                   rel.setLeftwardValue(type.getLeftwardType());
                   change = true;
               }
               if (fix && Objects.isNull(rel.getRightwardValue()) && Objects.nonNull(type.getRightwardType())){
                   if (verbose) {
                       handler.logInfo("Added rightwardValue " + type.getRightwardType() + " to " + rel.toString());
                   }
                   rel.setRightwardValue(type.getRightwardType());
                   change = true;
               }
               if (fix && change && !dryrun) {
                relationshipService.update(context, rel);
                   cnt_changed_rels++;
               } else if (fix && change && dryrun){
                   cnt_changed_rels++;
               }
           }
           if (!dryrun) {
               context.commit();
           }
           handler.logInfo("In total " + cnt_rels_total + " relationships.");
           if(fix){
               handler.logInfo("Fixed " + cnt_changed_rels + " relationships with null values in left or right type.");
           }
           //print out total number of relationships
           List<RelationshipType> types = relationshipTypeService.findAll(context);
           for (RelationshipType type : types) {
               int number = type_cnt.containsKey(type.getID()) ? type_cnt.get(type.getID()) : 0 ;
               handler.logInfo("ID " + type.getID() + " || Left Type " + type.getLeftType().getLabel() + " || Leftward Type " + type.getLeftwardType() +
                   " || Rightward Type " + type.getRightwardType() + " || Right Type " + type.getRightType().getLabel() + " || Count " + number);
           }
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
   public UnibaRelationshipCheckScriptConfiguration<UnibaRelationshipCheckScript> getScriptConfiguration() {
       return new DSpace().getServiceManager().getServiceByName("uniba-relationship-check",
           UnibaRelationshipCheckScriptConfiguration.class);
   }
}
