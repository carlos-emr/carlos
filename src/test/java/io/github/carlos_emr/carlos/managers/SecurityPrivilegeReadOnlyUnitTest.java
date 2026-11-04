/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.documentManager.IncomingDocumentCapacityResponse;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class SecurityPrivilegeReadOnlyUnitTest extends CarlosUnitTestBase {
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final HttpSession session = mock(HttpSession.class);
    private final SecuserroleDao roles = mock(SecuserroleDao.class);
    private final SecObjPrivilegeDao privileges = mock(SecObjPrivilegeDao.class);
    private final SecurityInfoManagerImpl security = new SecurityInfoManagerImpl();

    @BeforeEach void prepare() {
        registerMock(SecObjPrivilegeDao.class, privileges);
        injectDependency(security, "secUserRoleDao", roles);
        when(info.getLoggedInProviderNo()).thenReturn("900001");
        lenient().when(info.getSession()).thenReturn(session);
        Secuserrole role = new Secuserrole(); role.setRoleName("doctor"); role.setProviderNo("900001");
        when(roles.findActiveByProviderNo("900001")).thenReturn(List.of(role));
        lenient().when(privileges.findByObjectNames(any())).thenReturn(List.of());
    }

    private SecObjPrivilege grant(String object, String role, String right, int priority) {
        SecObjPrivilege value = new SecObjPrivilege();
        value.setId(new SecObjPrivilegePrimaryKey(role, object)); value.setPrivilege(right); value.setPriority(priority);
        return value;
    }

    private void data(SecObjPrivilege... values) {
        when(privileges.findByObjectNames(any())).thenAnswer(call -> {
            Collection<String> names = call.getArgument(0);
            return Arrays.stream(values).filter(v -> names.contains(v.getId().getObjectName())).toList();
        });
    }

    @ParameterizedTest
    @CsvSource({"r,r,true", "r,w,false", "u,r,true", "u,u,true", "u,w,false", "w,r,true",
            "w,u,true", "w,w,true", "w,d,false", "d,d,true", "d,r,false", "x,r,true", "x,d,true", "o,r,false"})
    void decisionsAndHierarchyMatchExistingApi(String granted, String requested, boolean expected) {
        data(grant("_edoc$10", "doctor", granted, 0), grant("_edoc", "doctor", "x", 0));
        assertThat(security.hasPrivilegeReadOnly(info, "_edoc", requested, "10")).isEqualTo(expected);
        verifyNoInteractions(session); verify(info, never()).setSession(any());
        assertThat(security.hasPrivilege(info, "_edoc", requested, "10")).isEqualTo(expected);
        if ("o".equals(granted)) { verify(session).setAttribute("accountLocked", true); verify(info).setSession(session); }
        else verifyNoInteractions(session);
    }

    @Test void targetedDenialNeverMarksEvenAnAlreadyLockedSession() {
        data(grant("_edoc$10", "doctor", "o", 0), grant("_edoc", "doctor", "x", 0));
        assertThat(security.hasPrivilegeReadOnly(info, "_edoc", "r", "10")).isFalse();
        verify(info, never()).getSession(); verify(info, never()).setSession(any()); verifyNoInteractions(session);
    }

    @Test void unmatchedPatientRoleFallsBackToGlobalPolicy() {
        data(grant("_edoc$10", "nurse", "o", 1), grant("_edoc", "doctor", "r", 0));
        assertThat(security.hasPrivilegeReadOnly(info, "_edoc", "r", "10")).isTrue();
        assertThat(security.hasPrivilege(info, "_edoc", "r", "10")).isTrue();
        verifyNoInteractions(session);
    }

    @Test void nullPatientAndProviderOverridePreserveOriginalResolution() {
        data(grant("_edoc", "doctor", "r", 0), grant("_edoc$10", "900001", "o", 1));
        assertThat(security.hasPrivilegeReadOnly(info, "_edoc", "r", null)).isTrue();
        assertThat(security.hasPrivilege(info, "_edoc", "r", (String) null)).isTrue();
        assertThat(security.hasPrivilegeReadOnly(info, "_edoc", "r", "10")).isFalse();
        verifyNoInteractions(session);
    }

    @Test void positivePriorityStillStopsLowerPriorityGrant() {
        Secuserrole second = new Secuserrole(); second.setRoleName("nurse"); second.setProviderNo("900001");
        when(roles.findActiveByProviderNo("900001")).thenReturn(List.of(second, new Secuserrole() {{ setRoleName("doctor"); }}));
        data(grant("_edoc$10", "nurse", "r", 1), grant("_edoc$10", "doctor", "x", 0));
        assertThat(security.hasPrivilegeReadOnly(info, "_edoc", "w", "10")).isFalse();
        assertThat(security.hasPrivilege(info, "_edoc", "w", "10")).isFalse();
        assertThat(security.hasPrivilegeReadOnly(info, "_edoc", "r", "10")).isTrue();
        verifyNoInteractions(session);
    }

    @Test void zeroPriorityStillAllowsLaterRoleGrant() {
        Secuserrole second = new Secuserrole(); second.setRoleName("nurse"); second.setProviderNo("900001");
        when(roles.findActiveByProviderNo("900001")).thenReturn(List.of(second, new Secuserrole() {{ setRoleName("doctor"); }}));
        data(grant("_edoc$10", "nurse", "r", 0), grant("_edoc$10", "doctor", "x", 0));
        assertThat(security.hasPrivilegeReadOnly(info, "_edoc", "w", "10")).isTrue();
        assertThat(security.hasPrivilege(info, "_edoc", "w", "10")).isTrue();
        verifyNoInteractions(session);
    }

    private void documentWithOwners() {
        CtlDocumentDao links = mock(CtlDocumentDao.class);
        DocumentDao documents = mock(DocumentDao.class);
        registerMock(CtlDocumentDao.class, links); registerMock(DocumentDao.class, documents);
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        CtlDocument first = new CtlDocument(); first.setId(new CtlDocumentPK("demographic", 10, 42));
        CtlDocument second = new CtlDocument(); second.setId(new CtlDocumentPK("demographic", 11, 42));
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(first, second));
        when(documents.find(42)).thenReturn(new Document(42));
    }

    @Test void discoveryRetainsAllStoredOwnerDenialWithoutChangingByteGateSideEffect() {
        documentWithOwners();
        data(grant("_edoc", "doctor", "r", 0), grant("_edoc$11", "doctor", "o", 0));
        assertThatThrownBy(() -> IncomingDocumentCapacityResponse.requireStoredDocumentListReadAccess(security, info, 42))
                .isInstanceOf(SecurityException.class).hasMessageContaining("Patient document read");
        verifyNoInteractions(session); verify(info, never()).getSession();
        assertThatThrownBy(() -> IncomingDocumentCapacityResponse.requireStoredDocumentReadAccess(security, info, 42))
                .isInstanceOf(SecurityException.class).hasMessageContaining("Patient document read");
        verify(session).setAttribute("accountLocked", true); verify(info).setSession(session);
    }

    @Test void discoveryAndBytesBothAllowEveryStoredOwnerWhenAuthorized() {
        documentWithOwners(); data(grant("_edoc", "doctor", "r", 0));
        IncomingDocumentCapacityResponse.requireStoredDocumentListReadAccess(security, info, 42);
        IncomingDocumentCapacityResponse.requireStoredDocumentReadAccess(security, info, 42);
        verifyNoInteractions(session);
    }
}
