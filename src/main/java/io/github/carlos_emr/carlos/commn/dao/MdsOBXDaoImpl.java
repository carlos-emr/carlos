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

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.MdsOBX;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.util.ParamAppender;

@Repository
/**
 * JPA implementation of {@link MdsOBXDao} for MDS HL7 segment data access.
 *
 * @since 2001
 */

public class MdsOBXDaoImpl extends AbstractDaoImpl<MdsOBX> implements MdsOBXDao {

    /** Constructs this DAO for the {@link MdsOBX} entity class. */

    public MdsOBXDaoImpl() {
        super(MdsOBX.class);
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<MdsOBX> findByIdObrAndCodes(Integer id, String associatedOBR, List<String> codes) {
        ParamAppender pa = getAppender("obx");
        pa.and("obx.id = :id", "id", id);
        pa.and("obx.associatedOBR = :associatedOBR", "associatedOBR", associatedOBR);

        if (!codes.isEmpty()) {
            ParamAppender codesPa = new ParamAppender();
            for (int i = 0; i < codes.size(); i++) {
                String paramName = "observationSubId" + i;
                codesPa.or("obx.observationSubId like :" + paramName, paramName, "%" + codes.get(i) + "%");
            }
            pa.and(codesPa);
        }

        Query query = entityManager.createQuery(pa.getQuery());
        pa.setParams(query);
        return query.getResultList();
    }
}
