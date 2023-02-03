/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer.script;

import org.apache.commons.cli.ParseException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.dspace.content.Item;
import org.dspace.content.enhancer.service.ItemEnhancerService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.EntityTypeService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.discovery.SearchUtils;
import org.dspace.discovery.SolrSearchCore;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Script that allows to enhance items, also forcing the updating of the
 * calculated metadata with the enhancement.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 * 
 * Extended to solr search to discover not-enhanced entities.
 * Some Query can be delivered,
 * additional some daterange filterquery on the lastModified field
 * and additional collection/entitytype queries as filterfacets.
 *
 * @author florian.gantner@uni-bamberg.de
 *
 */
public class ItemEnhancerByDateScript extends DSpaceRunnable<ItemEnhancerByDateScriptConfiguration<ItemEnhancerByDateScript>> {

    private ItemService itemService;
    
    private CollectionService collectionService;

    private ItemEnhancerService itemEnhancerService;

    protected SolrSearchCore solrSearchCore;

    private boolean force;
    
    private UUID collection;
    
    private String entitytype;

    private String query;

    private String dateupper;

    private String datelower;

    private Context context;

    private int max;

    private int limit;

    private EntityTypeService entityTypeService;

    private static final Logger log = LoggerFactory.getLogger(ItemEnhancerByDateScript.class);

    @Override
    public void setup() throws ParseException {

        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.collectionService = ContentServiceFactory.getInstance().getCollectionService();
        this.entityTypeService = ContentServiceFactory.getInstance().getEntityTypeService();
        itemEnhancerService = new DSpace().getSingletonService(ItemEnhancerService.class);
        this.solrSearchCore = new DSpace().getSingletonService(SolrSearchCore.class);

        this.force = commandLine.hasOption('f');
        if (commandLine.hasOption('c')) {
            this.collection = UUIDUtils.fromString(commandLine.getOptionValue('c'));
        }
        if (commandLine.hasOption('e')) {
            this.entitytype = commandLine.getOptionValue('e');
        }
        if (commandLine.hasOption('q')) {
            this.query = commandLine.getOptionValue('q');
        }
        if (commandLine.hasOption('d')) {
            this.dateupper = commandLine.getOptionValue('d');
        }
        if (commandLine.hasOption('s')) {
            this.datelower = commandLine.getOptionValue('s');
        }
        if (commandLine.hasOption('m')) {
            try{
                this.max = Integer.parseInt(commandLine.getOptionValue('m'));
            }catch (Exception e){
                //
            }
        }
        if (commandLine.hasOption('l')) {
            try{
                this.limit = Integer.parseInt(commandLine.getOptionValue('l'));
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
        SolrPingResponse ping = solrSearchCore.getSolr().ping();
        if(ping.getStatus() > 299) throw new Exception("Solr seems not to be available. Status" + ping.getStatus());

        context.turnOffAuthorisationSystem();
        try {
            searchItems();
            context.complete();
            handler.logInfo("Enhancement completed with success");
        } catch (Exception e) {
            handler.handleException("An error occurs during enhancement. The process is aborted", e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
    }


    private void searchItems() {

        List<String> items = new ArrayList<>();
        try {
            SolrDocumentList results = searchItemsInSolr(this.query, this.dateupper, this.datelower);
            for (SolrDocument doc : results) {
                String resourceid = (String) doc.getFieldValue(SearchUtils.RESOURCE_ID_FIELD);
                if (Objects.nonNull(resourceid) && Objects.nonNull(UUIDUtils.fromString(resourceid))) items.add(resourceid);
            }
        } catch (SolrServerException | IOException e) {
            handler.logError(e.getMessage(), e);
            log.error(e.getMessage());
        }
        int total = items.size();
        if (total == 0){
            handler.logInfo("No results in solr-Query");
            log.info("No results in solr-Query");
            return;
        }

        if (this.limit > 0) {
            // Split through every list
            int counter = 0, start, end;
            while (counter < total){
                start = counter;
                end = counter + limit;
                if(end > (total -1)) end = total - 1;
                try {
                    this.itemService.findByIds(context, items.subList(start, end)).forEachRemaining(this::enhanceItem);
                    context.commit();
                    counter += limit;
                } catch (SQLException e) {
                    handler.logError(e.getMessage());
                    counter += limit;
                }

            }
            handler.logInfo("enhanced " + total + " items");
            log.info("enhanced " + total + " items");

        } else {
            try {
                this.itemService.findByIds(context, items).forEachRemaining(this::enhanceItem);
            } catch (SQLException e) {
                handler.logError(e.getMessage());
            }
            handler.logInfo("enhanced " + total + " items");
            log.info("enhanced " + total + " items");
        }
    }

    private SolrDocumentList searchItemsInSolr(String query, String datequeryupper, String datequerylower) throws SolrServerException, IOException {
        SolrQuery sQuery;
        if (Objects.nonNull(query)) {
            sQuery = new SolrQuery(query);
        } else {
            sQuery = new SolrQuery("*");
        }
        if(Objects.nonNull(datequeryupper) && Objects.nonNull(datequerylower)){
            sQuery.addFilterQuery("lastModified:["+datequerylower+" TO " + datequeryupper +"]");
        } else if (Objects.nonNull(datequeryupper) ){
            sQuery.addFilterQuery("lastModified:[* TO " + datequeryupper +"]");
        } else if (Objects.nonNull(datequerylower) ){
            sQuery.addFilterQuery("lastModified:[" + datequerylower + " TO *]");
        }
        if (Objects.nonNull(entitytype)) {
            sQuery.addFilterQuery("search.entitytype:"+entitytype);
        }
        sQuery.addFilterQuery(SearchUtils.RESOURCE_TYPE_FIELD+":Item");
        if (Objects.nonNull(collection)) {
            sQuery.addFilterQuery("location.coll:"+UUIDUtils.toString(collection));
        }
        sQuery.addField(SearchUtils.RESOURCE_ID_FIELD);
        if (max > 0) {
            sQuery.setRows(this.max);
        } else {
            sQuery.setRows(Integer.MAX_VALUE);
        }
        sQuery.setSort("lastModified_dt",SolrQuery.ORDER.asc);
        handler.logInfo("Query Params:" + sQuery.toString());
        QueryResponse qResp = solrSearchCore.getSolr().query(sQuery);
        return qResp.getResults();
    }

    private void enhanceItem(Item item) {
        if (force) {
            itemEnhancerService.forceEnhancement(context, item);
        } else {
            itemEnhancerService.enhance(context, item);
        }

        uncacheItem(item);
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
    public ItemEnhancerByDateScriptConfiguration<ItemEnhancerByDateScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("item-enhancer-date",
                ItemEnhancerByDateScriptConfiguration.class);
    }

}
