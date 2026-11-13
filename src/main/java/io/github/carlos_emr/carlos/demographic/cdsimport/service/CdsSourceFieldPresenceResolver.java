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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
 * <p>A link's presence says that imported data exists in its category, so it is shown only to a
 * viewer who may read that category for that patient: the same
 * {@link CdsImportAccessService#requireClinicalRead clinical read check} the source-fields view
 * applies. The view still enforces its own admission barrier, patient hold and read policy when
 * the link is followed ({@link CdsClinicalSourceService}).</p>
 *
 * <p>Answers are remembered on the request. A patient without imported data costs two indexed
 * lookups and no authorization work, however many links the page asks about. An imported patient
 * adds, per category asked, the clinical read check and, where the category holds no preserved
 * field, one review-provenance lookup.</p>
 *
 * <p>The two kinds of failure are answered differently. A failed presence lookup shows the link to
 * an authorized viewer rather than hiding the only route to preserved source values. A failed or
 * refused authorization check hides it.</p>
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
    private final CdsImportAccessService access;

    /**
     * @param fields CdsImportFieldDao the preserved source fields, asked which categories a patient has
     * @param patients CdsImportPatientDao the import ledger's patients, asked whether an import matched a chart
     * @param reviews CdsImportReviewDao the operator reviews, asked for provenance a category would list
     * @param access CdsImportAccessService the clinical read policy of the source-fields view
     */
    public CdsSourceFieldPresenceResolver(CdsImportFieldDao fields, CdsImportPatientDao patients,
            CdsImportReviewDao reviews, CdsImportAccessService access) {
        this.fields = fields; this.patients = patients; this.reviews = reviews; this.access = access;
    }

    /**
     * @param request HttpServletRequest the rendering request: its session names the viewer, and it
     *        carries the remembered answers for the page's other links; {@code null} yields {@code false}
     * @param demographicNo String the patient identifier as the page holds it; blank, non-numeric or
     *        beyond the range of a demographic number yields {@code false}
     * @param category String a CDS source category; one outside {@link CdsClinicalSourceService#CATEGORIES}
     *        yields {@code false}
     * @return boolean whether the source-fields view has something to list for this patient and
     *         category that this viewer may read
     */
    public boolean hasSourceFields(HttpServletRequest request, String demographicNo, String category) {
        int patient = patient(demographicNo);
        if (patient < 1 || category == null || !CdsClinicalSourceService.CATEGORIES.contains(category)) return false;
        Memo memo = memo(request, patient);
        // Presence first: an ordinary patient is answered without any authorization work.
        return present(memo, patient, category)
                && memo.readable.computeIfAbsent(category, key -> readable(request, patient, key));
    }

    private boolean present(Memo memo, int patient, String category) {
        if (memo.unknown) return true;
        try {
            if (memo.fieldCategories == null) memo.fieldCategories = new HashSet<>(fields.categories(patient));
            if (memo.fieldCategories.contains(category)) return true;
            // A chart an import matched can carry patient-level review provenance without a field in this category.
            if (memo.importTarget == null) memo.importTarget = patients.target(patient);
            if (!Boolean.TRUE.equals(memo.importTarget)) return false;
            return memo.reviewed.computeIfAbsent(category, key -> !reviews.provenance(patient, key, "", 1).isEmpty());
        } catch (RuntimeException failure) {
            memo.unknown = true;
            LOG.warn("CDS source link lookup failed; showing the links to authorized viewers for this request", failure);
            return true;
        }
    }

    /** Whether the request's viewer may read this patient's source fields in this category. */
    private boolean readable(HttpServletRequest request, int patient, String category) {
        try {
            HttpSession session = request == null ? null : request.getSession(false);
            LoggedInInfo viewer = session == null ? null : LoggedInInfo.getLoggedInInfoFromSession(session);
            access.requireClinicalRead(viewer, patient, category);
            return true;
        } catch (SecurityException refused) {
            return false;
        } catch (RuntimeException failure) {
            LOG.warn("CDS source link authorization check failed; hiding the link", failure);
            return false;
        }
    }

    private static int patient(String demographicNo) {
        if (demographicNo == null) return 0;
        String value = demographicNo.trim();
        // Ten digits always fit a long; the range check then keeps only what a demographic number can be.
        if (value.isEmpty() || value.length() > 10 || !value.chars().allMatch(c -> c >= '0' && c <= '9')) return 0;
        long number = Long.parseLong(value);
        return number > Integer.MAX_VALUE ? 0 : (int) number;
    }

    private static Memo memo(HttpServletRequest request, int patient) {
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
        private final Map<String, Boolean> readable = new HashMap<>();
        private boolean unknown;
    }
}
