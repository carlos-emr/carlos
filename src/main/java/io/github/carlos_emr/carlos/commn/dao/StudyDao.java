/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Typed reads for the legacy study registry, including string-valued study numbers. */
@Repository
@Transactional(readOnly = true)
public class StudyDao {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public record StudyLink(String name, String link) { }

    public StudyLink findLink(String studyNo) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("SELECT study_name, study_link FROM study WHERE study_no=?1", Object[].class)
                .setParameter(1, studyNo).getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = rows.get(rows.size() - 1);
        return new StudyLink((String) row[0], (String) row[1]);
    }

    /**
     * Selects the greatest registry primary key (form_value) when a study name has multiple
     * registrations. This makes the legacy last-registration selection independent of query plans.
     */
    public String findFormTable(String studyNo) {
        @SuppressWarnings("unchecked")
        List<String> rows = entityManager.createNativeQuery("SELECT e.form_table FROM encounterForm e, study s"
                        + " WHERE e.form_name=s.form_name AND s.study_no=?1 ORDER BY e.form_value DESC", String.class)
                .setParameter(1, studyNo).setMaxResults(1).getResultList();
        return rows.isEmpty() ? "" : rows.get(0);
    }
}
