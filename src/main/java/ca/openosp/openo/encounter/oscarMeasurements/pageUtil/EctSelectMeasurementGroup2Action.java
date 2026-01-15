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

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ca.openosp.openo.commn.dao.MeasurementGroupDao;
import ca.openosp.openo.commn.dao.MeasurementGroupStyleDao;
import ca.openosp.openo.commn.model.MeasurementGroup;
import ca.openosp.openo.commn.model.MeasurementGroupStyle;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.managers.MeasurementManager;

import ca.openosp.openo.encounter.oscarMeasurements.bean.EctStyleSheetBeanHandler;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctSelectMeasurementGroup2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private MeasurementGroupStyleDao styleDao = SpringUtils.getBean(MeasurementGroupStyleDao.class);
    private MeasurementGroupDao groupDao = SpringUtils.getBean(MeasurementGroupDao.class);
    private MeasurementManager measurementManager = SpringUtils.getBean(MeasurementManager.class);

    public String execute() throws ServletException, IOException {
        String groupName = this.getSelectedGroupName();
        //String forward = frm.getForward();

        MiscUtils.getLogger().debug("The forward message is: " + forward);

        HttpSession session = request.getSession();
        session.setAttribute("groupName", groupName);

        if (forward.compareTo("style") == 0) {
            //get the current style
            EctValidation ectValidation = new EctValidation();
            EctStyleSheetBeanHandler sshd = new EctStyleSheetBeanHandler();
            Collection allStyleSheets = sshd.getStyleSheetNameVector();
            String css = ectValidation.getCssName(groupName);
            request.setAttribute("css", css);
            request.setAttribute("allStyleSheets", allStyleSheets);
            request.setAttribute("groupName", groupName);
            return "style";
        } else if (forward.compareTo("delete") == 0) {
            deleteGroup(groupName);
            return "delete";
        }
        if (forward.compareTo("dsHTML") == 0) {

            String state = "addDSHTML";
            String groupId = null;
            boolean propExists = false;
            String propKey = null;

            groupId = measurementManager.findGroupId(groupName);
            propKey = "mgroup.ds.html." + groupId;
            propExists = measurementManager.isProperty(propKey);

            if (propExists) {
                state = "removeDSHTML";
            }
            //set here if if should be addDSHTML or removeDSHTML or completeDSHTML
            return state;
        } else {
            return "type";
        }

    }

    /*****************************************************************************************
     * delete the selected group
     *
     * @return
     ******************************************************************************************/
    private void deleteGroup(String inputGroupName) {

        //MeasurementGroupStyle
        for (MeasurementGroupStyle ms : styleDao.findByGroupName(inputGroupName)) {
            styleDao.remove(ms.getId());
        }

        //MeasurementGroup
        for (MeasurementGroup m : groupDao.findByName(inputGroupName)) {
            groupDao.remove(m.getId());
        }

    }


    private final Map values = new HashMap();

    public void setValue(String key, Object value) {
        values.put(key, value);
    }

    public Object getValue(String key) {
        return values.get(key);
    }

    private String forward;

    public String getForward() {
        return forward;
    }

    public void setForward(String forward) {
        this.forward = forward;
    }

    private String selectedGroupName;

    public String getSelectedGroupName() {
        return selectedGroupName;
    }

    public void setSelectedGroupName(String selectedGroupName) {
        this.selectedGroupName = selectedGroupName;
    }
}
