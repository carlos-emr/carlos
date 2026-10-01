/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentPatientLink;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;

/** Uses the document viewer's patient, program and queue checks for tickler links. */
@Service
public class TicklerDocumentAccess {
    private final SecurityInfoManager securityInfoManager;
    private final CtlDocumentDao ctlDocumentDao;

    public TicklerDocumentAccess(SecurityInfoManager securityInfoManager, CtlDocumentDao ctlDocumentDao) {
        this.securityInfoManager = securityInfoManager;
        this.ctlDocumentDao = ctlDocumentDao;
    }

    public void requireRead(LoggedInInfo loggedInInfo, int documentNo) {
        DocumentPatientLink.requireAccess(loggedInInfo, documentNo, securityInfoManager, ctlDocumentDao);
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
