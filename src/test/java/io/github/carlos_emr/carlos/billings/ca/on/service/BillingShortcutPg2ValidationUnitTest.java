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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Pins the strict-parse contract on the FK fields written by
 * {@link BillingShortcutPg2Service#persistLegacyBillingRecord}.
 *
 * <p>Pre-fix: the local {@code parseInt(String)} helper returned 0 on
 * NumberFormatException/NullPointerException, silently persisting Billing
 * rows with demographic_no=0 / clinic_no=0 / appointment_no=0. The fix
 * adds {@code parseRequiredInt(name, value)} which throws
 * {@link BillingValidationException} so the exception-mapping in
 * struts-billing.xml routes the failure to the validation page.
 *
 * @since 2026-04-30
 */
@DisplayName("BillingShortcutPg2 FK validation")
@Tag("unit")
@Tag("billing")
class BillingShortcutPg2ValidationUnitTest {

    private BillingShortcutPg2Service newSvc() {
        return new BillingShortcutPg2Service(
                mock(BillingDao.class),
                mock(BillingDetailDao.class),
                mock(ProviderDao.class),
                mock(DemographicDao.class),
                mock(BillingServiceDao.class),
                mock(BillingPercLimitDao.class),
                mock(BillingClaimSubmissionService.class));
    }

    private static int invokeParseRequiredInt(String name, String value) throws Exception {
        Method m = BillingShortcutPg2Service.class.getDeclaredMethod(
                "parseRequiredInt", String.class, String.class);
        m.setAccessible(true);
        try {
            return (int) m.invoke(null, name, value);
        } catch (InvocationTargetException e) {
            // unwrap to surface the real exception type for assertions
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    @Test
    void shouldReturnInt_whenValueIsValidInteger() throws Exception {
        assertThat(invokeParseRequiredInt("demographic_no", "42")).isEqualTo(42);
    }

    @Test
    void shouldTrimWhitespace_beforeParsing() throws Exception {
        assertThat(invokeParseRequiredInt("clinic_no", "  17  ")).isEqualTo(17);
    }

    @Test
    void shouldThrowBillingValidationException_whenValueIsNull() {
        // Must not return 0 — that would silently persist a row keyed to an
        // invalid demographic.
        assertThatThrownBy(() -> invokeParseRequiredInt("demographic_no", null))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("demographic_no")
                .hasMessageContaining("missing");
    }

    @Test
    void shouldThrowBillingValidationException_whenValueIsEmpty() {
        assertThatThrownBy(() -> invokeParseRequiredInt("clinic_no", ""))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("clinic_no");
    }

    @Test
    void shouldThrowBillingValidationException_whenValueIsNonNumeric() {
        assertThatThrownBy(() -> invokeParseRequiredInt("appointment_no", "not-a-number"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("appointment_no")
                .hasMessageContaining("valid integer");
    }

    @Test
    void shouldNotInstantiate_butVerifyFactoryReachable() {
        // Smoke test that the service is constructible with the mocks above —
        // catches a future ctor-signature drift that would silently break the
        // test class wiring.
        assertThat(newSvc()).isNotNull();
    }
}
