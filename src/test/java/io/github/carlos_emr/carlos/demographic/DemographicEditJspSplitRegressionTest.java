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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level regressions for the master demographic edit JSP split.
 *
 * <p>The legacy {@code demographiceditdemographic.jsp} previously generated a
 * {@code _jspService} method larger than the JVM's 65,535-byte method-code
 * limit during JSPC. These tests pin the compatibility wrapper and dynamic
 * fragment includes so future edits do not silently reintroduce an oversized
 * JSP target.</p>
 *
 * @since 2026-05-05
 */
@DisplayName("Demographic edit JSP split regression tests")
@Tag("unit")
@Tag("demographic")
class DemographicEditJspSplitRegressionTest {

    private static final Path LEGACY_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/demographic/demographiceditdemographic.jsp");
    private static final Path MASTER_JSP = Path.of("src/main/webapp/WEB-INF/jsp/demographic/edit.jsp");
    // Keep this wrapper tiny so JSPC cannot generate an oversized _jspService method.
    private static final int MAX_LEGACY_JSP_LINES = 80;

    @Test
    @DisplayName("should keep legacy JSP target as tiny forward wrapper")
    void shouldRemainSmall_asForwardWrapper() throws IOException {
        String legacyJsp = Files.readString(LEGACY_JSP, StandardCharsets.UTF_8);

        assertThat(legacyJsp.lines().count())
                .as("legacy JSP must stay small enough for JSPC generated _jspService bytecode")
                .isLessThan(MAX_LEGACY_JSP_LINES);
        assertThat(legacyJsp).contains("<jsp:forward page=\"/demographic/DemographicEdit\"/>");
        assertThat(legacyJsp).doesNotContain("SpringUtils.getBean");
    }

    @Test
    @DisplayName("should render demographic edit through dynamic JSP fragments")
    void shouldUseDynamicFragments_forMasterEditPage() throws IOException {
        String masterJsp = Files.readString(MASTER_JSP, StandardCharsets.UTF_8);

        assertThat(masterJsp)
                .contains("<jsp:include page=\"edit-view.jsp\"/>")
                .contains("<jsp:include page=\"edit-form-personal.jsp\"/>")
                .contains("<jsp:include page=\"edit-form-clinical.jsp\"/>");
    }
}
