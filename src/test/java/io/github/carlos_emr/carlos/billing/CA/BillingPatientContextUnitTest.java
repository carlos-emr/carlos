/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billing.CA;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Patient labels must follow authorized identity, including within one request. @since 2026-11-09 */
@Tag("unit")
class BillingPatientContextUnitTest extends CarlosUnitTestBase {
    private DemographicManager manager;
    private MockHttpServletRequest request;
    private LoggedInInfo login;

    @BeforeEach
    void prepare() {
        manager = createAndRegisterMock(DemographicManager.class);
        request = new MockHttpServletRequest();
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
    }

    private Demographic patient(String last, String first) {
        Demographic patient = new Demographic();
        patient.setLastName(last); patient.setFirstName(first);
        return patient;
    }

    @Test
    void shouldIgnoreCallerNames_andUseTheAuthorizedPatient() {
        request.setParameter("demographic_no", "17");
        request.setParameter("demographic_name", "Forged patient");
        request.setParameter("lastName", "Forged");
        when(manager.getDemographic(login, 17)).thenReturn(patient("FAKE-Authorized", "Alice"));
        assertThat(BillingPatientContext.requiredName(request)).isEqualTo("FAKE-Authorized, Alice");
        assertThat(BillingPatientContext.name(request)).isEqualTo("FAKE-Authorized, Alice");
        verify(manager, times(1)).getDemographic(login, 17);
    }

    @Test
    void shouldReloadAndAuthorize_whenThePatientIdChangesWithinARequest() {
        when(manager.getDemographic(login, 17)).thenReturn(patient("FAKE-One", "Alice"));
        when(manager.getDemographic(login, 18)).thenReturn(patient("FAKE-Two", "Bob"));
        assertThat(BillingPatientContext.names(request, "17").last()).isEqualTo("FAKE-One");
        assertThat(BillingPatientContext.names(request, "18").last()).isEqualTo("FAKE-Two");
        verify(manager).getDemographic(login, 18);
    }

    @Test
    void shouldRefuseInvalidIdentifiers_beforeReadingPatientData() {
        for (String id : new String[] {null, "", "-1", "0", "bad", "2147483648"}) {
            assertThatThrownBy(() -> BillingPatientContext.names(request, id)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseMissingRequiredPatients_withoutUsingCallerNames() {
        request.setParameter("demographic_no", "17");
        request.setParameter("demographic_name", "Forged patient");
        assertThatThrownBy(() -> BillingPatientContext.requiredName(request)).isInstanceOf(IllegalArgumentException.class);
        assertThat(BillingPatientContext.name(request)).isEmpty();
    }

    @Test
    void shouldPropagateAuthorizationAndReadFailures() {
        when(manager.getDemographic(login, 17)).thenThrow(new SecurityException("synthetic denial"));
        assertThatThrownBy(() -> BillingPatientContext.names(request, "17")).isInstanceOf(SecurityException.class);
        when(manager.getDemographic(login, 18)).thenThrow(new jakarta.persistence.PersistenceException("synthetic read failure"));
        assertThatThrownBy(() -> BillingPatientContext.names(request, "18")).isInstanceOf(jakarta.persistence.PersistenceException.class);
    }
}
