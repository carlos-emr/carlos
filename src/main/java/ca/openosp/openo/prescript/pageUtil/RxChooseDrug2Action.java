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


package ca.openosp.openo.prescript.pageUtil;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.prescript.data.RxDrugData;
import ca.openosp.openo.prescript.data.RxPrescriptionData;
import ca.openosp.openo.prescript.util.RxUtil;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class RxChooseDrug2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public void p(String s) {

    }

    public String execute() throws IOException, ServletException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        // Extract attributes we will need

        //     p("locale="+locale.toString());
        //    p("message="+messages.toString());
        // Setup variables
        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        try {

            RxPrescriptionData rxData = new RxPrescriptionData();
            RxDrugData drugData = new RxDrugData();

            // create Prescription
            RxPrescriptionData.Prescription rx =
                    rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo());


            String BN = request.getParameter("BN");
            String drugId = request.getParameter("drugId");
            rx.setBrandName(BN);
            try {

                RxDrugData.DrugMonograph f = drugData.getDrug(drugId);
//                    rx.setGCN_SEQNO(f.gcnCode);
                String genName = "";
                genName = f.name;
                rx.setAtcCode(f.atc);
                rx.setBrandName(f.product);
                rx.setRegionalIdentifier(f.regionalIdentifier);

                request.setAttribute("components", f.components);
                String dosage = "";
                for (int c = 0; c < f.components.size(); c++) {
                    RxDrugData.DrugMonograph.DrugComponent dc = (RxDrugData.DrugMonograph.DrugComponent) f.components.get(c);
                    if (c == (f.components.size() - 1)) {
                        dosage += dc.strength + " " + dc.unit;
                    } else {
                        dosage += dc.strength + " " + dc.unit + " / ";
                    }
                }
                rx.setDosage(dosage);
                StringBuilder compString = null;
                if (f.components != null) {
                    compString = new StringBuilder();
                    for (int c = 0; c < f.components.size(); c++) {
                        RxDrugData.DrugMonograph.DrugComponent dc = (RxDrugData.DrugMonograph.DrugComponent) f.components.get(c);
                        compString.append(dc.name + " " + dc.strength + " " + dc.unit + " ");
                    }
                }

                MiscUtils.getLogger().debug("In here --=-=--=-_--==" + compString + "\n\n\n\n");
                if (compString != null) {
                    MiscUtils.getLogger().debug("In here --=-=--=-_--==" + compString.toString());
                    rx.setGenericName(compString.toString());
                } else {
                    rx.setGenericName(genName);
                }
            } catch (java.lang.NumberFormatException numEx) {          // Custom
                rx.setBrandName(null);
                rx.setCustomName("");
                    rx.setGCN_SEQNO("0");
            }

            rx.setRxDate(RxUtil.Today());
            rx.setEndDate(null);
            rx.setTakeMin(1);
            rx.setTakeMax(1);
            rx.setFrequencyCode("OID");
            rx.setDuration("30");
            rx.setDurationUnit("D");
            bean.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(bean.getStashIndex()));


            bean.setStashIndex(bean.addStashItem(loggedInInfo, rx));
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;
    }
}
