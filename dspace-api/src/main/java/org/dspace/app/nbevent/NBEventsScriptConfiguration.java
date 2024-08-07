/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.nbevent;

import java.io.InputStream;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

public class NBEventsScriptConfiguration<T extends NBEventsRunnable> extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    /**
     * Generic setter for the dspaceRunnableClass
     * @param dspaceRunnableClass   The dspaceRunnableClass to be set on this NBEventsScriptConfiguration
     */
    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();

            options.addOption("f", "file", true, "Import data from OpenAIRE notification broker files");
            options.getOption("f").setType(InputStream.class);
            options.getOption("f").setRequired(true);

            super.options = options;
        }
        return options;
    }

}
