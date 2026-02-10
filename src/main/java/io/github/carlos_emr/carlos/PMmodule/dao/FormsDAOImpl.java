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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import io.github.carlos_emr.carlos.PMmodule.model.FormInfo;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;

public class FormsDAOImpl extends HibernateDaoSupport implements FormsDAO {

    private Logger log = MiscUtils.getLogger();

    public void saveForm(Object o) {
        this.getHibernateTemplate().save(o);

        if (log.isDebugEnabled()) {
            log.debug("saveForm:" + o);
        }
    }

    public Object getCurrentForm(String clientId, Class clazz) {
        Object result = null;

        if (clientId == null || clazz == null) {
            throw new IllegalArgumentException();
        }

        // Use Criteria API to avoid entity name parameterization issues
        // Entity names cannot be parameterized in HQL/JPQL
        List results = this.getHibernateTemplate().findByCriteria(
            DetachedCriteria.forClass(clazz)
                .add(org.hibernate.criterion.Restrictions.eq("demographicNo", Integer.parseInt(clientId)))
        );
        if (results.size() > 0) {
            result = results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getCurrentForm: clientId=" + clientId + ",class=" + clazz + ",found=" + (result != null));
        }

        return result;
    }

    public List getFormInfo(String clientId, Class clazz) {
        if (clientId == null || clazz == null) {
            throw new IllegalArgumentException();
        }

        List<FormInfo> formInfos = new ArrayList<FormInfo>();

        // Use Criteria API with projections to select specific fields
        // Entity names cannot be parameterized in HQL/JPQL
        DetachedCriteria criteria = DetachedCriteria.forClass(clazz)
            .add(org.hibernate.criterion.Restrictions.eq("demographicNo", Integer.parseInt(clientId)))
            .setProjection(Projections.projectionList()
                .add(Projections.property("id"))
                .add(Projections.property("providerNo"))
                .add(Projections.property("formEdited")))
            .addOrder(org.hibernate.criterion.Order.desc("formEdited"));

        List results = this.getHibernateTemplate().findByCriteria(criteria);
        for (Iterator iter = results.iterator(); iter.hasNext(); ) {
            FormInfo fi = new FormInfo();
            Object[] values = (Object[]) iter.next();
            Integer id = (Integer) values[0];
            String providerNo = (String) values[1];
            Date dateEdited = (Date) values[2];
            Provider provider = this.getHibernateTemplate().get(Provider.class, providerNo);
            fi.setFormId(id.longValue());
            fi.setProviderNo(Long.parseLong(providerNo));
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
