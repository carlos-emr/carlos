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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsContactDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Thin category strategy for the already-separated Demographics/Contact occurrence. */
@Service
public class CdsContactImportService implements CdsCategoryImportService {
    private final CdsContactNormalizationService normalization;
    private final CdsContactPersister persister;
    private final CdsNativePreparationService preparation;
    public CdsContactImportService(CdsContactNormalizationService normalization, CdsContactPersister persister,
            CdsNativePreparationService preparation) {
        this.normalization = normalization; this.persister = persister; this.preparation = preparation;
    }
    @Override public Set<String> sections() { return Set.of("Contact"); }
    @Override public void validate(CdsRecordDto record, CdsImportCommand command, CdsSnapshotDto snapshot,
            String xmlRelative) throws IOException { normalization.normalize(record); }
    @Override public void prepare(String run, long epoch, String unitKey, CdsRecordDto record,
            CdsImportCommand command, CdsSnapshotDto snapshot, String xmlRelative) throws IOException {
        preparation.store(run, epoch, unitKey, record.effectiveSha256(), command, normalization.normalize(record));
    }
    @Override public Map<String, String> write(String run, long epoch, String unitKey, int demographicNo,
            CdsRecordDto record, CdsImportCommand command) throws IOException {
        CdsContactDto prepared = preparation.load(run, epoch, unitKey, record.effectiveSha256(), command, CdsContactDto.class);
        return persister.write(run, epoch, unitKey, demographicNo, record, command, prepared);
    }
}
