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

package ca.openosp.openo.PMmodule.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import ca.openosp.openo.PMmodule.model.FormInfo;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object implementation for managing form-related database operations in the PM module.
 * This class handles persistence operations for form data including saving forms and retrieving form information.
 * Migrated from HibernateDaoSupport to direct SessionFactory injection for Spring 6 compatibility.
 *
 * @since 2.0
 */
public class FormsDAOImpl implements FormsDAO {

    private Logger log = MiscUtils.getLogger();

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session from the session factory.
     *
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Saves a form object to the database.
     *
     * @param o the form object to save
     */
    public void saveForm(Object o) {
        getSession().save(o);

        if (log.isDebugEnabled()) {
            log.debug("saveForm:" + o);
        }
    }

    /**
     * Retrieves the current form for a given client and form class.
     *
     * @param clientId the ID of the client
     * @param clazz the class type of the form to retrieve
     * @return the current form object if found, null otherwise
     * @throws IllegalArgumentException if clientId or clazz is null
     */
    public Object getCurrentForm(String clientId, Class clazz) {
        Object result = null;

        if (clientId == null || clazz == null) {
            throw new IllegalArgumentException();
        }

        String className = clazz.getName();
        if (className.indexOf(".") != -1) {
            className = className.substring(className.lastIndexOf(".") + 1);
        }
        String sSQL = "from " + className + " f where f.DemographicNo=:clientId";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("clientId", clientId);
        List results = query.list();
        if (results.size() > 0) {
            result = results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getCurrentForm: clientId=" + clientId + ",class=" + clazz + ",found=" + (result != null));
        }

        return result;
    }

    /**
     * Retrieves form information for a given client and form class.
     * Returns a list of form metadata including form ID, provider information, and edit dates.
     *
     * @param clientId the ID of the client
     * @param clazz the class type of the form to retrieve information for
     * @return a list of FormInfo objects containing form metadata
     * @throws IllegalArgumentException if clientId or clazz is null
     */
    public List getFormInfo(String clientId, Class clazz) {
        if (clientId == null || clazz == null) {
            throw new IllegalArgumentException();
        }

        List<FormInfo> formInfos = new ArrayList<FormInfo>();
        String className = clazz.getName();
        if (className.indexOf(".") != -1) {
            className = className.substring(className.lastIndexOf(".") + 1);
        }
        String sSQL = "select f.id,f.ProviderNo,f.FormEdited from " + className + " f where f.DemographicNo=:clientId order by f.FormEdited DESC";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("clientId", Long.valueOf(clientId));
        List results = query.list();
        for (Iterator iter = results.iterator(); iter.hasNext(); ) {
            FormInfo fi = new FormInfo();
            Object[] values = (Object[]) iter.next();
            Long id = (Long) values[0];
            Long providerNo = (Long) values[1];
            Date dateEdited = (Date) values[2];
            Provider provider = getSession().get(Provider.class, String.valueOf(providerNo));
            fi.setFormId(id);
            fi.setProviderNo(providerNo);
            fi.setFormDate(dateEdited);
            fi.setProviderName(provider.getFormattedName());
            formInfos.add(fi);
        }

        if (log.isDebugEnabled()) {
            log.debug("getFormInfo: clientId=" + clientId + ",class=" + clazz + ",# of results=" + formInfos.size());
        }

        return formInfos;
    }
}
