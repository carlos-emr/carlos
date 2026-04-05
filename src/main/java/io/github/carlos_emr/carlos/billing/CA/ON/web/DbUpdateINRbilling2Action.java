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

package io.github.carlos_emr.carlos.billing.CA.ON.web;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts2 action for updating or deleting an INR billing record.
 *
 * <p>Migrated from {@code billing/CA/ON/inr/dbUpdateINRbilling.jsp}. Accepts POST only,
 * enforces {@code _admin.billing} write privilege. Validates service_code and diag_code,
 * then branches on the {@code inraction} parameter to either update or soft-delete
 * the target {@link BillingInr} via {@link BillingInrDao}.
 *
 * @since 2006-01-01
 */
public class DbUpdateINRbilling2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    private final HttpServletRequest request = ServletActionContext.getRequest();
    private final HttpServletResponse response = ServletActionContext.getResponse();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final BillingInrDao billingInrDao = SpringUtils.getBean(BillingInrDao.class);
    private final BillingServiceDao billingServiceDao = SpringUtils.getBean(BillingServiceDao.class);

    /**
     * Validates input codes, then updates or deletes the specified INR billing record.
     *
     * @return {@link #SUCCESS} to forward to the view JSP, or {@link #NONE} if the method
     *         is not POST
     * @throws Exception if an unexpected error occurs during persistence
     */
    @Override
    public String execute() throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required security object: _admin.billing");
        }

        String billinginrNo = request.getParameter("billinginr_no");
        String serviceCodeParam = request.getParameter("service_code");
        String diagCodeParam = request.getParameter("diag_code");
        String serviceCode = serviceCodeParam != null ? serviceCodeParam.trim() : "";
        String diagCode = diagCodeParam != null ? diagCodeParam.trim() : "";
        String serviceDesc = "";
        String serviceAmount = "";
        String errorCode = "";

        // Validate and resolve service code
        if (serviceCode.isEmpty()) {
            errorCode += "Please input a service code.<br>";
        } else {
            serviceCode = serviceCode.substring(0, Math.min(5, serviceCode.length()));
            Calendar cal = GregorianCalendar.getInstance();
            String yyyy = String.valueOf(cal.get(Calendar.YEAR));
            String mm = String.valueOf(cal.get(Calendar.MONTH) + 1);
            String dd = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));

            List<BillingService> bsList = billingServiceDao.findGst(
                    serviceCode, ConversionUtils.fromDateString(yyyy + "-" + mm + "-" + dd));
            for (BillingService bs : bsList) {
                serviceDesc = bs.getDescription();
                serviceCode = bs.getServiceCode();
                serviceAmount = bs.getValue();
            }
        }

        // Validate diagnostic code — must be exactly 3 numeric digits
        if (diagCode.isEmpty()) {
            errorCode += "Please input a diagnostic code.<br>";
        } else {
            diagCode = diagCode.substring(0, Math.min(3, diagCode.length()));
            StringBuilder numCode = new StringBuilder();
            for (int i = 0; i < diagCode.length(); i++) {
                char c = diagCode.charAt(i);
                if (c >= '0' && c <= '9') {
                    numCode.append(c);
                }
            }
            if (numCode.length() < 3) {
                diagCode = "000";
                errorCode += "Please input a diagnostic code.<br>";
            }
        }

        String inraction = request.getParameter("inraction");

        if (errorCode.isEmpty()) {
            if ("update".equals(inraction)) {
                String demoHin = request.getParameter("demo_hin");
                String demoDob = request.getParameter("demo_dob");

                BillingInr b = billingInrDao.find(Integer.parseInt(billinginrNo));
                if (b != null && !"D".equals(b.getStatus())) {
                    b.setHin(demoHin);
                    b.setDob(demoDob);
                    b.setServiceCode(serviceCode);
                    b.setServiceDesc(serviceDesc);
                    b.setBillingAmount(serviceAmount);
                    b.setDiagnosticCode(diagCode);
                    billingInrDao.merge(b);
                }

            } else if ("delete".equals(inraction)) {
                GregorianCalendar now = new GregorianCalendar();
                int curYear = now.get(Calendar.YEAR);
                int curMonth = now.get(Calendar.MONTH) + 1;
                int curDay = now.get(Calendar.DAY_OF_MONTH);
                String nowDate = curYear + "/" + curMonth + "/" + curDay;

                BillingInr bi = billingInrDao.find(Integer.parseInt(billinginrNo));
                if (bi != null && !"D".equals(bi.getStatus())) {
                    bi.setStatus("D");
                    bi.setCreateDateTime(ConversionUtils.fromDateString(nowDate));
                    billingInrDao.merge(bi);
                }
            }
        }

        request.setAttribute("errorCode", errorCode);
        request.setAttribute("inraction", inraction);
        request.setAttribute("billinginr_no", billinginrNo);
        return SUCCESS;
    }
}
