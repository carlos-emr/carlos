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

package ca.openosp.openo.casemgmt.dao;

import java.util.Date;
import java.util.List;

import ca.openosp.openo.casemgmt.model.CaseManagementNoteExt;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data Access Object implementation for CaseManagementNoteExt entities.
 * 
 * This DAO encapsulates Hibernate-based persistence operations for creating, reading, updating,
 * and querying case management note extensions, which store additional metadata and attributes
 * associated with core case management notes within the OpenO EMR case management module.
 * <p>
 * This class is annotated with {@link org.springframework.transaction.annotation.Transactional}
 * at the class level, so all public methods participate in Spring-managed transactions using
 * the default Spring transaction semantics.
 * 
 * @see CaseManagementNoteExt
 * @see CaseManagementNoteExtDAO
 */
@Transactional
public class CaseManagementNoteExtDAOImpl implements CaseManagementNoteExtDAO {

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
     * Retrieves a case management note extension by its ID.
     *
     * @param id the unique identifier of the note extension
     * @return the CaseManagementNoteExt entity, or null if not found
     */
    @Override
    public CaseManagementNoteExt getNoteExt(Long id) {
        return getSession().get(CaseManagementNoteExt.class, id);
    }

    /**
     * Retrieves all note extensions associated with a specific note ID.
     *
     * @param noteId the ID of the note
     * @return list of CaseManagementNoteExt entities ordered by ID descending
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNoteExt> getExtByNote(Long noteId) {
        String hql = "from CaseManagementNoteExt cExt where cExt.noteId = :noteId order by cExt.id desc";
        Query<CaseManagementNoteExt> query = getSession().createQuery(hql, CaseManagementNoteExt.class);
        query.setParameter("noteId", noteId);
        return query.list();
    }

    /**
     * Retrieves all note extensions with a specific key value.
     *
     * @param keyVal the key value to search for
     * @return list of CaseManagementNoteExt entities matching the key value
     */
    @SuppressWarnings("unchecked")
    @Override
    public List getExtByKeyVal(String keyVal) {
        String hql = "from CaseManagementNoteExt cExt where cExt.keyVal = :keyVal";
        Query<CaseManagementNoteExt> query = getSession().createQuery(hql, CaseManagementNoteExt.class);
        query.setParameter("keyVal", keyVal);
        return query.list();
    }

    /**
     * Retrieves all note extensions with a specific key value and value pattern.
     *
     * @param keyVal the key value to search for
     * @param value the value pattern to match using SQL LIKE semantics.
     *              Callers are responsible for including any required LIKE wildcards
     *              (for example, {@code "%value%"}, {@code "value%"}, or {@code "%value"}).
     * @return list of CaseManagementNoteExt entities matching the criteria
     */
    @SuppressWarnings("unchecked")
    @Override
    public List getExtByValue(String keyVal, String value) {
        String hql = "from CaseManagementNoteExt cExt where cExt.keyVal = :keyVal and cExt.value like :value";
        Query<CaseManagementNoteExt> query = getSession().createQuery(hql, CaseManagementNoteExt.class);
        query.setParameter("keyVal", keyVal);
        query.setParameter("value", value);
        return query.list();
    }

    /**
     * Retrieves all note extensions with a specific key value and date value before or equal to the specified date.
     *
     * @param keyVal the key value to search for
     * @param dateValue the date threshold (inclusive)
     * @return list of CaseManagementNoteExt entities with date values before or equal to the specified date
     */
    @SuppressWarnings("unchecked")
    @Override
    public List getExtBeforeDate(String keyVal, Date dateValue) {
        String hql = "from CaseManagementNoteExt cExt where cExt.keyVal = :keyVal and cExt.dateValue <= :dateValue";
        Query<CaseManagementNoteExt> query = getSession().createQuery(hql, CaseManagementNoteExt.class);
        query.setParameter("keyVal", keyVal);
        query.setParameter("dateValue", dateValue);
        return query.list();
    }

    /**
     * Retrieves all note extensions with a specific key value and date value after or equal to the specified date.
     *
     * @param keyVal the key value to search for
     * @param dateValue the date threshold (inclusive)
     * @return list of CaseManagementNoteExt entities with date values after or equal to the specified date
     */
    @SuppressWarnings("unchecked")
    @Override
    public List getExtAfterDate(String keyVal, Date dateValue) {
        String hql = "from CaseManagementNoteExt cExt where cExt.keyVal = :keyVal and cExt.dateValue >= :dateValue";
        Query<CaseManagementNoteExt> query = getSession().createQuery(hql, CaseManagementNoteExt.class);
        query.setParameter("keyVal", keyVal);
        query.setParameter("dateValue", dateValue);
        return query.list();
    }

    /**
     * Persists a new case management note extension to the database.
     *
     * @param cExt the CaseManagementNoteExt entity to save
     */
    @Override
    public void save(CaseManagementNoteExt cExt) {
        getSession().save(cExt);
    }

    /**
     * Updates an existing case management note extension in the database.
     *
     * @param cExt the CaseManagementNoteExt entity to update
     */
    @Override
    public void update(CaseManagementNoteExt cExt) {
        getSession().update(cExt);
    }
}
