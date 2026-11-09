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
package io.github.carlos_emr.carlos.demographic.cdsexport.engine;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import java.io.IOException;
import java.util.Set;

/**
 * Installed clinical engine boundary, shared by foreground CLI and durable browser workers.
 * An engine may list a profile only after source-domain/mapping, current-chart reconciliation,
 * coherent file/render capture, independent coverage, actual XML/supporting-file/reference
 * checks, recipient/audit arrangements, atomic publication and installed qualification pass.
 * Recognition of a profile or a passing byte-package test never qualifies it.
 * Unknown or unreconciled native evidence must refuse publication even for an
 * installed profile; profile recognition never supplies missing clinical evidence.
 */
public interface CdsExportEngine {
    /** Immutable qualification identity over the installed code/schema/mappings and capture/store contracts. */
    String compatibilityDigest();
    Set<CdsLogicalProfile> qualifiedProfiles();
    /** Binds server-owned sender, registered recipient and verified audit access; no client-supplied file descriptor is trusted. */
    CdsExportJobDocumentDto bind(CdsExportActorDto actor, CdsExportCommand command) throws IOException;
    /** Must publish only through independently proved publication; returning is not a completion claim. */
    void execute(CdsExportLeaseDto lease, CdsExportRunControl control) throws IOException;
}
