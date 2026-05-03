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
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for Rourke 2017/2020 form localization.
 *
 * @since 2026-05-03
 */
@DisplayName("Rourke form i18n key coverage")
@Tag("unit")
@Tag("i18n")
class RourkeI18nKeysTest {

    private static final String[] LOCALES = {"en", "fr", "es", "pt_BR", "pl"};

    private static final List<String> REQUIRED_KEYS = List.of(
            "form.rourke.pageI",
            "form.rourke.all",
            "encounter.formRourke1.btnAbout",
            "encounter.formRourke1.aboutRourkeTitle"
    );

    private static final List<Path> ROURKE_PAGE_JSPS = List.of(
            Path.of("src/main/webapp/WEB-INF/jsp/form/formrourke2017p1.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/form/formrourke2017p2.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/form/formrourke2017p3.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/form/formrourke2017p4.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/form/formRourke2020p1.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/form/formRourke2020p2.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/form/formRourke2020p3.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/form/formRourke2020p4.jsp")
    );

    @Test
    @DisplayName("should resolve Rourke tab and About keys in every shipped locale bundle")
    void shouldResolveRourkeKeys_inEveryLocale() throws IOException {
        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            List<String> missing = REQUIRED_KEYS.stream()
                    .filter(key -> !bundle.containsKey(key))
                    .toList();

            assertThat(missing)
                    .as("oscarResources_%s.properties should define the Rourke tab and About labels", locale)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("should use i18n keys for Rourke About buttons")
    void shouldUseI18nKeys_forRourkeAboutButtons() throws IOException {
        for (Path jsp : ROURKE_PAGE_JSPS) {
            String content = Files.readString(jsp, StandardCharsets.UTF_8);

            assertThat(content)
                    .as("%s should localize the About button label", jsp)
                    .contains("encounter.formRourke1.btnAbout")
                    .doesNotContain("value=\"About\"");
            assertThat(content)
                    .as("%s should localize the About popup title", jsp)
                    .contains("encounter.formRourke1.aboutRourkeTitle")
                    .doesNotContain("'About Rourke'");
        }
    }

    @Test
    @DisplayName("should not include the removed moment.js path from the Rourke measurement modal")
    void shouldNotReferenceMissingMomentLibrary_fromMeasurementModal() throws IOException {
        String content = Files.readString(
                Path.of("src/main/webapp/WEB-INF/jsp/form/demographicMeasurementModal.jsp"),
                StandardCharsets.UTF_8);

        assertThat(content).doesNotContain("/library/moment.js");
    }

    private Properties loadBundle(String locale) throws IOException {
        String resource = "/oscarResources_" + locale + ".properties";
        try (InputStream is = RourkeI18nKeysTest.class.getResourceAsStream(resource)) {
            assertThat(is)
                    .as("resource %s must exist on the classpath", resource)
                    .isNotNull();
            Properties properties = new Properties();
            properties.load(is);
            return properties;
        }
    }
}
