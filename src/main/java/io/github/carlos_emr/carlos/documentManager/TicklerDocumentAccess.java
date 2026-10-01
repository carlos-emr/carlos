/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentPatientLink;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;

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
     * @throws SecurityException when the viewer would refuse the document, or it is deleted
     */
    public void requireRead(LoggedInInfo loggedInInfo, int documentNo) {
        // Patient/program/queue first, so no document metadata is consulted for a denied caller.
        DocumentPatientLink.requireAccess(loggedInInfo, documentNo, securityInfoManager, ctlDocumentDao);
        Document document = documentDao.find(documentNo);
        if (document == null || document.getStatus() == 'D') {
            throw new SecurityException("Document is not available");
        }
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
