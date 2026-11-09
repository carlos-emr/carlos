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
import io.github.carlos_emr.carlos.commn.model.ImportedSignatureOwner;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;

import java.util.List;

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
                    + "OR ev.varValue LIKE ?5 OR ev.varValue LIKE ?6 OR ev.varValue LIKE ?7 "
                    + "OR ev.varValue LIKE ?8 OR ev.varValue LIKE ?9 OR ev.varValue LIKE ?10 "
                    + "OR ev.varValue LIKE ?11 OR ev.varValue LIKE ?12 OR ev.varValue LIKE ?13)";

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
        if (digitalSignature.getDemographicId() == null
                || digitalSignature.getDemographicId() == -1) {
            // A live clinical reference can be submitted after migration.
            // It is never authority for an imported unassigned signature.
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
        DigitalSignature resolved = findImportedUnassignedOwner(id);
        if (resolved == null || (stored.getModuleType() != null
                && stored.getModuleType() != resolved.getModuleType())) {
            // An imported -1 row is never itself proof of patient ownership.
            // A caller must not authorize its image with the sentinel or a
            // module that conflicts with the frozen source decision.
            return null;
        }
        resolved.setProviderNo(stored.getProviderNo());
        return resolved;
    }

    private DigitalSignature findImportedUnassignedOwner(int id) {
        ImportedSignatureOwner owner = entityManager.find(ImportedSignatureOwner.class, id);
        if (owner == null || owner.getDemographicId() == null
                || owner.getDemographicId() <= 0 || owner.getModuleType() == null) {
            return null;
        }
        return metadata(owner.getDemographicId(), owner.getModuleType());
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
                        + "AND " + EFORM_REFERENCE_PREDICATE + " AND ev.demographicId = ?14";

        Query query = entityManager.createQuery(jpql);
        query.setParameter(1, EFORM_SIGNATURE_FIELD_NAME);
        bindEformSignatureReference(query, idValue);
        query.setParameter(14, demographicId);
        return referencedMetadata(query, ModuleType.E_FORM);
    }

    private void bindEformSignatureReference(Query query, String idValue) {
        String token = DIGITAL_SIGNATURE_ID_PARAM + idValue;
        // A saved eForm may HTML-escape '&' in its URL. Match the same
        // query-parameter spellings the PDF renderer accepts. A saved Letter
        // can end the URL at a literal HTML attribute quote rather than at
        // the end of the stored value or before another parameter. Keep the
        // character after the exact id explicit so 42 cannot match 420.
        int parameter = 2;
        for (String suffix : new String[] {"&%", "", "\"%", "'%"}) {
            for (String prefix : new String[] {"%?", "%&", "%&amp;"}) {
                query.setParameter(parameter++, prefix + token + suffix);
            }
        }
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
