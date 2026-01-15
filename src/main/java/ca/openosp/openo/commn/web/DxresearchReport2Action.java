//CHECKSTYLE:OFF
/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.openosp.openo.commn.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.DxresearchDAO;
import ca.openosp.openo.commn.dao.MyGroupDao;
import ca.openosp.openo.commn.model.DxRegistedPTInfo;
import ca.openosp.openo.managers.CodingSystemManager;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarDocumentCreator;
import ca.openosp.openo.dxresearch.bean.dxCodeSearchBean;
import ca.openosp.openo.dxresearch.bean.dxQuickListBeanHandler;
import ca.openosp.openo.dxresearch.bean.dxQuickListItemsHandler;
import ca.openosp.openo.dxresearch.util.dxResearchCodingSystem;


/**
 * @author toby
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DxresearchReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final static String SUCCESS = "success";
    private final static String EDIT_DESC = "editdesc";
    private DxresearchDAO dxresearchdao = SpringUtils.getBean(DxresearchDAO.class);
    private MyGroupDao mygroupdao = SpringUtils.getBean(MyGroupDao.class);
    private static final String REPORTS_PATH = "org/oscarehr/common/web/DxResearchReport.jrxml";

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("patientRegistedAll".equals(method)) {
            return patientRegistedAll();
        } else if ("patientExcelReport".equals(method)) {
            return patientExcelReport();
        } else if ("patientRegistedDistincted".equals(method)) {
            return patientRegistedDistincted();
        } else if ("patientRegistedDeleted".equals(method)) {
            return patientRegistedDeleted();
        } else if ("patientRegistedActive".equals(method)) {
            return patientRegistedActive();
        } else if ("patientRegistedResolve".equals(method)) {
            return patientRegistedResolve();
        } else if ("editDesc".equals(method)) {
            return editDesc();
        } else if ("addSearchCode".equals(method)) {
            return addSearchCode();
        } else if ("clearSearchCode".equals(method)) {
            return clearSearchCode();
        } else if ("getQuickListName".equals(method)) {
            return getQuickListName();
        }

        request.getSession().setAttribute("listview", new DxRegistedPTInfo());
        dxQuickListBeanHandler quicklistHd = new dxQuickListBeanHandler();
        request.getSession().setAttribute("allQuickLists", quicklistHd);
        dxResearchCodingSystem codingSys = new dxResearchCodingSystem();
        request.getSession().setAttribute("codingSystem", codingSys);
        request.getSession().setAttribute("radiovaluestatus", request.getSession().getAttribute("radiovaluestatus"));
        return SUCCESS;
    }

    public String patientRegistedAll() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);


        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedAll(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedAll");
        return SUCCESS;
    }

    public String patientExcelReport() {
        ServletOutputStream outputStream = getServletOstream(response);

        List<DxRegistedPTInfo> patients = null;

        if (request.getSession().getAttribute("listview").getClass().getCanonicalName().contains("ArrayList")) {
            patients = (List<DxRegistedPTInfo>) request.getSession().getAttribute("listview");
        } else if (request.getSession().getAttribute("listview").getClass().getCanonicalName().contains("DxRegistedPTInfo")) {
            patients = new ArrayList<DxRegistedPTInfo>();
            DxRegistedPTInfo info = (DxRegistedPTInfo) request.getSession().getAttribute("listview");
            patients.add(info);
        }

        String providerNo = request.getParameter("provider_no");

        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
        }

        String mode = (String) request.getSession().getAttribute("radiovaluestatus");

        OscarDocumentCreator osc = new OscarDocumentCreator();
        HashMap<String, String> reportParams = new HashMap<String, String>();
        reportParams.put("providers", providerNo);
        reportParams.put("mode", mode);

        InputStream reportInstream = this.getClass().getClassLoader().getResourceAsStream(REPORTS_PATH);

        response.setContentType("application/excel");
        response.setHeader("Content-disposition", "inline; filename=dxResearchReport.xls");

        osc.fillDocumentStream(reportParams, outputStream, OscarDocumentCreator.EXCEL, reportInstream, patients);

        return null;
    }

    public String patientRegistedDistincted() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);

        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedDistincted(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedDistincted");
        return SUCCESS;
    }

    protected ServletOutputStream getServletOstream(HttpServletResponse response) {
        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
        } catch (IOException ex) {
            MiscUtils.getLogger().warn("Warning", ex);
        }
        return outputStream;
    }

    public String patientRegistedDeleted() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);

        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedDeleted(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedDeleted");
        return SUCCESS;
    }

    public String patientRegistedActive() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);

        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedActive(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedActive");
        return SUCCESS;
    }

    public String patientRegistedResolve() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);

        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedResolve(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedResolve");
        return SUCCESS;
    }

    public String editDesc() {
        String editingCodeType = request.getParameter("editingCodeType");
        String editingCodeCode = request.getParameter("editingCodeCode");
        String editingCodeDesc = request.getParameter("editingCodeDesc");

        dxQuickListItemsHandler.updatePatientCodeDesc(editingCodeType, editingCodeCode, editingCodeDesc);

        editingCodeDesc = String.format("\"%s\"", editingCodeDesc);
        request.getSession().setAttribute("editingCodeDesc", editingCodeDesc);

        return SUCCESS;
    }

    @SuppressWarnings("unchecked")
    public String addSearchCode() {

        String quickListName = this.getQuickListName();
        List<dxCodeSearchBean> codeSearch = dxresearchdao.getQuickListItems(quickListName);
        String codeSingle = request.getParameter("codesearch");
        String codeSystem = request.getParameter("codesystem");
        String action = request.getParameter("action");
        dxCodeSearchBean newAddition = null;

        // check the code
        CodingSystemManager codingSystemManager = SpringUtils.getBean(CodingSystemManager.class);
        String codeDescription = null;

        if (codeSystem != null && !codeSystem.isEmpty()) {
            codeDescription = codingSystemManager.getCodeDescription(codeSystem.toLowerCase().trim(), codeSingle);
        }

        if (codeDescription != null && !codeDescription.isEmpty()) {
            newAddition = new dxCodeSearchBean();
            newAddition.setType(codeSystem);
            newAddition.setDxSearchCode(codeSingle);
            newAddition.setDescription(codeDescription);
        }

        if (request.getSession().getAttribute("codeSearch") != null) {
            List<dxCodeSearchBean> existcodeSearch = (List<dxCodeSearchBean>) request.getSession().getAttribute("codeSearch");
            codeSearch.addAll(existcodeSearch);
        }
        if (newAddition != null) {
            codeSearch.add(newAddition);
        }

        request.getSession().setAttribute("codeSearch", codeSearch);
        return SUCCESS;
    }

    public String clearSearchCode() {

        List existcodeSearch = null;

        if (request.getSession().getAttribute("codeSearch") != null && ((List) (request.getSession().getAttribute("codeSearch"))).size() > 0) {
            existcodeSearch = (List) (request.getSession().getAttribute("codeSearch"));
            existcodeSearch.clear();
        }

        request.getSession().setAttribute("codeSearch", existcodeSearch);

        return SUCCESS;
    }

    private String quickListName;

    public String getQuickListName() {
        return quickListName;
    }

    public void setQuickListName(String quickListName) {
        this.quickListName = quickListName;
    }
}
