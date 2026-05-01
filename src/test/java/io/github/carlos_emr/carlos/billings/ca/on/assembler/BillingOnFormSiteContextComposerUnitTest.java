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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFormViewModel;
import io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.ClinicNbr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Smoke unit test for {@link BillingOnFormSiteContextComposer}. Pins the
 * constructor wiring so a future refactor that adds or removes a DAO
 * dependency surfaces here. Deeper behavioral tests for
 * {@code populate(...)} require a heavyweight HttpServletRequest +
 * IsPropertiesOn setup and are tracked as a separate scope.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingOnFormSiteContextComposer")
@Tag("unit")
@Tag("billing")
class BillingOnFormSiteContextComposerUnitTest {

    @Test
    void shouldNotThrow_whenPopulateCalledWithMockedDependencies() {
        // populate() reads multisite + rma_enabled flags from CarlosProperties
        // (static, hard to mock in pure unit context). Pin only that calling
        // populate() with all-mock DAOs doesn't throw — exercising real branch
        // coverage requires a CarlosProperties test fixture not currently
        // available; tracked as a separate test-infra task.
        SiteDao siteDao = mock(SiteDao.class);
        OscarAppointmentDao oscarAppointmentDao = mock(OscarAppointmentDao.class);
        ClinicNbrDao clinicNbrDao = mock(ClinicNbrDao.class);
        ProviderDao providerDao = mock(ProviderDao.class);
        when(clinicNbrDao.findAll()).thenReturn(new java.util.ArrayList<>());

        BillingOnFormSiteContextComposer composer = new BillingOnFormSiteContextComposer(
                siteDao, oscarAppointmentDao, clinicNbrDao, providerDao);

        BillingOnFormViewModel.Builder b = BillingOnFormViewModel.builder();
        composer.populate(b, new MockHttpServletRequest(), "999998", null, null);

        // Assert builder produced a usable VM (smoke-level branch coverage).
        BillingOnFormViewModel vm = b.build();
        assertThat(vm).isNotNull();
        assertThat(vm.getClinicNbrs()).isNotNull();
    }
}
