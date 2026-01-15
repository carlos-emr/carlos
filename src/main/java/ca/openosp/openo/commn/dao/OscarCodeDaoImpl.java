//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.commn.dao;

import java.util.List;
import javax.persistence.Query;

import ca.openosp.openo.commn.model.AbstractCodeSystemModel;
import ca.openosp.openo.commn.model.OscarCode;
import org.springframework.stereotype.Repository;

@Repository
public class OscarCodeDaoImpl extends AbstractCodeSystemDaoImpl<OscarCode> implements OscarCodeDao {

    public OscarCodeDaoImpl() {
        super(OscarCode.class);
    }

    @Override
    public List<OscarCode> getIcd9Code(String icdCode) {
        Query query = entityManager.createQuery("select i from OscarCode i where i.oscarCode=?1");
        query.setParameter(1, icdCode);

        @SuppressWarnings("unchecked")
        List<OscarCode> results = query.getResultList();

        return results;
    }

    @Override
    public List<OscarCode> getOscarCode(String query) {
        Query q = entityManager.createQuery("select i from OscarCode i where i.oscarCode like ?1 or i.description like ?2 order by i.description");
        q.setParameter(1, "%" + query + "%");
        q.setParameter(2, "%" + query + "%");

        @SuppressWarnings("unchecked")
        List<OscarCode> results = q.getResultList();

        return results;
    }

    @Override
    public OscarCode findByCode(String code) {
        List<OscarCode> results = getOscarCode(code);
        if (results.isEmpty())
            return null;
        return results.get(0);
    }

    @Override
    public AbstractCodeSystemModel<?> findByCodingSystem(String codingSystem) {
        Query query = entityManager.createQuery("FROM OscarCode i WHERE i.oscarCode like ?1");
        query.setParameter(1, codingSystem);
        query.setMaxResults(1);

        return getSingleResultOrNull(query);
    }

    @Override
    public List<OscarCode> searchCode(String code) {
        List<OscarCode> results =  getOscarCode(code);
        if(results.isEmpty())
            return null;
        return results;
    }
}
