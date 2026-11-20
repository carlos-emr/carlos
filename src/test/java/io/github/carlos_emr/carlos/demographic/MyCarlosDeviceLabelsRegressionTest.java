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
package io.github.carlos_emr.carlos.demographic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The myCarlos devices panel on the Manage portal page takes its words from a hidden label list in
 * portalManage.jsp, filled from the {@code mycarlos.approval.*} bundle keys. A key the script asks for
 * that is not in the list shows the "changed" error text instead, so the script, the list and the five
 * bundles must agree (#6124 added {@code status.earlierExpired}).
 *
 * @since 2026-11-10
 */
@Tag("unit")
@Tag("fast")
@DisplayName("myCarlos devices panel labels")
class MyCarlosDeviceLabelsRegressionTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/demographic/portalManage.jsp");
    private static final Path SCRIPT = Path.of("src/main/webapp/share/javascript/demographic/mycarlos-devices.js");
    private static final List<String> LOCALES = List.of("en", "fr", "es", "pl", "pt_BR");
    private static final Pattern LABEL_LIST = Pattern.compile(
            "<c:forTokens var=\"label\" delims=\",\" items=\"([^\"]+)\">\\s*<li data-key=\"\\$\\{carlos:forHtmlAttribute\\(label\\)}\">"
                    + "<fmt:message key=\"mycarlos\\.approval\\.\\$\\{label}\"/></li>");
    private static final Pattern DOTTED_KEY = Pattern.compile("'([a-z]+(?:\\.[a-zA-Z]+)+)'");
    private static final Pattern TEXT_CALL = Pattern.compile("\\btext\\('([a-zA-Z.]+)'\\)");

    private static Set<String> listedLabels() throws IOException {
        Matcher list = LABEL_LIST.matcher(Files.readString(JSP, StandardCharsets.UTF_8));
        assertThat(list.find()).as("label list in portalManage.jsp").isTrue();
        return new TreeSet<>(Arrays.asList(list.group(1).split(",")));
    }

    private static Set<String> scriptLabels() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        Set<String> keys = new TreeSet<>();
        for (Pattern pattern : List.of(DOTTED_KEY, TEXT_CALL)) {
            Matcher match = pattern.matcher(script);
            while (match.find()) keys.add(match.group(1));
        }
        return keys;
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }

    @Test
    @DisplayName("should list every label the script uses, the earlier-expired notice included")
    void shouldListEveryScriptLabel_inTheJsp() throws IOException {
        Set<String> listed = listedLabels();
        assertThat(scriptLabels()).contains("none", "status.expired", "status.earlierExpired", "status.unavailable");
        assertThat(listed).containsAll(scriptLabels());
    }

    @Test
    @DisplayName("should give every listed label a text in each of the five bundles")
    void shouldTranslateEveryListedLabel_inAllFiveBundles() throws IOException {
        Set<String> listed = listedLabels();
        for (String locale : LOCALES) {
            Properties texts = bundle(locale);
            for (String label : listed) {
                assertThat(texts.getProperty("mycarlos.approval." + label))
                        .as("%s: mycarlos.approval.%s", locale, label).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("should word the earlier-expired notice about the past, apart from a row's own expired label")
    void shouldWordEarlierExpiredNotice_aboutThePast() throws IOException {
        Properties english = bundle("en");
        assertThat(english.getProperty("mycarlos.approval.status.earlierExpired"))
                .isEqualTo("An earlier approval request expired. Ask the patient to request approval again from myCarlos.");
        for (String locale : LOCALES) {
            Properties texts = bundle(locale);
            assertThat(texts.getProperty("mycarlos.approval.status.earlierExpired"))
                    .as(locale).isNotEqualTo(texts.getProperty("mycarlos.approval.status.expired")).contains("myCarlos");
        }
    }
}
