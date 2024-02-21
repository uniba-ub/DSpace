/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.logic.condition;

import java.sql.SQLException;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.logic.LogicalStatementException;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;

/**
 * A condition that accepts a group parameter and returns true if the eperson is direct member of this group
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 * @version $Revision$
 */
public class MemberOfGroupCondition extends AbstractCondition {
    private final static Logger log = LogManager.getLogger();

    GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

    /**
     * Return true if the eperson in the context is member of a specified group
     * item is ignored
     * @param context   DSpace context
     * @param item      Item to evaluate
     * @return boolean result of evaluation
     * @throws LogicalStatementException
     */
    @Override
    public Boolean getResult(Context context, Item item) throws LogicalStatementException {

        String group = (String)getParameters().get("group");
        String direct = (String)getParameters().get("direct");
        try {
            Group groups = groupService.findByName(context, group);
            if (Objects.nonNull(groups)) {
                EPerson eperson = context.getCurrentUser();
                if (StringUtils.isNotBlank(direct)) {
                    return groupService.isDirectMember(groups, eperson);
                }
                return groupService.isMember(context, eperson, group);
            }
        } catch (SQLException e) {
            log.error("Error trying to read group membership for " + context.getCurrentUser().getID().toString()
                + " in Group" + group + ": " + e.getMessage());
            throw new LogicalStatementException(e);
        }
        return false;
    }


}
