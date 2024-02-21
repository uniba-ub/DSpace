/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.versioning.uniba;

import java.util.Objects;

import org.dspace.content.Item;
import org.dspace.util.UUIDUtils;

/**
 * Error for uniba versioning
 * contains information about some invalid structure of versioning.
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
public class VersioningStructureError {

    Item item;

    String description;

    String errorcase;

    Item refitem;

    VersioningStructureError(Item item, String description, String errorcase) {
        this.item = item;
        this.description = description;
        this.errorcase = errorcase;
    }

    VersioningStructureError(Item item, String description, String errorcase, Item refitem) {
        this.item = item;
        this.description = description;
        this.errorcase = errorcase;
        this.refitem = refitem;
    }

    public String toString() {
        return "Checkeditem: " + printItem(item) + "||" + errorcase + "|| " + this.description +
            (Objects.nonNull(refitem) ? ("||Referenceitem: " + printItem(refitem)) : "");
    }

    String printItem(Item item) {
        return UUIDUtils.toString(item.getID()) + " (" + item.getHandle() + ")";
    }
}
