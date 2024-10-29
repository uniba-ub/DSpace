package org.dspace.app.rest.health;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;


/**
 * Implementation of {@link HealthIndicator} that verifies if the table sites
 * has exactly one row.
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
public class SiteHealthIndicator extends AbstractHealthIndicator {


    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        throw new NotImplementedException();
    }
}
