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

package ca.openosp.openo.dxresearch.bean;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import ca.openosp.openo.commn.dao.AbstractCodeSystemDao;
import ca.openosp.openo.commn.dao.AbstractCodeSystemDaoImpl;
import ca.openosp.openo.commn.dao.QuickListDao;
import ca.openosp.openo.commn.dao.QuickListUserDao;
import ca.openosp.openo.commn.model.AbstractCodeSystemModel;
import ca.openosp.openo.commn.model.QuickListUser;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.dxresearch.util.dxResearchCodingSystem;

public class dxQuickListItemsHandler {

    private QuickListUserDao dao = SpringUtils.getBean(QuickListUserDao.class);

    Vector dxQuickListItemsVector = new Vector();

    public dxQuickListItemsHandler(String quickListName, String providerNo) {
        init(quickListName, providerNo);
    }

    public dxQuickListItemsHandler(String quickListName) {
        init(quickListName);
    }

    public boolean init(String quickListName, String providerNo) {
        int ListNameLen = 10;

        dxResearchCodingSystem codingSys = new dxResearchCodingSystem();
        String[] codingSystems = codingSys.getCodingSystems();
        String codingSystem;
        String name;

        if (quickListName == null) {
            quickListName = "";
        }

        if (quickListName.length() > ListNameLen) {
            name = quickListName.substring(0, ListNameLen);
        } else {
            name = quickListName;
        }

        List<QuickListUser> results = dao.findByNameAndProviderNo(name, providerNo);
        if (!results.isEmpty()) {
            for (QuickListUser result : results) {
                result.setLastUsed(new Date());
                dao.merge(result);
            }
        } else {
            QuickListUser q = new QuickListUser();
            q.setQuickListName(name);
            q.setProviderNo(providerNo);
            q.setLastUsed(new Date());
            dao.persist(q);
        }

        QuickListDao dao = SpringUtils.getBean(QuickListDao.class);
        for (int idx = 0; idx < codingSystems.length; ++idx) {
            codingSystem = codingSystems[idx];

            for (Object[] o : dao.findResearchCodeAndCodingSystemDescriptionByCodingSystem(codingSystem, quickListName)) {
                String dxResearchCode = String.valueOf(o[0]);
                String description = String.valueOf(o[1]);
                dxCodeSearchBean bean = new dxCodeSearchBean(description, dxResearchCode);
                bean.setType(codingSystem);
                dxQuickListItemsVector.add(bean);
            }

        }
        return true;
    }

    public boolean init(String quickListName) {

        dxResearchCodingSystem codingSys = new dxResearchCodingSystem();
        String[] codingSystems = codingSys.getCodingSystems();
        String codingSystem;
        String sql;

        for (int idx = 0; idx < codingSystems.length; ++idx) {
            codingSystem = codingSystems[idx];

            QuickListDao dao = SpringUtils.getBean(QuickListDao.class);
            for (Object[] o : dao.findResearchCodeAndCodingSystemDescriptionByCodingSystem(codingSystem, quickListName)) {
                String dxResearchCode = String.valueOf(o[0]);
                String description = String.valueOf(o[1]);

                dxCodeSearchBean bean = new dxCodeSearchBean(description, dxResearchCode);
                bean.setType(codingSystem);
                dxQuickListItemsVector.add(bean);
            }
        }

        return true;
    }

    public Collection<dxCodeSearchBean> getDxQuickListItemsVector() {
        return dxQuickListItemsVector;
    }

    public Collection<dxCodeSearchBean> getDxQuickListItemsVectorNotInPatientsList(Vector<dxResearchBean> patientsList) {
        Vector<String> dxList = new Vector<String>();
        Vector<dxCodeSearchBean> v = new Vector<dxCodeSearchBean>();

        for (dxResearchBean p : patientsList) {
            dxList.add(p.getDxSearchCode());
        }
        for (int j = 0; j < dxQuickListItemsVector.size(); j++) {
            dxCodeSearchBean dxCod = (dxCodeSearchBean) dxQuickListItemsVector.get(j);
            if (!dxList.contains(dxCod.getDxSearchCode())) {
                v.add(dxCod);
            }
        }
        return v;
    }

    public static void updatePatientCodeDesc(String type, String code, String desc) {
        Class<?> daoClass = AbstractCodeSystemDao.getDaoName(AbstractCodeSystemDaoImpl.codingSystem.valueOf(type));
        @SuppressWarnings("unchecked")
        AbstractCodeSystemDao<AbstractCodeSystemModel<?>> csDao = (AbstractCodeSystemDao<AbstractCodeSystemModel<?>>) SpringUtils.getBean(daoClass);

        AbstractCodeSystemModel<?> codingSystemEntity = csDao.findByCode(code);
        codingSystemEntity.setDescription(desc);
        csDao.merge(codingSystemEntity);
    }

}
