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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.Map.Entry;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

public final class LocaleUtils {
    private static Logger logger = MiscUtils.getLogger();
    private static final Locale DEFAULT_LOCALE;
    public static String BASE_NAME;
    private static HashMap<String, TreeMap<String, String>> provinceCache;

    /**
     * Bundle lookup that does not fall back to the JVM default locale, so "no bundle for this
     * locale" is a miss we can act on rather than a silent switch to whatever language the
     * server happens to be running in.
     */
    private static final ResourceBundle.Control NO_FALLBACK_CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    public LocaleUtils() {
    }

    public static Locale toLocale(String localeString) {
        return org.apache.commons.lang3.LocaleUtils.toLocale(localeString);
    }

    public static String getMessage(ServletRequest request, String key) {
        return getMessage(resolveBundleLocale(request), key);
    }

    /**
     * Resolves the locale a page should be rendered in from the browser's {@code Accept-Language}
     * preferences: the first preference that has a bundle wins, otherwise English.
     * The chart fragments also pass this locale to JSTL before loading their message bundle,
     * so both rendering paths share the same fallback policy.
     *
     * <p>Java code that renders user-facing text must use this rather than the JVM default locale or
     * {@code LocaleContextHolder}. CARLOS has no Spring {@code LocaleResolver} in the Struts/JSP
     * request path, so those both report the <em>server's</em> locale — a French clinician on an
     * English-defaulted server would get the JSP text translated and the Java-built text not, in
     * the same header.</p>
     *
     * <p>Plain {@code ResourceBundle.getBundle(name, locale)} is not enough on its own either: for
     * an unsupported first preference (say {@code de-DE,fr}) it falls back to the JVM default
     * locale instead of trying the next preference, which is the same server-locale leak one step
     * removed.</p>
     *
     * @param request current request; {@code null} is tolerated and yields the English fallback
     * @return the locale to load {@link #BASE_NAME} with; never {@code null}
     */
    // FindSecBugs SERVLET_HEADER: Accept-Language is client-controlled by design; it only selects
    // among the message bundles this WAR ships (anything else falls back to English) and is
    // logged, sanitized, at DEBUG. It never reaches an authorization decision or raw output.
    @SuppressFBWarnings(value = "SERVLET_HEADER", justification = "Accept-Language is used for message-bundle negotiation among shipped bundles and sanitized DEBUG diagnostics only; not an authorization decision or raw output")
    public static Locale resolveBundleLocale(ServletRequest request) {
        String acceptLanguage = null;
        if (request instanceof HttpServletRequest httpRequest) {
            acceptLanguage = httpRequest.getHeader("Accept-Language");
            // Without a preference, getLocales() supplies the container's default locale.
            // Treat that as an absent browser preference, not a supported language choice.
            if (acceptLanguage == null || acceptLanguage.isBlank()) {
                return logResolved(DEFAULT_LOCALE, acceptLanguage, request);
            }
        }
        Enumeration<Locale> preferred = request == null ? null : request.getLocales();
        while (preferred != null && preferred.hasMoreElements()) {
            Locale candidate = preferred.nextElement();
            try {
                ResourceBundle.getBundle(BASE_NAME, candidate, NO_FALLBACK_CONTROL);
                return logResolved(candidate, acceptLanguage, request);
            } catch (MissingResourceException _) {
                // No bundle for this preference; try the browser's next choice.
            }
        }
        return logResolved(DEFAULT_LOCALE, acceptLanguage, request);
    }

    /**
     * Records, at DEBUG only, which locale a request negotiated and from what. A field report
     * of a page rendered in the wrong language cannot be diagnosed from the page alone: the
     * markup carries the negotiated language (the chart's {@code lang} attributes), and this
     * line pairs it with the raw preference the server saw for that request. {@code Accept-Language}
     * and the request path carry no patient data, but both are client-controlled, so the path
     * goes through {@link LogSafe#sanitizeUri(String)} (it strips a URL-rewritten
     * {@code ;jsessionid} bearer token) and the header through {@link LogSafe#sanitize(String)}
     * (log-injection escaping). No-op unless DEBUG is enabled for this logger.
     */
    private static Locale logResolved(Locale resolved, String acceptLanguage, ServletRequest request) {
        if (logger.isDebugEnabled()) {
            String path = request instanceof HttpServletRequest httpRequest
                    ? LogSafe.sanitizeUri(httpRequest.getRequestURI()) : null;
            logger.debug("Negotiated bundle locale {} for {} from Accept-Language [{}] (bundle base {})",
                    resolved, path, LogSafe.sanitize(acceptLanguage), BASE_NAME);
        }
        return resolved;
    }

    public static String getMessage(String localeString, String key) {
        return getMessage(toLocale(localeString), key);
    }

    public static String getMessage(Locale locale, String key) {
        try {
            return ResourceBundle.getBundle(BASE_NAME, locale).getString(key);
        } catch (MissingResourceException _) {
            String message = "Resource not found. BASE_NAME=" + BASE_NAME + ", Locale=" + locale + ", key=" + key;
            logger.error(message);

            try {
                return ResourceBundle.getBundle(BASE_NAME, DEFAULT_LOCALE).getString(key);
            } catch (MissingResourceException _) {
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
