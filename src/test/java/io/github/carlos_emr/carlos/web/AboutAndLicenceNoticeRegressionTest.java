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
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the two user-facing legal pages: About
 * ({@code /encounter/ViewAbout}) and Licence ({@code /encounter/ViewLicense}).
 *
 * <p>These pages carry two competing obligations that are easy to break in
 * opposite directions. The GPL requires that upstream copyright notices stay
 * intact, so a well-meaning "CARLOS-ify everything" edit must not strip the
 * McMaster attribution. At the same time the pages are product surfaces, so
 * they must present CARLOS as the current project rather than reading like an
 * OSCAR install. The assertions below pin both ends.
 *
 * <p>The {@code build_info} assertion additionally pins a selector contract:
 * {@code scripts/rx-fax-*-playwright-checks.js} read {@code .build_info} from
 * the About page to prove the deployed WAR reports a real version instead of an
 * unresolved Maven placeholder.
 *
 * @since 2026-09-18
 */
@DisplayName("About and Licence notice regression tests")
@Tag("unit")
class AboutAndLicenceNoticeRegressionTest {

    private static final Path ABOUT_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/encounter/About.jsp");
    private static final Path LICENCE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/encounter/License.jsp");
    private static final Path NOTICE_MD = Path.of("NOTICE.md");
    private static final Path ABOUT_LAYOUT_IMAGES =
            Path.of("src/main/webapp/images/about_layout");

    @Test
    @DisplayName("About page should expose the build stamp selector the browser checks read")
    void shouldExposeBuildStampSelector_forAboutPage() throws IOException {
        String jsp = Files.readString(ABOUT_JSP);

        assertThat(jsp)
                .as("scripts/rx-fax-*-playwright-checks.js locate the build stamp by .build_info")
                .contains("class=\"build_info")
                .contains("CarlosProperties.getBuildDate()")
                .contains("CarlosProperties.getBuildTag()")
                .as("build stamp must go through the CARLOS null-safe encoder")
                .contains("SafeEncode.forHtmlContent(CarlosProperties.getBuildDate())")
                .contains("SafeEncode.forHtmlContent(CarlosProperties.getBuildTag())");
    }

    @Test
    @DisplayName("About page should present CARLOS as the current project")
    void shouldPresentCarlosAsCurrentProject_forAboutPage() throws IOException {
        String jsp = Files.readString(ABOUT_JSP);

        assertThat(jsp)
                .contains("CARLOS EMR")
                .contains("https://github.com/carlos-emr/carlos")
                .contains("NOTICE.md")
                .as("a released build must not send readers to unreleased branch docs")
                .doesNotContain("blob/develop/")
                .as("the OSCAR-era chrome was removed with the images it referenced")
                .doesNotContain("about_layout");
    }

    @Test
    @DisplayName("About page should preserve upstream attribution and the no-affiliation disclaimer")
    void shouldPreserveUpstreamAttribution_forAboutPage() throws IOException {
        String jsp = Files.readString(ABOUT_JSP);

        assertThat(jsp)
                .as("GPL attribution for the heritage holders must not be dropped")
                .contains("McMaster University")
                .contains("St. Michael's Hospital")
                .contains("OpenOSP")
                .as("the trademark and no-affiliation statements must stay on the page")
                .contains("official mark of McMaster University")
                .contains("no organizational affiliation");
    }

    @Test
    @DisplayName("Licence page should state the CARLOS licence before the verbatim upstream notice")
    void shouldStateCarlosLicenceFirst_forLicencePage() throws IOException {
        String jsp = Files.readString(LICENCE_JSP);

        int carlosStatement = jsp.indexOf("CARLOS EMR is free software");
        int upstreamNotice = jsp.indexOf("Copyright (c) 2001-2015. Department of Family Medicine");

        assertThat(carlosStatement).as("CARLOS licence statement is present").isNotNegative();
        assertThat(upstreamNotice).as("upstream notice is still reproduced").isNotNegative();
        assertThat(carlosStatement)
                .as("the CARLOS statement should lead, with the upstream notice below it")
                .isLessThan(upstreamNotice);
    }

    @Test
    @DisplayName("Licence page should keep the upstream GPL notice verbatim")
    void shouldKeepUpstreamNoticeVerbatim_forLicencePage() throws IOException {
        String jsp = Files.readString(LICENCE_JSP);

        assertThat(jsp)
                .as("GPL section 1 requires the original notice to travel with the software")
                .contains("Department of Family Medicine, McMaster University. All Rights Reserved.")
                .contains("either version 2")
                .contains("This software was written for the")
                .as("the page also points at the wider attribution record")
                .contains("OpenOSP")
                .contains("NOTICE.md")
                .as("a released build must not send readers to unreleased branch docs")
                .doesNotContain("blob/develop/");
    }

    @Test
    @DisplayName("NOTICE should record the OpenO EMR fork among the attributed contributors")
    void shouldRecordOpenoFork_forNoticeAttribution() throws IOException {
        String notice = Files.readString(NOTICE_MD);

        assertThat(notice)
                .contains("OpenOSP and the OpenO EMR contributors")
                .as("attribution is not endorsement, and NOTICE must say so")
                .contains("does not imply any endorsement")
                .as("NOTICE points at the in-app summaries so they are kept in step")
                .contains("encounter/About.jsp")
                .contains("encounter/License.jsp");
    }

    @Test
    @DisplayName("Unreferenced OSCAR-era About artwork should not ship in the WAR")
    void shouldNotShipUnreferencedArtwork_forAboutLayout() {
        assertThat(Files.exists(ABOUT_LAYOUT_IMAGES))
                .as("%s held only the retired OSCAR-branded About chrome", ABOUT_LAYOUT_IMAGES)
                .isFalse();
    }
}
