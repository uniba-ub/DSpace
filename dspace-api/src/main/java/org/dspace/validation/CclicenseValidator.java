/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.validation;

import static org.dspace.validation.service.ValidationService.OPERATION_PATH_SECTIONS;
import static org.dspace.validation.util.ValidationUtils.addError;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.license.CreativeCommonsServiceImpl;
import org.dspace.services.ConfigurationService;
import org.dspace.validation.model.ValidationError;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * This class validates that the Creative Commons License has been granted for the
 * in-progress submission.
 *
 * @author Mattia Vianelli (Mattia.Vianelli@4science.com)
 */
public class CclicenseValidator implements SubmissionStepValidator {

    /**
     * Construct a Creative Commons License configuration.
     * @param configurationService DSpace configuration provided by the DI container.
     */
    @Inject
    public CclicenseValidator(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    private final ConfigurationService configurationService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private CreativeCommonsServiceImpl creativeCommonsService;

    @Override
    public List<ValidationError> validate(Context context, InProgressSubmission<?> obj, SubmissionStepConfig config) {
        try {
            return performValidation(obj.getItem(), config);
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }

    public static final String ERROR_VALIDATION_CCLICENSEREQUIRED = "error.validation.cclicense.required";

    private String name;

    private Boolean required;

    /**
     * Perform validation on the item and config(ccLicense).
     * @param item The item to be validated.
     * @param config The configuration for the submission step for cclicense.
     * @return A list of validation errors.
     * @throws SQLException If there is a problem accessing the database.
     */
    private List<ValidationError> performValidation(Item item, SubmissionStepConfig config) throws SQLException {
        List<ValidationError> errors = new ArrayList<>();

        if (this.isRequired()) {
            errors.addAll(validateLicense(item, config));
        }

        return errors;
    }

    /**
     * Validate the license of the item.
     * @param item The item whose cclicense is to be validated.
     * @param config The configuration for the submission step for cclicense.
     * @return A list of validation errors.
     */
    private List<ValidationError> validateLicense(Item item, SubmissionStepConfig config) {
        List<ValidationError> errors = new ArrayList<>();

        String licenseURI = creativeCommonsService.getLicenseURI(item);
        if (licenseURI == null || licenseURI.isBlank()) {
            addError(errors, ERROR_VALIDATION_CCLICENSEREQUIRED, "/" + OPERATION_PATH_SECTIONS + "/" + config.getId());
        }

        return errors;
    }

    public ItemService getItemService() {
        return itemService;
    }

    public void setItemService(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * Check if at least one Creative Commons License is required when submitting a new Item.
     * @return true if a Creative Commons License is required setting true for the property cc.license.required.
     */
    public Boolean isRequired() {
        if (required == null) {
            return configurationService.getBooleanProperty("cc.license.required", false);
        }
        return required;
    }

    /**
     * Set whether at least one Creative Commons License is required when submitting a new Item.
     * @param required true if a Creative Commons License is required.
     */
    public void setRequired(Boolean required) {
        this.required = required;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
