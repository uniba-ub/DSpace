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
 * given item is related to a community handle, by checking both linked {@link org.dspace.workflow.WorkflowItem} and
 * {@link WorkspaceItem} collections.
 * It will return {@code true} only if one of those collections can be found inside the
 * {@link RelatedToCommunityFilter#handles} property.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class RelatedToCommunityFilter extends AbstractInHandlesFilter {

    public RelatedToCommunityFilter(Set<String> handles) {
        super(HandleSupplierFactory.getInstance().relatedCommunityHandleSupplier(), handles);
    }

}
