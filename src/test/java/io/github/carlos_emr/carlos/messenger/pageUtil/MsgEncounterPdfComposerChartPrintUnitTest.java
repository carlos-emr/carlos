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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.io.OutputStream;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

/**
 * #5856: the chart-notes item's real print. These flags decide what leaves the chart through Messenger,
 * so they are pinned here: every current note and the CPP, locked notes left out, no prescriptions, labs,
 * preventions or allergies, no date range, no note selection, and none of the request's parameters.
 */
@DisplayName("MsgEncounterPdfComposer's chart print")
@Tag("unit")
@Tag("messenger")
class MsgEncounterPdfComposerChartPrintUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 5856;

    @Test
    @DisplayName("should print all notes and the CPP only, without locked notes or request parameters")
    void shouldPrintAllNotesAndTheCppOnly_withoutLockedNotesOrRequestParameters() throws Exception {
        CdsLegacyProjectionService projections = createAndRegisterMock(CdsLegacyProjectionService.class);
        // The print's own services, in case its field initializers run under the construction mock.
        createAndRegisterMock(CaseManagementManager.class);
        createAndRegisterMock(PreventionManager.class);
        createAndRegisterMock(AllergyDao.class);
        LoggedInInfo user = mock(LoggedInInfo.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/messenger/Doc2PDF");
        request.addParameter("extPrintMeasurements", "true");
        request.addParameter("chartContext", "FAKE-forged");

        try (var prints = mockConstruction(CaseManagementPrint.class)) {
            new MsgEncounterPdfComposer().compose(request, user, PATIENT);

            verify(projections).requireSupported(PATIENT, MsgEncounterPdfComposer.PRINTED_CATEGORIES);
            assertThat(prints.constructed()).hasSize(1);
            CaseManagementPrint print = prints.constructed().getFirst();
            InOrder order = inOrder(print);
            order.verify(print).leaveOutLockedNotes();
            ArgumentCaptor<HttpServletRequest> printed = ArgumentCaptor.forClass(HttpServletRequest.class);
            order.verify(print).doPrint(eq(user), eq(PATIENT), eq(true), argThat(ids -> ids.length == 0),
                    eq(true), eq(false), eq(false), eq(false), eq(false), eq(false), isNull(), isNull(),
                    printed.capture(), any(OutputStream.class));
            assertThat(printed.getValue().getParameterMap()).isEmpty();
            assertThat(printed.getValue().getParameter("extPrintMeasurements")).isNull();
            assertThat(printed.getValue().getSession()).isSameAs(request.getSession());
        }
    }
}
