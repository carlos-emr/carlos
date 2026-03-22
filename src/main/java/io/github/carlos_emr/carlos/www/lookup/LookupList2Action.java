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
import io.github.carlos_emr.carlos.model.security.NoAccessException;
import io.github.carlos_emr.carlos.services.LookupManager;
import io.github.carlos_emr.carlos.utils.Utility;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts 2 action for listing and searching lookup codes within a lookup table.
 *
 * <p>Supports browsing lookup table entries by table ID and parent code,
 * as well as keyword-based searching. Access to certain system tables
 * (PRP, SIT, LKT, QGV, RPG) is restricted.
 *
 * @since 2009-01-01
 */
public class LookupList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private LookupManager lookupManager = SpringUtils.getBean(LookupManager.class);

    /**
     * Routes the request to search or list based on the "method" parameter.
     *
     * @return String the Struts result name
     * @throws NoAccessException if the user attempts to access restricted lookup tables
     */
    public String execute() throws NoAccessException {
        if ("search".equals(request.getParameter("method"))) {
            return search();
        }
        return list();
    }

    private String list() throws NoAccessException {
        String tableId = request.getParameter("tableId");
        if ("PRP,SIT,LKT,QGV,RPG".indexOf(tableId) > 0) throw new NoAccessException();

        String parentCode = request.getParameter("parentCode");
        request.setAttribute("parentCode", parentCode);

        LookupTableDefValue tableDef = lookupManager.GetLookupTableDef(tableId);
        List lst = lookupManager.LoadCodeList(tableId, true, parentCode, null, null);
        this.setLookups(lst);
        this.setTableDef(tableDef);

        request.setAttribute("notoken", "Y");
        return "list";
    }

    /**
     * Searches lookup codes by keyword within the specified table.
     *
     * @return String the "list" result name with filtered results
     */
    public String search() {
        String tableId = request.getParameter("tableId");
        String parentCode = request.getParameter("parentCode");
        if (Utility.IsEmpty(parentCode)) parentCode = this.getParentCode();
        List lst = lookupManager.LoadCodeList(tableId, true, parentCode, null, this.getKeywordName());
        LookupTableDefValue tableDef = lookupManager.GetLookupTableDef(tableId);
        this.setLookups(lst);
        this.setTableDef(tableDef);
        request.setAttribute("notoken", "Y");
        return "list";
    }


    /**
     * Determines whether the lookup list is read-only for the given function.
     *
     * @param request HttpServletRequest the current request
     * @param funName String the function name to check
     * @return boolean always returns {@code false} in the current implementation
     */
    public boolean isReadOnly(HttpServletRequest request, String funName) {
        boolean readOnly = false;

        return readOnly;
    }

    List lookups;
    String openerForm;
    String codeName;
    String descName;
    String keywordName;
    String tableId;
    String parentCode;
    String grandParentCode;
    LookupTableDefValue tableDef;

    public List getLookups() {
        return lookups;
    }

    public void setLookups(List lookups) {
        this.lookups = lookups;
    }

    public String getCodeName() {
        return codeName;
    }

    @StrutsParameter
    public void setCodeName(String codeName) {
        this.codeName = codeName;
    }

    public String getDescName() {
        return descName;
    }

    @StrutsParameter
    public void setDescName(String descName) {
        this.descName = descName;
    }

    public String getOpenerForm() {
        return openerForm;
    }

    @StrutsParameter
    public void setOpenerForm(String openerForm) {
        this.openerForm = openerForm;
    }

    public String getKeywordName() {
        return keywordName;
    }

    @StrutsParameter
    public void setKeywordName(String keywordName) {
        this.keywordName = keywordName;
    }

    public String getTableId() {
        return tableId;
    }

    @StrutsParameter
    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public LookupTableDefValue getTableDef() {
        return tableDef;
    }

    public void setTableDef(LookupTableDefValue tableDef) {
        this.tableDef = tableDef;
    }

    public String getGrandParentCode() {
        return grandParentCode;
    }

    @StrutsParameter
    public void setGrandParentCode(String grandParentCode) {
        this.grandParentCode = grandParentCode;
    }

    public String getParentCode() {
        return parentCode;
    }

    @StrutsParameter
    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }


}
