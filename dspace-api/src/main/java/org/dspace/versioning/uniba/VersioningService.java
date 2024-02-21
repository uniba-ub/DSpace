/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.versioning.uniba;

import java.sql.SQLException;
import java.util.List;

import org.dspace.app.util.DCInputsReaderException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Context;

/**
 * Interface vor uniba versioning
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
public interface VersioningService {

    /**
     * Create some new version if the specified item
     * @param c
     * @param itemId
     * @return
     * @throws SQLException
     * @throws DCInputsReaderException
     * @throws AuthorizeException
     */
    WorkspaceItem createNewVersion(Context c, Item itemId)
        throws SQLException, DCInputsReaderException, AuthorizeException;

    /**
     * Get Versions using the iterator and the main version field
     * @param c
     * @param itemId
     * @return
     * @throws SQLException
     * @throws AuthorizeException
     */
    List<Item> getVersion(Context c, Item itemId) throws SQLException, AuthorizeException;

    /**
     * Get all items of the version group where the item is member in including the specified item.
     * If no version group exist than an empty list is returned.
     * @param c
     * @param itemId
     * @return
     * @throws SQLException
     * @throws AuthorizeException
     */
    List<Item> getVersionGroupMember(Context c, Item itemId)
        throws SQLException, AuthorizeException, VersioningStructureException;

    /**
     * Get all items of the version group where the item is member in including the specified item.
     * Ignore validation of group
     * If no version group exist than an empty list is returned.
     * @param c
     * @param itemId
     * @param ignorevalidation
     * @return
     * @throws SQLException
     * @throws AuthorizeException
     */
    List<Item> getVersionGroupMember(Context c, Item itemId, boolean ignorevalidation)
        throws SQLException, AuthorizeException, VersioningStructureException;

    /***
     * Checks whether the conditions are fulfilled to create a new version of the given item
     * @param c
     * @param itemId
     * @return
     */
    boolean canCreateNewVersion(Context c, Item itemId);

    /***
     * Checks whether the conditions are fulfilled for the current user to change the main version
     * (=the public findable item) of some version group.
     * @param c
     * @return
         */
    boolean canChangeMainVersion(Context c);

    /***
     * Returns the name of the versionof metadatafield for the item
     * @param item
     * @return
     */
    String getVersionOfFieldName(Item item);

    /***
     * Returns the name of the hasversion metadatafield for the item
     * @param item
     * @return
     */
    String getHasVersionFieldName(Item item);

    /***
     * Returns the name of the versionof metadatafield for the entitytype
     * @return
     */
    String getVersionOfFieldName(String entitytype);

    /***
     * Returns the name of the hasversion metadatafield for the entitytype
     * @return
     */
    String getHasVersionFieldName(String stringentitytype);

    /**
     * Sets the current object as the main version of the version group/family.
     * @param context
     * @param item
     * @return
     */
    Item changeMainVersion(Context context, Item item) throws AuthorizeException, VersioningStructureException ;

    /**
     * Check the structure of the version group of some item
     * @param context
     * @param item
     * return list of errors
     */
    List<VersioningStructureError> checkVersionStructure(Context context, Item item);

}
