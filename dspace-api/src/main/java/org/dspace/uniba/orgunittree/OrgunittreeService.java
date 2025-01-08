/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.uniba.orgunittree;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.logic.DefaultFilter;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.CrisConstants;
import org.dspace.discovery.SearchUtils;
import org.dspace.discovery.SolrSearchCore;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;
import org.dspace.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The OrgunitTreeService contains all functionalities to create trees
 * and their structure on given metadatafields, orders and conditions
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 *
 */
public class OrgunittreeService {
    private String entity;

    private List<String> root;
    private DefaultFilter rootcondition;
    private DefaultFilter generalcondition;

    private List<OrgunittreeMetricsConfiguration> metricsconfiguration;
    private String verticalrelationfield;

    @Autowired
    ItemService itemService;

    @Autowired
    GroupService groupService;

    @Autowired
    SolrSearchCore solrSearchCore;

    private boolean onlyAnonymous;

    public String ANONYMOUS_GROUP_SOLR;

    private static final Logger log = LogManager.getLogger();

    //singleton instance of tree
    private Orgunittree instance;

    List<OrgunittreeNode> traversal_order = new ArrayList<>();


    public void setEntity(String entity) {
        this.entity = entity;
    }

    public void setRoot(List<String> root) {
        this.root = root;
    }

    public void setMetricsconfiguration(List<OrgunittreeMetricsConfiguration> metricsconfiguration) {
        this.metricsconfiguration = metricsconfiguration;
    }

    public String getEntity() {
        return entity;
    }

    public List<String> getRoot() {
        return root;
    }

    public List<OrgunittreeMetricsConfiguration> getMetricsconfiguration() {
        return metricsconfiguration;
    }
    public void setVerticalrelationfield(String verticalrelationfield) {
        this.verticalrelationfield = verticalrelationfield;
    }

    public String getVerticalrelationfield() {
        return verticalrelationfield;
    }

    public void setRootcondition(DefaultFilter rootcondition) {
        this.rootcondition = rootcondition;
    }

    public DefaultFilter getRootcondition() {
        return rootcondition;
    }

    public void setGeneralcondition(DefaultFilter generalcondition) {
        this.generalcondition = generalcondition;
    }

    public DefaultFilter getGeneralcondition() {
        return generalcondition;
    }


    public List<OrgunittreeNode> getRootTree(Context context) {
        if (instance == null) {
            try {
                instance = createTree(context);

                if (isOnlyAnonymous() && Objects.isNull(ANONYMOUS_GROUP_SOLR)) {
                    try {
                        Group a = groupService.findByName(context, Group.ANONYMOUS);
                        ANONYMOUS_GROUP_SOLR = 'g' + UUIDUtils.toString(a.getID());
                    } catch (SQLException e) {
                        log.debug(e.getMessage());
                    }
                }

                calculateMetrics(instance);
                log.info("Orgunit Tree created!");
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
        List<OrgunittreeNode> res = instance.getNodes();
        for (OrgunittreeNode r : res) {
            r = reloadItem(r, context);
        }
        return res;
    }

    public List<OrgunittreeNode> recreateTree(Context context) {
        instance = null;
        log.info("deleted tree - recreate orgunittree");
        return getRootTree(context);
    }

    public OrgunittreeNode getNode(Context context, UUID uuid) {

        if (instance == null) {
            //recreate tree
            try {
                instance = createTree(context);

                if (isOnlyAnonymous() && Objects.isNull(ANONYMOUS_GROUP_SOLR)) {
                    try {
                        Group a = groupService.findByName(context, Group.ANONYMOUS);
                        ANONYMOUS_GROUP_SOLR = 'g' + UUIDUtils.toString(a.getID());
                    } catch (SQLException e) {
                        log.debug(e.getMessage());
                    }
                }

                calculateMetrics(instance);
                log.info("Orgunit Tree created!");
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
        return reloadItem(instance.findNodeByUUID(uuid), context);
    }

    public Orgunittree createTree(Context context) {
        Orgunittree newtree = new Orgunittree();
        /*
        * 1. Get all instances of dspace.entity.type getEntity()
        * */
        List<Item> root = new ArrayList<>();
        List<Item> childs = new ArrayList<>();
        try {
            Iterator<Item> items =
                itemService.findArchivedByMetadataField(context, CrisConstants.MD_ENTITY_TYPE.toString(), getEntity());
            while (items.hasNext()) {
                Item item = items.next();
                // check rootcondition
                if (!getGeneralcondition().getResult(context, item)) {
                    continue;
                }

                if (getRootcondition().getResult(context, item)) {
                    root.add(item);
                } else {
                    childs.add(item);
                }
            }
        } catch (SQLException | AuthorizeException e) {
            log.debug(e.getMessage());
        }
        /*
        * 1. Assign the root nodes to the tree
        */
        for (Item rootnode : root) {
            newtree.addRoot(new OrgunittreeNode((rootnode)));
        }

        /*
        * 2: get all Entities which are children and append to root structure
        */
        try {
            List<Item> unassigned = childs;

            //Loop through list (and new list) until no further child can be added
            int newadded = unassigned.size();
            while (newadded > 0) {
                List<Item> tocheck = new ArrayList<>();
                newadded = 0 ;
                for (Item it : unassigned) {
                    try {
                        UUID relateduuid =
                            UUIDUtils.fromString(itemService.getMetadataByMetadataString(it,
                                getVerticalrelationfield()).get(0).getAuthority());
                        OrgunittreeNode nd = newtree.findNodeByUUID(relateduuid);
                        if (nd != null) {
                            OrgunittreeNode itnode = new OrgunittreeNode(it);
                            nd.addChild(itnode);
                            nd.addChild_UUID(it.getID());
                            newadded++;
                        } else {
                            tocheck.add(it);
                        }
                    } catch (Exception e) {
                        //ignore these orgunits where some error occurs
                        //nor some child or parent orgunit
                        log.error(e.getMessage(), e);
                    }
                }
                //assign list for next loop
                unassigned = tocheck;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return newtree;
    }

    private void calculateMetrics(Orgunittree newtree) {
        //this is repeated for every subordinate node;

        for (OrgunittreeNode node : newtree.getNodes()) {
            traversal_order = new ArrayList<>(); // new list for every root node!
            visitAllNodesAndAddToList(node);
            Collections.reverse(traversal_order);
            for (OrgunittreeMetricsConfiguration conf : getMetricsconfiguration()) {
                //performed on reveres traversal order of nodes
                if (conf.isAggregate()) {
                    assignMetricsByAggregating(conf,traversal_order);
                } else {
                    //perfomed on all nodes
                    assignMetricsBySolr(conf,node);
                }
            }
        }
    }

    /* Every node */
    private void assignMetricsBySolr(OrgunittreeMetricsConfiguration conf, OrgunittreeNode node) {
        try {
            OrgunittreeMetrics c = new OrgunittreeMetrics();
            int number = getQuery(conf, node.getUuidString());
            c.setValue(number);
            c.setShortname(conf.getShortname());
            node.addMetric(c);
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
        // repeat for every child node and every depth until no children exist (base case)
        if (!node.getChild().isEmpty()) {
            for (OrgunittreeNode childnode : node.getChild()) {
                assignMetricsBySolr(conf, childnode);
            }
        }
    }


    /* From depth to root */
    private void assignMetricsByAggregating(OrgunittreeMetricsConfiguration conf, List<OrgunittreeNode> nodelist) {
        for (OrgunittreeNode actualnode : nodelist) {
            //already visited. should not occur
            if (hasNodeMetrics(conf, actualnode)) {
                log.error("already visited!");
                continue;
            }
            if (!actualnode.getChild().isEmpty()) {
                //check, if some children exist.
                //add values form children and sum up together
                //children should already have some value
                int aggregatedvalue = 0;
                for (OrgunittreeNode child : actualnode.getChild()) {
                    if (!hasNodeMetrics(conf, child)) {
                        log.error("no metrics!");
                    }
                    // add already aggregated values from childs
                    try {
                        aggregatedvalue += child.getMetrics().get(conf.getShortname()).getValue();
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                }
                //add count from actual node!
                try {
                    aggregatedvalue += actualnode.getMetrics().get(conf.getQuery()).getValue();
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
                addAggregatedMetrics(conf, actualnode, aggregatedvalue);
            } else {
                //If no child nodes, set Value
                int value = 0;
                try {
                    value += actualnode.getMetrics().get(conf.getQuery()).getValue();
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
                addAggregatedMetrics(conf, actualnode, value);

            }
        }
    }

    private void visitAllNodesAndAddToList(OrgunittreeNode node) {
        if (!traversal_order.contains(node)) {
            traversal_order.add(node);
        }
        if (!node.getChild().isEmpty()) {
            for (OrgunittreeNode child : node.getChild()) {
                visitAllNodesAndAddToList(child);
            }
        }
    }

    private void addAggregatedMetrics(
        OrgunittreeMetricsConfiguration conf, OrgunittreeNode node, int additionalnumber) {
        try {
            OrgunittreeMetrics c = new OrgunittreeMetrics();
            c.setValue(additionalnumber);
            c.setShortname(conf.getShortname());
            c.setAggregated(true);
            node.addMetric(c);
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
    }

    private boolean hasNodeMetrics(OrgunittreeMetricsConfiguration conf, OrgunittreeNode node) {
        return node.getMetrics().containsKey(conf.getShortname());
    }

    private int getQuery(OrgunittreeMetricsConfiguration conf, String uuid)
        throws SolrServerException, IOException {
        SolrQuery sQuery;

        if (conf.getQuery() != null) {
            sQuery = new SolrQuery(conf.getQuery().replaceAll("\\{0\\}", uuid));
        } else {
            sQuery = new SolrQuery();
        }
        sQuery.setParam("q.op","OR");

        if (conf.getFilterquery() != null && !conf.getFilterquery().isEmpty()) {
            sQuery.setFilterQueries((String[]) conf.getFilterquery().toArray());
        }
        // add read condition for anonymous group! Considers "active" persons and projects
        if (isOnlyAnonymous() && Objects.nonNull(ANONYMOUS_GROUP_SOLR)) {
            sQuery.addFilterQuery("read:" + ANONYMOUS_GROUP_SOLR);
        }
        sQuery.setRows(Integer.MAX_VALUE);
        sQuery.setFields(SearchUtils.RESOURCE_ID_FIELD);
        QueryResponse qResp = solrSearchCore.getSolr().query(sQuery);
        SolrDocumentList results = qResp.getResults();
        Long numResults = results.getNumFound();
        return numResults.intValue();
    }

    private OrgunittreeNode reloadItem(OrgunittreeNode node, Context context)  {
        if (node.getItem() != null) {
            try {
                //Reload item / entity.
                //This avoids problems when the belonging collection item has been changed.
                Item rel = node.getItem();
                rel = context.reloadEntity(rel);
                node.setItem(rel);
            } catch (SQLException ex) {
                log.error(ex.getMessage());
            }
        } else if (node.getItem() == null && node.getUuid() != null) {
            try {
                node.setItem(itemService.find(context, node.getUuid()));
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
        return node;
    }

    public boolean isOnlyAnonymous() {
        return onlyAnonymous;
    }

    public void setOnlyAnonymous(boolean onlyAnonymous) {
        this.onlyAnonymous = onlyAnonymous;
    }
}
