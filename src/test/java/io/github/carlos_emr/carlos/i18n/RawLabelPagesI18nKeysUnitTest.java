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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The provider edit page, the annual female V2, HOME FAST and FDI function forms and the Rourke 2006 pages showed raw
 * {@code ???key???} labels: they named keys that no bundle defined (the raw-label finding behind #4949; the Rourke
 * 2006 page 1 title is part of #4593). Every label key these pages render must be defined in every shipped locale.
 *
 * @since 2026-10-18
 */
@DisplayName("Raw-label page keys")
@Tag("unit")
@Tag("i18n")
class RawLabelPagesI18nKeysUnitTest {

    private static final String[] LOCALES = {"en", "fr", "es", "pt_BR", "pl"};

    /** A literal message key, with either quote style. */
    private static final Pattern MESSAGE_KEY = Pattern.compile("<fmt:message\\s+key=(['\"])([^'\"$<%]+)\\1");

    /** Scriptlets, expressions and comments never render a tag, so keys inside them are not shown. */
    private static final Pattern NOT_RENDERED = Pattern.compile("<%--.*?--%>|<%.*?%>|<!--.*?-->", Pattern.DOTALL);

    private static Set<String> renderedKeys(String page) throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/" + page), StandardCharsets.UTF_8);
        Matcher matcher = MESSAGE_KEY.matcher(NOT_RENDERED.matcher(jsp).replaceAll(""));
        Set<String> keys = new TreeSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(2));
        }
        return keys;
    }

    private static Properties bundle(String locale) throws IOException {
        try (InputStream in = RawLabelPagesI18nKeysUnitTest.class.getResourceAsStream("/oscarResources_" + locale + ".properties")) {
            assertThat(in).as("oscarResources_%s.properties on the classpath", locale).isNotNull();
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin/providerupdateprovider.jsp", "form/formannualfemaleV2.jsp", "form/formhomefalls.jsp",
            "form/formlatelifeFDIfunction.jsp", "form/formrourke2006p1.jsp", "form/formrourke2006p2.jsp", "form/formrourke2006p3.jsp",
            "form/formrourke2006p4.jsp"})
    void shouldDefineEveryLabelKey_inEveryLocale(String page) throws IOException {
        Set<String> keys = renderedKeys(page);
        assertThat(keys).as("%s renders message keys", page).isNotEmpty();
        for (String locale : LOCALES) {
            Properties bundle = bundle(locale);
            List<String> missing = keys.stream().filter(key -> !bundle.containsKey(key)).toList();
            assertThat(missing).as("%s keys missing from oscarResources_%s.properties", page, locale).isEmpty();
        }
    }

    /** The three labels that reuse an existing, translated key with the same English instead of a new one. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "form/formannualfemaleV2.jsp|encounter.formFemaleAnnual.formNo|encounter.formFemaleAnnual.btnNo",
            "form/formhomefalls.jsp|global.na|form.homeFalls.na",
            "form/formlatelifeFDIfunction.jsp|form.lateLifeFDI.disability.cannotDo|form.lateLifeFDI.function.cannotDo",
    })
    void shouldUseTheTranslatedKey_insteadOfTheMissingOne(String page, String used, String missing) throws IOException {
        assertThat(renderedKeys(page)).contains(used).doesNotContain(missing);
    }
}
