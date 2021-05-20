/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.orcid.service;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.dspace.app.orcid.model.OrcidEntityType;
import org.dspace.app.orcid.model.OrcidTokenResponseDTO;
import org.dspace.app.profile.OrcidEntitySyncPreference;
import org.dspace.app.profile.OrcidProfileDisconnectionMode;
import org.dspace.app.profile.OrcidProfileSyncPreference;
import org.dspace.app.profile.OrcidSynchronizationMode;
import org.dspace.content.Item;
import org.dspace.core.Context;

/**
 * Service that handle the the syncronization between a DSpace profile and the
 * relative ORCID profile, if any.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public interface OrcidSynchronizationService {

    /**
     * Check if the given item is linked to an ORCID profile.
     *
     * @param  item the item to check
     * @return      true if the given item is linked to ORCID
     */
    boolean isLinkedToOrcid(Item item);

    /**
     * Configure the given profile with the data present in the given ORCID token.
     * This action is required to synchronize profile and related entities with
     * ORCID.
     *
     * @param  context      the relevant DSpace Context.
     * @param  profile      the profile to configure
     * @param  token        the ORCID token
     * @throws SQLException if a SQL error occurs during the profile update
     */
    public void linkProfile(Context context, Item profile, OrcidTokenResponseDTO token) throws SQLException;

    /**
     * Disconnect the given profile from ORCID.
     *
     * @param  context      the relevant DSpace Context.
     * @param  profile      the profile to disconnect
     * @throws SQLException if a SQL error occurs during the profile update
     */
    public void unlinkProfile(Context context, Item profile) throws SQLException;

    /**
     * Set the synchronization preference for the given profile related to the given
     * ORCID entity type.
     *
     * @param  context                  the relevant DSpace Context.
     * @param  profile                  the researcher profile to update
     * @param  entityType               the orcid entity type
     * @param  value                    the new synchronization preference value
     * @throws SQLException             if a SQL error occurs during the profile
     *                                  update
     * @throws IllegalArgumentException if the given researcher profile is no linked
     *                                  with an ORCID account
     */
    public void setEntityPreference(Context context, Item profile, OrcidEntityType entityType,
        OrcidEntitySyncPreference value) throws SQLException;

    /**
     * Update the profile's synchronization preference for the given profile.
     *
     * @param  context                  the relevant DSpace Context.
     * @param  profile                  the researcher profile to update
     * @param  value                    the new synchronization preference value
     * @throws SQLException             if a SQL error occurs during the profile
     *                                  update
     * @throws IllegalArgumentException if the given researcher profile is no linked
     *                                  with an ORCID account
     */
    public void setProfilePreference(Context context, Item profile,
        List<OrcidProfileSyncPreference> values) throws SQLException;

    /**
     * Set the ORCID synchronization mode for the given profile.
     *
     * @param  context      the relevant DSpace Context.
     * @param  profile      the researcher profile to update
     * @param  value        the new synchronization mode value
     * @throws SQLException if a SQL error occurs during the profile update
     */
    public void setSynchronizationMode(Context context, Item profile, OrcidSynchronizationMode value)
        throws SQLException;

    /**
     * Check if the given researcher profile item is configured to synchronize the
     * given item with ORCID.
     *
     * @param  profile the researcher profile item
     * @param  item    the entity type to check
     * @return         true if the given entity type can be synchronize with ORCID,
     *                 false otherwise
     */
    public boolean isSynchronizationEnabled(Item profile, Item item);

    /**
     * Returns the ORCID synchronization mode configured for the given profile item.
     *
     * @param  profile the researcher profile item
     * @return         the synchronization mode
     */
    Optional<OrcidSynchronizationMode> getSynchronizationMode(Item profile);

    /**
     * Returns the ORCID synchronization preference related to the given entity type
     * configured for the given profile item.
     *
     * @param  profile    the researcher profile item
     * @param  entityType the orcid entity type
     * @return            the configured preference
     */
    Optional<OrcidEntitySyncPreference> getEntityPreference(Item profile, OrcidEntityType entityType);

    /**
     * Returns the ORCID synchronization preferences related to the profile itself
     * configured for the given profile item.
     *
     * @param  profile the researcher profile item
     * @return         the synchronization mode
     */
    List<OrcidProfileSyncPreference> getProfilePreferences(Item profile);

    /**
     * Returns the configuration ORCID profile's disconnection mode. If that mode is
     * not configured or the configuration is wrong, the value DISABLED is returned.
     *
     * @return the disconnection mode
     */
    OrcidProfileDisconnectionMode getDisconnectionMode();

    /**
     * Returns all the profiles with the given orcid id.
     *
     * @param  context the relevant DSpace Context.
     * @param  orcid   the orcid id to search for
     * @return         an iterator over the found profile items
     */
    Iterator<Item> findProfilesByOrcid(Context context, String orcid);

    /**
     * Returns all the profiles that has an orcid id.
     *
     * @param  context the relevant DSpace Context.
     * @return         an iterator over the found profile items
     */
    Iterator<Item> findProfilesWithOrcid(Context context);
}