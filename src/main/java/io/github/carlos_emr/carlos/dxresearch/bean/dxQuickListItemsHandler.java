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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.dxresearch.bean;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDao;
import io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.QuickListDao;
import io.github.carlos_emr.carlos.commn.dao.QuickListUserDao;
import io.github.carlos_emr.carlos.commn.model.AbstractCodeSystemModel;
import io.github.carlos_emr.carlos.commn.model.QuickListUser;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.dxresearch.util.dxResearchCodingSystem;

/**
 * Handler that loads diagnosis code items from a named quick list.
 *
 * <p>Quick lists are provider-configurable shortcut lists of commonly used
 * diagnosis codes. This handler retrieves items from a named quick list,
 * tracks usage timestamps per provider, and supports filtering out codes
 * already assigned to a patient.</p>
 *
 * @since 2026-03-17
 */
public class dxQuickListItemsHandler {

    private QuickListUserDao dao = SpringUtils.getBean(QuickListUserDao.class);

    Vector dxQuickListItemsVector = new Vector();

    /**
     * Constructs a handler and loads quick list items, recording usage for the provider.
     *
     * @param quickListName String the name of the quick list to load
     * @param providerNo String the provider number for usage tracking
     */
    public dxQuickListItemsHandler(String quickListName, String providerNo) {
        init(quickListName, providerNo);
    }

    /**
     * Constructs a handler and loads quick list items without provider usage tracking.
     *
     * @param quickListName String the name of the quick list to load
     */
    public dxQuickListItemsHandler(String quickListName) {
        init(quickListName);
    }

    /**
     * Loads quick list items for the specified provider, updating or creating
     * usage tracking records and populating the items collection across all
     * configured coding systems.
     *
     * @param quickListName String the name of the quick list (truncated to 10 characters)
     * @param providerNo String the provider number for usage tracking
     * @return boolean always {@code true}
     */
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

    /**
     * Loads quick list items without provider usage tracking, populating the
     * items collection across all configured coding systems.
     *
     * @param quickListName String the name of the quick list
     * @return boolean always {@code true}
     */
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

    /**
     * Returns all items in the loaded quick list.
     *
     * @return Collection&lt;dxCodeSearchBean&gt; the quick list items
     */
    public Collection<dxCodeSearchBean> getDxQuickListItemsVector() {
        return dxQuickListItemsVector;
    }

    /**
     * Returns quick list items that are not already in the patient's diagnosis list.
     *
     * <p>Filters out codes whose diagnosis search code matches any entry in the
     * provided patient's research list.</p>
     *
     * @param patientsList Vector&lt;dxResearchBean&gt; the patient's current diagnosis research entries
     * @return Collection&lt;dxCodeSearchBean&gt; quick list items not yet assigned to the patient
     */
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

    /**
     * Updates the description of a diagnosis code in the coding system reference table.
     *
     * @param type String the coding system type (e.g. "icd9", "icd10"), must match
     *             a valid {@link AbstractCodeSystemDaoImpl.codingSystem} enum value
     * @param code String the diagnosis code to update
     * @param desc String the new description for the code
     */
    public static void updatePatientCodeDesc(String type, String code, String desc) {
        Class<?> daoClass = AbstractCodeSystemDao.getDaoName(AbstractCodeSystemDaoImpl.codingSystem.valueOf(type));
        @SuppressWarnings("unchecked")
        AbstractCodeSystemDao<AbstractCodeSystemModel<?>> csDao = (AbstractCodeSystemDao<AbstractCodeSystemModel<?>>) SpringUtils.getBean(daoClass);

        AbstractCodeSystemModel<?> codingSystemEntity = csDao.findByCode(code);
        codingSystemEntity.setDescription(desc);
        csDao.merge(codingSystemEntity);
    }

}
