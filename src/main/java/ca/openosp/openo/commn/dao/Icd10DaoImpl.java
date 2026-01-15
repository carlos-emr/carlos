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
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.AbstractCodeSystemModel;
import ca.openosp.openo.commn.model.Icd10;
import org.springframework.stereotype.Repository;

import javax.persistence.Query;
import java.util.Collections;
import java.util.List;

@Repository
public class Icd10DaoImpl extends AbstractDaoImpl<Icd10> implements Icd10Dao {

    public Icd10DaoImpl() {
        super(Icd10.class);
    }

    @Override
    public List<Icd10> searchCode(String term) {
        Query q = entityManager.createQuery("select i from Icd10 i where i.icd10 like ?1 or i.description like ?2 order by i.description");
        q.setParameter(1, "%" + term + "%");
        q.setParameter(2, "%" + term + "%");

        @SuppressWarnings("unchecked")
        List<Icd10> results = q.getResultList();

        if (results == null) {
            results = Collections.emptyList();
        }

        return results;
    }

    @Override
    public Icd10 findByCode(String code) {
        Query query = entityManager.createQuery("select i from Icd10 i where i.icd10=?1");
        query.setParameter(1, code);

        return getSingleResultOrNull(query);
    }

    @Override
    public AbstractCodeSystemModel<?> findByCodingSystem(String codingSystem) {
        Query query = entityManager.createQuery("FROM Icd10 i WHERE i.icd10 like ?1");
        query.setParameter(1, codingSystem);
        query.setMaxResults(1);

        return getSingleResultOrNull(query);
    }

    @Override
    public List<Icd10> searchText(String description) {
        String sql = "select x from DiagnosticCode x where x.description like ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "%" + description + "%");

        @SuppressWarnings("unchecked")
        List<Icd10> results = query.getResultList();

        if (results == null) {
            results = Collections.emptyList();
        }

        return results;
    }
}
