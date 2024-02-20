/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.orcid;

import static org.dspace.app.matcher.OrcidQueueMatcher.matches;
import static org.dspace.builder.OrcidHistoryBuilder.createOrcidHistory;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.dspace.orcid.OrcidOperation.DELETE;
import static org.dspace.orcid.OrcidOperation.INSERT;
import static org.dspace.orcid.OrcidOperation.UPDATE;
import static org.dspace.orcid.model.OrcidProfileSectionType.KEYWORDS;
import static org.dspace.profile.OrcidEntitySyncPreference.ALL;
import static org.dspace.profile.OrcidEntitySyncPreference.DISABLED;
import static org.dspace.profile.OrcidEntitySyncPreference.MINE;
import static org.dspace.profile.OrcidEntitySyncPreference.MY_SELECTED;
import static org.dspace.profile.OrcidProfileSyncPreference.AFFILIATION;
import static org.dspace.profile.OrcidProfileSyncPreference.BIOGRAPHICAL;
import static org.dspace.profile.OrcidProfileSyncPreference.EDUCATION;
import static org.dspace.profile.OrcidProfileSyncPreference.IDENTIFIERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EntityTypeBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.OrcidHistoryBuilder;
import org.dspace.builder.RelationshipBuilder;
import org.dspace.builder.RelationshipTypeBuilder;
import org.dspace.content.Collection;
import org.dspace.content.EntityType;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.Relationship;
import org.dspace.content.RelationshipType;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.orcid.consumer.OrcidQueueConsumer;
import org.dspace.orcid.factory.OrcidServiceFactory;
import org.dspace.orcid.model.OrcidEntityType;
import org.dspace.orcid.service.OrcidQueueService;
import org.dspace.profile.OrcidEntitySyncPreference;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.dspace.versioning.Version;
import org.dspace.versioning.service.VersioningService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link OrcidQueueConsumer}.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class OrcidQueueConsumerIT extends AbstractIntegrationTestWithDatabase {

    private OrcidQueueService orcidQueueService = OrcidServiceFactory.getInstance().getOrcidQueueService();

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private WorkspaceItemService workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();

    private InstallItemService installItemService = ContentServiceFactory.getInstance().getInstallItemService();

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    private VersioningService versioningService = new DSpace().getServiceManager()
        .getServicesByType(VersioningService.class).get(0);

    private Collection profileCollection;

    @Before
    public void setup() {

        context.setDispatcher("cris-default");
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent community")
            .build();

        profileCollection = createCollection("Profiles", "Person");

        context.restoreAuthSystemState();
    }

    @After
    public void after() throws SQLException, AuthorizeException {
        List<OrcidQueue> records = orcidQueueService.findAll(context);
        for (OrcidQueue record : records) {
            orcidQueueService.delete(context, record);
        }
        context.setDispatcher(null);
    }

    @Test
    public void testOrcidQueueRecordCreationForProfile() throws Exception {
        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withPersonAffiliation("4Science")
            .withPersonAffiliationStartDate("2021-01-01")
            .withPersonAffiliationEndDate(PLACEHOLDER_PARENT_METADATA_VALUE)
            .withPersonAffiliationRole("Researcher")
            .withOrcidSynchronizationProfilePreference(AFFILIATION)
            .withOrcidSynchronizationProfilePreference(EDUCATION)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> queueRecords = orcidQueueService.findAll(context);
        assertThat(queueRecords, hasSize(1));
        assertThat(queueRecords.get(0), matches(profile, profile, "AFFILIATION", null,
            containsString("4Science"), "Researcher at 4Science ( from 2021-01-01 to present )", INSERT));

        addMetadata(profile, "crisrp", "education", null, "High School", null);
        context.commit();

        queueRecords = orcidQueueService.findAll(context);
        assertThat(queueRecords, hasSize(2));
        assertThat(queueRecords, hasItem(matches(profile, profile, "AFFILIATION", null,
            containsString("4Science"), "Researcher at 4Science ( from 2021-01-01 to present )", INSERT)));
        assertThat(queueRecords, hasItem(matches(profile, profile, "EDUCATION", null,
            containsString("High School"), "High School ( from unspecified to present )", INSERT)));
    }

    @Test
    public void testOrcidQueueRecordCreationForProfileWithSameMetadataPreviouslyDeleted() throws Exception {
        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationProfilePreference(BIOGRAPHICAL)
            .build();

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType("COUNTRY")
            .withMetadata("crisrp.country::IT")
            .withPutCode("123456")
            .withOperation(OrcidOperation.INSERT)
            .withTimestamp(Date.from(Instant.ofEpochMilli(100000)))
            .withStatus(201)
            .build();

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType("COUNTRY")
            .withMetadata("crisrp.country::IT")
            .withPutCode("123456")
            .withOperation(OrcidOperation.DELETE)
            .withTimestamp(Date.from(Instant.ofEpochMilli(200000)))
            .withStatus(204)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

        addMetadata(profile, "crisrp", "country", null, "IT", null);
        context.commit();

        List<OrcidQueue> queueRecords = orcidQueueService.findAll(context);
        assertThat(queueRecords, hasSize(1));
        assertThat(queueRecords.get(0), matches(profile, "COUNTRY", null, "crisrp.country::IT", "IT", INSERT));
    }

    @Test
    public void testOrcidQueueRecordCreationForProfileWithMetadataPreviouslyDeletedAndThenInsertedAgain()
        throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationProfilePreference(BIOGRAPHICAL)
            .build();

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType("COUNTRY")
            .withMetadata("crisrp.country::IT")
            .withPutCode("123456")
            .withOperation(OrcidOperation.INSERT)
            .withTimestamp(Date.from(Instant.ofEpochMilli(100000)))
            .withStatus(201)
            .build();

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType("COUNTRY")
            .withMetadata("crisrp.country::IT")
            .withPutCode("123456")
            .withOperation(OrcidOperation.DELETE)
            .withTimestamp(Date.from(Instant.ofEpochMilli(200000)))
            .withStatus(204)
            .build();

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType("COUNTRY")
            .withMetadata("crisrp.country::IT")
            .withPutCode("123456")
            .withOperation(OrcidOperation.INSERT)
            .withTimestamp(Date.from(Instant.ofEpochMilli(300000)))
            .withStatus(201)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

        addMetadata(profile, "crisrp", "country", null, "IT", null);
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

    }

    @Test
    public void testOrcidQueueRecordCreationForProfileWithNotSuccessfullyMetadataDeletion()
        throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationProfilePreference(BIOGRAPHICAL)
            .build();

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType("COUNTRY")
            .withMetadata("crisrp.country::IT")
            .withPutCode("123456")
            .withOperation(OrcidOperation.INSERT)
            .withTimestamp(Date.from(Instant.ofEpochMilli(100000)))
            .withStatus(201)
            .build();

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType("COUNTRY")
            .withMetadata("crisrp.country::IT")
            .withPutCode("123456")
            .withOperation(OrcidOperation.DELETE)
            .withTimestamp(Date.from(Instant.ofEpochMilli(200000)))
            .withStatus(400)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

        addMetadata(profile, "crisrp", "country", null, "IT", null);
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

    }

    @Test
    public void testOrcidQueueRecordCreationForDeletionOfProfileMetadata() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationProfilePreference(AFFILIATION)
            .build();

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withMetadata("XX/YY/ZZ/AA")
            .withRecordType("AFFILIATION")
            .withOperation(OrcidOperation.INSERT)
            .withDescription("4Science (2020, 2021)")
            .withStatus(201)
            .withPutCode("12345")
            .build();

        addMetadata(profile, "crisrp", "education", null, "High School", null);

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> queueRecords = orcidQueueService.findAll(context);
        assertThat(queueRecords, hasSize(1));
        assertThat(queueRecords.get(0), matches(profile, "AFFILIATION", "12345", "XX/YY/ZZ/AA",
            "4Science (2020, 2021)", DELETE));
    }

    @Test
    public void testOrcidQueueRecordCreationAndDeletion() throws Exception {

        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withSubject("Science")
            .withOrcidSynchronizationProfilePreference(BIOGRAPHICAL)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> records = orcidQueueService.findAll(context);
        assertThat(records, hasSize(1));
        assertThat(records, hasItem(matches(item, KEYWORDS.name(), null, "dc.subject::Science", "Science", INSERT)));

        removeMetadata(item, "dc", "subject", null);
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

    }

    @Test
    public void testOrcidQueueRecordCreationAndDeletionWithOrcidHistoryInsertionInTheMiddle() throws Exception {

        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withSubject("Science")
            .withOrcidSynchronizationProfilePreference(BIOGRAPHICAL)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> records = orcidQueueService.findAll(context);
        assertThat(records, hasSize(1));
        assertThat(records, hasItem(matches(item, KEYWORDS.name(), null, "dc.subject::Science", "Science", INSERT)));

        OrcidHistoryBuilder.createOrcidHistory(context, item, item)
            .withPutCode("12345")
            .withMetadata("dc.subject::Science")
            .withDescription("Science")
            .withRecordType(KEYWORDS.name())
            .withOperation(INSERT)
            .withStatus(201)
            .build();

        removeMetadata(item, "dc", "subject", null);
        context.commit();

        records = orcidQueueService.findAll(context);
        assertThat(records, hasSize(1));
        assertThat(records, hasItem(matches(item, KEYWORDS.name(), "12345", "dc.subject::Science", "Science", DELETE)));

    }

    @Test
    public void testOrcidQueueRecordCreationAndDeletionWithFailedOrcidHistoryInsertionInTheMiddle() throws Exception {

        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withSubject("Science")
            .withOrcidSynchronizationProfilePreference(BIOGRAPHICAL)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> records = orcidQueueService.findAll(context);
        assertThat(records, hasSize(1));
        assertThat(records, hasItem(matches(item, KEYWORDS.name(), null, "dc.subject::Science", "Science", INSERT)));

        OrcidHistoryBuilder.createOrcidHistory(context, item, item)
            .withPutCode("12345")
            .withMetadata("dc.subject::Science")
            .withDescription("Science")
            .withRecordType(KEYWORDS.name())
            .withOperation(INSERT)
            .withStatus(400)
            .build();

        removeMetadata(item, "dc", "subject", null);
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

    }

    @Test
    public void testNoOrcidQueueRecordCreationOccursIfProfileSynchronizationIsDisabled() throws SQLException {
        context.turnOffAuthorisationSystem();

        ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withPersonAffiliation("4Science")
            .withPersonAffiliationStartDate("2021-01-01")
            .withPersonAffiliationEndDate(PLACEHOLDER_PARENT_METADATA_VALUE)
            .withPersonAffiliationRole("Researcher")
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());
    }

    @Test
    public void testNoOrcidQueueRecordCreationOccursIfNoComplianceMetadataArePresent() throws SQLException {
        context.turnOffAuthorisationSystem();

        ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withPersonAffiliation("4Science")
            .withPersonAffiliationStartDate("2021-01-01")
            .withPersonAffiliationEndDate(PLACEHOLDER_PARENT_METADATA_VALUE)
            .withPersonAffiliationRole("Researcher")
            .withOrcidSynchronizationProfilePreference(IDENTIFIERS)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());
    }

    @Test
    public void testOrcidQueueRecordCreationForPublication() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationPublicationsPreference(ALL)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));

        addMetadata(publication, "dc", "contributor", "editor", "Editor", null);
        context.commit();

        List<OrcidQueue> newOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newOrcidQueueRecords, hasSize(1));

        assertThat(orcidQueueRecords.get(0), equalTo(newOrcidQueueRecords.get(0)));
    }
    @Test
    public void testOrcidQueueRecordCreationForPublicationRelationMySELECTED() throws Exception {

        context.turnOffAuthorisationSystem();

        configurationService.addPropertyValue("orcid.relation.Publication.MY_SELECTED",
                "isResearchoutputsSelectedFor");

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationPublicationsPreference(MY_SELECTED)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        Item secondpublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication 2")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        Item thirdpublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication 3")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        EntityType personType = EntityTypeBuilder.createEntityTypeBuilder(context, "Person").build();

        RelationshipType SelectedType = RelationshipTypeBuilder
            .createRelationshipTypeBuilder(context, null, personType, "isResearchoutputsSelectedFor",
                "hasSelectedResearchoutputs", 0, null, 0,
                null).build();

        RelationshipBuilder.createRelationshipBuilder(context, publication, profile, SelectedType)
            .withLeftwardValue("isResearchoutputsSelectedFor").build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));

        addMetadata(publication, "dc", "contributor", "editor", "Editor", null);
        addMetadata(secondpublication, "dc", "contributor", "editor", "Editor", null);
        addMetadata(thirdpublication, "dc", "contributor", "editor", "Editor", null);
        context.commit();

        List<OrcidQueue> newOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newOrcidQueueRecords, hasSize(1));

        assertThat(orcidQueueRecords.get(0), equalTo(newOrcidQueueRecords.get(0)));

        context.turnOffAuthorisationSystem();
        Relationship secondrelselected =
                RelationshipBuilder.createRelationshipBuilder(context, secondpublication, profile, SelectedType)
                    .withLeftwardValue("isResearchoutputsSelectedFor").build();
        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> newaftercreationOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newaftercreationOrcidQueueRecords, hasSize(2));
        assertThat(newaftercreationOrcidQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));
        assertThat(newaftercreationOrcidQueueRecords.get(1), matches(profile, secondpublication,
                "Publication", INSERT));

        context.turnOffAuthorisationSystem();
        RelationshipBuilder.deleteRelationship(secondrelselected.getID());
        itemService.update(context, secondpublication);
        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> newafterdeletionOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newafterdeletionOrcidQueueRecords, hasSize(1));
        assertThat(newafterdeletionOrcidQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));
    }

    @Test
    public void testNoOrcidQueueRecordCreationForPublicationIfRelationMYSELECTEDIsNotConfigured() throws Exception {

        context.turnOffAuthorisationSystem();
        configurationService.setProperty("orcid.relation.Publication.MY_SELECTED", null);
        Item profile = ItemBuilder.createItem(context, profileCollection)
                .withTitle("Test User")
                .withOrcidIdentifier("0000-1111-2222-3333")
                .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
                .withOrcidSynchronizationPublicationsPreference(MY_SELECTED)
                .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        Item secondpublication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication 2")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        EntityType personType = EntityTypeBuilder.createEntityTypeBuilder(context, "Person").build();

        RelationshipType SelectedType = RelationshipTypeBuilder
                .createRelationshipTypeBuilder(context, null, personType, "isResearchoutputsSelectedFor",
                        "hasSelectedResearchoutputs", 0, null, 0,
                        null).build();

        RelationshipBuilder.createRelationshipBuilder(context, publication, profile, SelectedType)
            .withLeftwardValue("isResearchoutputsSelectedFor").build();
        RelationshipBuilder.createRelationshipBuilder(context, secondpublication, profile, SelectedType)
            .withLeftwardValue("isResearchoutputsSelectedFor").build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(0));

        addMetadata(publication, "dc", "contributor", "editor", "Editor", null);
        addMetadata(secondpublication, "dc", "contributor", "editor", "Editor", null);
        context.commit();

        List<OrcidQueue> newOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newOrcidQueueRecords, hasSize(0));

    }
    @Test
    public void testNoOrcidQueueRecordCreationForPublicationIfRelationMYSELECTEDQueryIsDifferent() throws Exception {

        context.turnOffAuthorisationSystem();
        configurationService.setProperty("orcid.relation.Publication.MY_SELECTED",
                "isSomeOtherRelationSelectedFor");
        Item profile = ItemBuilder.createItem(context, profileCollection)
                .withTitle("Test User")
                .withOrcidIdentifier("0000-1111-2222-3333")
                .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
                .withOrcidSynchronizationPublicationsPreference(MY_SELECTED)
                .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        Item secondpublication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication 2")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        EntityType personType = EntityTypeBuilder.createEntityTypeBuilder(context, "Person").build();

        RelationshipType SelectedType = RelationshipTypeBuilder
                .createRelationshipTypeBuilder(context, null, personType, "isResearchoutputsSelectedFor",
                        "hasSelectedResearchoutputs", 0, null, 0,
                        null).build();

        RelationshipBuilder.createRelationshipBuilder(context, publication, profile, SelectedType)
            .withLeftwardValue("isResearchoutputsSelectedFor").build();
        RelationshipBuilder.createRelationshipBuilder(context, secondpublication, profile, SelectedType)
            .withLeftwardValue("isResearchoutputsSelectedFor").build();

        context.commit();
        context.restoreAuthSystemState();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(0));
    }

    @Test
    public void testOrcidQueueRecordCreationForPublicationRelationMINE() throws Exception {

        context.turnOffAuthorisationSystem();
        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationPublicationsPreference(MINE)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        Item secondpublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication 2")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        Item thirdpublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication 3")
            .withAuthor("Test User", profile.getID().toString())
            .build();
        itemService.update(context, thirdpublication);
        EntityType personType = EntityTypeBuilder.createEntityTypeBuilder(context, "Person").build();

        RelationshipType SelectedType = RelationshipTypeBuilder
            .createRelationshipTypeBuilder(context, null, personType, "isResearchoutputsSelectedFor",
                "hasSelectedResearchoutputs", 0, null, 0,
                null).build();
        RelationshipType HiddenType = RelationshipTypeBuilder
            .createRelationshipTypeBuilder(context, null, personType, "isResearchoutputsHiddenFor",
                "notDisplayingResearchoutputs", 0, null, 0,
                null).build();

        RelationshipBuilder.createRelationshipBuilder(context, publication, profile, SelectedType)
            .withLeftwardValue("isResearchoutputsSelectedFor").build();
        RelationshipBuilder.createRelationshipBuilder(context, secondpublication, profile, HiddenType)
            .withLeftwardValue("isResearchoutputsHiddenFor").build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(2));
        assertThat(orcidQueueRecords.get(0), matches(profile, thirdpublication, "Publication", INSERT));
        assertThat(orcidQueueRecords.get(1), matches(profile, publication, "Publication", INSERT));

        addMetadata(publication, "dc", "contributor", "editor", "Editor", null);
        addMetadata(secondpublication, "dc", "contributor", "editor", "Editor", null);
        addMetadata(thirdpublication, "dc", "contributor", "editor", "Editor", null);
        context.commit();

        List<OrcidQueue> newOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newOrcidQueueRecords, hasSize(2));

        assertThat(orcidQueueRecords.get(0), equalTo(newOrcidQueueRecords.get(0)));
        assertThat(orcidQueueRecords.get(1), equalTo(newOrcidQueueRecords.get(1)));

        context.turnOffAuthorisationSystem();
        Relationship relbeingdeleted =
                RelationshipBuilder.createRelationshipBuilder(context, thirdpublication, profile, HiddenType)
                    .withLeftwardValue("isResearchoutputsHiddenFor").build();
        context.commit();
        context.restoreAuthSystemState();

        List<OrcidQueue> newcreationQueueRecords = orcidQueueService.findAll(context);
        assertThat(newcreationQueueRecords, hasSize(1));
        assertThat(newcreationQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));
        context.turnOffAuthorisationSystem();

        context.turnOffAuthorisationSystem();
        RelationshipBuilder.deleteRelationship(relbeingdeleted.getID());
        itemService.update(context, thirdpublication);
        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> newafterdeletionQueueRecords = orcidQueueService.findAll(context);
        assertThat(newafterdeletionQueueRecords, hasSize(2));
        assertThat(newafterdeletionQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));
        assertThat(newafterdeletionQueueRecords.get(1), matches(profile, thirdpublication, "Publication", INSERT));
    }

    @Test
    public void testNoOrcidQueueRecordCreationForPublicationRelationMineIfAllAreHidden() throws Exception {

        context.turnOffAuthorisationSystem();
        Item profile = ItemBuilder.createItem(context, profileCollection)
                .withTitle("Test User")
                .withOrcidIdentifier("0000-1111-2222-3333")
                .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
                .withOrcidSynchronizationPublicationsPreference(MINE)
                .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        Item secondpublication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication 2")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        EntityType personType = EntityTypeBuilder.createEntityTypeBuilder(context, "Person").build();

        RelationshipType HiddenType = RelationshipTypeBuilder
                .createRelationshipTypeBuilder(context, null, personType, "isResearchoutputsHiddenFor",
                        "notDisplayingResearchoutputs", 0, null, 0,
                        null).build();

        RelationshipBuilder.createRelationshipBuilder(context, publication, profile, HiddenType)
            .withLeftwardValue("isResearchoutputsHiddenFor").build();
        RelationshipBuilder.createRelationshipBuilder(context, secondpublication, profile, HiddenType)
            .withLeftwardValue("isResearchoutputsHiddenFor").build();
        context.commit();
        context.restoreAuthSystemState();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(0));

        addMetadata(publication, "dc", "contributor", "editor", "Editor", null);
        addMetadata(secondpublication, "dc", "contributor", "editor", "Editor", null);
        context.commit();

        List<OrcidQueue> newOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newOrcidQueueRecords, hasSize(0));
    }

    @Test
    public void testNoOrcidQueueRecordCreationForPublicationRelationMineIfOneDeleted() throws Exception {

        context.turnOffAuthorisationSystem();
        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationPublicationsPreference(MINE)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        Item secondpublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication 2")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        EntityType personType = EntityTypeBuilder.createEntityTypeBuilder(context, "Person").build();

        RelationshipType HiddenType = RelationshipTypeBuilder
            .createRelationshipTypeBuilder(context, null, personType, "isResearchoutputsHiddenFor",
                "notDisplayingResearchoutputs", 0, null, 0,
                null).build();

        RelationshipBuilder.createRelationshipBuilder(context, publication, profile, HiddenType)
            .withLeftwardValue("isResearchoutputsHiddenFor").build();
        Relationship hiddenbeingdeleted =
            RelationshipBuilder.createRelationshipBuilder(context, secondpublication, profile, HiddenType)
                .withLeftwardValue("isResearchoutputsHiddenFor").build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(0));

        addMetadata(publication, "dc", "contributor", "editor", "Editor", null);
        addMetadata(secondpublication, "dc", "contributor", "editor", "Editor", null);


        List<OrcidQueue> newOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newOrcidQueueRecords, hasSize(0));

        context.commit();
        context.turnOffAuthorisationSystem();
        RelationshipBuilder.deleteRelationship(hiddenbeingdeleted.getID());
        itemService.update(context,secondpublication);

        context.commit();
        context.restoreAuthSystemState();

        List<OrcidQueue> newafterdeletionOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newafterdeletionOrcidQueueRecords, hasSize(1));
    }

    @Test
    public void testNoOrcidQueueRecordCreationOccursIfPublicationRelationMYSELECTEDNoneExist() throws Exception {

        context.turnOffAuthorisationSystem();
        Item profile = ItemBuilder.createItem(context, profileCollection)
                .withTitle("Test User")
                .withOrcidIdentifier("0000-1111-2222-3333")
                .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
                .withOrcidSynchronizationPublicationsPreference(MY_SELECTED)
                .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        Item secondpublication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication 2")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(0));

        addMetadata(publication, "dc", "contributor", "editor", "Editor", null);
        addMetadata(secondpublication, "dc", "contributor", "editor", "Editor", null);
        context.commit();

        List<OrcidQueue> newOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newOrcidQueueRecords, hasSize(0));
    }

    @Test
    public void testOrcidQueueRecalculationForChangedPublicationRelationSettings() throws Exception {

        context.turnOffAuthorisationSystem();
        Item profile = ItemBuilder.createItem(context, profileCollection)
                .withTitle("Test User")
                .withOrcidIdentifier("0000-1111-2222-3333")
                .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
                .withOrcidSynchronizationPublicationsPreference(ALL)
                .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        Item secondpublication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication 2")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        Item thirdpublication = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test publication 3")
                .withAuthor("Test User", profile.getID().toString())
                .build();

        EntityType personType = EntityTypeBuilder.createEntityTypeBuilder(context, "Person").build();

        RelationshipType HiddenType = RelationshipTypeBuilder
                .createRelationshipTypeBuilder(context, null, personType, "isResearchoutputsHiddenFor",
                        "notDisplayingResearchoutputs", 0, null, 0,
                        null).build();

        RelationshipType SelectedType = RelationshipTypeBuilder
                .createRelationshipTypeBuilder(context, null, personType, "isResearchoutputsSelectedFor",
                        "hasSelectedResearchoutputs", 0, null, 0,
                        null).build();

        RelationshipBuilder.createRelationshipBuilder(context, publication, profile, HiddenType)
            .withLeftwardValue("isResearchoutputsHiddenFor").build();
        RelationshipBuilder.createRelationshipBuilder(context, secondpublication, profile, SelectedType)
            .withLeftwardValue("isResearchoutputsSelectedFor").build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(3));
        assertThat(orcidQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));
        assertThat(orcidQueueRecords.get(1), matches(profile, secondpublication, "Publication", INSERT));
        assertThat(orcidQueueRecords.get(2), matches(profile, thirdpublication, "Publication", INSERT));

        changeProfilePublicationSyncPreference(profile, MY_SELECTED);

        List<OrcidQueue> selectedOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(selectedOrcidQueueRecords, hasSize(1));
        assertThat(selectedOrcidQueueRecords.get(0), matches(profile, secondpublication, "Publication", INSERT));

        changeProfilePublicationSyncPreference(profile, MINE);

        List<OrcidQueue> mineOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(mineOrcidQueueRecords, hasSize(2));
        assertThat(mineOrcidQueueRecords.get(0), matches(profile, thirdpublication, "Publication", INSERT));
        assertThat(mineOrcidQueueRecords.get(1), matches(profile, secondpublication, "Publication", INSERT));

        changeProfilePublicationSyncPreference(profile, DISABLED);

        List<OrcidQueue> disabledOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(disabledOrcidQueueRecords, hasSize(0));
    }

    @Test
    public void testOrcidQueueRecordCreationToUpdatePublication() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationPublicationsPreference(ALL)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .build();

        createOrcidHistory(context, profile, publication)
            .withPutCode("123456")
            .withOperation(INSERT)
            .build();

        addMetadata(publication, "dc", "contributor", "author", "Test User", profile.getID().toString());

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, publication, "Publication", "123456", UPDATE));
    }

    @Test
    public void testNoOrcidQueueRecordCreationOccursIfPublicationSynchronizationIsDisabled() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

        addMetadata(profile, "dspace", "orcid", "sync-publications", DISABLED.name(), null);
        addMetadata(publication, "dc", "date", "issued", "2021-01-01", null);
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());
    }

    @Test
    public void testOrcidQueueRecordCreationForFunding() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationFundingsPreference(ALL)
            .build();

        Collection fundingCollection = createCollection("Fundings", "Funding");

        Item funding = ItemBuilder.createItem(context, fundingCollection)
            .withTitle("Test funding")
            .withFundingInvestigator("Test User", profile.getID().toString())
            .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, funding, "Funding", null, INSERT));

        addMetadata(funding, "dc", "type", null, "Funding type", null);
        context.commit();

        List<OrcidQueue> newOrcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(newOrcidQueueRecords, hasSize(1));

        assertThat(orcidQueueRecords.get(0), equalTo(newOrcidQueueRecords.get(0)));
    }

    @Test
    public void testOrcidQueueRecordCreationToUpdateFunding() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationFundingsPreference(ALL)
            .build();

        Collection fundingCollection = createCollection("Fundings", "Funding");

        Item funding = ItemBuilder.createItem(context, fundingCollection)
            .withTitle("Test funding")
            .build();

        createOrcidHistory(context, profile, funding)
            .withPutCode("123456")
            .build();

        addMetadata(funding, "crisfund", "coinvestigators", null, "Test User", profile.getID().toString());

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, funding, "Funding", "123456", UPDATE));
    }

    @Test
    public void testNoOrcidQueueRecordCreationOccursIfFundingSynchronizationIsDisabled() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .build();

        Collection fundingCollection = createCollection("Fundings", "Funding");

        Item funding = ItemBuilder.createItem(context, fundingCollection)
            .withTitle("Test funding")
            .withFundingInvestigator("Test User", profile.getID().toString())
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

        addMetadata(profile, "dspace", "orcid", "sync-fundings", DISABLED.name(), null);
        addMetadata(funding, "crispj", "partnerou", null, "Partner", null);
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());
    }

    @Test
    public void testNoOrcidQueueRecordCreationOccursIfProfileHasNotOrcidIdentifier() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationFundingsPreference(ALL)
            .build();

        Collection fundingCollection = createCollection("Fundings", "Funding");

        ItemBuilder.createItem(context, fundingCollection)
            .withTitle("Test funding")
            .withFundingInvestigator("Test User", profile.getID().toString())
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());
    }

    @Test
    public void testNoOrcidQueueRecordCreationOccursIfProfileHasNotOrcidAccessToken() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidSynchronizationFundingsPreference(ALL)
            .build();

        Collection fundingCollection = createCollection("Fundings", "Funding");

        ItemBuilder.createItem(context, fundingCollection)
            .withTitle("Test funding")
            .withFundingInvestigator("Test User", profile.getID().toString())
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());
    }

    @Test
    public void testNoOrcidQueueRecordCreationOccursForNotConfiguredEntities() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .build();

        Collection fundingCollection = createCollection("Fundings", "Funding");

        ItemBuilder.createItem(context, fundingCollection)
            .withTitle("Test funding")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        context.restoreAuthSystemState();
        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());
    }

    @Test
    public void testManyOrcidQueueRecordCreations() throws Exception {

        context.turnOffAuthorisationSystem();

        Item firstProfile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationPublicationsPreference(ALL)
            .build();

        Item secondProfile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Another Test User")
            .withDspaceObjectOwner(admin)
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", admin)
            .withOrcidSynchronizationPublicationsPreference(ALL)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item firstPublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("Test User", firstProfile.getID().toString())
            .withEditor("Test User", firstProfile.getID().toString())
            .withAuthor("Another Test User", secondProfile.getID().toString())
            .build();

        Item secondPublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Another Test publication")
            .withAuthor("Another Test User", secondProfile.getID().toString())
            .build();

        Item thirdPublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Third Test publication")
            .build();

        context.restoreAuthSystemState();
        context.commit();

        OrcidHistoryBuilder.createOrcidHistory(context, secondProfile, thirdPublication)
            .withDescription("Third Test publication")
            .withOperation(OrcidOperation.INSERT)
            .withPutCode("12345")
            .withStatus(201)
            .build();

        addMetadata(thirdPublication, "dc", "contributor", "author", "Test User", firstProfile.getID().toString());
        addMetadata(thirdPublication, "dc", "contributor", "author", "Another User", secondProfile.getID().toString());

        context.commit();

        List<OrcidQueue> queueRecords = orcidQueueService.findAll(context);
        assertThat(queueRecords, hasSize(5));

        assertThat(queueRecords, hasItem(matches(firstProfile, firstPublication, "Publication", null, INSERT)));
        assertThat(queueRecords, hasItem(matches(secondProfile, firstPublication, "Publication", null, INSERT)));
        assertThat(queueRecords, hasItem(matches(secondProfile, secondPublication, "Publication", null, INSERT)));
        assertThat(queueRecords, hasItem(matches(firstProfile, thirdPublication, "Publication", null, INSERT)));
        assertThat(queueRecords, hasItem(matches(secondProfile, thirdPublication, "Publication", "12345", UPDATE)));
    }

    @Test
    public void testOrcidQueueRecalculationOnProfilePreferenceUpdate() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-0000-0012-2345")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withSubject("Math")
            .withOrcidSynchronizationProfilePreference(BIOGRAPHICAL)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> records = orcidQueueService.findAll(context);
        assertThat(records, hasSize(1));
        assertThat(records, hasItem(matches(profile, "KEYWORDS", null, "dc.subject::Math", "Math", INSERT)));

        addMetadata(profile, "person", "identifier", "rid", "ID", null);
        addMetadata(profile, "dspace", "orcid", "sync-profile", IDENTIFIERS.name(), null);

        context.commit();

        records = orcidQueueService.findAll(context);
        assertThat(records, hasSize(2));
        assertThat(records, hasItem(matches(profile, "KEYWORDS", null, "dc.subject::Math", "Math", INSERT)));
        assertThat(records, hasItem(matches(profile, "EXTERNAL_IDS", null, "person.identifier.rid::ID", "ID", INSERT)));

        removeMetadata(profile, "dspace", "orcid", "sync-profile");

        context.commit();

        assertThat(orcidQueueService.findAll(context), empty());

    }

    @Test
    public void testWithMetadataFieldToIgnore() throws Exception {
        configurationService.addPropertyValue("orcid.linkable-metadata-fields.ignore", "dc.contributor.author");
        configurationService.addPropertyValue("orcid.linkable-metadata-fields.ignore", "crisfund.coinvestigators");

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-0000-0012-2345")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationFundingsPreference(ALL)
            .withOrcidSynchronizationPublicationsPreference(ALL)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item firstPublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Another Test publication")
            .withEditor("Test User", profile.getID().toString())
            .build();

        Item secondPublication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Another Test publication")
            .withAuthor("Test User", profile.getID().toString())
            .withEditor("Test User", profile.getID().toString())
            .build();

        ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        Collection fundingCollection = createCollection("Fundings", "Funding");

        Item firstFunding = ItemBuilder.createItem(context, fundingCollection)
            .withTitle("Test funding")
            .withFundingInvestigator("Test User", profile.getID().toString())
            .build();

        ItemBuilder.createItem(context, fundingCollection)
            .withTitle("Another funding")
            .withFundingCoInvestigator("Test User", profile.getID().toString())
            .build();

        context.restoreAuthSystemState();

        List<OrcidQueue> records = orcidQueueService.findAll(context);
        assertThat(records, hasSize(3));
        assertThat(records, hasItem(matches(profile, firstPublication, "Publication", null, INSERT)));
        assertThat(records, hasItem(matches(profile, secondPublication, "Publication", null, INSERT)));
        assertThat(records, hasItem(matches(profile, firstFunding, "Funding", null, INSERT)));

    }

    @Test
    public void testWithManyInsertionAndDeletionOfSameMetadataValue() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationProfilePreference(BIOGRAPHICAL)
            .withSubject("Science")
            .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> queueRecords = orcidQueueService.findAll(context);
        assertThat(queueRecords, hasSize(1));
        assertThat(queueRecords.get(0), matches(profile, "KEYWORDS", null,
            "dc.subject::Science", "Science", INSERT));

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType(KEYWORDS.name())
            .withDescription("Science")
            .withMetadata("dc.subject::Science")
            .withOperation(OrcidOperation.INSERT)
            .withPutCode("12345")
            .withStatus(201)
            .build();

        removeMetadata(profile, "dc", "subject", null);

        context.commit();

        queueRecords = orcidQueueService.findAll(context);
        assertThat(queueRecords, hasSize(1));
        assertThat(queueRecords.get(0), matches(profile, "KEYWORDS", "12345",
            "dc.subject::Science", "Science", DELETE));

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType(KEYWORDS.name())
            .withDescription("Science")
            .withMetadata("dc.subject::Science")
            .withOperation(OrcidOperation.DELETE)
            .withStatus(204)
            .build();

        addMetadata(profile, "dc", "subject", null, "Science", null);

        context.commit();

        queueRecords = orcidQueueService.findAll(context);
        assertThat(queueRecords, hasSize(1));
        assertThat(queueRecords.get(0), matches(profile, "KEYWORDS", null,
            "dc.subject::Science", "Science", INSERT));

        OrcidHistoryBuilder.createOrcidHistory(context, profile, profile)
            .withRecordType(KEYWORDS.name())
            .withDescription("Science")
            .withMetadata("dc.subject::Science")
            .withOperation(OrcidOperation.INSERT)
            .withPutCode("12346")
            .withStatus(201)
            .build();

        removeMetadata(profile, "dc", "subject", null);

        context.commit();

        queueRecords = orcidQueueService.findAll(context);
        assertThat(queueRecords, hasSize(1));
        assertThat(queueRecords.get(0), matches(profile, "KEYWORDS", "12346",
            "dc.subject::Science", "Science", DELETE));

    }

    @Test
    public void testOrcidQueueRecordCreationForPublicationWithNotFoundAuthority() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationPublicationsPreference(ALL)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("First User", UUID.randomUUID().toString())
            .withAuthor("Test User", profile.getID().toString())
            .build();

        context.restoreAuthSystemState();
        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));
    }

    @Test
    public void testOrcidQueueWithItemVersioning() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationPublicationsPreference(ALL)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("Test User", profile.getID().toString())
            .build();

        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));

        Version newVersion = versioningService.createNewVersion(context, publication);
        Item newPublication = newVersion.getItem();
        assertThat(newPublication.isArchived(), is(false));

        context.commit();

        orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, publication, "Publication", INSERT));

        WorkspaceItem workspaceItem = workspaceItemService.findByItem(context, newVersion.getItem());
        installItemService.installItem(context, workspaceItem);

        context.commit();

        context.restoreAuthSystemState();

        orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, newPublication, "Publication", INSERT));

    }

    @Test
    public void testOrcidQueueUpdateWithItemVersioning() throws Exception {

        context.turnOffAuthorisationSystem();

        Item profile = ItemBuilder.createItem(context, profileCollection)
            .withTitle("Test User")
            .withOrcidIdentifier("0000-1111-2222-3333")
            .withOrcidAccessToken("ab4d18a0-8d9a-40f1-b601-a417255c8d20", eperson)
            .withOrcidSynchronizationPublicationsPreference(ALL)
            .build();

        Collection publicationCollection = createCollection("Publications", "Publication");

        Item publication = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .build();

        OrcidHistory orcidHistory = OrcidHistoryBuilder.createOrcidHistory(context, profile, publication)
            .withDescription("Test publication")
            .withOperation(OrcidOperation.INSERT)
            .withPutCode("12345")
            .withStatus(201)
            .build();

        addMetadata(publication, "dc", "contributor", "author", "Test User", profile.getID().toString());

        context.commit();

        List<OrcidQueue> orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, publication, "Publication", "12345", UPDATE));

        Version newVersion = versioningService.createNewVersion(context, publication);
        Item newPublication = newVersion.getItem();
        assertThat(newPublication.isArchived(), is(false));

        context.commit();

        orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, publication, "Publication", "12345", UPDATE));

        WorkspaceItem workspaceItem = workspaceItemService.findByItem(context, newVersion.getItem());
        installItemService.installItem(context, workspaceItem);

        context.commit();

        context.restoreAuthSystemState();

        orcidQueueRecords = orcidQueueService.findAll(context);
        assertThat(orcidQueueRecords, hasSize(1));
        assertThat(orcidQueueRecords.get(0), matches(profile, newPublication, "Publication", "12345", UPDATE));

        orcidHistory = context.reloadEntity(orcidHistory);
        assertThat(orcidHistory.getEntity(), is(newPublication));

    }

    private void addMetadata(Item item, String schema, String element, String qualifier, String value,
        String authority) throws Exception {
        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        itemService.addMetadata(context, item, schema, element, qualifier, null, value, authority, 600);
        itemService.update(context, item);
        context.restoreAuthSystemState();
    }

    private void removeMetadata(Item item, String schema, String element, String qualifier) throws Exception {
        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> metadata = itemService.getMetadata(item, schema, element, qualifier, Item.ANY);
        itemService.removeMetadataValues(context, item, metadata);
        itemService.update(context, item);
        context.restoreAuthSystemState();
    }

    private Collection createCollection(String name, String entityType) {
        return CollectionBuilder.createCollection(context, parentCommunity)
            .withName(name)
            .withEntityType(entityType)
            .build();
    }

    private void changeProfilePublicationSyncPreference(Item profile, OrcidEntitySyncPreference preference)
            throws SQLException {
        context.turnOffAuthorisationSystem();
        orcidQueueService.recalculateOrcidQueue(context, profile, OrcidEntityType.PUBLICATION, preference);
        context.commit();
        context.restoreAuthSystemState();
    }
}
