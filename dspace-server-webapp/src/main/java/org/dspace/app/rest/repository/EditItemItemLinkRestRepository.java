/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import org.dspace.app.rest.model.EditItemRest;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.edit.EditItem;
import org.dspace.content.edit.service.EditItemService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
@Component(EditItemRest.CATEGORY + "." + EditItemRest.NAME_PLURAL + "." + EditItemRest.ITEM)
public class EditItemItemLinkRestRepository extends AbstractDSpaceRestRepository implements LinkRestRepository {

    private static final Logger log = LoggerFactory.getLogger(EditItemItemLinkRestRepository.class);

    @Autowired
    private EditItemService editItemService;
    @Autowired
    private ItemService itemService;

    /**
     * Retrieve the item for an edit item.
     *
     * @param request          - The current request
     * @param data             - The data template that contains both item uuid and mode {uuid:mode}, joined by a column
     * @param optionalPageable - optional pageable object
     * @param projection       - the current projection
     * @return the item for the edit item
     */
    public ItemRest getEditItemItem(
        @Nullable HttpServletRequest request, String data,
        @Nullable Pageable optionalPageable, Projection projection
    ) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        String[] split = data.split(":");

        UUID uuid;
        try {
            uuid = UUID.fromString(split[0]);
        } catch (Exception e) {
            log.error("Cannot convert the following uuid: {}", split[0], e);
            return null;
        }
        String mode = split[1];

        try {
            Context context = obtainContext();
            Item item = itemService.find(context, uuid);
            EditItem editItem = editItemService.find(context, item, mode);

            if (editItem == null || editItem.getItem() == null) {
                throw new ResourceNotFoundException("No such edit item found: " + uuid);
            }

            return converter.toRest(editItem.getItem(), projection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (AuthorizeException e) {
            throw new AccessDeniedException("The current user does not have rights to edit mode <" + mode + ">");
        }
    }

}
