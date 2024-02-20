/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer.script;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
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
 * Extended to limit the item set to collection/entitytype to speed up process
 * Extended to use pagination option with max/offset/limit options
 * - max for testing purposes (process max x items per collection)
 * - offset for pagination (start with item 0+offset per collection)
 * - limit to make some intermediary commit between x items (recommended 100 steps)
 * to make the process more error prone
 * 
 * @author florian.gantner@uni-bamberg.de
 *
 */
public class ItemEnhancerEntityTypeScript
    extends DSpaceRunnable<ItemEnhancerEntityTypeScriptConfiguration<ItemEnhancerEntityTypeScript>> {

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
            try {
                this.limit = Integer.parseInt(commandLine.getOptionValue('l'));
            } catch (Exception e) {
                //
            }
        }
        if (commandLine.hasOption('m')) {
            try {
                this.max = Integer.parseInt(commandLine.getOptionValue('m'));
            } catch (Exception e) {
                //
            }
        }
        if (commandLine.hasOption('o')) {
            try {
                this.offset = Integer.parseInt(commandLine.getOptionValue('o'));
            } catch (Exception e) {
                //
            }
        }
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();
        if (Objects.nonNull(entitytype) && Objects.isNull(entityTypeService.findByEntityType(context, entitytype))) {
            throw new Exception("unknown EntityType " + entitytype);
        }
        if (Objects.nonNull(entitytype) && StringUtils.isNotBlank(entitytype) &&
            this.collectionService.findAll(context).stream()
                .noneMatch(col -> col.getEntityType().contentEquals(entitytype))) {
            throw new Exception("no Collections with EntityType " + entitytype);
        }
        if (commandLine.hasOption('c') && Objects.isNull(collection)) {
            throw new Exception("invalid uuid in the specified Collection");
        }
        if (Objects.nonNull(collection) && (Objects.isNull(this.collectionService.find(context, collection)))) {
            throw new Exception("specified Collection does not exist");
        }
        if (Objects.nonNull(collection) && (Objects.nonNull(entitytype)) &&
            !this.collectionService.find(context, collection).getEntityType().contentEquals(entitytype)) {
            throw new Exception("the specified Collection does not match with the specified EntityType");
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

    private void enhanceItems() throws SQLException {
        if (Objects.nonNull(collection)) {
            Collection coll = this.collectionService.find(context, collection);
            findItemsToEnhance(coll);
        } else if (Objects.nonNull(entitytype)) {
            //for each colelction with entity type
            for (Collection coll : collectionService.findAll(context).stream()
                .filter(collection1 -> collection1.getEntityType().contentEquals(entitytype)).collect(
                Collectors.toList())) {
                findItemsToEnhance(coll);
            }
        } else {
            findItemsToEnhance(null);
        }
    }

    private void findItemsToEnhance(Collection coll) {
        int total = 0;
        int counter = 0;
        if (Objects.nonNull(coll)) {
            //Paginate through items in one (given) collection
            try {
                total = itemService.countItems(context, coll);
            } catch (SQLException e) {
                handler.logError(e.getMessage());
                return;
            }
            if ( this.max > 0) {
                total = this.max;
            }
            if (this.offset > 0) {
                //offset is being added to counter and offset
                total += offset;
                counter += offset;
                if (limit > 0) {
                    handler.logInfo("offset " + offset + " added. Range: ["
                        + counter + " to " + total + "] in " + limit + " steps");
                    log.info("offset " + offset + " added. Range: ["
                        + counter + " to " + total + "] in " + limit + " steps");
                } else {
                    handler.logInfo("offset " + offset + " added. Range: ["
                        + counter + " to " + total + "]");
                    log.info("offset " + offset + " added. Range: ["
                        + counter + " to " + total + "]");
                }
            } else {
                if (limit > 0) {
                    handler.logInfo("Range: [" + counter + " to "
                        + total + "] in " + limit + " steps");
                    log.info("Range: [" + counter + " to " + total + "] in " + limit + " steps");
                } else {
                    handler.logInfo("Range: [" + counter + " to "
                        + total + "]");
                    log.info("Range: [" + counter + " to " + total + "]");
                }
            }
            while (counter < total) {
                if (limit > 0) {
                    try {
                        itemService.findAllByCollection(context, coll, limit, counter)
                            .forEachRemaining(this::enhanceItem);
                        counter += limit;
                        context.commit();
                        handler.logInfo("processed " + counter + " out of total " + total + " items");
                        log.info("processed " + counter + " out of total " + total + " items");
                    } catch (SQLException e) {
                        handler.logError(e.getMessage());
                        counter += limit;
                        handler.logInfo("processed " + counter + " out of total " + total + " items");
                        log.info("processed " + counter + " out of total " + total + " items");
                    }
                } else {
                    try {
                        // no limit, so process all items in one commit
                        itemService.findAllByCollection(context, coll, total, counter)
                            .forEachRemaining(this::enhanceItem);
                        counter += total;
                        context.commit();
                        handler.logInfo("processed " + counter + " out of total " + total + " items");
                        log.info("processed " + counter + " out of total " + total + " items");
                    } catch (SQLException e) {
                        handler.logError(e.getMessage());
                        counter += limit;
                        handler.logInfo("processed " + counter + " out of total " + total + " items");
                        log.info("processed " + counter + " out of total " + total + " items");
                    }
                }
            }
        } else {
            // operate over all items
            try {
                total = itemService.countTotal(context);
            } catch (SQLException e) {
                handler.logError(e.getMessage());
                return;
            }
            if (this.max > 0) {
                total = this.max;
            }
            if (this.offset > 0) {
                //offset is being added to counter and offset
                total += offset;
                counter += offset;
                if (limit > 0) {
                    handler.logInfo("offset " + offset + " added. Range: ["
                        + counter + " to " + total + "] in " + limit + " steps");
                    log.info("offset " + offset + " added. Range: ["
                        + counter + " to " + total + "] in " + limit + " steps");
                } else {
                    handler.logInfo("offset " + offset + " added. Range: ["
                        + counter + " to " + total + "]");
                    log.info("offset " + offset + " added. Range: ["
                        + counter + " to " + total + "]");
                }
            } else {
                if (limit > 0) {
                    handler.logInfo("Range: [" + counter + " to "
                        + total + "] in " + limit + " steps");
                    log.info("Range: [" + counter + " to " + total + "] in " + limit + " steps");
                } else {
                    handler.logInfo("Range: [" + counter + " to "
                        + total + "]");
                    log.info("Range: [" + counter + " to " + total + "]");
                }
            }
            while (counter < total) {
                if (limit > 0) {
                    try {
                        // Check for entity type in enhanceItem method
                        if (Objects.nonNull(this.entitytype)) {
                            itemService.findAll(context, limit, counter).forEachRemaining(this::enhanceItemEntityCheck);
                        } else {
                            itemService.findAll(context, limit, counter).forEachRemaining(this::enhanceItem);
                        }
                        counter += limit;
                        context.commit();
                        handler.logInfo("processed " + counter + " out of total " + total + " items");
                        log.info("processed " + counter + " out of total " + total + " items");
                    } catch (SQLException e) {
                        handler.logError(e.getMessage());
                        counter += limit;
                        handler.logInfo("processed " + counter + " out of total " + total + " items");
                        log.info("processed " + counter + " out of total " + total + " items");
                    }
                } else {
                    try {
                        // Check for entity type in enhanceItem method
                        if (Objects.nonNull(this.entitytype)) {
                            itemService.findAll(context, total, counter).forEachRemaining(this::enhanceItemEntityCheck);
                        } else {
                            itemService.findAll(context, total, counter).forEachRemaining(this::enhanceItem);
                        }
                        counter += total;
                        context.commit();
                        handler.logInfo("processed " + counter + " out of total " + total + " items");
                        log.info("processed " + counter + " out of total " + total + " items");
                    } catch (SQLException e) {
                        handler.logError(e.getMessage());
                        counter += total;
                        handler.logInfo("processed " + counter + " out of total " + total + " items");
                        log.info("processed " + counter + " out of total " + total + " items");
                    }
                }
            }
        }
    }

    private void enhanceItem(Item item) {
        itemEnhancerService.enhance(context, item, force);
        uncacheItem(item);
    }

    /**
     * Additional Entity Check. Only applicable when operating over all entities
    */
    private void enhanceItemEntityCheck(Item item) {
        if (Objects.nonNull(entitytype)) {
            if (!entitytype.contentEquals(itemService.getEntityType(item))) {
                uncacheItem(item);
            }
        } else {
            itemEnhancerService.enhance(context, item, force);
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

    private void assignSpecialGroupsInContext() {
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
