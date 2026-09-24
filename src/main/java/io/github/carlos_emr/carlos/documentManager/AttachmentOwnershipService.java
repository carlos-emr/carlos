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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Verifies that attachment ids and consultation requests supplied by a browser belong to the
 * patient a request claims to act on.
 *
 * <p><b>Why this exists.</b> Consultation and Ocean eReferral attachment endpoints receive bare ids
 * ({@code D123}, {@code L45}, ...) plus a {@code demographicNo}. Every downstream renderer resolves
 * an attachment by id alone, so without this check a crafted request could attach, and send to an
 * external referral service, another patient's documents, labs, eForms or HRM reports
 * (issue #3867). The {@code _con} privilege says the user may work on consultations; it says
 * nothing about whether an id belongs to the patient in the request.</p>
 *
 * <p><b>Ownership sources.</b></p>
 * <ul>
 *   <li>{@link DocumentType#DOC}: a {@code ctl_document} row with module {@code demographic} and
 *       module_id = patient, whose {@code document} row is not deleted ({@code document.status},
 *       which is what {@code EDocUtil.deleteDocument} sets).</li>
 *   <li>{@link DocumentType#LAB}: a {@code patientLabRouting} row of type
 *       {@link PatientLabRoutingDao#HL7} for the patient. Only HL7 labs are matched because the
 *       attachment picker lists HL7 segments and the lab renderer only renders HL7 segments; lab
 *       numbers from other routing types live in other tables and can collide. At attach time
 *       only, {@link #findAttachableIds} also accepts labs routed to the patient under a legacy
 *       type the install has switched on (see there).</li>
 *   <li>{@link DocumentType#EFORM}: {@code eform_data.demographic_no}.</li>
 *   <li>{@link DocumentType#HRM}: an {@code HRMDocumentToDemographic} link to the patient.</li>
 * </ul>
 *
 * <p>{@link DocumentType#FORM} ids point into per-form tables with no common owner column and are
 * reported as not verifiable; callers decide how to handle them. Every method fails closed: a
 * {@code null} patient, an unverifiable type or an id the lookup cannot see counts as not owned.</p>
 *
 * <p>This is cross-DAO read orchestration, so it lives in a service; each lookup stays in its own
 * DAO. It has no side effects and never logs ids.</p>
 *
 * @since 2026-09-24
 */
@Service
public class AttachmentOwnershipService {

    private static final Set<DocumentType> VERIFIABLE_TYPES =
            Collections.unmodifiableSet(EnumSet.of(DocumentType.DOC, DocumentType.LAB, DocumentType.EFORM, DocumentType.HRM));

    private final CtlDocumentDao ctlDocumentDao;
    private final PatientLabRoutingDao patientLabRoutingDao;
    private final EFormDataDao eFormDataDao;
    private final HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    private final ConsultationRequestDao consultationRequestDao;
    private final Supplier<Set<String>> enabledLegacyLabTypes;

    @Autowired
    public AttachmentOwnershipService(CtlDocumentDao ctlDocumentDao,
                                      PatientLabRoutingDao patientLabRoutingDao,
                                      EFormDataDao eFormDataDao,
                                      HRMDocumentToDemographicDao hrmDocumentToDemographicDao,
                                      ConsultationRequestDao consultationRequestDao) {
        this.ctlDocumentDao = ctlDocumentDao;
        this.patientLabRoutingDao = patientLabRoutingDao;
        this.eFormDataDao = eFormDataDao;
        this.hrmDocumentToDemographicDao = hrmDocumentToDemographicDao;
        this.consultationRequestDao = consultationRequestDao;
        this.enabledLegacyLabTypes = AttachmentOwnershipService::legacyLabTypesFromProperties;
    }

    /** Test constructor: fixes the legacy lab types instead of reading carlos.properties. */
    AttachmentOwnershipService(CtlDocumentDao ctlDocumentDao,
                               PatientLabRoutingDao patientLabRoutingDao,
                               EFormDataDao eFormDataDao,
                               HRMDocumentToDemographicDao hrmDocumentToDemographicDao,
                               ConsultationRequestDao consultationRequestDao,
                               Supplier<Set<String>> enabledLegacyLabTypes) {
        this.ctlDocumentDao = ctlDocumentDao;
        this.patientLabRoutingDao = patientLabRoutingDao;
        this.eFormDataDao = eFormDataDao;
        this.hrmDocumentToDemographicDao = hrmDocumentToDemographicDao;
        this.consultationRequestDao = consultationRequestDao;
        this.enabledLegacyLabTypes = enabledLegacyLabTypes;
    }

    /**
     * The non-HL7 lab routing types whose labs the consultation form lists, mirroring
     * {@code CommonLabResultData}: CML_LABS and Epsilon_LABS list CML-routed labs, MDS_LABS lists
     * MDS, PATHNET_LABS lists BCP. All are off unless set to {@code yes}.
     */
    private static Set<String> legacyLabTypesFromProperties() {
        CarlosProperties properties = CarlosProperties.getInstance();
        Set<String> types = new HashSet<>();
        if (isYes(properties.getProperty("CML_LABS")) || isYes(properties.getProperty("Epsilon_LABS"))) {
            types.add("CML");
        }
        if (isYes(properties.getProperty("MDS_LABS"))) {
            types.add("MDS");
        }
        if (isYes(properties.getProperty("PATHNET_LABS"))) {
            types.add("BCP");
        }
        return types;
    }

    private static boolean isYes(String value) {
        return value != null && value.trim().equals("yes");
    }

    /**
     * Whether ownership of this attachment type can be checked at all.
     *
     * @param type attachment type
     * @return {@code true} for DOC, LAB, EFORM and HRM
     */
    public static boolean isVerifiable(DocumentType type) {
        return type != null && VERIFIABLE_TYPES.contains(type);
    }

    /**
     * Returns the subset of {@code ids} of the given type that belong to the patient.
     *
     * @param type attachment type; an unverifiable type yields an empty set
     * @param demographicNo patient that must own the attachments; {@code null} yields an empty set
     * @param ids candidate ids; {@code null} elements are ignored
     * @return owned ids; never {@code null}
     */
    public Set<Integer> findOwnedIds(DocumentType type, Integer demographicNo, Collection<Integer> ids) {
        if (!isVerifiable(type) || demographicNo == null || ids == null) {
            return Collections.emptySet();
        }
        Set<Integer> candidates = new HashSet<>();
        for (Integer id : ids) {
            if (id != null) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            return Collections.emptySet();
        }

        List<Integer> owned;
        switch (type) {
            case DOC:
                owned = ctlDocumentDao.findDocumentNosForDemographic(demographicNo, candidates);
                break;
            case LAB:
                owned = patientLabRoutingDao.findLabNosForDemographic(demographicNo, PatientLabRoutingDao.HL7, candidates);
                break;
            case EFORM:
                owned = eFormDataDao.findFdidsForDemographic(demographicNo, candidates);
                break;
            case HRM:
                owned = hrmDocumentToDemographicDao.findHrmIdsForDemographic(demographicNo, candidates);
                break;
            default:
                return Collections.emptySet();
        }

        // Intersect with the request: the result must never widen what the caller asked about.
        Set<Integer> result = new HashSet<>(candidates);
        result.retainAll(owned == null ? Collections.emptyList() : owned);
        return result;
    }

    /**
     * Keeps the attachments whose id belongs to the patient, in their original order, with one
     * batched {@link #findOwnedIds} lookup. This is the rendering-side filter: foreign, deleted and
     * unparseable ids are dropped, and for {@link DocumentType#LAB} only HL7 labs are kept because
     * the lab renderer resolves every LAB id as an HL7 segment.
     *
     * @param type attachment type
     * @param demographicNo patient that must own the attachments; {@code null} keeps nothing
     * @param attachments attachments as loaded for a consultation; {@code null} yields an empty list
     * @param idOf extracts the attachment id as text
     * @param <T> attachment representation
     * @return the owned attachments; never {@code null}
     */
    public <T> List<T> retainOwned(DocumentType type, Integer demographicNo, List<T> attachments,
                                   Function<T, String> idOf) {
        if (attachments == null || attachments.isEmpty()) {
            return new ArrayList<>();
        }
        Map<T, Integer> idByAttachment = new IdentityHashMap<>();
        for (T attachment : attachments) {
            Integer id = parseId(attachment == null ? null : idOf.apply(attachment));
            if (id != null) {
                idByAttachment.put(attachment, id);
            }
        }
        Set<Integer> owned = findOwnedIds(type, demographicNo, idByAttachment.values());
        List<T> retained = new ArrayList<>(attachments.size());
        for (T attachment : attachments) {
            Integer id = attachment == null ? null : idByAttachment.get(attachment);
            if (id != null && owned.contains(id)) {
                retained.add(attachment);
            }
        }
        return retained;
    }

    private static Integer parseId(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The ids a consultation may <em>keep or newly attach</em>: {@link #findOwnedIds}, plus, for
     * {@link DocumentType#LAB}, labs routed to the patient under a legacy lab type the install has
     * switched on (CML, MDS, BCP).
     *
     * <p>The consultation form lists those labs when their property is on, so rejecting them would
     * refuse a save the user made from the form, and an already-attached one would be detached on
     * every save. They are accepted here only. Printing, faxing and the Ocean queue keep using
     * {@link #findOwnedIds}: the lab renderer resolves every LAB id as an HL7 segment, so an
     * HL7 lab of another patient whose number collides with this patient's CML/MDS lab is still
     * never rendered for this consultation.</p>
     *
     * @param type attachment type
     * @param demographicNo patient that must own the attachments
     * @param ids candidate ids
     * @return attachable ids; never {@code null}
     */
    public Set<Integer> findAttachableIds(DocumentType type, Integer demographicNo, Collection<Integer> ids) {
        Set<Integer> result = new HashSet<>(findOwnedIds(type, demographicNo, ids));
        if (type != DocumentType.LAB || demographicNo == null || ids == null) {
            return result;
        }
        Set<Integer> remaining = new HashSet<>();
        for (Integer id : ids) {
            if (id != null && !result.contains(id)) {
                remaining.add(id);
            }
        }
        for (String labType : enabledLegacyLabTypes.get()) {
            if (remaining.isEmpty()) {
                break;
            }
            List<Integer> owned = patientLabRoutingDao.findLabNosForDemographic(demographicNo, labType, remaining);
            if (owned != null) {
                for (Integer id : owned) {
                    if (remaining.remove(id)) {
                        result.add(id);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Whether every id of the given type belongs to the patient.
     *
     * <p>An empty (or {@code null}) id collection is vacuously owned: there is nothing to
     * disclose. A {@code null} element, an unverifiable type with at least one id, or a
     * {@code null} patient with at least one id is not owned.</p>
     *
     * @param type attachment type
     * @param demographicNo patient that must own the attachments
     * @param ids candidate ids
     * @return {@code true} only when every id is owned by the patient
     */
    public boolean allBelongToDemographic(DocumentType type, Integer demographicNo, Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        return findOwnedIds(type, demographicNo, ids).containsAll(ids);
    }

    /**
     * Whether every id in every group belongs to the patient. Stops at the first failing type.
     *
     * @param idsByType candidate ids grouped by attachment type
     * @param demographicNo patient that must own the attachments
     * @return {@code true} only when every id of every type is owned by the patient
     */
    public boolean allBelongToDemographic(Map<DocumentType, ? extends Collection<Integer>> idsByType, Integer demographicNo) {
        if (idsByType == null) {
            return true;
        }
        for (Map.Entry<DocumentType, ? extends Collection<Integer>> entry : idsByType.entrySet()) {
            if (!allBelongToDemographic(entry.getKey(), demographicNo, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the consultation request exists and was written for the patient.
     *
     * @param requestId consultation request id
     * @param demographicNo patient the caller claims the request belongs to
     * @return {@code true} only when the stored request's demographic equals {@code demographicNo}
     */
    public boolean consultationRequestBelongsToDemographic(Integer requestId, Integer demographicNo) {
        if (requestId == null || demographicNo == null) {
            return false;
        }
        ConsultationRequest consultationRequest = consultationRequestDao.find(requestId.intValue());
        return consultationRequest != null && demographicNo.equals(consultationRequest.getDemographicId());
    }
}
