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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link BillingONReviewDxPersister}, the optional
 * {@code addToPatientDx} clinical write extracted out of
 * {@link BillingONReviewDataAssembler}.
 *
 * @since 2026-04-25
 */
@DisplayName("BillingONReviewDxPersister")
@Tag("unit")
@Tag("billing")
class BillingONReviewDxPersisterUnitTest extends CarlosUnitTestBase {

    @Mock
    private DxresearchDAO dxresearchDAO;

    private BillingONReviewDxPersister persister;
    private MockHttpServletRequest request;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        persister = new BillingONReviewDxPersister(dxresearchDAO);
        request = new MockHttpServletRequest();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldNotPersist_whenAddToPatientDxNotRequested() {
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "1");

        persister.persistIfRequested(request, "999998");

        verify(dxresearchDAO, never()).save(any());
    }

    @Test
    void shouldPersist_whenAddToPatientDxRequested() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "1");

        persister.persistIfRequested(request, "999998");

        ArgumentCaptor<Dxresearch> captor = ArgumentCaptor.forClass(Dxresearch.class);
        verify(dxresearchDAO, times(1)).save(captor.capture());
        Dxresearch saved = captor.getValue();
        assertThat(saved.getDemographicNo()).isEqualTo(1);
        assertThat(saved.getDxresearchCode()).isEqualTo("401");
        assertThat(saved.getCodingSystem()).isEqualTo("icd9");
        assertThat(saved.getProviderNo()).isEqualTo("999998");
    }

    @Test
    void shouldUseCodeMatchToPatientDx_whenSupplied_overDxCode() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("codeMatchToPatientDx", "401.1");
        request.setParameter("demographic_no", "1");

        persister.persistIfRequested(request, "999998");

        ArgumentCaptor<Dxresearch> captor = ArgumentCaptor.forClass(Dxresearch.class);
        verify(dxresearchDAO, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDxresearchCode()).isEqualTo("401.1");
    }

    @Test
    void shouldNotPersist_whenDemoNoMissing() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        // demographic_no missing

        persister.persistIfRequested(request, "999998");

        verify(dxresearchDAO, never()).save(any());
    }

    @Test
    void shouldNotPersist_whenBothDxFieldsEmpty() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("demographic_no", "1");
        // both dxCode and codeMatchToPatientDx missing

        persister.persistIfRequested(request, "999998");

        verify(dxresearchDAO, never()).save(any());
    }

    /**
     * Audit-trail integrity: silently dropping the write on a non-numeric
     * demographic_no would leave the provider believing the dx was added
     * when it wasn't. Throw to surface the bug.
     */
    @Test
    void shouldThrow_whenDemoNoIsNonNumeric() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "abc");

        assertThatThrownBy(() -> persister.persistIfRequested(request, "999998"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-numeric demographic_no");

        verify(dxresearchDAO, never()).save(any());
    }
}
