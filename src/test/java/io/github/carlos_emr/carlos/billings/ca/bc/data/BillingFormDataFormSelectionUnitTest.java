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
}
