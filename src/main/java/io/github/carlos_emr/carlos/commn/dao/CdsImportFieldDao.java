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

import io.github.carlos_emr.carlos.commn.model.CdsImportField;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportFieldDao extends AbstractDaoImpl<CdsImportField> {
    public CdsImportFieldDao() { super(CdsImportField.class); }
    public CdsImportField lock(String id) { return entityManager.find(CdsImportField.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportField get(String id) { return entityManager.find(CdsImportField.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    public List<CdsImportField> page(int demographicNo, String category, String afterId, int limit) {
        return entityManager.createQuery("select f from CdsImportField f where f.demographicNo=:demo and f.category=:category and f.id>:after order by f.id", CdsImportField.class)
                .setParameter("demo", demographicNo).setParameter("category", category).setParameter("after", afterId).setMaxResults(limit).getResultList();
    }
}
