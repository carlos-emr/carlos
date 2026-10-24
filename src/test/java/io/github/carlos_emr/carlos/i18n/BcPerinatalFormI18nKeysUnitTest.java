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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BC Birth Summary 2008, BC-NewBorn 2008 and BC-AR 2007 showed raw {@code ???key???} labels: the pages name keys that
 * no bundle defined (issue #4949). Every label key these pages render must be defined in every shipped locale.
 *
 * @since 2026-10-18
 */
@DisplayName("BC perinatal form label keys")
@Tag("unit")
@Tag("i18n")
class BcPerinatalFormI18nKeysUnitTest {

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
        try (InputStream in = BcPerinatalFormI18nKeysUnitTest.class.getResourceAsStream("/oscarResources_" + locale + ".properties")) {
            assertThat(in).as("oscarResources_%s.properties on the classpath", locale).isNotNull();
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"formbcbirthsummo2008.jsp", "formBCNewBorn2008pg1.jsp", "formbcar2007pg2.jsp", "formbcar2007pg3.jsp"})
    void shouldDefineEveryLabelKey_inEveryLocale(String page) throws IOException {
        Set<String> keys = renderedKeys(page);
        assertThat(keys).as("%s renders message keys", page).isNotEmpty();
        for (String locale : LOCALES) {
            Properties bundle = bundle(locale);
            List<String> missing = keys.stream().filter(key -> !bundle.containsKey(key)).toList();
            assertThat(missing).as("%s keys missing from oscarResources_%s.properties", page, locale).isEmpty();
        }
    }

    /** The English recovered from the pages before their keys were introduced, with its spelling corrected. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "form.bcbirthsummo.cervixDialated|Cervix Dilated",
            "form.bcbirthsummo.trailOfLabour|Trial of Labour",
            "form.bcbirthsummo.dateFormat|dd/mm/yyyy",
    })
    void shouldSpellTheEnglishLabel_correctly(String key, String english) throws IOException {
        assertThat(bundle("en").getProperty(key)).isEqualTo(english);
    }

    @Test
    void shouldUseTheTranslatedHint_forTheDoubleClickTooltip() throws IOException {
        assertThat(renderedKeys("formbcar2007pg3.jsp")).contains("form.bcar.doubleClickShadedFields").doesNotContain("form.bcar.doubleClickHint");
    }
}
