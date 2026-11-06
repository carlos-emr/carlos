/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao.forms;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.form.model.Growth0To36Form;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Typed access to Growth 0–36m versions; patient identity is bound in every form read. */
@Repository
public class Growth0To36FormDao extends AbstractDaoImpl<Growth0To36Form> {
    /** Selects the existing Growth form entity. */
    public Growth0To36FormDao() { super(Growth0To36Form.class); }

    /** Returns the requested patient's version, or null for a missing or foreign version. */
    @Transactional(readOnly = true)
    public Growth0To36Form findForPatient(int demographicNo, int id) {
        return entityManager.createQuery("SELECT f FROM Growth0To36Form f WHERE f.demographicNo = :patient AND f.id = :id",
                        Growth0To36Form.class)
                .setParameter("patient", demographicNo).setParameter("id", id)
                .getResultStream().findFirst().orElse(null);
    }

    /** Header projection for encounter history and the Rourke/eForm growth consumers. */
    public record Version(int id, int demographicNo, Date created, Date edited) { }

    /** Returns headers in the same descending version order as the legacy encounter list. */
    @Transactional(readOnly = true)
    public List<Version> findVersions(int demographicNo) {
        return entityManager.createQuery("SELECT f.id, f.demographicNo, f.formCreated, f.formEdited FROM Growth0To36Form f "
                        + "WHERE f.demographicNo = :patient ORDER BY f.id DESC", Object[].class)
                .setParameter("patient", demographicNo).getResultList().stream()
                .map(row -> new Version((Integer) row[0], (Integer) row[1], (Date) row[2], (Date) row[3]))
                .toList();
    }

    /**
     * Retains the encounter grouped-history projection and its MariaDB calendar-day grouping.
     * Mapped date aggregates retain their declared Java date types on Hibernate 7 as well as
     * the existing calendar-day grouping on the physical database.
     */
    @Transactional(readOnly = true)
    public List<Version> findGroupedVersions(int demographicNo) {
        List<Object[]> rows = entityManager.createQuery(
                "SELECT MAX(f.id), f.demographicNo, MIN(f.formCreated), MAX(f.formEdited) FROM Growth0To36Form f "
                        + "WHERE f.demographicNo = :patient GROUP BY FUNCTION('date', f.formEdited), f.demographicNo",
                        Object[].class)
                .setParameter("patient", demographicNo).getResultList();
        return rows.stream().map(row -> new Version(((Number) row[0]).intValue(), ((Number) row[1]).intValue(),
                (Date) row[2], (Date) row[3])).toList();
    }
}
