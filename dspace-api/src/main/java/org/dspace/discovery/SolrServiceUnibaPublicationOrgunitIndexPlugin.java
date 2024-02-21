/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.CrisConstants;
import org.dspace.discovery.indexobject.IndexableItem;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Dependend on existence of metadata (originfield) for certain entitiesa (originentity) follow hierarchical
 * structures upward to the root entity.
 * The hierarchy is defined by some metadatafield (targethierarchyfield) which
 * can be specified for certain entity types (targetentity). The indexer follows the hierarchy upwards to the root level
 * organization and saved the processed uuid's in some Set. The check for the visibility and discoverability of the
 * items can be disabled (targetonlyvisible).
 * Some optional sucessor (targetsuccessorfield) can be
 * specified which is also considered in the hierarchy and points to the (in tim) following or succeeding OrgUnit of
 * some OrgUnit.
 * The uuids/authority are saved in the indexfield for each document. Beware that changes in the hierarchy are not
 * automatically reflected in the Indexer and that some full index-discovery is necessary.
 * Example use case: Show Publication related to Orgunit and their Part-Orgunit's and their respective Successors
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
public class SolrServiceUnibaPublicationOrgunitIndexPlugin implements SolrServiceIndexPlugin {

    private static final Logger log = LogManager.getLogger(SolrServiceUnibaPublicationOrgunitIndexPlugin.class);

    @Autowired
    protected ItemService itemService;
    private List<String> originentity;
    private List<String> originfield;
    private String indexfield;
    private String targetentity;
    private boolean targetonlyvisible;
    private String targethierarchyfield;
    private String targetsuccessorfield;
    public void setOriginentity(List<String> val) {
        this.originentity = val;
    }
    private List<String> getOriginentity() {
        return this.originentity;
    }
    public void setOriginfield(List<String> val) {
        this.originfield = val;
    }
    private List<String> getOriginfield() {
        return this.originfield;
    }
    public void setIndexfield(String val) {
        this.indexfield = val;
    }
    private String getIndexfield() {
        return this.indexfield;
    }
    public void setTargetentity(String val) {
        this.targetentity = val;
    }
    private String getTargetentity() {
        return this.targetentity;
    }
    public void setTargetonlyvisible(boolean val) {
        this.targetonlyvisible = val;
    }
    private boolean getTargetonlyvisible() {
        return this.targetonlyvisible;
    }
    public void setTargethierarchyfield(String val) {
        this.targethierarchyfield = val;
    }
    private String getTargethierarchyfield() {
        return this.targethierarchyfield;
    }
    public void setTargetsuccessorfield(String val) {
        this.targetsuccessorfield = val;
    }
    private String getTargetsuccessorfield() {
        return this.targetsuccessorfield;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void additionalIndex(Context context, IndexableObject idxObj, SolrInputDocument document) {
        if (idxObj instanceof IndexableItem) {
            // Check the conditions
            if (getOriginentity() == null || getOriginentity().isEmpty() || StringUtils.isBlank(getIndexfield())
                || getOriginfield() == null || getOriginfield().isEmpty()) {
                // Not enough configuration settings to check
                return;
            }
            Item item = ((IndexableItem) idxObj).getIndexedObject();
            if (Objects.nonNull(item)) {

                if (!(item.isArchived() || item.isDiscoverable()) || item.isWithdrawn()) {
                    return;
                }
                // Set of processed uuids written to index field
                Set<UUID> uuids = new HashSet<>();
                // set of uuid of metadata with authorities on the origin item
                Set<UUID> tocheck = new HashSet<>();
                try {
                    String type = itemService.getEntityType(item);
                    if (StringUtils.isNotBlank(type) && getOriginentity().contains(type)) {
                        for (String originfield : getOriginfield()) {
                            List<MetadataValue> val = itemService.getMetadataByMetadataString(item, originfield);
                            if (!val.isEmpty()) {
                                //Could be simplified
                                for (MetadataValue md : val) {
                                    if (StringUtils.isNotBlank(md.getValue())
                                        && !md.getValue().contentEquals(CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE)
                                        && StringUtils.isNotBlank(md.getAuthority())) {
                                        Item parent = itemService.find(context, UUID.fromString(md.getAuthority()));
                                        tocheck.add(parent.getID());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
                if (!tocheck.isEmpty()) {
                    //already checked uuids
                    Set<UUID> checked = new HashSet<>();
                    //uuids to check in next round
                    //start with values above
                    int found = tocheck.size();

                    // Avoid concurrent Modifications problems while
                    // adding/removing items during iterating by using temporary set
                    // process while until no new items where found to be added to uuids
                    Set<UUID> temp = new HashSet<>();
                    while (found > 0) {
                        found = 0;
                        try {
                            for (UUID actual : tocheck) {
                                Item parentitem = itemService.find(context, actual);
                                if (checked.contains(actual)) {
                                    continue; //already checked
                                }
                                if (Objects.nonNull(parentitem)) {
                                    if (!getTargetonlyvisible() || (parentitem.isArchived()
                                        && parentitem.isDiscoverable()
                                        && !parentitem.isWithdrawn())) {
                                        String typeparent = itemService.getEntityType(parentitem);
                                        if (StringUtils.isNotBlank(typeparent)
                                            && getTargetentity().contentEquals(typeparent)) {
                                            // Follow Hierarchy upwards
                                            if (StringUtils.isNotBlank(getTargethierarchyfield())) {
                                                // follow target field to get the parent of this item, if it exists
                                                List<MetadataValue> val =
                                                    itemService.getMetadataByMetadataString(parentitem,
                                                        getTargethierarchyfield());
                                                if (!val.isEmpty()) {
                                                    // Could be simplified
                                                    for (MetadataValue md : val) {
                                                        if (StringUtils.isNotBlank(md.getAuthority())
                                                            && checked.contains(UUID.fromString(md.getAuthority()))) {
                                                            continue;
                                                        }
                                                        if (StringUtils.isNotBlank(md.getValue())
                                                            && !md.getValue().contentEquals(
                                                                CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE)
                                                            && StringUtils.isNotBlank(md.getAuthority())) {
                                                            temp.add(UUID.fromString(md.getAuthority()));
                                                            found++;
                                                        }
                                                    }
                                                }
                                            }

                                            // Follow Successor of the universities
                                            if (StringUtils.isNotBlank(getTargetsuccessorfield())) {
                                                // follow target field to get the parent of this item, if it exists
                                                List<MetadataValue> val =
                                                    itemService.getMetadataByMetadataString(parentitem,
                                                        getTargetsuccessorfield());
                                                if (!val.isEmpty()) {
                                                    //Could be simplified
                                                    for (MetadataValue md : val) {
                                                        if (StringUtils.isNotBlank(md.getAuthority())
                                                            && checked.contains(UUID.fromString(md.getAuthority()))) {
                                                            continue;
                                                        }
                                                        if (StringUtils.isNotBlank(md.getValue())
                                                            && !md.getValue().contentEquals(
                                                                CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE)
                                                            && StringUtils.isNotBlank(md.getAuthority())) {
                                                            temp.add(UUID.fromString(md.getAuthority()));
                                                            found++;
                                                        }
                                                    }
                                                }
                                            }

                                            //add uuid to list. uuids is the result set
                                            uuids.add(actual);
                                            checked.add(actual);

                                        } else {
                                            //wrong type
                                            checked.add(actual);
                                        }
                                    } else {
                                        //not visible
                                        checked.add(actual);
                                    }
                                } else {
                                    //not exist || null
                                    checked.add(actual);
                                }
                            }
                        //set new temp list for next loop
                            tocheck = new HashSet<>(temp);
                            temp.clear();
                        } catch (Exception e) {
                            log.error(e.getMessage());
                        }
                    }
                }
                if (!uuids.isEmpty()) {
                    //cast to string if this uuid format leads to problems
                    try {
                        List<String> uuidstring = uuids.stream().map(UUID::toString).collect(Collectors.toList());
                        document.addField(getIndexfield(), uuidstring);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                }
            }
        }
    }

}
