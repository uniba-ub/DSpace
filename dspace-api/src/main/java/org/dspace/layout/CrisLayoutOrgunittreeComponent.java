/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.layout;

/**
 *
 * Implementation of {@link CrisLayoutSectionComponent} that allows definition
 * of a section containing some trees or orgunits list of counters.
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 *
 */
public class CrisLayoutOrgunittreeComponent implements CrisLayoutSectionComponent {

    private String style = "";

    private String title = "";

    @Override
    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
