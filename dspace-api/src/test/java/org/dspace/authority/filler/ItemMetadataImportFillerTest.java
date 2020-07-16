/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authority.filler;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.UUID.randomUUID;
import static org.dspace.content.Item.ANY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.dspace.authority.filler.MetadataConfiguration.MappingDetails;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Test suite with Mockito for the {@link ItemMetadataImportFiller}.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ItemMetadataImportFillerTest {

    @Mock
    private ItemService itemService;

    @Mock
    private Context context;

    @InjectMocks
    private ItemMetadataImportFiller cut;


    /**
     * Verify that the allowsUpdate method returns false with an empty
     * configuration.
     */
    @Test
    public void testAllowsUpdateWithoutConfiguration() {
        MetadataValue metadataValue = buildMetadataValue("dc", "contributor", "author", "Mario Rossi");
        Item itemToFill = buildItem(UUID.randomUUID());
        boolean allowsUpdate = cut.allowsUpdate(context, metadataValue, itemToFill);
        assertFalse("With an empty configuration update should not be allowed", allowsUpdate);
    }

    /**
     * Verify that the allowsUpdate method returns false without the given metadata
     * configuration.
     */
    @Test
    public void testAllowsUpdateWithoutMetadataConfiguration() {
        MetadataValue metadataValue = buildMetadataValue("dc", "contributor", "author", "Mario Rossi");
        Item itemToFill = buildItem(UUID.randomUUID());

        Map<String, MetadataConfiguration> configurations = new HashMap<>();
        configurations.put("dc.contributor.editor", new MetadataConfiguration());
        cut.setConfigurations(configurations);

        boolean allowsUpdate = cut.allowsUpdate(context, metadataValue, itemToFill);
        assertFalse("Without a configuration update should not be allowed", allowsUpdate);
    }

    /**
     * Verify that the allowsUpdate method returns the default value if the metadata
     * configuration has no updateEnabled set.
     */
    @Test
    public void testAllowsUpdateWithFalseAllowsUpdateByDefault() {
        MetadataValue metadataValue = buildMetadataValue("dc", "contributor", "author", "Mario Rossi");
        Item itemToFill = buildItem(UUID.randomUUID());

        Map<String, MetadataConfiguration> configurations = new HashMap<>();
        configurations.put("dc.contributor.author", buildMetadataConfig(null, emptyMap()));
        cut.setConfigurations(configurations);
        cut.setAllowsUpdateByDefault(false);

        boolean allowsUpdate = cut.allowsUpdate(context, metadataValue, itemToFill);
        assertFalse("Should be equals to the allowsUpdateByDefault value", allowsUpdate);
    }

    /**
     * Verify that the allowsUpdate method returns the default value if the metadata
     * configuration has no updateEnabled set.
     */
    @Test
    public void testAllowsUpdateWithTrueAllowsUpdateByDefault() {
        MetadataValue metadataValue = buildMetadataValue("dc", "contributor", "author", "Mario Rossi");
        Item itemToFill = buildItem(UUID.randomUUID());

        Map<String, MetadataConfiguration> configurations = new HashMap<>();
        configurations.put("dc.contributor.author", buildMetadataConfig(null, emptyMap()));
        cut.setConfigurations(configurations);
        cut.setAllowsUpdateByDefault(true);

        boolean allowsUpdate = cut.allowsUpdate(context, metadataValue, itemToFill);
        assertTrue("Should be equals to the allowsUpdateByDefault value", allowsUpdate);
    }

    /**
     * Verify that the allowsUpdate method returns the configured updateEnabled
     * value.
     */
    @Test
    public void testAllowsUpdateWithTrueMetadataConfiguration() {
        MetadataValue metadataValue = buildMetadataValue("dc", "contributor", "author", "Mario Rossi");
        Item itemToFill = buildItem(UUID.randomUUID());

        Map<String, MetadataConfiguration> configurations = new HashMap<>();
        configurations.put("dc.contributor.author", buildMetadataConfig(true, emptyMap()));
        cut.setConfigurations(configurations);

        boolean allowsUpdate = cut.allowsUpdate(context, metadataValue, itemToFill);
        assertTrue("Should be equals to the configurated updateEnabled value", allowsUpdate);
    }

    /**
     * Verify that the allowsUpdate method returns the configured updateEnabled
     * value.
     */
    @Test
    public void testAllowsUpdateWithFalseMetadataConfiguration() {
        MetadataValue metadataValue = buildMetadataValue("dc", "contributor", "author", "Mario Rossi");
        Item itemToFill = buildItem(UUID.randomUUID());

        Map<String, MetadataConfiguration> configurations = new HashMap<>();
        configurations.put("dc.contributor.author", buildMetadataConfig(false, emptyMap()));
        cut.setConfigurations(configurations);

        boolean allowsUpdate = cut.allowsUpdate(context, metadataValue, itemToFill);
        assertFalse("Should be equals to the configurated updateEnabled value", allowsUpdate);
    }

    /**
     * Verify that the fillItem method does not add any metadata to the item if the
     * given metadata configuration is empty.
     *
     * @throws SQLException not expected
     */
    @Test
    public void testFillItemWithAnEmptyMetadataConfiguration() throws SQLException {
        MetadataValue metadataValue = buildMetadataValue("dc", "contributor", "author", "Mario Rossi");
        Item itemToFill = buildItem(UUID.randomUUID());

        Map<String, MetadataConfiguration> configurations = new HashMap<>();
        configurations.put("dc.contributor.author", buildMetadataConfig(true, emptyMap()));
        cut.setConfigurations(configurations);

        cut.fillItem(context, metadataValue, itemToFill);
        verifyNoMoreInteractions(context, itemService);
    }

    /**
     * Verify that the fillItem method add all the metadata to the related item with
     * an useAll configuration set to true.
     *
     * @throws SQLException not expected
     */
    @Test
    public void testFillItemWithUseAllConfigurationSetToTrue() throws SQLException {
        MetadataValue metadataValue = buildMetadataValue(randomUUID(), "dc", "contributor", "author", "Mario Rossi", 0);
        Item sourceItem = (Item) metadataValue.getDSpaceObject();
        Item itemToFill = buildItem(randomUUID());

        Map<String, MappingDetails> mappingDetails = new HashMap<>();
        mappingDetails.put("dc.contributor.affiliation", buildMappingDetails(true));

        Map<String, MetadataConfiguration> configurations = new HashMap<>();
        configurations.put("dc.contributor.author", buildMetadataConfig(true, mappingDetails));
        cut.setConfigurations(configurations);

        MetadataValue firstMetadata = buildMetadataValue("dc", "contributor", "affiliation", "4Science");
        MetadataValue secondMetadata = buildMetadataValue("dc", "contributor", "affiliation", "Affiliation");
        List<MetadataValue> expectedMetadata = asList(firstMetadata, secondMetadata);
        when(itemService.getMetadataByMetadataString(sourceItem, "dc.contributor.affiliation"))
                .thenReturn(expectedMetadata);

        when(itemService.getMetadataByMetadataString(itemToFill, "dc.contributor.affiliation"))
                .thenReturn(emptyList());

        cut.fillItem(context, metadataValue, itemToFill);

        verify(itemService).getMetadataByMetadataString(itemToFill, "dc.contributor.affiliation");
        verify(itemService).getMetadataByMetadataString(sourceItem, "dc.contributor.affiliation");
        verify(itemService).addMetadata(context, itemToFill, firstMetadata.getMetadataField(), ANY, "4Science");
        verify(itemService).addMetadata(context, itemToFill, secondMetadata.getMetadataField(), ANY, "Affiliation");
        verifyNoMoreInteractions(context, itemService);
    }

    /**
     * Verify that the fillItem method add only a single metadata by place to the
     * related item with the useAll configuration set to false.
     *
     * @throws SQLException not expected
     */
    @Test
    public void testFillItemWithUseAllConfigurationSetToFalse() throws SQLException {
        MetadataValue metadataValue = buildMetadataValue(randomUUID(), "dc", "contributor", "author", "Mario Rossi", 1);
        Item sourceItem = (Item) metadataValue.getDSpaceObject();
        Item itemToFill = buildItem(randomUUID());

        Map<String, MappingDetails> mappingDetails = new HashMap<>();
        mappingDetails.put("dc.contributor.affiliation", buildMappingDetails(false));

        Map<String, MetadataConfiguration> configurations = new HashMap<>();
        configurations.put("dc.contributor.author", buildMetadataConfig(true, mappingDetails));
        cut.setConfigurations(configurations);

        MetadataValue firstMetadata = buildMetadataValue("dc", "contributor", "affiliation", "4Science");
        MetadataValue secondMetadata = buildMetadataValue("dc", "contributor", "affiliation", "Affiliation");
        List<MetadataValue> archivedItemAffiliationMetadata = asList(firstMetadata, secondMetadata);
        when(itemService.getMetadataByMetadataString(sourceItem, "dc.contributor.affiliation"))
                .thenReturn(archivedItemAffiliationMetadata);

        List<MetadataValue> itemToFillMetadata = asList(buildMetadataValue("dc", "contributor", "affiliation", "old"));
        when(itemService.getMetadataByMetadataString(itemToFill, "dc.contributor.affiliation"))
                .thenReturn(itemToFillMetadata);

        cut.fillItem(context, metadataValue, itemToFill);

        verify(itemService).getMetadataByMetadataString(itemToFill, "dc.contributor.affiliation");
        verify(itemService).removeMetadataValues(context, itemToFill, itemToFillMetadata);
        verify(itemService).getMetadataByMetadataString(sourceItem, "dc.contributor.affiliation");
        verify(itemService).addMetadata(context, itemToFill, secondMetadata.getMetadataField(), ANY, "Affiliation");
        verifyNoMoreInteractions(context, itemService);
    }

    /**
     * Verify that the fillItem method add no metadata to the related item when the
     * useAll configuration set to false and the input metadata place is greater
     * than the found related metadata.
     *
     * @throws SQLException not expected
     */
    @Test
    public void testFillItemWithUseAllConfigurationSetToFalseAndPlaceTooHigh() throws SQLException {
        MetadataValue metadataValue = buildMetadataValue(randomUUID(), "dc", "contributor", "author", "Mario Rossi", 2);
        Item sourceItem = (Item) metadataValue.getDSpaceObject();
        Item itemToFill = buildItem(randomUUID());

        Map<String, MappingDetails> mappingDetails = new HashMap<>();
        mappingDetails.put("dc.contributor.affiliation", buildMappingDetails(false));

        Map<String, MetadataConfiguration> configurations = new HashMap<>();
        configurations.put("dc.contributor.author", buildMetadataConfig(true, mappingDetails));
        cut.setConfigurations(configurations);

        MetadataValue firstMetadata = buildMetadataValue("dc", "contributor", "affiliation", "4Science");
        MetadataValue secondMetadata = buildMetadataValue("dc", "contributor", "affiliation", "Affiliation");
        List<MetadataValue> expectedMetadata = asList(firstMetadata, secondMetadata);
        when(itemService.getMetadataByMetadataString(sourceItem, "dc.contributor.affiliation"))
                .thenReturn(expectedMetadata);

        cut.fillItem(context, metadataValue, itemToFill);

        verify(itemService).getMetadataByMetadataString(sourceItem, "dc.contributor.affiliation");
        verifyNoMoreInteractions(context, itemService);
    }

    private MappingDetails buildMappingDetails(boolean useAll) {
        MappingDetails mappingDetails = new MappingDetails();
        mappingDetails.setUseAll(useAll);
        return mappingDetails;
    }

    private MetadataValue buildMetadataValue(UUID itemId, String schema, String element,
            String qualifier, String value, int place) {

        MetadataSchema metadataSchema = buildMetadataSchema(schema);
        MetadataField field = buildMetadataField(metadataSchema, element, qualifier);
        MetadataValue metadataValue = buildMetadataValue(field, value);
        Item item = buildItem(itemId);
        when(metadataValue.getDSpaceObject()).thenReturn(item);
        when(metadataValue.getPlace()).thenReturn(place);
        return metadataValue;

    }

    private MetadataValue buildMetadataValue(String schema, String element, String qualifier, String value) {
        MetadataSchema metadataSchema = buildMetadataSchema(schema);
        MetadataField field = buildMetadataField(metadataSchema, element, qualifier);
        return buildMetadataValue(field, value);
    }

    private MetadataValue buildMetadataValue(MetadataField field, String value) {
        MetadataValue metadata = mock(MetadataValue.class);
        when(metadata.getValue()).thenReturn(value);
        when(metadata.getMetadataField()).thenReturn(field);
        return metadata;
    }

    private MetadataField buildMetadataField(MetadataSchema schema, String element, String qualifier) {
        MetadataField field = mock(MetadataField.class);
        when(field.getMetadataSchema()).thenReturn(schema);
        when(field.getElement()).thenReturn(element);
        when(field.getQualifier()).thenReturn(qualifier);
        String metadataAsString = metadataAsString(schema.getName(), element, qualifier, '.');
        when(field.toString('.')).thenReturn(metadataAsString);
        return field;
    }

    private MetadataSchema buildMetadataSchema(String name) {
        MetadataSchema schema = mock(MetadataSchema.class);
        when(schema.getName()).thenReturn(name);
        return schema;
    }

    private String metadataAsString(String schema, String element, String qualifier, char separator) {
        if (qualifier == null) {
            return schema + separator + element;
        } else {
            return schema + separator + element + separator + qualifier;
        }
    }

    private Item buildItem(UUID id) {
        Item item = mock(Item.class);
        when(item.getID()).thenReturn(id);
        return item;
    }

    private MetadataConfiguration buildMetadataConfig(Boolean updateEnabled, Map<String, MappingDetails> mapping) {
        MetadataConfiguration config = new MetadataConfiguration();
        config.setUpdateEnabled(updateEnabled);
        config.setMapping(mapping);
        return config;
    }

}
