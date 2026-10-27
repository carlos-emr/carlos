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
package io.github.carlos_emr.carlos.demographic.cdsimport.command;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable effective options shared by browser and CLI; authorization remains in the service. */
public record CdsImportCommand(Path input, String submissionKey, String providerNo,
        Integer programId, Integer facilityId, String sourceNamespace, String sourceProfile,
        boolean mergeExisting, boolean dryRun, boolean matchProviderNames, int timeshiftDays,
        Integer courseId, boolean synthetic) {
    public CdsImportCommand {
        Objects.requireNonNull(input, "input");
        bounded(submissionKey, 128); bounded(providerNo, 16);
        bounded(sourceNamespace, 128); bounded(sourceProfile, 32);
        if (!providerNo.matches("[0-9]{1,16}") || (programId != null && programId < 1)
                || (facilityId != null && facilityId < 1) || (courseId != null && courseId < 1)
                || (!synthetic && (timeshiftDays != 0 || courseId != null))
                || (synthetic && (mergeExisting || courseId == null))) {
            throw new IllegalArgumentException("IMPORT_OPTIONS_INVALID");
        }
    }

    private static void bounded(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum
                || value.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("IMPORT_OPTIONS_INVALID");
        }
    }

    @Override public String toString() { return "CdsImportCommand[protected]"; }
}
