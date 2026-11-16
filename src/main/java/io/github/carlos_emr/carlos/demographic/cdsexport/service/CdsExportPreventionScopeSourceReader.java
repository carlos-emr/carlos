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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Only metadata necessary to classify every patient prevention; no dates, descriptions, notes or
 * extension values enter this projection. A row restricted to a program is read only after its
 * program is authorized live ({@link #forCapture}, {@link #authorizeReadScope}) and the capture
 * proves inside its snapshot that the restrictions are the ones authorized.
 */
@Service
public class CdsExportPreventionScopeSourceReader extends CdsExportPatientTableSourceReader {
    private final CdsExportPreventionAccessService access;
    private final PreventionDao source;
    private final Set<CdsCategory> selected;
    private final CdsExportPreventionAccessService.Scope scope;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportPreventionScopeSourceReader(
            PreventionDao source, CdsExportPreventionAccessService access) {
        this(
                source,
                access,
                CdsExportPreventionSources.CATEGORIES,
                CdsExportPreventionAccessService.Scope.UNPREPARED);
    }

    private CdsExportPreventionScopeSourceReader(
            PreventionDao source,
            CdsExportPreventionAccessService access,
            Set<CdsCategory> selected,
            CdsExportPreventionAccessService.Scope scope) {
        super(source, CdsExportPatientTableReadSupport.Plan.PREVENTION_SCOPE, selected);
        this.access = java.util.Objects.requireNonNull(access);
        this.source = java.util.Objects.requireNonNull(source);
        this.selected = Set.copyOf(selected);
        this.scope = java.util.Objects.requireNonNull(scope);
    }

    @Override
    public Set<CdsCategory> supportedCategories() {
        return CdsExportPreventionSources.CATEGORIES;
    }

    /** Vaccines and screenings share one classification; only the selected categories are authorized. */
    @Override
    public CdsExportNativeSourceReader forRequest(
            io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand command) {
        return new CdsExportPreventionScopeSourceReader(
                source, access, CdsExportPreventionSources.selected(command), scope);
    }

    /** The chart's program restrictions, read in their own short transaction before capture. */
    @Override
    public CdsExportNativeSourceReader forCapture(int patient, CdsExportRunControl control)
            throws java.io.IOException {
        if (scope.prepared())
            throw new java.io.IOException("CDS_EXPORT_PREVENTION_CAPTURE_SCOPE_INVALID");
        return new CdsExportPreventionScopeSourceReader(
                source,
                access,
                selected,
                CdsExportPreventionAccessService.Scope.prepare(access, patient, control));
    }

    @Override
    public long count(int patient) {
        requireSnapshotScope(patient);
        return super.count(patient);
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws java.io.IOException {
        requireSnapshotScope(patient);
        super.capture(patient, output);
    }

    private void requireSnapshotScope(int patient) {
        scope.requireSnapshot(patient, source.readCdsExportProgramRestrictions(patient));
    }

    @Override
    public String table() {
        return "preventions_scope";
    }

    /**
     * Live program check for every program a prevention of this chart is restricted to, outside
     * the source transaction, for each selected prevention category.
     */
    @Override
    public void authorizeReadScope(int patient, CdsExportRunControl control) {
        scope.authorize(patient, selected, control);
        control.checkpoint();
    }

    @Override
    public java.util.List<
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column>
            receiptColumns() {
        return super.receiptColumns().stream()
                .filter(column -> columns().contains(column.name()))
                .toList();
    }
}
