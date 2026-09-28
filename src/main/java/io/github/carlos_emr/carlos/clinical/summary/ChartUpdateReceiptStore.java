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
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.carlos.commn.model.ChartUpdateReceipt;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/** Serializes this assistant's additions for a patient, including across sessions and application nodes. */
@Repository
public class ChartUpdateReceiptStore {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public void lockPatient(int patient) {
        if (entityManager.find(Demographic.class, patient, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new IllegalStateException("Patient record unavailable.");
        }
    }
    public ChartUpdateReceipt find(String key) { return entityManager.find(ChartUpdateReceipt.class, key); }
    public void requireNoteLock(LoggedInInfo user, int patient) {
        var session = user.getSession();
        Object stored = session == null ? null : session.getAttribute("casemgmtNoteLock" + patient);
        if (!(stored instanceof CasemgmtNoteLock held) || held.getId() == null) {
            throw new IllegalStateException("Open the patient's eChart and acquire its editing lock before saving history.");
        }
        var current = entityManager.find(CasemgmtNoteLock.class, held.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (current == null || !Integer.valueOf(patient).equals(current.getDemographicNo())
                || !session.getId().equals(current.getSessionId()) || !session.getId().equals(held.getSessionId())) {
            throw new IllegalStateException("The chart editing lock changed. Reopen the patient's eChart before saving history.");
        }
    }
    public void save(ChartUpdateReceipt receipt) {
        entityManager.persist(receipt);
        entityManager.flush();
    }

    /** Legacy installations can retain nontransactional tables; do not claim atomic writes there. */
    public void requireTransactionalTables(boolean history, boolean legacyChart) {
        String[] tables = history
                ? new String[]{"clinical_chart_update_receipt", "demographic", "casemgmt_note", "casemgmt_issue",
                    "casemgmt_issue_notes", "casemgmt_note_link", "casemgmt_note_lock", "hash_audit"}
                : new String[]{"clinical_chart_update_receipt", "demographic", "tickler", "tickler_link"};
        for (String table : tables) requireInnoDb(table);
        if (history && legacyChart) requireInnoDb("eChart");
    }
    private void requireInnoDb(String table) {
        var engines = entityManager.createNativeQuery("SELECT ENGINE FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName", String.class)
                .setParameter("tableName", table).getResultList();
        if (engines.size() != 1 || !"InnoDB".equalsIgnoreCase(engines.get(0).toString())) {
            throw new IllegalStateException("Chart updates require transactional target tables. Ask the administrator to review the database configuration.");
        }
    }
}
