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
package io.github.carlos_emr.carlos.email.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for security and consent-audit details rendered into email chart notes.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("EmailNoteUtil")
class EmailNoteUtilUnitTest {

    @Test
    @DisplayName("should keep the PDF password out of an encrypted email chart note")
    void shouldNotIncludePassword_whenEncryptedNoteCreated() {
        EmailLog emailLog = emailLog();
        emailLog.setIsEncrypted(true);
        emailLog.setIsAttachmentEncrypted(true);
        emailLog.setPassword("unique-pdf-password");
        emailLog.setPasswordClue("Use the separately provided clue");
        emailLog.setEncryptedMessage("Protected message contents");

        String note = noteUtil(emailLog).createNote();

        assertThat(note)
                .contains("Use the separately provided clue", "Attached Message (message.pdf)")
                .doesNotContain("unique-pdf-password", "with password");
    }

    @Test
    @DisplayName("should include the consent snapshot and override reason in a chart note")
    void shouldIncludeConsentSnapshotAndOverrideReason_inChartNote() {
        EmailLog emailLog = emailLog();
        emailLog.setConsentStatus(EmailConsentStatus.UNKNOWN);
        emailLog.setConsentId(55);
        emailLog.setConsentOverride(true);
        emailLog.setConsentOverrideReason("Provider confirmed verbal consent");

        String note = noteUtil(emailLog).createNote();

        assertThat(note).contains(
                "Consent: Unknown (consent #55); override reason: "
                        + "Provider confirmed verbal consent");
    }

    @Test
    @DisplayName("should identify a legacy chart note when consent was not recorded")
    void shouldIdentifyConsentAsNotRecorded_whenSnapshotIsAbsent() {
        assertThat(noteUtil(emailLog()).createNote()).contains("Consent: Not recorded");
    }

    private EmailLog emailLog() {
        EmailLog emailLog = new EmailLog();
        emailLog.setSubject("Subject");
        emailLog.setBody("Body");
        emailLog.setFromEmail("clinic@example.org");
        emailLog.setToEmail(new String[] {"patient@example.org"});
        emailLog.setTimestamp(new Date());
        emailLog.setIsEncrypted(false);
        emailLog.setEmailAttachments(Collections.emptyList());
        emailLog.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        return emailLog;
    }

    private EmailNoteUtil noteUtil(EmailLog emailLog) {
        // CALLS_REAL_METHODS avoids this legacy utility's eager Spring lookups while exercising
        // its public note-rendering path. Objenesis bypasses field initializers, so seed the two
        // date patterns along with the constructor state.
        EmailNoteUtil noteUtil = mock(EmailNoteUtil.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(noteUtil, "emailLog", emailLog);
        ReflectionTestUtils.setField(noteUtil, "loggedInInfo", new LoggedInInfo());
        ReflectionTestUtils.setField(noteUtil, "DATE_FORMAT", "yyyy.MM.dd");
        ReflectionTestUtils.setField(noteUtil, "TIME_FORMAT", "hh:mm a");
        return noteUtil;
    }
}
