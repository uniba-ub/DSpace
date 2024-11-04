/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.requestitem;

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;
import java.util.List;

import org.dspace.AbstractUnitTest;
import org.dspace.builder.AbstractBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.SearchUtils;
import org.dspace.eperson.EPerson;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test suit for RequestItemMetadataStrategy
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class RequestItemMetadataStrategyTest extends AbstractUnitTest {
    private static final String AUTHOR_ADDRESS = "john.doe@example.com";

    private ItemService itemService;
    private ConfigurationService configurationService;

    private static EPerson johnDoe;

    private Item item;
    private Item orphanItem;
    private Item notVadilEmailItem;

    @BeforeClass
    public static void setUpClass() throws SQLException {
        AbstractBuilder.init();
        Context context = new Context();
        context.turnOffAuthorisationSystem();
        johnDoe = EPersonBuilder.createEPerson(context)
                                .withEmail(AUTHOR_ADDRESS)
                                .withNameInMetadata("John", "Doe")
                                .build();
        context.restoreAuthSystemState();
        context.complete();
    }

    @AfterClass
    public static void tearDownClass() {
        AbstractBuilder.destroy();
    }

    @Before
    public void setUp() {
        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        context = new Context();
        context.setCurrentUser(johnDoe);
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, community).build();
        item = ItemBuilder.createItem(context, collection)
                          .withTitle("Misha Boychuk")
                          .withPersonEmail("misha.boychuk@test.com")
                          .build();

        orphanItem = ItemBuilder.createItem(context, collection)
                                .withTitle("Orphan Item")
                                .withPersonEmail("orphanItem@test.com")
                                .build();
        orphanItem.setSubmitter(null);

        notVadilEmailItem = ItemBuilder.createItem(context, collection)
                                       .withTitle("Not Valid email Item")
                                       .withPersonEmail("test@test")
                                       .build();
        context.restoreAuthSystemState();
    }

    /**
     * Test of getRequestItemAuthor method, of class RequestItemMetadataStrategy.
     * @throws Exception passed through.
     */
    @Test
    public void testGetRequestItemAuthor() throws Exception {
        RequestItemMetadataStrategy metadataStrategy = new RequestItemMetadataStrategy();
        metadataStrategy.setItemService(this.itemService);
        metadataStrategy.setConfigurationService(this.configurationService);
        metadataStrategy.setEmailMetadata("person.email");
        metadataStrategy.setFullNameMetadata("dc.title");
        List<RequestItemAuthor> author = metadataStrategy.getRequestItemAuthor(context, item);
        assertEquals("misha.boychuk@test.com", author.get(0).getEmail());
        assertEquals("Misha Boychuk", author.get(0).getFullName());

        this.configurationService.setProperty("mail.helpdesk", "helpdesk@test.com");
        this.configurationService.setProperty("mail.helpdesk.name", "Helpdesk Name");

        // item without 'person.notExist' metadata & submitter is null
        RequestItemMetadataStrategy metadataStrategy2 = new RequestItemMetadataStrategy();
        metadataStrategy2.setItemService(this.itemService);
        metadataStrategy2.setConfigurationService(this.configurationService);
        metadataStrategy2.setEmailMetadata("person.notExist");
        metadataStrategy2.setFullNameMetadata("dc.title");
        List<RequestItemAuthor> author2 = metadataStrategy2.getRequestItemAuthor(context, orphanItem);
        assertEquals("helpdesk@test.com", author2.get(0).getEmail());
        assertEquals("Helpdesk Name", author2.get(0).getFullName());

        // item without 'person.notExist' metadata & submitter is null & helpdesk is null
        this.configurationService.setProperty("mail.helpdesk", null);
        this.configurationService.setProperty("mail.admin", "adminTest@test.com");
        this.configurationService.setProperty("mail.admin.name", "Admin Name");

        RequestItemMetadataStrategy metadataStrategy3 = new RequestItemMetadataStrategy();
        metadataStrategy3.setItemService(this.itemService);
        metadataStrategy3.setConfigurationService(this.configurationService);
        metadataStrategy3.setEmailMetadata("person.notExist");
        metadataStrategy3.setFullNameMetadata("dc.title");
        List<RequestItemAuthor> author3 = metadataStrategy3.getRequestItemAuthor(context, orphanItem);
        assertEquals("adminTest@test.com", author3.get(0).getEmail());
        assertEquals("Admin Name", author3.get(0).getFullName());

        // not valid email test
        RequestItemMetadataStrategy metadataStrategy4 = new RequestItemMetadataStrategy();
        metadataStrategy4.setItemService(this.itemService);
        metadataStrategy4.setConfigurationService(this.configurationService);
        metadataStrategy4.setEmailMetadata("person.email");
        metadataStrategy4.setFullNameMetadata("dc.title");
        List<RequestItemAuthor> author4 = metadataStrategy4.getRequestItemAuthor(context, notVadilEmailItem);
        assertEquals(AUTHOR_ADDRESS, author4.get(0).getEmail());
        assertEquals(johnDoe.getFullName(), author4.get(0).getFullName());
    }

}
