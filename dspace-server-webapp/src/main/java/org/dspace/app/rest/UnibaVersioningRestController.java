/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.utils.RegexUtils.REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.WorkspaceItemRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.versioning.uniba.VersioningService;
import org.dspace.versioning.uniba.VersioningStructureError;
import org.dspace.versioning.uniba.VersioningStructureException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.hateoas.Link;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * This is a specialized controller to change uniba versioning structure and create new versions
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
@RequestMapping("/api/core/unibaversioning")
@RestController
public class UnibaVersioningRestController implements InitializingBean {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(UnibaVersioningRestController.class);

    @Autowired
    DiscoverableEndpointsService discoverableEndpointsService;

    @Autowired
    private VersioningService versioningService;

    @Autowired
    private ItemService itemService;

    @Autowired
    ConverterService converter;

    @Autowired
    Utils utils;

    @Override
    public void afterPropertiesSet() {
        discoverableEndpointsService
            .register(this, Arrays.asList(Link.of("/api/core/unibaversioning", "unibaversioning")));
    }

    /**
     * Create some new version of the item specified in uuid
     * @param request
     * @param response
     * @param uuid
     * @return
     */
    @PreAuthorize("@unibaVersioningSecurity.isEnableVersioning() && hasAuthority('AUTHENTICATED')")
    @RequestMapping(method = RequestMethod.PUT, value = REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID)
    public WorkspaceItemRest createVersion(HttpServletRequest request,
                                          HttpServletResponse response, @PathVariable UUID uuid) {
        Context context = null;
        WorkspaceItem witem;
        WorkspaceItemRest witemrest = null;
        Item item;
        try {
            context = ContextUtil.obtainContext(request);
            item = this.itemService.find(context, uuid);
            if (Objects.nonNull(item)) {
                witem = this.versioningService.createNewVersion(context, item);
                response.setStatus(HttpServletResponse.SC_CREATED);
                witemrest = converter.toRest(witem, utils.obtainProjection());
                context.commit();
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (AuthorizeException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            //TODO: check other cases
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (Objects.nonNull(context) && context.isValid()) {
                context.close();
            }
        }
        return witemrest;
    }

    /**
     * Make the item specified in uuid the main element of the version group
     * @param request
     * @param response
     * @param uuid
     * @return
     */
    @PreAuthorize("@unibaVersioningSecurity.isEnableVersioning() && hasAuthority('AUTHENTICATED')")
    @RequestMapping(method = RequestMethod.POST, value = "/change" + REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID)
    public ItemRest changeVersion(HttpServletRequest request,
                                           HttpServletResponse response, @PathVariable UUID uuid) {
        Context context = null;
        Item item;
        ItemRest itemrest = null;
        try {
            context = ContextUtil.obtainContext(request);
            item = this.itemService.find(context, uuid);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return null;
        }
        try {
            if (Objects.nonNull(item)) {
                this.versioningService.changeMainVersion(context, item);
                response.setStatus(HttpServletResponse.SC_CREATED);
                itemrest = converter.toRest(item, utils.obtainProjection());
                context.commit();
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (VersioningStructureException e) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        } catch (AuthorizeException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (Objects.nonNull(context) && context.isValid()) {
                context.close();
            }
        }
        return itemrest;
    }

    /**
     * Get all group members from the version group where the item belongs to.
     * @param request
     * @param response
     * @param uuid
     * @return
     */
    @PreAuthorize("@unibaVersioningSecurity.isEnableVersioning() && hasAuthority('AUTHENTICATED')")
    @RequestMapping(method = RequestMethod.GET, value = "/group" + REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID)
    public Page<ItemRest> getVersionGroup(HttpServletRequest request,
                                          HttpServletResponse response, @PathVariable UUID uuid,
                                          @Nullable Pageable optionalPageable,
                                          @Param(value = "ignorecheck") boolean ignorecheck) {
        Context context = null;
        List<Item> items = new ArrayList<>();
        Item item;
        Pageable pageable = utils.getPageable(optionalPageable);

        try {
            context = ContextUtil.obtainContext(request);
            item = this.itemService.find(context, uuid);
            if (Objects.nonNull(item)) {
                List<Item> res = this.versioningService.getVersionGroupMember(context, item, ignorecheck);
                for (Item it : res) {
                    items.add(it);
                }
                response.setStatus(HttpServletResponse.SC_OK);
                Page<ItemRest> result = converter.toRestPage(items, pageable, items.size(), utils.obtainProjection());
                return result;
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (AuthorizeException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (Objects.nonNull(context) && context.isValid()) {
                context.close();
            }
        }
        return null;
    }

    /**
     * Check the validity of the group and return errors if any
     * @param request
     * @param response
     * @param uuid
     * @return
     */
    @PreAuthorize("@unibaVersioningSecurity.isEnableVersioning() && hasAuthority('AUTHENTICATED')")
    @RequestMapping(method = RequestMethod.GET, value = "/check" + REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID)
    public List<String> getVersionGroupCheck(HttpServletRequest request,
                                          HttpServletResponse response, @PathVariable UUID uuid) {
        Context context = null;
        List<String> errors = new ArrayList<>();
        Item item;
        try {
            context = ContextUtil.obtainContext(request);
            item = this.itemService.find(context, uuid);
            if (Objects.nonNull(item)) {
                List<VersioningStructureError> res = this.versioningService.checkVersionStructure(context, item);
                for (VersioningStructureError it : res) {
                    errors.add(it.toString());
                }
                response.setStatus(HttpServletResponse.SC_OK);
                return errors;
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (Objects.nonNull(context) && context.isValid()) {
                context.close();
            }
        }
        return null;
    }

}
