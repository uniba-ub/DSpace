/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer.impl;

import java.sql.SQLException;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.enhancer.AbstractItemEnhancer;
import org.dspace.content.enhancer.ItemEnhancer;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.services.ConfigurationService;
import org.dspace.util.SimpleMapConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link ItemEnhancer} that add metadata values on the given
 * item from the result of some mapConverter
 * Only consider 1 to 1 value enhancement and no additional security/authority/language settings form the origin value.
 * e.g. dc.type (dspace) -> dc.type (coar)
 *
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 *
 */
public class MapConverterValueEnhancer extends AbstractItemEnhancer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapConverterValueEnhancer.class);

    @Autowired
    private ItemService itemService;

	@Autowired
	private MetadataFieldService metadatafieldService;

	@Autowired
	private ConfigurationService configurationService;

    private String sourceEntityType;

    private String sourceItemMetadataField;
    
    private String targetItemMetadataField;

	private boolean useDefaultLanguage;

	private SimpleMapConverter converter;

    @Override
    public boolean canEnhance(Context context, Item item) {
        return sourceEntityType == null || sourceEntityType.equals(itemService.getEntityType(item));
    }

    @Override
    public void enhance(Context context, Item item) {
        try {
		if (StringUtils.isBlank(sourceItemMetadataField) || Objects.isNull(converter) || StringUtils.isBlank(targetItemMetadataField)) return;
			String sourceval, targetval, calculatedval;
			sourceval = itemService.getMetadata(item, sourceItemMetadataField);
			targetval = itemService.getMetadata(item, targetItemMetadataField);
			if (StringUtils.isNotBlank(sourceval)) {
				calculatedval = converter.getValue(sourceval);
				if (StringUtils.isNotBlank(targetval) && !targetval.contentEquals(calculatedval)) {
					// replace mdv if it's different
					removeTargetMetadata(context, item);
					addTargetMetadata(context, item, calculatedval);
				} else if (StringUtils.isBlank(targetval)) {
					// set new value
					addTargetMetadata(context, item, calculatedval);
				}
			} else if (StringUtils.isBlank(sourceval) && StringUtils.isNotBlank(targetval)) {
				// remove value
				removeTargetMetadata(context, item);
			}
        } catch (SQLException e) {
            LOGGER.error("An error occurs enhancing item with id {}: {}", item.getID(), e.getMessage(), e);
            throw new SQLRuntimeException(e);
        }
    }
	private void addTargetMetadata(Context context, Item item, String value) throws SQLException {
		MetadataField targetmd = metadatafieldService.findByString(context, targetItemMetadataField, '.');
		if (targetmd != null) {
			String lang  = (this.useDefaultLanguage) ? this.configurationService.getProperty("default.language") : null;
			itemService.addMetadata(context, item, targetmd, lang, value);
		} else {
			LOGGER.error("No valid metadatavalue to enhance specified");
		}
	}
	
	private void removeTargetMetadata(Context context, Item item) throws SQLException {
		MetadataField targetmd = metadatafieldService.findByString(context, targetItemMetadataField, '.');
		if (targetmd != null) {
				itemService.clearMetadata(context, item, targetmd.getMetadataSchema().getName(), targetmd.getElement(), targetmd.getQualifier(), Item.ANY);
		} else {
			LOGGER.error("No valid metadatavalue to enhance specified");
		}
	}

    public void setSourceEntityType(String sourceEntityType) {
        this.sourceEntityType = sourceEntityType;
    }

    public void setTargetItemMetadataField(String targetItemMetadataField) {
        this.targetItemMetadataField = targetItemMetadataField;
    }

    public void setSourceItemMetadataField(String sourceItemMetadataField) {
        this.sourceItemMetadataField = sourceItemMetadataField;
    }

	public void setConverter(SimpleMapConverter converter) {
		this.converter = converter;
	}

	public void setUseDefaultLanguage(boolean useDefaultLanguage) {
		this.useDefaultLanguage = useDefaultLanguage;
	}
}
