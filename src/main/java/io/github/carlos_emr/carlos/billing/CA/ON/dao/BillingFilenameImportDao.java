/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billing.CA.ON.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Immutable import identities, with explicit administrator reconciliation of files. */
@Repository
@Transactional(readOnly = true)
public class BillingFilenameImportDao {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public record FileReview(String originalFilename, String importedFilename,
                             String status, String sha256, String reviewer, boolean dated) { }

    /** Old names remain occupied even when every colliding row received a new name. */
    public static boolean isReserved(EntityManager manager, String filename) {
        Query query = manager.createNativeQuery("SELECT COUNT(*) FROM billing_filename_import "
                + "WHERE original_filename = ?1");
        query.setParameter(1, filename);
        return ((Number) query.getSingleResult()).longValue() > 0;
    }

    /**
     * Finds exact original or allocated basenames in collision evidence ({@code group_size > 1}).
     * An empty result keeps ordinary file handling, including unchanged imports with group size one.
     *
     * @param filename the exact {@code String} basename
     * @return the matching {@code List<FileReview>}; empty when there is no collision evidence
     */
    public List<FileReview> findFileReviews(String filename) {
        return findFileReviews(entityManager, filename);
    }

    /**
     * Finds collision evidence with exact Java equality, including case and trailing spaces.
     * The SQL comparison supplies a superset; results are restricted to {@code group_size > 1}.
     * An empty list preserves ordinary file handling and does not imply absence of a group-one audit.
     *
     * @param manager the audit table's {@code EntityManager}
     * @param filename the exact {@code String} basename
     * @return the matching {@code List<FileReview>}; empty when there is no collision evidence
     */
    public static List<FileReview> findFileReviews(EntityManager manager, String filename) {
        Query query = manager.createNativeQuery("SELECT original_filename, imported_filename, "
                + "file_status, file_sha256, reconciled_by, reconciled_at FROM billing_filename_import "
                + "WHERE group_size > 1 AND (original_filename = ?1 OR imported_filename = ?1)");
        query.setParameter(1, filename);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        // The SQL comparison may have PAD SPACE/case-insensitive semantics.
        // Filter its superset by the actual byte-preserving Java filename.
        return rows.stream().filter(row -> filename.equals(row[0]) || filename.equals(row[1]))
                .map(row -> new FileReview((String) row[0], (String) row[1],
                (String) row[2], (String) row[3], (String) row[4], row[5] != null)).toList();
    }
}
