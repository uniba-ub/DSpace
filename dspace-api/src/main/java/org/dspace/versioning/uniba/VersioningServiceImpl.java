/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.versioning.uniba;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.authority.Choices;
import org.dspace.content.logic.DefaultFilter;
import org.dspace.content.logic.LogicalStatement;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Main service for uniba versioning
 * functionalities:
 * creates version
 * switch version
 * get all versions of some item
 * check version strcuture
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
public class VersioningServiceImpl implements VersioningService {

    @Autowired
    protected ConfigurationService configurationService;

    @Autowired
    protected ItemService itemService;

    @Autowired
    protected MetadataValueService metadataValueService;

    @Autowired
    protected MetadataFieldService metadataFieldService;

    @Autowired
    protected WorkspaceItemService workspaceItemService;

    @Autowired
    protected AuthorizeService authorizeService;

    @Autowired
    protected CollectionService collectionService;

    /**
     * Filter if some person can create some new version on the current item
     */
    private DefaultFilter filter;

    /**
     * Filter to check if some version exist for the current item.- normally this is some metadatafield filled
     */
    private DefaultFilter versioncheckFilter;

    /**
    conditions for items/eperson to match to be able to change the versions
    */
    private DefaultFilter canChangeFilter;

    /**
    * List of metadatafields which are on the submission forms but are not copied when a new version is being created.
    */
    private Map<String, List<String>> ignoredMetadataFields;

    /**
    * Name of the metadatafield which holds the versionof relation
    */
    protected Map<String, String> versionoffield;

    /**
    Optional/Additional name of the metadatafield which holds the reciprocal hasversionof relation
    */
    protected Map<String, String> hasversionfield;

    public Map<String, List<String>> getIgnoredMetadataFields() {
        return ignoredMetadataFields;
    }

    public void setIgnoredMetadataFields(Map<String, List<String>> ignoredMetadataFields) {
        this.ignoredMetadataFields = ignoredMetadataFields;
    }

    public String getVersionoffield(Item item) {
        return getVersionOfFieldName(itemService.getEntityType(item));
    }

    public void setVersionoffield(Map<String, String> versionoffield) {
        this.versionoffield = versionoffield;
    }

    public String getHasversionfield(Item item) {
        return getHasVersionFieldName(itemService.getEntityType(item));
    }

    public void setHasversionfield(Map<String, String> hasversionfield) {
        this.hasversionfield = hasversionfield;
    }

    public void setFilter(DefaultFilter filter) {
        this.filter = filter;
    }

    public LogicalStatement getFilter() {
        return this.filter;
    }

    public DefaultFilter getVersioncheckFilter() {
        return versioncheckFilter;
    }

    public void setVersioncheckFilter(DefaultFilter versioncheckFilter) {
        this.versioncheckFilter = versioncheckFilter;
    }

    public DefaultFilter getCanChangeFilter() {
        return canChangeFilter;
    }

    public void setCanChangeFilter(DefaultFilter canChangeFilter) {
        this.canChangeFilter = canChangeFilter;
    }

    @Override
    public WorkspaceItem createNewVersion(Context c, Item item)
        throws SQLException, AuthorizeException {
        if (!item.isArchived() || item.isWithdrawn()) {
            throw new AuthorizeException("version cannot be created from non-archived or withdrawn items");
        }
        // Submit to the collection the version was created
        Collection coll = item.getOwningCollection();
        // Copy all metadata in main collection input for this kind of item
        List<DCInputSet> inputs = null;

        try {
            inputs = new DCInputsReader().getInputsByCollection(item.getOwningCollection());
        } catch (DCInputsReaderException e) {
            e.printStackTrace();
            //error reading the inputs. do not process
        }
        String entitytype = itemService.getEntityType(item);

        WorkspaceItem wi = workspaceItemService.create(c, coll, false);
        for (DCInputSet input : inputs) {
            for (DCInput[] row : input.getFields()) {
                for (DCInput field : row) {
                    //do not copy workflow fields
                    if (!field.isVisible("submit")) {
                        continue;
                    }
                    //Check ignoredMetadataFields. if so, continue;
                    MetadataField mdf =
                        metadataFieldService.findByElement(c, field.getSchema(), field.getElement(),
                            field.getQualifier());
                    if (Objects.isNull(mdf)) {
                        // invalid mdf in submission forms
                        continue;
                    }
                    if (isIgnoredMetadataField(entitytype, mdf)) {
                        continue;
                    }

                    List<MetadataValue> mvexists =
                        itemService.getMetadata(item, field.getSchema(), field.getElement(), field.getQualifier(),
                            Item.ANY);
                    // ignore language of metadata, copy all languages
                    for (MetadataValue mvexist : mvexists) {
                        MetadataValue mvnew = metadataValueService.create(c, wi.getItem(), mdf);
                        mvnew.setValue(mvexist.getValue());
                        mvnew.setAuthority(mvexist.getAuthority());
                        mvnew.setPlace(mvexist.getPlace());
                        mvnew.setConfidence(mvexist.getConfidence());
                        mvnew.setLanguage(mvexist.getLanguage());
                        mvnew.setSecurityLevel((mvexist.getSecurityLevel()));
                    }
                }
            }
        }
        //do not copy bitstream

        // create some reference to version;
        if (Objects.nonNull(getVersionOfFieldName(item))) {
            MetadataField versionmdf = metadataFieldService.findByString(c, getVersionOfFieldName(item), '.');
            MetadataValue mvnew = metadataValueService.create(c, wi.getItem(), versionmdf);
            mvnew.setValue(item.getHandle());
            mvnew.setAuthority(UUIDUtils.toString(item.getID()));
            mvnew.setConfidence(Choices.CF_ACCEPTED);
        }

        // return the newly created WorkspaceItem or null
        return wi;
    }

    /**
     * check the type filter, whether the conditions are fulfilled to create new versions
     * @param c
     * @param item
     * @return
     */
    public boolean checkItemConditionsAreFulfilled(Context c, Item item) {
        // Check entitytype and subtype
        if (!item.isArchived() || item.isWithdrawn()) {
            return false;
        }
        if (Objects.nonNull(getFilter())) {
            return getFilter().getResult(c, item);
        }
        //Check if Person can submit
        return true;
    }

    /**
     * check the version filter, whether some version of this item exist
     * @param c
     * @param item
     * @return
     */
    protected boolean checkVersion(Context c, Item item) {
        if (Objects.nonNull(getVersioncheckFilter())) {
            return getVersioncheckFilter().getResult(c, item);
        }
        return true;
    }

    @Override
    public List<Item> getVersion(Context c, Item item) throws SQLException, AuthorizeException {
        // Check references. Some other way would be to check the reciprocal hasVersion field, but this is only optional
        Iterator<Item> items =
            itemService.findByMetadataFieldAuthority(c, getVersionoffield(item), item.getID().toString());
        List<Item> list = new ArrayList<>();
        while (items.hasNext()) {
            list.add(items.next());
        }
        return list;
    }

    @Override
    public List<Item> getVersionGroupMember(Context context, Item item)
        throws SQLException, AuthorizeException, VersioningStructureException {
        return getVersionGroupMember(context, item, false);
    }

    @Override
    public List<Item> getVersionGroupMember(Context context, Item item, boolean ignorevalidation)
        throws SQLException, AuthorizeException, VersioningStructureException {
        List<Item> list = new ArrayList<>();
        if (!ignorevalidation) {
            List<VersioningStructureError> errors = checkVersionStructure(context, item);
            if (!errors.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                errors.forEach(versioningStructureError -> sb.append(versioningStructureError.toString()));
                throw new VersioningStructureException(sb.toString());
            }
        }
        if (checkItemIsVersionOf(context, item)) {
            List<MetadataValue> mvs = itemService.getMetadataByMetadataString(item, getVersionOfFieldName(item));
            for (MetadataValue mv : mvs) {
                if (StringUtils.isBlank(mv.getAuthority()) || Objects.isNull(UUIDUtils.fromString(mv.getAuthority()))) {
                    continue; //invalid reference, perhaps old migration or workspaceitem
                }
                Item relitem = itemService.find(context, UUIDUtils.fromString(mv.getAuthority()));
                if (Objects.isNull(relitem)) {
                    continue;
                }
                list.add(relitem);
                // get all other childs pointing to this item. This should include item itself
                Iterator<Item> relitems =
                    itemService.findByMetadataFieldAuthority(context, getVersionoffield(item),
                        relitem.getID().toString());
                while (relitems.hasNext()) {
                    list.add(relitems.next());
                }
            }
        } else {
            // if the item itself is the mainversion, then it's added to the beginning of the list
            boolean relitemexist = false;
            Iterator<Item> relitems =
                itemService.findByMetadataFieldAuthority(context, getVersionoffield(item), item.getID().toString());
            while (relitems.hasNext()) {
                Item relitem = relitems.next();
                relitemexist = true;
                if (Objects.nonNull(relitem)) {
                    list.add(relitem);
                }
            }
            if (relitemexist) {
                list.add(0, item);
            }
        }
        return list;
    }

    @Override
    public boolean canCreateNewVersion(Context c, Item item) {
        // two fields, because the version check might be extended in future.
        return checkItemConditionsAreFulfilled(c, item) && checkVersion(c, item);
    }

    @Override
    public boolean canChangeMainVersion(Context c) {
        // No need to check the item, only the user
        if (Objects.isNull(c.getCurrentUser())) {
            return false;
        }

        //simple check of groups of the current user
        if (Objects.isNull(this.getCanChangeFilter())) {
            return false;
        }
        return canChangeFilter.getResult(c, null);
    }

    @Override
    public String getVersionOfFieldName(Item item) {
        return this.getVersionoffield(item);
    }

    @Override
    public String getHasVersionFieldName(Item item) {
        return this.getHasversionfield(item);
    }

    @Override
    public String getVersionOfFieldName(String entitytype) {
        if (versionoffield.containsKey(entitytype)) {
            return versionoffield.get(entitytype);
        } else if (versionoffield.containsKey("default")) {
            return versionoffield.get("default");
        }
        return null;
    }

    @Override
    public String getHasVersionFieldName(String entitytype) {
        if (hasversionfield.containsKey(entitytype)) {
            return hasversionfield.get(entitytype);
        } else if (hasversionfield.containsKey("default")) {
            return hasversionfield.get("default");
        }
        return null;
    }

    @Override
    public Item changeMainVersion(Context context, Item item) throws AuthorizeException, VersioningStructureException {
        // First check item throws AuthenticateException
        if (!item.isArchived() || item.isWithdrawn()) {
            throw new AuthorizeException();
        }
        // has the user the right to change the family? (checks the user only
        if (!this.canChangeMainVersion(context)) {
            throw new AuthorizeException();
        }

        // check structure
        List<VersioningStructureError> errors = checkVersionStructure(context, item);
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            errors.forEach(versioningStructureError -> sb.append(versioningStructureError.toString()));
            throw new VersioningStructureException(sb.toString());
        }
        List<Item> versionitems = null;
        try {
            versionitems = getVersionGroupMember(context, item);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (Objects.nonNull(versionitems) && !versionitems.isEmpty() ) {
            if (checkItemIsVersionOf(context, item)) {
                // is this item versionof -> ok, than change references to this item
                // on every member of the group except the current member.
                try {
                    MetadataField hasversionfield = (StringUtils.isNotBlank(getHasversionfield(item))) ?
                        this.metadataFieldService.findByString(context, getHasVersionFieldName(item), '.') : null;
                    MetadataField versionoffield =
                        this.metadataFieldService.findByString(context, getVersionoffield(item), '.');
                    // clear all references
                    for (Item versionitem: versionitems) {
                        // clear references. This is not done by the reciprocal consumer automatically
                        if (Objects.nonNull(hasversionfield)) {
                            itemService.clearMetadata(context, versionitem,
                                    hasversionfield.getMetadataSchema().getName(), hasversionfield.getElement(),
                                    hasversionfield.getQualifier(), Item.ANY);
                        }
                        itemService.clearMetadata(context, versionitem,
                                versionoffield.getMetadataSchema().getName(), versionoffield.getElement(),
                                versionoffield.getQualifier(), Item.ANY);
                    }
                    // create metadatavalues
                    for (Item versionitem: versionitems) {
                        // update all references to the new item except the item referencing itself.
                        // Item A,C > versionof -> Item B
                        if (!(UUIDUtils.toString(versionitem.getID())
                            .contentEquals(UUIDUtils.toString(item.getID())))) {
                            itemService.addMetadata(context, versionitem, versionoffield, null, item.getHandle(),
                                UUIDUtils.toString(item.getID()), Choices.CF_ACCEPTED);
                        }
                        /*
                         // The hasversion fields are set by the reciprocalitemAuthority consumer
                         if (!(UUIDUtils.toString(versionitem.getID()).contentEquals(UUIDUtils.toString(item.getID())))
                         && Objects.nonNull(hasversionfield)) {
                         // add hasversion relation to new item. Hoping enhancer won't set new values etc...
                         Item B -> hasversion -> Item A,C
                           itemService.addMetadata(context, item, hasversionfield, null, versionitem.getHandle(),
                           UUIDUtils.toString(versionitem.getID()), Choices.CF_ACCEPTED);
                        }
                        */

                        // Update every version item, so the metadata enhancers are called correctly
                        // for each items (discovery etc...)
                        itemService.update(context, versionitem);

                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }

            } else {
                    // it's already the main version, no update necessary
                throw new
                    VersioningStructureException("No need to update necessary. The item is alrady the main version");
            }
        } else {
            // invalid version group
            throw new VersioningStructureException("Invalid group. No group members found. Cannot update");
        }
        return null;
    }

    // Check if this specific item is in a valid structure
    @Override
    public List<VersioningStructureError> checkVersionStructure(Context context, Item item) {
        //check whether hasVersion is actually set
        List<VersioningStructureError> errors = new ArrayList<>();
        if (StringUtils.isBlank(this.getHasVersionFieldName(item))) {
            /*
             * check the metadata and references of this item and than of parents and childs
             */
            if (checkItemIsVersionOf(context, item)) {
                // structure check throws VersioningStructureException
                // There are two ways to check the structure. Rely on the reciprocal enhancer.
                // Or check relations and authority values using the itemService
                if (checkItemReferencesItself(context, item, getVersionOfFieldName(item))) {
                    errors.add(new VersioningStructureError(item, "A1", "Item point to itself"));
                }

                //This item is version of multiple items -> Error
                if (checkItemIsVersionOfMultiple(context, item)) {
                    errors.add(new VersioningStructureError(item, "A2", "This item is version of multiple items!"));
                }

                //
                try {
                    //This item is version of and has other versions pointing to this version -> Error
                    if (checkItemHasVersionsReferencedAuthority(context, item)) {
                        errors.add(
                            new VersioningStructureError(item, "A3",
                                "This item is version of some item but also has childs!"));
                    }
                } catch (SQLException | AuthorizeException e) {
                    //
                }
                //check authority value to main version
                if (checkItemIsVersionMetadataFieldHasInvalidReference(context, item,
                    this.getVersionOfFieldName(item))) {
                    errors.add(
                        new VersioningStructureError(item, "A4", "This item is version of some unresolvable item"));
                }
            }
            // if the item is not version of and has no references, than there is no group
            try {
                if (!checkItemIsVersionOf(context, item) && !checkItemHasVersionsReferencedAuthority(context, item)) {
                    errors.add(new VersioningStructureError(item, "A5", "There are no other members of this group"));
                }
            } catch (SQLException | AuthorizeException e) {
                //
            }
            // The item itself has no childs
            // check mainversion and all other childs pointing to main version
            List<MetadataValue> mvs =
                this.itemService.getMetadataByMetadataString(item, this.getVersionOfFieldName(item));
            for (MetadataValue mv : mvs) {
                Item rel = null;
                try {
                    rel = itemService.find(context, UUIDUtils.fromString(mv.getAuthority()));
                } catch (SQLException e) {
                 //
                }
                if (checkItemIsVersionOf(context, rel)) {
                    errors.add(new VersioningStructureError(item, "A6",
                        "The main version of this item is itself some version of another item", rel));
                }

                // Now check all other items (siblings) and their structures
                Iterator<Item> siblings = null;
                try {
                    siblings = itemService.findByMetadataFieldAuthority(context, getVersionoffield(item),
                            rel.getID().toString());
                } catch (SQLException | AuthorizeException e) {
                    //
                }
                while (siblings.hasNext()) {
                    Item sibling = siblings.next();
                    if (UUIDUtils.toString(sibling.getID()).contentEquals(UUIDUtils.toString(item.getID()))) {
                        continue; //this is the item
                    }
                    if (checkItemIsVersionOfMultiple(context, sibling)) {
                        errors.add(
                            new VersioningStructureError(item, "A7",
                                "Some sibling of this item is version of multiple items!",
                                sibling));
                    }
                    try {
                        if (checkItemHasVersionsReferencedAuthority(context, sibling)) {
                            errors.add(new VersioningStructureError(item, "A8",
                                "Some sibling of this item has versions", sibling));
                        }
                    } catch (SQLException | AuthorizeException e) {
                        //
                    }
                }
            }
        } else {
            /*
             * also check hasversion metadatafields
             * this check is more complex, since we have to check more fields.
             */

            // structure check throws VersioningStructureException
            // There are two ways to check the structure. Rely on the reciprocal enhancer.
            // Or check relations and authority values using the itemService
            if ((checkItemReferencesItself(context, item, getVersionOfFieldName(item)) ||
                checkItemReferencesItself(context, item, getHasVersionFieldName(item)))) {
                errors.add(new VersioningStructureError(item, "B1","Item point to itself"));
            }

            if (checkItemIsVersionOf(context, item)) {
                //This item is version of multiple items -> Error
                if (checkItemIsVersionOfMultiple(context, item)) {
                    errors.add(new VersioningStructureError(item, "B2", "This item is version of multiple items!"));
                }
                //This item is version of and has other versions pointing to this version -> Error
                if (checkItemHasVersionOf(context, item)) {
                    errors.add(new VersioningStructureError(item,"B3", "This item has versions and is version of."));
                }

                try {
                    if (checkItemIsVersionOf(context, item) && checkItemHasVersionsReferencedAuthority(context, item)) {
                        errors.add(new VersioningStructureError(item, "B4",
                            "This item is version of and hasversions!"));
                    }
                } catch (SQLException | AuthorizeException e) {
                    //
                }

                if (checkItemIsVersionMetadataFieldHasInvalidReference(context, item,
                    this.getVersionOfFieldName(item))) {
                    errors.add(new VersioningStructureError(item, "B5",
                        "This item is version of some unresolvable item"));
                }
            }
            //

            //check authority value to main version
            try {
                if (!checkItemIsVersionOf(context, item) && (!checkItemHasVersionOf(context, item) ||
                    !checkItemHasVersionsReferencedAuthority(context, item))) {
                    errors.add(new VersioningStructureError(item, "B6", "There are no other members of this group"));
                }
            } catch (SQLException | AuthorizeException e) {
                //
            }

            //Check if the reciprocal metadatavalues are correctly set
            try {
                checkItemReferencedAuthorityMatchesMetadataValues(context, item, getHasversionfield(item),
                    getVersionoffield(item));
                checkItemReferencedAuthorityMatchesMetadataValues(context, item, getVersionoffield(item),
                    getHasversionfield(item));
            } catch (SQLException | AuthorizeException e) {
                //e.printStackTrace();
            } catch (VersioningStructureException e) {
                errors.add(new VersioningStructureError(item, "B8", "reciprocal metadatavalues do not match"));
            }

            if (checkItemHasVersionOf(context, item) && checkItemIsVersionMetadataFieldHasInvalidReference(
                context, item, this.getHasVersionFieldName(item))) {
                errors.add(new VersioningStructureError(item, "B9", "This item has version of some unresolvable item"));
            }

            // The item itself must be some child
            // check mainversion and all other childs pointing to main version
            List<MetadataValue> mvs =
                this.itemService.getMetadataByMetadataString(item, this.getVersionOfFieldName(item));
            for (MetadataValue mv : mvs) {
                Item rel = null;
                try {
                    rel = itemService.find(context, UUIDUtils.fromString(mv.getAuthority()));
                } catch (SQLException e) {
                    //
                }
                if (checkItemIsVersionOf(context, rel)) {
                    errors.add(
                        new VersioningStructureError(item, "B10",
                            "The main version of this item is itself some version of another item", rel));
                }

                // Now check all other items (siblings) and their structures
                Iterator<Item> siblings = null;
                try {
                    siblings = itemService.findByMetadataFieldAuthority(context, getVersionoffield(item),
                        rel.getID().toString());
                } catch (SQLException | AuthorizeException e) {
                    e.printStackTrace();
                }
                while (siblings.hasNext()) {
                    Item sibling = siblings.next();
                    if (UUIDUtils.toString(sibling.getID()).contentEquals(UUIDUtils.toString(item.getID()))) {
                        continue; //this is the item
                    }
                    if (checkItemIsVersionOfMultiple(context, sibling)) {
                        errors.add(new VersioningStructureError(item, "B11",
                            "Some sibling of this item is version of multiple items!", sibling));
                    }
                    try {
                        if (checkItemHasVersionsReferencedAuthority(context, sibling)) {
                            errors.add(new VersioningStructureError(item, "B12",
                                "Some sibling of this item has versions", sibling));
                        }
                    } catch (SQLException | AuthorizeException e) {
                        //
                    }
                }
            }
            //check hasversions
            List<MetadataValue> mvs2 =
                this.itemService.getMetadataByMetadataString(item, this.getHasVersionFieldName(item));
            for (MetadataValue mv : mvs2) {
                Item rel = null;
                try {
                    rel = itemService.find(context, UUIDUtils.fromString(mv.getAuthority()));
                } catch (SQLException e) {
                    //
                }
                if (checkItemHasVersionOf(context, rel)) {
                    errors.add(new VersioningStructureError(item,"B13",
                        "Some version of this item itself has versions", rel));
                }

                // Now check all other items (siblings) and their structures
                Iterator<Item> siblings = null;
                try {
                    siblings = itemService.findByMetadataFieldAuthority(context, getHasVersionFieldName(item),
                        rel.getID().toString());
                } catch (AuthorizeException | SQLException e) {
                    e.printStackTrace();
                }
                while (siblings.hasNext()) {
                    Item sibling = siblings.next();
                    if (UUIDUtils.toString(sibling.getID()).contentEquals(UUIDUtils.toString(item.getID()))) {
                        continue; //this is the item
                    }
                    // if (checkItemIsVersionOfMultiple(context, sibling))
                    // throw new VersioningStructureException("
                    // Some sibling of this item is version of multiple items!");
                    try {
                        if (checkItemHasVersionsReferencedAuthority(context, sibling)) {
                            errors.add(new VersioningStructureError(item, "B14",
                                "Some sibling of this item has versions", sibling));
                        }
                    } catch (AuthorizeException | SQLException e) {
                        //
                    }
                }
            }
        }
        return errors;
    }

    /**
     * Check if the Item is some Version of
     * @param context
     * @param item
     * @return
     */
    boolean checkItemIsVersionOf(Context context, Item item) {
        List<MetadataValue> mvs = this.itemService.getMetadataByMetadataString(item, this.getVersionOfFieldName(item));
        return Objects.nonNull(mvs) && mvs.size() > 0;
    }

    /**
     * Check if the item is some version of multiple other items
     * @param context
     * @param item
     * @return
     */
    boolean checkItemIsVersionOfMultiple(Context context, Item item) {
        List<MetadataValue> mvs = this.itemService.getMetadataByMetadataString(item, this.getVersionOfFieldName(item));
        return Objects.nonNull(mvs) && mvs.size() > 1;
    }

    /**
     * Check if the item is some version of other items
     * @param context
     * @param item
     * @return
     */
    boolean checkItemHasVersionOf(Context context, Item item) {
        List<MetadataValue> mvs = this.itemService.getMetadataByMetadataString(item, this.getHasVersionFieldName(item));
        return Objects.nonNull(mvs) && !mvs.isEmpty();
    }

    /**
     * Checks if the item references itself in the versionof relation
     * @param context
     * @param item
     * @return
     */
    boolean checkItemReferencesItself(Context context, Item item, String field) {
        return this.itemService.getMetadataByMetadataString(item, this.getVersionOfFieldName(item)).
            stream().anyMatch(metadataValue ->
                UUIDUtils.toString(item.getID()).contentEquals(metadataValue.getAuthority()));
    }

    /**
     * Checks if the item has metadata referencing to this item
     * @param context
     * @param item
     * @return
     */
    boolean checkItemHasVersionsReferencedAuthority(Context context, Item item)
        throws SQLException, AuthorizeException {
        Iterator<Item> rels = itemService.findByMetadataFieldAuthority(context, getVersionoffield(item),
            item.getID().toString());
        return rels.hasNext();
    }

    /**
     * Compare the metadatavalues and references to this item in the reciprocal field
     * @param context
     * @param item
     * @return
     * @throws SQLException
     * @throws AuthorizeException
     */
    boolean checkItemReferencedAuthorityMatchesMetadataValues(
        Context context, Item item, String fieldnameA, String fieldnameB)
        throws SQLException, AuthorizeException, VersioningStructureException {
        List<Item> rellist = new ArrayList<>();
        List<MetadataValue> metadatavaluelist = itemService.getMetadataByMetadataString(item, fieldnameA);
        Iterator<Item> rels = itemService.findByMetadataFieldAuthority(context, fieldnameB, item.getID().toString());
        while (rels.hasNext()) {
            rellist.add(rels.next());
        }
        //now compare the values of each other
        for (MetadataValue mv : metadatavaluelist) {
            if (rellist.stream().noneMatch(item1 ->
                UUIDUtils.toString(item1.getID()).contentEquals(mv.getAuthority()))) {
                throw new VersioningStructureException("Item " + UUIDUtils.toString(item.getID()) +
                    " in metadatafield " + fieldnameA + " with Metadatavalue " + mv.getID() +
                    " references Item" + mv.getAuthority() + ", but the referenced item has no reciprocal metadata");
            }
        }
        for (Item rel : rellist) {
            if (metadatavaluelist.stream().noneMatch(mv1 ->
                UUIDUtils.toString(rel.getID()).contentEquals(mv1.getAuthority()))) {
                throw new VersioningStructureException("Item " + UUIDUtils.toString(rel.getID()) +
                    " in metadatafield " + fieldnameB +
                    " references " + item.getID() + ", but the referenced item has no reciprocal metadata");
            }
        }
        return true;
    }

    /**
     * Check if the item reference versionof has some invalid reference
     * @param context
     * @param item
     * @return
     */
    boolean checkItemIsVersionMetadataFieldHasInvalidReference(Context context, Item item, String fieldname) {
        List<MetadataValue> mvs = this.itemService.getMetadataByMetadataString(item, fieldname);
        for (MetadataValue mv : mvs) {
            try {
                if (Objects.isNull(itemService.find(context, UUIDUtils.fromString(mv.getAuthority())))) {
                    return true;
                }
            } catch (Exception e) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the metadatafield for the given entity should be ignored to being copied.
     * @param entity
     * @param mdf
     * @return boolean true if the metadatafield is ignored
     */
    protected boolean isIgnoredMetadataField(String entity, MetadataField mdf) {
        if (!getIgnoredMetadataFields().containsKey(entity)) {
            // No ignoredMetadataFields fields for this entity
            return false;
        }
        // check field, e.g. dc.title.alternative in list
        if (getIgnoredMetadataFields().get(entity).stream().noneMatch(s ->
            s.contentEquals(mdf.getMetadataSchema().getName() + '.' + mdf.getElement() + '.' + mdf.getQualifier()))) {
            // if no match, then check qualifier wildcard in list, e.g. dc.title.*
            if (getIgnoredMetadataFields().get(entity).stream().anyMatch(s ->
                s.contentEquals(mdf.getMetadataSchema().getName() + '.' + mdf.getElement() + ".*"))) {
                return true;
            }
            return false;
        } else {
            return true;
        }
    }
}

