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

import java.util.Hashtable;
import java.util.List;
import jakarta.persistence.Query;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.UserDSMessagePrefs;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
/**
 * JPA implementation of {@link UserDSMessagePrefsDao} for user data access.
 *
 * @since 2001
 */

public class UserDSMessagePrefsDaoImpl extends AbstractDaoImpl<UserDSMessagePrefs> implements UserDSMessagePrefsDao {
    private static Logger logger = MiscUtils.getLogger();

    /** Constructs this DAO for the {@link UserDSMessagePrefs} entity class. */

    public UserDSMessagePrefsDaoImpl() {
        super(UserDSMessagePrefs.class);
    }

    /** {@inheritDoc} */

    @Override
    public void saveProp(UserDSMessagePrefs prop) {
        this.persist(prop);
    }

    /** {@inheritDoc} */

    @Override
    public void updateProp(UserDSMessagePrefs prop) {
        this.merge(prop);
    }

    /** {@inheritDoc} */

    @Override
    public UserDSMessagePrefs getMessagePrefsOnType(String prov, String name) {
        Query query = entityManager.createQuery("SELECT p FROM UserDSMessagePrefs p WHERE p.providerNo=?1 and p.resourceType=?2 and p.archived=true");
        query.setParameter(1, prov);
        query.setParameter(2, name);

        List<UserDSMessagePrefs> list = query.getResultList();
        if (list != null && list.size() > 0) {
            UserDSMessagePrefs prop = list.get(0);
            return prop;
        } else return null;
    }

    /** {@inheritDoc} */

    @Override
    public Hashtable<String, Long> getHashofMessages(String providerNo, String name) {
        Query query = entityManager.createQuery("SELECT p FROM UserDSMessagePrefs p WHERE p.providerNo=?1 and p.resourceType=?2 and p.archived=true");
        query.setParameter(1, providerNo);
        query.setParameter(2, name);

        Hashtable<String, Long> retHash = new Hashtable<String, Long>();

        List<UserDSMessagePrefs> list = query.getResultList();
        logger.debug("dsmessage list size " + list.size() + " for providerNo " + providerNo + " and resource Type " + name);
        if (list != null && list.size() > 0) {
            for (UserDSMessagePrefs pref : list) {
                retHash.put(pref.getResourceType() + pref.getResourceId(), pref.getResourceUpdatedDate().getTime());
            }
        }
        return retHash;
    }

    /** {@inheritDoc} */

    @Override
    public List<UserDSMessagePrefs> findMessages(String providerNo, String resourceType, String resourceId, boolean archived) {
        Query query = createQuery("p", "p.providerNo = ?1 AND p.resourceType = ?2 and p.resourceId = ?3 and p.archived = ?4");
        query.setParameter(1, providerNo);
        query.setParameter(2, resourceType);
        query.setParameter(3, resourceId);
        query.setParameter(4, Boolean.valueOf(archived));
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public UserDSMessagePrefs getDsMessage(String providerNo, String resourceType, String resourceId, boolean archived) {
        Query query = entityManager.createQuery("SELECT p FROM UserDSMessagePrefs p WHERE p.providerNo=?1 " +
        "and p.resourceType=?2 and p.resourceId=?3 and p.archived = ?4 order by p.id DESC");
        query.setParameter(1, providerNo);
        query.setParameter(2, resourceType);
        query.setParameter(3, resourceId);
        query.setParameter(4, archived);

        List<UserDSMessagePrefs> list = query.getResultList();

        UserDSMessagePrefs pref = new UserDSMessagePrefs();
        if (list != null && list.size() > 0) {
            pref = list.get(0);
        }
        return pref;
    }

    /** {@inheritDoc} */

    @Override
    public List<UserDSMessagePrefs> findAllByResourceId(String resourceId) {
        Query query = createQuery("p", "p.resourceId = ?1");
        query.setParameter(1, resourceId);
        return query.getResultList();
    }
}
