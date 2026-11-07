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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAllergyDto;
import java.io.IOException;

/** Historical prepared representation only. Current row/alias/field reconciliation must authorize its selection separately. */
public final class CdsExportPreparedAllergyMapper {
    public CdsExportAllergyDto map(CdsAllergyDto source) throws IOException {
        java.util.Objects.requireNonNull(source); var dates = new CdsExportPreparedTemporalMapper();
        return new CdsExportAllergyDto(source.description(), source.property(), source.codeSystem(), source.code(),
                source.reactionType(), dates.map(source.start(), false), source.lifeStage(), source.severity(), source.reaction(),
                dates.map(source.recorded(), true), source.notes(), source.archived(), source.ageOfOnset(), source.onsetOfReaction());
    }
}
