//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */


package ca.openosp.openo.billing.CA.ON.web;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.time.DateUtils;
import ca.openosp.openo.commn.dao.BatchBillingDAO;
import ca.openosp.openo.commn.dao.BillingONCHeader1Dao;
import ca.openosp.openo.commn.model.BatchBilling;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

/**
 * @author rjonasz
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class BatchBill2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private BillingONCHeader1Dao billingONCHeader1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("doBatchBill".equals(method)) {
            return doBatchBill();
        } else if ("remove".equals(method)) {
            return remove();
        } else if ("add".equals(method)) {
            return add();
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }


        Date billingDate;
        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
        String strDate = request.getParameter("BillDate");
        if (strDate == null) {
            billingDate = new Date();
        } else {
            try {
                billingDate = dateFmt.parse(strDate);
            } catch (ParseException e) {
                MiscUtils.getLogger().error("Error", e);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }
        }

        String clinic_view = request.getParameter("clinic_view");
        String curUser = (String) request.getSession().getAttribute("user");
        String[] billingInfo = request.getParameterValues("bill");

        if (billingInfo != null) {

            String[] temp;
            for (int idx = 0; idx < billingInfo.length; ++idx) {
                temp = billingInfo[idx].split(";");
                this.billingONCHeader1Dao.createBill(temp[2], Integer.parseInt(temp[1]), temp[0], clinic_view, billingDate, curUser);
            }

        }
        return null;

    }

    public String doBatchBill() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        ResourceBundle oscarResource = ResourceBundle.getBundle("oscarResources", request.getLocale());
        Date billingDate;
        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
        String strDate = request.getParameter("BillDate");
        if (strDate == null) {
            billingDate = new Date();
        } else {
            try {
                billingDate = dateFmt.parse(strDate);
            } catch (ParseException e) {
                MiscUtils.getLogger().error("Error", e);
                request.setAttribute("error", oscarResource.getString("billing.batchbilling.badDate"));
                return "error";
            }
        }

        String clinic_view = request.getParameter("clinic_view");
        String curUser = (String) request.getSession().getAttribute("user");
        String[] billingInfo = request.getParameterValues("bill");
        BatchBillingDAO batchBillingDAO = (BatchBillingDAO) SpringUtils.getBean(BatchBillingDAO.class);
        List<BatchBilling> batchBillingList;
        BatchBilling batchBilling;
        String total;

        //create the invoice and update batch_billing table
        if (billingInfo != null) {

            String[] temp;
            for (int idx = 0; idx < billingInfo.length; ++idx) {
                temp = billingInfo[idx].split(";");
                //passed in order is billing providers, demographic no, service code, dx code
                total = this.billingONCHeader1Dao.createBill(temp[3], Integer.parseInt(temp[2]), temp[0], temp[1], clinic_view, billingDate, curUser);

                batchBillingList = batchBillingDAO.find(Integer.parseInt(temp[2]), temp[0]);
                batchBilling = batchBillingList.get(0);
                batchBilling.setBillingAmount(total);
                batchBilling.setLastBilledDate(billingDate);
                batchBillingDAO.merge(batchBilling);

            }

        }

        try {
            response.sendRedirect(request.getContextPath() + "/billing/CA/ON/batchBilling.jsp?provider_no=" + request.getParameter("providers") + "&service_code=" + request.getParameter("service_code"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    //Remove demographics from batch billing table
    public String remove() {


        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String[] billingInfo = request.getParameterValues("bill");
        BatchBillingDAO batchBillingDAO = (BatchBillingDAO) SpringUtils.getBean(BatchBillingDAO.class);
        List<BatchBilling> batchBillingList;
        BatchBilling batchBilling;

        //create the invoice and update batch_billing table
        if (billingInfo != null) {

            String[] temp;
            for (int idx = 0; idx < billingInfo.length; ++idx) {
                temp = billingInfo[idx].split(";");

                batchBillingList = batchBillingDAO.find(Integer.parseInt(temp[2]), temp[0]);
                batchBilling = batchBillingList.get(0);
                batchBillingDAO.remove(batchBilling.getId());
            }
        }

        try {
            response.sendRedirect(request.getContextPath() + "/billing/CA/ON/batchBilling.jsp?provider_no=" + request.getParameter("providers") + "&service_code=" + request.getParameter("service_code"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;

    }

    //Add demographic to batch billing table and allow update of record if already present
    public String add() throws ParseException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BatchBillingDAO batchBillingDAO = (BatchBillingDAO) SpringUtils.getBean(BatchBillingDAO.class);
        Integer demographicNo = Integer.parseInt(request.getParameter("demographic_no").trim());
        String billingProviderNo = request.getParameter("providers").trim();
        String creatorProviderNo = request.getParameter("creator").trim();
        String service_code = request.getParameter("xml_other1");
        String dxcode = request.getParameter("xml_diagnostic_detail");
        String createdDate = request.getParameter("createdate");
        Date date = DateUtils.parseDate(createdDate, new String[]{"yyyy/MM/dd HH:mm:ss"});
        Timestamp created = new Timestamp(date.getTime());
        int pipePos;

        if ((pipePos = dxcode.indexOf("|")) != -1) {
            String tmp = dxcode.substring(0, pipePos);
            dxcode = tmp;
        }

        List<BatchBilling> batchBillingList = batchBillingDAO.find(demographicNo, service_code);
        BatchBilling batchBilling;

        if (batchBillingList.isEmpty()) {
            batchBilling = new BatchBilling();

            batchBilling.setDemographicNo(demographicNo);
            batchBilling.setBillingProviderNo(billingProviderNo);
            batchBilling.setServiceCode(service_code);
            batchBilling.setDxcode(dxcode);
            batchBilling.setCreateDate(created);
            batchBilling.setCreator(creatorProviderNo);

            batchBillingDAO.persist(batchBilling);
        } else {
            batchBilling = batchBillingList.get(0);
            batchBilling.setBillingProviderNo(billingProviderNo);
            batchBilling.setDxcode(dxcode);
            batchBilling.setCreateDate(created);
            batchBilling.setCreator(creatorProviderNo);

            batchBillingDAO.merge(batchBilling);
        }


        return "saved";

    }
}
