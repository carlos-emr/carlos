/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * "Del" next to a report in the Clinical Reports list removes that report. The list holds the session's
 * run reports, and the page posts each one's position in it.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("report")
@DisplayName("Removing a report from the Clinical Reports list")
class RemoveClinicalReportFromHistory2ActionUnitTest extends CarlosUnitTestBase {

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/report/RemoveClinicalReport");

    @Test
    @DisplayName("should remove the report at the posted position and keep the others")
    void shouldRemoveTheReport_atThePostedPosition() {
        var reports = reports("FAKE report A", "FAKE report B", "FAKE report C");
        request.setParameter("id", "1");

        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(reports).containsExactly("FAKE report A", "FAKE report C");
        assertThat(request.getSession().getAttribute("ClinicalReports")).isSameAs(reports);
    }

    @Test
    @DisplayName("should drop the list once its last report is removed")
    void shouldDropTheList_whenItsLastReportIsRemoved() {
        reports("FAKE report A");
        request.setParameter("id", "0");

        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
    }

    @Test
    @DisplayName("should leave the list as it is for a position outside it")
    void shouldLeaveTheList_forAPositionOutsideIt() {
        var reports = reports("FAKE report A", "FAKE report B");
        for (String id : new String[] {"2", "-1"}) {
            request.setParameter("id", id);

            assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);
        }

        assertThat(reports).containsExactly("FAKE report A", "FAKE report B");
    }

    @Test
    @DisplayName("should show the page unchanged for an id that is not a number")
    void shouldShowThePage_forAnIdThatIsNotANumber() {
        var reports = reports("FAKE report A");
        request.setParameter("id", "FAKE-not-a-number");

        // The route maps only "success": an "error" result would end on the generic error page.
        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(reports).containsExactly("FAKE report A");
    }

    @Test
    @DisplayName("should still clear the whole list on clear=yes")
    void shouldClearTheList_whenClearIsYes() {
        reports("FAKE report A", "FAKE report B");
        request.setParameter("clear", "yes");

        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
    }

    private List<Object> reports(Object... runReports) {
        List<Object> reports = new ArrayList<>(List.of(runReports));
        request.getSession().setAttribute("ClinicalReports", reports);
        return reports;
    }

    private String remove() {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_report"), eq("r"), isNull())).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            return new RemoveClinicalReportFromHistory2Action().execute();
        }
    }
}
