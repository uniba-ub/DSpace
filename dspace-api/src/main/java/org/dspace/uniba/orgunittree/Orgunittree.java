/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.uniba.orgunittree;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * The OrgUnitTree model
 * Contains a list of rootnodes and some hashmap of all nodes for easier access
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 *
 */
public class Orgunittree {
    private Date date;
    private List<OrgunittreeNode> rootnodes = new LinkedList<>();

    public Orgunittree() {
        this.date = new Date();
    }

    public void addRoot(OrgunittreeNode node) {
        if (!this.rootnodes.contains(node)) {
            rootnodes.add(node);
        }
    }

    public List<OrgunittreeNode> getNodes() {
        return this.rootnodes;
    }

    public OrgunittreeNode findNodeByUUID(UUID uuid) {
        OrgunittreeNode res = null;
        for (OrgunittreeNode root : rootnodes) {
            if (res != null) {
                return res;
            }
            res = recursiveSearch(uuid, root);
        }
        return res;
    }

    private OrgunittreeNode recursiveSearch(UUID uuid,OrgunittreeNode node) {
        if (node.getUuid().equals(uuid)) {
            return node;
        }
        List<OrgunittreeNode> children = node.getChild();
        OrgunittreeNode res = null;
        for (int i = 0; res == null && i < children.size(); i++) {
            res = recursiveSearch(uuid, children.get(i));
        }
        return res;
    }
}
