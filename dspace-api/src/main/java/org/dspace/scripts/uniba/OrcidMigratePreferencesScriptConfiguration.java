package org.dspace.scripts.uniba;

import org.dspace.scripts.configuration.ScriptConfiguration;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import java.sql.SQLException;

public class OrcidMigratePreferencesScriptConfiguration<T extends OrcidMigratePreferencesScript> extends ScriptConfiguration<T> {


    @Autowired
    private AuthorizeService authorizeService;

    private Class<T> dspaceRunnableClass;

    @Override
    public boolean isAllowedToExecute(Context context) {
        try {
            return authorizeService.isAdmin(context);
        } catch (SQLException e) {
            throw new RuntimeException("SQLException occurred when checking if the current user is an admin", e);
        }
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();

            options.addOption("n", "dryrun", false, "Trigger no update on any metadata value / metadata fields");
            options.getOption("n").setType(boolean.class);
            options.getOption("n").setRequired(false);

            options.addOption("c", "clear metadatafields", false, "Additional delete old source preferences metadata values");
            options.getOption("c").setType(boolean.class);
            options.getOption("c").setRequired(false);

            options.addOption("d", "delete metadatafields", false, "Additional remove old orcid preferences metadata fields");
            options.getOption("d").setType(boolean.class);
            options.getOption("d").setRequired(false);

            options.addOption("a", "append values", false, "Append to existing values. allows repeatable metadatafields");
            options.getOption("a").setType(boolean.class);
            options.getOption("a").setRequired(false);


            super.options = options;
        }
        return options;
    }

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }
}
