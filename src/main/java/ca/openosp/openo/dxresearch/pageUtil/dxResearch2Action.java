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


package ca.openosp.openo.dxresearch.pageUtil;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.AbstractCodeSystemDao;
import ca.openosp.openo.commn.dao.DxresearchDAO;
import ca.openosp.openo.commn.model.AbstractCodeSystemModel;
import ca.openosp.openo.commn.model.Dxresearch;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.util.ConversionUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;

public class dxResearch2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_dxresearch", "w", null)) {
            throw new RuntimeException("missing required sec object (_dxresearch)");
        }

        //dxResearchForm frm = (dxResearchForm) form;
        //request.getSession().setAttribute("dxResearchForm", frm);
        String codingSystem = this.getSelectedCodingSystem();
        String demographicNo = this.getDemographicNo();
        String providerNo = this.getProviderNo();
        String forward = this.getForward();
        String[] xml_research = null;
        String[] codingSystems = null;
        boolean multipleCodes = false;

        if (!forward.equals("")) {
            xml_research = new String[1];
            xml_research[0] = forward;
            //We` have to split codingSystem from actual code value
        } else if (request.getParameterValues("xml_research") != null) {
            String[] values = request.getParameterValues("xml_research");
            String[] code;
            xml_research = new String[values.length];
            codingSystems = new String[values.length];
            for (int idx = 0; idx < values.length; ++idx) {
                code = values[idx].split(",");
                xml_research[idx] = code[1];
                codingSystems[idx] = code[0];
            }

            if (values.length > 0)
                multipleCodes = true;

        } else {
            xml_research = new String[5];
            xml_research[0] = this.getXml_research1();
            xml_research[1] = this.getXml_research2();
            xml_research[2] = this.getXml_research3();
            xml_research[3] = this.getXml_research4();
            xml_research[4] = this.getXml_research5();
        }
        boolean valid = true;
        DxresearchDAO dao = (DxresearchDAO) SpringUtils.getBean(DxresearchDAO.class);

        for (int i = 0; i < xml_research.length; i++) {
            int count = 0;
            if (multipleCodes) codingSystem = codingSystems[i];

            if (xml_research[i].compareTo("") != 0) {
                List<Dxresearch> research = dao.findByDemographicNoResearchCodeAndCodingSystem(ConversionUtils.fromIntString(demographicNo), xml_research[i], codingSystem);

                for (Dxresearch r : research) {
                    count = count + 1;

                    r.setUpdateDate(new Date());
                    r.setStatus('A');

                    dao.save(r);

                    String ip = request.getRemoteAddr();
                    LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.UPDATE, "DX", "" + r.getId(), ip, "");

                }

                if (count == 0) {
                    Class<?> daoClass = AbstractCodeSystemDao.getDaoName(AbstractCodeSystemDao.codingSystem.valueOf(codingSystem));
                    @SuppressWarnings("unchecked")
                    AbstractCodeSystemDao<AbstractCodeSystemModel<?>> csDao = (AbstractCodeSystemDao<AbstractCodeSystemModel<?>>) SpringUtils.getBean(daoClass);

                    AbstractCodeSystemModel<?> codingSystemEntity = csDao.findByCodingSystem(codingSystem);
                    boolean isCodingSystemAvailable = codingSystemEntity == null;

                    if (csDao.findByCode(xml_research[i]) == null) {
                        valid = false;
                        addActionError(getText("errors.codeNotFound", new String[]{xml_research[i], codingSystem}));

                    } else {
                        Dxresearch dr = new Dxresearch();
                        dr.setDemographicNo(Integer.valueOf(demographicNo));
                        dr.setStartDate(new Date());
                        dr.setUpdateDate(new Date());
                        dr.setStatus('A');
                        dr.setDxresearchCode(xml_research[i]);
                        dr.setCodingSystem(codingSystem);
                        dr.setProviderNo(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo());
                        dao.persist(dr);

                        String ip = request.getRemoteAddr();
                        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, "DX", "" + dr.getId(), ip, "");

                    }
                }
            }

        }

        if (!valid) {
            response.sendRedirect(request.getContextPath() + "/oscarResearch/oscarDxResearch/dxResearch.jsp");
            return NONE;
        }

        String forwardTo = "success";
        if (request.getParameter("forwardTo") != null) {
            forwardTo = request.getParameter("forwardTo");
        }

        StringBuilder actionforward = new StringBuilder();
        if ("success".equals(forwardTo)) {
            actionforward = new StringBuilder(request.getContextPath() + "/oscarResearch/dxresearch/setupDxResearch.do");
        } else if ("codeSearch".equals(forwardTo)) {
            actionforward = new StringBuilder(request.getContextPath() + "/oscarResearch/dxresearch/dxcodeSearch.do");
        } else if ("codeList".equals(forwardTo)) {
            actionforward = new StringBuilder(request.getContextPath() + "/oscarResearch/oscarDxResearch/quickCodeList.jsp");
        }
        actionforward.append("?demographicNo=").append(demographicNo);
        actionforward.append("&providerNo=").append(providerNo);
        actionforward.append("&quickList=");

        response.sendRedirect(actionforward.toString());
        return NONE;
    }

    private String demographicNo;
    private String providerNo;
    private String xml_research1;
    private String xml_research2;
    private String xml_research3;
    private String xml_research4;
    private String xml_research5;
    private String quickList;
    private String[] quickListItems;
    private String forward;
    private String curCodingSystem;

    public String getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getXml_research1() {
        return xml_research1;
    }

    public void setXml_research1(String xml_research1) {
        this.xml_research1 = xml_research1;
    }

    public String getXml_research2() {
        return xml_research2;
    }

    public void setXml_research2(String xml_research2) {
        this.xml_research2 = xml_research2;
    }

    public String getXml_research3() {
        return xml_research3;
    }

    public void setXml_research3(String xml_research3) {
        this.xml_research3 = xml_research3;
    }

    public String getXml_research4() {
        return xml_research4;
    }

    public void setXml_research4(String xml_research4) {
        this.xml_research4 = xml_research4;
    }

    public String getXml_research5() {
        return xml_research5;
    }

    public void setXml_research5(String xml_research5) {
        this.xml_research5 = xml_research5;
    }

    public String getQuickList() {
        return quickList;
    }

    public void setQuickList(String quickList) {
        this.quickList = quickList;
    }

    public String[] getQuickListItems() {
        return quickListItems;
    }

    public void setQuickListItems(String[] quickListItems) {
        this.quickListItems = quickListItems;
    }

    public String getForward() {
        return forward;
    }

    public void setForward(String forward) {
        this.forward = forward;
    }

    public String getSelectedCodingSystem() {
        return curCodingSystem;
    }

    public void setSelectedCodingSystem(String cs) {
        curCodingSystem = cs;
    }
}
