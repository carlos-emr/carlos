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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
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

    private ProviderDao providerDao;
    private BillingDiskCreationService diskCreationService;
    private BillingOnDiskTransactionService transactionService;
    private ObjectFactory<OhipClaimFileService> claimFileFactory;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private BillingOnDiskService service;

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path outputDirectory;
    private Object oldHome;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        oldHome = io.github.carlos_emr.CarlosProperties.getInstance().put("HOME_DIR", outputDirectory.toString());
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
        var dto = new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingBatchHeaderDto();
        dto.setId("40");
        when(diskCreationService.prepareBatchHeader(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new BillingDiskCreationService.PreparedBatchHeader(dto, dto));
        when(diskCreationService.getOhipfilename(DISK_ID)).thenReturn("group.txt");
        when(diskCreationService.getHtmlfilename(anyInt(), anyString())).thenReturn("group.html");

        providerDao = mock(ProviderDao.class);
        service = new BillingOnDiskService(providerDao, diskCreationService,
                mock(BillingOnDiskLoader.class), claimFileFactory, transactionService);
    }

    @AfterEach
    void tearDown() {
        if (oldHome == null) io.github.carlos_emr.CarlosProperties.getInstance().remove("HOME_DIR");
        else io.github.carlos_emr.CarlosProperties.getInstance().put("HOME_DIR", oldHome);
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
        verify(transactionService).finalizeGeneratedDisks(eq(List.of(zeroTotal, paid)), eq(DISK_ID), any(BillingOnDiskTransactionService.Outcome.class));
    }

    @Test
    void shouldIncludeProvider_whenClaimItemsNetToZero() {
        OhipClaimFileService netZero = memberWriter("net-zero-body", new BigDecimal("0.00"), 3);
        OhipClaimFileService ohipFile = mock(OhipClaimFileService.class);
        givenGroupMembers(provider("101"));
        when(claimFileFactory.getObject()).thenReturn(netZero, ohipFile);

        service.generateNewDisk(allProvidersRequest());

        verify(ohipFile).writeFile("net-zero-body");
        verify(transactionService).finalizeGeneratedDisks(eq(List.of(netZero)), eq(DISK_ID), any(BillingOnDiskTransactionService.Outcome.class));
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
        verify(transactionService).finalizeGeneratedDisks(eq(List.of(paid)), eq(DISK_ID), any(BillingOnDiskTransactionService.Outcome.class));
    }

    @Test
    void shouldWriteEmptyGroupFileWithoutFinalizing_whenNoProviderHasClaimItems() {
        // OSCAR 19 contract: the disk row and headers already exist, so the listed
        // download exists too (an empty claim file); no member is finalized.
        OhipClaimFileService first = memberWriter("empty-1", BigDecimal.ZERO, 0);
        OhipClaimFileService second = memberWriter("empty-2", BigDecimal.ZERO, 0);
        OhipClaimFileService output = mock(OhipClaimFileService.class);
        givenGroupMembers(provider("101"), provider("102"));
        when(claimFileFactory.getObject()).thenReturn(first, second, output);

        service.generateNewDisk(allProvidersRequest());

        verify(first, never()).writeFile(anyString());
        verify(second, never()).writeFile(anyString());
        verify(first, never()).writeHtml(anyString());
        verify(output).setOhipFilename("group.txt");
        verify(output).writeFile("");
        verify(transactionService, never()).finalizeGeneratedDisks(anyList(), anyInt(), any(BillingOnDiskTransactionService.Outcome.class));
    }

    @Test
    void shouldReportClaimRecords_byItemCountNotTotal() {
        assertThat(BillingOnDiskService.hasClaimRecords(memberWriter("b", BigDecimal.ZERO, 1))).isTrue();
        assertThat(BillingOnDiskService.hasClaimRecords(memberWriter("b", BigDecimal.TEN, 0))).isFalse();
    }

    @Test
    void shouldOmitEmptyProvider_whenRegeneratingGroupDisk() {
        OhipClaimFileService empty = memberWriter("empty-body", BigDecimal.ZERO, 0);
        OhipClaimFileService zero = memberWriter("zero-body", BigDecimal.ZERO, 2);
        when(diskCreationService.getProvider("20")).thenReturn(List.of(provider("101"), provider("102")));
        when(claimFileFactory.getObject()).thenReturn(empty, zero);
        MockHttpServletRequest request = allProvidersRequest();
        request.setParameter("diskId", "20");

        service.regenerateDisk(request);

        verify(zero).writeFile("zero-body\n");
        verify(empty, never()).writeHtml(anyString());
        verify(transactionService).finalizeGeneratedDisks(eq(List.of(zero)), eq(DISK_ID), any(BillingOnDiskTransactionService.Outcome.class));
    }

    @Test
    void shouldPreserveExistingDisk_whenRegenerationContainsNoClaimItems() {
        OhipClaimFileService empty = memberWriter("empty-body", BigDecimal.ZERO, 0);
        when(diskCreationService.getProvider("20")).thenReturn(List.of(provider("101")));
        when(claimFileFactory.getObject()).thenReturn(empty);
        MockHttpServletRequest request = allProvidersRequest();
        request.setParameter("diskId", "20");

        service.regenerateDisk(request);

        verify(empty, never()).backupFileForRollback();
        verify(empty, never()).writeFile(anyString());
        verify(transactionService, never()).finalizeGeneratedDisks(anyList(), anyInt(), any(BillingOnDiskTransactionService.Outcome.class));
    }

    @Test
    void shouldPreserveOriginalFile_whenRegenerationRenameFails() {
        OhipClaimFileService writer = memberWriter("new-body", BigDecimal.TEN, 1);
        when(diskCreationService.getProvider("20")).thenReturn(List.of(provider("101")));
        when(claimFileFactory.getObject()).thenReturn(writer);
        doThrow(new IllegalStateException("rename failed")).when(writer).backupFileForRollback();
        MockHttpServletRequest request = allProvidersRequest();
        request.setParameter("diskId", "20");

        assertThatThrownBy(() -> service.regenerateDisk(request)).isInstanceOf(IllegalStateException.class);

        verify(writer, never()).deleteOhipFileQuietly();
        verify(transactionService, never()).finalizeGeneratedDisks(anyList(), anyInt(), any(BillingOnDiskTransactionService.Outcome.class));
    }

    @Test
    void shouldKeepOtherGroupMembersUntouched_whenOneProviderIsSelected() {
        var selected = new io.github.carlos_emr.carlos.commn.model.Provider();
        selected.setProviderNo("101");
        selected.setComments("<xml_p_billinggroup_no>1234</xml_p_billinggroup_no>");
        when(providerDao.getProvider("101")).thenReturn(selected);
        givenGroupMembers(provider("101"), provider("102"));
        OhipClaimFileService zero = memberWriter("zero-body", BigDecimal.ZERO, 2);
        OhipClaimFileService other = memberWriter("other-body", BigDecimal.TEN, 1);
        OhipClaimFileService output = mock(OhipClaimFileService.class);
        when(claimFileFactory.getObject()).thenReturn(zero, other, output);
        MockHttpServletRequest request = allProvidersRequest();
        request.setParameter("providers", "101");

        service.generateNewDisk(request);

        verify(transactionService).finalizeGeneratedDisks(eq(List.of(zero)), eq(DISK_ID), any(BillingOnDiskTransactionService.Outcome.class));
        verify(other, never()).createBillingFileStr(any(), anyString(), any(), anyBoolean(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldRenderCompleteGroupBeforeWritingHtml_whenLaterProviderFails() {
        OhipClaimFileService first = memberWriter("first-body", BigDecimal.ZERO, 2);
        OhipClaimFileService second = memberWriter("second-body", BigDecimal.TEN, 1);
        givenGroupMembers(provider("101"), provider("102"));
        when(claimFileFactory.getObject()).thenReturn(first, second);
        doThrow(new IllegalStateException("render failed")).when(second)
                .createBillingFileStr(any(), anyString(), any(), anyBoolean(), anyString(), anyBoolean(), anyBoolean());

        assertThatThrownBy(() -> service.generateNewDisk(allProvidersRequest())).isInstanceOf(IllegalStateException.class);

        verify(first, never()).writeHtml(anyString());
        verify(transactionService, never()).finalizeGeneratedDisks(anyList(), anyInt(), any(BillingOnDiskTransactionService.Outcome.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void shouldRestoreEveryPreview_whenLaterGroupWriteFails(boolean restorationFails) {
        var first = memberWriter("first", BigDecimal.ZERO, 2);
        var second = memberWriter("second", BigDecimal.TEN, 1);
        when(diskCreationService.getProvider("20")).thenReturn(List.of(provider("101"), provider("102")));
        when(claimFileFactory.getObject()).thenReturn(first, second);
        doThrow(new BillingFileWriteException("second preview failed")).when(second).writeHtml(anyString());
        if (restorationFails) doThrow(new BillingFileWriteException("restore failed")).when(first).restoreHtmlForRollback();
        var request = allProvidersRequest();
        request.setParameter("diskId", "20");

        assertThatThrownBy(() -> service.regenerateDisk(request))
                .isInstanceOf(BillingFileWriteException.class)
                .hasMessageContaining(restorationFails ? "Reconcile retained files" : "second preview failed");

        verify(first).backupHtmlForRollback();
        verify(second).backupHtmlForRollback();
        verify(second).restoreRenamedFile();
        verify(first).restoreHtmlForRollback();
        verify(second).restoreHtmlForRollback();
        verify(transactionService, never()).finalizeGeneratedDisks(anyList(), anyInt(), any(BillingOnDiskTransactionService.Outcome.class));
    }

    private void givenGroupMembers(BillingProviderDto... providers) {
        when(diskCreationService.getCurGrpProvider()).thenReturn(new ArrayList<>(List.of(providers)));
    }

    @Test
    void shouldRejectInvalidGroup_beforeAnySoloOrGroupWriteForAllProviders() {
        BillingProviderDto solo = provider("101");
        solo.setBillingGroupNo("0000");
        BillingProviderDto invalid = provider("102");
        invalid.setBillingGroupNo("12345");
        when(diskCreationService.getCurSoloProvider()).thenReturn(List.of(solo));
        givenGroupMembers(provider("103"), invalid);

        assertThatThrownBy(() -> service.generateNewDisk(allProvidersRequest()))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("group");

        verify(diskCreationService, never()).createNewSoloDiskName(anyString(), anyString());
        verify(diskCreationService, never()).createNewGrpDiskName(anyList(), anyList(), anyString(), anyString());
        org.mockito.Mockito.verifyNoInteractions(claimFileFactory, transactionService);
    }

    @Test
    void shouldRejectInvalidGroup_beforeWritingSelectedProvider() {
        BillingProviderDto invalid = provider("102");
        invalid.setBillingGroupNo("12345");
        givenGroupMembers(invalid);
        var selected = mock(io.github.carlos_emr.carlos.commn.model.Provider.class);
        when(selected.getComments()).thenReturn("<xml_p_billinggroup_no>12345</xml_p_billinggroup_no>");
        when(providerDao.getProvider("102")).thenReturn(selected);
        var request = allProvidersRequest();
        request.setParameter("providers", "102");

        assertThatThrownBy(() -> service.generateNewDisk(request))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("group");

        verify(diskCreationService, never()).createNewGrpDiskName(anyList(), anyList(), anyString(), anyString());
        org.mockito.Mockito.verifyNoInteractions(claimFileFactory, transactionService);
    }

    @Test
    void shouldRejectUnavailableSelectedGroupProvider_beforeAnyWrite() {
        givenGroupMembers(provider("101"));
        var selected = mock(io.github.carlos_emr.carlos.commn.model.Provider.class);
        when(selected.getComments()).thenReturn("<xml_p_billinggroup_no>1234</xml_p_billinggroup_no>");
        when(providerDao.getProvider("102")).thenReturn(selected);
        var request = allProvidersRequest();
        request.setParameter("providers", "102");

        assertThatThrownBy(() -> service.generateNewDisk(request))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessage("Selected provider is not available for group billing.");

        verify(diskCreationService, never()).createNewSoloDiskName(anyString(), anyString());
        verify(diskCreationService, never()).createNewGrpDiskName(anyList(), anyList(), anyString(), anyString());
        org.mockito.Mockito.verifyNoInteractions(claimFileFactory, transactionService);
    }

    @Test
    void shouldGenerateSelectedProvider_whenAnUnselectedGroupIsInvalid() {
        BillingProviderDto invalid = provider("102");
        invalid.setBillingGroupNo("12345");
        givenGroupMembers(provider("101"), invalid);
        var selected = mock(io.github.carlos_emr.carlos.commn.model.Provider.class);
        when(selected.getComments()).thenReturn("<xml_p_billinggroup_no>1234</xml_p_billinggroup_no>");
        when(providerDao.getProvider("101")).thenReturn(selected);
        var request = allProvidersRequest();
        request.setParameter("providers", "101");
        var member = memberWriter("selected-body", BigDecimal.TEN, 1);
        var output = mock(OhipClaimFileService.class);
        when(claimFileFactory.getObject()).thenReturn(member, output);

        service.generateNewDisk(request);

        verify(diskCreationService).createNewGrpDiskName(
                List.of("101"), List.of("010100"), GROUP_NO, CURRENT_USER);
        verify(output).writeFile("selected-body");
        verify(transactionService).finalizeGeneratedDisks(
                eq(List.of(member)), eq(DISK_ID), any(BillingOnDiskTransactionService.Outcome.class));
    }

    @Test
    void shouldTreatShortNumericGroup_asThatGroupZeroPadded() {
        // The provider record says "123"; the lookup service hands the disk flow
        // the normalized 0123 and the solo/group decision agrees with it.
        BillingProviderDto member = provider("101");
        member.setBillingGroupNo("0123");
        givenGroupMembers(member);
        var selected = mock(io.github.carlos_emr.carlos.commn.model.Provider.class);
        when(selected.getComments()).thenReturn("<xml_p_billinggroup_no>123</xml_p_billinggroup_no>");
        when(providerDao.getProvider("101")).thenReturn(selected);
        when(diskCreationService.createNewGrpDiskName(anyList(), anyList(), eq("0123"), eq(CURRENT_USER)))
                .thenReturn(DISK_ID);
        var request = allProvidersRequest();
        request.setParameter("providers", "101");
        var writer = memberWriter("padded-body", BigDecimal.TEN, 1);
        var output = mock(OhipClaimFileService.class);
        when(claimFileFactory.getObject()).thenReturn(writer, output);

        service.generateNewDisk(request);

        verify(diskCreationService).createNewGrpDiskName(
                List.of("101"), List.of("010100"), "0123", CURRENT_USER);
        verify(diskCreationService, never()).createNewSoloDiskName(anyString(), anyString());
        verify(output).writeFile("padded-body");
    }

    @Test
    void shouldTreatZeroOnlyGroup_asSoloBilling() {
        // "000" normalizes to 0000: the provider bills solo, not as group "000".
        var selected = mock(io.github.carlos_emr.carlos.commn.model.Provider.class);
        when(selected.getComments()).thenReturn("<xml_p_billinggroup_no>000</xml_p_billinggroup_no>");
        when(providerDao.getProvider("101")).thenReturn(selected);
        BillingProviderDto solo = provider("101");
        solo.setBillingGroupNo("0000");
        when(diskCreationService.getProviderObj("101")).thenReturn(solo);
        when(diskCreationService.createNewSoloDiskName("101", CURRENT_USER)).thenReturn(DISK_ID);
        var soloWriter = memberWriter("solo-body", BigDecimal.TEN, 1);
        when(claimFileFactory.getObject()).thenReturn(soloWriter);
        var request = allProvidersRequest();
        request.setParameter("providers", "101");

        service.generateNewDisk(request);

        verify(diskCreationService).createNewSoloDiskName("101", CURRENT_USER);
        verify(diskCreationService, never()).getCurGrpProvider();
        verify(diskCreationService, never()).createNewGrpDiskName(anyList(), anyList(), anyString(), anyString());
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
