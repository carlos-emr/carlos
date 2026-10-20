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

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AttachmentOwnershipService}, the server-side ownership gate for
 * consultation and Ocean eReferral attachments (issue #3867).
 *
 * @since 2026-09-24
 */
@DisplayName("AttachmentOwnershipService")
@Tag("unit")
@Tag("security")
@Tag("documentManager")
class AttachmentOwnershipServiceUnitTest {

    private static final int PATIENT = 1001;
    private static final int OTHER_PATIENT = 2002;

    private CtlDocumentDao ctlDocumentDao;
    private PatientLabRoutingDao patientLabRoutingDao;
    private EFormDataDao eFormDataDao;
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    private ConsultationRequestDao consultationRequestDao;
    private AttachmentOwnershipService service;

    @BeforeEach
    void setUp() {
        ctlDocumentDao = mock(CtlDocumentDao.class);
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        eFormDataDao = mock(EFormDataDao.class);
        hrmDocumentToDemographicDao = mock(HRMDocumentToDemographicDao.class);
        consultationRequestDao = mock(ConsultationRequestDao.class);
        service = new AttachmentOwnershipService(ctlDocumentDao, patientLabRoutingDao, eFormDataDao,
                hrmDocumentToDemographicDao, consultationRequestDao);
    }

    @Nested
    @DisplayName("allBelongToDemographic")
    class AllBelong {

        @Test
        @DisplayName("should accept documents that all belong to the patient")
        void shouldReturnTrue_whenAllDocumentsOwned() {
            when(ctlDocumentDao.findDocumentNosForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of(10, 11));

            assertThat(service.allBelongToDemographic(DocumentType.DOC, PATIENT, List.of(10, 11))).isTrue();
        }

        @Test
        @DisplayName("should reject a document that belongs to another patient")
        void shouldReturnFalse_whenDocumentForeign() {
            when(ctlDocumentDao.findDocumentNosForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of());

            assertThat(service.allBelongToDemographic(DocumentType.DOC, PATIENT, List.of(999))).isFalse();
        }

        @Test
        @DisplayName("should reject a mixed list of own and foreign ids")
        void shouldReturnFalse_whenOwnAndForeignIdsMixed() {
            when(eFormDataDao.findFdidsForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of(30));

            assertThat(service.allBelongToDemographic(DocumentType.EFORM, PATIENT, List.of(30, 31))).isFalse();
        }

        @Test
        @DisplayName("should accept duplicate ids that are owned")
        void shouldReturnTrue_forDuplicateOwnedIds() {
            when(hrmDocumentToDemographicDao.findHrmIdsForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of(40));

            assertThat(service.allBelongToDemographic(DocumentType.HRM, PATIENT, List.of(40, 40))).isTrue();
        }

        @Test
        @DisplayName("should treat an empty list as owned without querying")
        void shouldReturnTrue_forEmptyList() {
            assertThat(service.allBelongToDemographic(DocumentType.DOC, PATIENT, Collections.emptyList())).isTrue();
            assertThat(service.allBelongToDemographic(DocumentType.DOC, PATIENT, null)).isTrue();
            verifyNoInteractions(ctlDocumentDao, patientLabRoutingDao, eFormDataDao, hrmDocumentToDemographicDao);
        }

        @Test
        @DisplayName("should reject when the patient is missing")
        void shouldReturnFalse_whenDemographicNull() {
            assertThat(service.allBelongToDemographic(DocumentType.DOC, null, List.of(10))).isFalse();
            verifyNoInteractions(ctlDocumentDao);
        }

        @Test
        @DisplayName("should reject a null id")
        void shouldReturnFalse_forNullId() {
            assertThat(service.allBelongToDemographic(DocumentType.DOC, PATIENT, Arrays.asList(10, null))).isFalse();
        }

        @Test
        @DisplayName("should reject form ids because they cannot be verified")
        void shouldReturnFalse_forUnverifiableFormType() {
            assertThat(service.allBelongToDemographic(DocumentType.FORM, PATIENT, List.of(5))).isFalse();
            assertThat(AttachmentOwnershipService.isVerifiable(DocumentType.FORM)).isFalse();
        }

        @Test
        @DisplayName("should check labs against HL7 routings only")
        void shouldQueryHl7Routing_forLabIds() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of(20));

            assertThat(service.allBelongToDemographic(DocumentType.LAB, PATIENT, List.of(20))).isTrue();
            verify(patientLabRoutingDao).findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection());
        }

        @Test
        @DisplayName("should reject the grouped request when any type has a foreign id")
        void shouldReturnFalse_whenAnyTypeInGroupForeign() {
            when(ctlDocumentDao.findDocumentNosForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of(10));
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), any(), anyCollection())).thenReturn(List.of());
            Map<DocumentType, Set<Integer>> grouped = new EnumMap<>(DocumentType.class);
            grouped.put(DocumentType.DOC, Set.of(10));
            grouped.put(DocumentType.LAB, Set.of(20));

            assertThat(service.allBelongToDemographic(grouped, PATIENT)).isFalse();
        }

        @Test
        @DisplayName("should accept the grouped request when every type is owned")
        void shouldReturnTrue_whenEveryTypeInGroupOwned() {
            when(ctlDocumentDao.findDocumentNosForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of(10));
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), any(), anyCollection())).thenReturn(List.of(20));
            Map<DocumentType, Set<Integer>> grouped = new EnumMap<>(DocumentType.class);
            grouped.put(DocumentType.DOC, Set.of(10));
            grouped.put(DocumentType.LAB, Set.of(20));

            assertThat(service.allBelongToDemographic(grouped, PATIENT)).isTrue();
        }
    }

    @Nested
    @DisplayName("findOwnedIds")
    class FindOwnedIds {

        @Test
        @DisplayName("should never return ids the caller did not ask about")
        void shouldIntersectWithRequest_whenDaoReturnsExtraIds() {
            when(ctlDocumentDao.findDocumentNosForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of(10, 77));

            assertThat(service.findOwnedIds(DocumentType.DOC, PATIENT, List.of(10, 11))).containsExactly(10);
        }
    }

    @Nested
    @DisplayName("retainOwned")
    class RetainOwned {

        @Test
        @DisplayName("should keep only the patient's attachments in their original order")
        void shouldKeepOwnedAttachmentsInOrder_whenForeignAndMalformedIdsMixed() {
            when(ctlDocumentDao.findDocumentNosForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of(12, 10));

            List<String> retained = service.retainOwned(DocumentType.DOC, PATIENT,
                    Arrays.asList("12", "11", "abc", null, "10"), id -> id);

            assertThat(retained).containsExactly("12", "10");
        }

        @Test
        @DisplayName("should keep a CML lab out of rendering even when CML labs are attachable")
        void shouldDropLegacyLab_forRendering() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of(20));
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq("CML"), anyCollection()))
                    .thenReturn(List.of(30));

            AttachmentOwnershipService cmlEnabled = new AttachmentOwnershipService(ctlDocumentDao, patientLabRoutingDao,
                    eFormDataDao, hrmDocumentToDemographicDao, consultationRequestDao, () -> Set.of("CML"));

            assertThat(cmlEnabled.retainOwned(DocumentType.LAB, PATIENT, List.of("20", "30"), id -> id))
                    .containsExactly("20");
        }

        @Test
        @DisplayName("should keep an enabled CML lab when listing attachable labs")
        void shouldKeepLegacyLab_forAttachableListing() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of(20));
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq("CML"), anyCollection()))
                    .thenReturn(List.of(30));
            AttachmentOwnershipService cmlEnabled = new AttachmentOwnershipService(ctlDocumentDao, patientLabRoutingDao,
                    eFormDataDao, hrmDocumentToDemographicDao, consultationRequestDao, () -> Set.of("CML"));

            assertThat(cmlEnabled.retainAttachable(DocumentType.LAB, PATIENT, List.of("30", "40", "20"), id -> id))
                    .containsExactly("30", "20");
        }

        @Test
        @DisplayName("should keep nothing when the patient is unknown")
        void shouldReturnEmptyList_whenDemographicNull() {
            assertThat(service.retainOwned(DocumentType.DOC, null, List.of("10"), id -> id)).isEmpty();
            assertThat(service.<String>retainOwned(DocumentType.DOC, PATIENT, null, id -> id)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAttachableIds")
    class FindAttachableIds {

        private AttachmentOwnershipService withLegacyLabTypes(Set<String> types) {
            return new AttachmentOwnershipService(ctlDocumentDao, patientLabRoutingDao, eFormDataDao,
                    hrmDocumentToDemographicDao, consultationRequestDao, () -> types);
        }

        @Test
        @DisplayName("should accept the patient's CML lab when CML labs are enabled")
        void shouldAcceptLegacyLab_whenLegacyLabTypeEnabled() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of(20));
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq("CML"), anyCollection()))
                    .thenReturn(List.of(30));

            assertThat(withLegacyLabTypes(Set.of("CML")).findAttachableIds(DocumentType.LAB, PATIENT, List.of(20, 30, 40)))
                    .containsExactlyInAnyOrder(20, 30);
        }

        @Test
        @DisplayName("should keep printing HL7-only when a CML lab is attachable")
        void shouldLeaveFindOwnedIdsHl7Only_whenLegacyLabTypeEnabled() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of());
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq("CML"), anyCollection()))
                    .thenReturn(List.of(30));

            assertThat(withLegacyLabTypes(Set.of("CML")).findOwnedIds(DocumentType.LAB, PATIENT, List.of(30))).isEmpty();
        }

        @Test
        @DisplayName("should not look at legacy lab types that are switched off")
        void shouldRejectLegacyLab_whenNoLegacyLabTypeEnabled() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of());
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq("CML"), anyCollection()))
                    .thenReturn(List.of(30));

            assertThat(withLegacyLabTypes(Set.of()).findAttachableIds(DocumentType.LAB, PATIENT, List.of(30))).isEmpty();
        }

        @Test
        @DisplayName("should never widen a non-lab type or the ids asked about")
        void shouldMatchFindOwnedIds_forNonLabTypes() {
            when(ctlDocumentDao.findDocumentNosForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of(10, 77));

            assertThat(withLegacyLabTypes(Set.of("CML")).findAttachableIds(DocumentType.DOC, PATIENT, List.of(10, 11)))
                    .containsExactly(10);
        }
    }

    /**
     * Lab identifier consistency: the renderers resolve every lab number as an HL7 segment and the
     * LAB ownership check matches on the number alone, so a listed non-HL7 lab must be dropped by
     * its type before that check, or a colliding HL7 lab of the patient would print instead.
     */
    @Nested
    @DisplayName("renderableLabsOnly")
    class RenderableLabsOnly {

        private LabResultData lab(boolean hl7) {
            LabResultData lab = mock(LabResultData.class);
            when(lab.isHL7TEXT()).thenReturn(hl7);
            return lab;
        }

        @Test
        @DisplayName("should keep HL7 labs in order and drop CML, MDS, BCP and null entries")
        void shouldKeepOnlyHl7Labs_forRendering() {
            LabResultData hl7First = lab(true);
            LabResultData cml = lab(false);
            LabResultData hl7Second = lab(true);

            assertThat(AttachmentOwnershipService.renderableLabsOnly(Arrays.asList(hl7First, cml, null, hl7Second)))
                    .containsExactly(hl7First, hl7Second);
        }

        @Test
        @DisplayName("should return an empty list for a null listing")
        void shouldReturnEmptyList_forNullListing() {
            assertThat(AttachmentOwnershipService.renderableLabsOnly(null)).isEmpty();
        }
    }

    /**
     * Pins how the lab properties map to {@code patient_lab_routing.lab_type} values, including
     * Epsilon: the consultation form lists CML routings for Epsilon_LABS, and Epsilon HL7 uploads
     * are routed as HL7, so no {@code Epsilon} lab type is ever queried.
     */
    @Nested
    @DisplayName("findOceanSendableIds")
    class FindOceanSendableIds {

        private AttachmentOwnershipService withLegacyLabTypes(Set<String> types) {
            return new AttachmentOwnershipService(ctlDocumentDao, patientLabRoutingDao, eFormDataDao,
                    hrmDocumentToDemographicDao, consultationRequestDao, () -> types);
        }

        @Test
        @DisplayName("should queue the patient's HL7 lab when no legacy lab shares its number")
        void shouldQueueHl7Lab_whenNoLegacyCollision() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of(20));
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq("CML"), anyCollection()))
                    .thenReturn(List.of());

            assertThat(withLegacyLabTypes(Set.of("CML")).findOceanSendableIds(DocumentType.LAB, PATIENT, List.of(20, 40)))
                    .containsExactly(20);
        }

        @Test
        @DisplayName("should not queue a number the patient holds as both an HL7 and an enabled legacy lab")
        void shouldNotQueueLab_whenLegacyLabSharesHl7Number() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of(20, 30));
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq("CML"), anyCollection()))
                    .thenReturn(List.of());
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq("MDS"), anyCollection()))
                    .thenReturn(List.of(30));

            assertThat(withLegacyLabTypes(Set.of("CML", "MDS")).findOceanSendableIds(DocumentType.LAB, PATIENT, List.of(20, 30)))
                    .containsExactly(20);
        }

        @Test
        @DisplayName("should not queue a legacy-only lab even though it is attachable")
        void shouldNotQueueLegacyLab_whenNotRoutedAsHl7() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of());
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq("CML"), anyCollection()))
                    .thenReturn(List.of(30));

            AttachmentOwnershipService service = withLegacyLabTypes(Set.of("CML"));
            assertThat(service.findAttachableIds(DocumentType.LAB, PATIENT, List.of(30))).containsExactly(30);
            assertThat(service.findOceanSendableIds(DocumentType.LAB, PATIENT, List.of(30))).isEmpty();
        }

        @Test
        @DisplayName("should ignore legacy lab types that are switched off")
        void shouldQueueHl7Lab_whenCollidingLegacyTypeDisabled() {
            when(patientLabRoutingDao.findLabNosForDemographic(eq(PATIENT), eq(PatientLabRoutingDao.HL7), anyCollection()))
                    .thenReturn(List.of(30));

            assertThat(withLegacyLabTypes(Set.of()).findOceanSendableIds(DocumentType.LAB, PATIENT, List.of(30)))
                    .containsExactly(30);
            verify(patientLabRoutingDao, org.mockito.Mockito.never())
                    .findLabNosForDemographic(eq(PATIENT), eq("CML"), anyCollection());
        }

        @Test
        @DisplayName("should match findOwnedIds for non-lab types")
        void shouldMatchFindOwnedIds_forNonLabTypes() {
            when(ctlDocumentDao.findDocumentNosForDemographic(eq(PATIENT), anyCollection())).thenReturn(List.of(10));

            assertThat(withLegacyLabTypes(Set.of("CML")).findOceanSendableIds(DocumentType.DOC, PATIENT, List.of(10, 11)))
                    .containsExactly(10);
            verifyNoInteractions(patientLabRoutingDao);
        }
    }

    @Nested
    @DisplayName("legacyLabTypes")
    class LegacyLabTypes {

        private java.util.Properties properties(String... keyValues) {
            java.util.Properties properties = new java.util.Properties();
            for (int i = 0; i < keyValues.length; i += 2) {
                properties.setProperty(keyValues[i], keyValues[i + 1]);
            }
            return properties;
        }

        @Test
        @DisplayName("should map Epsilon labs to CML routings and never to an Epsilon lab type")
        void shouldMapEpsilonToCmlRouting_whenEpsilonLabsEnabled() {
            assertThat(AttachmentOwnershipService.legacyLabTypes(properties("Epsilon_LABS", "yes")))
                    .containsExactly("CML")
                    .doesNotContain("Epsilon", "EPSILON", PatientLabRoutingDao.HL7);
        }

        @Test
        @DisplayName("should map each enabled lab property to its routing type")
        void shouldMapEachLabProperty_toItsRoutingType() {
            assertThat(AttachmentOwnershipService.legacyLabTypes(properties(
                    "CML_LABS", "yes", "MDS_LABS", " yes ", "PATHNET_LABS", "yes", "Epsilon_LABS", "yes")))
                    .containsExactlyInAnyOrder("CML", "MDS", "BCP");
        }

        @Test
        @DisplayName("should enable no legacy lab type unless a property is set to yes")
        void shouldReturnEmpty_whenNoLabPropertyIsYes() {
            assertThat(AttachmentOwnershipService.legacyLabTypes(properties(
                    "CML_LABS", "no", "Epsilon_LABS", "true", "MDS_LABS", ""))).isEmpty();
        }
    }

    @Nested
    @DisplayName("consultationRequestBelongsToDemographic")
    class ConsultationOwnership {

        @Test
        @DisplayName("should accept a consultation written for the patient")
        void shouldReturnTrue_whenConsultationBelongsToPatient() {
            ConsultationRequest consultation = new ConsultationRequest();
            consultation.setDemographicId(PATIENT);
            when(consultationRequestDao.find(55)).thenReturn(consultation);

            assertThat(service.consultationRequestBelongsToDemographic(55, PATIENT)).isTrue();
        }

        @Test
        @DisplayName("should reject a consultation written for another patient")
        void shouldReturnFalse_whenRequestIdBelongsToAnotherPatient() {
            ConsultationRequest consultation = new ConsultationRequest();
            consultation.setDemographicId(OTHER_PATIENT);
            when(consultationRequestDao.find(55)).thenReturn(consultation);

            assertThat(service.consultationRequestBelongsToDemographic(55, PATIENT)).isFalse();
        }

        @Test
        @DisplayName("should reject an unknown consultation")
        void shouldReturnFalse_whenConsultationUnknown() {
            when(consultationRequestDao.find(55)).thenReturn(null);

            assertThat(service.consultationRequestBelongsToDemographic(55, PATIENT)).isFalse();
            assertThat(service.consultationRequestBelongsToDemographic(null, PATIENT)).isFalse();
        }
    }
}
