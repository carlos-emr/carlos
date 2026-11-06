/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.dao.forms;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.form.model.FormGrowth0To36;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import java.util.Date;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

/** Patient-bound growth records; no caller supplies SQL or a table identifier. */
@Repository
public class FormGrowth0To36Dao extends AbstractDaoImpl<FormGrowth0To36> {
    public FormGrowth0To36Dao() {
        super(FormGrowth0To36.class);
    }

    @Transactional(readOnly = true)
    public FormGrowth0To36 findForPatient(int patient, int id) {
        return entityManager.createQuery("SELECT f FROM FormGrowth0To36 f WHERE f.demographicNo = :patient AND f.id = :id",
                FormGrowth0To36.class).setParameter("patient", patient).setParameter("id", id)
                .getResultStream().findFirst().orElse(null);
    }

    /** Metadata consumed by encounter shortcuts, Rourke graphs and eForm growth overlays. */
    public record Version(Integer id, Integer patient, Date created, Date edited) { }

    @Transactional(readOnly = true)
    public List<Version> findHistory(int patient) {
        return entityManager.createQuery("SELECT new io.github.carlos_emr.carlos.commn.dao.forms.FormGrowth0To36Dao$Version"
                + "(f.id, f.demographicNo, f.formCreated, f.formEdited) FROM FormGrowth0To36 f"
                + " WHERE f.demographicNo = :patient ORDER BY f.id DESC", Version.class)
                .setParameter("patient", patient).getResultList();
    }

    @Transactional(readOnly = true)
    public List<Version> findGroupedHistory(int patient) {
        // Preserve maximum ID and edit timestamp per day. Take formCreated from that
        // selected ID, rather than MariaDB's arbitrary non-grouped column value.
        List<Object[]> rows = entityManager.unwrap(Session.class).createNativeQuery("SELECT f.ID, f.demographic_no, f.formCreated, g.edited"
                + " FROM formGrowth0_36 f JOIN (SELECT MAX(ID) id, MAX(formEdited) edited"
                + " FROM formGrowth0_36 WHERE demographic_no = :patient GROUP BY DATE(formEdited)) g ON f.ID = g.id"
                + " ORDER BY DATE(g.edited)", Object[].class)
                .addScalar("ID", Integer.class).addScalar("demographic_no", Integer.class)
                .addScalar("formCreated", java.sql.Date.class).addScalar("edited", java.sql.Timestamp.class)
                .setParameter("patient", patient).getResultList();
        return rows.stream().map(row -> new Version((Integer) row[0], (Integer) row[1],
                (Date) row[2], (Date) row[3])).toList();
    }

    /**
     * Keeps the optional legacy XML interchange's JDBC column names and value encoding.
     * A fresh transaction is required because this read is called after the save commits.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Document readArchive(int patient, int id) {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var query = connection.prepareStatement("SELECT * FROM formGrowth0_36 WHERE demographic_no = ? AND ID = ?")) {
                query.setInt(1, patient);
                query.setInt(2, id);
                try (var rows = query.executeQuery()) {
                    return JDBCUtil.toDocument(rows);
                }
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException("Unable to create growth form XML", e);
            }
        });
    }
}
