// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.prevention.reports;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionReportDisplay;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Characterizes the release's screening report rules, not current clinical guidelines. */
@Tag("integration")
class ScreeningReportIntegrationTest extends CarlosTestBase {
    private final LoggedInInfo info = mock(LoggedInInfo.class);

    @ParameterizedTest
    @CsvSource({
        "MAM,2025-09-01,0,normal,Up to date,green,Y,----",
        "PAP,2025-09-01,0,normal,Up to date,green,Y,----",
        "MAM,2024-09-19,0,normal,Up to date,green,Y,----",
        "PAP,2023-09-19,0,normal,Up to date,green,Y,----",
        "MAM,2024-08-01,0,normal,due,yellow,Y,L1",
        "PAP,2023-08-01,0,normal,due,yellow,Y,L1",
        "MAM,2020-01-01,0,normal,Overdue,red,N,L1",
        "PAP,2020-01-01,0,normal,Overdue,red,N,L1",
        "MAM,2025-09-01,1,normal,Refused,orange,N,----",
        "PAP,2025-09-01,1,normal,Refused,orange,N,----",
        "MAM,2025-09-01,2,normal,Ineligible,grey,N,----",
        "PAP,2025-09-01,2,normal,Ineligible,grey,N,----",
        "MAM,2025-09-01,0,pending,Pending,pink,N,Follow Up",
        "MAM,2027-01-01,0,normal,No Info,Magenta,N,L1",
        "PAP,2027-01-01,0,normal,No Info,Magenta,N,L1"
    })
    void shouldClassifyScreening_whenHistoryHasKnownState(String type, String date, String refused,
            String result, String state, String color, String bonus, String next) throws Exception {
        Hashtable report = report(type, new ArrayList<>(List.of(history(date, refused))), result);
        List<?> rows = (List<?>) report.get("returnReport");
        assertThat(rows).hasSize(1);
        PreventionReportDisplay row = (PreventionReportDisplay) rows.get(0);
        assertThat(row.demographicNo).isEqualTo(770001);
        assertThat(row.state).isEqualTo(state);
        assertThat(row.color).isEqualTo(color);
        assertThat(row.bonusStatus).isEqualTo(bonus);
        assertThat(row.nextSuggestedProcedure).isEqualTo(next);
        assertThat(report)
                .containsEntry("up2date", bonus.equals("Y") ? "1" : "0")
                .containsEntry("inEligible", state.equals("Ineligible") ? "1" : "0")
                .containsEntry("percent", bonus.equals("Y") ? "100" : "0");
    }

    @ParameterizedTest
    @CsvSource({"MAM,Mam,MAMF,Q002A", "PAP,Pap,PAPF,Q001A"})
    void shouldOfferInitialFollowup_whenPatientHasNoHistory(String type, String search, String followup, String bill) throws Exception {
        Hashtable report = report(type, new ArrayList<>(), "");
        PreventionReportDisplay row = (PreventionReportDisplay) ((List<?>) report.get("returnReport")).get(0);
        assertThat(row.state).isEqualTo("No Info");
        assertThat(row.nextSuggestedProcedure).isEqualTo("L1");
        assertThat(report)
                .containsEntry("eformSearch", search)
                .containsEntry("followUpType", followup)
                .containsEntry("BillCode", bill);
    }

    @ParameterizedTest
    @CsvSource({
        "Flu,2026-10-14,Up to date,Y", "Flu,2026-10-15,Up to date,Y", "Flu,2026-10-16,No Info,N",
        "MAM,2026-10-14,Up to date,Y", "MAM,2026-10-15,Up to date,Y", "MAM,2026-10-16,No Info,N",
        "PAP,2026-10-14,Up to date,Y", "PAP,2026-10-15,Up to date,Y", "PAP,2026-10-16,No Info,N",
        "FOBT,2026-10-14,Up to date,Y", "FOBT,2026-10-15,Up to date,Y", "FOBT,2026-10-16,No Info,N"
    })
    void shouldIncludeAsOfDayInStateAndTotals_whenPreventionIsOnDateBoundary(
            String type, String date, String state, String bonus) throws Exception {
        Hashtable report = report(type, new ArrayList<>(List.of(history(date, "0"))), "normal", "2026-10-15");
        PreventionReportDisplay row = (PreventionReportDisplay) ((List<?>) report.get("returnReport")).get(0);
        assertThat(row.state).isEqualTo(state);
        assertThat(row.bonusStatus).isEqualTo(bonus);
        assertThat(report).containsEntry("up2date", bonus.equals("Y") ? "1" : "0");
    }

    private Map<String, Object> history(String date, String refused) {
        return Map.of("id", "880001", "prevention_date", date, "refused", refused);
    }

    private Hashtable report(String type, ArrayList<Map<String, Object>> history, String result) throws Exception {
        return report(type, history, result, "2026-09-19");
    }

    private Hashtable report(String type, ArrayList<Map<String, Object>> history, String result, String asOf) throws Exception {
        Demographic demographic = new Demographic();
        demographic.setYearOfBirth("1940");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("01");
        try (MockedStatic<PreventionData> data = mockStatic(PreventionData.class);
             var _ = mockConstruction(DemographicData.class,
                     (mock, context) -> when(mock.getDemographic(info, "770001")).thenReturn(demographic));
             MockedConstruction<EctMeasurementsDataBeanHandler> measurements = mockConstruction(
                     EctMeasurementsDataBeanHandler.class,
                     (mock, context) -> when(mock.getMeasurementsDataVector()).thenReturn(List.of()))) {
            data.when(() -> PreventionData.getPreventionData(info, type, 770001)).thenReturn(history);
            data.when(() -> PreventionData.getExtValue("880001", "result")).thenReturn(result);
            data.when(() -> PreventionData.getPreventionData(info, "COLONOSCOPY", 770001)).thenReturn(new ArrayList<>());
            PreventionReport report = switch (type) {
                case "MAM" -> new MammogramReport();
                case "PAP" -> new PapReport();
                case "Flu" -> new FluReport();
                case "FOBT" -> new FOBTReport();
                default -> throw new IllegalArgumentException(type);
            };
            Hashtable output = report.runReport(info, new ArrayList<>(List.of(new ArrayList<>(List.of("770001")))),
                    new SimpleDateFormat("yyyy-MM-dd").parse(asOf));
            data.verify(() -> PreventionData.getPreventionData(info, type, 770001));
            return output;
        }
    }
}
