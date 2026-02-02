//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.FormLabReq07;
import org.springframework.stereotype.Repository;

@Repository
public class FormLabReq07DaoImpl extends AbstractDaoImpl<FormLabReq07> implements FormLabReq07Dao {

    public FormLabReq07DaoImpl() {
        super(FormLabReq07.class);
    }

    public List<FormLabReq07> findCreatinine() {
        Query q = entityManager.createQuery("select f from FormLabReq07 f where f.creatinine=?1");
        q.setParameter(1, true);

        @SuppressWarnings("unchecked")
        List<FormLabReq07> results = q.getResultList();

        return results;
    }

    public List<FormLabReq07> findAcr() {
        Query q = entityManager.createQuery("select f from FormLabReq07 f where f.acr=?1");
        q.setParameter(1, true);

        @SuppressWarnings("unchecked")
        List<FormLabReq07> results = q.getResultList();

        return results;
    }
}
