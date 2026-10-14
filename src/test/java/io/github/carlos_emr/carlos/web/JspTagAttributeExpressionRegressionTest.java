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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards tag attributes that take a scriptlet expression.
 *
 * <p>Jasper evaluates an attribute as a request-time expression only when the whole value is a
 * single {@code <%= ... %>}. Text in front of it, even whitespace or a line break, turns the value
 * into a literal string, so the tag receives the Java code instead of the value; text after it or
 * a second expression garbles the value or fails translation. The #1787 rewrite left one such
 * value in the BC "adjust bill" notes box: the box was given the code text, and saving the bill
 * would store that text as the billing note.</p>
 *
 * @since 2026-10-06
 */
@DisplayName("JSP tag attributes with scriptlet expressions")
@Tag("unit")
class JspTagAttributeExpressionRegressionTest {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path WEBAPP_ROOT = resolveProjectPath(Path.of("src/main/webapp"));
    private static final Path ADJUST_BILL_JSP = WEBAPP_ROOT.resolve("WEB-INF/jsp/billing/CA/BC/adjustBill.jsp");

    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    // Start of a prefixed tag: a custom tag, a JSTL tag or a jsp: standard action.
    private static final Pattern PREFIXED_TAG_START = Pattern.compile("<[A-Za-z][\\w-]*:[\\w-]+");
    // One attribute; a quoted value may contain backslash-escaped quotes, as JSP allows.
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\s+([\\w:.-]+)\\s*=\\s*(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')");
    private static final Pattern NOTES_TEXTAREA = Pattern.compile(
            "<textarea\\b[^>]*\\bname=\"messageNotes\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
    private static final Pattern ENCODED_STORED_NOTE = Pattern.compile(
            "<carlos:encode\\s+value='<%=\\s*StringUtils\\.noNull\\(messageNotes\\)\\s*%>'\\s+context=\"html\"\\s*/>");

    @Test
    @Tag("billing-bc")
    @DisplayName("should fill the adjust bill notes box with only the encoded stored note")
    void shouldPrintStoredNote_inAdjustBillNotesTextarea() throws IOException {
        String jsp = Files.readString(ADJUST_BILL_JSP, StandardCharsets.UTF_8);
        Matcher textarea = NOTES_TEXTAREA.matcher(jsp);

        assertThat(textarea.find()).as("messageNotes textarea in %s", ADJUST_BILL_JSP).isTrue();
        String content = textarea.group(1);
        // Any text or whitespace around the tag is shown in the box and saved with the note.
        assertThat(content).as("text around the encode tag in the notes box").isEqualTo(content.strip());
        assertThat(content).matches(ENCODED_STORED_NOTE);
    }

    @Test
    @DisplayName("should give every tag attribute either a whole scriptlet expression or none")
    void shouldUseWholeExpression_inEveryTagAttributeWithAScriptletExpression() throws IOException {
        List<String> mixed = new ArrayList<>();
        try (Stream<Path> files = Files.walk(WEBAPP_ROOT)) {
            for (Path file : files.filter(JspTagAttributeExpressionRegressionTest::isJspSource).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (String finding : findMixedExpressionAttributes(source)) {
                    mixed.add(WEBAPP_ROOT.relativize(file) + ":" + finding);
                }
            }
        }

        assertThat(mixed)
                .as("tag attributes that mix text with a scriptlet expression (Jasper prints the code literally)")
                .isEmpty();
    }

    @Test
    @DisplayName("should report the #1787 shape: whitespace and a line break before the expression")
    void shouldReportMixedAttribute_forTheAdjustBillShape() {
        String broken = "<textarea><carlos:encode value=\"            \n<%= StringUtils.noNull(messageNotes) %>\""
                + " context=\"html\"/></textarea>";

        assertThat(findMixedExpressionAttributes(broken)).containsExactly("1 carlos:encode value");
    }

    @Test
    @DisplayName("should report text after the expression and two expressions in one value")
    void shouldReportMixedAttribute_forTrailingTextOrTwoExpressions() {
        String source = """
                <c:set var="a" value="<%= first %> "/>
                <c:set var="b" value='<%= first %><%= second %>'/>""";

        assertThat(findMixedExpressionAttributes(source)).containsExactly("1 c:set value", "2 c:set value");
    }

    @Test
    @DisplayName("should accept whole expressions, including escaped quotes and plain attributes")
    void shouldAcceptWholeExpression_withEscapedQuotesOrNoExpression() {
        String source = """
                <carlos:encode value="<%=bundle.getString(\\"key.name\\") %>" context="javascriptBlock"/>
                <carlos:encode value='<%= note %>' context="html"/>
                <fmt:message key="global.btnSave"/>
                <%-- <carlos:encode value=" <%= commentedOut %>"/> --%>
                <input value="  <%= templateTextIsFine %>  ">""";

        assertThat(findMixedExpressionAttributes(source)).isEmpty();
    }

    /** Returns "line tag attribute" for each prefixed-tag attribute that mixes text with an expression. */
    private static List<String> findMixedExpressionAttributes(String jspSource) {
        // Blank out JSP comments but keep their line breaks, so line numbers stay true.
        Matcher comment = JSP_COMMENT.matcher(jspSource);
        StringBuilder blanked = new StringBuilder();
        while (comment.find()) {
            comment.appendReplacement(blanked, Matcher.quoteReplacement(comment.group().replaceAll("[^\\n]", " ")));
        }
        comment.appendTail(blanked);
        String source = blanked.toString();

        List<String> findings = new ArrayList<>();
        Matcher tag = PREFIXED_TAG_START.matcher(source);
        while (tag.find()) {
            Matcher attribute = ATTRIBUTE.matcher(source);
            int position = tag.end();
            attribute.region(position, source.length());
            while (attribute.lookingAt()) {
                String quoted = attribute.group(2);
                String value = quoted.substring(1, quoted.length() - 1);
                if (value.contains("<%=") && !isWholeExpression(value)) {
                    int line = 1 + (int) source.substring(0, attribute.start(2)).chars().filter(c -> c == '\n').count();
                    findings.add(line + " " + tag.group().substring(1) + " " + attribute.group(1));
                }
                position = attribute.end();
                attribute.region(position, source.length());
            }
        }
        return findings;
    }

    private static boolean isWholeExpression(String value) {
        return value.startsWith("<%=") && value.indexOf("%>") == value.length() - 2;
    }

    private static boolean isJspSource(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file) && (name.endsWith(".jsp") || name.endsWith(".jspf") || name.endsWith(".tag"));
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
