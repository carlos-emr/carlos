/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.data;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("billing")
class BillingFormDataFormSelectionUnitTest extends CarlosUnitTestBase {
    private CtlBillingServiceDao forms;

    @BeforeEach
    void setUp() {
        createAndRegisterMock(DiagnosticCodeDao.class);
        forms = createAndRegisterMock(CtlBillingServiceDao.class);
        when(forms.getAllServiceTypes()).thenReturn(List.of(
                new Object[] {"General", "GP"}, new Object[] {"Emergency", "ER"}));
    }

    @Test
    void shouldChooseTheFirstAvailableForm_whenANewPhysicianHasNoPreference() {
        BillingFormData data = new BillingFormData();
        assertThat(data.resolveBillingForm(null)).isEqualTo("GP");
        assertThat(data.resolveBillingForm("")).isEqualTo("GP");
        assertThat(data.resolveBillingForm("retired-form")).isEqualTo("GP");
    }

    @Test
    void shouldKeepAnAvailablePreference_andUseItsCanonicalCode() {
        assertThat(new BillingFormData().resolveBillingForm(" er ")).isEqualTo("ER");
    }

    @Test
    void shouldReturnAnEmptySelection_whenNoBillingFormsAreInstalled() {
        when(forms.getAllServiceTypes()).thenReturn(List.of());
        assertThat(new BillingFormData().resolveBillingForm(null)).isEmpty();
    }

    @Test
    void shouldPreserveQueryCellConversion_forReferralDoctors() {
        BillingmasterDAO referrals = createAndRegisterMock(BillingmasterDAO.class);
        when(referrals.getRecentReferralDoctors(123)).thenReturn(List.of(
                new Object[]{null, ""},
                new Object[]{"alpha", "alpha"},
                new Object[]{new Object[]{new String[]{"beta", "ignored"}}, new int[]{42, 99}},
                new Object[]{new Object[0], new Object[]{null}},
                new Object[]{new boolean[]{true}, new char[]{'z'}},
                new Object[]{123L, new double[]{1.5}},
                new Object[]{new byte[]{7}, new short[]{8}},
                new Object[]{new long[]{9}, new float[]{2.5f}}));
        assertThat(new BillingFormData().getRecentReferralDoctorsList(123))
                .containsExactlyInAnyOrder("alpha", "beta", "42", "true", "z", "123", "1.5", "7", "8", "9", "2.5");
    }
}
