/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.dao.forms;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Date;
import java.util.List;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Allocates form version times against committed and pending saves in the same transaction. */
final class FormVersionEdits {
    private FormVersionEdits() { }

    static List<Date> lockAndFindEditsSince(EntityManager entityManager, Class<?> entity, int patient, Date from) {
        // The patient row serializes saves even before this patient has a version of the form.
        entityManager.createNativeQuery(
                "SELECT demographic_no FROM demographic WHERE demographic_no = ?1 FOR UPDATE", Integer.class)
                .setParameter(1, patient).getResultList();

        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(Date.class);
        var form = query.from(entity);
        var edited = form.<Date>get("formEdited");
        query.select(edited).where(builder.equal(form.get("demographicNo"), patient),
                builder.greaterThanOrEqualTo(edited, from));
        var edits = entityManager.createQuery(query);
        // The ordinary save uses READ COMMITTED. A caller's enclosing transaction may use
        // repeatable read, so use a locking read there to bypass any earlier snapshot.
        if (!Integer.valueOf(Isolation.READ_COMMITTED.value())
                .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            edits.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return edits.getResultList();
    }
}
