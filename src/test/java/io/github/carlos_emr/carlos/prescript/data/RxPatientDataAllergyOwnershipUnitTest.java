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
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the allergy ownership check added to {@link RxPatientData.Patient}
 * to close issue #2467 (cross-patient allergy archive/activate IDOR).
 *
 * <p>{@code Patient.allergyDao} is a {@code private static} field hydrated once
 * from {@code SpringUtils.getBean(AllergyDao.class)} at class-load time, so it
 * cannot be substituted through the normal {@link CarlosUnitTestBase#registerMock}
 * flow. This test swaps it via reflection for the duration of the test and
 * restores the original value afterward to avoid leaking state into other tests.
 *
 * @since 2026-07-06
 */
@DisplayName("RxPatientData.Patient allergy ownership unit tests")
@Tag("unit")
@Tag("rx")
class RxPatientDataAllergyOwnershipUnitTest extends CarlosUnitTestBase {

    private static final int SESSION_DEMOGRAPHIC_NO = 200;
    private static final int OTHER_DEMOGRAPHIC_NO = 100;
    private static final int ALLERGY_ID = 42;

    private AutoCloseable mocks;
    private Field allergyDaoField;
    private Object originalAllergyDao;

    @Mock
    private AllergyDao mockAllergyDao;

    @Mock
    private PartialDateDao mockPartialDateDao;

    @Mock
    private PlatformTransactionManager mockTransactionManager;

    private RxPatientData.Patient patient;

    @BeforeEach
    void setUp() throws Exception {
        // Bootstrap mock in case this is the first time RxPatientData.Patient is
        // initialized in this JVM/fork: its <clinit> resolves AllergyDao via
        // SpringUtils.getBean, and an unmocked failure here permanently poisons
        // the class (NoClassDefFoundError) for every other test in the same fork.
        registerMock(AllergyDao.class, mock(AllergyDao.class));

        mocks = MockitoAnnotations.openMocks(this);

        allergyDaoField = RxPatientData.Patient.class.getDeclaredField("allergyDao");
        allergyDaoField.setAccessible(true);
        originalAllergyDao = allergyDaoField.get(null);
        allergyDaoField.set(null, mockAllergyDao);

        registerMock(PartialDateDao.class, mockPartialDateDao);
        registerMock(PlatformTransactionManager.class, mockTransactionManager);

        Demographic demographic = new Demographic();
        demographic.setDemographicNo(SESSION_DEMOGRAPHIC_NO);
        patient = new RxPatientData.Patient(demographic);
    }

    @AfterEach
    void tearDown() throws Exception {
        allergyDaoField.set(null, originalAllergyDao);
        if (mocks != null) {
            mocks.close();
        }
    }

    private Allergy allergyOwnedBy(int demographicNo) {
        Allergy allergy = new Allergy();
        allergy.setDemographicNo(demographicNo);
        return allergy;
    }

    @Test
    @DisplayName("getAllergy should return the allergy when it belongs to the session patient")
    void shouldReturnAllergy_whenDemographicNoMatches() {
        Allergy allergy = allergyOwnedBy(SESSION_DEMOGRAPHIC_NO);
        when(mockAllergyDao.find(ALLERGY_ID)).thenReturn(allergy);

        Allergy result = patient.getAllergy(ALLERGY_ID);

        assertThat(result).isSameAs(allergy);
    }

    @Test
    @DisplayName("getAllergy should return null when the allergy belongs to a different patient")
    void shouldReturnNull_whenDemographicNoDiffers() {
        Allergy allergy = allergyOwnedBy(OTHER_DEMOGRAPHIC_NO);
        when(mockAllergyDao.find(ALLERGY_ID)).thenReturn(allergy);

        Allergy result = patient.getAllergy(ALLERGY_ID);

        assertThat(result).isNull();
        verify(mockPartialDateDao, never()).getPartialDate(any(), any(), any());
    }

    @Test
    @DisplayName("getAllergy should return null when the allergy does not exist")
    void shouldReturnNull_whenAllergyNotFound() {
        when(mockAllergyDao.find(ALLERGY_ID)).thenReturn(null);

        Allergy result = patient.getAllergy(ALLERGY_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("deleteAllergy should archive and return true when the allergy belongs to the session patient")
    void shouldArchiveAndReturnTrue_whenDemographicNoMatches() {
        Allergy allergy = allergyOwnedBy(SESSION_DEMOGRAPHIC_NO);
        when(mockAllergyDao.find(ALLERGY_ID)).thenReturn(allergy);

        boolean result = patient.deleteAllergy(ALLERGY_ID);

        assertThat(result).isTrue();
        assertThat(allergy.getArchived()).isTrue();
        verify(mockAllergyDao).merge(allergy);
    }

    @Test
    @DisplayName("deleteAllergy should not archive and return false when the allergy belongs to a different patient")
    void shouldNotArchiveAndReturnFalse_whenDemographicNoDiffers() {
        Allergy allergy = allergyOwnedBy(OTHER_DEMOGRAPHIC_NO);
        when(mockAllergyDao.find(ALLERGY_ID)).thenReturn(allergy);

        boolean result = patient.deleteAllergy(ALLERGY_ID);

        assertThat(result).isFalse();
        verify(mockAllergyDao, never()).merge(any());
    }

    @Test
    @DisplayName("activateAllergy should reactivate and return true when the allergy belongs to the session patient")
    void shouldActivateAndReturnTrue_whenDemographicNoMatches() {
        Allergy allergy = allergyOwnedBy(SESSION_DEMOGRAPHIC_NO);
        allergy.setArchived(true);
        when(mockAllergyDao.find(ALLERGY_ID)).thenReturn(allergy);

        boolean result = patient.activateAllergy(ALLERGY_ID);

        assertThat(result).isTrue();
        assertThat(allergy.getArchived()).isFalse();
        verify(mockAllergyDao).merge(allergy);
    }

    @Test
    @DisplayName("activateAllergy should not reactivate and return false when the allergy belongs to a different patient")
    void shouldNotActivateAndReturnFalse_whenDemographicNoDiffers() {
        Allergy allergy = allergyOwnedBy(OTHER_DEMOGRAPHIC_NO);
        when(mockAllergyDao.find(ALLERGY_ID)).thenReturn(allergy);

        boolean result = patient.activateAllergy(ALLERGY_ID);

        assertThat(result).isFalse();
        verify(mockAllergyDao, never()).merge(any());
    }

    @Test
    @DisplayName("amendActiveAllergy should archive the active original before adding its replacement, in one transaction")
    void shouldArchiveOriginalThenAddReplacement_whenOriginalIsActive() {
        Allergy replacement = allergyOwnedBy(SESSION_DEMOGRAPHIC_NO);
        when(mockAllergyDao.archiveIfActive(ALLERGY_ID, SESSION_DEMOGRAPHIC_NO)).thenReturn(1);

        boolean result = patient.amendActiveAllergy(new java.util.Date(), replacement, ALLERGY_ID);

        assertThat(result).isTrue();
        InOrder order = inOrder(mockTransactionManager, mockAllergyDao);
        order.verify(mockTransactionManager).getTransaction(any());
        order.verify(mockAllergyDao).archiveIfActive(ALLERGY_ID, SESSION_DEMOGRAPHIC_NO);
        order.verify(mockAllergyDao).persist(replacement);
        order.verify(mockTransactionManager).commit(any());
    }

    @Test
    @DisplayName("amendActiveAllergy should add nothing when the original is no longer active (issue #4410)")
    void shouldAddNothing_whenOriginalIsNoLongerActive() {
        Allergy replacement = allergyOwnedBy(SESSION_DEMOGRAPHIC_NO);
        when(mockAllergyDao.archiveIfActive(ALLERGY_ID, SESSION_DEMOGRAPHIC_NO)).thenReturn(0);

        boolean result = patient.amendActiveAllergy(new java.util.Date(), replacement, ALLERGY_ID);

        assertThat(result).isFalse();
        verify(mockAllergyDao, never()).persist(any());
        verify(mockPartialDateDao, never()).setPartialDate((Integer) any(), any(), any(), (String) any());
    }
}
