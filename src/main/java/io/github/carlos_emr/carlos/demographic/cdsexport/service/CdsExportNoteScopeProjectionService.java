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

import io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Scalar preflight only; callers must authorize the patient/categories before discovery and each
 * note before payload capture. The transaction has no timeout: a chart of any number of notes is
 * read, a page of notes a statement, and each statement keeps its own bound.
 */
@Service
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class CdsExportNoteScopeProjectionService {
    private final CdsExportNoteAccessDao notes;
    public CdsExportNoteScopeProjectionService(CdsExportNoteAccessDao notes) { this.notes = Objects.requireNonNull(notes); }
    /** Every note scope of the patient; {@code page} is the notes one statement returns. */
    public List<CdsExportNoteAccessDto> read(int patient, int page) { return notes.readScopes(patient, page); }
}
