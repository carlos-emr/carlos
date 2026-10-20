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

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #4720: the subject a lab's Recall message is pre-filled with. Lab Recall Settings now saves it under
 * {@link UserProperty#LAB_RECALL_MSG_SUBJECT}; a subject saved before the fix, under the old name, is still used.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("messaging")
@DisplayName("Lab Recall message subject preference (#4720)")
class MessagingManagerLabRecallSubjectUnitTest extends CarlosUnitTestBase {

    private static final String OWNER = "999998";

    private final UserPropertyDAO properties = mock(UserPropertyDAO.class);
    private final LoggedInInfo login = mock(LoggedInInfo.class);
    private MessagingManagerImpl manager;

    @BeforeEach
    void setUp() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_msg"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        registerMock(MessageListDao.class, mock(MessageListDao.class));
        registerMock(MessageTblDao.class, mock(MessageTblDao.class));
        registerMock(OscarCommLocationsDao.class, mock(OscarCommLocationsDao.class));
        registerMock(MessengerDemographicManager.class, mock(MessengerDemographicManager.class));
        registerMock(UserPropertyDAO.class, properties);
        manager = new MessagingManagerImpl();
        injectDependency(manager, "securityInfoManager", security);
        when(login.getLoggedInProviderNo()).thenReturn(OWNER);
    }

    private static UserProperty property(String name, String value) {
        UserProperty property = new UserProperty();
        property.setProviderNo(OWNER);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    @Test
    @DisplayName("should use the subject saved by Lab Recall Settings")
    void shouldUseSavedSubject_forRecallMessage() {
        when(properties.getProp(OWNER, UserProperty.LAB_RECALL_MSG_SUBJECT))
                .thenReturn(property(UserProperty.LAB_RECALL_MSG_SUBJECT, "FAKE-PW new subject"));

        assertThat(manager.getLabRecallMsgSubjectPref(login)).isEqualTo("FAKE-PW new subject");
    }

    @Test
    @DisplayName("should still use a subject saved under the old name")
    void shouldUseLegacySubject_whenOnlyOldRowExists() {
        when(properties.getProp(OWNER, UserProperty.LAB_RECALL_MSG_SUBJECT_LEGACY))
                .thenReturn(property(UserProperty.LAB_RECALL_MSG_SUBJECT_LEGACY, "FAKE-PW old subject"));

        assertThat(manager.getLabRecallMsgSubjectPref(login)).isEqualTo("FAKE-PW old subject");
    }

    @Test
    @DisplayName("should answer an empty subject when none is saved")
    void shouldAnswerEmptySubject_whenNoneSaved() {
        assertThat(manager.getLabRecallMsgSubjectPref(login)).isEmpty();
    }
}
