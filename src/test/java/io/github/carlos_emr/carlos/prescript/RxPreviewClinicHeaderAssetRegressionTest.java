/**
 * Copyright (c) 2026 CARLOS EMR Contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.carlos_emr.carlos.prescript;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins how {@code rx/Preview2.jsp} turns the clinic header's {@code <br>} joins into the line
 * breaks the PDF servlet renders.
 *
 * <p>The header is composed as {@code name<br>address<br>city   postal} and handed to the servlet
 * through the hidden {@code clinicName} input. That input's value is a tag ATTRIBUTE, and the JSP
 * specification unescapes {@code \\} to {@code \} inside attribute values before the scriptlet is
 * compiled — so the former {@code replaceAll("(<br>)", "\\\n")} reached Java as {@code "\\n"}, a
 * replacement string of backslash + n, which regex replacement reads as an escaped literal
 * {@code n}. Every faxed prescription rendered its clinic header as one glued line
 * ({@code ClinicnAddressnCity}). A literal {@link String#replace(CharSequence, CharSequence)} with
 * a plain {@code "\n"} has no second escaping layer to fall through.</p>
 *
 * <p>The browser check {@code scripts/rx-fax-record-binding-playwright-checks.js} asserts the
 * rendered result on a packaged install; this test keeps the source from regressing between runs.</p>
 *
 * @since 2026-09-05
 */
@DisplayName("Rx preview clinic header asset regressions")
@Tag("unit")
@Tag("prescription")
class RxPreviewClinicHeaderAssetRegressionTest {

    private static final Path PREVIEW2_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "rx", "Preview2.jsp");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The page without its JSP comments, so a fix explained in a comment cannot satisfy or fail an assertion. */
    private static String executableJsp(String page) {
        return page.replaceAll("(?s)<%--.*?--%>", "");
    }

    @Test
    @DisplayName("should convert the clinic header's <br> joins with a literal newline, not a regex replacement")
    void shouldConvertHeaderBreaks_withLiteralNewline() throws IOException {
        String page = executableJsp(read(PREVIEW2_JSP));

        assertThat(page)
                .as("clinicName is fed to the PDF servlet with real line breaks")
                .contains("clinicTitle.replace(\"<br>\", \"\\n\")");
        // Whitespace-insensitive: the regex form must not come back in any layout. Inside a JSP
        // attribute, \\\n compiles to a replacement of backslash + n, i.e. the letter n.
        assertThat(page.replaceAll("\\s+", ""))
                .doesNotContain("clinicTitle.replaceAll(\"(<br>)\"");
    }
}
