/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.logic;

import static org.dspace.content.logic.condition.AbstractInHandlesCondition.isInHandles;

import java.util.Set;

import org.dspace.content.Item;
import org.dspace.content.logic.supplier.HandleSupplier;
import org.dspace.core.Context;

/**
 * This is an abstract class that will check if a given handle obtained with the configured
 * {@link AbstractInHandlesFilter#handleSupplier} is contained inside the {@link AbstractInHandlesFilter#handles}
 * provided.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class AbstractInHandlesFilter implements Filter {

    protected String name;
    protected Set<String> handles;
    protected HandleSupplier handleSupplier;

    protected AbstractInHandlesFilter(HandleSupplier handleSupplier) {
        this.handleSupplier = handleSupplier;
    }

    AbstractInHandlesFilter(HandleSupplier handleSupplier, Set<String> handles) {
        this(handleSupplier);
        this.handles = handles;
    }

    @Override
    public Boolean getResult(Context context, Item item) throws LogicalStatementException {
        return isInHandles(context, item, handleSupplier, getHandles());
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setBeanName(String name) {
        this.name = name;
    }

    public Set<String> getHandles() {
        return handles;
    }

    public void setHandles(Set<String> handles) {
        this.handles = handles;
    }
}
