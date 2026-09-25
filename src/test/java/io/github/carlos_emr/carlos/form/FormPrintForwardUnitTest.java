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
package io.github.carlos_emr.carlos.form;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chain a form's Print Pdf button travels, pinned at the three places it broke (issue #3735).
 *
 * <p>A form JSP puts a token in {@code document.forms[0].submit.value} and posts to
 * {@code /form/formname}. {@link Frm2Action} asks {@link FrmRecordHelp#findActionValue(String)} what
 * that token means, and returns the answer as a <strong>Struts result name</strong>. The result then
 * forwards to the endpoint that writes the PDF. Three things have to line up, and none of them is
 * checked by the compiler:
 *
 * <ol>
 *   <li>every token a JSP actually emits has to be one {@code findActionValue} recognises — seven
 *       JSPs send {@code printall} where the rest send {@code printAll}, and the exact-case lookup
 *       answered {@code failure};</li>
 *   <li>what the action returns has to be a result name, not a Struts 1 style path with a query
 *       string appended;</li>
 *   <li>the endpoint the result forwards to has to be reachable — the PDF servlets are declared in
 *       web.xml, and the Struts filters run on FORWARD, so a servlet path that is not in
 *       {@code struts.action.excludePattern} is resolved as an action and 404s.</li>
 * </ol>
 *
 * <p>Each of the three produced a different failure for the clinician (500, the error page, 404),
 * which is why the issue reads as several bugs at once.
 *
 * @since 2026-09-25
 */
@DisplayName("Form Print Pdf forward chain")
@Tag("unit")
@Tag("form")
class FormPrintForwardUnitTest {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path FORM_JSP_DIR = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "form");
    private static final Path STRUTS_FORM_XML = Path.of("src", "main", "webapp", "WEB-INF", "classes", "struts-form.xml");
    private static final Path STRUTS_XML = Path.of("src", "main", "webapp", "WEB-INF", "classes", "struts.xml");
    private static final Path FRM2ACTION_JAVA = Path.of("src", "main", "java", "io", "github",
            "carlos_emr", "carlos", "form", "Frm2Action.java");

    /** {@code document.forms[0].submit.value = "printall";} and friends. */
    private static final Pattern SUBMIT_VALUE =
            Pattern.compile("submit\\.value\\s*=\\s*\"([A-Za-z]+)\"");
    /** {@code <result name="printAll">} in the form/formname action. */
    private static final Pattern RESULT_NAME = Pattern.compile("<result\\s+name=\"([^\"]+)\"");

    /**
     * Tokens the JSPs emit that this action deliberately handles before the forward, so they never
     * reach findActionValue: the AJAX quick-save and the letter save both write their own response
     * and return null.
     */
    private static final Set<String> HANDLED_BEFORE_FORWARD = Set.of("autosaveAjax", "saveFormLetter");

    @Test
    @DisplayName("should canonicalise a known action token regardless of case")
    void shouldCanonicaliseActionToken_forAnyCase() {
        FrmRecordHelp help = new FrmRecordHelp();

        assertThat(help.findActionValue("printall"))
                .as("the lab requisition and mental health JSPs have always sent this spelling")
                .isEqualTo("printAll");
        assertThat(help.findActionValue("PRINTALL")).isEqualTo("printAll");
        assertThat(help.findActionValue("printAll"))
                .as("the canonical spelling still answers itself")
                .isEqualTo("printAll");
        assertThat(help.findActionValue("save")).isEqualTo("save");
    }

    @Test
    @DisplayName("should refuse an unknown or absent action token")
    void shouldRefuseActionToken_whenUnknown() {
        FrmRecordHelp help = new FrmRecordHelp();

        assertThat(help.findActionValue("printEverything")).isEqualTo("failure");
        assertThat(help.findActionValue("")).isEqualTo("failure");
        assertThat(help.findActionValue(null)).isEqualTo("failure");
    }

    @Test
    @DisplayName("should resolve every submit token the form JSPs emit to a declared result")
    void shouldResolveSubmitToken_forEveryFormJsp() throws IOException {
        FrmRecordHelp help = new FrmRecordHelp();
        Set<String> declaredResults = declaredResultNames();
        assertThat(declaredResults)
                .as("the form/formname action declares results at all (guards against a vacuous pass)")
                .contains("printAll", "save", "exit");

        Set<String> unresolved = new TreeSet<>();
        for (String token : submitTokensEmittedByFormJsps()) {
            if (HANDLED_BEFORE_FORWARD.contains(token)) {
                continue;
            }
            String resolved = help.findActionValue(token);
            if ("failure".equals(resolved) || !declaredResults.contains(resolved)) {
                unresolved.add(token + " -> " + resolved);
            }
        }

        assertThat(unresolved)
                .as("a submit token no result is declared for reaches the clinician as the error "
                        + "page instead of the form's PDF or next screen (issue #3735)")
                .isEmpty();
    }

    @Test
    @DisplayName("should leave the PDF servlet paths out of Struts action routing")
    void shouldExcludePdfServletPaths_fromStrutsRouting() throws IOException {
        String strutsXml = read(STRUTS_XML);
        int start = strutsXml.indexOf("struts.action.excludePattern");
        assertThat(start).as("struts.xml declares an excludePattern at all").isGreaterThan(-1);
        String excludeConstant = strutsXml.substring(start, strutsXml.indexOf("/>", start));

        // The Struts filters are mapped for FORWARD as well as REQUEST (web.xml), so a result that
        // forwards to a servlet path re-enters Struts. With struts.action.extension="" every path
        // looks like an action, so an un-excluded servlet path answers 404 rather than reaching the
        // servlet declared for it in web.xml.
        // SPLIT, NOT contains(). The pattern is one alternation, and "eform/createpdf" contains
        // "form/createpdf" as a substring -- a contains() assertion passes with the form endpoint
        // deleted, which is the very mutation this test exists to catch.
        Set<String> alternatives = Stream.of(excludeConstant.split("\\|"))
                .map(alternative -> alternative.replaceAll("[()^$]", ""))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertThat(alternatives)
                .as("form/createpdf is the endpoint every non-Jasper form print forwards to")
                .contains("form/createpdf");
        assertThat(alternatives)
                .as("eform/createpdf is its eForm counterpart, reached the same way")
                .contains("eform/createpdf");
    }

    @Test
    @DisplayName("should return the action name rather than a Struts 1 forward path")
    void shouldReturnActionName_forEveryForwardButSave() throws IOException {
        String source = read(FRM2ACTION_JAVA);
        int blockStart = source.indexOf("if (newID > -1) {");
        assertThat(blockStart).as("Frm2Action still has the forward block this pins").isGreaterThan(-1);
        String block = source.substring(blockStart, source.indexOf("} catch (Exception ex)", blockStart));

        assertThat(block)
                .as("the result the action returns is the action name; a Struts 2 result is a name, "
                        + "not a path with a query string (issue #3735)")
                .contains("actionForward = strAction;");
        assertThat(block)
                .as("createActionURL()'s output must not become the result name again")
                .doesNotContain("actionForward = rec.createActionURL(");
        assertThat(block)
                .as("the save branch still redirects through the URL createActionURL builds")
                .contains("actionUrl.startsWith(SAVE_ACTION_PREFIX)");
    }

    /** Every distinct token any form JSP writes into the submit field. */
    private static Set<String> submitTokensEmittedByFormJsps() throws IOException {
        Set<String> tokens = new LinkedHashSet<>();
        try (Stream<Path> jsps = Files.list(resolveProjectPath(FORM_JSP_DIR))) {
            List<Path> files = jsps.filter(path -> path.toString().endsWith(".jsp")).toList();
            assertThat(files)
                    .as("the form JSP directory holds JSPs at all (guards against a vacuous pass)")
                    .isNotEmpty();
            for (Path jsp : files) {
                for (String line : Files.readAllLines(jsp, StandardCharsets.UTF_8)) {
                    // Several JSPs keep a commented-out print token from the Struts 1 days; it is
                    // not emitted, so matching it would fail this test on dead text.
                    if (line.stripLeading().startsWith("//")) {
                        continue;
                    }
                    Matcher matcher = SUBMIT_VALUE.matcher(line);
                    while (matcher.find()) {
                        tokens.add(matcher.group(1));
                    }
                }
            }
        }
        assertThat(tokens)
                .as("the form JSPs emit submit tokens at all (guards against a vacuous pass)")
                .contains("save", "printAll");
        return tokens;
    }

    /** The result names declared on the form/formname action. */
    private static Set<String> declaredResultNames() throws IOException {
        String xml = read(STRUTS_FORM_XML);
        int actionStart = xml.indexOf("<action name=\"form/formname\"");
        assertThat(actionStart).as("struts-form.xml declares the form/formname action").isGreaterThan(-1);
        int actionEnd = xml.indexOf("</action>", actionStart);
        Matcher matcher = RESULT_NAME.matcher(xml.substring(actionStart, actionEnd));
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String read(Path relative) throws IOException {
        Path absolute = resolveProjectPath(relative);
        assertThat(absolute)
                .as("config file not found — run tests from the project root or set -Dbasedir=<project-root>")
                .exists();
        return Files.readString(absolute, StandardCharsets.UTF_8);
    }

    private static Path resolveProjectPath(Path relativePath) {
        return Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .resolve(relativePath)
                .normalize();
    }
}
