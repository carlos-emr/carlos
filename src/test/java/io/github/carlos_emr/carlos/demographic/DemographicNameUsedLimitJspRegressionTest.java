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
package io.github.carlos_emr.carlos.demographic;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #5128: both Master Record forms stop a Name Used at the alias column (70), where it is
 * stored whole. The add page sets its limit from the shared field-length fragment; the edit form
 * carries it on the box itself (before, it had none).
 *
 * @since 2026-10-21
 */
@Tag("unit")
@Tag("demographic")
class DemographicNameUsedLimitJspRegressionTest {

    private static final String ALIAS_LIMIT = "<%= Demographic.ALIAS_MAX_LENGTH %>";

    @Test
    @DisplayName("should limit the edit form's Name Used box to the alias column")
    void shouldLimitNameUsedToTheAlias_onTheEditForm() throws IOException {
        String jsp = read("WEB-INF/jsp/demographic/edit-form-personal.jsp");

        int box = jsp.indexOf("name=\"nameUsed\"");
        assertThat(box).as("the edit form renders a Name Used box").isNotNegative();
        // The tag holds a scriptlet (getDisabled), so read up to the end of its attributes, not the first '>'.
        String attributes = jsp.substring(box, jsp.indexOf("onBlur", box));
        assertThat(attributes).contains("maxlength=\"" + ALIAS_LIMIT + "\"");
    }

    @Test
    @DisplayName("should limit the add page's Name Used box to the alias column through the shared fragment")
    void shouldLimitNameUsedToTheAlias_onTheAddPage() throws IOException {
        assertThat(read("WEB-INF/jsp/demographic/add.jsp"))
                .contains("/WEB-INF/jspf/demographic-field-length-limits.jspf");
        assertThat(read("WEB-INF/jspf/demographic-field-length-limits.jspf"))
                .contains("nameUsed: <%= Demographic.ALIAS_MAX_LENGTH %>");
    }

    private static String read(String webappPath) throws IOException {
        return Files.readString(projectRoot().resolve("src/main/webapp").resolve(webappPath), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        try {
            Path location = Path.of(DemographicNameUsedLimitJspRegressionTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path current = Files.isRegularFile(location) ? location.getParent() : location;
                 current != null; current = current.getParent()) {
                if (Files.isDirectory(current.resolve("src/main/webapp"))) {
                    return current;
                }
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve test class location", e);
        }
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(workingDirectory.resolve("src/main/webapp"))) {
            return workingDirectory;
        }
        throw new IllegalStateException("Unable to locate CARLOS EMR project root from test classpath");
    }
}
