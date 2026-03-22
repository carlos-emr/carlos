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

import io.github.carlos_emr.carlos.commn.model.MessageList;

/**
 * DAO interface for messaging operations.
 *
 * @since 2001
 */

public interface MessageListDao extends AbstractDao<MessageList> {

    /**
     * Find By Provider No And Message No.
     *
     * @param providerNo String the providerNo
     * @param messageNo Long the messageNo
     * @return List<MessageList>
     */
    public List<MessageList> findByProviderNoAndMessageNo(String providerNo, Long messageNo);

    /**
     * Find By Provider No And Location No.
     *
     * @param providerNo String the providerNo
     * @param locationNo Integer the locationNo
     * @return List<MessageList>
     */
    public List<MessageList> findByProviderNoAndLocationNo(String providerNo, Integer locationNo);

    /**
     * Find All By Message No And Location No.
     *
     * @param messageNo Long the messageNo
     * @param locationNo Integer the locationNo
     * @return List<MessageList>
     */
    public List<MessageList> findAllByMessageNoAndLocationNo(Long messageNo, Integer locationNo);

    /**
     * Find By Message No And Location No.
     *
     * @param messageNo Long the messageNo
     * @param locationNo Integer the locationNo
     * @return List<MessageList>
     */
    public List<MessageList> findByMessageNoAndLocationNo(Long messageNo, Integer locationNo);

    /**
     * Find By Message.
     *
     * @param messageNo Long the messageNo
     * @return List<MessageList>
     */
    public List<MessageList> findByMessage(Long messageNo);

    /**
     * Find By Provider And Status.
     *
     * @param providerNo String the providerNo
     * @param status String the status
     * @return List<MessageList>
     */
    public List<MessageList> findByProviderAndStatus(String providerNo, String status);

    /**
     * Find Unread By Provider.
     *
     * @param providerNo String the providerNo
     * @return List<MessageList>
     */
    public List<MessageList> findUnreadByProvider(String providerNo);

    /**
     * Find Unread By Provider And Attached Count.
     *
     * @param providerNo String the providerNo
     * @return int
     */
    public int findUnreadByProviderAndAttachedCount(String providerNo);

    /**
     * Search.
     *
     * @param providerNo String the providerNo
     * @param status String the status
     * @param start int the start
     * @param max int the max
     * @return List<MessageList>
     */
    public List<MessageList> search(String providerNo, String status, int start, int max);

    /**
     * Search And Return Total.
     *
     * @param providerNo String the providerNo
     * @param status String the status
     * @return Integer
     */
    public Integer searchAndReturnTotal(String providerNo, String status);

    /**
     * Messages Total.
     *
     * @param type int the type
     * @param providerNo String the providerNo
     * @param remoteLocation Integer the remoteLocation
     * @param searchFilter String the searchFilter
     * @return Integer
     */
    public Integer messagesTotal(int type, String providerNo, Integer remoteLocation, String searchFilter);

}
