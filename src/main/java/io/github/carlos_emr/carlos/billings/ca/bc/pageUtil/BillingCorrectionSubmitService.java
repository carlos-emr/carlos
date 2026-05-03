/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.ListIterator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.BillingBean;
import io.github.carlos_emr.BillingDataBean;
import io.github.carlos_emr.BillingItemBean;
import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LogSanitizer;

/**
 * Transactional write side for BC billing corrections.
 *
 * @since 2026-05-01
 */
@Service
@Transactional
public class BillingCorrectionSubmitService {

    private final BillingDetailDao billingDetailDao;
    private final RecycleBinDao recycleBinDao;
    private final BillingDao billingDao;

    public BillingCorrectionSubmitService(BillingDetailDao billingDetailDao,
                                          RecycleBinDao recycleBinDao,
                                          BillingDao billingDao) {
        this.billingDetailDao = billingDetailDao;
        this.recycleBinDao = recycleBinDao;
        this.billingDao = billingDao;
    }

    public void submit(String providerNo, BillingDataBean billingDataBean, BillingBean billingBean) {
        String billingNoStr = billingDataBean.getBilling_no();
        int billingNo = Integer.parseInt(billingNoStr);
        String content = billingDataBean.getContent();
        String total = billingDataBean.getTotal();
        Billing b = billingDao.find(billingNo);
        if (b == null) {
            throw new IllegalStateException("BC billing correction rejected: billing record not found [billingNo="
                    + LogSanitizer.sanitize(billingNoStr) + "]");
        }

        GregorianCalendar now = new GregorianCalendar();

        RecycleBin recycleBin = new RecycleBin();
        recycleBin.setProviderNo(providerNo);
        recycleBin.setTableName("billing");
        recycleBin.setTableContent(content);
        recycleBin.setUpdateDateTime(new java.util.Date());
        recycleBinDao.persist(recycleBin);

        for (BillingDetail bd : billingDetailDao.findAllIncludingDeletedByBillingNo(billingNo)) {
            bd.setStatus("D");
            billingDetailDao.merge(bd);
        }

        b.setHin(billingDataBean.getHin());
        b.setDob(billingDataBean.getDob());
        b.setVisitType(billingDataBean.getVisittype());
        b.setVisitDate(ConversionUtils.fromDateString(billingDataBean.getVisitdate()));
        b.setClinicRefCode(billingDataBean.getClinic_ref_code());
        b.setProviderNo(billingDataBean.getProviderNo());
        b.setStatus(billingDataBean.getStatus());
        b.setUpdateDate(ConversionUtils.fromDateString(
                now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DAY_OF_MONTH)));
        b.setTotal(total);
        b.setContent(content);
        billingDao.merge(b);

        ListIterator it = billingBean.getBillingItems().listIterator();
        while (it.hasNext()) {
            BillingItemBean billingItem = (BillingItemBean) it.next();

            BillingDetail bd = new BillingDetail();
            bd.setBillingNo(billingNo);
            bd.setServiceCode(billingItem.getService_code());
            bd.setServiceDesc(billingItem.getDesc());
            bd.setBillingAmount(billingItem.getService_value());
            bd.setDiagnosticCode(billingItem.getDiag_code());
            bd.setAppointmentDate(MyDateFormat.getSysDate(billingDataBean.getBilling_date()));
            bd.setStatus(billingDataBean.getStatus());
            bd.setBillingUnit(billingItem.getQuantity());
            billingDetailDao.persist(bd);
        }
    }
}
