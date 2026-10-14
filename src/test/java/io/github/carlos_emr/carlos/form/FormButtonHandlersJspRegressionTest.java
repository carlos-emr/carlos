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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Button handlers of the consultant letter and counseling forms.
 *
 * <p>The #1806 merge repeated lines in these handlers. In {@code formConsultant.jsp},
 * {@code onPrint()} also declared {@code ret} with both {@code let} and {@code const} (#1805),
 * and #1806 repeated {@code const ret = confirm(...)}. A repeated declaration is a syntax error
 * for the whole {@code <script>} block, so none of its functions existed. The Save, Save and Exit,
 * Exit and Print buttons are submit buttons whose {@code onclick} handler then failed, so each
 * one submitted the form with its hidden default {@code submit=exit}: the form was saved and
 * closed without the confirmation, even from Exit. In {@code formCounseling.jsp} the repeated
 * line used {@code var}, which parses, so Save and Save and Exit asked the same question twice
 * and only the second answer counted.</p>
 *
 * <p>Once the handlers ran again, Print on the consultant letter still saved (#4370): it was a
 * submit button, and answering OK to "Do you wish to make changes?" returned {@code true} after
 * setting {@code submit=save}, so going back to edit the letter also saved it. Print and Exit are
 * now plain buttons, and only Save and Save and Exit post the form. The node scenarios drive the
 * handlers against a stand-in for the page; {@code scripts/form-consultant-print-playwright-checks.js}
 * covers the same behaviour in a browser against a running server.</p>
 *
 * @since 2026-10-06
 */
@DisplayName("Form button handlers (consultant letter, counseling)")
@Tag("unit")
@Tag("form")
class FormButtonHandlersJspRegressionTest {

    private static final Path FORM_JSP_DIR = resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/form"));

    /** Stands in for anything the server prints; valid inside a string and as an expression. */
    private static final String PLACEHOLDER = "x";

    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern JSP_EXPRESSION = Pattern.compile("<%=.*?%>", Pattern.DOTALL);
    private static final Pattern JSP_SCRIPTLET = Pattern.compile("<%.*?%>", Pattern.DOTALL);
    private static final Pattern EL_EXPRESSION = Pattern.compile("(?<!\\\\)\\$\\{[^}]*}");
    // A message tag becomes its key, so two different prompts stay distinguishable. The tag and
    // its key attribute are matched separately so neither pattern backtracks super-linearly.
    private static final Pattern MESSAGE_TAG = Pattern.compile("<fmt:message\\b[^<>]*/>");
    private static final Pattern MESSAGE_KEY = Pattern.compile("\\bkey\\s*=\\s*['\"]([\\w.-]+)['\"]");
    private static final Pattern PREFIXED_SELF_CLOSING_TAG = Pattern.compile("<[A-Za-z][\\w-]*:[\\w-]+[^<>]*/>");
    // Group 1: the <script> tag's attributes. Group 2: its inline source.
    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "<script\\b([^>]*)>(.*?)</script\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTERNAL_SCRIPT = Pattern.compile("\\bsrc\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUNCTION_START = Pattern.compile("\\bfunction\\s+(\\w+)\\s*\\(");
    private static final Pattern LEXICAL_DECLARATION = Pattern.compile("\\b(?:let|const)\\s+(\\w+)");
    private static final Pattern CONFIRM_CALL = Pattern.compile("confirm\\([^;\\n]*\\)");
    private static final Pattern INPUT_TAG = Pattern.compile("<input\\b[^<>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_ATTRIBUTE = Pattern.compile("(?<![\\w-])id\\s*=\\s*['\"]([\\w-]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_ATTRIBUTE = Pattern.compile("(?<![\\w-])type\\s*=\\s*['\"]([\\w-]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    /** Parses one block as a classic browser script, as a {@code <script>} element is parsed. */
    private static final String SCRIPT_PARSE = """
            const fs = require('fs');
            const vm = require('vm');
            new vm.Script(fs.readFileSync(process.argv[2], 'utf8'), { filename: process.argv[2] });
            """;

    /**
     * Runs one block in an empty context and reports the named functions it fails to define. Function
     * declarations are hoisted, so a later top-level statement that needs the page (and throws here)
     * does not hide them.
     */
    private static final String HANDLER_PROBE = """
            const fs = require('fs');
            const vm = require('vm');
            const context = vm.createContext({}, { microtaskMode: 'afterEvaluate' });
            const script = new vm.Script(fs.readFileSync(process.argv[2], 'utf8'));
            try {
                script.runInContext(context, { timeout: 5000 });
            } catch (pageDependentStatement) {
                // only the declarations matter here
            }
            const missing = process.argv.slice(3).filter(name => typeof context[name] !== 'function');
            if (missing.length) {
                console.log('not defined: ' + missing.join(', '));
                process.exit(2);
            }
            """;

    /**
     * Runs the consultant letter's handler block against a stand-in for its page and presses one
     * button (#4370). argv[3] names the scenario: print-ok and print-cancel press Print and answer its
     * "make changes?" question, save-ok presses Save and answers OK, exit-ok presses Exit and answers
     * OK. The stand-in starts in the layout the page loads with. Prints each unmet expectation and
     * exits 2.
     */
    private static final String CONSULTANT_PROBE = """
            const fs = require('fs');
            const vm = require('vm');
            const [scriptPath, scenario] = process.argv.slice(2);
            const elements = new Map();
            const element = (id) => {
                if (!elements.has(id)) {
                    elements.set(id, { id, style: {}, value: '', innerHTML: '', children: [],
                        appendChild(child) { this.children.push(child); return child; } });
                }
                return elements.get(id);
            };
            element('textDiv').style.visibility = 'hidden';
            element('textDiv').style.position = 'absolute';
            element('textareaDiv').style.position = 'relative';
            const letter = 'Dear colleague,\\nThank you for seeing this patient.';
            element('comments').value = letter;
            // forms[0].submit is the hidden input named "submit", which shadows HTMLFormElement.submit().
            const form = { submit: { value: 'exit' } };
            const posted = [];
            form.requestSubmit = () => posted.push('requestSubmit');
            const seen = { prints: 0, questions: [], closed: false };
            const answer = !scenario.endsWith('-cancel');
            const context = vm.createContext({
                document: {
                    forms: [form],
                    getElementById: element,
                    createTextNode: (text) => ({ text }),
                    createElement: (tag) => ({ tag }),
                },
                confirm: (question) => { seen.questions.push(question); return answer; },
            });
            context.window = context;
            context.print = () => { seen.prints++; };
            context.close = () => { seen.closed = true; };
            new vm.Script(fs.readFileSync(scriptPath, 'utf8')).runInContext(context, { timeout: 5000 });

            const failures = [];
            const expect = (ok, what) => { if (!ok) failures.push(what); };
            const shown = (id) => element(id).style.visibility !== 'hidden';
            const style = (id) => JSON.stringify(element(id).style);
            if (scenario.startsWith('print-')) {
                const result = context.onPrint();
                expect(result === false, 'onPrint() returned ' + result + '; from a submit button that posts the form');
                expect(form.submit.value === 'exit', 'onPrint() set the form to submit=' + form.submit.value);
                expect(posted.length === 0, 'onPrint() submitted the form');
                expect(seen.prints === 1, 'onPrint() called window.print() ' + seen.prints + ' times');
                expect(seen.questions.join() === 'global.msgDoYouWishMakeChanges',
                    'onPrint() asked ' + JSON.stringify(seen.questions));
                expect(shown('buttons'), 'the buttons stayed hidden');
                expect(element('comments').value === letter, 'the letter text changed');
                if (answer) {
                    expect(shown('textareaDiv') && element('textareaDiv').style.position === 'relative',
                        'OK did not put the editor back in the page flow: textareaDiv ' + style('textareaDiv'));
                    expect(!shown('textDiv') && element('textDiv').style.position === 'absolute',
                        'OK left the print copy shown or in the page flow: textDiv ' + style('textDiv'));
                    expect(shown('dating') && shown('searching'), 'OK left the date picker or the referral search hidden');
                } else {
                    expect(shown('textDiv') && !shown('textareaDiv'), 'Cancel left the print view');
                    expect(element('textDiv').children.length === 3, 'the print copy does not hold both lines of the letter');
                }
            } else if (scenario === 'save-ok') {
                const result = context.onSave();
                expect(result === true, 'onSave() returned ' + result + ', so Save would not post the form');
                expect(form.submit.value === 'save', 'onSave() set the form to submit=' + form.submit.value);
                expect(seen.questions.join() === 'global.msgWannaSave', 'onSave() asked ' + JSON.stringify(seen.questions));
            } else if (scenario === 'exit-ok') {
                const result = context.onExit();
                expect(result === false, 'onExit() returned ' + result + '; from a submit button that posts the form');
                expect(form.submit.value === 'exit' && posted.length === 0, 'onExit() prepared or sent a post');
                expect(seen.closed, 'OK on Exit did not close the window');
            } else {
                failures.push('unknown scenario ' + scenario);
            }
            if (failures.length) {
                console.log(failures.join('\\n'));
                process.exit(2);
            }
            """;

    @TempDir
    Path scratchDir;

    static Stream<Arguments> formsWithButtonHandlers() {
        return Stream.of(
                Arguments.of("formConsultant.jsp", List.of("onPrint", "onSave", "onExit", "onSaveExit", "cleanForm", "start")),
                Arguments.of("formCounseling.jsp", List.of("onSave", "onSaveExit", "onExit", "onPrintPDF", "reset")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formsWithButtonHandlers")
    @DisplayName("should declare each let/const name once per function")
    void shouldDeclareEachNameOnce_inEveryFunction(String jsp, List<String> handlers) throws IOException {
        List<String> redeclared = new ArrayList<>();
        for (String block : handlerScriptBlocks(jsp, handlers)) {
            redeclared.addAll(findRepeats(block, LEXICAL_DECLARATION));
        }

        assertThat(redeclared).as("let/const names declared twice in one function of %s", jsp).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formsWithButtonHandlers")
    @DisplayName("should ask each confirmation question once per function")
    void shouldAskOnce_inEveryFunction(String jsp, List<String> handlers) throws IOException {
        List<String> repeatedPrompts = new ArrayList<>();
        for (String block : handlerScriptBlocks(jsp, handlers)) {
            repeatedPrompts.addAll(findRepeats(block, CONFIRM_CALL));
        }

        assertThat(repeatedPrompts).as("the same confirm(...) call twice in one function of %s", jsp).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formsWithButtonHandlers")
    @DisplayName("should parse every inline script and define the button handlers")
    void shouldDefineButtonHandlers_whenScriptsRunInNode(String jsp, List<String> handlers)
            throws IOException, InterruptedException {
        assumeTrue(nodeIsAvailable(), "node is not on the PATH; the source checks still ran");

        List<String> blocks = inlineScriptBlocks(jsp);
        assertThat(blocks).as("inline <script> blocks in %s", jsp).isNotEmpty();
        Path parser = scratchDir.resolve("script-parse.js");
        Files.writeString(parser, SCRIPT_PARSE, StandardCharsets.UTF_8);
        String handlerBlock = null;
        for (int index = 0; index < blocks.size(); index++) {
            Path script = scratchDir.resolve("block-" + index + ".js");
            Files.writeString(script, blocks.get(index), StandardCharsets.UTF_8);
            assertNodeSucceeds(List.of("node", parser.toString(), script.toString()),
                    "classic-script parse of inline <script> block " + index + " of " + jsp);
            if (blocks.get(index).contains("function " + handlers.get(0) + "(")) {
                handlerBlock = script.toString();
            }
        }
        assertThat(handlerBlock).as("block declaring %s in %s", handlers.get(0), jsp).isNotNull();

        Path probe = scratchDir.resolve("handler-probe.js");
        Files.writeString(probe, HANDLER_PROBE, StandardCharsets.UTF_8);
        List<String> command = new ArrayList<>(List.of("node", probe.toString(), handlerBlock));
        command.addAll(handlers);
        assertNodeSucceeds(command, "button handlers defined by " + jsp);
    }

    @Test
    @DisplayName("should let only Save and Save & Exit submit the consultant letter (#4370)")
    void shouldSubmitOnlyFromSaveButtons_onConsultantLetter() throws IOException {
        Map<String, String> types = inputTypesById("formConsultant.jsp");

        assertThat(types).as("button types on formConsultant.jsp")
                .containsEntry("savebut", "submit")
                .containsEntry("saveexitbut", "submit")
                .containsEntry("exitbut", "button")
                .containsEntry("printbut", "button");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"print-ok", "print-cancel", "save-ok", "exit-ok"})
    @DisplayName("should save the consultant letter only when Save is confirmed (#4370)")
    void shouldSaveOnlyFromSave_whenConsultantHandlersRunInNode(String scenario)
            throws IOException, InterruptedException {
        assumeTrue(nodeIsAvailable(), "node is not on the PATH; the button-type check still ran");

        String handlerBlock = inlineScriptBlocks("formConsultant.jsp").stream()
                .filter(block -> block.contains("function onPrint("))
                .findFirst()
                .orElse(null);
        assertThat(handlerBlock).as("block declaring onPrint in formConsultant.jsp").isNotNull();
        Path script = scratchDir.resolve("consultant-handlers.js");
        Files.writeString(script, handlerBlock, StandardCharsets.UTF_8);
        Path probe = scratchDir.resolve("consultant-probe.js");
        Files.writeString(probe, CONSULTANT_PROBE, StandardCharsets.UTF_8);

        assertNodeSucceeds(List.of("node", probe.toString(), script.toString(), scenario),
                "consultant letter " + scenario);
    }

    @Test
    @DisplayName("should report repeated declarations and prompts in the #1805/#1806 shapes")
    void shouldReportRepeats_forTheIssueShapes() {
        String broken = """
                function onPrint() {
                    let ret;
                    const ret = confirm("x");
                    return ret;
                }
                function onSave() {
                    var ret = confirm("x");
                    var ret = confirm("x");
                    return ret;
                }
                function onExit() {
                    if (confirm("x") == true) {
                        window.close();
                    }
                    return false;
                }
                """;

        assertThat(findRepeats(broken, LEXICAL_DECLARATION)).containsExactly("onPrint: ret");
        assertThat(findRepeats(broken, CONFIRM_CALL)).containsExactly("onSave: confirm(\"x\")");
    }

    /** The page's inline scripts, after checking that they declare every expected handler. */
    private static List<String> handlerScriptBlocks(String jsp, List<String> handlers) throws IOException {
        List<String> blocks = inlineScriptBlocks(jsp);
        String scripts = String.join("\n", blocks);
        for (String handler : handlers) {
            assertThat(scripts).as("declaration of %s in the inline scripts of %s", handler, jsp)
                    .contains("function " + handler + "(");
        }
        return blocks;
    }

    /** The page with server output replaced, in the shape the browser receives. */
    private static String renderedPage(String jsp) throws IOException {
        String source = Files.readString(FORM_JSP_DIR.resolve(jsp), StandardCharsets.UTF_8);
        source = JSP_COMMENT.matcher(source).replaceAll("");
        source = JSP_EXPRESSION.matcher(source).replaceAll(PLACEHOLDER);
        source = JSP_SCRIPTLET.matcher(source).replaceAll("");
        source = EL_EXPRESSION.matcher(source).replaceAll(PLACEHOLDER);
        source = MESSAGE_TAG.matcher(source).replaceAll(tag -> {
            Matcher key = MESSAGE_KEY.matcher(tag.group());
            return Matcher.quoteReplacement(key.find() ? key.group(1) : tag.group());
        });
        return PREFIXED_SELF_CLOSING_TAG.matcher(source).replaceAll(PLACEHOLDER);
    }

    /** The {@code type} of each {@code <input>} with an id, keyed by that id. */
    private static Map<String, String> inputTypesById(String jsp) throws IOException {
        Map<String, String> types = new LinkedHashMap<>();
        Matcher input = INPUT_TAG.matcher(renderedPage(jsp));
        while (input.find()) {
            Matcher id = ID_ATTRIBUTE.matcher(input.group());
            Matcher type = TYPE_ATTRIBUTE.matcher(input.group());
            if (id.find()) {
                // An <input> without a type is a text field.
                types.put(id.group(1), type.find() ? type.group(1).toLowerCase(Locale.ROOT) : "text");
            }
        }
        return types;
    }

    /** The page's inline scripts, with server output replaced, in the shape the browser receives. */
    private static List<String> inlineScriptBlocks(String jsp) throws IOException {
        String source = renderedPage(jsp);

        List<String> blocks = new ArrayList<>();
        Matcher script = SCRIPT_BLOCK.matcher(source);
        while (script.find()) {
            if (!EXTERNAL_SCRIPT.matcher(script.group(1)).find()) {
                blocks.add(script.group(2));
            }
        }
        return blocks;
    }

    /**
     * Returns "function: match" for each match of {@code pattern} seen twice in one named function.
     *
     * <p>A source-level check that also runs without node. Each named function runs to the next
     * one, and block scope is ignored: these handlers have no nested named functions and no
     * same-name declarations in sibling blocks. The node check is the authoritative syntax test.</p>
     */
    private static List<String> findRepeats(String script, Pattern pattern) {
        List<String> repeats = new ArrayList<>();
        Matcher function = FUNCTION_START.matcher(script);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (function.find()) {
            starts.add(function.start());
            names.add(function.group(1));
        }
        for (int index = 0; index < starts.size(); index++) {
            int end = index + 1 < starts.size() ? starts.get(index + 1) : script.length();
            Set<String> seen = new HashSet<>();
            Matcher match = pattern.matcher(script.substring(starts.get(index), end));
            while (match.find()) {
                String found = match.groupCount() > 0 ? match.group(1) : match.group();
                if (!seen.add(found)) {
                    repeats.add(names.get(index) + ": " + found);
                }
            }
        }
        return repeats;
    }

    private void assertNodeSucceeds(List<String> command, String description)
            throws IOException, InterruptedException {
        // Output goes to a file so the timed wait below is the only place this can block.
        Path output = Files.createTempFile(scratchDir, "node-", ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
        assertThat(finished).as("%s finished", description).isTrue();
        assertThat(process.exitValue())
                .as("%s%n%s", description, Files.readString(output, StandardCharsets.UTF_8))
                .isZero();
    }

    private static boolean nodeIsAvailable() {
        try {
            Process probe = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            probe.getInputStream().readAllBytes();
            return probe.waitFor(10, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath);
    }
}
