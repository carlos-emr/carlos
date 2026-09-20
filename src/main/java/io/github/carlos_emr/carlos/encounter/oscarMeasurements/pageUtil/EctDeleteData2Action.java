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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.ConversionUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class EctDeleteData2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String execute() throws ServletException, IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "d", null)) {
            throw new SecurityException("missing required sec object (_measurement)");
        }

        MeasurementsDeletedDao measurementsDeletedDao = (MeasurementsDeletedDao) SpringUtils.getBean(MeasurementsDeletedDao.class);
        MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);
        if (deleteCheckbox != null) {

            MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);

            // Authorize every row before removing any of them. The ids are
            // client-supplied and name rows directly, so the chart-wide privilege
            // above does not say whether this caller may touch THESE patients'
            // charts; the owning demographic comes off each row itself, which means
            // nothing has to be passed in and every caller of this endpoint is
            // covered. Checking inside the delete loop would let a batch that mixes
            // an authorized id with another patient's delete the first and then
            // refuse the second, which is a partial delete nobody asked for.
            List<Measurement> authorized = new ArrayList<>();
            for (int i = 0; i < deleteCheckbox.length; i++) {
                MiscUtils.getLogger().debug(deleteCheckbox[i]);

                Measurement m = dao.find(ConversionUtils.fromIntString(deleteCheckbox[i]));
                if (m == null) {
                    continue;
                }
                if (!isAllowedToDelete(loggedInInfo, m.getDemographicId())) {
                    throw new SecurityException("missing required sec object (_measurement)");
                }
                authorized.add(m);
            }

            for (Measurement m : authorized) {
                measurementsDeletedDao.persist(new MeasurementsDeleted(m));
                measurementDao.remove(m.getId());
            }
        }

        if (this.getType() != null) {
            response.sendRedirect(
                request.getContextPath()
                    + "/encounter/oscarMeasurements/SetupDisplayHistory?type="
                    + URLEncoder.encode(this.getType(), StandardCharsets.UTF_8.name()));
            return NONE;
        }
        return SUCCESS;
    }

    /**
     * Whether the logged-in provider may delete a measurement belonging to
     * {@code demographicNo}.
     *
     * <p>Package-private so the contract can be driven directly from a test.
     */
    boolean isAllowedToDelete(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (demographicNo == null) {
            return false;
        }
        return securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "d", String.valueOf(demographicNo))
                && securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo);
    }

    private String[] deleteCheckbox;

    public String[] getDeleteCheckbox() {
        return deleteCheckbox;
    }

    @StrutsParameter
    public void setDeleteCheckbox(String[] deleteCheckbox) {
        this.deleteCheckbox = deleteCheckbox;
    }

    private String type;

    public String getType() {
        return type;
    }

    @StrutsParameter
    public void setType(String type) {
        this.type = type;
    }
}
