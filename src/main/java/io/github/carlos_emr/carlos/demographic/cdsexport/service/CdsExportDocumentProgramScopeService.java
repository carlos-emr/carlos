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

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import java.util.Objects;
import java.util.SortedMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded restriction preflight for a chart's documents, read in its own short transaction
 * before the Reports source snapshot: only document numbers and their programs, never a document
 * value. The caller authorizes each program before capture, and the capture proves the same
 * restrictions inside its snapshot.
 *
 * @since 2026-11-13
 */
@Service
@Transactional(
        readOnly = true,
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED,
        timeout = 30)
public class CdsExportDocumentProgramScopeService {
    private final DocumentDao documents;

    public CdsExportDocumentProgramScopeService(
            @Qualifier("documentDaoImpl") DocumentDao documents) {
        this.documents = Objects.requireNonNull(documents);
    }

    /** Document number to program, for each document the chart shows only inside a program. */
    public SortedMap<Integer, Integer> read(int patient) {
        return documents.readCdsExportProgramRestrictions(patient);
    }
}
