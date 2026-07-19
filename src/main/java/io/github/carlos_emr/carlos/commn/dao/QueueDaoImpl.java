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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.Queue;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

@Repository
public class QueueDaoImpl extends AbstractDaoImpl<Queue> implements QueueDao {
    public QueueDaoImpl() {
        super(Queue.class);
    }

    @Override
    public HashMap getHashMapOfQueues() {
        String q = "select q from Queue q";
        Query query = entityManager.createQuery(q);
        List<Queue> result = new ArrayList<Queue>();
        result = query.getResultList();
        HashMap<Integer, String> hm = new HashMap<Integer, String>();
        for (Queue que : result) {
            hm.put(que.getId(), que.getName());
        }
        return hm;
    }

    @Override
    public List<Hashtable> getQueues() {
        String q = "select q from Queue q";
        Query query = entityManager.createQuery(q);
        List<Queue> result = new ArrayList<Queue>();
        result = query.getResultList();
        List<Hashtable> r = new ArrayList();
        for (Queue que : result) {
            Hashtable ht = new Hashtable();
            ht.put("id", que.getId());
            ht.put("queue", que.getName());
            r.add(ht);
        }
        return r;
    }

    @Override
    public String getLastId() {
        String r = "";
        try {
            Query query = entityManager.createQuery("select MAX(q.id) from Queue q");
            Integer ri = (Integer) query.getSingleResult();
            r = ri.toString();
        } catch (NoResultException e) {
            //ignore
        }

        return r;
    }

    @Override
    public String getQueueName(int id) {

        String q = "select q from Queue q where q.id=" + id;
        Query query = entityManager.createQuery(q);
        try {
            Queue result = (Queue) query.getSingleResult();
            return result.getName();
        } catch (NoResultException e) {
            //ignore
        }
        return "";
    }

    @Override
    public String getQueueid(String name) {
        String q = "select q from Queue q where q.name=" + name;
        Query query = entityManager.createQuery(q);
        try {
            Queue result = (Queue) query.getSingleResult();
            return result.getId().toString();
        } catch (NoResultException e) {
            //ignore
        }
        return "";
    }

    @Override
    public boolean addNewQueue(String qn) {
        try {
            Queue q = new Queue();
            q.setName(qn);
            entityManager.persist(q);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            return false;
        }
        return true;
    }
}
