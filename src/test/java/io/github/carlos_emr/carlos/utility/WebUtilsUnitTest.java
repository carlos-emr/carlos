/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebUtils")
@Tag("unit")
class WebUtilsUnitTest {

    @Test
    void shouldEscapeMessages_whenRenderedAsHtml() {
        MockHttpSession session = new MockHttpSession();
        WebUtils.addErrorMessage(session, "<script>alert(1)</script>");

        String html = WebUtils.popErrorMessagesAsHtml(session);

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("</li>");
        assertThat(html).doesNotContain("</il>");
    }
    @Test
    void shouldEscapeInfoMessages_whenRenderedAsHtml() {
        MockHttpSession session = new MockHttpSession();
        WebUtils.addInfoMessage(session, "saved <b>file</b>");

        String html = WebUtils.popInfoMessagesAsHtml(session);

        assertThat(html).contains("saved &lt;b&gt;file&lt;/b&gt;");
        assertThat(html).doesNotContain("<b>file</b>");
        assertThat(html).contains("</li>");
        assertThat(html).doesNotContain("</il>");
    }

}
