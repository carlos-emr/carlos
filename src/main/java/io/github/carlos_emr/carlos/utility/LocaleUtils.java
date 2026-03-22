/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.Map.Entry;
import jakarta.servlet.ServletRequest;

/**
 * Utility class for locale-aware resource bundle message retrieval and geographic data lookup.
 *
 * <p>Provides methods to retrieve localized messages from resource bundles and to look up
 * province/state lists by country code. Falls back to English when a locale-specific
 * resource is not available.
 *
 * @since 2026-03-17
 */
public final class LocaleUtils {
    private static Logger logger = MiscUtils.getLogger();
    private static final Locale DEFAULT_LOCALE;
    public static String BASE_NAME;
    private static HashMap<String, TreeMap<String, String>> provinceCache;

    public LocaleUtils() {
    }

    /**
     * Converts a locale string (e.g., "en_CA") to a {@link Locale} object.
     *
     * @param localeString String the locale string
     * @return Locale the parsed locale
     */
    public static Locale toLocale(String localeString) {
        return org.apache.commons.lang3.LocaleUtils.toLocale(localeString);
    }

    /**
     * Returns a localized message for the given key using the request's locale.
     *
     * @param request ServletRequest the request containing locale information
     * @param key     String the resource bundle key
     * @return String the localized message, or the key itself if not found
     */
    public static String getMessage(ServletRequest request, String key) {
        return getMessage(request.getLocale(), key);
    }

    /**
     * Returns a localized message for the given key using the specified locale string.
     *
     * @param localeString String the locale string (e.g., "en_CA")
     * @param key          String the resource bundle key
     * @return String the localized message, or the key itself if not found
     */
    public static String getMessage(String localeString, String key) {
        return getMessage(toLocale(localeString), key);
    }

    /**
     * Returns a localized message for the given key and locale. Falls back to the
     * default locale (English) if the message is missing for the specified locale.
     *
     * @param locale Locale the target locale
     * @param key    String the resource bundle key
     * @return String the localized message, or the key itself if not found in any locale
     */
    public static String getMessage(Locale locale, String key) {
        try {
            return ResourceBundle.getBundle(BASE_NAME, locale).getString(key);
        } catch (MissingResourceException var5) {
            String message = "Resource not found. BASE_NAME=" + BASE_NAME + ", Locale=" + locale + ", key=" + key;
            logger.error(message);

            try {
                return ResourceBundle.getBundle(BASE_NAME, DEFAULT_LOCALE).getString(key);
            } catch (MissingResourceException var4) {
                message = "Resource not found. BASE_NAME=" + BASE_NAME + ", DEFAULT_LOCALE=" + DEFAULT_LOCALE + ", key=" + key;
                logger.error(message);
                return key;
            }
        }
    }

    /**
     * Returns a sorted map of province/state codes to names for the given country.
     * Results are cached after the first lookup.
     *
     * @param countryCode String the ISO country code (e.g., "CA", "US")
     * @return TreeMap&lt;String, String&gt; province/state codes mapped to names, or {@code null} if unavailable
     * @throws IOException if the properties file cannot be read
     */
    public static TreeMap<String, String> getProvinceStateList(String countryCode) throws IOException {
        TreeMap<String, String> result = (TreeMap) provinceCache.get(countryCode);
        if (result != null) {
            return result;
        } else {
            InputStream is = LocaleUtils.class.getResourceAsStream("/geo/" + countryCode + ".properties");
            if (is == null) {
                return null;
            } else {
                Properties p = new Properties();
                p.load(is);
                result = new TreeMap();
                Iterator i$ = p.entrySet().iterator();

                while (i$.hasNext()) {
                    Entry<Object, Object> entry = (Entry) i$.next();
                    result.put((String) entry.getKey(), (String) entry.getValue());
                }

                provinceCache.put(countryCode, result);
                return result;
            }
        }
    }

    static {
        DEFAULT_LOCALE = Locale.ENGLISH;
        BASE_NAME = "string_tables/strings";
        provinceCache = new HashMap();
    }
}