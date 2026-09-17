/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.providers.pageUtil;

import io.github.carlos_emr.carlos.casemgmt.model.ProviderExt;
import io.github.carlos_emr.carlos.commn.dao.ProviderExtDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.providers.data.ProSignatureData;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ProEditSignature2ActionUnitTest extends CarlosWebTestBase {
    private ProviderExtDao register() {
        ProviderExtDao dao = mock(ProviderExtDao.class);
        replaceSpringUtilsBean(ProviderExtDao.class, dao);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_pref"), eq("w"), isNull())).thenReturn(true);
        return dao;
    }

    @Test void shouldPreserveSignature_whenRequestIsGet() throws Exception {
        ProviderExtDao dao = register();
        mockRequest.setMethod("GET");
        assertThat(new ProEditSignature2Action().execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        verifyNoInteractions(dao);
    }

    @Test void shouldUpdateExistingRow_whenStoredSignatureIsNull() {
        ProviderExtDao dao = register();
        ProviderExt existing = new ProviderExt();
        existing.setProviderNo("999998");
        existing.setSignature(null);
        when(dao.find("999998")).thenReturn(existing);
        new ProSignatureData().enterSignature("999998", "Synthetic Signature");
        assertThat(existing.getSignature()).isEqualTo("Synthetic Signature");
        verify(dao).merge(existing);
        verify(dao, never()).persist(any());
    }

    @Test void shouldCreateProviderExtension_whenFirstSignatureSaved() {
        ProviderExtDao dao = register();
        new ProSignatureData().enterSignature("999998", "Synthetic Signature");
        verify(dao).persist(argThat((ProviderExt p) -> "999998".equals(p.getProviderNo()) && "Synthetic Signature".equals(p.getSignature())));
        verify(dao, never()).merge(any());
    }
}
