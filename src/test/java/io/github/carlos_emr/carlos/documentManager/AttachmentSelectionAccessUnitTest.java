// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("fast")
class AttachmentSelectionAccessUnitTest {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DocumentDao documents = mock(DocumentDao.class);
    private final EFormDataDao eforms = mock(EFormDataDao.class);
    private final HRMDocumentToDemographicDao hrms = mock(HRMDocumentToDemographicDao.class);
    private final FormsManager forms = mock(FormsManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private AttachmentSelectionAccess access;

    @BeforeEach
    void setUp() {
        access = new AttachmentSelectionAccess(security, documents, eforms, hrms, forms);
        when(security.hasPrivilege(eq(info), anyString(), eq(SecurityInfoManager.READ), eq("42"))).thenReturn(true);
    }

    private void owner(DocumentType type, int patient) {
        switch (type) {
            case DOC -> {
                CtlDocumentPK key = new CtlDocumentPK();
                key.setModule("demographic");
                key.setModuleId(patient);
                CtlDocument link = new CtlDocument();
                link.setId(key);
                when(documents.findCtlDocsAndDocsByDocNo(7)).thenReturn(java.util.Collections.singletonList(new Object[]{new io.github.carlos_emr.carlos.commn.model.Document(), link}));
            }
            case EFORM -> {
                EFormData form = new EFormData();
                form.setDemographicId(patient);
                when(eforms.find(7)).thenReturn(form);
            }
            case HRM -> {
                HRMDocumentToDemographic link = new HRMDocumentToDemographic();
                link.setDemographicNo(patient);
                when(hrms.findByHrmDocumentId(7)).thenReturn(List.of(link));
            }
            case FORM -> when(forms.getEncounterFormsbyDemographicNumber(info, 42, true, false))
                    .thenReturn(List.of(new EctFormData.PatientForm("formAnnual", "Annual", 7, patient)));
            default -> throw new AssertionError(type);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectDeletedDocumentOrPatientLink_whenPreviouslyAttached(boolean documentDeleted) {
        var document = new io.github.carlos_emr.carlos.commn.model.Document();
        document.setStatus(documentDeleted ? 'D' : 'A');
        var link = new CtlDocument();
        link.setId(new CtlDocumentPK("demographic", 42, 7));
        link.setStatus(documentDeleted ? "A" : "D");
        when(documents.findCtlDocsAndDocsByDocNo(7))
                .thenReturn(java.util.Collections.singletonList(new Object[]{document, link}));
        assertThatThrownBy(() -> access.validate(info, DocumentType.DOC, 42, List.of("7"), List.of("7")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"DOC", "EFORM", "HRM", "FORM"})
    void acceptsOwnedItems(DocumentType type) {
        owner(type, 42);
        assertThat(access.validate(info, type, 42, List.of("7"), List.of())).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"DOC", "EFORM", "HRM", "FORM"})
    void rejectsForeignItemsEvenWhenPreviouslyAttached(DocumentType type) {
        owner(type, 99);
        assertThatThrownBy(() -> access.validate(info, type, 42, List.of("7"), List.of("7")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("patient");
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"DOC", "LAB", "EFORM", "HRM", "FORM"})
    void preservesUnchangedRestrictedItemsButRejectsChanges(DocumentType type) {
        when(security.hasPrivilege(eq(info), anyString(), eq(SecurityInfoManager.READ), eq("42"))).thenReturn(false);
        assertThat(access.validate(info, type, 42, List.of("7"), List.of("7"))).isFalse();
        assertThatThrownBy(() -> access.validate(info, type, 42, List.of("8"), List.of("7")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> access.validate(info, type, 42, List.of(), List.of("7")))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(documents, eforms, hrms, forms);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "7x", "2147483648", "", "7 OR 1=1"})
    void rejectsMalformedIdsBeforeLookup(String id) {
        assertThatThrownBy(() -> access.validate(info, DocumentType.DOC, 42, List.of(id), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(documents);
    }

    @Test
    void allowsLabSourceValidationToRunInTheWriter() {
        assertThat(access.validate(info, DocumentType.LAB, 42, List.of("HL7:7"), List.of())).isTrue();
        verifyNoInteractions(documents, eforms, hrms, forms);
    }
    @Test
    void shouldUseConfiguredDocumentDao_whenLegacyAndMergedPatientBeansBothExist() {
        var unusedLegacy = mock(DocumentDao.class);
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("documentDaoImpl", unusedLegacy);
            context.getBeanFactory().registerSingleton("documentDao", documents);
            context.getBeanFactory().registerSingleton("securityInfoManager", security);
            context.getBeanFactory().registerSingleton("eFormDataDao", eforms);
            context.getBeanFactory().registerSingleton("hrmDocumentToDemographicDao", hrms);
            context.getBeanFactory().registerSingleton("formsManager", forms);
            context.register(AttachmentSelectionAccess.class);
            context.refresh();
            owner(DocumentType.DOC, 42);
            assertThat(context.getBean(AttachmentSelectionAccess.class)
                    .validate(info, DocumentType.DOC, 42, List.of("7"), List.of())).isTrue();
            verify(documents).findCtlDocsAndDocsByDocNo(7);
            verifyNoInteractions(unusedLegacy);
        }
    }

}
