/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

public class OAIREPublicationLoaderScriptConfiguration<T extends OAIREPublicationLoaderRunnable>
    extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    /**
     * Generic setter for the dspaceRunnableClass
     * @param dspaceRunnableClass   The dspaceRunnableClass to be set on this OAIREPublicationLoaderScriptConfiguration
     */
    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }


    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();

            options.addOption("s", "single-researcher", true, "Single researcher UUID");
            options.getOption("s").setType(String.class);

            super.options = options;
        }
        return options;
    }

}
