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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.BatchEligibilityDao;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingClaimsErrorReportImportService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingObecOutputApplyService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFileImportException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnErrorReportService;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.context.annotation.Scope;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the security-critical surface of {@link BillingDocumentErrorReportUpload2Action}.
 * The silent-failure-hunter agent flagged that this action mutates patient
 * demographic records via the {@code R*} branch with no POST gate; that
 * remains an open finding (out of scope here), but the privilege
 * check itself is the primary line of defence and must not regress.
 */
@DisplayName("BillingDocumentErrorReportUpload2Action")
@Tag("unit")
@Tag("billing")
class BillingDocumentErrorReportUpload2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private DemographicManager mockDemographicManager;
    @Mock private BatchEligibilityDao mockBatchEligibilityDao;
    @Mock private DemographicCustDao mockDemographicCustDao;
    @Mock private ProviderDao mockProviderDao;
    @Mock private BillingOnErrorReportService mockErrorReportService;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse)
                .thenReturn(new MockHttpServletResponse());

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private BillingDocumentErrorReportUpload2Action newAction() {
        return new BillingDocumentErrorReportUpload2Action(mockSecurityInfoManager,
                mockDemographicManager, mockBatchEligibilityDao, mockDemographicCustDao,
                mockProviderDao, mockErrorReportService);
    }

    @Test
    void shouldBePrototypeSpringComponent_forStrutsSpringObjectFactory() {
        assertThat(BillingDocumentErrorReportUpload2Action.class.getAnnotation(Component.class))
                .isNotNull();
        Scope scope = BillingDocumentErrorReportUpload2Action.class.getAnnotation(Scope.class);
        assertThat(scope).isNotNull();
        assertThat(scope.value()).isEqualTo("prototype");
    }

    @Test
    void shouldRejectPathNames_whenLegacyFilenameSetterIsCalled() {
        BillingDocumentErrorReportUpload2Action action = newAction();

        assertThatThrownBy(() -> action.setFilename("../Rreport.txt"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("must not include a path");
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("filename", "Bsomething.txt");

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        // No DAO interaction on a security failure — the gate must be the
        // first line of defense before any file IO or DB lookup.
        verifyNoInteractions(mockDemographicManager, mockBatchEligibilityDao,
                mockDemographicCustDao, mockProviderDao, mockErrorReportService);
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissingEvenWithBlankFilename() {
        // Even the upload branch (blank filename → falls into saveFile path)
        // must hit the security gate first.
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);
        // filename intentionally absent → upload branch

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        verifyNoInteractions(mockDemographicManager, mockBatchEligibilityDao,
                mockDemographicCustDao, mockProviderDao, mockErrorReportService);
    }

    @Test
    void shouldReturnError_whenBlankFilenameAndNoUploadedFile() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        // filename intentionally absent and no UploadedFilesAware callback.

        BillingDocumentErrorReportUpload2Action action = newAction();

        assertThat(action.execute()).isEqualTo("error");
        assertThat(action.getActionErrors()).isNotEmpty();
    }

    @Test
    void shouldShowConfigurationError_whenMohReportDirectoryMissing() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("filename", "B12345.txt");

        CarlosProperties props = mock(CarlosProperties.class);
        when(props.getProperty("ONEDT_INBOX")).thenReturn("");
        when(props.getProperty("ONEDT_ARCHIVE")).thenReturn(null);
        when(props.getProperty("isNewONbilling", "")).thenReturn("");

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            BillingDocumentErrorReportUpload2Action action = newAction();

            assertThat(action.execute()).isEqualTo("error");
            assertThat(action.getActionErrors())
                    .anySatisfy(error -> assertThat(error).contains("directory is not configured"));
        }
    }

    @Test
    void shouldShowFileAccessError_whenMohReportMissingFromInboxAndArchive() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("filename", "Bmissing.txt");

        Path inbox = Files.createDirectory(tempDir.resolve("inbox"));
        Path archive = Files.createDirectory(tempDir.resolve("archive"));
        CarlosProperties props = mock(CarlosProperties.class);
        when(props.getProperty("ONEDT_INBOX")).thenReturn(inbox.toString());
        when(props.getProperty("ONEDT_ARCHIVE")).thenReturn(archive.toString());
        when(props.getProperty("isNewONbilling", "")).thenReturn("");

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            BillingDocumentErrorReportUpload2Action action = newAction();

            assertThat(action.execute()).isEqualTo("error");
            assertThat(action.getActionErrors())
                    .anySatisfy(error -> assertThat(error).contains("could not be read"));
        }
    }

    @Test
    void shouldShowImportError_whenClaimsErrorReportImportFails() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("filename", "Eclaims.err");

        Path inbox = Files.createDirectory(tempDir.resolve("inbox"));
        Path archive = Files.createDirectory(tempDir.resolve("archive"));
        Files.writeString(inbox.resolve("Eclaims.err"), "E malformed fixed-width report");

        CarlosProperties props = mock(CarlosProperties.class);
        when(props.getProperty("ONEDT_INBOX")).thenReturn(inbox.toString());
        when(props.getProperty("ONEDT_ARCHIVE")).thenReturn(archive.toString());
        when(props.getProperty("isNewONbilling", "")).thenReturn("true");

        BillingClaimsErrorReportImportService importService = mock(BillingClaimsErrorReportImportService.class);
        when(importService.importStream(any(), eq("Eclaims.err")))
                .thenThrow(new BillingFileImportException("malformed Eclaims.err", new RuntimeException("bad row")));

        registerMock(BillingClaimsErrorReportImportService.class, importService);

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            BillingDocumentErrorReportUpload2Action action = newAction();

            assertThat(action.execute()).isEqualTo("error");
            assertThat(action.getActionErrors())
                    .anySatisfy(error -> assertThat(error).contains("import failed"));
            assertThat(action.getActionErrors())
                    .noneSatisfy(error -> assertThat(error).contains("incorrect"));
        }
    }

    @Test
    void shouldNotApplyObecOutputSpec_whenRReportParseVerdictIsFalse() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockDemographicManager.searchByHealthCard(any(LoggedInInfo.class), any()))
                .thenReturn(java.util.List.of());
        mockRequest.setParameter("filename", "Rpartial.txt");

        Path inbox = Files.createDirectory(tempDir.resolve("inbox"));
        Path archive = Files.createDirectory(tempDir.resolve("archive"));
        Files.writeString(inbox.resolve("Rpartial.txt"),
                fixedWidthLine("1234567890", "AB", "05", "20261231", "Second") + "\n"
                        + "9999999999AB05too-short\n");

        BillingObecOutputApplyService applyService = mock(BillingObecOutputApplyService.class);
        registerMock(BillingObecOutputApplyService.class, applyService);

        CarlosProperties props = mock(CarlosProperties.class);
        when(props.getProperty("ONEDT_INBOX")).thenReturn(inbox.toString());
        when(props.getProperty("ONEDT_ARCHIVE")).thenReturn(archive.toString());
        when(props.getProperty("isNewONbilling", "")).thenReturn("");

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            BillingDocumentErrorReportUpload2Action action = spy(newAction());
            doReturn("errors.incorrectFileFormat").when(action).getText("errors.incorrectFileFormat");

            assertThat(action.execute()).isEqualTo("error");
            verify(applyService, never()).applyOutputSpec(any(LoggedInInfo.class), anyList());
        }
    }

    @Test
    void shouldExposeObecApplyResult_whenApplyRollsBack() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockDemographicManager.searchByHealthCard(any(LoggedInInfo.class), any()))
                .thenReturn(java.util.List.of());
        mockRequest.setParameter("filename", "Rvalid.txt");

        Path inbox = Files.createDirectory(tempDir.resolve("inbox"));
        Path archive = Files.createDirectory(tempDir.resolve("archive"));
        Files.writeString(inbox.resolve("Rvalid.txt"),
                fixedWidthLine("1234567890", "AB", "05", "20261231", "Second") + "\n");

        BillingObecOutputApplyService applyService = mock(BillingObecOutputApplyService.class);
        doThrow(new RuntimeException("db failed"))
                .when(applyService).applyOutputSpec(any(LoggedInInfo.class), anyList());
        registerMock(BillingObecOutputApplyService.class, applyService);

        CarlosProperties props = mock(CarlosProperties.class);
        when(props.getProperty("ONEDT_INBOX")).thenReturn(inbox.toString());
        when(props.getProperty("ONEDT_ARCHIVE")).thenReturn(archive.toString());
        when(props.getProperty("isNewONbilling", "")).thenReturn("");

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            BillingDocumentErrorReportUpload2Action action = spy(newAction());
            doReturn("errors.incorrectFileFormat").when(action).getText("errors.incorrectFileFormat");

            assertThat(action.execute()).isEqualTo("error");
            Object result = mockRequest.getAttribute("obecApplyResult");
            assertThat(result).isInstanceOf(BillingObecOutputApplyService.ApplyResult.class);
            BillingObecOutputApplyService.ApplyResult applyResult =
                    (BillingObecOutputApplyService.ApplyResult) result;
            assertThat(applyResult.getAppliedCount()).isZero();
            assertThat(applyResult.getSkippedCount()).isEqualTo(1);
            assertThat(applyResult.getReasons()).anySatisfy(reason ->
                    assertThat(reason).contains("rolled back"));
        }
    }

    @Test
    void shouldRenderObecApplyResult_whenWholeFileRejectedWithoutAttemptedRows() throws Exception {
        String jsp = Files.readString(Path.of(
                "src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingEAreport.jsp"));

        assertThat(jsp).contains("not empty obecApplyResult");
        assertThat(jsp).contains("not empty obecApplyResult.reasons");
        assertThat(jsp)
                .doesNotContain("not empty obecApplyResult and obecApplyResult.skippedCount gt 0");
    }

    private static String fixedWidthLine(String hin, String version, String response,
                                         String expiry, String secondName) {
        StringBuilder line = new StringBuilder(" ".repeat(110));
        replace(line, 0, hin);
        replace(line, 10, version);
        replace(line, 12, response);
        replace(line, 27, expiry);
        replace(line, 85, String.format("%-20s", secondName));
        return line.toString();
    }

    private static void replace(StringBuilder target, int start, String value) {
        target.replace(start, start + value.length(), value);
    }
}
