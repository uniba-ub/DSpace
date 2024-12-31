/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.ror.service;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class RorServicesFactoryImpl extends RorServicesFactory {

    protected RorImportMetadataSourceService metadataSourceService;

    public RorServicesFactoryImpl(@Autowired RorImportMetadataSourceService metadataSourceService) {
        this.metadataSourceService = metadataSourceService;
    }

    @Override
    public RorImportMetadataSourceService getRorImportMetadataSourceService() {
        return metadataSourceService;
    }
}