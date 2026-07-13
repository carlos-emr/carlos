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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.View;
import org.springframework.stereotype.Repository;

@Repository
public class ViewDaoImpl extends AbstractDaoImpl<View> implements ViewDao {

    public ViewDaoImpl() {
        super(View.class);
    }

    @Override
    public Map<String, View> getView(String view, String role) {
        Query query = entityManager.createQuery("select v from View v where v.view_name=?1 and v.role=?2 and v.providerNo is null");
        query.setParameter(1, view);
        query.setParameter(2, role);
        return getView(query);
    }

    @Override
    public Map<String, View> getView(String view, String role, String providerNo) {
        Query query = entityManager.createQuery("select v from View v where v.view_name=?1 and v.role=?2 and v.providerNo = ?3");
        query.setParameter(1, view);
        query.setParameter(2, role);
        query.setParameter(3, providerNo);
        return getView(query);
    }

    private Map<String, View> getView(Query query) {
        List<View> list = query.getResultList();
        Map<String, View> map = new HashMap<String, View>();
        for (View v : list) {
            map.put(v.getName(), v);
        }
        return map;
    }

    @Override
    public void saveView(View v) {
        if (v != null && v.getId() != null && v.getId() > 0) {
            merge(v);
        } else {
            persist(v);
        }
    }

    @Override
    public void delete(View v) {
        remove(v.getId());
    }
}
