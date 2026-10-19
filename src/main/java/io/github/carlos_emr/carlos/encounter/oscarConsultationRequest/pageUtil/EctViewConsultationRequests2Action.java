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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDaoImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Gate action for the Consultations list page ({@code encounter/ViewConsultation}).
 *
 * <p>Checks {@code _con r}, parses the date filters, and resolves the optional Consultant
 * ({@code consultantId}, a specialist specId) and Provider ({@code filterProviderNo}, the
 * patient's MRP) filters added for issue #3976 through {@link ConsultationListFilterResolver}
 * before forwarding to {@code ViewConsultationRequests.jsp}.</p>
 */
public class EctViewConsultationRequests2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager;
    private final ConsultationListFilterResolver filterResolver;

    private String sendTo;
    private String currentTeam;

    private String startDate;
    private String endDate;
    private String includeCompleted;
    private String orderby;
    private String desc;
    private String searchDate = null;
    private Integer offset;
    private Integer limit = ConsultationRequestDaoImpl.DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT;
    // Bound as text and parsed here: an Integer property would turn "abc" into a Struts
    // conversion error and an unmapped "input" result instead of simply ignoring the filter.
    private String consultantId;
    private String filterProviderNo;

    /**
     * Struts/Spring entry point: resolves the collaborators from the Spring context.
     */
    public EctViewConsultationRequests2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), ConsultationListFilterResolver.fromSpringContext());
    }

    /**
     * Test constructor.
     */
    EctViewConsultationRequests2Action(SecurityInfoManager securityInfoManager,
                                       ConsultationListFilterResolver filterResolver) {
        this.securityInfoManager = securityInfoManager;
        this.filterResolver = filterResolver;
    }

    public String execute() throws ServletException, IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        String defaultPattern = "yyyy-MM-dd";
        String includeCompleted = null;
        boolean includedComp = false;

        if (includeCompleted != null && includeCompleted.equals("include")) {
            includedComp = true;
        }

        SimpleDateFormat simpleDateFormat = null;

        try {
            if (startDate != null && !startDate.isEmpty()) {
                simpleDateFormat = new SimpleDateFormat(defaultPattern);
                request.setAttribute("startDate", simpleDateFormat.parse(startDate));
            }

            if (endDate != null && !endDate.isEmpty()) {
                if (simpleDateFormat == null) {
                    simpleDateFormat = new SimpleDateFormat(defaultPattern);
                }
                request.setAttribute("endDate", simpleDateFormat.parse(endDate));
            }
        } catch (Exception e) {
            logger.error("Cannot parse start date " + startDate + " and/or end date " + endDate + " for consultation request report. ", e);
        }

        request.setAttribute("includeCompleted", Boolean.valueOf(includedComp));
        request.setAttribute("teamVar", sendTo);
        request.setAttribute("orderby", orderby);
        request.setAttribute("desc", desc);
        request.setAttribute("searchDate", searchDate);

        filterResolver.publish(request, loggedInInfo, consultantId, filterProviderNo);
        return SUCCESS;
    }

    public String getSendTo() {
        return sendTo;
    }

    @StrutsParameter
    public void setSendTo(String str) {
        sendTo = str;
    }

    public String getCurrentTeam() {
        if (currentTeam == null)
            currentTeam = new String();
        return currentTeam;
    }

    @StrutsParameter
    public void setCurrentTeam(String str) {
        currentTeam = str;
    }

    /**
     * Getter for property startDate.
     *
     * @return Value of property startDate.
     */
    public String getStartDate() {
        return startDate;
    }

    /**
     * Setter for property startDate.
     *
     * @param startDate New value of property startDate.
     */
    @StrutsParameter
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    /**
     * Getter for property endDate.
     *
     * @return Value of property endDate.
     */
    public String getEndDate() {
        return endDate;
    }

    /**
     * Setter for property endDate.
     *
     * @param endDate New value of property endDate.
     */
    @StrutsParameter
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    /**
     * Getter for property includeCompleted.
     *
     * @return Value of property includeCompleted.
     */
    public String getIncludeCompleted() {
        return includeCompleted;
    }

    /**
     * Setter for property includeCompleted.
     *
     * @param includeCompleted New value of property includeCompleted.
     */
    @StrutsParameter
    public void setIncludeCompleted(String includeCompleted) {
        this.includeCompleted = includeCompleted;
    }

    /**
     * Getter for property orderby.
     *
     * @return Value of property orderby.
     */
    public String getOrderby() {
        return orderby;
    }

    /**
     * Setter for property orderby.
     *
     * @param orderby New value of property orderby.
     */
    @StrutsParameter
    public void setOrderby(String orderby) {
        this.orderby = orderby;
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
    @StrutsParameter
    public void setDesc(String desc) {
        this.desc = desc;
    }

    /**
     * Getter for property searchDate.
     *
     * @return Value of property searchDate.
     */
    public String getSearchDate() {
        if (searchDate == null) {
            searchDate = "0";
        }
        return searchDate;
    }

    /**
     * Setter for property searchDate.
     *
     * @param searchDate New value of property searchDate.
     */
    @StrutsParameter
    public void setSearchDate(String searchDate) {
        this.searchDate = searchDate;
    }

    public Integer getOffset() {
        return offset;
    }

    @StrutsParameter
    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getLimit() {
        return limit;
    }

    @StrutsParameter
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    // No getters for consultantId / filterProviderNo on purpose: the JSP must only ever see the
    // values ConsultationListFilterResolver validated, never the raw parameters via the value stack.
    @StrutsParameter
    public void setConsultantId(String consultantId) {
        this.consultantId = consultantId;
    }

    @StrutsParameter
    public void setFilterProviderNo(String filterProviderNo) {
        this.filterProviderNo = filterProviderNo;
    }
}
