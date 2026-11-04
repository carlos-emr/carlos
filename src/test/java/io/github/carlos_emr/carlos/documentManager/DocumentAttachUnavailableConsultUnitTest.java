/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Preservation of omitted consultation attachments under the parent lock.
 *
 * @since 2026-11-04
 */
@Tag("unit")
class DocumentAttachUnavailableConsultUnitTest extends CarlosUnitTestBase {
    private ConsultDocsDao docs;
    private ConsultationManager manager;
    private SecurityInfoManager security;
    private LoggedInInfo info;
    private DocumentAttach attach;

    @BeforeEach
    void setUp() {
        docs = createAndRegisterMock(ConsultDocsDao.class);
        createAndRegisterMock(EFormDocsDao.class);
        manager = createAndRegisterMock(ConsultationManager.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        info = mock(LoggedInInfo.class);
        when(security.hasPrivilege(eq(info), anyString(), eq("r"), eq("1"))).thenReturn(true);
        registerMock(AttachmentSelectionAccess.class, new AttachmentSelectionAccess(security,
                mock(DocumentDao.class), mock(EFormDataDao.class), mock(HRMDocumentToDemographicDao.class), mock(FormsManager.class)));
        var transactions = createAndRegisterMock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var parent = new ConsultationRequest();
        parent.setDemographicId(1);
        var parents = createAndRegisterMock(ConsultationRequestDao.class);
        lenient().when(parents.find((Object) 42)).thenReturn(parent);
        lenient().when(parents.lockForAttachmentSync(42)).thenReturn(parent);
        attach = new DocumentAttach(info, 1, false);
    }

    private ConsultDocs stored(DocumentType type) {
        var row = new ConsultDocs(42, 7, type.getType(), "999998");
        lenient().when(docs.findByRequestIdDocType(42, type.getType())).thenReturn(List.of(row));
        when(docs.findByRequestIdDocTypeForUpdate(42, type.getType())).thenReturn(List.of(row));
        return row;
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"FORM", "HRM"})
    void shouldRetainUnavailableAttachments_whenPickerOmitsThem(DocumentType type) {
        stored(type);
        attach.verifyConsultAttachments(new String[0], type, 42);
        attach.attachToConsult(new String[0], type, "999998", 42);
        verify(docs, never()).merge(any());
        verify(docs, never()).persist(any());
    }

    @Test
    void shouldPreserveHiddenHrms_whenUserHasNoReadPrivilege() {
        stored(DocumentType.HRM);
        when(security.hasPrivilege(info, "_hrm", "r", "1")).thenReturn(false);
        attach.verifyConsultAttachments(new String[0], DocumentType.HRM, 42);
        attach.attachToConsult(new String[0], DocumentType.HRM, "999998", 42);
        verify(docs, never()).merge(any());
        verify(docs, never()).persist(any());
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"FORM", "HRM"})
    void shouldDetachVisibleAttachments_whenUserDeliberatelyRemovesThem(DocumentType type) {
        var row = stored(type);
        if (type == DocumentType.FORM) {
            when(manager.getAttachedForms(info, 42, 1))
                    .thenReturn(List.of(new EctFormData.PatientForm("formAnnual", "Annual", 7, 1)));
        } else {
            HashMap<String, Object> hrm = new HashMap<>();
            hrm.put("id", 7);
            when(manager.getAttachedHRMDocuments(info, "1", "42"))
                    .thenReturn(new ArrayList<>(List.of(hrm)));
        }
        when(docs.findByRequestIdDocNoDocType(42, 7, type.getType())).thenReturn(List.of(row));
        attach.attachToConsult(new String[0], type, "999998", 42);
        verify(docs).merge(row);
        assertThat(row.getDeleted()).isEqualTo("Y");
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"FORM", "HRM"})
    void shouldAvoidAvailabilityLookup_whenSelectionIsUnchanged(DocumentType type) {
        stored(type);
        attach.verifyConsultAttachments(new String[]{"7"}, type, 42);
        attach.attachToConsult(new String[]{"7"}, type, "999998", 42);
        verifyNoInteractions(manager);
        verify(docs, never()).merge(any());
        verify(docs, never()).persist(any());
    }

    @Test
    void shouldRetainFormsAndRejectAdditions_whenFormReadIsDenied() {
        stored(DocumentType.FORM);
        when(security.hasPrivilege(info, "_form", "r", "1")).thenReturn(false);
        attach.verifyConsultAttachments(new String[0], DocumentType.FORM, 42);
        attach.attachToConsult(new String[0], DocumentType.FORM, "999998", 42);
        assertThatThrownBy(() -> attach.verifyConsultAttachments(new String[]{"8"}, DocumentType.FORM, 42))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(manager);
        verify(docs, never()).merge(any());
        verify(docs, never()).persist(any());
    }

    @Test
    void shouldRejectNewHrm_whenRegionDoesNotSupportIt() {
        stored(DocumentType.HRM);
        try (var properties = mockStatic(CarlosProperties.class)) {
            var settings = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(settings);
            when(settings.isOntarioBillingRegion()).thenReturn(false);
            assertThatThrownBy(() -> attach.verifyConsultAttachments(new String[]{"8"}, DocumentType.HRM, 42))
                    .isInstanceOf(SecurityException.class);
            verify(docs, never()).merge(any());
            verify(docs, never()).persist(any());
        }
    }
}
