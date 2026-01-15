//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.utility;

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
import javax.servlet.ServletRequest;

public final class LocaleUtils {
    private static Logger logger = MiscUtils.getLogger();
    private static final Locale DEFAULT_LOCALE;
    public static String BASE_NAME;
    private static HashMap<String, TreeMap<String, String>> provinceCache;

    public LocaleUtils() {
    }

    public static Locale toLocale(String localeString) {
        return org.apache.commons.lang3.LocaleUtils.toLocale(localeString);
    }

    public static String getMessage(ServletRequest request, String key) {
        return getMessage(request.getLocale(), key);
    }

    public static String getMessage(String localeString, String key) {
        return getMessage(toLocale(localeString), key);
    }

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