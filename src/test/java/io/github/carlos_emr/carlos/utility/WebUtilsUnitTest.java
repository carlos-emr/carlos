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

    @Test
    void shouldEscapeStyledMessages_whenRenderedAsHtml() {
        MockHttpSession session = new MockHttpSession();
        WebUtils.addErrorMessage(session, "failed <b>claim</b>");

        String html = WebUtils.popErrorMessagesAsHtml(session, "alert", "color:red", "err", "errName");

        assertThat(html).contains("id=\"err\"");
        assertThat(html).contains("name=\"errName\"");
        assertThat(html).contains("class=\"alert\"");
        assertThat(html).contains("style=\"color:red\"");
        assertThat(html).contains("failed &lt;b&gt;claim&lt;/b&gt;");
        assertThat(html).doesNotContain("<b>claim</b>");
        assertThat(html).contains("</li>");
        assertThat(html).doesNotContain("</il>");
    }

}
