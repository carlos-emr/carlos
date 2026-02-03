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

package io.github.carlos_emr.carlos.dxresearch.bean;

import java.util.Collection;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.QuickListDao;
import io.github.carlos_emr.carlos.commn.model.QuickList;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;

public class dxQuickListBeanHandler {

    Vector dxQuickListBeanVector = new Vector();
    String lastUsedQuickList = null;

    public dxQuickListBeanHandler(String providerNo) {
        init(providerNo);
    }

    public dxQuickListBeanHandler(String providerNo, String codingSystem) {
        init(providerNo, codingSystem);
    }

    public dxQuickListBeanHandler() {
        init();
    }

    public boolean init(String providerNo) {
        return init(providerNo, null);
    }

    public boolean init(String providerNo, String codingSystem) {
        String qlDefault = OscarProperties.getInstance().getProperty("DX_QUICK_LIST_DEFAULT");
        QuickListDao dao = SpringUtils.getBean(QuickListDao.class);

        for (QuickList ql : dao.findByCodingSystem(codingSystem)) {
            dxQuickListBean bean = new dxQuickListBean(ql.getQuickListName(), ql.getCreatedByProvider());
            String quickListName = ql.getQuickListName();

            if (quickListName.equals(qlDefault) || lastUsedQuickList == null) { //select default or 1st quickList
                bean.setLastUsed("Selected");
                lastUsedQuickList = ql.getQuickListName();
            }

            dxQuickListBeanVector.add(bean);
        }
        return true;
    }

    public boolean init() {
        QuickListDao dao = SpringUtils.getBean(QuickListDao.class);
        for (Object qlName : dao.findDistinct()) {
            dxQuickListBean bean = new dxQuickListBean((String) qlName);
            dxQuickListBeanVector.add(bean);
        }
        return true;
    }

    public Collection getDxQuickListBeanVector() {
        return dxQuickListBeanVector;
    }

    public String getLastUsedQuickList() {
        return lastUsedQuickList;
    }
}
