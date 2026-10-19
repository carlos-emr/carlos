/**
 * Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundled Rich Text Letter starter templates.
 *
 * <p>These files are seeded into the eForm images directory by {@link EFormAssetDeployer} and are
 * what the editor's template dropdown offers. Two properties matter and neither is visible from
 * reading the template alone, which is why they are pinned here.</p>
 *
 * @since 2026-09-20
 */
@DisplayName("Rich Text Letter template regressions")
@Tag("unit")
@Tag("eform")
class RichTextLetterTemplateRegressionTest {

    private static final Path TEMPLATE_DIR =
            Path.of("src", "main", "webapp", "WEB-INF", "eform-assets");
    private static final Pattern PLACEHOLDER = Pattern.compile("##([^#]+)##");
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    /**
     * Placeholders that always resolve to a non-empty value for a real patient.
     *
     * <p>{@code populateTemplate()} in {@code editControl2.js} falls back to a browser
     * {@code prompt()} for any placeholder whose cached value is empty. A placeholder that is
     * merely <em>often</em> populated therefore interrupts the clinician with a dialog on every new
     * letter. {@code _ReferringBlock} is the one that bit: it is built from the patient's referral
     * fields and is empty for any patient with no referring doctor on file — which is most of them.
     * It reached a shipped template and prompted on a freshly seeded install; the sidebar's
     * "Referring Block" button inserts it on demand instead.</p>
     *
     * <p>Adding a name here asserts "this is never empty for a real patient". Anything conditional
     * belongs on a button, not in a template.</p>
     */
    private static final Set<String> ALWAYS_POPULATED_PLACEHOLDERS = Set.of(
            "letterhead",            // clinic_name + contact block
            "today",                 // current date
            "label",                 // patient demographic label
            "first_last_name",       // patient name
            "ageGender",             // derived from age + sex
            "_ClosingSalutation");   // always builds at least "Yours Sincerely" + stamp

    static Stream<Path> bundledTemplates() throws IOException {
        try (Stream<Path> files = Files.list(TEMPLATE_DIR)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".rtl"))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    /** Template body with HTML comments removed: the editor only ever reads {@code body.innerHTML}. */
    private static String templateBody(Path template) throws IOException {
        String html = Files.readString(template, StandardCharsets.UTF_8);
        int bodyStart = html.indexOf("<body");
        int bodyEnd = html.lastIndexOf("</body>");
        String body = (bodyStart >= 0 && bodyEnd > bodyStart) ? html.substring(bodyStart, bodyEnd) : html;
        return HTML_COMMENT.matcher(body).replaceAll("");
    }

    @ParameterizedTest
    @MethodSource("bundledTemplates")
    @DisplayName("should only use placeholders that cannot leave the clinician facing a prompt")
    void shouldOnlyUseAlwaysPopulatedPlaceholders_inBundledTemplates(Path template) throws IOException {
        Matcher matcher = PLACEHOLDER.matcher(templateBody(template));
        List<String> conditional = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!ALWAYS_POPULATED_PLACEHOLDERS.contains(key)) {
                conditional.add(key);
            }
        }
        assertThat(conditional)
                .as("%s uses placeholders that can resolve empty; editControl2.js would prompt the "
                        + "clinician for them on every new letter", template.getFileName())
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("bundledTemplates")
    @DisplayName("should declare the print rules the rendered PDF reproduces")
    void shouldDeclarePrintRules_inBundledTemplates(Path template) throws IOException {
        String html = Files.readString(template, StandardCharsets.UTF_8);

        // EFormRenderPdfHtmlComposer re-declares exactly these on the render surface, because the
        // editor stores body.innerHTML alone and the template's <head> never reaches the PDF. If a
        // template's own rules drift from that, the on-screen Print and the generated PDF disagree.
        assertThat(html)
                .as("%s must carry the Rich Text Letter print block", template.getFileName())
                .contains("media=\"print\"")
                .contains("@page { margin: 2cm; }")
                .contains("* { color: #000000; }")
                .contains(".DoNotPrint { display: none; }");
    }

    @Test
    @DisplayName("should ship every bundled template through the asset deployer")
    void shouldShipEveryBundledTemplate_throughTheAssetDeployer() throws IOException {
        // A .rtl file that exists in the WAR but is not in the deployer's ASSETS list never reaches
        // the eForm images directory, and efmformrtl_templates lists that directory — so the
        // template simply would not exist as far as the dropdown is concerned.
        String deployer = Files.readString(
                Path.of("src", "main", "java", "io", "github", "carlos_emr", "carlos", "eform",
                        "EFormAssetDeployer.java"), StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (Path template : bundledTemplates().toList()) {
            String name = template.getFileName().toString();
            if (!deployer.contains("\"" + name + "\"")) {
                missing.add(name);
            }
        }
        assertThat(missing).as("templates present in the WAR but never deployed").isEmpty();
    }
}
