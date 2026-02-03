/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 */

package io.github.carlos_emr.carlos.providers.data;

import java.util.Properties;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.BillCenterDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderBillCenterDao;
import io.github.carlos_emr.carlos.commn.model.BillCenter;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * @author Toby
 */
public class ProviderBillCenter {

    Logger log = MiscUtils.getLogger();
    ProviderBillCenterDao dao = SpringUtils.getBean(ProviderBillCenterDao.class);
    BillCenterDao bcDao = SpringUtils.getBean(BillCenterDao.class);


    public ProviderBillCenter() {
    }

    public boolean hasBillCenter(String provider_no) {
        boolean retval = false;
        io.github.carlos_emr.carlos.commn.model.ProviderBillCenter pbc = dao.find(provider_no);
        if (pbc != null && pbc.getBillCenterCode() != null && pbc.getBillCenterCode().length() > 0) {
            retval = true;
        }
        return retval;
    }

    public boolean hasProvider(String provider_no) {
        boolean retval = false;
        if (dao.find(provider_no) != null) {
            retval = true;
        }
        return retval;
    }

    public void addBillCenter(String provider_no, String billCenterCode) {
        io.github.carlos_emr.carlos.commn.model.ProviderBillCenter pbc = new io.github.carlos_emr.carlos.commn.model.ProviderBillCenter();
        pbc.setProviderNo(provider_no);
        pbc.setBillCenterCode(billCenterCode);
        dao.persist(pbc);
    }

    public String getBillCenter(String provider_no) {
        String billCenterCode = "";

        io.github.carlos_emr.carlos.commn.model.ProviderBillCenter pbc = dao.find(provider_no);
        if (pbc != null) {
            billCenterCode = pbc.getBillCenterCode();
        }
        return billCenterCode;
    }

    public void updateBillCenter(String provider_no, String billCenterCode) {
        if (!hasProvider(provider_no)) {
            addBillCenter(provider_no, billCenterCode);
        } else {
            io.github.carlos_emr.carlos.commn.model.ProviderBillCenter pbc = dao.find(provider_no);
            if (pbc != null) {
                pbc.setBillCenterCode(billCenterCode);
                dao.merge(pbc);
            }
        }
    }

    public Properties getAllBillCenter() {
        Properties allBillCenter = new Properties();

        for (BillCenter bc : bcDao.findAll()) {
            allBillCenter.setProperty(bc.getBillCenterCode(), bc.getBillCenterDesc());
        }

        return allBillCenter;
    }

}
