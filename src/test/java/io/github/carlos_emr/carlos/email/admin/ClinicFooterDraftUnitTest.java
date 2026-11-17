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
package io.github.carlos_emr.carlos.email.admin;

import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.email.core.EmailFooterHtml;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unsaved clinic footer text kept across a reload of Configure Email (issue #3981, Ben's demo of
 * 15 Oct 2026): what is kept, what is dropped, and that it is shown once.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("Clinic footer draft")
class ClinicFooterDraftUnitTest {

    private static final String SHOWN = ClinicEmailFooterService.fingerprint("FAKE saved footer");

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/saveClinicEmailLogo");

    @Test
    @DisplayName("should keep typed text cleaned as a save would clean it, and give it back once")
    void shouldKeepCleanedText_andGiveItBackOnce() {
        ClinicFooterDraft.keep(request, "<b>FAKE typed</b><script>bad()</script>", SHOWN, false);

        assertThat(ClinicFooterDraft.take(request)).isEqualTo(new ClinicFooterDraft("<b>FAKE typed</b>", SHOWN, false));
        assertThat(ClinicFooterDraft.take(request)).isNull();
    }

    @Test
    @DisplayName("should keep the settings chosen with the text, and nothing of them without it")
    void shouldKeepChosenSettings_onlyWithTheText() {
        ClinicFooterDraft.keep(request, "FAKE typed", SHOWN, false, ClinicEmailFooterService.Mode.OFF, false);
        assertThat(ClinicFooterDraft.take(request)).isEqualTo(new ClinicFooterDraft("FAKE typed", SHOWN, false, "off", false));

        ClinicFooterDraft.keep(request, "FAKE saved footer", SHOWN, false, ClinicEmailFooterService.Mode.OFF, false);
        assertThat(ClinicFooterDraft.take(request)).as("unchanged text keeps nothing").isNull();
    }

    @Test
    @DisplayName("should keep nothing when the text is what the page showed")
    void shouldKeepNothing_whenTextIsUnchanged() {
        ClinicFooterDraft.keep(request, "FAKE saved footer", SHOWN, false);

        assertThat(ClinicFooterDraft.take(request)).isNull();
    }

    @Test
    @DisplayName("should keep nothing without visible text, a well-formed fingerprint, or within the read limit")
    void shouldKeepNothing_forBlankMalformedOrOversizedInput() {
        ClinicFooterDraft.keep(request, "<b> </b>", SHOWN, false);
        ClinicFooterDraft.keep(request, null, SHOWN, false);
        ClinicFooterDraft.keep(request, "FAKE typed", null, false);
        ClinicFooterDraft.keep(request, "FAKE typed", "not-a-fingerprint", false);
        ClinicFooterDraft.keep(request, "FAKE typed", SHOWN.toUpperCase(java.util.Locale.ROOT), false);
        ClinicFooterDraft.keep(request, "x".repeat(4 * EmailFooterHtml.MAX_HTML_LENGTH + 1), SHOWN, false);

        assertThat(ClinicFooterDraft.take(request)).isNull();
    }

    @Test
    @DisplayName("should say the save was stale when another administrator changed the footer")
    void shouldMarkStale_whenTheSaveWasStale() {
        ClinicFooterDraft.keep(request, "FAKE typed", SHOWN, true);

        assertThat(ClinicFooterDraft.take(request).stale()).isTrue();
    }

    @Test
    @DisplayName("should find nothing, and create no session, when none was kept")
    void shouldFindNothing_withoutASession() {
        assertThat(ClinicFooterDraft.take(request)).isNull();
        assertThat(request.getSession(false)).isNull();
    }
}
