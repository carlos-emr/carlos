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

package io.github.carlos_emr.carlos.casemgmt.dao;

import java.util.List;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class CaseManagementNoteLinkDAOImpl extends HibernateDaoSupport implements CaseManagementNoteLinkDAO {

    @Override
    public CaseManagementNoteLink getNoteLink(Long id) {
        CaseManagementNoteLink noteLink = this.getHibernateTemplate().get(CaseManagementNoteLink.class, id);
        return noteLink;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNoteLink> getLinkByTableId(Integer tableName, Long tableId) {
        Object[] param = {tableName, tableId};
        String hql = "from CaseManagementNoteLink cLink where cLink.tableName = ?0 and cLink.tableId = ?1 order by cLink.id";
        return (List<CaseManagementNoteLink>) this.getHibernateTemplate().find(hql, param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNoteLink> getLinkByTableId(Integer tableName, Long tableId, String otherId) {
        Object[] param = {tableName, tableId, otherId};
        String hql = "from CaseManagementNoteLink cLink where cLink.tableName = ?0 and cLink.tableId = ?1 and cLink.otherId=?2 order by cLink.id";
        return (List<CaseManagementNoteLink>) this.getHibernateTemplate().find(hql, param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNoteLink> getLinkByTableIdDesc(Integer tableName, Long tableId) {
        Object[] param = {tableName, tableId};
        String hql = "from CaseManagementNoteLink cLink where cLink.tableName = ?0 and cLink.tableId = ?1 order by cLink.id desc";
        return (List<CaseManagementNoteLink>) this.getHibernateTemplate().find(hql, param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNoteLink> getLinkByTableIdDesc(Integer tableName, Long tableId, String otherId) {
        Object[] param = {tableName, tableId, otherId};
        String hql = "from CaseManagementNoteLink cLink where cLink.tableName = ?0 and cLink.tableId = ?1 and cLink.otherId=?2 order by cLink.id desc";
        return (List<CaseManagementNoteLink>) this.getHibernateTemplate().find(hql, param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNoteLink> getLinkByNote(Long noteId) {
        String hql = "from CaseManagementNoteLink cLink where cLink.noteId = ?0 order by cLink.id";
        return (List<CaseManagementNoteLink>) this.getHibernateTemplate().find(hql, noteId);
    }

    @Override
    public CaseManagementNoteLink getLastLinkByTableId(Integer tableName, Long tableId, String otherId) {
        return getLast(getLinkByTableId(tableName, tableId, otherId));
    }

    @Override
    public CaseManagementNoteLink getLastLinkByTableId(Integer tableName, Long tableId) {
        return getLast(getLinkByTableId(tableName, tableId));
    }

    @Override
    public CaseManagementNoteLink getLastLinkByNote(Long noteId) {
        return getLast(getLinkByNote(noteId));
    }

    private CaseManagementNoteLink getLast(List<CaseManagementNoteLink> listLink) {
        if (listLink.isEmpty())
            return null;
        return listLink.get(listLink.size() - 1);
    }

    @Override
    public void save(CaseManagementNoteLink cLink) {
        this.getHibernateTemplate().save(cLink);
    }

    @Override
    public void update(CaseManagementNoteLink cLink) {
        this.getHibernateTemplate().update(cLink);
    }
}
