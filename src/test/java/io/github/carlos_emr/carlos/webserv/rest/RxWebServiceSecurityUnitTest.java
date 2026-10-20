/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.PrescriptionConverter;
import io.github.carlos_emr.carlos.webserv.rest.util.AccessDeniedExceptionMapper;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Privilege and stored-owner checks must precede printing, conversion, demographic reads, and streaming. */
@Tag("unit")
class RxWebServiceSecurityUnitTest extends CarlosUnitTestBase {
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private RxWebService service;
    private Prescription script;

    @BeforeEach
    void setUp() {
        service = new RxWebService() {
            @Override public LoggedInInfo getLoggedInInfo() { return info; }
        };
        service.securityInfoManager = mock(SecurityInfoManager.class);
        service.prescriptionManager = mock(PrescriptionManager.class);
        service.demographicManager = mock(DemographicManager.class);
        service.prescriptionConverter = mock(PrescriptionConverter.class);
        service.rxManager = mock(RxManager.class);
        script = new Prescription();
        script.setDemographicId(42);
        when(service.prescriptionManager.getPrescription(info, 7)).thenReturn(script);
    }

    @Test
    void shouldDenyPrintRecording_beforeAnyWriteOrConversion() {
        assertThatThrownBy(() -> service.recordPrescriptionPrint(7)).isInstanceOf(AccessDeniedException.class);
        verify(service.prescriptionManager, never()).print(any(), anyInt());
        verifyNoInteractions(service.prescriptionConverter);
        verify(service.securityInfoManager).hasPrivilege(info, "_rx", "r", 42);
    }

    @Test
    void shouldRecordPrint_whenStoredPatientIsAuthorized() {
        when(service.securityInfoManager.hasPrivilege(info, "_rx", "r", 42)).thenReturn(true);
        service.recordPrescriptionPrint(7);
        var order = inOrder(service.securityInfoManager, service.prescriptionManager, service.prescriptionConverter);
        order.verify(service.securityInfoManager).hasPrivilege(info, "_rx", "r", 42);
        order.verify(service.prescriptionManager).print(info, 7);
        order.verify(service.prescriptionConverter).getAsTransferObject(info, script);
    }

    @ParameterizedTest
    @ValueSource(strings = {"watermark", "print"})
    void shouldRejectForgedPatient_beforeReadingDrugOrDemographicData(String endpoint) {
        when(service.securityInfoManager.hasPrivilege(info, "_rx", "r", 99)).thenReturn(true);
        assertThatThrownBy(() -> invoke(endpoint, 99)).isInstanceOf(NotFoundException.class);
        verify(service.prescriptionManager, never()).getDrugsByScriptNo(any(), anyInt(), any());
        verifyNoInteractions(service.demographicManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"watermark", "print"})
    void shouldDenyRead_beforeFetchingThePrescription(String endpoint) {
        assertThatThrownBy(() -> invoke(endpoint, 42)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service.prescriptionManager, service.demographicManager);
    }

    @Test
    void shouldRefuseMixedPatientDrugRows_beforeReturningWatermark() {
        when(service.securityInfoManager.hasPrivilege(info, "_rx", "r", 42)).thenReturn(true);
        Drug foreign = new Drug();
        foreign.setDemographicId(99);
        when(service.prescriptionManager.getDrugsByScriptNo(info, 7, null)).thenReturn(List.of(foreign));
        assertThatThrownBy(() -> invoke("watermark", 42)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldDenyReprescribe_beforeMutatingTheDrug() {
        Drug drug = new Drug();
        drug.setDemographicId(42);
        drug.setSpecial("Original instructions");
        when(service.rxManager.getDrug(info, 5)).thenReturn(drug);
        assertThatThrownBy(() -> service.represcribe(5)).isInstanceOf(AccessDeniedException.class);
        assertThat(drug.getSpecial()).isEqualTo("Original instructions");
        verify(service.securityInfoManager).hasPrivilege(info, "_rx", "w", 42);
    }

    @Test
    void shouldReturn403_withoutDisclosingDenialDetails() {
        var denial = new AccessDeniedException("_rx", "r", 42);
        try (var response = new AccessDeniedExceptionMapper().toResponse(denial)) {
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getEntity()).isEqualTo("Access denied");
        }
    }

    private Object invoke(String endpoint, int patient) {
        var request = new MockHttpServletRequest();
        return "print".equals(endpoint) ? service.print(patient, 7, request, null)
                : service.watermark(patient, 7, request, new MockHttpServletResponse());
    }
}
