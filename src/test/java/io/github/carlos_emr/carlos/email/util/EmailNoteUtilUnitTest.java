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
import static org.mockito.Mockito.mock;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderExtDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@DisplayName("EmailNoteUtil")
@Tag("unit")
@Tag("fast")
@Tag("security")
class EmailNoteUtilUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerDependencies() {
        createAndRegisterMock(EformDataManager.class);
        createAndRegisterMock(FormsManager.class);
        createAndRegisterMock(ProviderExtDao.class);
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
    }

    @Test
    @Tag("read")
    @DisplayName("should keep the PDF password out of an encrypted email chart note")
    void shouldNotIncludePassword_whenEncryptedNoteCreated() {
        EmailLog emailLog = new EmailLog(null, "clinic@example.com",
                new String[] {"patient@example.com"}, "Subject", "Body", EmailStatus.SUCCESS);
        emailLog.setIsEncrypted(true);
        emailLog.setIsAttachmentEncrypted(true);
        emailLog.setPassword("unique-pdf-password");
        emailLog.setPasswordClue("Use the separately provided clue");
        emailLog.setEncryptedMessage("Protected message contents");
        emailLog.setEmailAttachments(Collections.emptyList());
        emailLog.setChartDisplayOption(ChartDisplayOption.WITH_FULL_NOTE);
        emailLog.setInternalComment("");

        String note = new EmailNoteUtil(mock(LoggedInInfo.class), emailLog).createNote();

        assertThat(note)
                .contains("Use the separately provided clue", "Attached Message (message.pdf)")
                .doesNotContain("unique-pdf-password", "with password");
    }
}
