/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level pins for the document viewer's comment prompt (Acknowledge and Comment).
 *
 * <p>For a document with no comment the viewer shows the translated {@code showDocument.noComment} placeholder in
 * place of the provider's comment. The prompt used to start from that visible text, so OK saved "No comment" as the
 * provider's comment. It now starts from the acknowledge form's hidden comment field, which the page fills from the
 * provider's own routing row. {@code scripts/doc-comment-prompt.test.js} drives the same functions.
 */
@DisplayName("Document viewer comment prompt")
@Tag("unit")
@Tag("fast")
@Tag("document")
class DocumentCommentPromptRegressionTest {

    private static final Path SHOW_DOCUMENT_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "documentManager", "showDocument.jsp");
    private static final Path DOCUMENT_JS = Path.of(
            "src", "main", "webapp", "share", "javascript", "oscarMDSIndex.js");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The source of one top-level function, from its declaration to its closing brace at column 0. */
    private static String function(String source, String name) {
        int start = source.indexOf("function " + name + "(");
        assertThat(start).as("function %s in oscarMDSIndex.js", name).isGreaterThanOrEqualTo(0);
        int end = source.indexOf("\n}\n", start);
        assertThat(end).as("end of function %s", name).isGreaterThan(start);
        return source.substring(start, end + 2);
    }

    @Test
    void shouldStartPromptsFromStoredComment_notFromVisibleText() throws IOException {
        String js = read(DOCUMENT_JS);

        assertThat(function(js, "storedDocComment"))
                .contains("document.getElementById(\"comment_\" + docId)")
                .contains("input.value");
        for (String name : new String[] {"getDocComment", "addDocComment"}) {
            assertThat(function(js, name))
                    .as(name)
                    .contains("const comment = storedDocComment(docId);")
                    .doesNotContain(".text()")
                    .doesNotContain("\"no comment\"");
        }
    }

    @Test
    void shouldKeepSavedComment_forTheNextPrompt() throws IOException {
        assertThat(function(read(DOCUMENT_JS), "addDocComment"))
                .contains("commentInputEl.value = submittedComment;")
                .contains("commentDisplayEl.textContent = submittedComment;")
                .doesNotContain("commentInputEl.value = \"\"");
    }

    @Test
    void shouldKeepSavedState_untilTheServerConfirmsTheCommentWrite() throws IOException {
        String comment = function(read(DOCUMENT_JS), "addDocComment");
        int post = comment.indexOf("return postForm(");
        int httpCheck = comment.indexOf("if (!response.ok)");
        int bodyCheck = comment.indexOf("if (!json || typeof json.date !== \"string\" || !json.date)");
        int saveHidden = comment.indexOf("commentInputEl.value = submittedComment;");
        int showComment = comment.indexOf("commentDisplayEl.textContent = submittedComment;");
        int failure = comment.indexOf(".catch(");

        assertThat(post).as("the comment is sent to the server").isGreaterThanOrEqualTo(0);
        assertThat(httpCheck).as("HTTP failures are refused").isGreaterThan(post);
        assertThat(bodyCheck).as("the expected save confirmation is checked").isGreaterThan(httpCheck);
        assertThat(saveHidden).as("only a confirmed comment becomes the next prompt's default").isGreaterThan(bodyCheck);
        assertThat(showComment).as("only a confirmed comment is displayed as saved").isGreaterThan(bodyCheck);
        assertThat(failure).as("failed writes have a recovery path").isGreaterThan(showComment);

        assertThat(comment.substring(0, bodyCheck))
                .as("preparing the request must not replace saved form state")
                .contains("const data = new URLSearchParams(serializeForm(formid));", "data.set(\"comment\", submittedComment);")
                .doesNotContain(".val(", "commentInputEl.value =", "commentDisplayEl.textContent =", "statusEl.value =");
        assertThat(comment.substring(failure))
                .as("a failed write tells the reviewer and preserves the saved comment and status")
                .contains("alert(")
                .doesNotContain(".value =", ".textContent =", ".val(");
    }

    @Test
    void shouldFillHiddenCommentField_fromTheReviewingProvidersOwnComment() throws IOException {
        assertThat(read(SHOW_DOCUMENT_JSP))
                .contains("reportStatus.getOscarProviderNo().equals(providerNo)")
                .contains("docCommentTxt = reportStatus.getComment();")
                .contains("id=\"comment_<%=docId%>\" value=\"<carlos:encode value='<%= docCommentTxt %>' context=\"htmlAttribute\"/>\"");
    }
}
