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

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The records each of the patient's notes is attached to ({@code casemgmt_note_link}): the
 * document a "Document ... created" note files, the prescription a discontinuation note
 * explains, the eForm, tickler or e-mail a note records, the note an imported data note
 * annotates. Links inherit the exact captured parent-note authorization scope, like note
 * extensions; no row, revision copy or record type is filtered. Each link travels with its
 * note's own occurrence ({@link CdsExportNativeHistoryMapper}).
 */
@Service
public class CdsExportNoteLinkSourceReader extends CdsExportPatientTableSourceReader {
    public CdsExportNoteLinkSourceReader(CaseManagementNoteLinkDAO links) {
        super(
                links,
                CdsExportPatientTableReadSupport.Plan.CASE_NOTE_LINK,
                CdsExportNoteSourceReader.CATEGORIES);
    }

    @Override
    public Set<String> requiredTables() {
        return Set.of("casemgmt_note");
    }
}
