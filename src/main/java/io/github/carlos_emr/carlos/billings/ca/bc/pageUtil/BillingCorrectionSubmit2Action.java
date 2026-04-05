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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.BillingBean;
import io.github.carlos_emr.BillingDataBean;
import io.github.carlos_emr.BillingItemBean;
import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingSessionUtils;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.ListIterator;

/**
 * Struts 2Action for BC billing correction submission.
 * Archives old billing content, soft-deletes old billing details, updates the
 * billing header, and inserts corrected service line items.
 *
 * <p>Notable difference from the ON version: total is stored directly without
 * {@code movePointLeft(2)}.
 *
 * <p>Migrated from {@code billing/CA/BC/billingCorrectionSubmit.jsp}.
 *
 * @since 2026
 */
public final class BillingCorrectionSubmit2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingDetailDao billingDetailDao = SpringUtils.getBean(BillingDetailDao.class);
    private RecycleBinDao recycleBinDao = SpringUtils.getBean(RecycleBinDao.class);
    private BillingDao billingDao = SpringUtils.getBean(BillingDao.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingDataBean billingDataBean = (BillingDataBean) request.getSession().getAttribute("billingDataBean");
        // Session key for BillingBean is "billing" (set by billingCorrectionValid.jsp)
        BillingBean billingBean = BillingSessionUtils.getBillingBean(request.getSession());

        if (billingDataBean == null || billingBean == null) {
            MiscUtils.getLogger().error("billingDataBean or billingBean not found in session");
            return ERROR;
        }

        try {
            String billingNoStr = billingDataBean.getBilling_no();
            String content = billingDataBean.getContent();
            String total = billingDataBean.getTotal();

            GregorianCalendar now = new GregorianCalendar();

            RecycleBin recycleBin = new RecycleBin();
            recycleBin.setProviderNo(loggedInInfo.getLoggedInProviderNo());
            recycleBin.setTableName("billing");
            recycleBin.setTableContent(content);
            recycleBin.setUpdateDateTime(new java.util.Date());
            recycleBinDao.persist(recycleBin);

            for (BillingDetail bd : billingDetailDao.findByBillingNo(Integer.parseInt(billingNoStr))) {
                bd.setStatus("D");
                billingDetailDao.merge(bd);
            }

            Billing b = billingDao.find(Integer.parseInt(billingNoStr));
            if (b != null) {
                b.setHin(billingDataBean.getHin());
                b.setDob(billingDataBean.getDob());
                b.setVisitType(billingDataBean.getVisittype());
                b.setVisitDate(ConversionUtils.fromDateString(billingDataBean.getVisitdate()));
                b.setClinicRefCode(billingDataBean.getClinic_ref_code());
                b.setProviderNo(billingDataBean.getProviderNo());
                b.setStatus(billingDataBean.getStatus());
                b.setUpdateDate(ConversionUtils.fromDateString(
                        now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DAY_OF_MONTH)));
                // BC stores amount directly (no movePointLeft(2))
                b.setTotal(total);
                b.setContent(content);
                billingDao.merge(b);
            }

            ListIterator it = billingBean.getBillingItems().listIterator();
            while (it.hasNext()) {
                BillingItemBean billingItem = (BillingItemBean) it.next();

                BillingDetail bd = new BillingDetail();
                bd.setBillingNo(Integer.parseInt(billingNoStr));
                bd.setServiceCode(billingItem.getService_code());
                bd.setServiceDesc(billingItem.getDesc());
                bd.setBillingAmount(billingItem.getService_value());
                bd.setDiagnosticCode(billingItem.getDiag_code());
                bd.setAppointmentDate(MyDateFormat.getSysDate(billingDataBean.getBilling_date()));
                bd.setStatus(billingDataBean.getStatus());
                bd.setBillingUnit(billingItem.getQuantity());
                billingDetailDao.persist(bd);
            }

        } catch (ArrayIndexOutOfBoundsException e) {
            MiscUtils.getLogger().warn("ArrayIndexOutOfBoundsException during BC billing correction submit", e);
        }

        return SUCCESS;
    }
}
