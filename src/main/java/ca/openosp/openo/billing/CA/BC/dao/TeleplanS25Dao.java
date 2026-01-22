//CHECKSTYLE:OFF
/**
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
 */


package ca.openosp.openo.billing.CA.BC.dao;

import java.util.List;

import javax.persistence.Query;

import ca.openosp.openo.billing.CA.BC.model.TeleplanS25;
import ca.openosp.openo.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

@Repository
public class TeleplanS25Dao extends AbstractDaoImpl<TeleplanS25> {

    public TeleplanS25Dao() {
        super(TeleplanS25.class);
    }

    @SuppressWarnings("unchecked")
    public List<TeleplanS25> search_taS25(Integer s21Id, String type, String practitionerNo) {
        Query q = entityManager.createQuery("select t from TeleplanS25 t where t.s21Id=?1 and t.s25Type<>?2 and t.practitionerNo like ?3 order by t.id, t.practitionerNo");
        q.setParameter(1, s21Id);
        q.setParameter(2, type);
        q.setParameter(3, practitionerNo);
        return q.getResultList();
    }
}
