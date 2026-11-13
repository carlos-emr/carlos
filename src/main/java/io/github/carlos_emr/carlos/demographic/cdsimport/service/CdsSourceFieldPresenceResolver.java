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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsImportFieldDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportReviewDao;
import jakarta.servlet.ServletRequest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Decides whether a native screen shows its <strong>CDS source fields</strong> link for one patient
 * and category.
 *
 * <p>The link belongs only on a chart that a CDS import has something to say about. A patient no
 * import ever touched gets no link and no CDS wording, so the ordinary chart keeps its layout. The
 * answer mirrors what {@code /demographic/cdsSourceFields} would list: preserved source fields in
 * that category, or operator review provenance for an imported chart.</p>
 *
 * <p>This is a display decision, not an authorization one. The source-fields view applies the
 * admission barrier, patient hold and clinical read policy itself when the link is followed
 * ({@link CdsClinicalSourceService}); the page that asks here is already showing that patient.</p>
 *
 * <p>The answer is memoized on the request, so a chart with several links costs at most two indexed
 * lookups for a patient without imported data. A failed lookup shows the link rather than hiding
 * it: silently dropping the only route to preserved source values is the worse error.</p>
 *
 * @since 2026-11-12
 */
@Service
public class CdsSourceFieldPresenceResolver {
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(CdsSourceFieldPresenceResolver.class);
    private static final String MEMO_PREFIX = CdsSourceFieldPresenceResolver.class.getName() + ".patient.";

    private final CdsImportFieldDao fields;
    private final CdsImportPatientDao patients;
    private final CdsImportReviewDao reviews;

    public CdsSourceFieldPresenceResolver(CdsImportFieldDao fields, CdsImportPatientDao patients, CdsImportReviewDao reviews) {
        this.fields = fields; this.patients = patients; this.reviews = reviews;
    }

    /**
     * @param request the rendering request, used only to remember the answer for its other links
     * @param demographicNo the patient identifier as the page holds it; blank or non-numeric yields {@code false}
     * @param category a CDS source category; one outside {@link CdsClinicalSourceService#CATEGORIES} yields {@code false}
     * @return whether the source-fields view has anything to list for this patient and category
     */
    public boolean hasSourceFields(ServletRequest request, String demographicNo, String category) {
        int patient = patient(demographicNo);
        if (patient < 1 || category == null || !CdsClinicalSourceService.CATEGORIES.contains(category)) return false;
        Memo memo = memo(request, patient);
        if (memo.unknown) return true;
        try {
            if (memo.fieldCategories == null) memo.fieldCategories = new HashSet<>(fields.categories(patient));
            if (memo.fieldCategories.contains(category)) return true;
            // A chart an import matched can carry patient-level review provenance without a field in this category.
            if (memo.importTarget == null) memo.importTarget = patients.target(patient);
            if (!memo.importTarget) return false;
            return memo.reviewed.computeIfAbsent(category, key -> !reviews.provenance(patient, key, "", 1).isEmpty());
        } catch (RuntimeException failure) {
            memo.unknown = true;
            LOG.warn("CDS source link lookup failed; showing the links for this request", failure);
            return true;
        }
    }

    private static int patient(String demographicNo) {
        if (demographicNo == null) return 0;
        String value = demographicNo.trim();
        // Nine digits always fit an int; anything else cannot name a demographic row.
        if (value.isEmpty() || value.length() > 9 || !value.chars().allMatch(c -> c >= '0' && c <= '9')) return 0;
        return Integer.parseInt(value);
    }

    private static Memo memo(ServletRequest request, int patient) {
        if (request == null) return new Memo();
        String name = MEMO_PREFIX + patient;
        if (request.getAttribute(name) instanceof Memo memo) return memo;
        Memo memo = new Memo();
        request.setAttribute(name, memo);
        return memo;
    }

    /** One request's answers for one patient; a request is rendered by a single thread. */
    private static final class Memo {
        private Set<String> fieldCategories;
        private Boolean importTarget;
        private final Map<String, Boolean> reviewed = new HashMap<>();
        private boolean unknown;
    }
}
