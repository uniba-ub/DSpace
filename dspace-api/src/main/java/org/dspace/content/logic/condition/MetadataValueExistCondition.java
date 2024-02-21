/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.logic.condition;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.logic.LogicalStatementException;
import org.dspace.core.Context;


/**
 * A condition that returns true if some value exist
 * in a given metadata field
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 * @version $Revision$
 */
public class MetadataValueExistCondition extends AbstractCondition {

    private static Logger log = LogManager.getLogger(MetadataValueExistCondition.class);

    /**
     * Return true if any value for a specified field in the item exist
     * Return false if not
     * @param context   DSpace context
     * @param item      Item to evaluate
     * @return boolean result of evaluation
     * @throws LogicalStatementException
     */
    @Override
    public Boolean getResult(Context context, Item item) throws LogicalStatementException {
        String field = (String)getParameters().get("field");
        if (field == null) {
            return false;
        }

        String[] fieldParts = field.split("\\.");
        String schema = (fieldParts.length > 0 ? fieldParts[0] : null);
        String element = (fieldParts.length > 1 ? fieldParts[1] : null);
        String qualifier = (fieldParts.length > 2 ? fieldParts[2] : null);

        String value = itemService.getMetadataFirstValue(item, schema, element, qualifier, Item.ANY);
        return value != null;
    }
}
