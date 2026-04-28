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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;

@DisplayName("OhipReportGenerationService dependencies")
@Tag("unit")
@Tag("billing")
class OhipReportGenerationServiceDependencyInjectionUnitTest {

    @Test
    void shouldReceiveExtractBeanRepositoriesThroughConstructorInjection() {
        OhipReportGenerationService service = new OhipReportGenerationService(
                mock(BillActivityDao.class),
                mock(ProviderDao.class),
                mock(BillingDao.class),
                mock(BillingDetailDao.class));

        assertThat(service).isNotNull();
    }
}
