/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.uniba.orgunittree;

/**
 * The OrgunittreeMetrics to some single orgunittreenode
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
public class OrgunittreeMetrics {

    private Integer value;

    private String shortname;

    private boolean aggregated;

    public String getShortname() {
        return shortname;
    }

    public void setShortname(String shortname) {
        this.shortname = shortname;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public boolean getAggregated() {
        return aggregated;
    }

    public void setAggregated(boolean aggregated) {
        this.aggregated = aggregated;
    }
}
