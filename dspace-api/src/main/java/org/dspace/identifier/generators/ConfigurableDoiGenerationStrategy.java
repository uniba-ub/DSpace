/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.identifier.generators;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.identifier.DOI;
import org.dspace.identifier.service.DOIService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Provide custom doi generation based on certain criteria.
 *
 *
 * @author Stefano Maffei (steph-ieffam @ 4Science.com)
 */
public class ConfigurableDoiGenerationStrategy implements DoiGenerationStrategy {

    @Autowired(required = true)
    protected DOIService doiService;

    private DoiApplicationRule doiApplicationRule;

    private DoiNamespaceGenerator doiNamespaceGenerator;

    private DoiGenType generationType;

    @Override
    public boolean isApplicable(Context context, DSpaceObject dso) {
        return doiApplicationRule.getApplicable(context, (Item) dso);
    }

    @Override
    public String generateDoi(Context context, DSpaceObject dso, DOI doi) {
        return doiNamespaceGenerator.getNamespace(context, (Item) dso) +
            doi.getID();
    }

    @Override
    public DoiGenType getGenerationType() {
        return generationType;
    }

    @Override
    public String getDOIResolverAndPrefix(Context context, DSpaceObject dso, String prefix) {
        return doiService.getResolver() + SLASH + prefix + SLASH
            + doiNamespaceGenerator.getNamespace(context, (Item) dso);
    }

    public DoiApplicationRule getDoiApplicationRule() {
        return doiApplicationRule;
    }

    public void setDoiApplicationRule(DoiApplicationRule doiApplicationRule) {
        this.doiApplicationRule = doiApplicationRule;
    }

    public DoiNamespaceGenerator getDoiNamespaceGenerator() {
        return doiNamespaceGenerator;
    }

    public void setDoiNamespaceGenerator(DoiNamespaceGenerator doiNamespaceGenerator) {
        this.doiNamespaceGenerator = doiNamespaceGenerator;
    }

    public void setGenerationType(DoiGenType generationType) {
        this.generationType = generationType;
    }

}
