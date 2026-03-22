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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.www.lookup;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import io.github.carlos_emr.carlos.model.LookupTableDefValue;
import io.github.carlos_emr.carlos.services.LookupManager;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts 2 action for listing all codes within a specific lookup table.
 *
 * <p>Retrieves the lookup table definition and all associated code entries
 * for display in the lookup code management interface.
 *
 * @since 2009-01-01
 */
public class LookupCodeList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private LookupManager lookupManager = SpringUtils.getBean(LookupManager.class);

    /**
     * Executes the action by loading all codes for the specified lookup table.
     *
     * @return String the "list" result name
     */
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

    /**
     * Determines whether the code list is read-only for the given function.
     *
     * @param request HttpServletRequest the current request
     * @param funName String the function name to check
     * @return boolean always returns {@code false} in the current implementation
     */
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
