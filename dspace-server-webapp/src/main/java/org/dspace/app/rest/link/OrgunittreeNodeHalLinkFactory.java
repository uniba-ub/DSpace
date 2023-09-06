/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.link;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import java.util.LinkedList;

import org.atteo.evo.inflector.English;
import org.dspace.app.rest.RestResourceController;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.hateoas.OrgunittreeNodeResource;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * This HalLinkFactory adds links to the OrgunittreeNode object
 */
@Component
public class OrgunittreeNodeHalLinkFactory
    extends HalLinkFactory<OrgunittreeNodeResource, RestResourceController> {

    @Override
    protected void addLinks(OrgunittreeNodeResource halResource, Pageable pageable, LinkedList<Link> list)
        throws Exception {
        ItemRest item = halResource.getContent().getItem();
        UriComponentsBuilder uriComponentsBuilder = linkTo(getMethodOn(ItemRest.CATEGORY, ItemRest.NAME)
            .findRel(null, null, ItemRest.CATEGORY, English.plural(ItemRest.NAME),
                item.getId(), "", null, null)).toUriComponentsBuilder();
        String uribuilder = uriComponentsBuilder.build().toString();
        list.add(buildLink("items", uribuilder.substring(0, uribuilder.lastIndexOf("/"))));
    }

    @Override
    protected Class<RestResourceController> getControllerClass() {
        return RestResourceController.class;
    }

    protected Class<OrgunittreeNodeResource> getResourceClass() {
        return OrgunittreeNodeResource.class;
    }
}
