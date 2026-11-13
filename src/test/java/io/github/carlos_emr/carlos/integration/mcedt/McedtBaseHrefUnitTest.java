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
package io.github.carlos_emr.carlos.integration.mcedt;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the MCEDT pages that build {@code <base href>} from the request.
 * <p>
 * The base must be context-path relative. Building it from {@code getScheme()},
 * {@code getServerName()} and {@code getServerPort()} lets a TLS-terminating proxy's internal
 * Host and scheme leak into every relative link and form action (issue #5786).
 *
 * @since 2026-11-07
 */
@DisplayName("MCEDT base href")
@Tag("unit")
class McedtBaseHrefUnitTest {

    private static final String CONTEXT_RELATIVE_BASE =
            "<base href=\"<%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlAttribute(request.getContextPath() + \"/\") %>\">";

    @Test
    void shouldUseContextRelativeBase_inMcedtPagesThatBuiltItFromHostHeader() throws Exception {
        String[] pages = {
                "mcedt/addUpload.jsp",
                "mcedt/uploads.jsp",
                "mcedt/update.jsp",
                "mcedt/index.jsp",
                "mcedt/mailbox/autoDownload.jsp"
        };
        for (String page : pages) {
            String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp", page));
            assertThat(jsp)
                    .as("%s must not derive <base href> from the Host header", page)
                    .doesNotContain("request.getServerName()")
                    .doesNotContain("request.getServerPort()")
                    .contains(CONTEXT_RELATIVE_BASE);
        }
    }
}
