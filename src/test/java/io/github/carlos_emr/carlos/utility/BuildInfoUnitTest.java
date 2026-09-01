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

import java.util.Properties;

import io.github.carlos_emr.CarlosProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BuildInfo}: tag composition, placeholder hygiene, and the guarantee that
 * the build identity cannot be shadowed by a {@code carlos.properties} override.
 */
@DisplayName("BuildInfo build identity tests")
@Tag("unit")
class BuildInfoUnitTest {

    private static BuildInfo of(String version, String date, String job, String number) {
        Properties properties = new Properties();
        if (version != null) properties.setProperty(BuildInfo.VERSION_KEY, version);
        if (date != null) properties.setProperty(BuildInfo.DATE_KEY, date);
        if (job != null) properties.setProperty(BuildInfo.JOB_KEY, job);
        if (number != null) properties.setProperty(BuildInfo.NUMBER_KEY, number);
        return new BuildInfo(properties);
    }

    @Test
    @DisplayName("should compose tag from version, job and number when all are stamped")
    void shouldComposeTag_withVersionJobAndNumber() {
        BuildInfo info = of("2026.08.0-alpha11-SNAPSHOT", "2026-09-01 10:15 AM", "carlos-emr-deb", "2026.08.0~alpha11");

        assertThat(info.getBuildTag()).isEqualTo("2026.08.0-alpha11-SNAPSHOT (carlos-emr-deb 2026.08.0~alpha11)");
        assertThat(info.getBuildDate()).isEqualTo("2026-09-01 10:15 AM");
    }

    @Test
    @DisplayName("should return bare version when the build carried no CI stamp")
    void shouldReturnBareVersion_whenNoStamp() {
        BuildInfo info = of("2026.08.0-alpha11", "2026-09-01 10:15 AM", "", "");

        assertThat(info.getBuildTag()).isEqualTo("2026.08.0-alpha11");
        assertThat(info.getJobName()).isEmpty();
        assertThat(info.getBuildNumber()).isEmpty();
    }

    @Test
    @DisplayName("should append only the build number when the job name is absent")
    void shouldAppendNumberOnly_whenJobNameAbsent() {
        BuildInfo info = of("2026.08.0-alpha11", null, null, "20260901-101500");

        assertThat(info.getBuildTag()).isEqualTo("2026.08.0-alpha11 (20260901-101500)");
    }

    @Test
    @DisplayName("should treat unsubstituted placeholders as absent so they never reach the login page")
    void shouldIgnorePlaceholders_whenStampUnsubstituted() {
        BuildInfo info = of("2026.08.0-alpha11", "${build.dateTime}", "${env.JOB_NAME}", "${env.BUILD_NUMBER}");

        assertThat(info.getBuildTag()).isEqualTo("2026.08.0-alpha11");
        assertThat(info.getBuildDate()).isEmpty();
    }

    @Test
    @DisplayName("should report unknown when nothing is stamped")
    void shouldReportUnknown_whenNothingStamped() {
        BuildInfo info = new BuildInfo(new Properties());

        assertThat(info.getBuildTag()).isEqualTo("unknown");
        assertThat(info.getVersion()).isEmpty();
        assertThat(info.getBuildDate()).isEmpty();
    }

    @Test
    @DisplayName("should load a placeholder-free stamp from the classpath")
    void shouldLoadStamp_fromClasspath() {
        // The real values (project version, build date) are written into
        // target/classes/carlos-build.properties by Maven resource filtering plus the antrun
        // process-classes step, so a full Maven build has them. A bare-runner / IDE build that
        // copies src/main/resources without filtering leaves the raw ${...} placeholders, which
        // BuildInfo.clean() strips to empty. Assert only what holds either way: nothing rendered
        // ever contains a raw placeholder, and the tag composition is consistent. When the build
        // pipeline HAS filtered the resource (version present), assert the tag starts with it.
        BuildInfo info = BuildInfo.fromClasspath();

        assertThat(info.getVersion()).doesNotContain("${");
        assertThat(info.getBuildDate()).doesNotContain("${");
        assertThat(info.getBuildTag()).doesNotContain("${");
        if (!info.getVersion().isEmpty()) {
            assertThat(info.getBuildTag()).startsWith(info.getVersion());
        } else {
            assertThat(info.getBuildTag()).isEqualTo("unknown");
        }
    }

    @Test
    @DisplayName("should not let a carlos.properties buildVersion override shadow the WAR build tag")
    void shouldIgnoreLegacyOverride_forBuildTag() {
        String previous = CarlosProperties.getInstance().getProperty("buildVersion", null);
        try {
            CarlosProperties.getInstance().setProperty("buildVersion", "stale-first-install 2026.08.0~alpha4");

            assertThat(CarlosProperties.getBuildTag()).isEqualTo(BuildInfo.getInstance().getBuildTag());
            assertThat(CarlosProperties.getBuildTag()).doesNotContain("stale-first-install");
        } finally {
            if (previous == null) {
                CarlosProperties.getInstance().remove("buildVersion");
            } else {
                CarlosProperties.getInstance().setProperty("buildVersion", previous);
            }
        }
    }
}
