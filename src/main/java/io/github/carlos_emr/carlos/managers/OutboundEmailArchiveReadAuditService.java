/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists archive access evidence independently of the caller's transaction.
 * @since 2026-09-15
 */
@Service
public class OutboundEmailArchiveReadAuditService {
    private final OscarLogDao oscarLogDao;

    /** @param oscarLogDao audit persistence; failures must propagate to the reader */
    public OutboundEmailArchiveReadAuditService(OscarLogDao oscarLogDao) {
        this.oscarLogDao = oscarLogDao;
    }

    /**
     * Commits an attributed access event before an archive read returns or throws.
     * Only identifiers and fixed event names belong in this record.
     *
     * @param loggedInInfo authenticated caller, required
     * @param archiveId archive identifier, required
     * @param documentNo document identifier, nullable for corrupt archive metadata
     * @param demographicNo authorized patient identifier, required
     * @param event fixed access outcome
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LoggedInInfo loggedInInfo, Integer archiveId, Integer documentNo,
            Integer demographicNo, Event event) {
        OscarLog entry = new OscarLog();
        entry.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        if (loggedInInfo.getLoggedInSecurity() != null) {
            entry.setSecurityId(loggedInInfo.getLoggedInSecurity().getSecurityNo());
        }
        entry.setIp(loggedInInfo.getIp());
        entry.setDemographicId(demographicNo);
        entry.setAction("OutboundEmailArchiveService." + event.action);
        entry.setContent("Outbound email archive");
        entry.setContentId("archiveId=" + archiveId + " documentNo=" + documentNo);
        oscarLogDao.persist(entry);
    }

    /** Fixed outcomes prevent clinical content or caller-supplied strings entering the audit. */
    public enum Event {
        METADATA_READ("getActiveArchive"),
        ARTIFACT_READ("readArchivedArtifact"),
        READ_FAILURE("readArchivedArtifact.readFailure"),
        INTEGRITY_FAILURE("readArchivedArtifact.integrityFailure");

        private final String action;

        Event(String action) {
            this.action = action;
        }
    }
}
