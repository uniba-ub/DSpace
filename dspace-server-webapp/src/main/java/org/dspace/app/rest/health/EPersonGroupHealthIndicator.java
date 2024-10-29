package org.dspace.app.rest.health;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Implementation of {@link HealthIndicator} to verify the presence of essential
 * groups in the system, specifically "Anonymous" and "Administrators" in the
 * `epersongroups` table.
 * Marks the status as "UP" if both groups are present, otherwise "DOWN" with
 * details about the missing group(s).
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
public class EPersonGroupHealthIndicator extends AbstractHealthIndicator {


    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        throw new NotImplementedException();
    }
}
