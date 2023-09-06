/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dspace.app.rest.OrgunittreeRestController;
import org.dspace.app.rest.projection.Projection;

/**
 * The OrgUnitTreeNode REST Resource
 * 
 * @author florian.gantner@uni-bamberg.de
 *
 */
public class OrgunittreeNodeRest extends RestAddressableModel {

    public static final String NAME = "orgunittreenode";

    public static final String CATEGORY = RestModel.ORGUNITTREE;

    private Projection projection = Projection.DEFAULT;

    private List<String> childs = new ArrayList<>();
    private Map<String, Integer> metrics = new HashMap<>();
    private Map<String, Integer> aggregatedmetrics = new HashMap<>();
    private String uuid;
    private ItemRest item;

    public OrgunittreeNodeRest() {
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Map<String, Integer> getMetrics() {
        return this.metrics;
    }

    public void setMetrics(Map<String, Integer> metrics) {
        this.metrics = metrics;
    }

    public void addMetrics(String name, Integer value) {
        this.metrics.put(name, value);
    }

    public void setAggregatedmetrics(Map<String, Integer> aggregatedmetrics) {
        this.aggregatedmetrics = aggregatedmetrics;
    }

    public void addAggregatedmetrics(String name, Integer value) {
        this.aggregatedmetrics.put(name, value);
    }

    public Map<String, Integer> getAggregatedmetrics() {
        return this.aggregatedmetrics;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class getController() {
        return OrgunittreeRestController.class;
    }

    @JsonIgnore
    public Projection getProjection() {
        return projection;
    }

    public void setProjection(Projection projection) {
        this.projection = projection;
    }


    @Override
    //@JsonIgnore
    public String getType() {
        return NAME;
    }

    public ItemRest getItem() {
        return item;
    }

    public void setItem(ItemRest item) {
        this.item = item;
    }

    public List<String> getChilds() {
        return childs;
    }

    public void setChild(List<String> childs) {
        this.childs = childs;
    }

    public void addChild(String child) {
        this.childs.add(child);
    }
}

