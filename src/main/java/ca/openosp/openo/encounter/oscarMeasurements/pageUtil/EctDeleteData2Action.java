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

package ca.openosp.openo.encounter.oscarMeasurements.pageUtil;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.MeasurementDao;
import ca.openosp.openo.commn.dao.MeasurementsDeletedDao;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.commn.model.MeasurementsDeleted;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.util.ConversionUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class EctDeleteData2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_measurement", "d", null)) {
            throw new SecurityException("missing required sec object (_measurement)");
        }

        MeasurementsDeletedDao measurementsDeletedDao = (MeasurementsDeletedDao) SpringUtils.getBean(MeasurementsDeletedDao.class);
        MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);
        if (deleteCheckbox != null) {

            MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
            for (int i = 0; i < deleteCheckbox.length; i++) {
                MiscUtils.getLogger().debug(deleteCheckbox[i]);

                Measurement m = dao.find(ConversionUtils.fromIntString(deleteCheckbox[i]));
                if (m != null) {
                    measurementsDeletedDao.persist(new MeasurementsDeleted(m));
                    measurementDao.remove(Integer.parseInt(deleteCheckbox[i]));
                }
            }
        }

        if (this.getType() != null) {
            response.sendRedirect(
                request.getContextPath()
                    + "/oscarEncounter/oscarMeasurements/SetupDisplayHistory.do?type="
                    + URLEncoder.encode(this.getType(), StandardCharsets.UTF_8.name()));
            return NONE;
        }
        return SUCCESS;
    }

    private String[] deleteCheckbox;

    public String[] getDeleteCheckbox() {
        return deleteCheckbox;
    }

    public void setDeleteCheckbox(String[] deleteCheckbox) {
        this.deleteCheckbox = deleteCheckbox;
    }

    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
