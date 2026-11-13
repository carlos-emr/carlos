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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportHeldRowDto;
import io.github.carlos_emr.carlos.commn.model.CdsExportHeldPatient;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Held-back patients of one export attempt. Callers hold the owning run's row lock before writing,
 * so the list for an epoch is written exactly once and never changes afterwards.
 */
@Repository
public class CdsExportHeldPatientDao extends AbstractDaoImpl<CdsExportHeldPatient> {
    public CdsExportHeldPatientDao() {
        super(CdsExportHeldPatient.class);
    }

    /** Ordered by chart number; {@code limit} bounds the read for diagnostic reports. */
    public List<CdsExportHeldRowDto> held(String runId, long epoch, int limit) {
        return entityManager
                .createQuery(
                        "select new io.github.carlos_emr.carlos.commn.dto.CdsExportHeldRowDto("
                                + "h.demographicNo,h.safeCode) from CdsExportHeldPatient h where"
                                + " h.runId=:run and h.epoch=:epoch order by h.demographicNo",
                        CdsExportHeldRowDto.class)
                .setParameter("run", runId)
                .setParameter("epoch", epoch)
                .setMaxResults(limit)
                .getResultList();
    }

    public long count(String runId, long epoch) {
        return entityManager
                .createQuery(
                        "select count(h) from CdsExportHeldPatient h where h.runId=:run and"
                                + " h.epoch=:epoch",
                        Long.class)
                .setParameter("run", runId)
                .setParameter("epoch", epoch)
                .getSingleResult();
    }
}
