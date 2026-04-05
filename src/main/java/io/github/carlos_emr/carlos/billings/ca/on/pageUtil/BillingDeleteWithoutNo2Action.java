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
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.appt.ApptStatusData;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.List;

/**
 * Struts 2Action for Ontario billing removal (unbill).
 * Soft-deletes billing records by setting status to 'D' and rolls back
 * the appointment status. Refreshes the schedule page via BroadcastChannel.
 *
 * <p>Migrated from {@code billing/CA/ON/billingDeleteWithoutNo.jsp}.
 *
 * @since 2026
 */
public final class BillingDeleteWithoutNo2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingDao billingDao = SpringUtils.getBean(BillingDao.class);
    private AppointmentArchiveDao appointmentArchiveDao = SpringUtils.getBean(AppointmentArchiveDao.class);
    private OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String apptNoStr = request.getParameter("appointment_no");
        String curUserNo = loggedInInfo.getLoggedInProviderNo();

        int apptNo;
        try {
            apptNo = Integer.parseInt(apptNoStr);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().error("Invalid appointment_no: {}", apptNoStr);
            return ERROR;
        }

        // Check billing status
        List<Billing> billings = billingDao.findByAppointmentNo(apptNo);
        String billCode = " ";
        String billNo = "";
        for (Billing b : billings) {
            billCode = b.getStatus();
            billNo = b.getId().toString();
        }

        if (billCode.substring(0, 1).compareTo("B") == 0) {
            request.setAttribute("cannotDelete", Boolean.TRUE);
            return "cannotDelete";
        }

        int rowsAffected = 0;
        CarlosProperties props = CarlosProperties.getInstance();
        if (props.getProperty("isNewONbilling", "").equals("true")) {
            BillingCorrectionPrep dbObj = new BillingCorrectionPrep();
            List<String> billStatus = dbObj.getBillingNoStatusByAppt(apptNoStr);
            if (billStatus != null && ((billStatus.size() == 0) || (billStatus.size() > 1 && billStatus.get(billStatus.size() - 1).startsWith("B")))) {
                request.setAttribute("cannotDelete", Boolean.TRUE);
                return "cannotDelete";
            } else if (billStatus != null) {
                for (int idx = 0; idx < billStatus.size(); idx += 2) {
                    // idx = billing header ID, idx+1 = status
                    if (!billStatus.get(idx + 1).equals("D")) {
                        rowsAffected = dbObj.deleteBilling(billStatus.get(idx), "D", curUserNo) ? 1 : 0;
                    }
                }
            }
        } else {
            if (!billNo.isEmpty()) {
                try {
                    Billing b = billingDao.find(Integer.parseInt(billNo));
                    if (b != null) {
                        b.setStatus("D");
                        billingDao.merge(b);
                        rowsAffected = 1;
                    }
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().error("Invalid billNo: {}", billNo);
                }
            }
        }

        if (rowsAffected == 1) {
            ApptStatusData as = new ApptStatusData();
            String unbillStatus = as.unbillStatus(request.getParameter("status"));
            Appointment appt = appointmentDao.find(apptNo);
            appointmentArchiveDao.archiveAppointment(appt);
            if (appt != null) {
                appt.setStatus(unbillStatus);
                appt.setLastUpdateUser(curUserNo);
                appointmentDao.merge(appt);
            }
            return SUCCESS;
        }

        return ERROR;
    }
}
