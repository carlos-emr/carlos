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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomInterfaceTag")
@Tag("unit")
class CustomInterfaceTagUnitTest {

    @Test
    void shouldEncodeCommandParameter_whenAppendingIssueNoteUrl() {
        CustomInterfaceTag tag = new CustomInterfaceTag();
        StringBuilder output = new StringBuilder();

        ReflectionTestUtils.invokeMethod(
                tag, "appendIssueNoteUrl", output, "1&x='", "SocHistory", "socHistoryLabel");

        String expectedCommand = SafeEncode.forJavaScript(SafeEncode.forUriComponent("div1&x='"));
        assertThat(output.toString())
                .contains("&cmd=" + expectedCommand)
                .doesNotContain("&cmd=div1&x='");
    }

    @Test
    void shouldEmitQuotedObjectKey_whenAppendingIssueNoteUrl() {
        CustomInterfaceTag tag = new CustomInterfaceTag();
        StringBuilder output = new StringBuilder();

        ReflectionTestUtils.invokeMethod(
                tag, "appendIssueNoteUrl", output, "R1C1", "SocHistory", "socHistoryLabel");

        // The issueNoteUrls map keys must stay quoted string literals so a
        // metacharacter-bearing position cannot break the object syntax.
        assertThat(output.toString()).startsWith("\"divR1C1\":");
    }

    @Test
    void shouldEscapeObjectKey_whenPositionContainsQuote() {
        CustomInterfaceTag tag = new CustomInterfaceTag();
        StringBuilder output = new StringBuilder();

        ReflectionTestUtils.invokeMethod(
                tag, "appendIssueNoteUrl", output, "R1\"C1", "SocHistory", "socHistoryLabel");

        assertThat(output.toString())
                .startsWith("\"div" + SafeEncode.forJavaScript("R1\"C1") + "\":")
                .doesNotContain("div" + "R1\"C1");
    }
}
