/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.eperson.EPerson;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;


/**
 * I18nUtil.java
 *
 * Some Utilities for i18n Support.
 * - getting the default Locale for this DSpace Instance
 * - getting all supported Locales for this DSpace Instance
 * - getting email template, help file, input forms for a given Locale
 *
 * @author Bernadette Schlonsok and Claudia Juergen
 * @version 1.0
 */

public class I18nUtil {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(I18nUtil.class);

    // delimiters between elements of UNIX/POSIX locale spec, e.g. en_US.UTF-8
    private static final String LOCALE_DELIMITERS = " _.";

    private static MessageSource messageSource;

    /**
     * Default constructor
     */
    private I18nUtil() { }

    /**
     * Gets the default locale as defined in dspace.cfg If no default locale is
     * defined, the Locale of the JVM is used
     *
     * @return defaultLocale
     * the default Locale for this DSpace instance
     */
    public static Locale getDefaultLocale() {
        ConfigurationService config = DSpaceServicesFactory.getInstance().getConfigurationService();
        // First, try configured default locale
        Locale defaultLocale = null;
        if (config.hasProperty("default.locale")) {
            defaultLocale = makeLocale(config.getProperty("default.locale"));
        }

        // Finally, get the Locale of the JVM
        if (defaultLocale == null) {
            defaultLocale = Locale.getDefault();
        }

        return defaultLocale;
    }

    // Translate a string locale specification (e.g. "en_US.UTF-8") into Locale
    // This is needed because Locale constructor expects args for
    // language, territory, and variant to be separated already.
    private static Locale makeLocale(String localeSpec) {
        StringTokenizer st = new StringTokenizer(localeSpec, LOCALE_DELIMITERS);
        int countTokens = st.countTokens();
        switch (countTokens) {
            case 1:
                return new Locale(st.nextToken().trim());
            case 2:
                return new Locale(st.nextToken().trim(), st.nextToken().trim());
            case 3:
                return new Locale(st.nextToken().trim(), st.nextToken().trim(),
                                  st.nextToken().trim());
            default:
                return null;
        }
    }

    /**
     * Get the Locale for a specified EPerson. If the language is missing,
     * return the default Locale for the repository.
     *
     * @param ep Eperson
     * @return Locale
     */
    public static Locale getEPersonLocale(EPerson ep) {
        if (ep == null) {
            log.info("No EPerson specified, returning default locale");
            return I18nUtil.getDefaultLocale();
        }

        String lang = ep.getLanguage();

        if (StringUtils.isBlank(lang)) {
            log.info("No language specified for EPerson " + ep.getID() + ", returning default locale");
            return I18nUtil.getDefaultLocale();
        }

        return I18nUtil.getSupportedLocale(new Locale(lang));
    }

    /**
     * get the available Locales for the User Interface as defined in dspace.cfg
     * returns an array of Locales or null
     *
     * @return an array of supported Locales or null
     */
    public static Locale[] getSupportedLocales() {
        ConfigurationService config = DSpaceServicesFactory.getInstance().getConfigurationService();

        String[] locales = config.getArrayProperty("webui.supported.locales");
        if (locales != null && locales.length > 0) {
            return parseLocales(locales);
        } else {
            Locale[] availableLocales = new Locale[1];
            availableLocales[0] = getDefaultLocale();
            return availableLocales;
        }
    }

    /**
     * Gets the appropriate supported Locale according for a given Locale If
     * no appropriate supported locale is found, the DEFAULTLOCALE is used
     *
     * @param locale Locale to find the corresponding Locale
     * @return supportedLocale
     * Locale for session according to locales supported by this DSpace instance as set in dspace.cfg
     */

    public static Locale getSupportedLocale(Locale locale) {

        Locale[] availableLocales = getSupportedLocales();
        boolean isSupported = false;
        Locale supportedLocale = null;
        String testLocale = "";
        if (availableLocales == null) {
            supportedLocale = getDefaultLocale();
        } else {
            if (!locale.getVariant().equals("")) {
                testLocale = locale.toString();
                for (int i = 0; i < availableLocales.length; i++) {
                    if (testLocale.equalsIgnoreCase(availableLocales[i]
                                                        .toString())) {
                        isSupported = true;
                        supportedLocale = availableLocales[i];
                    }

                }
            }

            if (!(isSupported && locale.getCountry().equals(""))) {
                testLocale = locale.getLanguage() + "_"
                    + locale.getCountry();

                for (int i = 0; i < availableLocales.length; i++) {
                    if (testLocale.equalsIgnoreCase(availableLocales[i]
                                                        .toString())) {
                        isSupported = true;
                        supportedLocale = availableLocales[i];
                    }
                }

            }
            if (!isSupported) {
                testLocale = locale.getLanguage();

                for (int i = 0; i < availableLocales.length; i++) {
                    if (testLocale.equalsIgnoreCase(availableLocales[i]
                                                        .toString())) {
                        isSupported = true;
                        supportedLocale = availableLocales[i];
                    }

                }
            }
            if (!isSupported) {
                supportedLocale = getDefaultLocale();
            }
        }
        return supportedLocale;
    }

    /**
     * Gets the appropriate supported Locale according for a given Locale If
     * no appropriate supported locale is found, the DEFAULTLOCALE is used
     *
     * @param locale String to find the corresponding Locale
     * @return supportedLocale
     * Locale for session according to locales supported by this DSpace instance as set in dspace.cfg
     */
    public static Locale getSupportedLocale(String locale) {
        Locale currentLocale = null;
        if (locale != null) {
            currentLocale = I18nUtil.getSupportedLocale(new Locale(locale));
        } else {
            currentLocale = I18nUtil.getDefaultLocale();
        }
        return currentLocale;
    }

    /**
     * Get the appropriate localized version of submission-forms.xml according to language settings
     *
     * @param locale Locale, the local to get the submission-forms.xml for
     * @return String - localized filename for submission-forms.xml
     */
    public static String getInputFormsFileName(Locale locale) {
        /** Name of the form definition XML file */
        final String FORM_DEF_FILE = "submission-forms";
        final String FILE_TYPE = ".xml";
        String defsFilename = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("dspace.dir")
            + File.separator + "config" + File.separator + FORM_DEF_FILE;
        String fileName = getFilename(locale, defsFilename, FILE_TYPE);
        return fileName;
    }

    public static String getControlledVocabularyFileName(Locale locale, String vocabularyName) {
        String fileName = "";
        final String FILE_TYPE = ".xml";
        String defsFilename = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("dspace.dir")
                              + File.separator + "config/controlled-vocabularies/" + File.separator + vocabularyName;
        fileName = getFilename(locale, defsFilename, FILE_TYPE);
        return fileName;
    }

    /**
     * Get the i18n message string for a given key and use the default Locale.
     *
     * @param key String - name of the key to get the message for
     * @return message
     * String of the message
     */
    public static String getMessage(String key) {
        return getMessage(key.trim(), getDefaultLocale());
    }

    /**
     * Get the i18n message string for a given key and locale
     *
     * @param key    String - name of the key to get the message for
     * @param locale Locale, to get the message for
     * @return message
     * String of the message
     */
    public static String getMessage(String key, Locale locale) {
        if (locale == null) {
            locale = getDefaultLocale();
        }
        ResourceBundle.Control control =
            ResourceBundle.Control.getNoFallbackControl(
                ResourceBundle.Control.FORMAT_DEFAULT);

        ResourceBundle messages = ResourceBundle.getBundle("Messages", locale, control);
        try {
            String message = messages.getString(key.trim());
            return message;
        } catch (MissingResourceException e) {
            log.warn("'" + key + "' translation undefined in locale '"
                          + locale.toString() + "'");
            return key;
        }
    }

    /**
     * Get the i18n message string for a given key and context
     *
     * @param key String - name of the key to get the message for
     * @param c   Context having the desired Locale
     * @return message
     * String of the message
     */
    public static String getMessage(String key, Context c) {
        return getMessage(key.trim(), c.getCurrentLocale());
    }


    /**
     * Get the appropriate localized version of the default.license according to language settings
     *
     * @param context the current DSpace context
     * @return fileName
     * String - localized filename for default.license
     */
    public static String getDefaultLicense(Context context) {
        Locale locale = context.getCurrentLocale();
        /** Name of the default license */
        final String DEF_LIC_FILE = "default";
        final String FILE_TYPE = ".license";
        String defsFilename = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("dspace.dir")
            + File.separator + "config" + File.separator + DEF_LIC_FILE;

        String fileName = getFilename(locale, defsFilename, FILE_TYPE);

        return fileName;
    }

    /**
     * Get the appropriate localized version of a file according to language settings
     * e. g. help files in jsp/help/
     *
     * @param locale   Locale to get the file for
     * @param fileName String fileName, to get the localized file for
     * @param fileType String file extension
     * @return localizedFileName
     * String - localized filename
     */
    private static String getFilename(Locale locale, String fileName, String fileType) {
        String localizedFileName = null;
        boolean fileFound = false;
        // with Language, Country, Variant
        String fileNameLCV = null;
        // with Language, Country
        String fileNameLC = null;
        // with Language
        String fileNameL = fileName + "_" + locale.getLanguage();

        if (fileType == null) {
            fileType = "";
        }

        if (!locale.getCountry().isEmpty()) {
            fileNameLC = fileName + "_" + locale.getLanguage() + "_"
                + locale.getCountry();

            if (!locale.getVariant().isEmpty()) {
                fileNameLCV = fileName + "_" + locale.getLanguage() + "_"
                    + locale.getCountry() + "_" + locale.getVariant();
            }
        }

        if (fileNameLCV != null && !fileFound) {
            File fileTmp = new File(fileNameLCV + fileType);
            if (fileTmp.exists()) {
                fileFound = true;
                localizedFileName = fileNameLCV + fileType;
            }
        }

        if (fileNameLC != null && !fileFound) {
            File fileTmp = new File(fileNameLC + fileType);
            if (fileTmp.exists()) {
                fileFound = true;
                localizedFileName = fileNameLC + fileType;
            }
        }

        if (!fileFound) {
            File fileTmp = new File(fileNameL + fileType);
            if (fileTmp.exists()) {
                fileFound = true;
                localizedFileName = fileNameL + fileType;
            }
        }
        if (!fileFound) {
            localizedFileName = fileName + fileType;
        }
        return localizedFileName;
    }


    /**
     * Get the appropriate localized version of an email template according to language settings
     *
     * @param locale Locale for this request
     * @param name   String - base name of the email template
     * @return templateName
     * String - localized filename of an email template
     */
    public static String getEmailFilename(Locale locale, String name) {
        String templateFile = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("dspace.dir")
            + File.separator + "config" + File.separator + "emails"
            + File.separator + name;

        String templateName = getFilename(locale, templateFile, "");
        return templateName;
    }

    /**
     * Creates array of Locales from text list of locale-specifications.
     * Used to parse lists in DSpace configuration properties.
     *
     * @param locales locale string array
     * @return array of locale results, possibly empty
     */
    public static Locale[] parseLocales(String[] locales) {
        List<Locale> resultList = new ArrayList<>();
        for (String ls : locales) {
            Locale lc = makeLocale(ls);
            if (lc != null) {
                resultList.add(lc);
            }
        }
        return resultList.toArray(new Locale[resultList.size()]);
    }

    /**
     * Check if the input locale is in the list of supported locales
     * @param locale
     * @return true if locale is supported, false otherwise
     */
    public static boolean isSupportedLocale(Locale locale) {
        boolean isSupported = false;
        Locale[] supportedLocales = getSupportedLocales();
        if (supportedLocales != null) {
            for (Locale sLocale: supportedLocales) {
                if (locale.getLanguage().equals(sLocale.getLanguage()) ) {
                    isSupported = true;
                    break;
                }
            }
        }
        return isSupported;
    }

    public static String getMessage(String key, Object[] args, Context c) throws MissingResourceException {
        return getMessage(key.trim(), args, c.getCurrentLocale());
    }

    /**
     * Get the appropriate localized version for the message string for a given key
     * and parameters
     * 
     * @param key    String - name of the key to get the message for
     * @param args   Object[] - arguments for substitution
     * @param locale Locale - to get the message for
     * 
     * @return
     * @throws MissingResourceException
     */
    public static String getMessage(String key, Object[] args, Locale locale) throws MissingResourceException {
        return getMessage(key, args, locale, false);
    }

    /**
     * 
     * Get the appropriate localized version for the message string for a given key
     * and parameters
     * 
     * @param key                String - name of the key to get the message for
     * @param args               Object[] - arguments for substitution
     * @param locale             Locale - to get the message for
     * @param throwExcIfNotFound boolean - false if you want fail silent
     * 
     * @return
     * @throws MissingResourceException
     */
    public static String getMessage(String key, Object[] args, Locale locale, boolean throwExcIfNotFound)
            throws MissingResourceException {
        String message = "";
        if (locale == null) {
            locale = getDefaultLocale();
        }

        try {
            message = getMessageSource().getMessage(key.trim(), args, locale);
        } catch (MissingResourceException | NoSuchMessageException e) {
            if (throwExcIfNotFound) {
                throw new MissingResourceException(e.getMessage(), messageSource.getClass().toString(), key);
            }
            log.error("'" + key + "' translation undefined in locale '" + locale.toString() + "'");
            return key;
        }
        return message;
    }

    public static MessageSource getMessageSource() {
        if (I18nUtil.messageSource == null) {
            DSpace dspace = new DSpace();
            I18nUtil.messageSource = dspace.getServiceManager().getServiceByName("messageSource", MessageSource.class);
        }
        return I18nUtil.messageSource;
    }
}
