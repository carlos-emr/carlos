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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Master Record read view shows the home and work phones, their extensions and the cell phone through
 * {@code carlos:encode}, as develop does.
 *
 * @since 2026-10-18
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("Master Record read view phone lines")
class MasterRecordPhoneLinesJspUnitTest {

    private static String editView() throws IOException, URISyntaxException {
        Path current = Path.of(MasterRecordPhoneLinesJspUnitTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        while (current != null && !Files.isDirectory(current.resolve("src/main/webapp"))) {
            current = current.getParent();
        }
        assertThat(current).as("project root with src/main/webapp").isNotNull();
        return Files.readString(current.resolve("src/main/webapp/WEB-INF/jsp/demographic/edit-view.jsp"), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "demographic.getPhone()",
            "demoExt.get(\"hPhoneExt\")",
            "demographic.getPhone2()",
            "demoExt.get(\"wPhoneExt\")",
            "demoExt.get(\"demo_cell\")"})
    void shouldShowThePhoneValueThroughTheEncoder_forEachPhoneLine(String value) throws Exception {
        String jsp = editView();

        assertThat(jsp).contains("<carlos:encode value='<%= StringUtils.trimToEmpty(" + value + ") %>' context=\"html\"/>");
        assertThat(jsp).doesNotContain("<%=StringUtils.trimToEmpty(" + value + ")%>");
    }
}
