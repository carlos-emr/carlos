/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Reproduces the production context's legacy XML and scanned audit DAO names.
 * @since 2026-11-09
 */
@Tag("unit")
@Tag("lab")
class Hl7PatientUnlinkWiringUnitTest {
    @Test
    void shouldWireTransactionalAuditDao_whenBothAuditBeansExist() {
        var legacy = mock(OscarLogDao.class);
        var selected = mock(OscarLogDao.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PatientLabRoutingDao.class, () -> mock(PatientLabRoutingDao.class));
            context.registerBean(ProviderLabRoutingDao.class, () -> mock(ProviderLabRoutingDao.class));
            context.registerBean(MeasurementDao.class, () -> mock(MeasurementDao.class));
            context.registerBean(MeasurementsDeletedDao.class, () -> mock(MeasurementsDeletedDao.class));
            context.registerBean(MeasurementsExtDao.class, () -> mock(MeasurementsExtDao.class));
            context.registerBean(Hl7TextMessageDao.class, () -> mock(Hl7TextMessageDao.class));
            context.registerBean(SecurityInfoManager.class, () -> mock(SecurityInfoManager.class));
            context.registerBean("oscarLogDao", OscarLogDao.class, () -> legacy);
            context.registerBean("oscarLogDaoImpl", OscarLogDao.class, () -> selected);
            context.register(Hl7PatientUnlinkService.class);
            context.refresh();
            assertThat(ReflectionTestUtils.getField(context.getBean(Hl7PatientUnlinkService.class), "audit"))
                    .isSameAs(selected).isNotSameAs(legacy);
        }
    }
}
