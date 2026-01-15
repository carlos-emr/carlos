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

import java.util.List;

import ca.openosp.openo.casemgmt.model.CaseManagementNoteLink;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data Access Object implementation for CaseManagementNoteLink entity.
 * Provides CRUD operations and query methods for managing links between case management notes
 * and other clinical entities in the OpenO EMR system.
 *
 * <p>This DAO supports linking clinical notes to various table entities through a generic
 * table name and ID mechanism, enabling flexible associations between notes and other
 * healthcare data.</p>
 *
 * @since 2012-06-15
 */
@Transactional
public class CaseManagementNoteLinkDAOImpl implements CaseManagementNoteLinkDAO {

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session from the session factory.
     *
     * @return Session the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves a case management note link by its unique identifier.
     *
     * @param id Long the unique identifier of the note link
     * @return CaseManagementNoteLink the note link entity, or null if not found
     */
    @Override
    public CaseManagementNoteLink getNoteLink(Long id) {
        return getSession().get(CaseManagementNoteLink.class, id);
    }

    /**
     * Retrieves all note links for a specific table and table ID, ordered by link ID ascending.
     *
     * @param tableName Integer the table identifier (e.g., demographic table)
     * @param tableId Long the unique identifier within the specified table
     * @return List&lt;CaseManagementNoteLink&gt; list of note links, ordered by ID ascending
     */
    @Override
    public List<CaseManagementNoteLink> getLinkByTableId(Integer tableName, Long tableId) {
        String hql = "from CaseManagementNoteLink cLink where cLink.tableName = :tableName and cLink.tableId = :tableId order by cLink.id";
        Query<CaseManagementNoteLink> query = getSession().createQuery(hql, CaseManagementNoteLink.class);
        query.setParameter("tableName", tableName);
        query.setParameter("tableId", tableId);
        return query.list();
    }

    /**
     * Retrieves all note links for a specific table, table ID, and other ID, ordered by link ID ascending.
     *
     * @param tableName Integer the table identifier
     * @param tableId Long the unique identifier within the specified table
     * @param otherId String additional identifier for filtering links
     * @return List&lt;CaseManagementNoteLink&gt; list of note links matching all criteria, ordered by ID ascending
     */
    @Override
    public List<CaseManagementNoteLink> getLinkByTableId(Integer tableName, Long tableId, String otherId) {
        String hql = "from CaseManagementNoteLink cLink where cLink.tableName = :tableName and cLink.tableId = :tableId and cLink.otherId = :otherId order by cLink.id";
        Query<CaseManagementNoteLink> query = getSession().createQuery(hql, CaseManagementNoteLink.class);
        query.setParameter("tableName", tableName);
        query.setParameter("tableId", tableId);
        query.setParameter("otherId", otherId);
        return query.list();
    }

    /**
     * Retrieves all note links for a specific table and table ID, ordered by link ID descending.
     *
     * @param tableName Integer the table identifier
     * @param tableId Long the unique identifier within the specified table
     * @return List&lt;CaseManagementNoteLink&gt; list of note links, ordered by ID descending (most recent first)
     */
    @Override
    public List<CaseManagementNoteLink> getLinkByTableIdDesc(Integer tableName, Long tableId) {
        String hql = "from CaseManagementNoteLink cLink where cLink.tableName = :tableName and cLink.tableId = :tableId order by cLink.id desc";
        Query<CaseManagementNoteLink> query = getSession().createQuery(hql, CaseManagementNoteLink.class);
        query.setParameter("tableName", tableName);
        query.setParameter("tableId", tableId);
        return query.list();
    }

    /**
     * Retrieves all note links for a specific table, table ID, and other ID, ordered by link ID descending.
     *
     * @param tableName Integer the table identifier
     * @param tableId Long the unique identifier within the specified table
     * @param otherId String additional identifier for filtering links
     * @return List&lt;CaseManagementNoteLink&gt; list of note links matching all criteria, ordered by ID descending
     */
    @Override
    public List<CaseManagementNoteLink> getLinkByTableIdDesc(Integer tableName, Long tableId, String otherId) {
        String hql = "from CaseManagementNoteLink cLink where cLink.tableName = :tableName and cLink.tableId = :tableId and cLink.otherId = :otherId order by cLink.id desc";
        Query<CaseManagementNoteLink> query = getSession().createQuery(hql, CaseManagementNoteLink.class);
        query.setParameter("tableName", tableName);
        query.setParameter("tableId", tableId);
        query.setParameter("otherId", otherId);
        return query.list();
    }

    /**
     * Retrieves all note links associated with a specific note ID, ordered by link ID ascending.
     *
     * @param noteId Long the unique identifier of the case management note
     * @return List&lt;CaseManagementNoteLink&gt; list of note links for the specified note, ordered by ID ascending
     */
    @Override
    public List<CaseManagementNoteLink> getLinkByNote(Long noteId) {
        String hql = "from CaseManagementNoteLink cLink where cLink.noteId = :noteId order by cLink.id";
        Query<CaseManagementNoteLink> query = getSession().createQuery(hql, CaseManagementNoteLink.class);
        query.setParameter("noteId", noteId);
        return query.list();
    }

    /**
     * Retrieves the most recent note link for a specific table, table ID, and other ID.
     *
     * @param tableName Integer the table identifier
     * @param tableId Long the unique identifier within the specified table
     * @param otherId String additional identifier for filtering links
     * @return CaseManagementNoteLink the last (most recent) note link, or null if no links exist
     */
    @Override
    public CaseManagementNoteLink getLastLinkByTableId(Integer tableName, Long tableId, String otherId) {
        return getLast(getLinkByTableId(tableName, tableId, otherId));
    }

    /**
     * Retrieves the most recent note link for a specific table and table ID.
     *
     * @param tableName Integer the table identifier
     * @param tableId Long the unique identifier within the specified table
     * @return CaseManagementNoteLink the last (most recent) note link, or null if no links exist
     */
    @Override
    public CaseManagementNoteLink getLastLinkByTableId(Integer tableName, Long tableId) {
        return getLast(getLinkByTableId(tableName, tableId));
    }

    /**
     * Retrieves the most recent note link for a specific note ID.
     *
     * @param noteId Long the unique identifier of the case management note
     * @return CaseManagementNoteLink the last (most recent) note link, or null if no links exist
     */
    @Override
    public CaseManagementNoteLink getLastLinkByNote(Long noteId) {
        return getLast(getLinkByNote(noteId));
    }

    /**
     * Helper method to retrieve the last element from a list of note links.
     *
     * @param listLink List&lt;CaseManagementNoteLink&gt; the list of note links
     * @return CaseManagementNoteLink the last element in the list, or null if the list is empty
     */
    private CaseManagementNoteLink getLast(List<CaseManagementNoteLink> listLink) {
        if (listLink.isEmpty())
            return null;
        return listLink.get(listLink.size() - 1);
    }

    /**
     * Persists a new case management note link to the database.
     *
     * @param cLink CaseManagementNoteLink the note link entity to save
     */
    @Override
    public void save(CaseManagementNoteLink cLink) {
        getSession().save(cLink);
    }

    /**
     * Updates an existing case management note link in the database.
     *
     * @param cLink CaseManagementNoteLink the note link entity to update
     */
    @Override
    public void update(CaseManagementNoteLink cLink) {
        getSession().update(cLink);
    }
}
