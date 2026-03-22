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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.daos.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

/**
 * Hibernate-based implementation of {@link UserAccessDao} for user access permission retrieval.
 *
 * @since 2005-01-01
 */
@Transactional
public class UserAccessDaoImpl extends AbstractHibernateDao implements UserAccessDao {

    @SuppressWarnings("unchecked")
    @Override
    public List GetUserAccessList(String providerNo, Integer shelterId) {
        if (shelterId != null && shelterId.intValue() > 0) {
            String shelterPattern = "%S" + shelterId.toString() + ",%";
            String hql = "from UserAccessValue s where s.providerNo = :providerNo and s.orgCdcsv like :shelterPattern order by s.functionCd, s.privilege desc, s.orgCd";
            Map<String, Object> params = new HashMap<>();
            params.put("providerNo", providerNo);
            params.put("shelterPattern", shelterPattern);
            return HqlQueryHelper.find(currentSession(), hql, params);
        } else {
            String sSQL = "from UserAccessValue s where s.providerNo= ?1 order by s.functionCd, s.privilege desc, s.orgCd";
            return HqlQueryHelper.find(currentSession(), sSQL, providerNo);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List GetUserOrgAccessList(String providerNo, Integer shelterId) {
        if (shelterId != null && shelterId.intValue() > 0) {
            String shelterPattern = "%S" + shelterId.toString() + ",%";
            String hql = "select distinct o.codecsv from UserAccessValue s, LstOrgcd o where s.providerNo = :providerNo and s.privilege >= 'r' and s.orgCd = o.code and o.codecsv like :shelterPattern order by o.codecsv";
            Map<String, Object> params = new HashMap<>();
            params.put("providerNo", providerNo);
            params.put("shelterPattern", shelterPattern);
            return HqlQueryHelper.find(currentSession(), hql, params);
        } else {
            String sSQL = "select distinct o.codecsv from UserAccessValue s, LstOrgcd o where s.providerNo= ?1 and s.privilege>='r' and s.orgCd=o.code order by o.codecsv";
            return HqlQueryHelper.find(currentSession(), sSQL, providerNo);
        }
    }
}
