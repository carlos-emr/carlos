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
package io.github.carlos_emr.carlos.prevention;

import java.util.Objects;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;
import org.springframework.stereotype.Service;

/** Reads the existing native metadata, including partial dates created before CDS import. */
@Service
public class PreventionDateService {
    private final PartialDateDao partialDateDao;

    public PreventionDateService(PartialDateDao partialDateDao) {
        this.partialDateDao = Objects.requireNonNull(partialDateDao);
    }

    /**
     * Returns the native calendar precision; a missing date remains UNKNOWN. A persisted
     * entity's latest partial_date tag is authoritative regardless of import provenance.
     * @throws io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException
     *         when native metadata uses an unsupported format
     */
    public PreventionDateDTO getDate(Prevention prevention) {
        Objects.requireNonNull(prevention);
        String format = prevention.getId() == null ? null : partialDateDao.getFormat(
                PartialDate.PREVENTION, prevention.getId(), PartialDate.PREVENTION_PREVENTIONDATE);
        return PreventionDateDTO.from(prevention.getPreventionDate(), format);
    }

    /** Preserves the established exact-date print format and shows native partial/unknown timing. */
    public String displayDate(Prevention prevention, java.text.DateFormat exactFormatter) {
        PreventionDateDTO date = getDate(prevention);
        return date.isExact() ? exactFormatter.format(prevention.getPreventionDate())
                : date.getOriginalValue() == null ? "Unknown" : date.getOriginalValue();
    }
}
