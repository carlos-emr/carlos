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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards pages where an automated rewrite (#1787) cut a word in two: it padded the first part with
 * spaces and moved the rest to the start of the next line. A cut message key renders "???key???",
 * a cut {@code value} attribute leaves a button unlabelled, a cut {@code <tr>} becomes stray text or an
 * empty unknown tag, and a cut in a {@code javascript:} link or an {@code on*} handler makes it fail
 * when clicked.
 */
@DisplayName("JSP line-break regressions")
@Tag("unit")
class JspLineBreakRegressionTest {

    private static final Path WEBAPP = Path.of("src/main/webapp");
    private static final String JSP_DIR = "src/main/webapp/WEB-INF/jsp/";
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String[] LOCALES = {"en", "es", "fr", "pl", "pt_BR"};
    /** The cut: text, then a run of padding spaces at the end of the line, then the rest at column 0. */
    private static final Pattern CUT = Pattern.compile("\\S {8,}\\r?\\n(?=\\S)");
    private static final Pattern MESSAGE_KEY = Pattern.compile("<fmt:message key=[\"']([^\"'$<]+)[\"']");
    /**
     * JSP comments, scriptlets, EL expressions, then custom tags, masked in that order so a
     * {@code >} or {@code "} inside one cannot end the attribute it sits in.
     */
    private static final Pattern[] JSP_CONSTRUCTS = {
            Pattern.compile("<%--[\\s\\S]*?--%>"),
            Pattern.compile("<%[\\s\\S]*?%>"),
            Pattern.compile("\\$\\{[^}]*}"),
            Pattern.compile("</?[A-Za-z][\\w-]*:[\\w-]+[^>]*>")};
    /** A {@code javascript:} link or an {@code on*} handler, quoted with double quotes. */
    private static final Pattern SCRIPT_ATTRIBUTE =
            Pattern.compile("(?i)\\s(?:href\\s*=\\s*\"\\s*javascript:|on[a-z]+\\s*=\\s*\")([^\"]*)\"");
    /**
     * A word cut in two inside script: padding after a word, then the rest of it at column 0. Browsers
     * drop a bare line break from a {@code javascript:} link but keep the padding, so the padding is
     * what breaks the link; in an {@code on*} handler either half alone is already wrong.
     */
    private static final Pattern SCRIPT_WORD_CUT = Pattern.compile("\\w[ \\t]+\\r?\\n\\w");
    /**
     * Cuts another open PR repairs, as page to the text just before the cut. #4107 rejoins the
     * eChart button's onClick on the BC lab display; remove this entry once it is merged.
     */
    private static final Map<String, String> REPAIRED_ELSEWHERE =
            Map.of("WEB-INF/jsp/lab/CA/BC/labDisplay.jsp", "/oscarMDS/Se");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "lab/CA/ON/CMLDisplay.jsp",
            "oscarMDS/Index.jsp",
            "oscarMDS/OpenEChart.jsp",
            "tickler/ticklerDemoMain.jsp",
            "billing/CA/BC/genTAS00.jsp",
            "billing/CA/BC/genTAS01.jsp"})
    @DisplayName("should keep each line whole on the repaired pages")
    void shouldKeepLinesWhole_onRepairedPages(String page) throws IOException {
        String jsp = Files.readString(Path.of(JSP_DIR + page), StandardCharsets.UTF_8);

        List<Integer> cutLines = new ArrayList<>();
        Matcher cut = CUT.matcher(jsp);
        while (cut.find()) {
            cutLines.add(lineOf(jsp, cut.start()));
        }
        assertThat(cutLines).as("lines of %s that end mid-word and continue at column 0", page).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "lab/CA/BC/labDisplay.jsp",
            "lab/CA/ON/CMLDisplay.jsp",
            "oscarMDS/Index.jsp",
            "tickler/ticklerDemoMain.jsp"})
    @DisplayName("should find every label key of the repaired pages in every bundle")
    void shouldResolveLabelKeys_inEveryBundle(String page) throws IOException {
        String jsp = Files.readString(Path.of(JSP_DIR + page), StandardCharsets.UTF_8);
        List<String> keys = MESSAGE_KEY.matcher(jsp).results().map(match -> match.group(1)).distinct().toList();
        assertThat(keys).as("message keys on %s", page).isNotEmpty();

        for (String locale : LOCALES) {
            Properties bundle = new Properties();
            try (Reader reader = Files.newBufferedReader(RESOURCES.resolve("oscarResources_" + locale + ".properties"),
                    StandardCharsets.UTF_8)) {
                bundle.load(reader);
            }
            assertThat(keys)
                    .as("keys on %s with no %s text", page, locale)
                    .allSatisfy(key -> assertThat(bundle.getProperty(key)).as(key).isNotBlank());
        }
    }

    @Test
    @DisplayName("should keep every word whole in javascript: links and on* handlers")
    void shouldKeepWordsWhole_inScriptAttributes() throws IOException {
        List<String> cuts = new ArrayList<>();
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            for (Path file : files.filter(JspLineBreakRegressionTest::isJspSource).sorted().toList()) {
                String page = WEBAPP.relativize(file).toString().replace('\\', '/');
                String masked = maskJsp(Files.readString(file, StandardCharsets.UTF_8));
                Matcher attribute = SCRIPT_ATTRIBUTE.matcher(masked);
                while (attribute.find()) {
                    Matcher cut = SCRIPT_WORD_CUT.matcher(attribute.group(1));
                    while (cut.find()) {
                        String before = attribute.group(1).substring(0, cut.start() + 1);
                        String repairedElsewhere = REPAIRED_ELSEWHERE.get(page);
                        if (repairedElsewhere == null || !before.endsWith(repairedElsewhere)) {
                            cuts.add(page + ":" + lineOf(masked, attribute.start(1) + cut.start()));
                        }
                    }
                }
            }
        }
        assertThat(cuts).as("script attributes with a word cut across a line break").isEmpty();
    }

    private static boolean isJspSource(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file) && (name.endsWith(".jsp") || name.endsWith(".jspf") || name.endsWith(".tag"));
    }

    /** Replaces every non-blank character of a JSP construct with {@code #}, keeping offsets and line breaks. */
    private static String maskJsp(String jsp) {
        String masked = jsp;
        for (Pattern construct : JSP_CONSTRUCTS) {
            Matcher match = construct.matcher(masked);
            StringBuilder out = new StringBuilder(masked.length());
            while (match.find()) {
                match.appendReplacement(out, Matcher.quoteReplacement(match.group().replaceAll("\\S", "#")));
            }
            match.appendTail(out);
            masked = out.toString();
        }
        return masked;
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
