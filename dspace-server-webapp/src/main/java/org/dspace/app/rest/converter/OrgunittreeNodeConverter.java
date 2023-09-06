/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import java.util.Map;

import org.dspace.app.rest.model.OrgunittreeNodeRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.uniba.orgunittree.OrgunittreeMetrics;
import org.dspace.uniba.orgunittree.OrgunittreeNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The OrgUnitTreeNode REST Converter
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 *
 */
@Component
public class OrgunittreeNodeConverter implements DSpaceConverter<OrgunittreeNode, OrgunittreeNodeRest> {

    // Must be loaded @Lazy, as ConverterService autowires all DSpaceConverter components
    @Lazy
    @Autowired
    private ConverterService converter;

    @Override
    public OrgunittreeNodeRest convert(OrgunittreeNode modelObject, Projection projection) {
        OrgunittreeNodeRest outreeNodeRest = new OrgunittreeNodeRest();
        outreeNodeRest.setProjection(projection);
        outreeNodeRest.setUuid(modelObject.getUuidString());
        try {
            for (Map.Entry entry : modelObject.getMetrics().entrySet()) {
                String key = (String) entry.getKey();
                OrgunittreeMetrics val = (OrgunittreeMetrics) entry.getValue();
                if (val.getAggregated()) {
                    outreeNodeRest.addAggregatedmetrics(key, val.getValue());
                } else {
                    outreeNodeRest.addMetrics(key, val.getValue());
                }
            }
        } catch (Exception e) {
            //
        }
        for (OrgunittreeNode node : modelObject.getChild()) {
            //convert node and add to rest object
            //
            outreeNodeRest.addChild(node.getUuidString());
        }
        //convert the item
        try {
            outreeNodeRest.setItem(converter.toRest(modelObject.getItem(), projection));
        } catch (Exception e) {
            //
            e.printStackTrace();
        }
        return outreeNodeRest;
    }

    @Override
    public Class<OrgunittreeNode> getModelClass() {
        return OrgunittreeNode.class;
    }
}
