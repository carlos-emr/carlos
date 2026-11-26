/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.dao.forms;

import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyAssessment;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import java.util.Optional;
import javax.xml.parsers.ParserConfigurationException;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

/**
 * Patient-bound versions of the 2004 rehabilitation-study questionnaires.
 *
 * <p>Every method is typed by the questionnaire entity class. No caller supplies SQL, a table or
 * a column name: the one native statement names the table of the entity's own {@code @Table}.</p>
 *
 * @since 2026-11-26
 */
@Repository
public class RehabStudyFormDao {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * @param type the questionnaire entity
     * @param patient the patient's demographic number
     * @param id the version ID
     * @param <E> the questionnaire entity
     * @return the patient's version, or {@code null} when the ID is missing or another patient's
     */
    @Transactional(readOnly = true)
    public <E extends RehabStudyAssessment> E findForPatient(Class<E> type, int patient, int id) {
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(type);
        var form = query.from(type);
        query.select(form).where(builder.equal(form.get("demographicNo"), patient), builder.equal(form.get("id"), id));
        var rows = entityManager.createQuery(query).getResultList();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Inserts a new version; the generated ID is assigned once the insert is flushed.
     *
     * @param form the unsaved version
     */
    @Transactional
    public void persist(RehabStudyAssessment form) {
        entityManager.persist(form);
    }

    /** Sends pending inserts to the database inside the caller's transaction. */
    @Transactional
    public void flush() {
        entityManager.flush();
    }

    /**
     * The study ID a new questionnaire shows. A patient enrolled more than once gets the lowest
     * study ID: the retired readers took the first row of an unordered query, which InnoDB
     * returns in primary-key order.
     *
     * @param patient the patient's demographic number
     * @return the study ID, or empty when the patient is not enrolled
     */
    @Transactional(readOnly = true)
    public Optional<String> findStudyId(int patient) {
        return entityManager.createQuery("SELECT e.studyId FROM RehabStudyEnrolment e"
                        + " WHERE e.demographicNo = :patient ORDER BY e.studyId", Integer.class)
                .setParameter("patient", patient).setMaxResults(1).getResultList().stream()
                .findFirst().map(String::valueOf);
    }

    /**
     * Reads a committed version as the optional XML interchange copy, keeping its JDBC column
     * names and value encoding.
     *
     * <p>Called from the save's after-commit callback, where the save's EntityManager and
     * connection are still bound: SUPPORTS reads through them. A new transaction would hold a
     * second pooled connection beside the save's, so concurrent archived saves could exhaust the
     * pool. Called with no transaction, it reads in a non-transactional scope of its own.</p>
     *
     * @param type the questionnaire entity
     * @param patient the patient's demographic number
     * @param id the version ID
     * @return the row as the legacy {@code <Results><Row>} document
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Document readArchive(Class<? extends RehabStudyAssessment> type, int patient, int id) {
        String sql = archiveSql(tableOf(type));
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var query = connection.prepareStatement(sql)) {
                query.setInt(1, patient);
                query.setInt(2, id);
                try (var rows = query.executeQuery()) {
                    return JDBCUtil.toDocument(rows);
                }
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException("Unable to create rehabilitation study form XML", e);
            }
        });
    }

    private static String archiveSql(String table) {
        // Table identifiers cannot be JDBC-bound. The name is the mapped entity's compile-time
        // @Table value, validated by tableOf; the patient and version remain bind parameters.
        // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
        return "SELECT * FROM " + table + " WHERE demographic_no = ? AND ID = ?"; // NOSONAR javasecurity:S2077 -- entity @Table name; values are bound
    }

    /**
     * @param type the questionnaire entity
     * @return the physical table of the mapped entity
     * @throws IllegalArgumentException if the class is not a mapped questionnaire entity
     */
    public String tableOf(Class<? extends RehabStudyAssessment> type) {
        Table table = type.getAnnotation(Table.class);
        // Refuse anything the persistence unit does not map, so a table name only ever comes
        // from a registered entity.
        entityManager.getMetamodel().entity(type);
        if (table == null || !table.name().matches("form[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Not a rehabilitation study questionnaire entity");
        }
        return table.name();
    }
}
