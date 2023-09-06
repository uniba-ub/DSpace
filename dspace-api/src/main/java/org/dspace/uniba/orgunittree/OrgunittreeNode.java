/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.uniba.orgunittree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.dspace.content.Item;
import org.dspace.util.UUIDUtils;

/**
 * The OrgUnitTreeNode Model
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 *
 */
public class OrgunittreeNode {

    public OrgunittreeNode(Item item) {
        this.item = item;
        this.uuid = item.getID();
        this.displayname = item.getName();
        this.childs = new ArrayList<>();
        this.child_uuid = new ArrayList<>();
        this.metrics = new HashMap<>();
    }

    private Map<String,OrgunittreeMetrics> metrics;
    private final List<OrgunittreeNode> childs;
    private final List<UUID> child_uuid;

    //The item of the node
    private Item item;

    //uuid of the dspace object
    private UUID uuid;
    //displayname (as fallback)
    private String displayname;

    public void addChild(OrgunittreeNode node) {
        if (!this.getChild().contains(node)) {
            this.getChild().add(node);
        }
    }

    public void addChild_UUID(UUID id) {
        if (!this.getChild_uuid().contains(id)) {
            this.getChild_uuid().add(id);
        }
    }

    public List<OrgunittreeNode> getChild() {
        return this.childs;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUuidString() {
        return UUIDUtils.toString(uuid);
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getDisplayname() {
        return displayname;
    }

    public void setDisplayname(String displayname) {
        this.displayname = displayname;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Map<String, OrgunittreeMetrics> getMetrics() {
        return metrics;
    }

    public void setMetrics(HashMap<String, OrgunittreeMetrics> metrics) {
        this.metrics = metrics;
    }
    public void addMetric(OrgunittreeMetrics node) {
        if (!this.metrics.containsKey(node.getShortname())) {
            this.metrics.put(node.getShortname(),node);
        }
    }

    public List<UUID> getChild_uuid() {
        return child_uuid;
    }
}
