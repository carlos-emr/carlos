/*
 * Copyright (c) 2026. CARLOS EMR contributors and others.
 *
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
 */
package io.github.carlos_emr.carlos.fax.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FaxStatusUpdater#updateStatus()}.
 * Tests status polling, orphan detection, inactive account handling, and error resilience.
 *
 * @since 2026-02-13
 */
@Tag("unit")
@Tag("fax")
@DisplayName("FaxStatusUpdater Unit Tests")
class FaxStatusUpdaterTest extends OpenOUnitTestBase {

    private static final String FAX_LINE_A = "6045551234";
    private static final String FAX_LINE_B = "6045559999";
    private static final String FAX_LINE_C = "6045550000";
    private static final String FAX_LINE_D = "6045551111";
    private static final String FAX_LINE_E = "6045552222";
    private static final String FAX_LINE_F = "6045553333";
    private static final String FAX_LINE_G = "6045554444";
    private static final String FAX_LINE_H = "6045555555";

    private FaxJobDao faxJobDao;
    private FaxConfigDao faxConfigDao;
    private FaxProviderClientFactory faxProviderClientFactory;
    private FaxProviderClient faxProviderClient;
    private FaxStatusUpdater faxStatusUpdater;

    @BeforeEach
    void setUp() {
        faxJobDao = mock(FaxJobDao.class);
        faxConfigDao = mock(FaxConfigDao.class);
        faxProviderClientFactory = mock(FaxProviderClientFactory.class);
        faxProviderClient = mock(FaxProviderClient.class);
        faxStatusUpdater = new FaxStatusUpdater(faxJobDao, faxConfigDao, faxProviderClientFactory);
    }

    @Test
    @DisplayName("should update status and statusString from provider response")
    void shouldUpdateStatusAndStatusString_fromProviderResponse() throws FaxProviderException {
        // Given
        FaxJob inProgressFax = createFaxJob(1, FAX_LINE_A, FaxJob.STATUS.SENT, 100L);
        when(faxJobDao.getInprogressFaxesByJobId()).thenReturn(Collections.singletonList(inProgressFax));

        FaxConfig activeConfig = createActiveFaxConfig(1, FAX_LINE_A);
        when(faxConfigDao.getConfigByNumber(FAX_LINE_A)).thenReturn(activeConfig);
        when(faxProviderClientFactory.getClient(activeConfig)).thenReturn(faxProviderClient);

        FaxJob updatedFromProvider = new FaxJob();
        updatedFromProvider.setStatus(FaxJob.STATUS.COMPLETE);
        updatedFromProvider.setStatusString("Delivered to recipient");
        when(faxProviderClient.fetchFaxStatus(activeConfig, inProgressFax)).thenReturn(updatedFromProvider);

        // When
        faxStatusUpdater.updateStatus();

        // Then
        assertThat(inProgressFax.getStatus()).isEqualTo(FaxJob.STATUS.COMPLETE);
        assertThat(inProgressFax.getStatusString()).isEqualTo("Delivered to recipient");
        verify(faxJobDao).merge(inProgressFax);
    }

    @Test
    @DisplayName("should mark fax as ERROR when FaxConfig is deleted")
    void shouldMarkFaxAsError_whenFaxConfigIsDeleted() {
        // Given
        FaxJob orphanedFax = createFaxJob(2, FAX_LINE_B, FaxJob.STATUS.SENT, 200L);
        when(faxJobDao.getInprogressFaxesByJobId()).thenReturn(Collections.singletonList(orphanedFax));
        when(faxConfigDao.getConfigByNumber(FAX_LINE_B)).thenReturn(null);

        // When
        faxStatusUpdater.updateStatus();

        // Then
        assertThat(orphanedFax.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(orphanedFax.getStatusString())
                .isEqualTo("Fax account configuration was deleted - cannot check status");
        verify(faxJobDao).merge(orphanedFax);
    }

    @Test
    @DisplayName("should skip status check when FaxConfig account is inactive")
    void shouldSkipStatusCheck_whenFaxConfigAccountIsInactive() throws FaxProviderException {
        // Given
        FaxJob faxWithInactiveAccount = createFaxJob(3, FAX_LINE_C, FaxJob.STATUS.WAITING, 300L);
        when(faxJobDao.getInprogressFaxesByJobId())
                .thenReturn(Collections.singletonList(faxWithInactiveAccount));

        FaxConfig inactiveConfig = new FaxConfig();
        inactiveConfig.setId(3);
        inactiveConfig.setFaxNumber(FAX_LINE_C);
        inactiveConfig.setActive(false);
        when(faxConfigDao.getConfigByNumber(FAX_LINE_C)).thenReturn(inactiveConfig);

        // When
        faxStatusUpdater.updateStatus();

        // Then
        verify(faxProviderClientFactory, never()).getClient(any());
        verify(faxProviderClient, never()).fetchFaxStatus(any(), any());
        verify(faxJobDao, never()).merge(any());
    }

    @Test
    @DisplayName("should handle empty fax list gracefully")
    void shouldHandleEmptyFaxList_gracefully() {
        // Given
        when(faxJobDao.getInprogressFaxesByJobId()).thenReturn(Collections.emptyList());

        // When
        faxStatusUpdater.updateStatus();

        // Then
        verify(faxConfigDao, never()).getConfigByNumber(any());
        verify(faxJobDao, never()).merge(any());
    }

    @Test
    @DisplayName("should continue processing remaining faxes when one status check fails")
    void shouldContinueProcessingRemainingFaxes_whenOneStatusCheckFails() throws FaxProviderException {
        // Given
        FaxJob failingFax = createFaxJob(10, FAX_LINE_D, FaxJob.STATUS.SENT, 1001L);
        FaxJob succeedingFax = createFaxJob(11, FAX_LINE_E, FaxJob.STATUS.SENT, 1002L);
        when(faxJobDao.getInprogressFaxesByJobId())
                .thenReturn(Arrays.asList(failingFax, succeedingFax));

        FaxConfig config1 = createActiveFaxConfig(10, FAX_LINE_D);
        FaxConfig config2 = createActiveFaxConfig(11, FAX_LINE_E);
        when(faxConfigDao.getConfigByNumber(FAX_LINE_D)).thenReturn(config1);
        when(faxConfigDao.getConfigByNumber(FAX_LINE_E)).thenReturn(config2);

        FaxProviderClient client1 = mock(FaxProviderClient.class);
        FaxProviderClient client2 = mock(FaxProviderClient.class);
        when(faxProviderClientFactory.getClient(config1)).thenReturn(client1);
        when(faxProviderClientFactory.getClient(config2)).thenReturn(client2);

        when(client1.fetchFaxStatus(config1, failingFax))
                .thenThrow(new FaxProviderException("Provider API timeout"));

        FaxJob updatedSecondFax = new FaxJob();
        updatedSecondFax.setStatus(FaxJob.STATUS.COMPLETE);
        updatedSecondFax.setStatusString("Sent successfully");
        when(client2.fetchFaxStatus(config2, succeedingFax)).thenReturn(updatedSecondFax);

        // When
        faxStatusUpdater.updateStatus();

        // Then - first fax status enum unchanged but statusString updated, second fax fully updated
        assertThat(failingFax.getStatus()).isEqualTo(FaxJob.STATUS.SENT);
        assertThat(failingFax.getStatusString())
                .contains("[Status check failed: Provider API timeout]");
        assertThat(succeedingFax.getStatus()).isEqualTo(FaxJob.STATUS.COMPLETE);
        assertThat(succeedingFax.getStatusString()).isEqualTo("Sent successfully");
        verify(faxJobDao).merge(failingFax);
        verify(faxJobDao).merge(succeedingFax);
    }

    @Test
    @DisplayName("should process multiple faxes from different accounts")
    void shouldProcessMultipleFaxes_fromDifferentAccounts() throws FaxProviderException {
        // Given
        FaxJob fax1 = createFaxJob(20, FAX_LINE_D, FaxJob.STATUS.SENT, 2001L);
        FaxJob fax2 = createFaxJob(21, FAX_LINE_E, FaxJob.STATUS.WAITING, 2002L);
        when(faxJobDao.getInprogressFaxesByJobId()).thenReturn(Arrays.asList(fax1, fax2));

        FaxConfig config1 = createActiveFaxConfig(20, FAX_LINE_D);
        FaxConfig config2 = createActiveFaxConfig(21, FAX_LINE_E);
        when(faxConfigDao.getConfigByNumber(FAX_LINE_D)).thenReturn(config1);
        when(faxConfigDao.getConfigByNumber(FAX_LINE_E)).thenReturn(config2);

        FaxProviderClient client1 = mock(FaxProviderClient.class);
        FaxProviderClient client2 = mock(FaxProviderClient.class);
        when(faxProviderClientFactory.getClient(config1)).thenReturn(client1);
        when(faxProviderClientFactory.getClient(config2)).thenReturn(client2);

        FaxJob updated1 = new FaxJob();
        updated1.setStatus(FaxJob.STATUS.COMPLETE);
        updated1.setStatusString("Delivered");
        when(client1.fetchFaxStatus(config1, fax1)).thenReturn(updated1);

        FaxJob updated2 = new FaxJob();
        updated2.setStatus(FaxJob.STATUS.ERROR);
        updated2.setStatusString("Invalid fax number");
        when(client2.fetchFaxStatus(config2, fax2)).thenReturn(updated2);

        // When
        faxStatusUpdater.updateStatus();

        // Then
        assertThat(fax1.getStatus()).isEqualTo(FaxJob.STATUS.COMPLETE);
        assertThat(fax1.getStatusString()).isEqualTo("Delivered");
        assertThat(fax2.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(fax2.getStatusString()).isEqualTo("Invalid fax number");
        verify(faxJobDao).merge(fax1);
        verify(faxJobDao).merge(fax2);
    }

    @Test
    @DisplayName("should continue processing when RuntimeException occurs for one fax")
    void shouldContinueProcessing_whenRuntimeExceptionOccursForOneFax() throws FaxProviderException {
        // Given
        FaxJob crashingFax = createFaxJob(30, FAX_LINE_F, FaxJob.STATUS.SENT, 3001L);
        FaxJob normalFax = createFaxJob(31, FAX_LINE_G, FaxJob.STATUS.SENT, 3002L);
        when(faxJobDao.getInprogressFaxesByJobId())
                .thenReturn(Arrays.asList(crashingFax, normalFax));

        when(faxConfigDao.getConfigByNumber(FAX_LINE_F))
                .thenThrow(new RuntimeException("Database connection lost"));

        FaxConfig config2 = createActiveFaxConfig(31, FAX_LINE_G);
        when(faxConfigDao.getConfigByNumber(FAX_LINE_G)).thenReturn(config2);
        when(faxProviderClientFactory.getClient(config2)).thenReturn(faxProviderClient);

        FaxJob updated = new FaxJob();
        updated.setStatus(FaxJob.STATUS.COMPLETE);
        updated.setStatusString("OK");
        when(faxProviderClient.fetchFaxStatus(config2, normalFax)).thenReturn(updated);

        // When
        faxStatusUpdater.updateStatus();

        // Then - second fax still processed despite first crashing
        assertThat(normalFax.getStatus()).isEqualTo(FaxJob.STATUS.COMPLETE);
        assertThat(normalFax.getStatusString()).isEqualTo("OK");
        verify(faxJobDao).merge(normalFax);
    }

    @Test
    @DisplayName("should preserve fax status enum but append failure info to statusString when provider exception occurs")
    void shouldPreserveFaxStatusButAppendFailureInfo_whenProviderExceptionOccurs() throws FaxProviderException {
        // Given
        FaxJob fax = createFaxJob(40, FAX_LINE_H, FaxJob.STATUS.SENT, 4001L);
        fax.setStatusString("Queued for delivery");
        when(faxJobDao.getInprogressFaxesByJobId()).thenReturn(Collections.singletonList(fax));

        FaxConfig config = createActiveFaxConfig(40, FAX_LINE_H);
        when(faxConfigDao.getConfigByNumber(FAX_LINE_H)).thenReturn(config);
        when(faxProviderClientFactory.getClient(config)).thenReturn(faxProviderClient);
        when(faxProviderClient.fetchFaxStatus(config, fax))
                .thenThrow(new FaxProviderException("API rate limit exceeded"));

        // When
        faxStatusUpdater.updateStatus();

        // Then - status enum unchanged, but statusString updated with failure info and merged
        assertThat(fax.getStatus()).isEqualTo(FaxJob.STATUS.SENT);
        assertThat(fax.getStatusString())
                .startsWith("Queued for delivery")
                .contains("[Status check failed: API rate limit exceeded]");
        verify(faxJobDao).merge(fax);
    }

    // -- helper methods --

    private FaxJob createFaxJob(Integer id, String faxLine, FaxJob.STATUS status, Long jobId) {
        FaxJob faxJob = new FaxJob();
        faxJob.setId(id);
        faxJob.setFax_line(faxLine);
        faxJob.setStatus(status);
        faxJob.setJobId(jobId);
        return faxJob;
    }

    private FaxConfig createActiveFaxConfig(Integer id, String faxNumber) {
        FaxConfig config = new FaxConfig();
        config.setId(id);
        config.setFaxNumber(faxNumber);
        config.setActive(true);
        return config;
    }
}
