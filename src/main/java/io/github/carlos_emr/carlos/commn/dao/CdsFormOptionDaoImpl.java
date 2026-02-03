/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.CdsFormOption;
import org.springframework.stereotype.Repository;

@Repository
public class CdsFormOptionDaoImpl extends AbstractDaoImpl<CdsFormOption> implements CdsFormOptionDao {

    public CdsFormOptionDaoImpl() {
        super(CdsFormOption.class);
    }

    public List<CdsFormOption> findByVersionAndCategory(String formVersion, String mainCatgeory) {
        String sqlCommand = "select x from CdsFormOption x where x.cdsFormVersion=?1 and x.cdsDataCategory like ?2 order by x.id";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, formVersion);
        query.setParameter(2, mainCatgeory + '%');
        @SuppressWarnings("unchecked")
        List<CdsFormOption> results = query.getResultList();
        return (results);
    }

    public List<CdsFormOption> findByVersion(String formVersion) {
        String sqlCommand = "select x from CdsFormOption x where x.cdsFormVersion=?1 order by x.id";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, formVersion);
        @SuppressWarnings("unchecked")
        List<CdsFormOption> results = query.getResultList();
        return (results);
    }
}
