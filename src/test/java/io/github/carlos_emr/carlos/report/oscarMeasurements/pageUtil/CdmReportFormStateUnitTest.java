/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CdmReportFormStateUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldUseDefaults_whenOpeningForm() {
        var request = new MockHttpServletRequest("GET", "/form");
        request.addParameter("startDateD", "unsubmitted");
        var state = new CdmReportFormState(request);
        assertEquals("2025-01-01", state.value("startDateD", 0, "2025-01-01"));
        assertTrue(state.selected("instruction", "yes", true));
        assertFalse(state.selected("row", "0", false));
    }

    @Test
    void shouldUseDefaults_whenSelectingReportWithPost() {
        var request = new MockHttpServletRequest("POST", "/SelectCDMReport");
        request.addParameter("value(CDMgroup)", "owned-group");
        var state = new CdmReportFormState(request);
        assertEquals("2025-01-01", state.value("startDateB", 0, "2025-01-01"));
        assertTrue(state.selected("instruction", "yes", true));
    }

    @Test
    void shouldRestoreRawValue_whenConversionFailsBeforeActionExecutes() {
        var request = new MockHttpServletRequest("POST", "/form");
        request.setAttribute("fieldErrors", java.util.Map.of("exactly", java.util.List.of("Invalid number")));
        request.addParameter("exactly", "not-an-integer");
        assertEquals("not-an-integer", new CdmReportFormState(request).value("exactly", 0, ""));
    }

    @Test
    void shouldPreserveInvalidValuesAndArrayPositions_whenReturningInput() {
        var request = new MockHttpServletRequest("POST", "/form");
        request.setAttribute("actionErrors", java.util.List.of("Invalid date"));
        request.addParameter("startDateD", "2025-01-01", "not-a-date");
        request.addParameter("exactly", "1", "not-an-integer");
        var state = new CdmReportFormState(request);
        assertEquals("not-a-date", state.value("startDateD", 1, "default"));
        assertEquals("not-an-integer", state.value("exactly", 1, ""));
    }

    @Test
    void shouldKeepOmittedAndBlankValuesEmpty_whenSubmitted() {
        var request = new MockHttpServletRequest("POST", "/form");
        request.setAttribute("actionErrors", java.util.List.of("Invalid date"));
        request.addParameter("date", "");
        var state = new CdmReportFormState(request);
        assertEquals("", state.value("date", 0, "default"));
        assertEquals("", state.value("missing", 0, "default"));
        assertEquals("", state.value("date", 1, "default"));
        assertEquals("", state.value("date", -1, "default"));
        assertFalse(state.selected("instruction", "yes", true));
    }

    @Test
    void shouldRestoreExactSelections_whenReturningInput() {
        var request = new MockHttpServletRequest("POST", "/form");
        request.setAttribute("actionErrors", java.util.List.of("Invalid date"));
        request.addParameter("row", "1", "10");
        request.addParameter("comparison", "<");
        var state = new CdmReportFormState(request);
        assertTrue(state.selected("row", "1", false));
        assertTrue(state.selected("row", "10", false));
        assertFalse(state.selected("row", "0", true));
        assertTrue(state.selected("comparison", "<", false));
        assertFalse(state.selected("comparison", ">", true));
    }
}
