/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.util;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for consent audit details rendered into email chart notes.
 *
 * @since 2026-09-11
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailNoteUtil consent audit details")
class EmailNoteUtilUnitTest {

    @Test
    @DisplayName("should include the consent snapshot and override reason in a chart note")
    void shouldIncludeConsentSnapshotAndOverrideReason_inChartNote() {
        EmailLog emailLog = emailLog();
        emailLog.setConsentStatus(EmailConsentStatus.UNKNOWN);
        emailLog.setConsentId(55);
        emailLog.setConsentOverride(true);
        emailLog.setConsentOverrideReason("Provider confirmed verbal consent");
        EmailNoteUtil noteUtil = noteUtil(emailLog);

        String note = noteUtil.createNote();

        assertThat(note).contains(
                "Consent: Unknown (consent #55); override reason: "
                        + "Provider confirmed verbal consent");
    }

    @Test
    @DisplayName("should identify a legacy chart note when consent was not recorded")
    void shouldIdentifyConsentAsNotRecorded_whenSnapshotIsAbsent() {
        EmailNoteUtil noteUtil = noteUtil(emailLog());

        assertThat(noteUtil.createNote()).contains("Consent: Not recorded");
    }

    private EmailLog emailLog() {
        EmailLog emailLog = new EmailLog();
        emailLog.setSubject("Subject");
        emailLog.setBody("Body");
        emailLog.setFromEmail("clinic@example.org");
        emailLog.setToEmail(new String[]{"patient@example.org"});
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
