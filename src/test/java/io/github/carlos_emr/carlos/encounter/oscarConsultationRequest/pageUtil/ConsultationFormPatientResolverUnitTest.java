// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ConsultationFormPatientResolverUnitTest {
    private final ConsultationRequestDao requests = mock(ConsultationRequestDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final ConsultationFormPatientResolver resolver = new ConsultationFormPatientResolver(requests, security);

    @Test
    void shouldAuthorizeStoredOwner_whenSuppliedPatientDiffers() {
        ConsultationRequest owner = new ConsultationRequest();
        owner.setDemographicId(42);
        when(requests.find(11)).thenReturn(owner);
        when(security.hasPrivilege(info, "_con", "r", "42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(info, 42)).thenReturn(true);
        assertThat(resolver.resolve(info, "11", "7")).isEqualTo(42);
        verify(security, never()).hasPrivilege(info, "_con", "r", "7");
    }

    @Test
    void shouldRefuseBeforeClinicalLoading_whenPatientRecordIsRestricted() {
        when(security.hasPrivilege(info, "_con", "r", "42")).thenReturn(true);
        assertThatThrownBy(() -> resolver.resolve(info, null, "42")).isInstanceOf(SecurityException.class);
        verifyNoInteractions(requests);
    }

    @Test
    void shouldRetainSegmentOnlyAccess_whenNoLocalPatientExists() {
        when(security.hasPrivilege(info, "_con", "r", (String) null)).thenReturn(true);
        assertThat(resolver.resolve(info, null, null)).isNull();
        verify(security, never()).isAllowedAccessToPatientRecord(any(), any());
    }

    @Test
    void shouldRejectInvalidIdentifiers_whenMalformedOrOverflowing() {
        for (String id : new String[]{"0", "-1", "x", "2147483648", "1&de=2"}) {
            assertThatThrownBy(() -> resolver.resolve(info, null, id)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(requests, security);
    }
}
