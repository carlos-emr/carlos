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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class EmailFooterAuditSnapshotUnitTest {
    @Test
    void shouldDisplaySavedFooterAfterClinicChanges_andPreserveLegacyAbsenceAndOutcome() {
        var security = mock(SecurityInfoManager.class);
        var logs = mock(EmailLogDaoImpl.class);
        var currentClinic = mock(ClinicEmailFooterService.class);
        var user = new LoggedInInfo();
        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, null)).thenReturn(true);
        var manager = new EmailManager(mock(EmailConsentResolver.class), new EmailSenderFactory(), security,
                mock(OutboundEmailArchiveService.class));
        ReflectionTestUtils.setField(manager, "emailLogDao", logs);
        ReflectionTestUtils.setField(manager, "clinicFooterService", currentClinic);
        var saved = log(41, EmailLog.EmailStatus.FAILED);
        saved.setFooter("<div>Personal</div><br><div><b>Old Clinic</b></div>");
        var legacy = log(42, EmailLog.EmailStatus.PENDING);
        legacy.setFooter(null);
        when(logs.getEmailStatusByDateDemographicSenderStatus(any(), any(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(saved, legacy));
        var result = manager.getEmailStatusByDateDemographicSenderStatus(user, "2026-10-09", "2026-10-09",
                null, null, null);
        var old = result.stream().filter(row -> row.getLogId().equals(41)).findFirst().orElseThrow();
        var absent = result.stream().filter(row -> row.getLogId().equals(42)).findFirst().orElseThrow();
        assertThat(old.getSentFooterText()).isEqualTo("Personal\n\nOld Clinic");
        assertThat(old.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
        assertThat(absent.getSentFooterText()).isEmpty();
        assertThat(absent.getStatus()).isEqualTo(EmailLog.EmailStatus.PENDING);
        verifyNoInteractions(currentClinic);
        assertThat(saved.getFooter()).contains("Old Clinic");
    }

    @Test
    void shouldRequireAuditReadPrivilege_beforeLookingUpHistory() {
        var logs = mock(EmailLogDaoImpl.class);
        var manager = new EmailManager(mock(EmailConsentResolver.class), new EmailSenderFactory(),
                mock(SecurityInfoManager.class), mock(OutboundEmailArchiveService.class));
        ReflectionTestUtils.setField(manager, "emailLogDao", logs);
        assertThatThrownBy(() -> manager.getEmailStatusByDateDemographicSenderStatus(new LoggedInInfo(),
                "2026-10-09", "2026-10-09", null, null, null)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(logs);
    }

    @Test
    void shouldStartFooterDetailsCollapsed_andEscapePersistedText() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/emailStatusResults.jspf"));
        var detail = Jsoup.parse(jsp).selectFirst("details[id^=sentFooters]");
        assertThat(detail).isNotNull();
        assertThat(detail.hasAttr("open")).isFalse();
        assertThat(jsp).contains("carlos:forHtml(emailStatusResult.sentFooterText)", "email.audit.footer.none",
                "emailStatusResult.status");
        assertThat(jsp).doesNotContain("carlos:forHtmlContent(emailStatusResult.sentFooterText)");
    }

    private static EmailLog log(int id, EmailLog.EmailStatus status) {
        var log = new EmailLog();
        ReflectionTestUtils.setField(log, "id", id);
        log.setSubject("FAKE audit email");
        log.setToEmail(new String[]{"fake@example.test"});
        log.setTimestamp(new Date(id));
        log.setStatus(status);
        log.setIsEncrypted(false);
        log.setTransactionType(EmailLog.TransactionType.EFORM);
        return log;
    }
}
