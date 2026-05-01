/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingOnDiskService")
@Tag("unit")
@Tag("billing")
class BillingOnDiskServiceUnitTest {

    private ProviderDao providerDao;
    private BillingDiskCreationService diskCreationService;
    private BillingOnDiskLoader diskLoader;
    private ObjectFactory<OhipClaimFileService> claimFileFactory;
    private OhipClaimFileService claimFileService;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private BillingOnDiskService service;

    @BeforeEach
    void setUp() {
        providerDao = mock(ProviderDao.class);
        diskCreationService = mock(BillingDiskCreationService.class);
        diskLoader = mock(BillingOnDiskLoader.class);
        claimFileFactory = mock(ObjectFactory.class);
        claimFileService = mock(OhipClaimFileService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(claimFileFactory.getObject()).thenReturn(claimFileService);
        when(claimFileService.getValue()).thenReturn("claim-body");
        when(claimFileService.getHtmlCode()).thenReturn("<html>claim</html>");
        when(claimFileService.getBigTotal()).thenReturn(BigDecimal.TEN);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        service = new BillingOnDiskService(providerDao, diskCreationService, diskLoader, claimFileFactory);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
    }

    @Test
    void shouldWriteSoloDiskFiles_whenGeneratingNewDiskForSingleProvider() {
        MockHttpServletRequest request = newDiskRequest("999998");
        BillingProviderDto provider = provider("999998", "0000");
        when(diskCreationService.getProviderObj("999998")).thenReturn(provider);
        when(diskCreationService.createNewSoloDiskName("999998", "999998")).thenReturn(12);
        when(diskCreationService.createBatchHeader(provider, "12", "4", "1", "999998")).thenReturn(34);
        when(diskCreationService.getOhipfilename(12)).thenReturn("ohip.txt");
        when(diskCreationService.getHtmlfilename(12, "999998")).thenReturn("ohip.html");

        service.generateNewDisk(request);

        verify(claimFileService).setContextPath("/ctx");
        verify(claimFileService).setProviderNo("999998");
        verify(claimFileService).setOhipFilename("ohip.txt");
        verify(claimFileService).setHtmlFilename("ohip.html");
        verify(claimFileService).createBillingFileStr(eq(loggedInInfo), eq("34"),
                aryEq(new String[]{"O", "W", "I"}), eq(false), eq("4"), eq(false), eq(false));
        verify(claimFileService).writeFile("claim-body");
        verify(claimFileService).writeHtml("<html>claim</html>");
        verify(claimFileService).updateDisknameSum(12);
    }

    @Test
    void shouldPropagateDiskFullFailure_whenOhipFileWriteFails() {
        MockHttpServletRequest request = newDiskRequest("999998");
        BillingProviderDto provider = provider("999998", "0000");
        when(diskCreationService.getProviderObj("999998")).thenReturn(provider);
        when(diskCreationService.createNewSoloDiskName("999998", "999998")).thenReturn(12);
        when(diskCreationService.createBatchHeader(provider, "12", "4", "1", "999998")).thenReturn(34);
        when(diskCreationService.getOhipfilename(12)).thenReturn("ohip.txt");
        when(diskCreationService.getHtmlfilename(12, "999998")).thenReturn("ohip.html");
        doThrow(new BillingFileWriteException("disk full"))
                .when(claimFileService).writeFile("claim-body");

        assertThatThrownBy(() -> service.generateNewDisk(request))
                .isInstanceOf(BillingFileWriteException.class)
                .hasMessageContaining("disk full");

        verify(claimFileService, never()).writeHtml(anyString());
        verify(claimFileService, never()).updateDisknameSum(12);
    }

    @Test
    void shouldPropagatePermissionFailure_whenRegeneratedHtmlWriteFails() {
        MockHttpServletRequest request = regenerateRequest("55");
        BillingProviderDto provider = provider("999998", "0000");
        when(diskLoader.getDiskCreateDate("55")).thenReturn("2026-04-30");
        when(diskCreationService.getProvider("55")).thenReturn(List.of(provider));
        when(diskCreationService.updateBatchHeader(provider, "55", "4", "1", "999998")).thenReturn(78);
        when(diskCreationService.getOhipfilename(55)).thenReturn("regen.txt");
        when(diskCreationService.getHtmlfilename(55, "999998")).thenReturn("regen.html");
        doThrow(new BillingFileWriteException("permission denied"))
                .when(claimFileService).writeHtml("<html>claim</html>");

        assertThatThrownBy(() -> service.regenerateDisk(request))
                .isInstanceOf(BillingFileWriteException.class)
                .hasMessageContaining("permission denied");

        verify(claimFileService).readInBillingNo();
        verify(claimFileService).renameFile();
        verify(claimFileService).createBillingFileStr(eq(loggedInInfo), eq("78"),
                aryEq(new String[]{"B"}), eq(false), eq("4"), eq(false), eq(false));
        verify(claimFileService, never()).updateDisknameSum(55);
    }

    private static MockHttpServletRequest newDiskRequest(String providerNo) {
        MockHttpServletRequest request = baseRequest();
        request.setParameter("providers", providerNo);
        request.setParameter("billcenter", "4");
        request.setParameter("xml_vdate", "2026-04-01");
        request.setParameter("xml_appointment_date", "2026-04-30");
        request.setParameter("curDate", "2026-04-30");
        return request;
    }

    private static MockHttpServletRequest regenerateRequest(String diskId) {
        MockHttpServletRequest request = baseRequest();
        request.setParameter("diskId", diskId);
        request.setParameter("billcenter", "4");
        return request;
    }

    private static MockHttpServletRequest baseRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/ctx");
        request.getSession().setAttribute("user", "999998");
        return request;
    }

    private static BillingProviderDto provider(String providerNo, String groupNo) {
        BillingProviderDto provider = new BillingProviderDto();
        provider.setProviderNo(providerNo);
        provider.setOhipNo("123456");
        provider.setBillingGroupNo(groupNo);
        return provider;
    }
}
