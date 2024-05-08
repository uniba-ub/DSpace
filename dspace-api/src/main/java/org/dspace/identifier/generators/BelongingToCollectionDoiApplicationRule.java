/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.identifier.generators;

import java.sql.SQLException;
import java.util.Set;

import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Provide custom doi generation based on certain criteria.
 *
 *
 * @author Stefano Maffei (steph-ieffam @ 4Science.com)
 */
public class BelongingToCollectionDoiApplicationRule implements DoiApplicationRule {

    private static final Logger log = LoggerFactory.getLogger(BelongingToCollectionDoiApplicationRule.class);

    @Autowired
    protected WorkspaceItemService workspaceItemService;

    @Autowired
    protected WorkflowItemService workflowItemService;

    protected final Set<String> handles;

    BelongingToCollectionDoiApplicationRule(Set<String> handles) {
        this.handles = handles;
    }

    @Override
    public boolean getApplicable(Context context, Item item) {
        return handles.contains(getOwningCollection(context, item).getHandle());
    }

    protected Collection getOwningCollection(Context context, Item item) {

        Collection owningCollection = item.getOwningCollection();

        try {
            if (owningCollection == null) {
                WorkspaceItem workspaceItem = workspaceItemService.findByItem(context, item);
                if (workspaceItem != null) {
                    owningCollection = workspaceItem.getCollection();
                }
            }

            if (owningCollection == null) {
                WorkflowItem workflowItem = workflowItemService.findByItem(context, item);
                if (workflowItem != null) {
                    owningCollection = workflowItem.getCollection();
                }
            }
        } catch (SQLException e) {
            log.error("Cannot access communities for Item: " + item.getID(), e);
            throw new SQLRuntimeException("Cannot access communities for Item: " + item.getID(), e);
        }

        return owningCollection;
    }
}
