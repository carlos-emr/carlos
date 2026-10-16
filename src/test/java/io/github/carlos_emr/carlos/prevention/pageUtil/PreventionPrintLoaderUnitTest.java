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
package io.github.carlos_emr.carlos.prevention.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.prevention.PreventionPageData;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Entry;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Printout;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Section;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@Tag("fast")
@Tag("prevention")
@DisplayName("PreventionPrintLoader reads the printout from the chart")
class PreventionPrintLoaderUnitTest {

    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private final PreventionPageData pageData = mock(PreventionPageData.class);
    private final List<Map<String, String>> preventionTypes = new ArrayList<>();
    private final Demographic patient = new Demographic();
    private final Map<String, Map<String, String>> extensions = new HashMap<>();
    private final AtomicReference<String> requestedPatient = new AtomicReference<>();
    private boolean mrpModel = true;
    private boolean showComments = true;
    private PreventionPrintLoader loader;

    @BeforeEach
    void setUp() {
        patient.setDemographicNo(42);
        patient.setProviderNo("101");
        when(pageData.getDemographic()).thenReturn(patient);
        when(pageData.getPreventionData(anyString())).thenReturn(new ArrayList<>());
        preventionTypes.addAll(List.of(
                type("PAP", "Screenings", "PAPMAM"),
                type("Inf", "Immunizations", "injection"),
                type("Smoking", "Screenings", "history"),
                type("Td", "Immunizations", "injection"),
                type("OtherA", "Other", "injection"),
                type("Td", "Immunizations", "injection")));
        Function<String, Map<String, String>> extensionReader = id -> extensions.getOrDefault(id, new HashMap<>());
        loader = new PreventionPrintLoader(() -> preventionTypes, (info, demographicNo) -> {
            requestedPatient.set(demographicNo);
            return pageData;
        }, extensionReader, providerNo -> "101".equals(providerNo) ? " Jane Carlos " : "", () -> mrpModel,
                () -> showComments);
    }

    @Test
    @DisplayName("should print every immunization before every screening, in configuration order")
    void shouldOrderSections_byConfigurationWithImmunizationsFirst() {
        records("PAP", row("1", "2024-05-05", "44 years", "0", "Dr A"));
        records("Smoking", row("2", "2023-01-01", "43 years", "1", "Dr A"));
        records("Td", row("3", "2020-01-01", "40 years", "0", "Dr B"));
        records("Inf", row("4", "2025-10-01", "45 years", "0", "Dr C"));
        records("OtherA", row("5", "2019-02-02", "39 years", "0", "Dr D"));

        Printout printout = loader.load(loggedInInfo, "42", new String[]{"Smoking", "Td", "PAP", "OtherA", "Inf"}, false);

        assertThat(requestedPatient.get()).isEqualTo("42");
        assertThat(printout.patient()).isSameAs(patient);
        assertThat(printout.sections()).extracting(Section::preventionType)
                .containsExactly("Inf", "Td", "OtherA", "PAP", "Smoking");
        assertThat(printout.sections()).extracting(Section::screening)
                .containsExactly(false, false, false, true, true);
        assertThat(printout.sections()).extracting(Section::historyLayout)
                .containsExactly(false, false, false, false, true);
    }

    @Test
    @DisplayName("should leave screenings out when only immunizations are asked for")
    void shouldOmitScreenings_whenImmunizationsOnly() {
        records("PAP", row("1", "2024-05-05", "44 years", "0", "Dr A"));
        records("Inf", row("4", "2025-10-01", "45 years", "0", "Dr C"));

        Printout printout = loader.load(loggedInInfo, "42", new String[]{"PAP", "Inf"}, true);

        assertThat(printout.immunizationsOnly()).isTrue();
        assertThat(printout.sections()).extracting(Section::preventionType).containsExactly("Inf");
        verify(pageData, never()).getPreventionData("PAP");
    }

    @Test
    @DisplayName("should read every printed value from the chart record and its extensions")
    void shouldReadEntries_fromChartRecords() {
        records("Inf", row("4", "2025-10-01", "45 years", "3", "Dr C"), row("7", "2024-10-02", "44 years", "1", null));
        extensions.put("4", Map.of("comments", "Reviewed with the patient; find attached the CBC.",
                "result", "normal", "reason", "seasonal", "location", "left deltoid", "manufacture", "Sanofi",
                "name", "FLUZONE", "lot", "L123", "dose", "0.5 mL"));

        Section section = loader.load(loggedInInfo, "42", new String[]{"Inf"}, false).sections().get(0);

        assertThat(section.entries()).containsExactly(
                new Entry("2025-10-01", "45 years", "3", "normal", "seasonal",
                        "Reviewed with the patient; find attached the CBC.", "left deltoid", "Sanofi", "FLUZONE",
                        "L123", "0.5 mL", "Dr C"),
                new Entry("2024-10-02", "44 years", "1", null, null, null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("should leave comments off the printout when prevention_show_comments is off")
    void shouldOmitComments_whenShowCommentsOff() {
        records("Inf", row("4", "2025-10-01", "45 years", "0", "Dr C"));
        extensions.put("4", Map.of("comments", "Reviewed with the patient.", "lot", "L123"));
        showComments = false;

        Entry entry = loader.load(loggedInInfo, "42", new String[]{"Inf"}, false).sections().get(0).entries().get(0);

        assertThat(entry.comments()).isNull();
        assertThat(entry.lot()).isEqualTo("L123");
    }

    @Test
    @DisplayName("should ignore names that are not configured types and types without records")
    void shouldIgnoreUnknownAndEmptyTypes_forTamperedSelection() {
        records("Inf", row("4", "2025-10-01", "45 years", "0", "Dr C"));

        Printout printout = loader.load(loggedInInfo, "42",
                new String[]{"Inf", "Td", "<script>", "Inf", "preventProcedureComments34-0"}, false);

        assertThat(printout.sections()).extracting(Section::preventionType).containsExactly("Inf");
        verify(pageData, never()).getPreventionData("<script>");
    }

    @Test
    @DisplayName("should return no sections when nothing is selected")
    void shouldReturnNoSections_whenNothingSelected() {
        records("Inf", row("4", "2025-10-01", "45 years", "0", "Dr C"));

        assertThat(loader.load(loggedInInfo, "42", null, false).sections()).isEmpty();
        assertThat(loader.load(loggedInInfo, "42", new String[0], false).sections()).isEmpty();
    }

    @Test
    @DisplayName("should name the patient's own MRP when the clinic uses the MRP model")
    void shouldNamePatientMrp_whenMrpModelEnabled() {
        assertThat(loader.load(loggedInInfo, "42", null, false).mrpName()).isEqualTo("Jane Carlos");

        patient.setProviderNo("202");
        assertThat(loader.load(loggedInInfo, "42", null, false).mrpName()).isEqualTo("unknown");

        patient.setProviderNo(" ");
        assertThat(loader.load(loggedInInfo, "42", null, false).mrpName()).isNull();

        patient.setProviderNo("101");
        mrpModel = false;
        assertThat(loader.load(loggedInInfo, "42", null, false).mrpName()).isNull();
    }

    @Test
    @DisplayName("should refuse a demographic number with no patient")
    void shouldThrow_whenPatientMissing() {
        when(pageData.getDemographic()).thenReturn(null);

        assertThatThrownBy(() -> loader.load(loggedInInfo, "42", new String[]{"Inf"}, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patient not found");
    }

    @SafeVarargs
    private void records(String type, Map<String, Object>... rows) {
        when(pageData.getPreventionData(type)).thenReturn(new ArrayList<>(List.of(rows)));
    }

    private static Map<String, Object> row(String id, String date, String age, String status, String provider) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("prevention_date_no_time", date);
        row.put("age", age);
        row.put("refused", status);
        row.put("provider_name", provider);
        return row;
    }

    private static HashMap<String, String> type(String name, String heading, String layout) {
        HashMap<String, String> type = new HashMap<>();
        type.put("name", name);
        type.put("headingName", heading);
        type.put("layout", layout);
        return type;
    }
}
