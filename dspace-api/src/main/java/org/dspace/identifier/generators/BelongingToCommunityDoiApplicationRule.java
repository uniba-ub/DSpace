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
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide custom doi generation based on certain criteria.
 *
 *
 * @author Stefano Maffei (steph-ieffam @ 4Science.com)
 */

public class BelongingToCommunityDoiApplicationRule implements DoiApplicationRule {

    private static final Logger log = LoggerFactory.getLogger(BelongingToCommunityDoiApplicationRule.class);

    private final Set<String> handles;

    BelongingToCommunityDoiApplicationRule(Set<String> handles) {
        this.handles = handles;
    }

    @Override
    public boolean getApplicable(Context context, Item item) {

        try {
            Collection owningCollection = item.getOwningCollection();

            if (null == owningCollection) {
                return false;
            }

            for (Community community : owningCollection.getCommunities()) {
                if (handles.contains(community.getHandle())) {
                    return true;
                }
            }
        } catch (SQLException e) {
            log.error("Cannot access communities for Item: " + item.getID(), e);
            throw new SQLRuntimeException("Cannot access communities for Item: " + item.getID(), e);
        }
        return false;
    }

}
