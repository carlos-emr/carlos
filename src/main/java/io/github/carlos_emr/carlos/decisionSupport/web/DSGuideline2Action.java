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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.decisionSupport.web;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.decisionSupport.model.DSCondition;
import io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence;
import io.github.carlos_emr.carlos.decisionSupport.model.DSDemographicAccess;
import io.github.carlos_emr.carlos.decisionSupport.model.DSGuideline;
import io.github.carlos_emr.carlos.decisionSupport.model.DSGuidelineFactory;
import io.github.carlos_emr.carlos.decisionSupport.service.DSService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.demographic.data.DemographicData;

/**
 * Struts2 action for managing and displaying clinical decision support guidelines.
 * <p>
 * This action provides a web interface for healthcare providers to view their assigned
 * clinical decision support guidelines and examine detailed evaluation results for
 * specific patients. It supports two main operations:
 * </p>
 * <ul>
 *   <li><strong>list</strong> - Displays all guidelines assigned to a specific provider</li>
 *   <li><strong>detail</strong> - Shows detailed evaluation of a specific guideline, optionally
 *       evaluated against a specific patient's data with per-condition results</li>
 * </ul>
 *
 * @since 2009-07-06
 * @see DSService for guideline evaluation logic
 * @see DSGuideline for guideline definitions
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DSGuideline2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private DSService dsService = SpringUtils.getBean(DSService.class);

    /**
     * Default constructor for DSGuideline2Action.
     */
    public DSGuideline2Action() {

    }

    /**
     * Routes to the appropriate method based on the "method" request parameter.
     * <p>
     * Dispatches to {@link #detail()} when method equals "detail", otherwise
     * defaults to {@link #list()}.
     * </p>
     *
     * @return String Struts result name ("guidelineList" or "guidelineDetail")
     * @throws Exception if guideline evaluation fails
     */
    public String execute() throws Exception {
        if ("detail".equals(request.getParameter("method"))) {
            return detail();
        }
        return list();
    }

    public String list() {
        String providerNo = request.getParameter("provider_no");
        List<DSGuideline> providerGuidelines = new ArrayList<DSGuideline>();
        if (providerNo != null)
            providerGuidelines = dsService.getDsGuidelinesByProvider(providerNo);
        request.setAttribute("guidelines", providerGuidelines);
        return "guidelineList";
    }

    public String detail() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String guidelineId = request.getParameter("guidelineId");
        String demographicNo = request.getParameter("demographic_no");
        if (guidelineId == null) {
            response.getWriter().println("guidelineId cannot be null");
            return null;
        }
        DSGuidelineFactory factory = new DSGuidelineFactory();
        DSGuideline dsGuideline = dsService.findGuideline(Integer.parseInt(guidelineId));
        if (demographicNo == null) { //if just viewing details about guideline.
            request.setAttribute("guideline", dsGuideline);
            List<DSCondition> dsConditions = dsGuideline.getConditions();
            List<ConditionResult> conditionResults = new ArrayList<ConditionResult>();
            for (DSCondition dsCondition : dsConditions) {
                conditionResults.add(new ConditionResult(dsCondition, null, null));
            }
            request.setAttribute("conditionResults", conditionResults);
            return "guidelineDetail";
        }
        List<DSCondition> dsConditions = dsGuideline.getConditions();
        List<ConditionResult> conditionResults = new ArrayList<ConditionResult>();
        for (DSCondition dsCondition : dsConditions) { //if viewing details about guideline in regards to patient
            DSGuideline testGuideline = factory.createBlankGuideline();
            //BeanUtils.copyProperties(dsCondition, testGuideline);
            ArrayList<DSCondition> testCondition = new ArrayList<DSCondition>();
            testCondition.add(dsCondition);
            testGuideline.setConditions(testCondition);
            testGuideline.setConsequences(new ArrayList<DSConsequence>());
            testGuideline.setTitle(dsGuideline.getTitle());
            testGuideline.setParsed(true); //supress parsing of xml, othewrise would overwrite the condition
            boolean result = testGuideline.evaluateBoolean(loggedInInfo, demographicNo);
            DSDemographicAccess demographicAccess = new DSDemographicAccess(loggedInInfo, demographicNo);
            String actualValues = demographicAccess.getDemogrpahicValues(dsCondition.getConditionType());
            conditionResults.add(new ConditionResult(dsCondition, result, actualValues));
        }
        DemographicData demographicData = new DemographicData();
        Demographic demographic = demographicData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographicNo);

        request.setAttribute("patientName", demographic.getFirstName() + " " + demographic.getLastName());
        request.setAttribute("guideline", dsGuideline);
        request.setAttribute("consequences", dsGuideline.evaluate(loggedInInfo, demographicNo));
        request.setAttribute("conditionResults", conditionResults);
        request.setAttribute("demographicAccess", new DSDemographicAccess(loggedInInfo, demographicNo));
        return "guidelineDetail";

    }

    //for returning stuff
    public class ConditionResult {
        DSCondition condition;
        Boolean result;
        String actualValues;

        public ConditionResult(DSCondition condition, Boolean result, String actualValues) {
            this.condition = condition;
            this.result = result;
            this.actualValues = actualValues;
        }

        public void setCondition(DSCondition condition) {
            this.condition = condition;
        }

        public DSCondition getCondition() {
            return this.condition;
        }

        public void setResult(Boolean result) {
            this.result = result;
        }

        public Boolean getResult() {
            return this.result;
        }

        public void setActualValues(String actualValues) {
            this.actualValues = actualValues;
        }

        public String getActualValues() {
            return this.actualValues;
        }
    }
}
