/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.link.HalLinkService;
import org.dspace.app.rest.model.OrgunittreeNodeRest;
import org.dspace.uniba.orgunittree.OrgunittreeNode;
import org.dspace.uniba.orgunittree.OrgunittreeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * This class' purpose is to return a REST object to the controller class. This repository handles all the
 * information lookup
 * that has to be done for the endpoint
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
@Component(OrgunittreeNodeRest.CATEGORY + "." + OrgunittreeNodeRest.NAME)
public class OrgunittreeRestRepository extends AbstractDSpaceRestRepository {

    private static final Logger log = LogManager.getLogger();

    @Autowired
    private OrgunittreeService orgunittreeservice;

    @Autowired
    private HalLinkService halLinkService;

    public Page<OrgunittreeNodeRest> findAll(Pageable pageable) {
        List<OrgunittreeNode> trees = orgunittreeservice.getRootTree(this.obtainContext());
        return converter.toRestPage(trees, pageable, utils.obtainProjection());
    }

    public Page<OrgunittreeNodeRest> findRoot(Pageable pageable) {
        List<OrgunittreeNode> trees = orgunittreeservice.getRootTree(this.obtainContext());
        return converter.toRestPage(trees, pageable, utils.obtainProjection());
    }

    public OrgunittreeNodeRest findOne(UUID uuid, int depth) {
        OrgunittreeNode node = orgunittreeservice.getNode(obtainContext(), uuid);
        return converter.toRest(node, utils.obtainProjection());
    }

    public Page<OrgunittreeNodeRest> RecreateAndfindAll(Pageable pageable) {
        List<OrgunittreeNode> trees = orgunittreeservice.recreateTree(this.obtainContext());
        return converter.toRestPage(trees, pageable, utils.obtainProjection());
    }


}
