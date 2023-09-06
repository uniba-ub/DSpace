/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.uniba.orgunittree;

import java.util.ArrayList;
import java.util.List;

/**
 * The OrgUnitTreeNode Metrics configuration settings
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 *
 */
public class OrgunittreeMetricsConfiguration {

    public OrgunittreeMetricsConfiguration() {
        this.filterquery = new ArrayList<>();
    }
    private String shortname;
    private boolean aggregate = false;
    private String query;
    private List<String> filterquery;

    public void setShortname(String shortname) {
        this.shortname = shortname;
    }
    public String getShortname() {
        return this.shortname;
    }

    public void setQuery(String query) {
        this.query = query;
    }
    public String getQuery() {
        return this.query;
    }
    public void setAggregate(boolean aggregate) {
        this.aggregate = aggregate;
    }
    public boolean isAggregate() {
        return this.aggregate;
    }
    public void setFilterquery(List<String> filterquery) {
        this.filterquery = filterquery;
    }
    public List<String> getFilterquery() {
        return this.filterquery;
    }
}
