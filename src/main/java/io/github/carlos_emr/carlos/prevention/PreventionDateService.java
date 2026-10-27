/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
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
}
