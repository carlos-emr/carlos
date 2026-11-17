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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks that the inline JavaScript of JSPs damaged by the automated encoder rewrite in #1787 still
 * parses.
 *
 * <p>That rewrite inserted {@code <c:set var="__enc_N">} lines and, in a few places, split the
 * following line in the middle of a token. Inside a JavaScript string literal the split leaves a raw
 * line break, which is a syntax error, so the browser drops the whole {@code <script>} block. Issue
 * #4089: in {@code demographicupdatearecord.jsp} the waiting-list confirm branch never ran and staff
 * saw a blank page.
 *
 * <p>The JSP source is not JavaScript until the server renders it, so each check first swaps JSP
 * constructs for placeholder values: {@code <%= %>}, {@code ${}} and self-closing custom tags become
 * {@code x}; scriptlets, {@code <c:set>} bodies and other tag markup become nothing. Two checks run
 * on the result:
 * <ul>
 *   <li>a small tokenizer that fails on any quoted string literal cut by a raw line break (always
 *       runs, no external tools);</li>
 *   <li>{@code node --check} on each block for a full parse, the same approach as
 *       {@code EFormRenderPdfHtmlComposerUnitTest}. It is skipped when {@code node} is not on the
 *       PATH (the dev container and CI image both ship it).</li>
 * </ul>
 *
 * @since 2026-09-30
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Inline script syntax of JSPs split by the #1787 rewrite")
class InlineScriptJspRegressionTest {

    /** Placeholder for anything the server would print; valid both inside a string and as an expression. */
    private static final String PLACEHOLDER = "x";

    /*
     * Stands in for a source line break that the browser never receives, because it sat inside a
     * scriptlet or tag the server consumes. It still counts toward line numbers, but it must not
     * look like a raw line break inside a string literal.
     */
    private static final char HIDDEN_LINE_BREAK = '\u0001';

    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern JSP_EXPRESSION = Pattern.compile("<%=.*?%>", Pattern.DOTALL);
    // Scriptlets, declarations and directives: they print nothing themselves.
    private static final Pattern JSP_SCRIPTLET = Pattern.compile("<%.*?%>", Pattern.DOTALL);
    // EL, except the escaped form \${ which JSP prints literally (JavaScript template interpolation).
    private static final Pattern EL_EXPRESSION = Pattern.compile("(?<!\\\\)\\$\\{[^}]*}");
    private static final Pattern C_SET = Pattern.compile("<c:set\\b[^>]*/>|<c:set\\b[^>]*>.*?</c:set>",
            Pattern.DOTALL);
    private static final String TAG_ATTRIBUTES = "(?:\\s+[\\w:.-]+(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))?)*";
    private static final Pattern SELF_CLOSING_TAG = Pattern.compile(
            "<[A-Za-z][\\w-]*:[\\w-]+" + TAG_ATTRIBUTES + "\\s*/>");
    private static final Pattern BODY_TAG_MARKUP = Pattern.compile(
            "</?[A-Za-z][\\w-]*:[\\w-]+" + TAG_ATTRIBUTES + "\\s*>");

    // Group 1: the attributes of the <script> tag. Group 2: the inline source.
    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "<script\\b([^>]*)>(.*?)</script\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    // A type attribute naming something other than JavaScript, such as an HTML template or JSON.
    private static final Pattern NON_JAVASCRIPT_TYPE = Pattern.compile(
            "\\btype\\s*=\\s*[\"'](?![^\"']*javascript)[^\"']+[\"']", Pattern.CASE_INSENSITIVE);

    // After one of these characters a '/' starts a regular expression literal, not a division.
    private static final String REGEX_MAY_FOLLOW = "(,=:[!&|?{};+-*%<>~^";
    // After one of these keywords a '/' also starts a regular expression literal.
    private static final Set<String> REGEX_MAY_FOLLOW_KEYWORDS = Set.of(
            "return", "typeof", "case", "do", "else", "in", "of", "new", "delete", "void", "throw", "instanceof");

    @TempDir
    Path scratchDir;

    /** The pages whose inline scripts #1787 split. Add a page here when fixing another one. */
    static Stream<Path> pagesSplitByTheEncoderRewrite() {
        return Stream.of(
                // #4089: waiting-list confirm branch of the demographic update result page
                Path.of("src/main/webapp/WEB-INF/jsp/demographic/demographicupdatearecord.jsp"),
                Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/noteBrowser.jsp"),
                Path.of("src/main/webapp/WEB-INF/jsp/documentManager/documentBrowser.jsp"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pagesSplitByTheEncoderRewrite")
    @DisplayName("should keep every JavaScript string literal on one line")
    void shouldKeepStringLiteralsOnOneLine_inInlineScriptBlocks(Path jsp) throws IOException {
        List<ScriptBlock> blocks = inlineScriptBlocks(jsp);
        assertThat(blocks).as("inline <script> blocks found in %s", jsp).isNotEmpty();

        List<String> splitLiterals = new ArrayList<>();
        for (ScriptBlock block : blocks) {
            splitLiterals.addAll(findSplitStringLiterals(jsp, block));
        }

        assertThat(splitLiterals)
                .as("string literals broken by a raw line break (JSP output shown as '%s')", PLACEHOLDER)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pagesSplitByTheEncoderRewrite")
    @DisplayName("should parse every inline script block as JavaScript")
    void shouldParseAsJavaScript_forEveryInlineScriptBlock(Path jsp) throws IOException, InterruptedException {
        assumeTrue(nodeIsAvailable(), "node is not on the PATH; the string-literal check still ran");

        List<ScriptBlock> blocks = inlineScriptBlocks(jsp);
        assertThat(blocks).as("inline <script> blocks found in %s", jsp).isNotEmpty();

        for (ScriptBlock block : blocks) {
            Path script = scratchDir.resolve("block-" + block.firstLine() + ".js");
            Files.writeString(script, block.source().replace(String.valueOf(HIDDEN_LINE_BREAK), ""),
                    StandardCharsets.UTF_8);
            Process check = new ProcessBuilder("node", "--check", script.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(check.waitFor(30, TimeUnit.SECONDS)).as("node --check finished").isTrue();
            assertThat(check.exitValue())
                    .as("node --check on the <script> block starting at %s:%d%n%s", jsp, block.firstLine(), output)
                    .isZero();
        }
    }

    @Test
    @DisplayName("should keep the SearchPatient URL of the BC lab eChart button on one line")
    void shouldKeepSearchPatientUrlOnOneLine_inBcLabEChartButton() throws IOException {
        // Same #1787 split, but inside an onClick attribute rather than a <script> block: the handler
        // failed to compile, so the button did nothing.
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/lab/CA/BC/labDisplay.jsp"),
                StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContainPattern("/oscarMDS/Se\\s+archPatient");
    }

    @Test
    @DisplayName("should report a string literal that a line break splits")
    void shouldReportSplitLiteral_forTheIssue4089Shape() {
        // Guards the tokenizer itself: this is the broken line from issue #4089 after placeholder swap.
        ScriptBlock broken = new ScriptBlock(10, """
                if (add2List) {
                    document.add2WLFrm.action = "x/wa
                itinglist/Add2WaitingList";
                } else {
                    // it's fine: apostrophes in comments and "quotes" on one line are not reported
                    var pattern = /["']/g;
                }
                """);

        assertThat(findSplitStringLiterals(Path.of("example.jsp"), broken))
                .containsExactly("example.jsp:11: \"x/wa");
    }

    /** One inline script: its source after placeholder swap, and the JSP line its body starts on. */
    private record ScriptBlock(int firstLine, String source) {
    }

    private static List<ScriptBlock> inlineScriptBlocks(Path jsp) throws IOException {
        String rendered = replaceJspConstructs(Files.readString(jsp, StandardCharsets.UTF_8));
        List<ScriptBlock> blocks = new ArrayList<>();
        Matcher script = SCRIPT_BLOCK.matcher(rendered);
        while (script.find()) {
            boolean javaScript = !NON_JAVASCRIPT_TYPE.matcher(script.group(1)).find();
            if (javaScript && !script.group(2).isBlank()) {
                blocks.add(new ScriptBlock(1 + countLineBreaks(rendered, 0, script.start(2)), script.group(2)));
            }
        }
        return blocks;
    }

    /** Approximates what the server sends for this JSP, keeping every source line break countable. */
    private static String replaceJspConstructs(String jsp) {
        String text = replaceKeepingLines(jsp, JSP_COMMENT, "");
        text = replaceKeepingLines(text, JSP_EXPRESSION, PLACEHOLDER);
        text = replaceKeepingLines(text, JSP_SCRIPTLET, "");
        text = replaceKeepingLines(text, EL_EXPRESSION, PLACEHOLDER).replace("\\${", "${");
        // Expressions are gone now, so tag attributes no longer contain '>' and the tag patterns hold.
        text = replaceKeepingLines(text, C_SET, "");
        text = replaceKeepingLines(text, SELF_CLOSING_TAG, PLACEHOLDER);
        return replaceKeepingLines(text, BODY_TAG_MARKUP, "");
    }

    private static String replaceKeepingLines(String text, Pattern pattern, String replacement) {
        return pattern.matcher(text).replaceAll(match -> Matcher.quoteReplacement(replacement
                + String.valueOf(HIDDEN_LINE_BREAK).repeat(countLineBreaks(match.group(), 0, match.group().length()))));
    }

    /**
     * Returns {@code file:line: literal-start} for each '...' or "..." literal that runs into a raw
     * line break. Comments, template literals and regular expression literals are skipped so that
     * quote characters inside them are not mistaken for string delimiters.
     */
    private static List<String> findSplitStringLiterals(Path jsp, ScriptBlock block) {
        String js = block.source();
        List<String> found = new ArrayList<>();
        int line = block.firstLine();
        char previous = '(';  // last significant character; at the start a '/' begins a regex
        int i = 0;
        while (i < js.length()) {
            char c = js.charAt(i);
            if (c == '\n' || c == HIDDEN_LINE_BREAK) {
                line++;
                i++;
            } else if (Character.isWhitespace(c)) {
                i++;
            } else if (js.startsWith("//", i) || js.startsWith("<!--", i)) {
                // Browsers also treat "<!--" in a classic script as a line comment.
                i = endOfLine(js, i);
            } else if (js.startsWith("/*", i)) {
                int end = js.indexOf("*/", i + 2);
                end = end < 0 ? js.length() : end + 2;
                line += countLineBreaks(js, i, end);
                i = end;
            } else if (c == '\'' || c == '"') {
                int end = i + 1;
                while (end < js.length() && js.charAt(end) != c && js.charAt(end) != '\n') {
                    if (js.charAt(end) == '\\' && end + 1 < js.length()) {
                        end++;  // escaped character, including a backslash line continuation
                    }
                    if (js.charAt(end) == '\n' || js.charAt(end) == HIDDEN_LINE_BREAK) {
                        line++;
                    }
                    end++;
                }
                if (end < js.length() && js.charAt(end) == '\n') {
                    found.add(jsp + ":" + line + ": "
                            + js.substring(i, end).replace(String.valueOf(HIDDEN_LINE_BREAK), "").strip());
                    // Skip the dangling tail of the broken literal so it is not reported a second time.
                    line++;
                    end = endOfLine(js, end + 1) - 1;
                }
                i = end + 1;
                previous = c;
            } else if (c == '`') {
                int end = i + 1;
                while (end < js.length() && js.charAt(end) != '`') {
                    if (js.charAt(end) == '\\') {
                        end++;
                    } else if (js.charAt(end) == '\n' || js.charAt(end) == HIDDEN_LINE_BREAK) {
                        line++;  // template literals may span lines
                    }
                    end++;
                }
                i = end + 1;
                previous = c;
            } else if (c == '/' && REGEX_MAY_FOLLOW.indexOf(previous) >= 0) {
                i = endOfRegexLiteral(js, i);
                previous = 'r';
            } else if (Character.isJavaIdentifierStart(c)) {
                int end = i + 1;
                while (end < js.length() && Character.isJavaIdentifierPart(js.charAt(end))) {
                    end++;
                }
                previous = REGEX_MAY_FOLLOW_KEYWORDS.contains(js.substring(i, end)) ? '(' : 'a';
                i = end;
            } else {
                previous = c;
                i++;
            }
        }
        return found;
    }

    private static int endOfRegexLiteral(String js, int start) {
        boolean inCharacterClass = false;
        int i = start + 1;
        while (i < js.length() && js.charAt(i) != '\n') {
            char c = js.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '[') {
                inCharacterClass = true;
            } else if (c == ']') {
                inCharacterClass = false;
            } else if (c == '/' && !inCharacterClass) {
                return i + 1;
            }
            i++;
        }
        return i;
    }

    private static int endOfLine(String text, int from) {
        int end = text.indexOf('\n', from);
        return end < 0 ? text.length() : end;
    }

    private static int countLineBreaks(String text, int from, int to) {
        int count = 0;
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == HIDDEN_LINE_BREAK) {
                count++;
            }
        }
        return count;
    }

    private static boolean nodeIsAvailable() throws InterruptedException {
        try {
            Process version = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            version.getInputStream().readAllBytes();
            return version.waitFor(10, TimeUnit.SECONDS) && version.exitValue() == 0;
        } catch (IOException notInstalled) {
            return false;
        }
    }
}
