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

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Proves Spring wires the required security constructor and the legacy DAO dependencies together.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("email")
class EmailComposeManagerWiringUnitTest {
    @Test
    void shouldWireSecurityAndDao_whenSpringCreatesTheComposeManager() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        EmailLogDaoImpl logs = mock(EmailLogDaoImpl.class);
        LoggedInInfo info = mock(LoggedInInfo.class);
        EmailLog log = new EmailLog();
        when(logs.find(Integer.valueOf(5))).thenReturn(log);
        try (var context = new AnnotationConfigApplicationContext()) {
            var beans = context.getBeanFactory();
            beans.registerSingleton("securityInfoManager", security);
            beans.registerSingleton("emailLogDao", logs);
            beans.registerSingleton("emailConfigDao", mock(EmailConfigDaoImpl.class));
            beans.registerSingleton("userPropertyDAO", mock(UserPropertyDAO.class));
            beans.registerSingleton("demographicManager", mock(DemographicManager.class));
            beans.registerSingleton("documentAttachmentManager", mock(DocumentAttachmentManager.class));
            beans.registerSingleton("formsManager", mock(FormsManager.class));
            beans.registerSingleton("patientConsentManager", mock(PatientConsentManager.class));
            beans.registerSingleton("nioFileManager", mock(NioFileManager.class));
            context.register(EmailComposeManager.class);
            context.refresh();
            EmailComposeManager manager = context.getBean(EmailComposeManager.class);
            when(security.hasPrivilege(info, "_email", SecurityInfoManager.READ, null)).thenReturn(true);
            assertThat(manager.prepareEmailForResend(info, 5)).isSameAs(log);
            verify(logs).find(Integer.valueOf(5));
            clearInvocations(logs);
            when(security.hasPrivilege(info, "_email", SecurityInfoManager.READ, null)).thenReturn(false);
            assertThatThrownBy(() -> manager.prepareEmailForResend(info, 5)).isInstanceOf(SecurityException.class);
            verifyNoInteractions(logs);
        }
    }
}
