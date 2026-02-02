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


package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.util.List;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.LogTeleplanTxDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.LogTeleplanTx;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * @author jay
 */

public class TeleplanLogDAO {

    private LogTeleplanTxDao dao = SpringUtils.getBean(LogTeleplanTxDao.class);

    public TeleplanLogDAO() {
    }

    public void save(TeleplanLog tl) {
        LogTeleplanTx l = new LogTeleplanTx();
        l.setSequenceNo(tl.getSequenceNo());
        l.setClaim(tl.getClaim().getBytes());
        l.setBillingMasterNo(tl.getBillingmasterNo());
        dao.persist(l);
    }

    public void save(List list) {
        MiscUtils.getLogger().debug("LOG LIST SIZE" + list.size());
        for (int i = 0; i < list.size(); i++) {
            TeleplanLog tl = (TeleplanLog) list.get(i);
            LogTeleplanTx l = new LogTeleplanTx();
            l.setSequenceNo(tl.getSequenceNo());
            l.setClaim(tl.getClaim().getBytes());
            l.setBillingMasterNo(tl.getBillingmasterNo());
            dao.persist(l);
        }
    }
}
