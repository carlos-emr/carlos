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

import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
/**
 * JPA implementation of {@link ReportTemplatesDao} for reporting data access.
 *
 * @since 2001
 */

public class ReportTemplatesDaoImpl extends AbstractDaoImpl<ReportTemplates> implements ReportTemplatesDao {

    public ReportTemplatesDaoImpl() {
        super(ReportTemplates.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<ReportTemplates> findAll() {
        Query q = createQuery("t", null);
        return q.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public List<ReportTemplates> findActive() {
        Query q = createQuery("t", "t.active = 1");
        return q.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public ReportTemplates findByUuid(String uuid) {
        Query query = entityManager.createQuery("SELECT r from ReportTemplates r where r.uuid = ?1 and r.active = 1");
        query.setParameter(1, uuid);

        @SuppressWarnings("unchecked")
        List<ReportTemplates> results = query.getResultList();
        if (!results.isEmpty())
            return results.get(0);
        return null;
    }
}
