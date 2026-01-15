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

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import ca.openosp.openo.model.LookupTableDefValue;
import ca.openosp.openo.services.LookupManager;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.utility.SpringUtils;

public class LookupCodeList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private LookupManager lookupManager = SpringUtils.getBean(LookupManager.class);

    public String execute() {
        return list();
    }

    private String list() {
        String tableId = request.getParameter("id");
        LookupTableDefValue tableDef = lookupManager.GetLookupTableDef(tableId);

        List lst = lookupManager.LoadCodeList(tableId, false, null, null);

        this.setCodes(lst);
        this.setTableDef(tableDef);
        return "list";
    }

    public boolean isReadOnly(HttpServletRequest request, String funName) {
        boolean readOnly = false;

        return readOnly;
    }

    private List codes;

    public List getCodes() {
        return codes;
    }

    public void setCodes(List codes) {
        this.codes = codes;
    }

    public LookupTableDefValue getTableDef() {
        return tableDef;
    }

    public void setTableDef(LookupTableDefValue tableDef) {
        this.tableDef = tableDef;
    }

    private LookupTableDefValue tableDef;


}
