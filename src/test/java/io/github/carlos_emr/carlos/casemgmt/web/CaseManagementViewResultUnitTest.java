/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The new chart's {@code ajaxview} parameter selects a Struts result. It used to be returned
 * verbatim, so an unexpected value reached an unmapped result (a ConfigurationException answered
 * as a 500) or another of the action's views (#6333). Only the partial the chart asks for is
 * accepted now; every result this method can return must be mapped for the action.
 */
@Tag("unit")
class CaseManagementViewResultUnitTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"null", "NULL"})
    void shouldReturnFullChartPage_whenNoPartialIsRequested(String requested) {
        assertThat(CaseManagementView2Action.newCaseManagementViewResult(requested)).isEqualTo("page.newcasemgmt.view");
    }

    @Test
    void shouldReturnNotesPartial_forTheChartsOwnAjaxViewRequest() {
        assertThat(CaseManagementView2Action.newCaseManagementViewResult("ajaxView")).isEqualTo("ajaxView");
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "clientHistoryPrintPreview", "listNotes", "AJAXVIEW", "ajaxView ", "x"})
    void shouldRefuseResult_forAnyOtherRequestedView(String requested) {
        assertThat(CaseManagementView2Action.newCaseManagementViewResult(requested)).isNull();
    }

    @Test
    void shouldMapEveryAcceptedResult_inTheCaseManagementViewAction() throws Exception {
        String struts = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-clinical.xml"));
        int start = struts.indexOf("<action name=\"CaseManagementView\"");
        assertThat(start).isNotNegative();
        String action = struts.substring(start, struts.indexOf("</action>", start));
        assertThat(action).contains("<result name=\"page.newcasemgmt.view\">", "<result name=\"ajaxView\">");
    }
}
