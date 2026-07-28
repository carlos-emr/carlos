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

import jakarta.persistence.Query;

import org.springframework.stereotype.Repository;

@Repository
public class DigitalSignatureDaoImpl extends AbstractDaoImpl<DigitalSignature> implements DigitalSignatureDao {

    private static final String EFORM_SIGNATURE_FIELD_NAME = "signatureValue";
    private static final String DIGITAL_SIGNATURE_ID_PARAM = "digitalSignatureId=";

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
                "SELECT ds.demographicId, ds.moduleType FROM DigitalSignature ds WHERE ds.id = ?1");
        query.setParameter(1, id);
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        if (results.isEmpty()) {
            return null;
        }

        Object[] row = results.get(0);
        DigitalSignature digitalSignature = metadata((Integer) row[0], (ModuleType) row[1]);
        if (digitalSignature.getModuleType() != null) {
            return digitalSignature;
        }
        if (digitalSignature.getDemographicId() == null) {
            return digitalSignature;
        }

        DigitalSignature inferredMetadata = findLegacyMetadataByReference(id, digitalSignature.getDemographicId());
        return inferredMetadata != null ? inferredMetadata : digitalSignature;
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
                "SELECT ev.demographicId FROM EFormValue ev WHERE ev.varName = ?1 "
                        + "AND (ev.varValue LIKE ?2 OR ev.varValue LIKE ?3 "
                        + "OR ev.varValue LIKE ?4 OR ev.varValue LIKE ?5) "
                        + "AND ev.demographicId = ?6";

        Query query = entityManager.createQuery(jpql);
        query.setParameter(1, EFORM_SIGNATURE_FIELD_NAME);
        query.setParameter(2, "%?" + DIGITAL_SIGNATURE_ID_PARAM + idValue + "&%");
        query.setParameter(3, "%&" + DIGITAL_SIGNATURE_ID_PARAM + idValue + "&%");
        query.setParameter(4, "%?" + DIGITAL_SIGNATURE_ID_PARAM + idValue);
        query.setParameter(5, "%&" + DIGITAL_SIGNATURE_ID_PARAM + idValue);
        query.setParameter(6, demographicId);
        return referencedMetadata(query, ModuleType.E_FORM);
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
