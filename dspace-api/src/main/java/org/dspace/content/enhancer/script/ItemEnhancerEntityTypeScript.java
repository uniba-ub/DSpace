/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer.script;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.enhancer.service.ItemEnhancerService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.EntityTypeService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Script that allows to enhance items, also forcing the updating of the
 * calculated metadata with the enhancement.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 * 
 * Extended to limit the item set to collection/entity wide use to speed up process and recalculation of certain entities.
 * Extended to use some pagination option with some limit where some commit to the database is made
 * 
 * @author florian.gantner@uni-bamberg.de
 *
 */
public class ItemEnhancerEntityTypeScript extends DSpaceRunnable<ItemEnhancerEntityTypeScriptConfiguration<ItemEnhancerEntityTypeScript>> {

    private ItemService itemService;
    
    private CollectionService collectionService;

    private ItemEnhancerService itemEnhancerService;

    private boolean force;
    
    private UUID collection;
    
    private String entitytype;

    private Context context;

    private int limit;

    private int max;

    private int offset;

    private EntityTypeService entityTypeService;

    private static final Logger log = LoggerFactory.getLogger(ItemEnhancerEntityTypeScript.class);

    @Override
    public void setup() throws ParseException {

        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.collectionService = ContentServiceFactory.getInstance().getCollectionService();
        this.entityTypeService = ContentServiceFactory.getInstance().getEntityTypeService();
        itemEnhancerService = new DSpace().getSingletonService(ItemEnhancerService.class);

        this.force = commandLine.hasOption('f');
        if (commandLine.hasOption('c')) {
            this.collection = UUIDUtils.fromString(commandLine.getOptionValue('c'));
        }
        if (commandLine.hasOption('e')) {
            this.entitytype = commandLine.getOptionValue('e');
        }
        if (commandLine.hasOption('l')) {
            try{
                this.limit = Integer.parseInt(commandLine.getOptionValue('l'));
            }catch (Exception e){
                //
            }
        }
        if (commandLine.hasOption('m')) {
            try{
                this.max = Integer.parseInt(commandLine.getOptionValue('m'));
            }catch (Exception e){
                //
            }
        }
        if (commandLine.hasOption('o')) {
            try{
                this.offset = Integer.parseInt(commandLine.getOptionValue('o'));
            }catch (Exception e){
                //
            }
        }
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();
        if (commandLine.hasOption('e') && Objects.isNull(entityTypeService.findByEntityType(context, entitytype))) {
            throw new Exception("unknown entity " + entitytype);
        }
        if (commandLine.hasOption('c') && (Objects.isNull(collection) || Objects.isNull(this.collectionService.find(context, collection)))) {
            throw new Exception("specified collection does not exist");
        }
        context.turnOffAuthorisationSystem();
        try {
            enhanceItems();
            context.complete();
            handler.logInfo("Enhancement completed with success");
        } catch (Exception e) {
            handler.handleException("An error occurs during enhancement. The process is aborted", e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private void enhanceItems() {

        if (this.limit > 0) {
            //use limit and max parameters for pagination.
            findItemsToEnhanceLimitMax();
        } else {
            //common way
            if (Objects.nonNull(entitytype)) {
                findItemsToEnhance().forEachRemaining(this::enhanceItemEntityCheck);
            } else {
                findItemsToEnhance().forEachRemaining(this::enhanceItem);
            }
        }
    }

    private void findItemsToEnhanceLimitMax(){
        Collection coll;
        int total, counter = 0;
        if (Objects.nonNull(collection)) {
            //Paginate through items in one collection
            try {
            coll = collectionService.find(context, collection);
            total = itemService.countItems(context, coll);
            } catch (SQLException e){
                handler.logError(e.getMessage());
                return;
            }
            if ( this.max > 0) total = this.max;
            if (this.offset > 0) {
                //offset is being added to counter and offset
                total += offset;
                counter += offset;
                handler.logInfo("offset " + offset + " added. Range: [" + counter + " to " + total + "] in " + limit + " steps");
                log.info("offset " + offset + " added. Range: [" + counter + " to " + total + "] in " + limit + " steps");
            } else {
                handler.logInfo("Range: [" + counter + " to " + total + "] in " + limit + " steps");
                log.info("Range: [" + counter + " to " + total + "] in " + limit + " steps");
            }
            while (counter < total) {
                try {
                    itemService.findAllByCollection(context, coll, limit, counter).forEachRemaining(this::enhanceItem);
                    counter += limit;
                    context.commit();
                    handler.logInfo("processed " + counter + " out of total " + total + " items");
                    log.info("processed " + counter + " out of total " + total + " items");
                } catch (SQLException e){
                    handler.logError(e.getMessage());
                    counter += limit;
                    handler.logInfo("processed " + counter + " out of total " + total + " items");
                    log.info("processed " + counter + " out of total " + total + " items");
                }
            }
        } else {
            //loop through all!
            try {
                total = itemService.countTotal(context);
            } catch (SQLException e){
                handler.logError(e.getMessage());
                return;
            }
            if (this.max > 0) total = this.max;
            if (this.offset > 0) {
                //offset is being added to counter and offset
                total += offset;
                counter += offset;
                handler.logInfo("offset" + offset + " added. Range: [" + counter + " to " + total + "] in " + limit + " steps");
                log.info("offset" + offset + " added. Range: [" + counter + " to " + total + "] in " + limit + " steps");
            } else {
                handler.logInfo("Range: [" + counter + " to " + total + "] in " + limit + " steps");
                log.info("Range: [" + counter + " to " + total + "] in " + limit + " steps");
            }
            while (counter < total) {
                try {
                    //No Entity check here!
                    if (Objects.nonNull(this.entitytype)) {
                        itemService.findAll(context, limit, counter).forEachRemaining(this::enhanceItemEntityCheck);
                    } else {
                        itemService.findAll(context, limit, counter).forEachRemaining(this::enhanceItem);
                    }
                    counter += limit;
                    context.commit();
                    handler.logInfo("processed " + counter + " out of total " + total + " items");
                    log.info("processed " + counter + " out of total " + total + " items");
                } catch (SQLException e){
                    handler.logError(e.getMessage());
                    counter += limit;
                    handler.logInfo("processed " + counter + " out of total " + total + " items");
                    log.info("processed " + counter + " out of total " + total + " items");
                }
            }
        }

    }

    private Iterator<Item> findItemsToEnhance() {
        try {
        	Iterator<Item> result = null;
         	if (Objects.nonNull(collection)) {
         		//Check, if uuid exist
        			Collection coll = collectionService.find(context, collection);
        			if(coll != null) {
        				result = itemService.findAllByCollection(context, coll);
        			}
        	} else {
        		result = itemService.findAll(context);
        	}
        	return result;
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }

    private void enhanceItem(Item item) {
        if (force) {
            itemEnhancerService.forceEnhancement(context, item);
        } else {
            itemEnhancerService.enhance(context, item);
        }

        uncacheItem(item);

    }

    /**
     * Additional Entity Check.
    */
    private void enhanceItemEntityCheck(Item item) {
        if (Objects.nonNull(entitytype)) {
            if (!entitytype.contentEquals(itemService.getEntityType(item))) {
                uncacheItem(item);
            }
        } else {
            if (force) {
                itemEnhancerService.forceEnhancement(context, item);
            } else {
                itemEnhancerService.enhance(context, item);
            }

            uncacheItem(item);
        }
    }

    private void uncacheItem(Item item) {
        try {
            context.uncacheEntity(item);
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
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
    public ItemEnhancerEntityTypeScriptConfiguration<ItemEnhancerEntityTypeScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("item-enhancer-type",
        		ItemEnhancerEntityTypeScriptConfiguration.class);
    }

}
