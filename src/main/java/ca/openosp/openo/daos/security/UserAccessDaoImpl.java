//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 * <p>
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.daos.security;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Data Access Object implementation for UserAccess entities.
 * <p>
 * This DAO provides methods to retrieve user access information including
 * access lists and organization access lists for providers. It has been
 * migrated from HibernateDaoSupport to direct SessionFactory injection
 * for compatibility with Spring 6 and Jakarta EE.
 * </p>
 *
 * @see UserAccessDao
 * @see ca.openosp.openo.model.security.UserAccessValue
 */
public class UserAccessDaoImpl implements UserAccessDao {
    
    @Autowired
    private SessionFactory sessionFactory;
    
    /**
     * Gets the current Hibernate session from the SessionFactory.
     *
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves the user access list for a specific provider, optionally filtered by shelter ID.
     *
     * @param providerNo the provider number to retrieve access for
     * @param shelterId optional shelter ID to filter by (can be null)
     * @return list of UserAccessValue objects representing the provider's access rights
     */
    @Override
    public List GetUserAccessList(String providerNo, Integer shelterId) {
        String sSQL = "";
        if (shelterId != null && shelterId.intValue() > 0) {
            String s = "'%S" + shelterId.toString() + ",%'";
            sSQL = "from UserAccessValue s where s.providerNo= :providerNo " +
                    " and s.orgCdcsv like " + s + " order by s.functionCd, s.privilege desc, s.orgCd";
        } else {
            sSQL = "from UserAccessValue s where s.providerNo= :providerNo " +
                    " order by s.functionCd, s.privilege desc, s.orgCd";
        }
        Query query = getSession().createQuery(sSQL);
        query.setParameter("providerNo", providerNo);
        return query.list();
    }

    /**
     * Retrieves the organization access list for a specific provider, optionally filtered by shelter ID.
     *
     * @param providerNo the provider number to retrieve organization access for
     * @param shelterId optional shelter ID to filter by (can be null)
     * @return list of organization code CSV strings that the provider has access to
     */
    @Override
    public List GetUserOrgAccessList(String providerNo, Integer shelterId) {
        String sSQL = "";
        if (shelterId != null && shelterId.intValue() > 0) {
            sSQL = "select distinct o.codecsv from UserAccessValue s, LstOrgcd o " +
                    "where s.providerNo= :providerNo and s.privilege>='r' and s.orgCd=o.code " +
                    " and o.codecsv like '%S" + shelterId.toString() + ",%'" +
                    " order by o.codecsv";
            Query query = getSession().createQuery(sSQL);
            query.setParameter("providerNo", providerNo);
            return query.list();
        } else {
            sSQL = "select distinct o.codecsv from UserAccessValue s, LstOrgcd o where s.providerNo= :providerNo and s.privilege>='r' and s.orgCd=o.code order by o.codecsv";
            Query query = getSession().createQuery(sSQL);
            query.setParameter("providerNo", providerNo);
            return query.list();
        }
    }
}
