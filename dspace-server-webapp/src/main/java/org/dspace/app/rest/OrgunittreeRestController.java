/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.exception.PaginationException;
import org.dspace.app.rest.link.HalLinkService;
import org.dspace.app.rest.model.OrgunittreeNodeRest;
import org.dspace.app.rest.model.hateoas.OrgunittreeNodeResource;
import org.dspace.app.rest.repository.OrgunittreeRestRepository;
import org.dspace.app.rest.utils.Utils;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The controller for the orgunit tree endpoint
 *
  * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
@RestController
@RequestMapping("/api/" + OrgunittreeRestController.CATEGORY)
public class OrgunittreeRestController implements InitializingBean {

    public static final String CATEGORY = "orgunittree";
    private static final Logger log = LogManager.getLogger();

    @Autowired
    protected Utils utils;

    @Autowired
    private DiscoverableEndpointsService discoverableEndpointsService;


    @Autowired
    private HalLinkService halLinkService;

    @Autowired
    private ConverterService converter;

    @Autowired
    private OrgunittreeRestRepository repository;

    @Autowired
    protected ConfigurationService configurationService;

    @Override
    public void afterPropertiesSet() throws Exception {
        discoverableEndpointsService
                .register(this, List.of(Link.of(
                    "/api/" + OrgunittreeRestController.CATEGORY,
                    OrgunittreeRestController.CATEGORY)));
    }

    @RequestMapping(method = RequestMethod.GET)
    @SuppressWarnings("unchecked")
    public PagedModel<OrgunittreeNodeResource> getTree(HttpServletRequest request,
        HttpServletResponse response, Pageable page, PagedResourcesAssembler assembler) {
        if (!this.configurationService.getBooleanProperty("orgunittree.enabled")) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return null;
        }
        PagedModel<OrgunittreeNodeResource> result = null;
        try {
            Link link = linkTo(this.getClass(), CATEGORY, OrgunittreeNodeRest.NAME).withSelfRel();
            Page<OrgunittreeNodeResource> resources;
            try {
                Page<OrgunittreeNodeRest> tree = repository.findAll(page);
                resources = tree.map(converter::toResource);
                resources.forEach(orgunittreeNodeResource -> halLinkService.addLinks(orgunittreeNodeResource));
            } catch (PaginationException pe) {
                resources = new PageImpl<>(new ArrayList<>(), page, pe.getTotal());
            }
            result = assembler.toModel(resources, link);
            response.setStatus(HttpServletResponse.SC_OK);
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return result;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/recreate")
    @SuppressWarnings("unchecked")
    @PreAuthorize("hasAuthority('ADMIN')")
    public PagedModel<OrgunittreeNodeResource> getRecreation(HttpServletRequest request,
                          HttpServletResponse response, Pageable page, PagedResourcesAssembler assembler) {
        if (!this.configurationService.getBooleanProperty("orgunittree.enabled")) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return null;
        }
        PagedModel<OrgunittreeNodeResource> result = null;
        try {
            Link link = linkTo(this.getClass(), CATEGORY, OrgunittreeNodeRest.NAME).withSelfRel();
            Page<OrgunittreeNodeResource> resources;
            try {
                Page<OrgunittreeNodeRest> tree = repository.RecreateAndfindAll(page);
                resources = tree.map(converter::toResource);
                resources.forEach(orgunittreeNodeResource -> halLinkService.addLinks(orgunittreeNodeResource));
            } catch (PaginationException pe) {
                resources = new PageImpl<>(new ArrayList<>(), page, pe.getTotal());
            }
            result = assembler.toModel(resources, link);
            response.setStatus(HttpServletResponse.SC_OK);
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return result;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{uuid}")
    @SuppressWarnings("unchecked")
    public OrgunittreeNodeResource getTreeNode(HttpServletRequest request,
                          HttpServletResponse response,
                          @PathVariable(name = "uuid", required = true) String uuid,
                          @RequestParam(name = "depth", required = false, defaultValue = "1") int depth) {
        if (!this.configurationService.getBooleanProperty("orgunittree.enabled")) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return null;
        }
        OrgunittreeNodeResource result = null;
        try {
            UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        try {
            OrgunittreeNodeRest node = repository.findOne(UUID.fromString(uuid), depth);
            result = converter.toResource(node);
            Link link = linkTo(this.getClass(), CATEGORY).slash(uuid).withSelfRel();
            result.add(link);
            halLinkService.addLinks(result);
            response.setStatus(HttpServletResponse.SC_OK);
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return result;
    }
}
