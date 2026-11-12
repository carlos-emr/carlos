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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the Health Care Team display JSP (issue #4684).
 *
 * <p>The master record embeds {@code displayHealthCareTeam.jsp} with {@code <jsp:include>}, which gives the
 * page its own page scope. A {@code <fmt:setBundle>} that only runs in the {@code view=detached} branch
 * therefore leaves the embedded view without a bundle, and its heading renders as
 * {@code ???demographic.manageHealthCareTeam.heading???}.
 *
 * @since 2026-10-16
 */
@DisplayName("Display Health Care Team JSP regression tests")
@Tag("unit")
@Tag("demographic")
class DisplayHealthCareTeamJspRegressionTest {

    private static final Path DISPLAY_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/demographic/displayHealthCareTeam.jsp");
    private static final String SET_BUNDLE = "<fmt:setBundle basename=\"oscarResources\"/>";
    private static final String DETACHED_BRANCH = "<c:if test=\"${ param.view eq 'detached' }\">";
    private static final String FMT_TAGLIB = "<%@ taglib uri=\"jakarta.tags.fmt\" prefix=\"fmt\" %>";

    private static String jsp() throws IOException {
        return Files.readString(DISPLAY_JSP, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should set the message bundle exactly once")
    void shouldSetMessageBundleOnce_whenJspIsLoaded() throws IOException {
        String jsp = jsp();

        assertThat(jsp.split(java.util.regex.Pattern.quote(SET_BUNDLE), -1).length - 1)
                .as("the page needs one setBundle that serves both the embedded and the detached view")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("should set the message bundle before the detached-only branch")
    void shouldSetBundleOutsideDetachedBranch_whenEmbeddedInMasterRecord() throws IOException {
        String jsp = jsp();

        int bundle = jsp.indexOf(SET_BUNDLE);
        int firstDetachedBranch = jsp.indexOf(DETACHED_BRANCH);

        assertThat(bundle).as("fmt:setBundle must be present").isNotNegative();
        assertThat(firstDetachedBranch).as("detached branch must be present").isNotNegative();
        assertThat(bundle)
                .as("the master record embeds this page via jsp:include, so a bundle set only inside the"
                        + " detached branch leaves the heading as ???key???")
                .isLessThan(firstDetachedBranch);
    }

    @Test
    @DisplayName("should set the message bundle before the first message lookup")
    void shouldSetBundleBeforeFirstMessage_whenRendered() throws IOException {
        String jsp = jsp();

        // Presence first: indexOf returns -1 for a missing tag, which would satisfy isLessThan on its own.
        assertThat(jsp).contains(SET_BUNDLE).contains("<fmt:message");
        assertThat(jsp.indexOf(SET_BUNDLE)).isLessThan(jsp.indexOf("<fmt:message"));
    }

    @Test
    @DisplayName("should declare the fmt taglib before it is used")
    void shouldDeclareFmtTaglibBeforeUse_whenJspIsLoaded() throws IOException {
        String jsp = jsp();

        assertThat(jsp).contains(FMT_TAGLIB).contains(SET_BUNDLE);
        assertThat(jsp.indexOf(FMT_TAGLIB)).isLessThan(jsp.indexOf(SET_BUNDLE));
    }
}
