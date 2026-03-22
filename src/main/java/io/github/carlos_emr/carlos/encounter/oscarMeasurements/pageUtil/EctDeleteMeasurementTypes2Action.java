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

import java.io.IOException;
import java.util.Date;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDeletedDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.MeasurementTypeDeleted;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementTypes;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action that deletes measurement type definitions.
 *
 * @since 2001-01-01
 */
public class EctDeleteMeasurementTypes2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private MeasurementTypeDao measurementTypeDao = SpringUtils.getBean(MeasurementTypeDao.class);
    private MeasurementGroupDao measurementGroupDao = SpringUtils.getBean(MeasurementGroupDao.class);
    private MeasurementTypeDeletedDao measurementTypeDeletedDao = SpringUtils.getBean(MeasurementTypeDeletedDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {
        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {
            if (deleteCheckbox != null) {
                for (int i = 0; i < deleteCheckbox.length; i++) {
                    MiscUtils.getLogger().debug(deleteCheckbox[i]);

                    MeasurementType mt = measurementTypeDao.find(Integer.parseInt(deleteCheckbox[i]));
                    if (mt != null) {

                        for (MeasurementGroup g : measurementGroupDao.findByTypeDisplayName(mt.getTypeDisplayName())) {
                            measurementGroupDao.remove(g.getId());
                        }

                        MeasurementTypeDeleted mtd = new MeasurementTypeDeleted();
                        mtd.setType(mt.getType());
                        mtd.setTypeDisplayName(mt.getTypeDisplayName());
                        mtd.setTypeDescription(mt.getTypeDescription());
                        mtd.setMeasuringInstruction(mt.getMeasuringInstruction());
                        mtd.setValidation(mt.getValidation());
                        mtd.setDateDeleted(new Date());
                        measurementTypeDeletedDao.persist(mtd);

                        measurementTypeDao.remove(mt.getId());


                    }


                }
            }


            MeasurementTypes mt = MeasurementTypes.getInstance();
            mt.reInit();
            return SUCCESS;

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }
    }
    private String[] deleteCheckbox;

    public String[] getDeleteCheckbox() {
        return deleteCheckbox;
    }

    @StrutsParameter
    public void setDeleteCheckbox(String[] deleteCheckbox) {
        this.deleteCheckbox = deleteCheckbox;
    }
}
