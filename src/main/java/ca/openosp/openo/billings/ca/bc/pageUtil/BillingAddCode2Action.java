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

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.billings.ca.bc.data.BillingCodeData;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class BillingAddCode2Action extends ActionSupport {
    private HttpServletRequest request = ServletActionContext.getRequest();

    public String execute() {
        if (request.getSession().getAttribute("user") == null) {
            return "Logout";
        }

        String pCode = code;
        String returnMessage = null;
        boolean added = true;

        if (whereTo != null && whereTo.equals("private")) {
            code = "A" + code;
        }

        BillingCodeData bcd = new BillingCodeData();
        List list = bcd.findBillingCodesByCode(code, 1);

        if (list.size() == 0) {
            bcd.addBillingCode(code, desc, value);
            returnMessage = "Code Added Successfully";
        } else {
            added = false;
            returnMessage = "Code Already in use";
            request.setAttribute("code", pCode);
            request.setAttribute("desc", desc);
            request.setAttribute("value", value);

        }

        request.setAttribute("returnMessage", returnMessage);
        String retval;
        if (whereTo == null || whereTo.equals("")) {
            if (added) {
                retval = SUCCESS;
                MiscUtils.getLogger().debug("success");
            } else {
                retval = "normalCodeError";
                MiscUtils.getLogger().debug("nCE");
            }
        } else {
            if (added) {
                retval = "private";
                MiscUtils.getLogger().debug("pri");
            } else {
                retval = "privateCodeError";
                MiscUtils.getLogger().debug("privCodErr");
            }
        }

        return retval;
    }
    String codeId;
    String code;
    String desc;
    String value;
    String whereTo;

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
}
