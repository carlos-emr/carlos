/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentPatientLink;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Uses the document viewer's patient, program and queue checks for tickler links, and additionally
 * refuses soft-deleted documents.
 *
 * <p>{@link DocumentPatientLink#requireAccess} deliberately leaves document status to the calling
 * workflow: the document viewer still serves a deleted ({@code status 'D'}) document to an
 * authorized user from the document report's "deleted" view so it can be reviewed and undeleted.
 * A tickler is not that workflow. Deleted documents keep their {@code document}, {@code ctl_document}
 * and {@code tickler_docs} rows, so without this check a tickler would keep linking (and could newly
 * attach) a document the user deleted; consult/eForm attachment selection refuses deleted documents
 * for the same reason ({@code AttachmentSelectionAccess}).
 */
@Service
public class TicklerDocumentAccess {
    private final SecurityInfoManager securityInfoManager;
    private final CtlDocumentDao ctlDocumentDao;
    private final DocumentDao documentDao;

    public TicklerDocumentAccess(SecurityInfoManager securityInfoManager, CtlDocumentDao ctlDocumentDao,
                                 DocumentDao documentDao) {
        this.securityInfoManager = securityInfoManager;
        this.ctlDocumentDao = ctlDocumentDao;
        this.documentDao = documentDao;
    }

    /**
     * @throws SecurityException when the viewer would refuse the document, it is deleted, or it
     *         no longer has any live patient association
     */
    public void requireRead(LoggedInInfo loggedInInfo, int documentNo) {
        // Patient/program/queue first, so no document metadata is consulted for a denied caller.
        DocumentPatientLink.requireAccess(loggedInInfo, documentNo, securityInfoManager, ctlDocumentDao);
        Document document = documentDao.find(documentNo);
        if (document == null || document.getStatus() == 'D') {
            throw new SecurityException("Document is not available");
        }
        // The shared gate counts every ctl_document row when deciding WHOSE access is needed,
        // deleted links included (that only adds checks). It does not ask whether the document is
        // still filed against any patient at all, and must not for its other callers (provider
        // and unfiled inbox documents are legitimately patient-less there). A tickler link only
        // ever points at a patient's document, so require at least one live association by the
        // same rule as attachment ownership (AttachmentSelectionAccess.isLiveDemographicLink).
        if (!hasLivePatientLink(documentNo)) {
            throw new SecurityException("Document is not available");
        }
    }

    private boolean hasLivePatientLink(int documentNo) {
        List<Object[]> rows = documentDao.findCtlDocsAndDocsByDocNo(documentNo);
        if (rows == null) return false;
        for (Object[] row : rows) {
            if (row != null && row.length > 1 && row[1] instanceof CtlDocument link && link.getId() != null) {
                Integer patient = link.getId().getModuleId();
                if (patient != null && patient > 0 && AttachmentSelectionAccess.isLiveDemographicLink(row, patient)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canRead(LoggedInInfo loggedInInfo, int documentNo) {
        try {
            requireRead(loggedInInfo, documentNo);
            return true;
        } catch (SecurityException denied) {
            return false;
        }
    }
}
