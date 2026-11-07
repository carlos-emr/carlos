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

/** Idempotent durable acceptance, after source publication and authorization by the caller. */
public record CdsImportJobCommand(String submissionKey, String operator, String providerNo,
        String sourceNamespace, String profile, String sourceDigest, String optionsDigest,
        String rulesetDigest, String snapshotLocation, boolean dryRun, boolean cli) {
    public CdsImportJobCommand(String submissionKey, String operator, String providerNo, String sourceNamespace,
            String profile, String sourceDigest, String optionsDigest, String rulesetDigest, String snapshotLocation, boolean dryRun) {
        this(submissionKey, operator, providerNo, sourceNamespace, profile, sourceDigest, optionsDigest, rulesetDigest, snapshotLocation, dryRun, false);
    }
    public CdsImportJobCommand(String submissionKey, String operator, String providerNo, String sourceNamespace,
            String profile, String sourceDigest, String optionsDigest, String rulesetDigest, String snapshotLocation) {
        this(submissionKey, operator, providerNo, sourceNamespace, profile, sourceDigest, optionsDigest, rulesetDigest, snapshotLocation, false, false);
    }
    @Override
    public String toString() { return "CdsImportJobCommand[protected]"; }
}
