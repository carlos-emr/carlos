/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.model;

import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Source-derived ownership frozen by the OSCAR 19 importer before go-live. */
@Entity
@Immutable
@Table(name = "o19_imported_signature_owner")
public class ImportedSignatureOwner {
    @Id
    @Column(name = "signature_id")
    private Integer signatureId;

    @Column(name = "demographic_id", nullable = false)
    private Integer demographicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false)
    private ModuleType moduleType;

    protected ImportedSignatureOwner() {
    }

    public ImportedSignatureOwner(Integer signatureId, Integer demographicId, ModuleType moduleType) {
        this.signatureId = signatureId;
        this.demographicId = demographicId;
        this.moduleType = moduleType;
    }

    public Integer getSignatureId() {
        return signatureId;
    }

    public Integer getDemographicId() {
        return demographicId;
    }

    public ModuleType getModuleType() {
        return moduleType;
    }
}
