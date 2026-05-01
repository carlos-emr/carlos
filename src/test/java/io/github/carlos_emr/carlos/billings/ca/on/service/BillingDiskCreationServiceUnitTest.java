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

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingBatchHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingDiskNameDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for {@link BillingDiskCreationService}. Focuses on the
 * delegation pattern (the service is mostly a facade over
 * {@link BillingOnLookupService} and {@link BillingOnDiskLoader}) and on
 * {@code createBatchHeader} — the disk-creation write path that emits the
 * {@code billing_batch_header} row.
 *
 * <p><b>Known gap (out of scope for this commit):</b> a byte-level golden
 * test for the OHIP fixed-width record format that
 * {@link BillingOnClaimPersister} eventually emits. The method-level
 * coverage here exercises the field assembly; format drift would surface
 * via Playwright/runbook coverage of the disk-creation flow rather than a
 * unit test.</p>
 */
@DisplayName("BillingDiskCreationService")
@Tag("unit")
@Tag("billing")
class BillingDiskCreationServiceUnitTest {

    private BillingOnClaimPersister claimPersister;
    private BillingOnDiskLoader diskLoader;
    private BillingOnLookupService lookupService;
    private BillingDiskCreationService service;

    @BeforeEach
    void setUp() {
        claimPersister = mock(BillingOnClaimPersister.class);
        diskLoader = mock(BillingOnDiskLoader.class);
        lookupService = mock(BillingOnLookupService.class);
        // Default lookup data for tests that do not exercise refreshed OHIP
        // mappings.
        Properties props = new Properties();
        props.setProperty("999998", "012345");
        when(lookupService.getPropProviderOHIP()).thenReturn(props);
        service = newService();
    }

    private BillingDiskCreationService newService() {
        // Reflection here is necessary because the constructor is package-
        // private and not on the public surface even within the package due
        // to a shared inheritance/visibility quirk in the test layout.
        try {
            java.lang.reflect.Constructor<BillingDiskCreationService> ctor =
                    BillingDiskCreationService.class.getDeclaredConstructor(
                            BillingOnClaimPersister.class,
                            BillingOnDiskLoader.class,
                            BillingOnLookupService.class);
            ctor.setAccessible(true);
            return ctor.newInstance(claimPersister, diskLoader, lookupService);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- delegation methods --------------------------------------------

    @Test
    void shouldDelegateGetCurSoloProvider_toLookupService() {
        List<BillingProviderDto> stubbed = List.of(new BillingProviderDto(), new BillingProviderDto());
        when(lookupService.getCurSoloProvider()).thenReturn(stubbed);

        assertThat(service.getCurSoloProvider()).isEqualTo(stubbed);
        verify(lookupService).getCurSoloProvider();
    }

    @Test
    void shouldDelegateGetCurGrpProvider_toLookupService() {
        List<BillingProviderDto> stubbed = List.of(new BillingProviderDto());
        when(lookupService.getCurGrpProvider()).thenReturn(stubbed);

        assertThat(service.getCurGrpProvider()).isEqualTo(stubbed);
        verify(lookupService).getCurGrpProvider();
    }

    @Test
    void shouldDelegateGetProvider_toLookupService() {
        List<BillingProviderDto> stubbed = List.of(new BillingProviderDto());
        when(lookupService.getProvider("disk-77")).thenReturn(stubbed);

        assertThat(service.getProvider("disk-77")).isEqualTo(stubbed);
        verify(lookupService).getProvider("disk-77");
    }

    @Test
    void shouldDelegateGetProviderObj_toLookupService() {
        BillingProviderDto stubbed = new BillingProviderDto();
        when(lookupService.getProviderObj("999998")).thenReturn(stubbed);

        assertThat(service.getProviderObj("999998")).isSameAs(stubbed);
        verify(lookupService).getProviderObj("999998");
    }

    @Test
    void shouldDelegateGetOhipfilename_toDiskLoader() {
        when(diskLoader.getOhipfilename(42)).thenReturn("HD012345.001");

        assertThat(service.getOhipfilename(42)).isEqualTo("HD012345.001");
    }

    @Test
    void shouldDelegateGetHtmlfilename_toDiskLoader() {
        when(diskLoader.getHtmlfilename(42, "999998")).thenReturn("HD999998_012345.html");

        assertThat(service.getHtmlfilename(42, "999998")).isEqualTo("HD999998_012345.html");
    }

    @Test
    void shouldRefreshGetPropProviderOHIP_onEachCall() {
        // The ctor caches one snapshot but the public getter delegates fresh
        // each call — confirm the two paths.
        Properties fresh = new Properties();
        fresh.setProperty("888888", "088888");
        when(lookupService.getPropProviderOHIP()).thenReturn(fresh);

        Properties got = service.getPropProviderOHIP();

        assertThat(got.getProperty("888888")).isEqualTo("088888");
    }

    @Test
    void shouldResolveSoloDiskProviderOhipFromLiveLookupSnapshot() {
        Properties startup = new Properties();
        startup.setProperty("999998", "012345");
        Properties refreshed = new Properties();
        refreshed.setProperty("999998", "054321");
        when(lookupService.getPropProviderOHIP()).thenReturn(startup);
        service = newService();
        when(lookupService.getPropProviderOHIP()).thenReturn(refreshed);
        when(claimPersister.addBillingDiskName(org.mockito.ArgumentMatchers.any(BillingDiskNameDto.class)))
                .thenReturn(33);

        int diskId = service.createNewSoloDiskName("999998", "creator");

        assertThat(diskId).isEqualTo(33);
        ArgumentCaptor<BillingDiskNameDto> captor = ArgumentCaptor.forClass(BillingDiskNameDto.class);
        verify(claimPersister).addBillingDiskName(captor.capture());
        assertThat(captor.getValue().getProviderohipno()).containsExactly("054321");
        assertThat(captor.getValue().getOhipfilename()).contains("054321");
    }

    // ---- createBatchHeader: assembles the BillingBatchHeaderDto ---------

    @Test
    void shouldAssembleBatchHeaderWithExpectedConstants_whenCreateBatchHeader() {
        BillingProviderDto providerData = new BillingProviderDto();
        providerData.setBillingGroupNo("GRP1");
        providerData.setOhipNo("012345");
        providerData.setSpecialtyCode("00");
        when(claimPersister.addOneBatchHeaderRecord(org.mockito.ArgumentMatchers.any(BillingBatchHeaderDto.class)))
                .thenReturn(99);

        int id = service.createBatchHeader(providerData, "42", "G", "1", "999998");

        assertThat(id).isEqualTo(99);

        ArgumentCaptor<BillingBatchHeaderDto> captor =
                ArgumentCaptor.forClass(BillingBatchHeaderDto.class);
        verify(claimPersister).addOneBatchHeaderRecord(captor.capture());
        BillingBatchHeaderDto persisted = captor.getValue();

        assertThat(persisted.getDisk_id()).isEqualTo("42");
        assertThat(persisted.getMoh_office()).isEqualTo("G");
        assertThat(persisted.getProvider_reg_num()).isEqualTo("012345");
        assertThat(persisted.getSpecialty()).isEqualTo("00");
        assertThat(persisted.getCreator()).isEqualTo("999998");
        // Constants from BillingOnConstants — these are the fixed-width
        // record markers OHIP requires; drift here breaks claim submission.
        assertThat(persisted.getTransc_id())
                .isEqualTo(BillingOnConstants.BATCHHEADER_TRANSACTIONIDENTIFIER);
        assertThat(persisted.getRec_id())
                .isEqualTo(BillingOnConstants.BATCHHEADER_REORDIDENTIFICATION);
        assertThat(persisted.getSpec_id())
                .isEqualTo(BillingOnConstants.BATCHHEADER_SPECID);
        assertThat(persisted.getAction())
                .isEqualTo(BillingOnConstants.BILLINGACTION_CREATE);
    }

    @Test
    void shouldRightJustifySeqNum_whenAssemblingBatchId() {
        // batchId = today + zero-padded(seqNum, 4)
        BillingProviderDto providerData = new BillingProviderDto();
        providerData.setBillingGroupNo("");
        providerData.setOhipNo("012345");
        providerData.setSpecialtyCode("00");
        when(claimPersister.addOneBatchHeaderRecord(org.mockito.ArgumentMatchers.any(BillingBatchHeaderDto.class)))
                .thenReturn(1);

        service.createBatchHeader(providerData, "42", "G", "7", "999998");

        ArgumentCaptor<BillingBatchHeaderDto> captor =
                ArgumentCaptor.forClass(BillingBatchHeaderDto.class);
        verify(claimPersister).addOneBatchHeaderRecord(captor.capture());
        // The batch id ends in zero-padded "0007".
        assertThat(captor.getValue().getBatch_id()).endsWith("0007");
        assertThat(captor.getValue().getBatch_id()).hasSize(12); // yyyyMMdd + 4
    }

    @Test
    void shouldReturnEmpty_whenGetCurSoloProviderListIsEmpty() {
        when(lookupService.getCurSoloProvider()).thenReturn(Collections.emptyList());

        assertThat(service.getCurSoloProvider()).isEmpty();
    }
}
