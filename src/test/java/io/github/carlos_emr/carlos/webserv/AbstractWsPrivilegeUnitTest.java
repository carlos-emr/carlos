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

package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.FacilityManager;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.MeasurementTransfer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("AbstractWs privilege enforcement")
@Tag("unit")
@Tag("webservice")
class AbstractWsPrivilegeUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("requirePrivilege throws when access is missing")
    void shouldThrowSecurityException_whenAccessIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null))
                .thenReturn(false);

        TestAbstractWs service = new TestAbstractWs(loggedInInfo, securityInfoManager);

        assertThatThrownBy(() -> service.enforce("_appointment", "r"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");
    }

    @Test
    @DisplayName("schedule service checks appointment privilege before loading data")
    void shouldCheckAppointmentPrivilege_beforeLoadingData() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null))
                .thenReturn(true);
        when(scheduleManager.getAppointmentTypes()).thenReturn(Collections.emptyList());

        TestScheduleWs service = new TestScheduleWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);

        service.getAppointmentTypes();

        var inOrder = inOrder(securityInfoManager, scheduleManager);
        inOrder.verify(securityInfoManager).hasPrivilege(loggedInInfo, "_appointment", "r", (String) null);
        inOrder.verify(scheduleManager).getAppointmentTypes();
    }

    @Test
    @DisplayName("schedule service stops before loading data when privilege is missing")
    void shouldStopLoadingData_whenPrivilegeIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null))
                .thenReturn(false);

        TestScheduleWs service = new TestScheduleWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);

        assertThatThrownBy(service::getAppointmentTypes)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");

        verifyNoInteractions(scheduleManager);
    }


    @Test
    @DisplayName("provider properties allow self reads with pref privilege")
    void shouldAllowSelfReads_withPrefPrivilege() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ProviderManager2 providerManager = mock(ProviderManager2.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_pref", "r", null))
                .thenReturn(true);
        when(providerManager.getProviderProperties(loggedInInfo, "101", "faxnumber"))
                .thenReturn(List.of(new Property()));

        TestProviderWs service = new TestProviderWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "providerManager", providerManager);

        service.getProviderProperties("101", "faxnumber");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_pref", "r", (String) null);
        verify(providerManager).getProviderProperties(loggedInInfo, "101", "faxnumber");
    }

    @Test
    @DisplayName("provider properties require admin for cross-provider reads")
    void shouldRequireAdmin_forCrossProviderReads() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ProviderManager2 providerManager = mock(ProviderManager2.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null))
                .thenReturn(false);

        TestProviderWs service = new TestProviderWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "providerManager", providerManager);

        assertThatThrownBy(() -> service.getProviderProperties("202", "faxnumber"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");

        verifyNoInteractions(providerManager);
    }

    @Test
    @DisplayName("provider properties deny unauthenticated requests without consulting the manager")
    void shouldDenyWithoutConsultingManager_whenSessionIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ProviderManager2 providerManager = mock(ProviderManager2.class);

        TestProviderWs service = new TestProviderWs(null, securityInfoManager);
        ReflectionTestUtils.setField(service, "providerManager", providerManager);

        assertThatThrownBy(() -> service.getProviderProperties("202", "faxnumber"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");

        // A null LoggedInInfo is denied in requirePrivilege rather than passed down:
        // SecurityInfoManagerImpl would dereference it, swallow its own NPE and return false,
        // logging an "Error checking privileges" stack trace for a plain unauthenticated call.
        verifyNoInteractions(securityInfoManager);
        verifyNoInteractions(providerManager);
    }

    @Test
    @DisplayName("demographic privilege checks pass null demographic numbers through unchanged")
    void shouldPassNullDemographicNumber_whenDemographicIdIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null))
                .thenReturn(false);

        TestDemographicWs service = new TestDemographicWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);

        assertThatThrownBy(() -> service.getDemographic(null))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_demographic", "r", (String) null);
        verifyNoInteractions(demographicManager);
    }

    @Test
    @DisplayName("demographic batch reads enforce per-patient privileges before loading data")
    void shouldRequirePerDemographicPrivilege_beforeBatchLoad() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", "11")).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", "22")).thenReturn(true);
        when(demographicManager.getDemographics(loggedInInfo, List.of(11, 22))).thenReturn(Collections.emptyList());

        TestDemographicWs service = new TestDemographicWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);

        service.getDemographics(new Integer[] {11, 22});

        var inOrder = inOrder(securityInfoManager, demographicManager);
        inOrder.verify(securityInfoManager).hasPrivilege(loggedInInfo, "_demographic", "r", "11");
        inOrder.verify(securityInfoManager).hasPrivilege(loggedInInfo, "_demographic", "r", "22");
        inOrder.verify(demographicManager).getDemographics(loggedInInfo, List.of(11, 22));
    }

    @Test
    @DisplayName("demographic search filters unreadable patients instead of throwing")
    void shouldFilterUnreadableDemographics_whenSearchReturnsMixedVisibility() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        io.github.carlos_emr.carlos.commn.model.Demographic readable = new io.github.carlos_emr.carlos.commn.model.Demographic();
        readable.setDemographicNo(11);
        io.github.carlos_emr.carlos.commn.model.Demographic unreadable = new io.github.carlos_emr.carlos.commn.model.Demographic();
        unreadable.setDemographicNo(22);
        when(demographicManager.searchDemographicByName(loggedInInfo, "smith", 0, 10))
                .thenReturn(List.of(readable, unreadable));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", (String) null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", "11")).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", "22")).thenReturn(false);

        TestDemographicWs service = new TestDemographicWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);

        var result = service.searchDemographicByName("smith", 0, 10);

        org.assertj.core.api.Assertions.assertThat(result).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(result[0].getDemographicNo()).isEqualTo(11);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_demographic", "r", "11");
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_demographic", "r", "22");
    }

    @Test
    @DisplayName("measurement writes enforce demographic-scoped privileges")
    void shouldRequireDemographicScopedPrivilege_whenAddingMeasurement() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        MeasurementManager measurementManager = mock(MeasurementManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "w", "33")).thenReturn(false);

        TestMeasurementWs service = new TestMeasurementWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "measurementManager", measurementManager);

        MeasurementTransfer transfer = new MeasurementTransfer();
        transfer.setDemographicId(33);

        assertThatThrownBy(() -> service.addMeasurement(transfer))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_measurement)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_measurement", "w", "33");
        verifyNoInteractions(measurementManager);
    }

    /**
     * Pins the archive endpoint to {@code _appointment r} and, by asserting no interaction with
     * {@code _appointment.UpdatedAfterDate}, pins that it is <em>not</em> gated on that flag.
     *
     * <p>That object is a consent-bypass marker in {@code ScheduleManagerImpl}, not an access gate,
     * and no migration grants it to any role - requiring it here denied every caller including
     * admin, taking Integrator archive sync down on stock installs.</p>
     */
    @Test
    @DisplayName("appointment archive sync requires appointment read privilege before loading data")
    void shouldRequireAppointmentReadPrivilege_beforeLoadingAppointmentArchives() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null))
                .thenReturn(true);
        when(scheduleManager.getAppointmentArchiveUpdatedAfterDate(loggedInInfo, null, 25))
                .thenReturn(Collections.emptyList());

        TestScheduleWs service = new TestScheduleWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);

        service.getAppointmentArchivesUpdatedAfterDate(null, 25, false);

        var inOrder = inOrder(securityInfoManager, scheduleManager);
        inOrder.verify(securityInfoManager).hasPrivilege(loggedInInfo, "_appointment", "r", (String) null);
        inOrder.verify(scheduleManager).getAppointmentArchiveUpdatedAfterDate(loggedInInfo, null, 25);
        verify(securityInfoManager, org.mockito.Mockito.never())
                .hasPrivilege(loggedInInfo, "_appointment.UpdatedAfterDate", "x", (String) null);
    }

    @Test
    @DisplayName("appointment archive sync stops before loading data when privilege is missing")
    void shouldStopLoadingArchives_whenAppointmentPrivilegeIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null)).thenReturn(false);

        TestScheduleWs service = new TestScheduleWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);

        assertThatThrownBy(() -> service.getAppointmentArchivesUpdatedAfterDate(null, 25, false))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");

        verifyNoInteractions(scheduleManager);
    }

    @Test
    @DisplayName("prescription reads enforce demographic-scoped privileges before loading drugs")
    void shouldRequireDemographicScopedPrivilege_beforeLoadingPrescriptionDrugs() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        PrescriptionManager prescriptionManager = mock(PrescriptionManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        Prescription prescription = new Prescription();
        prescription.setDemographicId(55);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", (String) null)).thenReturn(true);
        when(prescriptionManager.getPrescription(loggedInInfo, 44)).thenReturn(prescription);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", "55")).thenReturn(false);

        TestPrescriptionWs service = new TestPrescriptionWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "prescriptionManager", prescriptionManager);

        assertThatThrownBy(() -> service.getPrescription(44))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_rx", "r", "55");
        verify(prescriptionManager).getPrescription(loggedInInfo, 44);
        verify(prescriptionManager, org.mockito.Mockito.never())
                .getDrugsByScriptNo(loggedInInfo, 44, false);
    }


    @Test
    @DisplayName("demographic batch reads stop before loading when one patient is restricted")
    void shouldStopBatchLoad_whenOneDemographicIsRestricted() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", "11")).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", "22")).thenReturn(false);

        TestDemographicWs service = new TestDemographicWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);

        // Explicit-ID reads fail closed rather than silently filtering: the caller named these
        // patients, so a short result set would misrepresent the record rather than protect it.
        assertThatThrownBy(() -> service.getDemographics(new Integer[] {11, 22}))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");

        verifyNoInteractions(demographicManager);
    }

    @Test
    @DisplayName("demographic search stops before loading when demographic read is missing")
    void shouldStopSearch_whenDemographicReadIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", (String) null)).thenReturn(false);

        TestDemographicWs service = new TestDemographicWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);

        // Without the coarse gate a caller holding no demographic rights would get an empty list
        // from the per-patient filter, indistinguishable from "no patients matched".
        assertThatThrownBy(() -> service.searchDemographicByName("smith", 0, 10))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");

        verifyNoInteractions(demographicManager);
    }

    @Test
    @DisplayName("prescription reads stop before loading the record when rx read is missing")
    void shouldStopPrescriptionLoad_whenRxReadIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        PrescriptionManager prescriptionManager = mock(PrescriptionManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", (String) null)).thenReturn(false);

        TestPrescriptionWs service = new TestPrescriptionWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "prescriptionManager", prescriptionManager);

        // The entry check closes an enumeration oracle: before it, a missing prescription returned
        // null with no check at all while an existing one faulted, distinguishing the two.
        assertThatThrownBy(() -> service.getPrescription(44))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verifyNoInteractions(prescriptionManager);
    }

    @Test
    @DisplayName("prescription reads deny missing records the same way as existing ones")
    void shouldDenyIdentically_whenPrescriptionDoesNotExist() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        PrescriptionManager prescriptionManager = mock(PrescriptionManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", (String) null)).thenReturn(false);

        TestPrescriptionWs service = new TestPrescriptionWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "prescriptionManager", prescriptionManager);

        // Same fault, same message as the existing-prescription case above, and the manager is
        // never reached either way - so the response cannot distinguish a real ID from a bogus one.
        assertThatThrownBy(() -> service.getPrescription(999))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verifyNoInteractions(prescriptionManager);
    }

    @Test
    @DisplayName("booking stops before writing an appointment when appointment write is missing")
    void shouldStopBooking_whenAppointmentWriteIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)).thenReturn(false);

        TestBookingWs service = new TestBookingWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);

        assertThatThrownBy(() -> service.bookAppointment("slot", "notes"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");

        verifyNoInteractions(scheduleManager);
    }

    @Test
    @DisplayName("document reads stop before loading when edoc read is missing")
    void shouldStopDocumentLoad_whenEdocReadIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        DocumentManager documentManager = mock(DocumentManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", null)).thenReturn(false);

        TestDocumentWs service = new TestDocumentWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "documentManager", documentManager);

        assertThatThrownBy(() -> service.getDocument(7))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_edoc)");

        verifyNoInteractions(documentManager);
    }

    /**
     * The denial must escape as an exception, not be flattened into the method's
     * {@code success:0} JSON, which a sending lab system would read as a routine per-file
     * rejection and never retry.
     */
    @Test
    @DisplayName("lab upload rejects the call outright when lab write is missing")
    void shouldRejectUpload_whenLabWriteIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "w", null)).thenReturn(false);

        TestLabUploadWs service = new TestLabUploadWs(loggedInInfo, securityInfoManager);

        assertThatThrownBy(() -> service.uploadCLS("lab.hl7", "contents", "999998"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_lab)");
    }

    /**
     * {@code _pmm.programList} and {@code _pmm.staffList} replaced {@code _pmm_management}, which
     * no migration grants to any role - gating on it made both endpoints unreachable for everyone,
     * admin included.
     */
    @Test
    @DisplayName("program list reads stop before loading when program list read is missing")
    void shouldStopProgramLoad_whenProgramListReadIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ProgramManager2 programManager = mock(ProgramManager2.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_pmm.programList", "r", null)).thenReturn(false);

        TestProgramWs service = new TestProgramWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "programManager", programManager);

        assertThatThrownBy(service::getAllPrograms)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_pmm.programList)");

        verifyNoInteractions(programManager);
    }

    @Test
    @DisplayName("program provider reads stop before loading when staff list read is missing")
    void shouldStopProgramProviderLoad_whenStaffListReadIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ProgramManager2 programManager = mock(ProgramManager2.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_pmm.staffList", "r", null)).thenReturn(false);

        TestProgramWs service = new TestProgramWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "programManager", programManager);

        assertThatThrownBy(service::getAllProgramProviders)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_pmm.staffList)");

        verifyNoInteractions(programManager);
    }

    @Test
    @DisplayName("facility listing stops before loading when admin read is missing")
    void shouldStopFacilityListing_whenAdminReadIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        FacilityManager facilityManager = mock(FacilityManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(false);

        TestFacilityWs service = new TestFacilityWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "facilityManager", facilityManager);

        assertThatThrownBy(() -> service.getAllFacilities(Boolean.TRUE))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");

        verifyNoInteractions(facilityManager);
    }

    /**
     * Everything else here overrides {@code getSecurityInfoManager()}, which leaves the real
     * SpringUtils-backed resolution path untested. This drives it through the registry instead.
     */
    @Test
    @DisplayName("privilege checks resolve the security manager through Spring wiring")
    void shouldResolveSecurityManager_throughSpringWiring() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null)).thenReturn(false);

        SpringWiredWs service = new SpringWiredWs(loggedInInfo);

        assertThatThrownBy(() -> service.enforce("_appointment", "r"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_appointment", "r", (String) null);
    }

    private static LoggedInInfo loggedInInfo(String providerNo) {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLocale(Locale.CANADA);
        return loggedInInfo;
    }

    private static class TestAbstractWs extends AbstractWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestAbstractWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        void enforce(String objectName, String privilege) {
            requirePrivilege(objectName, privilege);
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestProviderWs extends ProviderWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestProviderWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestDemographicWs extends DemographicWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestDemographicWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestScheduleWs extends ScheduleWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestScheduleWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestMeasurementWs extends MeasurementWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestMeasurementWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestPrescriptionWs extends PrescriptionWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestPrescriptionWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    /** Exercises the real SpringUtils-backed resolution rather than overriding it. */
    private static class SpringWiredWs extends AbstractWs {
        private final LoggedInInfo loggedInInfo;

        private SpringWiredWs(LoggedInInfo loggedInInfo) {
            this.loggedInInfo = loggedInInfo;
        }

        void enforce(String objectName, String privilege) {
            requirePrivilege(objectName, privilege);
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }
    }

    private static class TestBookingWs extends BookingWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestBookingWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestDocumentWs extends DocumentWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestDocumentWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestLabUploadWs extends LabUploadWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestLabUploadWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestProgramWs extends ProgramWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestProgramWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestFacilityWs extends FacilityWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestFacilityWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }
}
