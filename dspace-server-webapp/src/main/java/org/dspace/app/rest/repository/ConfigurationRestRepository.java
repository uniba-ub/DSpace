/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.model.PropertyRest;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * This is the repository responsible of exposing configuration properties
 */
@Component(PropertyRest.CATEGORY + "." + PropertyRest.NAME)
public class ConfigurationRestRepository extends DSpaceRestRepository<PropertyRest, String> {

    @Autowired
    private AuthorizeService authorizeService;

    private ConfigurationService configurationService;

    @Autowired
    public ConfigurationRestRepository(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    protected String[] getExposedProperties() {
        return configurationService.getArrayProperty("rest.properties.exposed");
    }

    protected String[] getAdminRestrictedProperties() {
        return configurationService.getArrayProperty("admin.rest.properties.exposed");
    }


    /**
     * Gets the value of a configuration property if it is exposed via REST
     *
     * Example:
     * <pre>
     * {@code
     * curl http://<dspace.server.url>/api/config/properties/google.analytics.key
     *  -XGET \
     *  -H 'Authorization: Bearer eyJhbGciOiJI...'
     * }
     * </pre>
     *
     * @param property
     * @return
     */
    @Override
    @PreAuthorize("permitAll()")
    public PropertyRest findOne(Context context, String property) {
        List<String> exposedProperties = Arrays.asList(getExposedProperties());
        List<String> adminRestrictedProperties = Arrays.asList(getAdminRestrictedProperties());

        if (!configurationService.hasProperty(property) ||
            (adminRestrictedProperties.contains(property) && !isCurrentUserAdmin(context)) ||
            (!exposedProperties.contains(property) && !isCurrentUserAdmin(context))) {
            throw new ResourceNotFoundException("No such configuration property: " + property);
        }

        String[] propertyValues = configurationService.getArrayProperty(property);
        PropertyRest propertyRest = new PropertyRest();
        propertyRest.setName(property);
        propertyRest.setValues(Arrays.asList(propertyValues));
        return propertyRest;
    }

    private boolean isCurrentUserAdmin(Context context) {
        try {
            return authorizeService.isAdmin(context);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<PropertyRest> findAll(Context context, Pageable pageable) {
        throw new RepositoryMethodNotImplementedException("No implementation found; Method not allowed", "");
    }

    @Override
    public Class<PropertyRest> getDomainClass() {
        return PropertyRest.class;
    }
}
