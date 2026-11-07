/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billing.CA.ON.model;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** Durable import evidence; CARLOS reads it, administrators reconcile files explicitly. */
@Entity
@Table(name = "billing_filename_import")
@IdClass(BillingFilenameImport.Key.class)
public class BillingFilenameImport extends AbstractModel<BillingFilenameImport.Key> {
    @Id @Column(name = "source_table", length = 64, nullable = false)
    private String sourceTable;
    @Id @Column(name = "source_id", nullable = false)
    private Long sourceId;
    @Column(name = "original_filename", length = 50)
    private String originalFilename;
    @Column(name = "imported_filename", length = 50)
    private String importedFilename;
    @Column(name = "group_size", nullable = false)
    private Long groupSize;
    @Column(name = "reason", length = 32, nullable = false)
    private String reason;
    @Column(name = "file_status", length = 32, nullable = false)
    private String fileStatus;
    @Column(name = "file_sha256", length = 64)
    private String fileSha256;
    @Column(name = "reconciled_by", length = 128)
    private String reconciledBy;
    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Override @Transient
    public Key getId() {
        return new Key(sourceTable, sourceId);
    }

    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        private String sourceTable;
        private Long sourceId;

        public Key() { }
        public Key(String sourceTable, Long sourceId) {
            this.sourceTable = sourceTable;
            this.sourceId = sourceId;
        }
        /** The source table and row ID jointly identify one imported evidence row. */
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(sourceTable, key.sourceTable)
                    && Objects.equals(sourceId, key.sourceId);
        }
        @Override public int hashCode() {
            return Objects.hash(sourceTable, sourceId);
        }
    }
}
