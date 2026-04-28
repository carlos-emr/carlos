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
package io.github.carlos_emr.carlos.billings.ca.on.OHIP;

import io.github.carlos_emr.carlos.billings.ca.on.service.FeeScheduleApplyResult;
import io.github.carlos_emr.carlos.billings.ca.on.service.FeeScheduleAppliedChange;
import io.github.carlos_emr.carlos.billings.ca.on.service.FeeScheduleChange;
import io.github.carlos_emr.carlos.billings.ca.on.service.FeeScheduleImportResult;
import io.github.carlos_emr.carlos.billings.ca.on.service.FeeScheduleImportService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Schedule of Benefits actions")
@Tag("unit")
@Tag("billing")
class ScheduleOfBenefitsActionsUnitTest {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private FeeScheduleImportService feeScheduleImportService;
    private LoggedInInfo loggedInInfo;
    private Path uploadFile;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        feeScheduleImportService = mock(FeeScheduleImportService.class);
        loggedInInfo = mock(LoggedInInfo.class);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (uploadFile != null) Files.deleteIfExists(uploadFile);
    }

    @Test
    void uploadShouldPreviewFeeScheduleWithInjectedService() throws Exception {
        uploadFile = Files.createTempFile("schedule-of-benefits", ".txt");
        Files.writeString(uploadFile,
                "A001" + "20260428" + "99999999"
                        + "00000337000" + "00000000000" + "00000000000"
                        + "00000000000" + "00000000000" + "\n");
        FeeScheduleChange change = new FeeScheduleChange("A001A", null, new BigDecimal("33.70"), null,
                "prices", "20260428", "99999999", "Minor assessment", 0, true);
        when(feeScheduleImportService.preview(any(), any()))
                .thenReturn(new FeeScheduleImportResult(List.of(change), Collections.emptyList(), false));

        ScheduleOfBenefitsUpload2Action action =
                new ScheduleOfBenefitsUpload2Action(securityInfoManager, feeScheduleImportService);
        action.setImportFile(uploadFile.toFile());

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("outcome")).isEqualTo("success");
        assertThat((List) request.getAttribute("warnings")).singleElement()
                .satisfies(warning -> assertThat((java.util.Map) warning).containsEntry("feeCode", "A001A"));
        verify(feeScheduleImportService).preview(any(), any());
    }

    @Test
    void updateShouldApplySelectedChangesWithInjectedService() {
        request.setParameter("change", "A001A|33.70|20260428|99999999|Minor assessment");
        when(feeScheduleImportService.applySelected(any()))
                .thenReturn(new FeeScheduleApplyResult(
                        List.of(new FeeScheduleAppliedChange("A001A", new BigDecimal("33.70"))),
                        Collections.emptyList()));

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(securityInfoManager, feeScheduleImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat((List) request.getAttribute("changes")).singleElement()
                .satisfies(change -> assertThat((java.util.Map) change).containsEntry("code", "A001A"));
        verify(feeScheduleImportService).applySelected(any());
    }
}
