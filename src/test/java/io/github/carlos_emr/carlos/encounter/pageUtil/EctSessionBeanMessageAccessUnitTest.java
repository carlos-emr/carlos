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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.service.MessageEncounterService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Exercises the real legacy entry bean and shared message loader before clinical rendering.
 * Linked chart readers retain access; a message ID alone never authorizes its body.
 *
 * @since 2026-10-25
 */
@Tag("unit")
@Tag("security")
@Tag("encounter")
class EctSessionBeanMessageAccessUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager security;
    private MessageTblDao messages;
    private MsgDemoMapDao links;
    private DemographicManager demographics;
    private EChartDao charts;
    private EncounterTemplateDao templates;
    private MeasurementGroupStyleDao groups;
    private LoggedInInfo login;
    private EctSessionBean bean;
    private MessageTbl message;

    @BeforeEach
    void prepareRealMessageLoader() {
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("101");
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(login, "_msg", "r", (String) null)).thenReturn(true);
        when(security.hasPrivilege(login, "_msg", "r", "42")).thenReturn(true);
        when(security.hasPrivilege(login, "_eChart", "r", "42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 42)).thenReturn(true);
        messages = createAndRegisterMock(MessageTblDao.class);
        links = createAndRegisterMock(MsgDemoMapDao.class);
        registerMock(MessageEncounterService.class, new MessageEncounterService(messages, links, security));
        demographics = createAndRegisterMock(DemographicManager.class);
        charts = createAndRegisterMock(EChartDao.class);
        templates = createAndRegisterMock(EncounterTemplateDao.class);
        groups = createAndRegisterMock(MeasurementGroupStyleDao.class);
        Demographic patient = new Demographic();
        patient.setDemographicNo(42);
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("02");
        when(demographics.getDemographic(login, "42")).thenReturn(patient);
        message = new MessageTbl();
        message.setSentBy("FAKE sender");
        message.setSentTo("FAKE recipient");
        message.setDate(java.sql.Date.valueOf("2026-10-25"));
        message.setTime(java.sql.Time.valueOf("12:34:56"));
        message.setSubject("FAKE <subject> & details");
        message.setMessage("FAKE line one\nline two </script> + Unicode \u00e9\u4e2d\u6587");
        when(messages.find(7)).thenReturn(message);
        bean = new EctSessionBean();
        bean.demographicNo = "42";
        bean.oscarMsgID = "7";
        bean.oscarMsg = "FAKE stale message from another window";
        bean.encounter = "FAKE stale clinical text";
    }

    @ParameterizedTest
    @ValueSource(strings = {"101", "202"})
    void shouldLoadExactLinkedMessage_forLocalSenderOrSharedChartReader(String sender) {
        message.setSentByNo(sender);
        linkTo(42);

        bean.setUpEncounterPage(login);

        assertThat(bean.oscarMsg).isEqualTo("From: FAKE sender\nTo: FAKE recipient\nDate: 2026-10-25 12:34:56"
                + "\nSubject: FAKE <subject> & details\nFAKE line one\nline two </script> + Unicode \u00e9\u4e2d\u6587");
        assertThat(bean.demographicNo).isEqualTo("42");
        assertThat(bean.encounter).isEmpty();
        var order = inOrder(links, messages, demographics);
        order.verify(links).findByMessageId(7);
        order.verify(messages).find(7);
        order.verify(demographics).getDemographic(login, "42");
        verify(messages, never()).persist(any());
        verify(messages, never()).merge(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"message-module", "patient-message", "chart-module", "record"})
    void shouldClearOldTextWithoutClinicalLookups_whenHandoffAccessIsDenied(String boundary) {
        if ("message-module".equals(boundary)) when(security.hasPrivilege(login, "_msg", "r", (String) null)).thenReturn(false);
        if ("patient-message".equals(boundary)) {
            when(security.hasPrivilege(login, "_msg", "r", "42")).thenReturn(false);
            linkTo(42);
        }
        if ("chart-module".equals(boundary)) when(security.hasPrivilege(login, "_eChart", "r", "42")).thenReturn(false);
        if ("record".equals(boundary)) when(security.isAllowedAccessToPatientRecord(login, 42)).thenReturn(false);

        assertThatThrownBy(() -> bean.setUpEncounterPage(login)).isInstanceOf(SecurityException.class);

        assertThat(bean.oscarMsg).isEmpty();
        assertThat(bean.encounter).isEmpty();
        verifyNoInteractions(links, messages, demographics, charts, templates, groups);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 43})
    void shouldRefuseEvenTheSendersMessage_whenItIsNotLinkedToTheSelectedPatient(int linkedPatient) {
        message.setSentByNo("101");
        if (linkedPatient > 0) linkTo(linkedPatient);

        assertThatThrownBy(() -> bean.setUpEncounterPage(login)).isInstanceOf(SecurityException.class)
                .hasMessage("Message is not linked to this patient");

        assertThat(bean.oscarMsg).isEmpty();
        assertThat(bean.encounter).isEmpty();
        verifyNoInteractions(messages, demographics, charts, templates, groups);
    }

    @Test
    void shouldClearOldMessageAndRefuse_whenTheLinkedMessageDisappears() {
        linkTo(42);
        when(messages.find(7)).thenReturn(null);

        assertThatThrownBy(() -> bean.setUpEncounterPage(login)).isInstanceOf(MessageEncounterService.MissingMessageException.class)
                .hasMessage("Message no longer exists");

        assertThat(bean.oscarMsg).isEmpty();
        assertThat(bean.encounter).isEmpty();
        verifyNoInteractions(demographics, charts, templates, groups);
        verify(messages).find(7);
        verifyNoMoreInteractions(messages);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldClearOldMessageWithoutLoadingAnother_whenNoHandoffIsRequested(String messageId) {
        bean.oscarMsgID = messageId;

        bean.setUpEncounterPage(login);

        assertThat(bean.oscarMsg).isEmpty();
        verifyNoInteractions(links, messages);
    }

    private void linkTo(int patient) {
        MsgDemoMap link = new MsgDemoMap();
        link.setMessageID(7);
        link.setDemographic_no(patient);
        when(links.findByMessageId(7)).thenReturn(List.of(link));
    }
}
