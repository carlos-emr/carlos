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
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportClinicalJobService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;

/** Clinical adapter; registration follows the installed publication and transport qualification. */
public final class CdsExportClinicalEngine implements CdsExportEngine {
    private final CdsExportClinicalJobService jobs;
    public CdsExportClinicalEngine(CdsExportClinicalJobService jobs) { this.jobs = Objects.requireNonNull(jobs); }
    @Override public String compatibilityDigest() {
        try { return jobs.compatibilityDigest(); }
        catch (IOException failed) { throw new UncheckedIOException("CDS_EXPORT_ENGINE_IDENTITY_UNAVAILABLE", failed); }
    }
    @Override public Set<CdsLogicalProfile> qualifiedProfiles() { return Set.of(CdsLogicalProfile.ONTARIO_MD_5_2); }
    @Override public CdsExportJobDocumentDto bind(CdsExportActorDto actor, CdsExportCommand command) throws IOException {
        requireProfile(command); return jobs.bind(actor, command);
    }
    @Override public void execute(CdsExportLeaseDto lease, CdsExportRunControl control) throws IOException {
        requireProfile(Objects.requireNonNull(lease).document().command()); jobs.execute(lease, control);
    }
    private static void requireProfile(CdsExportCommand command) {
        if (Objects.requireNonNull(command).profile() != CdsLogicalProfile.ONTARIO_MD_5_2) {
            throw new IllegalStateException("CDS_EXPORT_PROFILE_NOT_QUALIFIED");
        }
    }
}
