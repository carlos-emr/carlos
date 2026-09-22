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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A portal invitation's body carried a one-time code that activates a patient's portal account. The
 * email viewer must not hand that credential back to anyone who may read the patient's email history.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
class EmailComposePortalInviteUnitTest extends CarlosUnitTestBase {

    private EmailComposeManager emailComposeManager;
    private EmailLogDaoImpl emailLogDao;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        emailLogDao = mock(EmailLogDaoImpl.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        emailComposeManager = new EmailComposeManager();
        injectDependency(emailComposeManager, "emailLogDao", emailLogDao);
        injectDependency(emailComposeManager, "securityInfoManager", securityInfoManager);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), nullable(String.class)))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("should refuse to reopen a portal invitation in the compose window")
    void shouldRefuseResend_forAPortalInvitation() {
        when(emailLogDao.find((Object) 7)).thenReturn(emailLog(TransactionType.PORTAL_INVITE));

        assertThatThrownBy(() -> emailComposeManager.prepareEmailForResend(loggedInInfo, 7))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should still reopen an ordinary patient email")
    void shouldAllowResend_forAnOrdinaryEmail() {
        when(emailLogDao.find((Object) 7)).thenReturn(emailLog(TransactionType.DIRECT));

        assertThat(emailComposeManager.prepareEmailForResend(loggedInInfo, 7)).isNotNull();
    }

    private static EmailLog emailLog(TransactionType transactionType) {
        EmailLog emailLog = new EmailLog();
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        emailLog.setDemographic(demographic);
        emailLog.setTransactionType(transactionType);
        emailLog.setBody("2. Enter this invitation code: live-code");
        return emailLog;
    }
}
