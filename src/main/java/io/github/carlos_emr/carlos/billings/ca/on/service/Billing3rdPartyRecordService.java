/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.Properties;
/**
 * Service-layer component for {@code Billing3rdPartyRecordService}.
 *
 * <p>Services centralize billing-domain work that should not live in Struts
 * actions or JSPs. Callers are expected to enforce web security before invoking
 * service methods that read or mutate billing state.</p>
 */

@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class Billing3rdPartyRecordService {

    private final Billing3rdPartyService dbObj;

    Billing3rdPartyRecordService(Billing3rdPartyService dbObj) {
        this.dbObj = dbObj;
    }

    // get 3rd billing data
    public Properties get3rdPartBillProp(String invNo) {
        Properties ret = new Properties();
        ret = dbObj.get3rdPartBillProp(invNo);
        return ret;
    }

    public Properties get3rdPartBillPropInactive(String invNo) {
        Properties ret = new Properties();
        ret = dbObj.get3rdPartBillPropInactive(invNo);
        return ret;
    }


    public Properties getLocalClinicAddr() {
        Properties ret = new Properties();
        ret = dbObj.getLocalClinicAddr();
        return ret;
    }

    public Properties get3rdPayMethod() {
        Properties ret = new Properties();
        ret = dbObj.get3rdPayMethod();
        return ret;
    }

    public Properties getGst(String invNo) {
        Properties ret = new Properties();
        ret = dbObj.getGstTotal(invNo);
        return ret;
    }

    public boolean updateKeyValue(String billingNo, String demoNo, String key,
                                  String value) {
        boolean ret = dbObj.updateKeyValue(billingNo, key, value);
        return ret;
    }

}
