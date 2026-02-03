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

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;

public class CaseManagementNoteExtDAOImpl extends HibernateDaoSupport implements CaseManagementNoteExtDAO {

    @Override
    public CaseManagementNoteExt getNoteExt(Long id) {
        CaseManagementNoteExt noteExt = this.getHibernateTemplate().get(CaseManagementNoteExt.class, id);
        return noteExt;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CaseManagementNoteExt> getExtByNote(Long noteId) {
        String hql = "from CaseManagementNoteExt cExt where cExt.noteId = ?0 order by cExt.id desc";
        return (List<CaseManagementNoteExt>) this.getHibernateTemplate().find(hql, noteId);
    }

    @Override
    public List getExtByKeyVal(String keyVal) {
        String hql = "from CaseManagementNoteExt cExt where cExt.keyVal = ?0";
        return this.getHibernateTemplate().find(hql, keyVal);
    }

    @Override
    public List getExtByValue(String keyVal, String value) {
        Object[] param = {keyVal, value};
        String hql = "from CaseManagementNoteExt cExt where cExt.keyVal = ?0 and cExt.value like ?1";
        return this.getHibernateTemplate().find(hql, param);
    }

    @Override
    public List getExtBeforeDate(String keyVal, Date dateValue) {
        Object[] param = {keyVal, dateValue};
        String hql = "from CaseManagementNoteExt cExt where cExt.keyVal = ?0 and cExt.dateValue <= ?1";
        return this.getHibernateTemplate().find(hql, param);
    }

    @Override
    public List getExtAfterDate(String keyVal, Date dateValue) {
        Object[] param = {keyVal, dateValue};
        String hql = "from CaseManagementNoteExt cExt where cExt.keyVal = ?0 and cExt.dateValue >= ?1";
        return this.getHibernateTemplate().find(hql, param);
    }

    @Override
    public void save(CaseManagementNoteExt cExt) {
        this.getHibernateTemplate().save(cExt);
    }

    @Override
    public void update(CaseManagementNoteExt cExt) {
        this.getHibernateTemplate().update(cExt);
    }
}
