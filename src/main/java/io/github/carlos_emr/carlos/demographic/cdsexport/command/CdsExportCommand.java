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
package io.github.carlos_emr.carlos.demographic.cdsexport.command;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import java.util.List;
import java.util.Set;

/** Immutable disclosure intent; provider discovery and current authorization belong to the worker. */
public record CdsExportCommand(
        String submissionKey,
        String providerNo,
        Integer facilityId,
        CdsLogicalProfile profile,
        Selection selection,
        List<String> physicianNos,
        List<Integer> patientNos,
        Set<CdsCategory> categories,
        String recipientId,
        String auditPlanId) {
    public enum Selection {
        PHYSICIAN_ROSTER,
        SELECTED_PATIENTS
    }

    public CdsExportCommand {
        if (submissionKey == null
                || !submissionKey.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || providerNo == null
                || !providerNo.matches("[A-Za-z0-9_-]{1,16}")
                || facilityId != null && facilityId < 1
                || profile == null
                || selection == null
                || recipientId == null
                || !recipientId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                || auditPlanId == null
                || !auditPlanId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("CDS_EXPORT_REQUEST_INVALID");
        }
        physicianNos = List.copyOf(physicianNos);
        patientNos = List.copyOf(patientNos);
        categories = Set.copyOf(categories);
        if (!categories.contains(CdsCategory.DEMOGRAPHICS)
                || physicianNos.size() > 100
                || patientNos.size() > 10_000
                || Set.copyOf(physicianNos).size() != physicianNos.size()
                || Set.copyOf(patientNos).size() != patientNos.size()
                || physicianNos.stream().anyMatch(value -> !value.matches("[A-Za-z0-9_-]{1,16}"))
                || patientNos.stream().anyMatch(value -> value < 1)
                || selection == Selection.PHYSICIAN_ROSTER
                        && (physicianNos.isEmpty() || !patientNos.isEmpty())
                || selection == Selection.SELECTED_PATIENTS
                        && (patientNos.isEmpty() || !physicianNos.isEmpty())) {
            throw new IllegalArgumentException("CDS_EXPORT_SELECTION_INVALID");
        }
        physicianNos = physicianNos.stream().sorted().toList();
        patientNos = patientNos.stream().sorted().toList();
        categories = java.util.Collections.unmodifiableSet(java.util.EnumSet.copyOf(categories));
    }

    @Override
    public String toString() {
        return "CdsExportCommand[protected]";
    }
}
