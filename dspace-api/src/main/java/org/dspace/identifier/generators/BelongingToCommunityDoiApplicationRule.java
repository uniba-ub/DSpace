/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.identifier.generators;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Context;

/**
 * Provide custom doi generation based on certain criteria.
 *
 *
 * @author Stefano Maffei (steph-ieffam @ 4Science.com)
 */

public class BelongingToCommunityDoiApplicationRule implements DoiApplicationRule {

    private final Set<String> handles;

    BelongingToCommunityDoiApplicationRule(Set<String> handles) {
        this.handles = handles;
    }

    @Override
    public boolean getApplicable(Context context, Item item) {
        List<Community> communities;
        try {
            communities = item.getOwningCollection().getCommunities();
            for (Community community : communities) {
                if (handles.contains(community.getHandle())) {
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

}
