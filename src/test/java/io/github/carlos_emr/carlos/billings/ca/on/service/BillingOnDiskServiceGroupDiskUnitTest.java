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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.commn.dao.BillCenterDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderBillCenterDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Group-disk coverage for {@link BillingOnDiskService#generateNewDisk}: which
 * group members' claim batches land in the OHIP file (issue #3942).
 *
 * <p>Membership is decided by claim-item count, not dollar total. Ported from
 * the {@code ongenreport.jsp} fix by Sebastian Ibanez in openo-beta/Open-O
 * PR #2510.</p>
 *
 * @since 2026-09-26
 */
@DisplayName("BillingOnDiskService group disk membership")
@Tag("unit")
@Tag("billing")
class BillingOnDiskServiceGroupDiskUnitTest extends CarlosUnitTestBase {

    private static final String GROUP_NO = "1234";
    private static final String CURRENT_USER = "999998";
    private static final int DISK_ID = 20;

    private BillingDiskCreationService diskCreationService;
    private BillingOnDiskTransactionService transactionService;
    private ObjectFactory<OhipClaimFileService> claimFileFactory;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private BillingOnDiskService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // ProviderBillCenter (constructed inside the service) resolves these via SpringUtils.
        createAndRegisterMock(ProviderBillCenterDao.class);
        createAndRegisterMock(BillCenterDao.class);

        diskCreationService = mock(BillingDiskCreationService.class);
        transactionService = mock(BillingOnDiskTransactionService.class);
        claimFileFactory = mock(ObjectFactory.class);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mock(LoggedInInfo.class));

        when(diskCreationService.getCurSoloProvider()).thenReturn(List.of());
        when(diskCreationService.createNewGrpDiskName(anyList(), anyList(), eq(GROUP_NO), eq(CURRENT_USER)))
                .thenReturn(DISK_ID);
        when(diskCreationService.createBatchHeader(any(BillingProviderDto.class), eq("" + DISK_ID),
                anyString(), anyString(), eq(CURRENT_USER))).thenReturn(40);
        when(diskCreationService.getOhipfilename(DISK_ID)).thenReturn("group.txt");
        when(diskCreationService.getHtmlfilename(anyInt(), anyString())).thenReturn("group.html");

        service = new BillingOnDiskService(mock(ProviderDao.class), diskCreationService,
                mock(BillingOnDiskLoader.class), claimFileFactory, transactionService);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
    }

    @Test
    void shouldIncludeZeroTotalProvider_whenProviderHasClaimItems() {
        OhipClaimFileService zeroTotal = memberWriter("zero-body", BigDecimal.ZERO, 2);
        OhipClaimFileService paid = memberWriter("paid-body", BigDecimal.TEN, 1);
        OhipClaimFileService ohipFile = mock(OhipClaimFileService.class);
        givenGroupMembers(provider("101"), provider("102"));
        when(claimFileFactory.getObject()).thenReturn(zeroTotal, paid, ohipFile);

        service.generateNewDisk(allProvidersRequest());

        verify(ohipFile).writeFile("zero-bodypaid-body");
        verify(zeroTotal).writeHtml("<html>zero-body</html>");
        verify(paid).writeHtml("<html>paid-body</html>");
        verify(transactionService).finalizeGeneratedDisks(List.of(zeroTotal, paid), DISK_ID);
    }

    @Test
    void shouldIncludeProvider_whenClaimItemsNetToZero() {
        OhipClaimFileService netZero = memberWriter("net-zero-body", new BigDecimal("0.00"), 3);
        OhipClaimFileService ohipFile = mock(OhipClaimFileService.class);
        givenGroupMembers(provider("101"));
        when(claimFileFactory.getObject()).thenReturn(netZero, ohipFile);

        service.generateNewDisk(allProvidersRequest());

        verify(ohipFile).writeFile("net-zero-body");
        verify(transactionService).finalizeGeneratedDisks(List.of(netZero), DISK_ID);
    }

    @Test
    void shouldOmitProviderBatch_whenProviderHasNoClaimItems() {
        OhipClaimFileService empty = memberWriter("empty-header-and-trailer", BigDecimal.ZERO, 0);
        OhipClaimFileService paid = memberWriter("paid-body", BigDecimal.TEN, 1);
        OhipClaimFileService ohipFile = mock(OhipClaimFileService.class);
        givenGroupMembers(provider("101"), provider("102"));
        when(claimFileFactory.getObject()).thenReturn(empty, paid, ohipFile);

        service.generateNewDisk(allProvidersRequest());

        verify(ohipFile).writeFile("paid-body");
        verify(empty, never()).writeHtml(anyString());
        verify(transactionService).finalizeGeneratedDisks(List.of(paid), DISK_ID);
    }

    @Test
    void shouldNotWriteGroupFile_whenNoProviderHasClaimItems() {
        OhipClaimFileService first = memberWriter("empty-1", BigDecimal.ZERO, 0);
        OhipClaimFileService second = memberWriter("empty-2", BigDecimal.ZERO, 0);
        givenGroupMembers(provider("101"), provider("102"));
        when(claimFileFactory.getObject()).thenReturn(first, second);

        service.generateNewDisk(allProvidersRequest());

        verify(first, never()).writeFile(anyString());
        verify(second, never()).writeFile(anyString());
        verify(transactionService, never()).finalizeGeneratedDisks(anyList(), anyInt());
    }

    @Test
    void shouldReportClaimRecords_byItemCountNotTotal() {
        assertThat(BillingOnDiskService.hasClaimRecords(memberWriter("b", BigDecimal.ZERO, 1))).isTrue();
        assertThat(BillingOnDiskService.hasClaimRecords(memberWriter("b", BigDecimal.TEN, 0))).isFalse();
    }

    private void givenGroupMembers(BillingProviderDto... providers) {
        when(diskCreationService.getCurGrpProvider()).thenReturn(new ArrayList<>(List.of(providers)));
    }

    private static OhipClaimFileService memberWriter(String body, BigDecimal total, int recordCount) {
        OhipClaimFileService writer = mock(OhipClaimFileService.class);
        when(writer.getValue()).thenReturn(body);
        when(writer.getHtmlCode()).thenReturn("<html>" + body + "</html>");
        when(writer.getBigTotal()).thenReturn(total);
        when(writer.getRecordCount()).thenReturn(recordCount);
        return writer;
    }

    private static MockHttpServletRequest allProvidersRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/ctx");
        request.getSession().setAttribute("user", CURRENT_USER);
        request.setParameter("providers", "all");
        request.setParameter("billcenter", "4");
        request.setParameter("xml_vdate", "2026-04-01");
        request.setParameter("xml_appointment_date", "2026-04-30");
        request.setParameter("curDate", "2026-04-30");
        return request;
    }

    private static BillingProviderDto provider(String providerNo) {
        BillingProviderDto provider = new BillingProviderDto();
        provider.setProviderNo(providerNo);
        provider.setOhipNo("0" + providerNo + "00");
        provider.setBillingGroupNo(GROUP_NO);
        return provider;
    }
}
