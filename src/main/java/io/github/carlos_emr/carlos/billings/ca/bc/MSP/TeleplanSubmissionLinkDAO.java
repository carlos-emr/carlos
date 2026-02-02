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

import io.github.carlos_emr.carlos.billing.CA.BC.dao.TeleplanSubmissionLinkDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanSubmissionLink;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class TeleplanSubmissionLinkDAO {

    private TeleplanSubmissionLinkDao dao = SpringUtils.getBean(TeleplanSubmissionLinkDao.class);


    public TeleplanSubmissionLinkDAO() {
    }

    public void save(int billActId, List billingMasterList) {
        for (int i = 0; i < billingMasterList.size(); i++) {
            String bi = (String) billingMasterList.get(i);
            int b = Integer.parseInt(bi);
            TeleplanSubmissionLink t = new TeleplanSubmissionLink();
            t.setBillActivityId(billActId);
            t.setBillingMasterNo(b);
            dao.persist(t);
        }
    }

}
