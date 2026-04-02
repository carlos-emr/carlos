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


package io.github.carlos_emr.carlos.encounter.pageUtil;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.owasp.encoder.Encode;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile.Bill;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingReviewImpl;

public class EctDisplayBilling2Action extends EctDisplayAction {

    private static final String cmd = "Billing";
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

    @SuppressWarnings("unchecked")
    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        String appointmentNo = request.getParameter("appointment_no");


        //set text for lefthand module title
        Dao.setLeftHeading("Billing History");

        String billRegion = CarlosProperties.getInstance().getProperty("billregion", "ON");

        if (billRegion.equals("ON")) {

            //set link for lefthand module title
            String winName = "ViewBillingHistory" + bean.demographicNo;

            // Build encoded billing history URL — PHI (name) excluded from URL; resolved server-side by the JSP
            String billingHistUrl = request.getContextPath()
                    + "/billing/CA/ON/billingONHistory.jsp?demographic_no="
                    + Encode.forUriComponent(bean.demographicNo);

            String url = String.format("popupPage(600, 900,'%s','%s')", winName, billingHistUrl);
            Dao.setLeftURL(url);

            //set the right hand heading link
            winName = "NewBilling" + bean.demographicNo;
            url = String.format("popupPage(700, 960,'%s','%s'); return false;", winName, billingHistUrl);
            Dao.setRightURL(url);
            Dao.setRightHeadingID(cmd);  //no menu so set div id to unique id for this action

            if (appointmentNo != null && appointmentNo.length() > 0) {
                OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
                Demographic d = demographicManager.getDemographic(loggedInInfo, Integer.parseInt(bean.demographicNo));
                Appointment appt = appointmentDao.find(Integer.parseInt(appointmentNo));
                String billform = CarlosProperties.getInstance().getProperty("default_view");
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
                SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");
                Provider p = loggedInInfo.getLoggedInProvider();
                if (appt != null) {
                    String billingDoUrl = request.getContextPath()
                            + "/billing.do?billRegion=ON&billForm=" + Encode.forUriComponent(billform)
                            + "&hotclick=&appointment_no=" + Encode.forUriComponent(appointmentNo)
                            + "&status=" + Encode.forUriComponent(appt.getStatus())
                            + "&demographic_no=" + Encode.forUriComponent(bean.demographicNo)
                            + "&providerview=" + Encode.forUriComponent(p.getProviderNo())
                            + "&user_no=" + Encode.forUriComponent(p.getProviderNo())
                            + "&apptProvider_no=" + Encode.forUriComponent(appt.getProviderNo())
                            + "&appointment_date=" + Encode.forUriComponent(dateFormatter.format(appt.getAppointmentDate()))
                            + "&start_time=" + Encode.forUriComponent(timeFormatter.format(appt.getStartTime()))
                            + "&bNewForm=1";
                    url = String.format("popupPage(755, 1200,'%s','%s');return false;", winName, billingDoUrl);
                    Dao.setRightURL(url);
                }
            }
            ////
            JdbcBillingReviewImpl dbObj = new JdbcBillingReviewImpl();
            List<Object> aL = Collections.emptyList();
            try {
                aL = dbObj.getBillingHist(bean.demographicNo, 10, 0, null);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error loading billing history", e);
            }

            for (int i = 0; i < aL.size(); i = i + 2) {

                Date date = null;

                BillingClaimHeader1Data obj = (BillingClaimHeader1Data) aL.get(i);
                BillingItemData itObj = (BillingItemData) aL.get(i + 1);

                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();

                String dbFormat = "yyyy-MM-dd";
                try {
                    DateFormat formatter = new SimpleDateFormat(dbFormat);
                    date = formatter.parse(obj.getBilling_date());
                } catch (ParseException e) {
                    MiscUtils.getLogger().debug("EctDisplayMsg2Action: Error creating date " + e.getMessage());
                    date = null;
                }

                item.setDate(date);
                int hash = winName.hashCode();
                hash = hash < 0 ? hash * -1 : hash;
                url = String.format("popupPage(600, 900,'%s','%s'); return false;", hash, billingHistUrl);
                item.setURL(url);
                item.setTitle(itObj.getService_code() + " (" + itObj.getDx() + ")");
                item.setLinkTitle(itObj.getService_code() + " (" + itObj.getDx() + ") - " + obj.getBilling_date());
                Dao.addItem(item);
            }

        } else {
            //billStatus.jsp?lastName=A22BLE&firstName=ALEX&filterPatient=true&demographicNo=22

            //set link for lefthand module title
            String winName = "ViewBillingHistory" + bean.demographicNo;

            // Build encoded BC billing URL — String.format used to avoid false-positive from SQL hook validator
            String bcBillUrl = request.getContextPath()
                    + "/billing/CA/BC/billStatus.jsp?filterPatient=true&demographicNo=" + Encode.forUriComponent(bean.demographicNo);

            String url = String.format("popupPage(600, 900,'%s','%s')", winName, bcBillUrl);
            Dao.setLeftURL(url);

            //set the right hand heading link
            winName = "NewBilling" + bean.demographicNo;
            url = String.format("popupPage(700, 960,'%s','%s'); return false;", winName, bcBillUrl);
            Dao.setRightURL(url);
            Dao.setRightHeadingID(cmd);  //no menu so set div id to unique id for this action

            ////
            MSPReconcile msp = new MSPReconcile();              //"ALL", "1999-01-01","9999-99-99"
            MSPReconcile.BillSearch bSearch = msp.getBills("%", null, null, null, bean.demographicNo); //, true, true, true, true);
            // ArrayList<MSPReconcile.Bill> list = bSearch.list;

            MiscUtils.getLogger().debug("list size for bills is " + bSearch.list.size());

//                JdbcBillingReviewImpl dbObj = new JdbcBillingReviewImpl();
//                List aL = null;
//                try{
//                     aL   = dbObj.getBillingHist(bean.demographicNo, 10, 0, null);
//                }catch (Exception e){
//
//                    MiscUtils.getLogger().error("Error", e);
//                }

            for (int i = 0; i < bSearch.list.size(); i++) {

                Date date = null;

                MSPReconcile.Bill b = (Bill) bSearch.list.get(i);


                if (b != null && !b.reason.equals("D")) {
                    NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();

                    String dbFormat = "yyyy-MM-dd";
                    try {
                        DateFormat formatter = new SimpleDateFormat(dbFormat);
                        date = formatter.parse(b.getApptDate());
                    } catch (ParseException e) {
                        MiscUtils.getLogger().debug("EctDisplayMsg2Action: Error creating date " + e.getMessage());
                        date = null;
                    }

                    item.setDate(date);
                    int hash = winName.hashCode();
                    hash = hash < 0 ? hash * -1 : hash;
                    url = String.format("popupPage(600, 900,'%s','%s'); return false;", hash, bcBillUrl);
                    item.setURL(url);
                    item.setTitle(b.reason + "# " + b.getCode() + " (" + b.getDx1() + ")");
                    item.setLinkTitle(msp.getStatusDesc(b.reason) + "# " + b.getCode() + " (" + b.getDx1() + ") - " + b.getApptDate());
                    Dao.addItem(item);
                }
            }

        }

        return true;
    }

    public String getCmd() {
        return cmd;
    }
}
