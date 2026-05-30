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
package io.github.carlos_emr.carlos.web;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JavaScript i18n assets")
@Tag("unit")
class JavaScriptI18nAssetRegressionTest {
    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path WEBAPP_ROOT = resolveProjectPath(Path.of("src/main/webapp"));
    private static final Path RESOURCES_ROOT = resolveProjectPath(Path.of("src/main/resources"));
    private static final List<String> RESOURCE_BUNDLES = List.of(
            "oscarResources_en.properties",
            "oscarResources_fr.properties",
            "oscarResources_es.properties",
            "oscarResources_pl.properties",
            "oscarResources_pt_BR.properties");
    private static final List<String> JS_KEYS = List.of(
            "js.validation.userIdAlphanumeric",
            "js.validation.fieldRequired",
            "js.validation.valueTooLong",
            "js.validation.valueTooLongQuoted",
            "js.validation.valueTooShortQuoted",
            "js.checkDate.invalidDateEntered",
            "js.checkDate.invalidFormatNoPeriod",
            "js.checkDate.invalidFormat",
            "js.checkDate.enterDateYmd",
            "js.checkDate.invalidDate",
            "js.checkDate.enterDateDmy",
            "js.checkDate.birthMonthDigits",
            "js.checkDate.birthDateDigits",
            "js.checkDate.birthYearDigits",
            "js.checkDate.birthYearFull",
            "js.checkDate.yearMismatch",
            "js.checkDate.monthMismatch",
            "js.checkDate.dayMismatch",
            "js.checkDate.invalidTimeFormat",
            "js.checkDate.hourRange",
            "js.checkDate.minutesRange",
            "js.checkDate.monthRange",
            "js.checkDate.dayRange",
            "js.checkDate.monthNo31Days",
            "js.checkDate.februaryNoDays",
            "js.checkDate.birthDateFuture",
            "js.checkDate.birthDateTooOld",
            "js.global.confirmExit");

    @Test
    void shouldUseCarlosI18nWithFallbacks_forStandaloneDialogAssets() throws Exception {
        String checkDateJs = readWebAsset("js/checkDate.js");
        String validationJs = readWebAsset("js/validation.js");
        String globalJs = readWebAsset("js/global.js");

        assertThat(checkDateJs)
                .contains("window.carlosI18n")
                .contains("js.checkDate.invalidDateEntered")
                .contains("Date entered is not valid.")
                .doesNotContainPattern("alert\\(\\s*['\"]");
        assertThat(validationJs)
                .contains("window.carlosI18n")
                .contains("js.validation.userIdAlphanumeric")
                .contains("User ID should be alphanumeric")
                .doesNotContainPattern("alert\\(\\s*['\"]");
        assertThat(globalJs)
                .contains("window.carlosI18n")
                .contains("js.global.confirmExit")
                .contains("Are you sure you wish to exit without saving your changes?")
                .doesNotContainPattern("confirm\\(\\s*['\"]");
    }

    @Test
    void shouldDeclareJavaScriptI18nKeys_inEveryLocaleBundle() throws Exception {
        for (String bundle : RESOURCE_BUNDLES) {
            Properties properties = readProperties(bundle);

            for (String key : JS_KEYS) {
                assertThat(properties)
                        .as("%s contains %s", bundle, key)
                        .containsKey(key);
                assertThat(properties.getProperty(key))
                        .as("%s value for %s", bundle, key)
                        .isNotBlank();
                if (!"oscarResources_en.properties".equals(bundle)) {
                    assertThat(properties.getProperty(key))
                            .as("%s English fallback marker for %s", bundle, key)
                            .startsWith("[EN] ");
                }
            }
        }
    }

    private static String readWebAsset(String relativePath) throws Exception {
        return Files.readString(WEBAPP_ROOT.resolve(relativePath));
    }

    private static Properties readProperties(String fileName) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(RESOURCES_ROOT.resolve(fileName))) {
            properties.load(reader);
        }
        return properties;
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")));
    }
}
