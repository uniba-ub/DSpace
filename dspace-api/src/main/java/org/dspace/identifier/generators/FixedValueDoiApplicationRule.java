/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.identifier.generators;

import org.dspace.content.Item;
import org.dspace.core.Context;

/**
 * Provide custom doi generation based on certain criteria.
 *
 *
 * @author Stefano Maffei (steph-ieffam @ 4Science.com)
 */

public class FixedValueDoiApplicationRule implements DoiApplicationRule {

    private boolean configuredValue;

    @Override
    public boolean getApplicable(Context context, Item item) {
        return configuredValue;
    }

    public boolean isConfiguredValue() {
        return configuredValue;
    }

    public void setConfiguredValue(boolean configuredValue) {
        this.configuredValue = configuredValue;
    }

}
