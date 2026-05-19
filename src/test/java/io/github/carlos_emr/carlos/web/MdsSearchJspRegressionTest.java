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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the MDS search JSP against server-side tags leaking into JavaScript.
 *
 * @since 2026-05-19
 */
@DisplayName("MDS search JSP regressions")
@Tag("unit")
@Tag("regression")
@Tag("security")
class MdsSearchJspRegressionTest {

    private static final Pattern SCRIPT_BLOCK_PATTERN = Pattern.compile("(?is)<script\\b[^>]*>(.*?)</script>");
    private static final Pattern ON_SUBMIT_FUNCTION_PATTERN = Pattern.compile("\\bfunction\\s+onSubmitCheck\\s*\\(");
    private static final Path SEARCH_JSP = repositoryRoot()
            .resolve(Path.of("src", "main", "webapp", "WEB-INF", "jsp", "oscarMDS", "Search.jsp"));

    @Test
    @DisplayName("should precompute providerNo encoding before JavaScript builds search URL")
    void shouldPrecomputeProviderNoEncoding_beforeJavaScriptBuildsUrl() throws IOException {
        String jsp = Files.readString(SEARCH_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("String encodedProviderNo")
                .contains("SafeEncode.forJavaScript")
                .contains("SafeEncode.forUriComponent")
                .contains("request.getParameter(\"providerNo\")")
                .contains("&providerNo=<%= encodedProviderNo %>");
        assertThat(onSubmitScriptBlock(jsp))
                .doesNotContain("<c:")
                .doesNotContain("<carlos:")
                .doesNotContain("<e:")
                .doesNotContain("<fn:")
                .doesNotContain("<jsp:");
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("src/main/webapp"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate CARLOS repository root");
    }

    private static String onSubmitScriptBlock(String jsp) {
        Matcher matcher = SCRIPT_BLOCK_PATTERN.matcher(jsp);
        while (matcher.find()) {
            String scriptBody = matcher.group(1);
            if (ON_SUBMIT_FUNCTION_PATTERN.matcher(scriptBody).find()) {
                return scriptBody;
            }
        }
        throw new IllegalStateException("Unable to locate MDS search submit script block");
    }
}
