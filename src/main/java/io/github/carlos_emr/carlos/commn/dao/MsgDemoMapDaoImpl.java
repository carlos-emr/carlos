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

import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link MsgDemoMapDao} for message data access.
 *
 * @since 2001
 */

public class MsgDemoMapDaoImpl extends AbstractDaoImpl<MsgDemoMap> implements MsgDemoMapDao {

    /** Constructs this DAO for the {@link MsgDemoMap} entity class. */

    public MsgDemoMapDaoImpl() {
        super(MsgDemoMap.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<MsgDemoMap> findByDemographicNo(Integer demographicNo) {
        String sql = "select x from MsgDemoMap x where x.demographic_no=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);

        @SuppressWarnings("unchecked")
        List<MsgDemoMap> results = query.getResultList();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public List<MsgDemoMap> findByMessageId(Integer messageId) {
        String sql = "select x from MsgDemoMap x where x.messageID=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, messageId);

        @SuppressWarnings("unchecked")
        List<MsgDemoMap> results = query.getResultList();
        return results;
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<Object[]> getMessagesAndDemographicsByMessageId(Integer messageId) {
        String sql = "SELECT m, d FROM MsgDemoMap m, Demographic d WHERE m.messageID = :msgId AND d.DemographicNo = m.demographic_no ORDER BY d.LastName, d.FirstName";
        Query query = entityManager.createQuery(sql);
        query.setParameter("msgId", messageId);
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public List<Object[]> getMapAndMessagesByDemographicNo(Integer demoNo) {
        // TODO Auto-generated method stub
        String sql = "SELECT map, m FROM MsgDemoMap map, MessageTbl m WHERE m.id = map.messageID AND map.demographic_no = :demoNo ORDER BY m.date DESC, m.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter("demoNo", demoNo);
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public List<Object[]> getMapAndMessagesByDemographicNoAndType(Integer demoNo, Integer type) {
        String sql = "SELECT map, m FROM MsgDemoMap map, MessageTbl m WHERE m.id = map.messageID AND map.demographic_no = :demoNo AND m.type = :type ORDER BY m.date DESC, m.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter("demoNo", demoNo);
        query.setParameter("type", type);
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public void remove(Integer messageID, Integer demographicNo) {
        String sql = "select x from MsgDemoMap x where x.messageID = :id and x.demographic_no = :demoNo";
        Query query = entityManager.createQuery(sql);
        query.setParameter("id", messageID);
        query.setParameter("demoNo", demographicNo);

        List<MsgDemoMap> list = query.getResultList();
        for (MsgDemoMap demoMap : list) {
            this.remove(demoMap.getId());
        }

    }
}
