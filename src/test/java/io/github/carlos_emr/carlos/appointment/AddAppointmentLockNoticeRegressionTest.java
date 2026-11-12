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
package io.github.carlos_emr.carlos.appointment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Add Appointment's slot-lock notice ("Viewers: N (locked)") shows the viewing providers' names, which
 * come from the server. The lock refresh builds it from text nodes and attributes, not from an HTML string.
 */
@DisplayName("Add Appointment slot-lock notice")
@Tag("unit")
@Tag("appointment")
class AddAppointmentLockNoticeRegressionTest {

    private static final Path PAGE = resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/appointment/addappointment.jsp"));

    /** The lock refresh, from its declaration up to the cancel function that follows it. */
    private static String lockRefresh() throws IOException {
        String jsp = Files.readString(PAGE, StandardCharsets.UTF_8);
        int start = jsp.indexOf("function updatePageLock(");
        assertThat(start).as("addappointment.jsp declares updatePageLock").isNotNegative();
        int end = jsp.indexOf("function cancelPageLock(", start);
        assertThat(end).as("cancelPageLock follows updatePageLock").isGreaterThan(start);
        return jsp.substring(start, end);
    }

    @Test
    void shouldBuildTheNoticeFromText_whenTheLockRefreshAnswers() throws IOException {
        String refresh = lockRefresh();

        assertThat(refresh).doesNotContain("innerHTML");
        assertThat(refresh)
                .contains("viewers.setAttribute('title', providerNames)")
                .contains("lockedMark.setAttribute('title', lockedProviderName)")
                .contains("viewers.textContent = 'Viewers:' + data.length")
                .contains("notice.replaceChildren(viewers)");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot find " + relativePath);
    }
}
