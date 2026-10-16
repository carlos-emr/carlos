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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * The Mental Health Outcome print view must keep the line breaks typed into Comments.
 *
 * <p>Comments is the form's only textarea. The print view wrote it into an ordinary table cell,
 * where HTML collapses newlines, so a comment of several paragraphs printed as one block. The
 * value is now written into an element whose class sets {@code white-space: pre-wrap} for screen
 * and print alike, still encoded exactly once. Newlines are kept by the encoder and shown by the
 * style, so nothing is converted to {@code <br>} and no encoded text is touched again.</p>
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("form")
@DisplayName("Mental Health Outcome print view Comments")
class MentalHealthOutcomePrintCommentsJspRegressionTest {

    private static final Path PRINT_JSP = Path.of("src/main/webapp/WEB-INF/jsp/form/formmhoutcomeprint.jsp");
    private static final String ENCODED_COMMENTS =
            "<%= SafeEncode.forHtmlContent(props.getProperty(\"o_outComments\", \"\")) %>";

    private static String jsp() throws IOException {
        return Files.readString(PRINT_JSP, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should print Comments inside an element that keeps line breaks")
    void shouldKeepTypedLineBreaks_inPrintedComments() throws IOException {
        String jsp = jsp();

        // No whitespace between the tags and the value: under pre-wrap the JSP's own indentation
        // would print as part of the comment.
        assertThat(jsp).contains("<span class=\"mhOutComments\">" + ENCODED_COMMENTS + "</span>");
        Matcher rule = Pattern.compile("\\.mhOutComments\\s*\\{([^}]*)}").matcher(jsp);
        assertThat(rule.find()).as("page style for .mhOutComments").isTrue();
        assertThat(rule.group(1)).contains("white-space: pre-wrap");
    }

    @Test
    @DisplayName("should apply the Comments style on screen and on paper")
    void shouldStyleCommentsForEveryMedium_inPageStyle() throws IOException {
        Matcher style = Pattern.compile("<style([^>]*)>(.*?)</style>", Pattern.DOTALL).matcher(jsp());

        assertThat(style.find()).as("page <style> block").isTrue();
        // The linked sheets are split by medium (print.css for paper, mhStyles.css for screen);
        // this rule must not be limited to either.
        assertThat(style.group(1)).doesNotContain("media");
        assertThat(style.group(2)).contains(".mhOutComments");
    }

    @Test
    @DisplayName("should encode Comments once, with no line-break rewriting")
    void shouldEncodeCommentsOnce_forPrintView() throws IOException {
        String jsp = jsp();
        String line = jsp.lines().filter(l -> l.contains("o_outComments")).findFirst().orElseThrow();

        assertThat(jsp.split(Pattern.quote("o_outComments"), -1)).as("one use of the value").hasSize(2);
        assertThat(line).contains(ENCODED_COMMENTS).doesNotContain("replace").doesNotContain("<br");
        assertThat(line.split(Pattern.quote("SafeEncode."), -1)).as("one encoder call").hasSize(2);
    }

    @Test
    @DisplayName("should keep newlines and encode markup exactly once for typed comments")
    void shouldKeepNewlinesAndEncodeOnce_forTypedComments() {
        String typed = "First paragraph.\r\n\r\nLiteral &amp; stays as typed\nO'Neil <b>not bold</b>";

        assertThat(SafeEncode.forHtmlContent(typed))
                .isEqualTo("First paragraph.\r\n\r\nLiteral &amp;amp; stays as typed\nO'Neil &lt;b&gt;not bold&lt;/b&gt;");
    }
}
