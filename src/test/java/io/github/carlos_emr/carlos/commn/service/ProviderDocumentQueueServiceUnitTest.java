/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.daos.security.SecObjectNameDao;
import io.github.carlos_emr.carlos.model.security.Secobjectname;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** @since 2026-11-16 */
@Tag("unit")
class ProviderDocumentQueueServiceUnitTest extends CarlosUnitTestBase {
    private final QueueDao queues = mock(QueueDao.class);
    private final SecObjectNameDao security = mock(SecObjectNameDao.class);
    private final UserPropertyDAO properties = mock(UserPropertyDAO.class);
    private final ProviderDocumentQueueService service = new ProviderDocumentQueueService(queues, security, properties);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t", "\r\n"})
    void shouldRejectBlankNames_beforeAnyDatabaseCall(String name) {
        assertThatThrownBy(() -> service.createDefaultQueue("owner", name)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queues, security, properties);
    }

    @Test
    void shouldRefuseAnUnassignedId_beforeCreatingSecurityOrPreferences() {
        when(queues.addNewQueueAndGetId("Mine")).thenReturn(null);
        assertThatThrownBy(() -> service.createDefaultQueue("owner", "Mine")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(security, properties);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCreateSecurityBeforeSavingTheOwningProvidersPreference(boolean existing) {
        UserProperty previous = new UserProperty();
        previous.setProviderNo("owner");
        previous.setName(UserProperty.DOC_DEFAULT_QUEUE);
        previous.setValue("7");
        when(queues.addNewQueueAndGetId("Mine")).thenReturn(42);
        if (existing) when(properties.getProp("owner", UserProperty.DOC_DEFAULT_QUEUE)).thenReturn(previous);
        UserProperty result = service.createDefaultQueue("owner", "Mine");
        assertThat(result.getProviderNo()).isEqualTo("owner");
        assertThat(result.getName()).isEqualTo(UserProperty.DOC_DEFAULT_QUEUE);
        assertThat(result.getValue()).isEqualTo("42");
        if (existing) assertThat(result).isSameAs(previous);
        var order = inOrder(queues, security, properties);
        order.verify(queues).addNewQueueAndGetId("Mine");
        order.verify(security).saveOrUpdate(argThat(object -> "_queue.42".equals(object.getObjectname())
                && "Mine".equals(object.getDescription()) && object.getOrgapplicable() == 0));
        order.verify(properties).getProp("owner", UserProperty.DOC_DEFAULT_QUEUE);
        order.verify(properties).saveProp(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Doctor's queue", "' OR 1=1 --", " Équipe "})
    void shouldPreserveNonblankNames_asLiteralValues(String name) {
        when(queues.addNewQueueAndGetId(name)).thenReturn(42);
        service.createDefaultQueue("owner", name);
        verify(queues).addNewQueueAndGetId(name);
        verify(security).saveOrUpdate(argThat(object -> name.equals(object.getDescription())));
    }

    @Test
    void shouldStopBeforeChangingPreferences_whenTheSecurityWriteFails() {
        when(queues.addNewQueueAndGetId("Mine")).thenReturn(42);
        doThrow(new IllegalStateException("FAKE security refusal")).when(security).saveOrUpdate(any(Secobjectname.class));
        assertThatThrownBy(() -> service.createDefaultQueue("owner", "Mine")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(properties);
    }
}
