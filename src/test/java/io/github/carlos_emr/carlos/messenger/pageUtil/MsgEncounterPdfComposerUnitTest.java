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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * #5856: Messenger's chart-notes item is the modern chart's own print of the patient's notes and CPP,
 * checked for imported records first; attaching it is audited as a chart print. FAKE patients only.
 */
@DisplayName("MsgEncounterPdfComposer")
@Tag("unit")
@Tag("messenger")
class MsgEncounterPdfComposerUnitTest {

    private static final int PATIENT = 5856;
    private static final byte[] PDF = "%PDF-1.4 FAKE chart print".getBytes(StandardCharsets.UTF_8);

    private final CdsLegacyProjectionService projections = mock(CdsLegacyProjectionService.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/messenger/Doc2PDF");
    /** Each print asked for: the user, the patient, all notes, CPP, the request. */
    private final List<Object[]> prints = new ArrayList<>();
    private byte[] printed = PDF;

    private MsgEncounterPdfComposer composer;

    @BeforeEach
    void setUp() {
        composer = new MsgEncounterPdfComposer(projections, (login, demographicNo, allNotes, cpp, req, out) -> {
            prints.add(new Object[]{login, demographicNo, allNotes, cpp, req});
            out.write(printed);
        });
    }

    @Test
    @DisplayName("should print every current note and the CPP through the chart print")
    void shouldPrintEveryCurrentNoteAndTheCpp_throughTheChartPrint() throws Exception {
        byte[] pdf = composer.compose(request, user, PATIENT);

        assertThat(pdf).isEqualTo(PDF);
        assertThat(prints).hasSize(1);
        // The choice decided in #5856: all notes, with the CPP.
        assertThat(prints.get(0)).containsExactly(user, PATIENT, true, true, request);
    }

    @Test
    @DisplayName("should check imported records for exactly what the print shows, before printing")
    void shouldCheckImportedRecordsBeforePrinting_forWhatThePrintShows() throws Exception {
        composer.compose(request, user, PATIENT);

        verify(projections).requireSupported(PATIENT, MsgEncounterPdfComposer.PRINTED_CATEGORIES);
        assertThat(MsgEncounterPdfComposer.PRINTED_CATEGORIES)
                .containsExactlyInAnyOrder("ClinicalNotes", "PersonalHistory", "FamilyHistory", "PastHealth",
                        "ProblemList", "RiskFactors", "AlertsAndSpecialNeeds");
    }

    @Test
    @DisplayName("should print nothing for a record the print cannot show in full")
    void shouldPrintNothing_whenThePrintCannotShowTheRecord() {
        doThrow(new CdsLegacyProjectionException()).when(projections).requireSupported(anyInt(), any());

        assertThatThrownBy(() -> composer.compose(request, user, PATIENT))
                .isInstanceOf(CdsLegacyProjectionException.class);
        assertThat(prints).isEmpty();
    }

    @Test
    @DisplayName("should hand back an empty PDF when the print wrote nothing")
    void shouldHandBackNothing_forAPrintThatWroteNothing() throws Exception {
        printed = new byte[0];

        assertThat(composer.compose(request, user, PATIENT)).isEmpty();
    }

    @Test
    @DisplayName("should audit no print by itself; attaching records a chart print sent through Messenger")
    void shouldRecordAnAttachment_asAChartPrintThroughMessenger() throws Exception {
        try (var audit = mockStatic(LogAction.class)) {
            composer.compose(request, user, PATIENT);
            audit.verifyNoInteractions();

            MsgEncounterPdfComposer.recordAttached(user, PATIENT);

            audit.verify(() -> LogAction.addLogStrict(user, "print", "echart", String.valueOf(PATIENT),
                    String.valueOf(PATIENT), "messenger"), times(1));
        }
    }

    @Test
    @DisplayName("should record a preview as a chart print shown through Messenger Preview")
    void shouldRecordAPreview_asAChartPrintThroughMessengerPreview() {
        try (var audit = mockStatic(LogAction.class)) {
            MsgEncounterPdfComposer.recordPreviewed(user, PATIENT);

            audit.verify(() -> LogAction.addLogStrict(user, "print", "echart", String.valueOf(PATIENT),
                    String.valueOf(PATIENT), "messenger-preview"), times(1));
        }
    }

    @Test
    @DisplayName("should let the print read none of the Messenger request's parameters")
    void shouldHideEveryRequestParameter_fromThePrint() {
        request.addParameter("extPrintMeasurements", "true");
        request.addParameter("chartContext", "FAKE-forged");
        request.addParameter("demographic_no", "999");
        request.setQueryString("extPrintMeasurements=true");
        request.setAttribute("FAKE-attribute", "kept");

        var seen = new MsgEncounterPdfComposer.ParameterlessRequest(request);

        assertThat(seen.getParameter("extPrintMeasurements")).isNull();
        assertThat(seen.getParameterValues("chartContext")).isNull();
        assertThat(seen.getParameterMap()).isEmpty();
        assertThat(Collections.list(seen.getParameterNames())).isEmpty();
        assertThat(seen.getQueryString()).isNull();
        assertThat(seen.getSession()).as("the login's session is kept").isSameAs(request.getSession());
        assertThat(seen.getAttribute("FAKE-attribute")).isEqualTo("kept");
    }
}
