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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * One demographic assembly order shared by future CLI and browser execution: fixed
 * scalars, phones, notes, enrolment, primary physician, personal/professional contacts,
 * pharmacy and ancillary base-row decisions. Every dependency belongs to the same
 * authenticated capture; remaining retained-source and associated-row hand-offs prevent
 * a demographic-complete result. This component does not qualify an export profile.
 */
public final class CdsExportDemographicAssembler {
    /**
     * Composes existing owning reconcilers before the final base-row closure. The caller
     * owns bounded, authenticated row collections and the active retained-value scope.
     * Any unresolved component withholds all proposed XML changes; no partial result is
     * silently emitted. Unselected alerts leave their note carriers explicitly deferred.
     */
    public CdsExportDemographicCareAssembler.Result assemble(int patient, Set<CdsCategory> selected,
            String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness, List<CdsExportCapturedTextRowDto> properties,
            List<CdsExportNativeRowReceiptDto> propertyWitnesses, CdsExportCapturedTextRowDto custom,
            CdsExportNativeRowReceiptDto customWitness, List<CdsExportCapturedTextRowDto> archives,
            List<CdsExportNativeRowReceiptDto> archiveWitnesses, CdsExportPublicProviderLookupsDto lookups,
            CdsExportRetainedProfessionalContactReconciler.NativeRows contacts,
            CdsExportRetainedPharmacyReconciler.NativeRows pharmacies, int maximumRows, long maximumBytes,
            CdsExportRetainedSectionWriter.Values values, Runnable checkpoint) throws IOException {
        values.requireActive(); checkpoint.run();
        var core = new CdsExportDemographicRetainedCoreReconciler().reconcileWithPrimaryPhysician(
                patient, selected, address, index, checked, allFields, current, witness, properties,
                propertyWitnesses, custom, customWitness, archives, archiveWitnesses, lookups,
                maximumRows, maximumBytes, values, checkpoint);
        var care = new CdsExportDemographicCareAssembler().assemble(core, patient, index, checked, allFields,
                current, witness, contacts, pharmacies, maximumRows, maximumBytes, values, checkpoint);
        var completeBaseRow = new CdsExportDemographicAncillaryReconciler().reconcile(
                care.demographic(), patient, current, witness, checkpoint);
        values.requireActive(); checkpoint.run();
        return new CdsExportDemographicCareAssembler.Result(completeBaseRow, care.professionalNameUses());
    }
}
