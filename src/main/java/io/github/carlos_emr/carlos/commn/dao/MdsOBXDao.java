/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.MdsOBX;

public interface MdsOBXDao extends AbstractDao<MdsOBX> {
    /**
     * Returns the result rows of one observation request of a report, as scalar columns.
     *
     * <p>Not {@link MdsOBX} entities: the table has no primary key and every result of a report shares
     * the segmentID the entity maps as its id, so Hibernate would return the first result once per row.</p>
     *
     * @param id the report's segmentID
     * @param associatedOBR the {@code obrID} the results belong to
     * @param codes result codes; a row matches when its observationSubID contains any of them
     *              (no filter when empty)
     * @return rows of {@code [obxID (Integer), observationIden, observationValue, abnormalFlags,
     *         observationResultStatus, producersID]}, the last five {@code String}
     */
    List<Object[]> findByIdObrAndCodes(Integer id, Integer associatedOBR, List<String> codes);
}
