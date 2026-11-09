/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;

import java.util.List;
import java.util.ArrayList;

import jakarta.persistence.Query;

import org.springframework.stereotype.Repository;

@Repository
public class DigitalSignatureDaoImpl extends AbstractDaoImpl<DigitalSignature> implements DigitalSignatureDao {

    private static final String EFORM_SIGNATURE_FIELD_NAME = "signatureValue";
    private static final String DIGITAL_SIGNATURE_ID_PARAM = "digitalSignatureId=";
    // The PDF composer can render a stored signature from signatureValue or
    // from the Rich Text Letter's stored Letter HTML, but not arbitrary fields.
    private static final String EFORM_REFERENCE_PREDICATE =
            "(ev.varValue LIKE ?2 OR ev.varValue LIKE ?3 OR ev.varValue LIKE ?4 "
                    + "OR ev.varValue LIKE ?5 OR ev.varValue LIKE ?6 OR ev.varValue LIKE ?7)";

    public DigitalSignatureDaoImpl() {
        super(DigitalSignature.class);
    }

    /**
     * Loads authorization metadata for a stored signature without fetching the signature image.
     *
     * <p>Current records store both demographic and module metadata directly. Legacy records can
     * have only a demographic id, so this method infers the missing module by checking, in order,
     * consultation request, archived consultation request, consultation response, prescription, and
     * eForm signature references.</p>
     */
    @Override
    public DigitalSignature findMetadataById(int id) {
        Query query = entityManager.createQuery(
                "SELECT ds.demographicId, ds.moduleType, ds.providerNo FROM DigitalSignature ds WHERE ds.id = ?1");
        query.setParameter(1, id);
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        if (results.isEmpty()) {
            return null;
        }

        Object[] row = results.get(0);
        DigitalSignature digitalSignature = metadata((Integer) row[0], (ModuleType) row[1]);
        digitalSignature.setProviderNo((String) row[2]);
        if (digitalSignature.getModuleType() != null) {
            return digitalSignature;
        }
        if (digitalSignature.getDemographicId() == null) {
            return digitalSignature;
        }

        DigitalSignature inferredMetadata = findLegacyMetadataByReference(id, digitalSignature.getDemographicId());
        if (inferredMetadata != null) {
            // Legacy inference determines only module/patient ownership. Provider ownership still
            // comes from the signature row itself and must survive the projection; fax/signature
            // binding rejects a null or mismatched provider rather than guessing an identity.
            inferredMetadata.setProviderNo(digitalSignature.getProviderNo());
        }
        return inferredMetadata != null ? inferredMetadata : digitalSignature;
    }

    @Override
    public DigitalSignature findReadableMetadataById(int id) {
        DigitalSignature stored = findMetadataById(id);
        if (stored == null || stored.getDemographicId() == null
                || stored.getDemographicId() != -1) {
            return stored;
        }
        DigitalSignature resolved = findUniqueUnassignedOwner(id);
        if (resolved == null || (stored.getModuleType() != null
                && stored.getModuleType() != resolved.getModuleType())) {
            // An imported -1 row is never itself proof of patient ownership.
            // A caller must not authorize its image with the sentinel or a
            // module inferred from a conflicting clinical reference.
            return null;
        }
        resolved.setProviderNo(stored.getProviderNo());
        return resolved;
    }

    private DigitalSignature findUniqueUnassignedOwner(int id) {
        String idValue = String.valueOf(id);
        List<DigitalSignature> candidates = new ArrayList<>();
        addCandidates(candidates,
                "SELECT DISTINCT cr.demographicId FROM ConsultationRequest cr "
                        + "WHERE cr.signatureImg = ?1 AND cr.demographicId > 0",
                idValue, ModuleType.CONSULTATION);
        addCandidates(candidates,
                "SELECT DISTINCT cr.demographicId FROM ConsultationRequestArchive cr "
                        + "WHERE cr.signatureImg = ?1 AND cr.demographicId > 0",
                idValue, ModuleType.CONSULTATION);
        addCandidates(candidates,
                "SELECT DISTINCT cr.demographicNo FROM ConsultationResponse cr "
                        + "WHERE cr.signatureImg = ?1 AND cr.demographicNo > 0",
                idValue, ModuleType.CONSULTATION);
        addCandidates(candidates,
                "SELECT DISTINCT rx.demographicId FROM Prescription rx "
                        + "WHERE rx.digitalSignatureId = ?1 AND rx.demographicId > 0",
                id, ModuleType.PRESCRIPTION);
        // A signatureValue is written by the signature control. Letter HTML
        // is editable document text and cannot establish patient ownership
        // for a signature whose stored demographicId is -1.
        Query eform = entityManager.createQuery(
                "SELECT DISTINCT ev.demographicId FROM EFormValue ev WHERE ev.varName = ?1 "
                        + "AND " + EFORM_REFERENCE_PREDICATE + " AND ev.demographicId > 0");
        eform.setParameter(1, EFORM_SIGNATURE_FIELD_NAME);
        bindEformSignatureReference(eform, idValue);
        addCandidates(candidates, eform, ModuleType.E_FORM);
        // Even two valid references are unsafe if they disagree on
        // patient or module. No first-match inference for a -1 row.
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private void addCandidates(List<DigitalSignature> candidates, String jpql,
                               Object id, ModuleType moduleType) {
        Query query = entityManager.createQuery(jpql);
        query.setParameter(1, id);
        addCandidates(candidates, query, moduleType);
    }

    private void addCandidates(List<DigitalSignature> candidates, Query query,
                               ModuleType moduleType) {
        // DISTINCT in every query makes two results enough to detect a
        // within-module patient conflict without loading an unbounded set.
        query.setMaxResults(2);
        @SuppressWarnings("unchecked")
        List<Integer> patients = query.getResultList();
        for (Integer patient : patients) {
            if (patient == null || patient <= 0) {
                continue;
            }
            boolean seen = candidates.stream().anyMatch(candidate ->
                    candidate.getDemographicId().equals(patient)
                            && candidate.getModuleType() == moduleType);
            if (!seen) {
                candidates.add(metadata(patient, moduleType));
            }
        }
    }

    private DigitalSignature findLegacyMetadataByReference(int id, int demographicId) {
        String idValue = String.valueOf(id);

        DigitalSignature metadata = findLegacyConsultationRequestMetadata(idValue, demographicId);
        if (metadata != null) {
            return metadata;
        }

        metadata = findLegacyConsultationRequestArchiveMetadata(idValue, demographicId);
        if (metadata != null) {
            return metadata;
        }

        metadata = findLegacyConsultationResponseMetadata(idValue, demographicId);
        if (metadata != null) {
            return metadata;
        }

        metadata = findLegacyPrescriptionMetadata(id, demographicId);
        if (metadata != null) {
            return metadata;
        }

        return findLegacyEformMetadata(idValue, demographicId);
    }

    private DigitalSignature findLegacyConsultationRequestMetadata(String idValue, int demographicId) {
        return referencedMetadata(
                "SELECT cr.demographicId FROM ConsultationRequest cr "
                        + "WHERE cr.signatureImg = ?1 AND cr.demographicId = ?2",
                idValue,
                demographicId,
                ModuleType.CONSULTATION);
    }

    private DigitalSignature findLegacyConsultationRequestArchiveMetadata(String idValue, int demographicId) {
        return referencedMetadata(
                "SELECT cr.demographicId FROM ConsultationRequestArchive cr "
                        + "WHERE cr.signatureImg = ?1 AND cr.demographicId = ?2",
                idValue,
                demographicId,
                ModuleType.CONSULTATION);
    }

    private DigitalSignature findLegacyConsultationResponseMetadata(String idValue, int demographicId) {
        return referencedMetadata(
                "SELECT cr.demographicNo FROM ConsultationResponse cr "
                        + "WHERE cr.signatureImg = ?1 AND cr.demographicNo = ?2",
                idValue,
                demographicId,
                ModuleType.CONSULTATION);
    }

    private DigitalSignature findLegacyPrescriptionMetadata(int id, int demographicId) {
        return referencedMetadata(
                "SELECT rx.demographicId FROM Prescription rx "
                        + "WHERE rx.digitalSignatureId = ?1 AND rx.demographicId = ?2",
                id,
                demographicId,
                ModuleType.PRESCRIPTION);
    }

    private DigitalSignature findLegacyEformMetadata(String idValue, int demographicId) {
        String jpql =
                "SELECT ev.demographicId FROM EFormValue ev WHERE "
                        + "(ev.varName = ?1 OR ev.varName = 'Letter') "
                        + "AND " + EFORM_REFERENCE_PREDICATE + " AND ev.demographicId = ?8";

        Query query = entityManager.createQuery(jpql);
        query.setParameter(1, EFORM_SIGNATURE_FIELD_NAME);
        bindEformSignatureReference(query, idValue);
        query.setParameter(8, demographicId);
        return referencedMetadata(query, ModuleType.E_FORM);
    }

    private void bindEformSignatureReference(Query query, String idValue) {
        String token = DIGITAL_SIGNATURE_ID_PARAM + idValue;
        // A saved eForm may HTML-escape '&' in its URL. Match the same
        // query-parameter spellings the PDF renderer accepts, with an
        // end-of-value or next-parameter boundary after the exact id.
        query.setParameter(2, "%?" + token + "&%");
        query.setParameter(3, "%&" + token + "&%");
        query.setParameter(4, "%&amp;" + token + "&%");
        query.setParameter(5, "%?" + token);
        query.setParameter(6, "%&" + token);
        query.setParameter(7, "%&amp;" + token);
    }

    private DigitalSignature referencedMetadata(
            String jpql,
            Object referenceValue,
            int demographicId,
            ModuleType moduleType) {
        Query query = entityManager.createQuery(jpql);
        query.setParameter(1, referenceValue);
        query.setParameter(2, demographicId);
        return referencedMetadata(query, moduleType);
    }

    private DigitalSignature referencedMetadata(Query query, ModuleType moduleType) {
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();
        if (results.isEmpty()) {
            return null;
        }

        Integer referencedDemographicId = results.get(0);
        return metadata(referencedDemographicId, moduleType);
    }

    private static DigitalSignature metadata(Integer demographicId, ModuleType moduleType) {
        DigitalSignature digitalSignature = new DigitalSignature();
        digitalSignature.setDemographicId(demographicId);
        digitalSignature.setModuleType(moduleType);
        return digitalSignature;
    }
}
