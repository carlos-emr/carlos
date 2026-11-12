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
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * #5856: Messenger's current-encounter item is the modern chart's own print of the patient's notes and
 * CPP, checked for imported records first and audited as a chart print. FAKE patients only.
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
    /** Each print asked for: the patient, all notes, CPP. */
    private final List<Object[]> prints = new ArrayList<>();
    private byte[] printed = PDF;
    private MockedStatic<LogAction> audit;

    private MsgEncounterPdfComposer composer;

    @BeforeEach
    void setUp() {
        audit = mockStatic(LogAction.class);
        composer = new MsgEncounterPdfComposer(projections, (login, demographicNo, allNotes, cpp, req, out) -> {
            prints.add(new Object[]{login, demographicNo, allNotes, cpp, req});
            out.write(printed);
        });
    }

    @AfterEach
    void tearDown() {
        audit.close();
    }

    @Test
    void shouldPrintEveryCurrentNoteAndTheCpp_throughTheChartPrint() throws Exception {
        byte[] pdf = composer.compose(request, user, PATIENT);

        assertThat(pdf).isEqualTo(PDF);
        assertThat(prints).hasSize(1);
        // D278, provisional option A: all notes, with the CPP.
        assertThat(prints.get(0)).containsExactly(user, PATIENT, true, true, request);
    }

    @Test
    void shouldCheckImportedRecordsBeforePrinting_forWhatThePrintShows() throws Exception {
        composer.compose(request, user, PATIENT);

        verify(projections).requireSupported(PATIENT, MsgEncounterPdfComposer.PRINTED_CATEGORIES);
        assertThat(MsgEncounterPdfComposer.PRINTED_CATEGORIES)
                .contains("ClinicalNotes", "PersonalHistory", "FamilyHistory", "PastHealth", "ProblemList",
                        "RiskFactors", "AlertsAndSpecialNeeds")
                .doesNotContain("MedicationsAndTreatments", "LaboratoryResults", "Immunizations",
                        "AllergiesAndAdverseReactions");
    }

    @Test
    void shouldAuditTheEncounterAsAChartPrintThroughMessenger_onceItIsPrinted() throws Exception {
        composer.compose(request, user, PATIENT);

        audit.verify(() -> LogAction.addLogStrict(user, "print", "echart", String.valueOf(PATIENT),
                String.valueOf(PATIENT), "messenger"), times(1));
    }

    @Test
    void shouldNeitherPrintNorAudit_whenThePrintCannotShowTheRecord() {
        doThrow(new CdsLegacyProjectionException())
                .when(projections).requireSupported(anyInt(), any());

        assertThatThrownBy(() -> composer.compose(request, user, PATIENT))
                .isInstanceOf(CdsLegacyProjectionException.class);
        assertThat(prints).isEmpty();
        audit.verify(() -> LogAction.addLogStrict(any(LoggedInInfo.class), anyString(), anyString(), anyString(),
                anyString(), anyString()), never());
    }

    @Test
    void shouldNotAudit_aPrintThatWroteNothing() throws Exception {
        printed = new byte[0];

        assertThat(composer.compose(request, user, PATIENT)).isEmpty();
        audit.verify(() -> LogAction.addLogStrict(any(LoggedInInfo.class), anyString(), anyString(), anyString(),
                anyString(), anyString()), never());
    }

    @Test
    void shouldNotHandTheEncounterBack_whenItsAuditFails() {
        audit.when(() -> LogAction.addLogStrict(any(LoggedInInfo.class), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenThrow(new IllegalStateException("FAKE audit failure"));

        // Strict, as the chart print's audit: no record of the disclosure, no PDF.
        assertThatThrownBy(() -> composer.compose(request, user, PATIENT)).isInstanceOf(IllegalStateException.class);
    }
}
