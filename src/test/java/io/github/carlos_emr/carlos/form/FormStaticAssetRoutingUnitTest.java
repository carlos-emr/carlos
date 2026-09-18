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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the image extensions the form pages serve out of the webapp against
 * {@code struts.action.excludePattern}.
 *
 * <p>An extension missing from that alternation is not a 404 from the container: Struts claims
 * the request, finds no action of that name and answers 404 itself, even though the file is
 * packaged and deployed. {@code formmmse.jsp} references {@code MMSEpentagons.bmp} and
 * {@code bmp} was absent, so that image had never loaded — a failure indistinguishable from the
 * broken relative paths #3727 is about, and one that only shows up against a running server.
 */
@DisplayName("Form static asset routing Tests")
@Tag("unit")
@Tag("form")
class FormStaticAssetRoutingUnitTest {

    private static final Path STRUTS_XML = Path.of("src/main/webapp/WEB-INF/classes/struts.xml");
    private static final Pattern EXCLUDE_CONSTANT = Pattern.compile(
            "<constant name=\"struts\\.action\\.excludePattern\" value=\"([^\"]+)\"\\s*/>");

    private static Pattern excludePattern() throws IOException {
        Matcher matcher = EXCLUDE_CONSTANT.matcher(Files.readString(STRUTS_XML, StandardCharsets.UTF_8));
        assertThat(matcher.find()).as("struts.xml declares struts.action.excludePattern").isTrue();
        return Pattern.compile(matcher.group(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/form/graphics/MMSEpentagons.bmp",
            "/carlos/form/graphics/MMSEpentagons.bmp",
            "/form/graphics/checkmark.gif",
            "/form/graphics/caregiver/scale.png",
            "/form/graphics/functionVisualAid/some.jpg",
            "/form/print.css",
            "/form/formScripts.js",
    })
    @DisplayName("should let a form asset bypass Struts and reach the container")
    void shouldBypassStruts_forFormAssetRequest(String path) throws IOException {
        assertThat(excludePattern().matcher(path).matches())
                .as("%s must be served as a static resource, not resolved as an action", path)
                .isTrue();
    }

    @Test
    @DisplayName("should still route an extensionless form action through Struts")
    void shouldRouteThroughStruts_forExtensionlessFormAction() throws IOException {
        // The guard on the guard: widening the alternation must not start excluding the
        // routes the forms are actually reached by.
        assertThat(excludePattern().matcher("/form/formmmse").matches()).isFalse();
        assertThat(excludePattern().matcher("/form/formlabreq07").matches()).isFalse();
    }
}
