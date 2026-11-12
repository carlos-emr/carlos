/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class DocumentListAccessUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final CtlDocumentDao links = mock(CtlDocumentDao.class);
    private final DocumentDao documents = mock(DocumentDao.class);
    private final PatientLabRoutingDao routes = mock(PatientLabRoutingDao.class);

    @BeforeEach void prepare() {
        registerMock(CtlDocumentDao.class, links);
        registerMock(DocumentDao.class, documents);
        registerMock(PatientLabRoutingDao.class, routes);
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        when(security.hasPrivilege(eq(user), eq("_edoc"), eq("r"), nullable(String.class))).thenReturn(true);
        when(security.hasPrivilegeReadOnly(eq(user), eq("_edoc"), eq("r"), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 10)).thenReturn(true);
        when(documents.find(42)).thenReturn(new Document(42));
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link(10)));
    }

    private CtlDocument link(int patient) {
        CtlDocument value = new CtlDocument(); value.setId(new CtlDocumentPK("demographic", patient, 42)); return value;
    }
    private EDoc doc(String number) { EDoc value = new EDoc(); value.setDocId(number); return value; }

    @Test void chartReadDoesNotGrantDocumentDomainRead() {
        when(security.hasPrivilege(user, "_edoc", "r", (String) null)).thenReturn(false);
        assertThat(DocumentListAccess.mayReadPatient(user, security, "10")).isFalse();
        assertThat(DocumentListAccess.readableDocuments(user, security, List.of(doc("42")))).isEmpty();
        verifyNoInteractions(documents, links, routes);
    }

    @Test void targetedDocumentDenialUsesReadOnlyEvaluationForDiscovery() {
        when(security.hasPrivilegeReadOnly(user, "_edoc", "r", "10")).thenReturn(false);
        assertThat(DocumentListAccess.mayReadPatient(user, security, "10")).isFalse();
        assertThat(DocumentListAccess.mayReadDocument(user, security, 42)).isFalse();
        verify(security, never()).hasPrivilege(user, "_edoc", "r", "10");
    }

    @Test void openPatientAndStoredOwnerRemainVisible() {
        EDoc value = doc("42");
        assertThat(DocumentListAccess.mayReadPatient(user, security, "10")).isTrue();
        assertThat(DocumentListAccess.readableDocuments(user, security, List.of(value))).containsExactly(value);
    }

    @Test void lockedPatientIsNotAReadableDocumentContext() {
        when(security.isAllowedAccessToPatientRecord(user, 10)).thenReturn(false);
        assertThat(DocumentListAccess.mayReadPatient(user, security, "10")).isFalse();
        assertThat(DocumentListAccess.readableDocuments(user, security, List.of(doc("42")))).isEmpty();
    }

    @Test void everyActualOwnerMustAllowReadEvenWhenFirstOwnerIsOpen() {
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link(10), link(11)));
        assertThat(DocumentListAccess.mayReadDocument(user, security, 42)).isFalse();
        verify(security).isAllowedAccessToPatientRecord(user, 11);
    }

    @Test void routedLockedOwnerIsNotBypassedByAnOpenFiledOwner() {
        PatientLabRouting route = new PatientLabRouting(); route.setDemographicNo(11);
        when(routes.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(route));
        assertThat(DocumentListAccess.mayReadDocument(user, security, 42)).isFalse();
    }

    @Test void missingStoredDocumentIsNotPublic() {
        when(documents.find(42)).thenReturn(null);
        assertThat(DocumentListAccess.mayReadDocument(user, security, 42)).isFalse();
    }

    @Test void infrastructureFailureIsNotSilentlyTreatedAsAccessDenial() {
        when(links.findByDocumentNoAndModule(42, "demographic")).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> DocumentListAccess.mayReadDocument(user, security, 42))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(strings={"0", "-1", "2147483648", "no-patient"})
    void malformedPatientsDoNotBecomeAReadableContext(String patient) {
        assertThat(DocumentListAccess.mayReadPatient(user, security, patient)).isFalse();
    }
}
