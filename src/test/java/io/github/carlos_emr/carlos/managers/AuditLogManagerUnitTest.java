/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Date;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class AuditLogManagerUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldConstructWithoutDatabaseConfiguration_whenSpringCreatesTheBean() {
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            assertThatCode(AuditLogManager::new).doesNotThrowAnyException();
            properties.verifyNoInteractions();
        }
    }

    @Test
    void shouldPreserveEntryPointAndStrictAudit_whenPlainDatabaseNameIsConfigured() throws Exception {
        var properties = mock(CarlosProperties.class);
        when(properties.getProperty("db_name")).thenReturn("plain_database_name");
        when(properties.getProperty("log.purge.minDays")).thenReturn("3650");
        var service = mock(AuditLogPurgeService.class);
        registerMock(AuditLogPurgeService.class, service);
        var login = mock(LoggedInInfo.class);
        Date through = new Date(946684800000L);
        when(service.purge(any(), eq(through), any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run(); return 7;
        });
        try (MockedStatic<CarlosProperties> configured = mockStatic(CarlosProperties.class)) {
            configured.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(new AuditLogManager().purgeAuditLog(login, through)).isEqualTo(7);
            logActionMock.verify(() -> LogAction.addLogStrict(eq(login), eq("AuditLogManager.purgeAuditLog"),
                    isNull(), isNull(), isNull(), anyString()));
        }
    }
}
