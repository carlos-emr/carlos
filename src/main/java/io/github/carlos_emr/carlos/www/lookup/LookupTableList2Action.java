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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import io.github.carlos_emr.carlos.model.LookupCodeValue;
import io.github.carlos_emr.carlos.services.LookupManager;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class LookupTableList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private LookupManager lookupManager = SpringUtils.getBean(LookupManager.class);

    public void setLookupManager(LookupManager lookupManager) {
        this.lookupManager = lookupManager;
    }

    public String execute() {
        return list();
    }

    private String list() {
        String tableId = "FCT";
        List lst = lookupManager.LoadCodeList(tableId, true, null, null);

        for (int i = 0; i < lst.size(); i++) {
            LookupCodeValue lkv = (LookupCodeValue) lst.get(i);
            List l1 = lookupManager.LoadCodeList("LKT", true, lkv.getCode(), null, null);
            lkv.setAssociates(l1);
        }
        this.setModules(lst);
        return "list";
    }

    private List modules;

    @StrutsParameter(depth = 1)
    public List getModules() {
        return modules;
    }

    @StrutsParameter
    public void setModules(List modules) {
        this.modules = modules;
    }
}
