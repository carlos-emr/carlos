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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.CaisiFormData;
import io.github.carlos_emr.carlos.commn.model.CaisiFormInstance;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.springframework.stereotype.Repository;

@Repository
public class CaisiFormInstanceDaoImpl extends AbstractDaoImpl<CaisiFormInstance> implements CaisiFormInstanceDao {
    public CaisiFormInstanceDaoImpl() {
        super(CaisiFormInstance.class);
    }

    public CaisiFormInstance getCurrentFormById(Integer formInstanceId) {
        return find(formInstanceId);
    }

    public List<CaisiFormInstance> findByFormId(Integer formId) {
        Query query = entityManager.createQuery("SELECT f FROM CaisiFormInstance f where f.formId = ?1 order by f.clientId, f.dateCreated");
        query.setParameter(1, formId);

        @SuppressWarnings("unchecked")
        List<CaisiFormInstance> result = query.getResultList();

        return result;
    }

    public List<CaisiFormInstance> getForms(Integer formId, Integer clientId) {
        Query query = entityManager.createQuery("SELECT f FROM CaisiFormInstance f where f.formId = ?1 and f.clientId = ?2 order by f.dateCreated DESC");
        query.setParameter(1, formId);
        query.setParameter(2, clientId);
        @SuppressWarnings("unchecked")
        List<CaisiFormInstance> result = query.getResultList();

        return result;
    }

    public CaisiFormInstance getLatestForm(Integer formId, Integer clientId) {
        Query query = entityManager.createQuery("SELECT f FROM CaisiFormInstance f where f.formId = ?1 and f.clientId = ?2 order by f.dateCreated DESC");
        query.setParameter(1, formId);
        query.setParameter(2, clientId);
        @SuppressWarnings("unchecked")
        List<CaisiFormInstance> result = query.getResultList();
        if (result.size() > 0) {
            return result.get(0);
        }
        return null;
    }

    public List<CaisiFormInstance> getFormsByFacility(Integer clientId, Integer facilityId) {
        String sSQL = "from CaisiFormInstance f where f.clientId = ?1 and f.formId in " +
                "(select s.formId from CaisiForm s where s.facilityId =?2) order by f.dateCreated DESC";

        Query query = entityManager.createNativeQuery(sSQL);
        query.setParameter(1, clientId);
        query.setParameter(2, facilityId);

        @SuppressWarnings("unchecked")
        List<CaisiFormInstance> result = query.getResultList();
        return result;

    }

    public List<CaisiFormInstance> getCurrentForms(String formId, List<Demographic> clients) {
        List<CaisiFormInstance> results = new ArrayList<CaisiFormInstance>();

        for (Iterator<Demographic> iter = clients.iterator(); iter.hasNext(); ) {
            Demographic client = iter.next();
            CaisiFormInstance ofi = getLatestForm(Integer.valueOf(formId), client.getDemographicNo());
            results.add(ofi);
        }

        return results;
    }

    public List<CaisiFormInstance> getForms(Long clientId) {
        Query query = entityManager.createQuery("SELECT f FROM CaisiFormInstance f where f.clientId = ?1 order by f.dateCreated DESC");
        query.setParameter(1, clientId);
        @SuppressWarnings("unchecked")
        List<CaisiFormInstance> result = query.getResultList();

        return result;
    }


    public Integer countAnswersByQuestions(String value, Integer formId, Date startDate, Date endDate, Integer pageNumber, Integer sectionId, Integer questionId) {
        Query query = entityManager.createQuery("select count(d.value) from CaisiFormInstance i, CaisiFormData d where d.value=?1 and i.formId=?2 and i.dateCreated>=?3 and i.dateCreated<=?4 and i.id=d.instanceId and d.pageNumber=?5 and d.sectionId=?6 and d.questionId= ?7");
        query.setParameter(1, value);
        query.setParameter(2, formId);
        query.setParameter(3, startDate);
        query.setParameter(4, endDate);
        query.setParameter(5, pageNumber);
        query.setParameter(6, sectionId);
        query.setParameter(7, questionId);

        BigInteger result = (BigInteger) query.getSingleResult();

        return result.intValue();
    }


    public List<CaisiFormData> query1(Integer formId, Date startDate, Date endDate, int pageNumber, int sectionId, int questionId) {
        Query query = entityManager.createQuery("select distinct d from CaisiFormInstance i, CaisiFormData d where i.formId=?1 and i.dateCreated>=?2 and i.dateCreated<=?3 and i.id=d.instanceId and d.pageNumber=?4 and d.sectionId=?5 and d.questionId= ?6 group by d.key, d.value");
        query.setParameter(1, formId);
        query.setParameter(2, startDate);
        query.setParameter(3, endDate);
        query.setParameter(4, pageNumber);
        query.setParameter(5, sectionId);
        query.setParameter(6, questionId);

        @SuppressWarnings("unchecked")
        List<CaisiFormData> result = query.getResultList();

        return result;
    }

    public List<CaisiFormData> query2(Integer formId, Date startDate, Date endDate, int pageNumber, int sectionId, int questionId) {
        Query query = entityManager.createQuery("select distinct d from CaisiFormInstance i, CaisiFormData d where i.formId=?1 and i.dateCreated>=?2 and i.dateCreated<=?3 and i.id=d.instanceId and d.pageNumber=?4 and d.sectionId=?5 and d.questionId= ?6 group by d.value");
        query.setParameter(1, formId);
        query.setParameter(2, startDate);
        query.setParameter(3, endDate);
        query.setParameter(4, pageNumber);
        query.setParameter(5, sectionId);
        query.setParameter(6, questionId);

        @SuppressWarnings("unchecked")
        List<CaisiFormData> result = query.getResultList();

        return result;
    }
}
