//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 */
package ca.openosp.openo.www.lookup;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.MyDateFormat;

import ca.openosp.openo.commons.KeyConstants;
import ca.openosp.openo.model.FieldDefValue;
import ca.openosp.openo.model.LookupTableDefValue;
import ca.openosp.openo.services.LookupManager;
import ca.openosp.openo.utils.Utility;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.utility.SpringUtils;

public class LookupCodeEdit2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private LookupManager lookupManager = SpringUtils.getBean(LookupManager.class);

    public String execute() throws Exception {
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }
        return loadCode();
    }

    private String loadCode() {

        String[] codeIds = request.getParameter("id").split(":");
        String tableId = codeIds[0];
        String code = "0";
        boolean isNew = true;
        if (codeIds.length > 1) {
            code = codeIds[1];
            isNew = false;
        }

        LookupTableDefValue tableDef = lookupManager.GetLookupTableDef(tableId);

        List codeFields = lookupManager.GetCodeFieldValues(tableDef, code);
        boolean editable = false;
        for (int i = 0; i < codeFields.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) codeFields.get(i);
            if (isNew && fdv.getGenericIdx() == 1 && !fdv.isAuto()) fdv.setEditable(true);  // force a new code be added
            if (fdv.isEditable()) {
                editable = true;
                break;
            }
        }
        boolean isReadOnly = false;

        if (!editable) isReadOnly = true;
        if (isReadOnly) request.setAttribute("isReadOnly", Boolean.valueOf(isReadOnly));
        return "edit";
    }

    public String save() throws Exception {
        LookupTableDefValue tableDef = this.getTableDef();
        List fieldDefList = this.getCodeFields();
        boolean isNew = this.isNewCode();

        boolean isInActive = false;

        String code = "";
        String providerNo = (String) request.getSession(true).getAttribute(KeyConstants.SESSION_KEY_PROVIDERNO);
        Map map = request.getParameterMap();
        for (int i = 0; i < fieldDefList.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fieldDefList.get(i);
            if (fdv.getGenericIdx() == 8) {
                fdv.setVal(providerNo);
            } else if (fdv.getGenericIdx() == 9) {
                fdv.setVal(MyDateFormat.getStandardDateTime(Calendar.getInstance()));
            } else {
                String[] val = (String[]) map.get("field[" + i + "].val");
                if (val != null) {
                    fdv.setVal(val[0]);
                    if (fdv.getGenericIdx() == 1) code = fdv.getVal();
                    if (fdv.getGenericIdx() == 3) isInActive = "0".equals(fdv.getVal());
                    if ("D".equals(fdv.getFieldType())) {
                        if (!Utility.IsDate(fdv.getVal())) {
                            addActionMessage(getText("error.lookup.date", fdv.getFieldDesc()));
                        }
                    } else if ("N".equals(fdv.getFieldType())) {
                        if (!(fdv.isAuto() && isNew)) {
                            if (!Utility.IsInt(fdv.getVal())) {
                                addActionMessage(getText("error.lookup.integer", fdv.getFieldDesc()));
                            } else if (Utility.IsIntLessThanZero(fdv.getVal())) {
                                addActionMessage(getText("error.lookup.integer_eq0", fdv.getFieldDesc()));
                            } else if (!Utility.IsIntBiggerThanZero(fdv.getVal())) {
                                if (fdv.getGenericIdx() == 1) {
                                    addActionMessage(getText("error.lookup.integer_gt0", fdv.getFieldDesc()));
                                }
                            }
                        }
                    } else if ("S".equals(fdv.getFieldType())) {
                        if (Utility.IsEmpty(fdv.getVal()) && fdv.getGenericIdx() == 1) {
                            addActionMessage(getText("error.lookup.empty", fdv.getFieldDesc()));
                        }
                    }
                } else {
                    if (fdv.getGenericIdx() == 1) {
                        addActionMessage(getText("error.lookup.empty", fdv.getFieldDesc()));
                    }
                    fdv.setVal("");
                }
            }
        }
        if ((!isNew) && isInActive) {
            if ("SHL,OGN".indexOf(tableDef.getTableId()) >= 0) {
                int clientCount = lookupManager.getCountOfActiveClient(tableDef.getTableId().substring(0, 1) + code);
                if (clientCount > 0)
                    addActionMessage(getText("error.lookup.client", tableDef.getDescription()));
            }
        }
        if (!getActionMessages().isEmpty()) {
            return "edit";
        }
        try {
            code = lookupManager.SaveCodeValue(isNew, tableDef, fieldDefList);
            fieldDefList = lookupManager.GetCodeFieldValues(tableDef, code);
            this.setCodeFields(fieldDefList);
            this.setNewCode(false);
            this.setErrMsg("Saved Successfully");
            addActionMessage(getText("error.lookup.success"));
            return "edit";
        } catch (SQLException e) {
            addActionMessage(getText("error.lookup.duplicate"));
            return "edit";
        }
    }

    public boolean isReadOnly(HttpServletRequest request, String funName) {
        return false;
    }

    List codeFields;
    LookupTableDefValue tableDef;
    boolean newCode;
    String errMsg;

    public List getCodeFields() {
        return codeFields;
    }

    public void setCodeFields(List codeFields) {
        this.codeFields = codeFields;
    }

    public boolean isNewCode() {
        return newCode;
    }

    public void setNewCode(boolean newCode) {
        this.newCode = newCode;
    }

    public LookupTableDefValue getTableDef() {
        return tableDef;
    }

    public void setTableDef(LookupTableDefValue tableDef) {
        this.tableDef = tableDef;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }
}
