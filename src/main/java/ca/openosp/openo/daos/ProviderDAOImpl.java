//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */

package ca.openosp.openo.daos;

import java.util.List;

import ca.openosp.openo.commn.model.Provider;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Data Access Object (DAO) implementation for Provider entities.
 * <p>
 * This DAO provides methods to retrieve Provider entities from the database
 * using Hibernate SessionFactory for database access. It has been migrated
 * from the deprecated HibernateDaoSupport to direct SessionFactory injection
 * as part of the Spring 6/Jakarta EE migration effort.
 * </p>
 * <p>
 * Key operations supported:
 * </p>
 * <ul>
 *   <li>Retrieve all providers ordered by last name</li>
 *   <li>Retrieve a specific provider by provider number</li>
 *   <li>Retrieve a provider by first and last name</li>
 * </ul>
 * 
 * @see Provider
 * @see ProviderDAO
 */
public class ProviderDAOImpl implements ProviderDAO {

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session.
     * 
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all providers from the database, ordered by last name.
     * 
     * @return a list of all providers ordered by last name
     */
    @SuppressWarnings("unchecked")
    public List<Provider> getProviders() {
        return getSession().createQuery("from Provider p order by p.lastName").list();
    }

    /**
     * Retrieves a specific provider by provider number.
     * 
     * @param provider_no the unique provider number identifier
     * @return the Provider entity, or null if not found
     */
    public Provider getProvider(String provider_no) {
        return getSession().get(Provider.class, provider_no);
    }

    /**
     * Retrieves a provider by first and last name.
     * 
     * @param lastName the provider's last name
     * @param firstName the provider's first name
     * @return the Provider entity matching the given names
     * @throws IndexOutOfBoundsException if no provider is found with the given names
     */
    @SuppressWarnings("unchecked")
    public Provider getProviderByName(String lastName, String firstName) {
        List<Provider> providers = getSession()
            .createQuery("from Provider p where p.firstName = :firstName and p.lastName = :lastName")
            .setParameter("firstName", firstName)
            .setParameter("lastName", lastName)
            .list();
        return providers.get(0);
    }

}
