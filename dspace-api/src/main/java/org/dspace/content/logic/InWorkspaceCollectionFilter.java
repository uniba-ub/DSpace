/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.logic;

import java.util.Set;

import org.dspace.content.WorkspaceItem;
import org.dspace.content.logic.supplier.HandleSupplierFactory;

/**
 * This class implements the {@link Filter} interface just to check if the
 * given item has a {@link WorkspaceItem} submitted in that can be found in one of the {@code handle}
 * configured with the {@link InWorkspaceCollectionFilter#handles} property.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class InWorkspaceCollectionFilter
    extends AbstractInHandlesFilter {

    InWorkspaceCollectionFilter(Set<String> handles) {
        super(HandleSupplierFactory.getInstance().collectionWorkspaceHandleSupplier(), handles);
    }

}
