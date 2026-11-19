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
package io.github.carlos_emr.carlos.prevention;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the output encoding of the patient heading on the vaccine-choice popup, in line with the
 * OWASP encoding rules the rest of the prevention pages follow.
 *
 * @since 2026-10-05
 */
@DisplayName("AddPreventionDataDisambiguate.jsp patient heading encoding")
@Tag("unit")
@Tag("prevention")
class AddPreventionDataDisambiguateEncodingUnitTest {
    private static final Path DISAMBIGUATE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/prevention/AddPreventionDataDisambiguate.jsp");

    @Test
    @DisplayName("should encode the patient's name and age heading as HTML text")
    void shouldEncodePatientHeading_asHtmlText() throws Exception {
        String jsp = Files.readString(DISAMBIGUATE_JSP);

        assertThat(jsp)
                .doesNotContain("<%=nameage%>")
                .contains("<carlos:encode value='<%= nameage %>' context=\"html\"/>");
    }
}
