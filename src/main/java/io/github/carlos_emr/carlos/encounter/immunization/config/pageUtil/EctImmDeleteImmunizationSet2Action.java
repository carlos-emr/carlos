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


package io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ConfigImmunizationDao;
import io.github.carlos_emr.carlos.commn.model.ConfigImmunization;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action that deletes an existing immunization set configuration.
 *
 * @since 2001-01-01
 */
public class EctImmDeleteImmunizationSet2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ConfigImmunizationDao configImmunizationDao = (ConfigImmunizationDao) SpringUtils.getBean(ConfigImmunizationDao.class);


    public String execute()
            throws ServletException, IOException {
        String sets[] = this.getImmuSets();

        for (String set : sets) {
            ConfigImmunization configImmunization = configImmunizationDao.find(Integer.parseInt(set));
            configImmunization.setArchived(1);
            configImmunizationDao.merge(configImmunization);
        }

        return SUCCESS;
    }

    public String[] getImmuSets() {
        if (immuSets == null)
            immuSets = new String[0];
        return immuSets;
    }

    @StrutsParameter
    public void setImmuSets(String str[]) {
        immuSets = str;
    }

    String immuSets[];
}
