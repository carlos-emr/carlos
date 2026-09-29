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
package io.github.carlos_emr.carlos.documentManager.annotation;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.commn.model.Queue;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolves the patient a document is filed against, if any.
 *
 * <p>{@code ctl_document} is a generic link table: {@code module} names the KIND of thing the
 * document is attached to and {@code module_id} is that thing's id. Only when the module is
 * {@code demographic} is {@code module_id} a demographic number. A document filed against a
 * provider carries a PROVIDER number there, and reading it as a demographic number gives a
 * different patient's chart — so a circle-of-care check on it either denies a legitimate action
 * or, worse, authorises and audit-logs against an unrelated patient.
 *
 * <p>{@code CtlDocument.isDemographicDocument()} makes the same test on the entity; this is the
 * equivalent for callers holding the {@link EDoc} view, which flattens both columns to strings.
 *
 * @since 2026-09
 */
public final class DocumentPatientLink {

    /** Module value under which {@code module_id} is a demographic number. */
    private static final String DEMOGRAPHIC_MODULE = "demographic";

    private DocumentPatientLink() {
    }

    /**
     * Requires access to every patient linked or DOC-routed to the stored document. A request's patient
     * parameter or a flattened first link must never authorize bytes shared by several charts.
     * Provider links and the unfiled inbox sentinel IDs are deliberately not patient records.
     * Persisted program restrictions and actual active-queue visibility apply to every caller.
     */
    public static void requireAccess(LoggedInInfo info, int documentNo, SecurityInfoManager security,
                                     CtlDocumentDao links) {
        if (info == null || documentNo <= 0) throw new SecurityException("Document access denied");
        Set<Integer> checkedPatients = new HashSet<>();
        for (CtlDocument link : links.findByDocumentNoAndModule(documentNo, DEMOGRAPHIC_MODULE)) {
            requirePatientAccess(info, link.getId().getModuleId(), security, checkedPatients);
        }
        // Some stored documents are patient-bound only through DOC routing.
        // Re-read actual routes for each request; another session's successful
        // authorization or a flattened first ctl_document link is insufficient.
        for (PatientLabRouting route : SpringUtils.getBean(PatientLabRoutingDao.class)
                .findByLabNoAndLabType(documentNo, "DOC")) {
            requirePatientAccess(info, route.getDemographicNo(), security, checkedPatients);
        }
        requireProgramAccess(info, documentNo);
        requireQueueAccess(info, documentNo, security);
    }

    private static void requirePatientAccess(LoggedInInfo info, Integer patient, SecurityInfoManager security,
                                              Set<Integer> checkedPatients) {
        if (patient != null && patient > 0 && checkedPatients.add(patient)
                && !security.isAllowedAccessToPatientRecord(info, patient)) {
            throw new SecurityException("Unauthorized access to patient record");
        }
    }

    /** Match inbox visibility: one visible active queue suffices; no active queue is unrestricted. */
    private static void requireQueueAccess(LoggedInInfo info, int documentNo, SecurityInfoManager security) {
        boolean hasActiveQueue = false;
        for (QueueDocumentLink link : SpringUtils.getBean(QueueDocumentLinkDao.class).getQueueFromDocument(documentNo)) {
            if (!"A".equals(link.getStatus())) continue;
            hasActiveQueue = true;
            if (link.getQueueId() == Queue.DEFAULT_QUEUE_ID
                    || security.hasPrivilege(info, "_queue." + link.getQueueId(), SecurityInfoManager.READ, (String) null)) return;
        }
        if (hasActiveQueue) throw new SecurityException("You do not have access to the source document queue.");
    }

    /** Match the persisted restriction used by EDocUtil's document-list filtering. */
    private static void requireProgramAccess(LoggedInInfo info, int documentNo) {
        Document document = SpringUtils.getBean(DocumentDao.class).find(documentNo);
        if (document == null) {
            throw new SecurityException("Document is not available");
        }
        Integer program = document.getProgramId();
        if (!Boolean.TRUE.equals(document.isRestrictToProgram()) || program == null || program == -1) return;
        var domain = SpringUtils.getBean(ProgramManager2.class).getProgramDomain(info, info.getLoggedInProviderNo());
        if (domain != null && domain.stream().anyMatch(link -> link.getProgramId() != null
                && program.longValue() == link.getProgramId().longValue())) return;
        throw new SecurityException("Document program access denied");
    }

    /**
     * @return the demographic number this document is filed against, or {@code 0} when it is not
     *         patient-linked — a provider-scoped document, an unfiled inbox document
     *         ({@code module_id} of {@code 0} or {@code -1}), or a non-numeric link
     */
    // IMPROPER_UNICODE: case-insensitive comparison of the ctl_document module name against "demographic", an ASCII protocol/domain
    // constant. String.equalsIgnoreCase is locale-independent, and the detector fires on the
    // call shape regardless of Locale, so it cannot be cleared in code.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an ASCII protocol/domain constant; equalsIgnoreCase is locale-independent")
    public static int demographicNoOf(EDoc doc) {
        if (doc == null || !DEMOGRAPHIC_MODULE.equalsIgnoreCase(StringUtils.trimToEmpty(doc.getModule()))) {
            return 0;
        }
        String moduleId = StringUtils.trimToNull(doc.getModuleId());
        if (moduleId == null || "0".equals(moduleId) || "-1".equals(moduleId)) {
            return 0;
        }
        try {
            int demographicNo = Integer.parseInt(moduleId);
            return demographicNo > 0 ? demographicNo : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
