/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test ensuring every {@code <fmt:message key="..."/>} reference on the
 * Master Demographic Record (MDR) JSPs resolves in each of the shipped locale bundles.
 *
 * <p>Background: a French browser opening the MDR previously surfaced a "CARLOS Error 0"
 * because {@code global.gender.female} (and a handful of other keys) were missing from
 * {@code oscarResources_fr.properties}. PR #1099 added the missing {@code global.gender.*}
 * keys, and the salutation and section-heading keys referenced on the page. This test
 * guards against future regressions by cross-checking every key the MDR JSPs reference
 * against every locale bundle.</p>
 *
 * @since 2026-04-21
 */
@DisplayName("Master Demographic Record i18n key coverage")
@Tag("unit")
@Tag("i18n")
class MasterDemographicRecordKeysTest {

    private static final String[] LOCALES = {"en", "fr", "es", "pt_BR", "pl"};

    /**
     * JSPs making up the Master Demographic Record edit/view flow. These are the pages
     * a French (or any non-English) browser hits when navigating to the MDR.
     */
    private static final String[] MDR_JSPS = {
            "src/main/webapp/WEB-INF/jsp/demographic/demographiceditdemographic.jsp",
            "src/main/webapp/WEB-INF/jsp/demographic/edit.jsp",
            "src/main/webapp/WEB-INF/jsp/demographic/edit-view.jsp",
            "src/main/webapp/WEB-INF/jsp/demographic/edit-form-personal.jsp",
            "src/main/webapp/WEB-INF/jsp/demographic/edit-form-clinical.jsp"
    };

    private static final Pattern FMT_KEY = Pattern.compile("fmt:message\\s+key=\"([^\"${}]+)\"");

    /**
     * Keys that are not referenced via {@code <fmt:message>} in the JSP source, but
     * are still resolved against the same bundle at request time (for example, the
     * gender enum labels that {@link io.github.carlos_emr.carlos.demographic.pageUtil.DemographicEditHelper}
     * renders from Java). Missing entries for these keys were the original trigger
     * for the "CARLOS Error 0" reported on a French browser.
     */
    private static final String[] DYNAMIC_KEYS = {
            "global.gender.male",
            "global.gender.female",
            "global.gender.intersex",
            "global.gender.other",
            "global.gender.undisclosed"
    };

    @Test
    @DisplayName("should resolve every MDR JSP fmt:message key in every shipped locale bundle")
    void shouldResolveEveryMdrKey_inEveryLocale() throws IOException {
        Set<String> keys = collectMdrKeys();
        assertThat(keys).as("MDR JSPs should reference at least one i18n key").isNotEmpty();
        for (String dynamic : DYNAMIC_KEYS) {
            keys.add(dynamic);
        }

        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            List<String> missing = new ArrayList<>();
            for (String key : keys) {
                if (!bundle.containsKey(key)) {
                    missing.add(key);
                }
            }
            assertThat(missing)
                    .as("oscarResources_%s.properties is missing MDR i18n keys; "
                                    + "these render as ???key??? for users with a %s browser",
                            locale, locale)
                    .isEmpty();
        }
    }

    private Set<String> collectMdrKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (String jsp : MDR_JSPS) {
            Path path = Paths.get(jsp);
            assertThat(Files.exists(path))
                    .as("MDR JSP %s should exist; update MDR_JSPS if it was renamed", jsp)
                    .isTrue();
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Matcher m = FMT_KEY.matcher(content);
            while (m.find()) {
                keys.add(m.group(1));
            }
        }
        return keys;
    }

    private Properties loadBundle(String locale) throws IOException {
        String resource = "/oscarResources_" + locale + ".properties";
        try (InputStream is = MasterDemographicRecordKeysTest.class.getResourceAsStream(resource)) {
            assertThat(is)
                    .as("resource %s must exist on the classpath", resource)
                    .isNotNull();
            Properties p = new Properties();
            p.load(is);
            return p;
        }
    }
}
