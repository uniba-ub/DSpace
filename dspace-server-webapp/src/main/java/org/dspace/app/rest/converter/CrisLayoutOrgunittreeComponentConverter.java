/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.CrisLayoutSectionRest.CrisLayoutOrgunittreeComponentRest;
import org.dspace.layout.CrisLayoutOrgunittreeComponent;
import org.dspace.layout.CrisLayoutSectionComponent;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link CrisLayoutSectionComponentConverter} for
 * {@link CrisLayoutOrgunittreeComponent}.
 * 
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
@Component
public class CrisLayoutOrgunittreeComponentConverter implements CrisLayoutSectionComponentConverter {

    @Override
    public boolean support(CrisLayoutSectionComponent component) {
        return component instanceof CrisLayoutOrgunittreeComponent;
    }

    @Override
    public CrisLayoutOrgunittreeComponentRest convert(CrisLayoutSectionComponent component) {
        CrisLayoutOrgunittreeComponent orgunittreeComponent = (CrisLayoutOrgunittreeComponent) component;
        CrisLayoutOrgunittreeComponentRest rest = new CrisLayoutOrgunittreeComponentRest();
        rest.setStyle(orgunittreeComponent.getStyle());
        rest.setTitle(orgunittreeComponent.getTitle());
        return rest;
    }

}
