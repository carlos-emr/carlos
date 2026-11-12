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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BC-AR 2020 pages 1, 2, 4 and 5 showed raw {@code ???key???} labels: the i18n sweep named keys that no bundle
 * defined (issue #4949). Every label key these pages render must be defined in every shipped locale, and page 1's
 * delivery lists must not mix English options with translated ones.
 *
 * @since 2026-10-18
 */
@DisplayName("BC-AR 2020 label keys")
@Tag("unit")
@Tag("i18n")
class BcAr2020FormI18nKeysUnitTest {

    private static final String[] LOCALES = {"en", "fr", "es", "pt_BR", "pl"};

    /** A literal message key, with either quote style. */
    private static final Pattern MESSAGE_KEY = Pattern.compile("<fmt:message\\s+key=(['\"])([^'\"$<%]+)\\1");

    /** Scriptlets, expressions and comments never render a tag, so keys inside them are not shown. */
    private static final Pattern NOT_RENDERED = Pattern.compile("<%--.*?--%>|<%.*?%>|<!--.*?-->", Pattern.DOTALL);

    private static Set<String> renderedKeys(String page) throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/form/" + page), StandardCharsets.UTF_8);
        Matcher matcher = MESSAGE_KEY.matcher(NOT_RENDERED.matcher(jsp).replaceAll(""));
        Set<String> keys = new TreeSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(2));
        }
        return keys;
    }

    private static Properties bundle(String locale) throws IOException {
        try (InputStream in = BcAr2020FormI18nKeysUnitTest.class.getResourceAsStream("/oscarResources_" + locale + ".properties")) {
            assertThat(in).as("oscarResources_%s.properties on the classpath", locale).isNotNull();
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"formBCAR2020pg1.jsp", "formBCAR2020pg2.jsp", "formBCAR2020pg4.jsp", "formBCAR2020pg5.jsp"})
    void shouldDefineEveryLabelKey_inEveryLocale(String page) throws IOException {
        Set<String> keys = renderedKeys(page);
        assertThat(keys).as("%s renders message keys", page).isNotEmpty();
        for (String locale : LOCALES) {
            Properties bundle = bundle(locale);
            List<String> missing = keys.stream().filter(key -> !bundle.containsKey(key)).toList();
            assertThat(missing).as("%s keys missing from oscarResources_%s.properties", page, locale).isEmpty();
        }
    }

    /** The blood group and Rh lists' "Unknown" option uses page 1's translated key instead of the missing global one. */
    @Test
    void shouldUseTheTranslatedUnknownOption_onPage2() throws IOException {
        assertThat(renderedKeys("formBCAR2020pg2.jsp")).contains("form.formBCAR2020pg1.option.unknown").doesNotContain("global.unknown");
    }

    /**
     * The obstetric-history delivery lists show every option through a key, so a translated list has no English
     * "Vacuum" or "Forceps" beside its translated "Vacuum and Forceps". The stored values stay English.
     */
    @Test
    void shouldTranslateEveryDeliveryOption_inTheObstetricHistoryLists() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg1.jsp"), StandardCharsets.UTF_8);
        assertThat(jsp).doesNotContain("<option value=\"Vacuum\">Vacuum</option>", "<option value=\"Forceps\">Forceps</option>");
        assertThat(jsp.split("<option value=\"Vacuum\"><fmt:message key=\"form.formBCAR2020pg1.option.vacuum\"/></option>", -1)).hasSize(5);
        assertThat(jsp.split("<option value=\"Forceps\"><fmt:message key=\"form.formBCAR2020pg1.option.forceps\"/></option>", -1)).hasSize(5);
    }

    /** Page 4's "Postpartum care" says what page 5's existing, translated label says, in every locale. */
    @Test
    void shouldLabelPostpartumCareAlike_onPages4And5() throws IOException {
        for (String locale : LOCALES) {
            Properties bundle = bundle(locale);
            assertThat(bundle.getProperty("form.formBCAR2020pg4.label.postpartumCare")).as(locale)
                    .isEqualTo(bundle.getProperty("form.formBCAR2020pg5.label.postpartumCare"));
        }
    }
}
