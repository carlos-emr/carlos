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

package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDao;
import io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.QuickListDao;
import io.github.carlos_emr.carlos.commn.model.AbstractCodeSystemModel;
import io.github.carlos_emr.carlos.commn.model.QuickList;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class dxResearchUpdateQuickList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_dxresearch", "w", null)) {
            throw new RuntimeException("missing required sec object (_dxresearch)");
        }

        String codingSystem = this.getSelectedCodingSystem();
        String curUser = (String) request.getSession().getAttribute("user");
        boolean valid = true;

        if (forward == null || quickListName == null || quickListName.isEmpty()) {
            return SUCCESS;
        }

        if (forward.equals("add")) {
            valid = doAdd(quickListName, codingSystem, curUser);
        } else if (forward.equals("remove")) {
            doRemove(quickListName);
        }

        if (!valid) {
            request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
            return "failure";
        }

        return SUCCESS;
    }

    private void doRemove(String quickListName) {
        String[] removedItems = this.getQuickListItems();
        String[] itemValues;
        if (removedItems != null) {
            for (int i = 0; i < removedItems.length; i++) {
                itemValues = removedItems[i].split(",");

                QuickListDao quickListDao = SpringUtils.getBean(QuickListDao.class);
                List<QuickList> quickLists = quickListDao.findByNameResearchCodeAndCodingSystem(quickListName, itemValues[1], itemValues[0]);
                for (QuickList q : quickLists) {
                    quickListDao.remove(q.getId());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean doAdd(String quickListName, String codingSystem, String curUser) {
        boolean valid = true;
        String[] xml_research = this.getXmlResearch();
        AbstractCodeSystemDao<AbstractCodeSystemModel<?>> dao = (AbstractCodeSystemDao<AbstractCodeSystemModel<?>>) SpringUtils.getBean(AbstractCodeSystemDao.getDaoName(AbstractCodeSystemDaoImpl.codingSystem.valueOf(codingSystem)));

        for (int i = 0; i < xml_research.length; i++) {
            if (xml_research[i] == null || xml_research[i].equals("")) {
                continue;
            }

            //need to validate the dxresearch code before write to the database
            AbstractCodeSystemModel<?> codingSystemEntity = dao.findByCodingSystem(xml_research[i]);
            boolean isCodingSystemEntitySet = codingSystemEntity != null;
            if (!isCodingSystemEntitySet) {
                valid = false;
                addActionError(getText("errors.codeNotFound", new String[]{xml_research[i], codingSystem}));

            } else {
                QuickListDao quickListDao = SpringUtils.getBean(QuickListDao.class);
                List<QuickList> quickLists = quickListDao.findByNameResearchCodeAndCodingSystem(quickListName, xml_research[i], codingSystem);
                if (!quickLists.isEmpty()) {
                    continue;
                }

                QuickList ql = new QuickList();
                ql.setQuickListName(quickListName);
                ql.setDxResearchCode(xml_research[i]);
                ql.setCreatedByProvider(curUser);
                ql.setCodingSystem(codingSystem);

                quickListDao.persist(ql);

            }

        }
        return valid;
    }

    private String[] quickListItems;
    private String quickListName;
    private String forward;
    private String xml_research1;
    private String xml_research2;
    private String xml_research3;
    private String xml_research4;
    private String xml_research5;
    private String selectedCodingSystem;

    public String[] getXmlResearch() {
        return new String[]{xml_research1, xml_research2, xml_research3, xml_research4, xml_research5};
    }

    public String getSelectedCodingSystem() {
        return selectedCodingSystem;
    }

    @StrutsParameter
    public void setSelectedCodingSystem(String cs) {
        selectedCodingSystem = cs;
    }

    public String[] getQuickListItems() {
        return quickListItems;
    }

    @StrutsParameter
    public void setQuickListItems(String[] quickListItems) {
        this.quickListItems = quickListItems;
    }

    public String getQuickListName() {
        return quickListName;
    }

    @StrutsParameter
    public void setQuickListName(String quickListName) {
        this.quickListName = quickListName;
    }

    public String getForward() {
        return forward;
    }

    @StrutsParameter
    public void setForward(String forward) {
        this.forward = forward;
    }

    public String getXml_research1() {
        return xml_research1;
    }

    @StrutsParameter
    public void setXml_research1(String xml_research1) {
        this.xml_research1 = xml_research1;
    }

    public String getXml_research2() {
        return xml_research2;
    }

    @StrutsParameter
    public void setXml_research2(String xml_research2) {
        this.xml_research2 = xml_research2;
    }

    public String getXml_research3() {
        return xml_research3;
    }

    @StrutsParameter
    public void setXml_research3(String xml_research3) {
        this.xml_research3 = xml_research3;
    }

    public String getXml_research4() {
        return xml_research4;
    }

    @StrutsParameter
    public void setXml_research4(String xml_research4) {
        this.xml_research4 = xml_research4;
    }

    public String getXml_research5() {
        return xml_research5;
    }

    @StrutsParameter
    public void setXml_research5(String xml_research5) {
        this.xml_research5 = xml_research5;
    }


}
