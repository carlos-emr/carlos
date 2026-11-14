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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportLocalSourceDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportUnlinkedSourceDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Protected supporting-file input; contact/audit arrangements are explicit, not inferred defaults.
 * Held patients, unreviewed local tables, populated tables of removed features that belong to no
 * chart and the per-table counts of inbox rows linked to no patient are listed in the ReadMe so a
 * partial transfer, an unexported source or an excluded inbox item is never presented as complete
 * or lost silently.
 *
 * <p>{@code legacyLaboratoryReports} is the number of legacy-format laboratory reports (MDS, CML
 * and BC PathNet routes) written in Reports by a job that selected LaboratoryResults without
 * Reports, and is 0 for any other job; the ReadMe states it for such a job. {@code merges} is
 * what the ReadMe says about Merge Records for the whole job, or null when no chart of the job has
 * a Merge Records link or privilege row.
 */
public record CdsExportPackageMetadataDto(String exportRoot, String physicianGroup,
        String groupContactInformation, String applicationVersion, Instant exportTime,
        ZoneId clinicalZone, CdsLogicalProfile profile, Set<CdsCategory> categories,
        String auditAccessDescription, List<CdsExportHeldPatientDto> heldPatients,
        List<CdsExportLocalSourceDto> unreviewedLocalSources,
        List<CdsExportListedSourceDto> listedClinicSources,
        List<CdsExportUnlinkedSourceDto> unlinkedSources, long legacyLaboratoryReports,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                CdsExportMergeJobSummaryDto merges) {
    /** A package whose charts have no Merge Records link or privilege row. */
    public CdsExportPackageMetadataDto(String exportRoot, String physicianGroup,
            String groupContactInformation, String applicationVersion, Instant exportTime,
            ZoneId clinicalZone, CdsLogicalProfile profile, Set<CdsCategory> categories,
            String auditAccessDescription, List<CdsExportHeldPatientDto> heldPatients,
            List<CdsExportLocalSourceDto> unreviewedLocalSources,
            List<CdsExportListedSourceDto> listedClinicSources,
            List<CdsExportUnlinkedSourceDto> unlinkedSources, long legacyLaboratoryReports) {
        this(exportRoot, physicianGroup, groupContactInformation, applicationVersion, exportTime,
                clinicalZone, profile, categories, auditAccessDescription, heldPatients,
                unreviewedLocalSources, listedClinicSources, unlinkedSources,
                legacyLaboratoryReports, null);
    }

    public CdsExportPackageMetadataDto {
        if (legacyLaboratoryReports < 0)
            throw new IllegalArgumentException("CDS_EXPORT_PACKAGE_METADATA_INVALID");
        categories = Set.copyOf(categories);
        heldPatients = List.copyOf(heldPatients);
        unreviewedLocalSources = List.copyOf(unreviewedLocalSources);
        listedClinicSources = List.copyOf(listedClinicSources);
        unlinkedSources = List.copyOf(unlinkedSources);
    }

    /** A package with no legacy laboratory report written in Reports without Reports selected. */
    public CdsExportPackageMetadataDto(String exportRoot, String physicianGroup,
            String groupContactInformation, String applicationVersion, Instant exportTime,
            ZoneId clinicalZone, CdsLogicalProfile profile, Set<CdsCategory> categories,
            String auditAccessDescription, List<CdsExportHeldPatientDto> heldPatients,
            List<CdsExportLocalSourceDto> unreviewedLocalSources,
            List<CdsExportListedSourceDto> listedClinicSources,
            List<CdsExportUnlinkedSourceDto> unlinkedSources) {
        this(exportRoot, physicianGroup, groupContactInformation, applicationVersion, exportTime,
                clinicalZone, profile, categories, auditAccessDescription, heldPatients,
                unreviewedLocalSources, listedClinicSources, unlinkedSources, 0);
    }

    /** A package without listed clinic tables of removed features or unlinked inbox rows. */
    public CdsExportPackageMetadataDto(String exportRoot, String physicianGroup,
            String groupContactInformation, String applicationVersion, Instant exportTime,
            ZoneId clinicalZone, CdsLogicalProfile profile, Set<CdsCategory> categories,
            String auditAccessDescription, List<CdsExportHeldPatientDto> heldPatients,
            List<CdsExportLocalSourceDto> unreviewedLocalSources) {
        this(exportRoot, physicianGroup, groupContactInformation, applicationVersion, exportTime,
                clinicalZone, profile, categories, auditAccessDescription, heldPatients,
                unreviewedLocalSources, List.of(), List.of(), 0);
    }

    /** A package with every selected patient and no unreviewed local source. */
    public CdsExportPackageMetadataDto(String exportRoot, String physicianGroup,
            String groupContactInformation, String applicationVersion, Instant exportTime,
            ZoneId clinicalZone, CdsLogicalProfile profile, Set<CdsCategory> categories,
            String auditAccessDescription) {
        this(exportRoot, physicianGroup, groupContactInformation, applicationVersion, exportTime,
                clinicalZone, profile, categories, auditAccessDescription, List.of(), List.of(),
                List.of(), List.of(), 0);
    }

    @Override public String toString() { return "CdsExportPackageMetadataDto[protected]"; }
}
