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

import java.util.Collections;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.commn.model.OscarMsgType;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class MessageListDaoImpl extends AbstractDaoImpl<MessageList> implements MessageListDao {

    public MessageListDaoImpl() {
        super(MessageList.class);
    }

    @Override
    public List<MessageList> findByProviderNoAndMessageNo(String providerNo, Long messageNo) {
        Query query = createQuery("msg", "msg.providerNo = ?1 AND msg.message = ?2");
        query.setParameter(1, providerNo);
        query.setParameter(2, messageNo);
        return query.getResultList();
    }

    /**
     * Caution: excludes message recipients with a deleted status.
     */
    @Override
    public List<MessageList> findByProviderNoAndLocationNo(String providerNo, Integer locationNo) {
        Query query = createQuery("ml",
                "ml.providerNo = ?1 and ml.status not like 'del' and ml.remoteLocation = ?2 order by ml.message");
        query.setParameter(1, providerNo);
        query.setParameter(2, locationNo);
        return query.getResultList();
    }

    /**
     * Gets entire list of message recipients regardless of message status.
     *
     * @param messageNo
     * @param locationNo
     * @return
     */
    @Override
    public List<MessageList> findAllByMessageNoAndLocationNo(Long messageNo, Integer locationNo) {
        Query query = createQuery("ml",
                "ml.message = ?1 and ml.remoteLocation = ?2 order by ml.message");
        query.setParameter(1, messageNo);
        query.setParameter(2, locationNo);
        return query.getResultList();
    }

    /**
     * Caution: excludes message recipients with a deleted status.
     *
     * @param messageNo
     * @param locationNo
     * @return
     */
    @Override
    public List<MessageList> findByMessageNoAndLocationNo(Long messageNo, Integer locationNo) {
        Query query = createQuery("ml",
                "ml.message = ?1 and ml.status not like 'del' and ml.remoteLocation = ?2 order by ml.message");
        query.setParameter(1, messageNo);
        query.setParameter(2, locationNo);
        return query.getResultList();
    }

    @Override
    public List<MessageList> findByMessage(Long messageNo) {
        Query query = createQuery("ml", "ml.message = ?1");
        query.setParameter(1, messageNo);
        return query.getResultList();
    }

    @Override
    public List<MessageList> findByProviderAndStatus(String providerNo, String status) {
        Query query = createQuery("ml", "ml.providerNo = ?1 and ml.status = ?2");
        query.setParameter(1, providerNo);
        query.setParameter(2, status);
        return query.getResultList();
    }

    @Override
    public List<MessageList> findUnreadByProvider(String providerNo) {
        Query query = createQuery("ml", "ml.providerNo = ?1 and ml.status ='new'");
        query.setParameter(1, providerNo);
        return query.getResultList();
    }

    @Override
    public int findUnreadByProviderAndAttachedCount(String providerNo) {
        Query query = entityManager.createQuery(
                "select count(l) from MessageList l, MsgDemoMap m where l.providerNo= ?1 and l.status='new' and l.message=m.messageID");

        query.setParameter(1, providerNo);
        return getCountResult(query).intValue();

    }

    @Override
    public int countUnreadByProviderAndFromIntegratedFacility(String providerNo) {
        Query query = entityManager.createQuery(
                "SELECT count(l) FROM MessageList l, MessageTbl mt WHERE l.message = mt.id AND l.providerNo= ?1 AND l.status='new' AND mt.type = ?2");

        query.setParameter(1, providerNo);
        query.setParameter(2, OscarMsgType.INTEGRATOR_TYPE);
        return getCountResult(query).intValue();
    }

    @Override
    public int countUnreadByProvider(String providerNo) {
        Query query = entityManager.createQuery(
                "select count(l) from MessageList l where l.providerNo= ?1 and l.status='new' and l.sourceFacilityId > 0");

        query.setParameter(1, providerNo);
        return getCountResult(query).intValue();
    }

    @Override
    public List<MessageList> search(String providerNo, String status, int start, int max) {

        StringBuilder sql = new StringBuilder();
        sql.append("select ml from MessageList ml, MessageTbl mt where ml.message = mt.id");

        if (providerNo != null && !providerNo.isEmpty()) {
            sql.append(" AND ml.providerNo= ?1 ");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND ml.status = ?2 ");
        }

        sql.append(" ORDER BY mt.date DESC, mt.time DESC");

        Query query = entityManager.createQuery(sql.toString());

        if (providerNo != null && !providerNo.isEmpty()) {
            query.setParameter(1, providerNo);
        }
        if (status != null && !status.isEmpty()) {
            query.setParameter(2, status);
        }
        query.setFirstResult(start);
        setLimit(query, max);
        List<MessageList> result = query.getResultList();

        return result;
    }

    @Override
    public Integer searchAndReturnTotal(String providerNo, String status) {

        StringBuilder sql = new StringBuilder();
        sql.append("select count(ml) from MessageList ml, MessageTbl mt where ml.message = mt.id");

        if (providerNo != null && !providerNo.isEmpty()) {
            sql.append(" AND ml.providerNo= ?1 ");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND ml.status = ?2 ");
        }

        Query query = entityManager.createQuery(sql.toString());

        if (providerNo != null && !providerNo.isEmpty()) {
            query.setParameter(1, providerNo);
        }
        if (status != null && !status.isEmpty()) {
            query.setParameter(2, status);
        }

        Integer result = ((Long) query.getSingleResult()).intValue();

        return result;
    }

    @Override
    public Integer messagesTotal(int type, String providerNo, Integer remoteLocation, String searchFilter) {

        searchFilter = "%" + searchFilter + "%";

        StringBuilder sql = new StringBuilder();
        sql.append("select count(mt) from ");

        switch (type) {
            case 1:
                // sent
                sql.append("MessageTbl mt where mt.sentByNo= ?1 AND mt.sentByLocation = ?2 ");
                break;
            case 2:
                // deleted
                sql.append(
                        "MessageList ml, MessageTbl mt where ml.status LIKE 'del' AND ml.message = mt.id AND ml.providerNo= ?1 AND ml.remoteLocation = ?2 ");
                break;
            default:
                // inbox
                sql.append(
                        "MessageList ml, MessageTbl mt where ml.status !='del' AND ml.message = mt.id AND ml.providerNo= ?1 AND ml.remoteLocation = ?2 ");
                break;
        }

        if (searchFilter != null && !searchFilter.isEmpty()) {
            sql.append(
                    " AND (mt.subject Like ?3 OR mt.message Like ?4 OR mt.sentBy Like ?5 OR mt.sentTo Like ?6)");
        }

        Query query = entityManager.createQuery(sql.toString());

        // Else statement should never be reached, but added just to be safe
        if (providerNo != null && !providerNo.isEmpty() && remoteLocation != null) {
            query.setParameter(1, providerNo);
            query.setParameter(2, remoteLocation);
        } else {
            return 0;
        }

        if (searchFilter != null && !searchFilter.isEmpty()) {
            query.setParameter(3, searchFilter);
            query.setParameter(4, searchFilter);
            query.setParameter(5, searchFilter);
            query.setParameter(6, searchFilter);
        }

        Integer result = ((Long) query.getSingleResult()).intValue();

        return result;
    }

    @Override
    public List<MessageList> findByIntegratedFacility(int facilityId, String status) {
        Query query = createQuery("ml",
                "ml.status like ?1 and ml.destinationFacilityId = ?2 order by ml.id");
        query.setParameter(1, facilityId);
        query.setParameter(2, status);
        List<MessageList> results = query.getResultList();
        if (results == null) {
            results = Collections.emptyList();
        }
        return results;
    }

    @Override
    public List<MessageList> findByMessageAndIntegratedFacility(Long messageNo, int facilityId) {
        Query query = createQuery("ml",
                "ml.message = ?1 and ml.destinationFacilityId = ?2 order by ml.id");
        query.setParameter(1, messageNo);
        query.setParameter(2, facilityId);
        List<MessageList> results = query.getResultList();
        if (results == null) {
            results = Collections.emptyList();
        }
        return results;
    }

}
