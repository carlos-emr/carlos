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


package ca.openosp.openo.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.openosp.openo.commn.dao.BillingServiceDao;
import ca.openosp.openo.commn.model.BillingService;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.billings.ca.bc.data.BillingCodeData;
import ca.openosp.openo.util.UtilDateUtilities;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class BillingEditCode2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static BillingServiceDao billingServiceDao = (BillingServiceDao) SpringUtils.getBean(BillingServiceDao.class);

    public String ajaxCodeUpdate() throws IOException {
        String id = request.getParameter("id");
        String val = request.getParameter("val");
        String billingServiceDate = request.getParameter("billService");
        String termDate = request.getParameter("termDate");
        String codeId = request.getParameter("codeId");

        BillingService itemCode = billingServiceDao.find(Integer.parseInt(codeId));

        itemCode.setValue(val);
        itemCode.setBillingserviceDate(UtilDateUtilities.StringToDate(billingServiceDate));
        itemCode.setTerminationDate(UtilDateUtilities.StringToDate(termDate));
        billingServiceDao.merge(itemCode);

        Map map = new HashMap();
        map.put("id", id);
        map.put("val", val);
        map.put("billService", billingServiceDate);
        map.put("termDate", termDate);
        ObjectNode jsonObject = objectMapper.valueToTree(itemCode);
        jsonObject.put("id", id);
        MiscUtils.getLogger().debug(jsonObject.toString());
        response.getOutputStream().write(jsonObject.toString().getBytes());
        return null;
    }

    public String returnToSearch() {
        String whereTo = request.getParameter("whereTo");
        String retval;
        if (whereTo == null || whereTo.equals("") || whereTo.equals("null")) {
            retval = SUCCESS;
        } else {
            retval = "private";
        }
        return retval;
    }


    public String execute() throws IOException, ServletException {
        String method = request.getParameter("method");
        if ("ajaxCodeUpdate".equals(method)) {
            return ajaxCodeUpdate();
        } else if ("returnToSearch".equals(method)) {
            return returnToSearch();
        }

        if (request.getSession().getAttribute("user") == null) {
            return "Logout";
        }

        MiscUtils.getLogger().debug(submitButton);
        if (submitButton.equals("Edit")) {
            MiscUtils.getLogger().debug("here with codeid " + codeId);
            BillingCodeData bcd = new BillingCodeData();
            bcd.editBillingCode(code, desc, value, codeId);
        }

        String retval;
        if (whereTo == null || whereTo.equals("")) {
            retval = SUCCESS;
        } else {
            retval = "private";
        }

        return retval;
    }

    String codeId;
    String code;
    String desc;
    String value;
    String whereTo;
    String submitButton;

    /**
     * Getter for property codeId.
     *
     * @return Value of property codeId.
     */
    public String getCodeId() {
        return codeId;
    }

    /**
     * Setter for property codeId.
     *
     * @param codeId New value of property codeId.
     */
    public void setCodeId(String codeId) {
        this.codeId = codeId;
    }

    /**
     * Getter for property code.
     *
     * @return Value of property code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Setter for property code.
     *
     * @param code New value of property code.
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Getter for property desc.
     *
     * @return Value of property desc.
     */
    public String getDesc() {
        return desc;
    }

    /**
     * Setter for property desc.
     *
     * @param desc New value of property desc.
     */
    public void setDesc(String desc) {
        this.desc = desc;
    }

    /**
     * Getter for property value.
     *
     * @return Value of property value.
     */
    public String getValue() {
        return value;
    }

    /**
     * Setter for property value.
     *
     * @param value New value of property value.
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Getter for property whereTo.
     *
     * @return Value of property whereTo.
     */
    public String getWhereTo() {
        return whereTo;
    }

    /**
     * Setter for property whereTo.
     *
     * @param whereTo New value of property whereTo.
     */
    public void setWhereTo(String whereTo) {
        this.whereTo = whereTo;
    }

    /**
     * Getter for property submitButton.
     *
     * @return Value of property submitButton.
     */
    public String getSubmitButton() {
        return submitButton;
    }

    /**
     * Setter for property submitButton.
     *
     * @param submitButton New value of property submitButton.
     */
    public void setSubmitButton(String submitButton) {
        this.submitButton = submitButton;
    }
}
