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

import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import org.springframework.stereotype.Repository;

@Repository
public class ConsultationRequestExtDaoImpl extends AbstractDaoImpl<ConsultationRequestExt> implements ConsultationRequestExtDao {

    public ConsultationRequestExtDaoImpl() {
        super(ConsultationRequestExt.class);
    }

    public List<ConsultationRequestExt> getConsultationRequestExts(int requestId) {
        Query query = entityManager.createQuery("select cre from ConsultationRequestExt cre where cre.requestId=?1");
        query.setParameter(1, requestId);
        return query.getResultList();
    }

    public String getConsultationRequestExtsByKey(int requestId, String key) {
        Query query = entityManager.createQuery("select cre.value from ConsultationRequestExt cre where cre.requestId=?1 and cre.key=?2");
        query.setParameter(1, requestId);
        query.setParameter(2, key);
        List<String> results = query.getResultList();
        if (results.size() > 0)
            return results.get(0);
        return null;
    }

    public void clear(int requestId) {
        Query query = entityManager.createQuery("delete from ConsultationRequestExt cre where cre.requestId = ?1");
        query.setParameter(1, requestId);
        query.executeUpdate();
    }
}
