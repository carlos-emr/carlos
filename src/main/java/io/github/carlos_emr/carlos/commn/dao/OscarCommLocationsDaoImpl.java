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
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;
import org.springframework.stereotype.Repository;

@Repository
public class OscarCommLocationsDaoImpl extends AbstractDaoImpl<OscarCommLocations> implements OscarCommLocationsDao {

    public OscarCommLocationsDaoImpl() {
        super(OscarCommLocations.class);
    }

    @Override
    public List<OscarCommLocations> findByCurrent1(int current1) {
        Query q = entityManager.createQuery("SELECT x FROM OscarCommLocations x WHERE x.current1=?1");
        q.setParameter(1, current1);

        @SuppressWarnings("unchecked")
        List<OscarCommLocations> results = q.getResultList();

        return results;

    }

    @NativeSql({"messagetbl", "oscarcommlocations"})
    @Override
    public List<Object[]> findFormLocationByMesssageId(String messId) {
        String sql = "select ocl.locationDesc, mess.thesubject from messagetbl mess, oscarcommlocations ocl where"
                + "mess.sentByLocation = ocl.locationId and mess.messageid = ?1 ";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, messId);
        return query.getResultList();
    }

    @NativeSql({"messagetbl", "oscarcommlocations"})
    @Override
    public List<Object[]> findAttachmentsByMessageId(String messageId) {
        String sql = "SELECT m.thesubject, m.theime, m.thedate, m.attachment, m.themessage, m.sentBy, ocl.locationDesc  "
                + "FROM messagetbl m, oscarcommlocations ocl where m.sentByLocation = ocl.locationId and "
                + " messageid = ?1";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, messageId);
        return query.getResultList();
    }
}
