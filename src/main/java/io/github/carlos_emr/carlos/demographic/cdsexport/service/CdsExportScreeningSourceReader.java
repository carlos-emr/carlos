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
import org.springframework.stereotype.Service;

/**
 * Every physical column of the patient's frozen screening (non-immunization prevention)
 * selection, read for PastHealth only. Deleted, refused and other native states are captured
 * as recorded; the mapper decides each field's disposition.
 */
@Service
public class CdsExportScreeningSourceReader extends CdsExportSelectedPreventionSourceReader {
    public CdsExportScreeningSourceReader(
            PreventionDao source,
            CdsExportPreventionSnapshotService snapshots,
            CdsExportPreventionAccessService access) {
        super(
                source,
                CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL,
                snapshots,
                access,
                Partition.SCREENINGS,
                CdsExportPreventionSources.SCREENINGS);
    }
}
