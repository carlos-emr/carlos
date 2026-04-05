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

import io.github.carlos_emr.carlos.appt.ApptStatusData;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Struts 2Action for BC billing removal (unbill).
 * Soft-deletes billing records and Teleplan Billingmaster records, then rolls
 * back the appointment status. Refreshes the schedule page via BroadcastChannel.
 *
 * <p>Migrated from {@code billing/CA/BC/billingDeleteWithoutNo.jsp}.
 *
 * @since 2026
 */
public final class BillingDeleteWithoutNo2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingDao billingDao = SpringUtils.getBean(BillingDao.class);
    private BillingmasterDAO billingMasterDao = SpringUtils.getBean(BillingmasterDAO.class);
    private AppointmentArchiveDao appointmentArchiveDao = SpringUtils.getBean(AppointmentArchiveDao.class);
    private OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String apptNoStr = request.getParameter("appointment_no");
        int apptNo;
        try {
            apptNo = Integer.parseInt(apptNoStr);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().error("Invalid appointment_no: {}", apptNoStr);
            return ERROR;
        }

        List<Billing> billings = billingDao.findByAppointmentNo(apptNo);
        List<String> billCodeList = new ArrayList<>();
        boolean cannotDelete = false;

        for (Billing b : billings) {
            if (b.getStatus().substring(0, 1).equals("B")) {
                cannotDelete = true;
            }
            billCodeList.add(b.getId().toString());
        }

        if (cannotDelete) {
            request.setAttribute("cannotDelete", Boolean.TRUE);
            return "cannotDelete";
        }

        boolean updateApptStatus = false;
        for (String billNo : billCodeList) {
            try {
                Billing b = billingDao.find(Integer.parseInt(billNo));
                if (b != null) {
                    b.setStatus("D");
                    billingDao.merge(b);
                    updateApptStatus = true;
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Invalid billNo: {}", billNo);
            }

            for (Billingmaster m : billingMasterDao.getBillingMasterByBillingNo(billNo)) {
                m.setBillingstatus("D");
                billingMasterDao.update(m);
            }
        }

        if (updateApptStatus) {
            ApptStatusData as = new ApptStatusData();
            String unbillStatus = as.unbillStatus(request.getParameter("status"));
            Appointment appt = appointmentDao.find(apptNo);
            appointmentArchiveDao.archiveAppointment(appt);
            if (appt != null) {
                appt.setStatus(unbillStatus);
                appt.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
                appointmentDao.merge(appt);
            }
            return SUCCESS;
        }

        return ERROR;
    }
}
