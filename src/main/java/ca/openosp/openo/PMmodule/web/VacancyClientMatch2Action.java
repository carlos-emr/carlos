//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package ca.openosp.openo.PMmodule.web;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.WaitlistDao;
import ca.openosp.openo.PMmodule.wlmatch.MatchBO;
import ca.openosp.openo.PMmodule.wlmatch.MatchingManager;
import ca.openosp.openo.PMmodule.wlmatch.VacancyDisplayBO;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class VacancyClientMatch2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private WaitlistDao waitlistDao = SpringUtils.getBean(WaitlistDao.class);


    @Override
    public String execute() throws Exception {
        MatchingManager matchingManager = new MatchingManager();
        double minPercentage = 0.0;
        try {
            minPercentage = Double.parseDouble(request.getParameter("percentage"));
        } catch (Exception e) {
            logger.info("Failed to get match cutoff!");
        }
        Integer vacancyId = Integer.parseInt(request.getParameter("vacancyId"));
        logger.info("vacancyID: " + vacancyId);
        //List<MatchBO> matchList= matchingManager.listTopMatches(vacancyID, 50);
        List<MatchBO> matchList = matchingManager.getClientMatchesWithMinMatchPercentage(vacancyId, minPercentage);
        logger.info(" VacancyClientMatchList: " + matchList.size());

        request.setAttribute("clientList", matchList);

        VacancyDisplayBO dis = matchingManager.getVacancyDisplay(vacancyId);
        this.setTemplate(dis.getVacancyTemplateName());

        this.setVacancy(dis.getVacancyName());

        List<String> criteriaList = getCriteriaList(dis.getCriteriaSummary());
        this.setCriteriaList(criteriaList);

        int programId = waitlistDao.getProgramIdByVacancyId(vacancyId);
        this.setProgramId(programId);
        return SUCCESS;
    }

    private List<String> getCriteriaList(String criteriaSummary) {
        List<String> criteriaList = new ArrayList<>();
        if (criteriaSummary != null && criteriaSummary.length() > 0) {
            criteriaSummary = criteriaSummary.replaceAll(" AND ", "\n");
            StringTokenizer st = new StringTokenizer(criteriaSummary, "\n");
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                if (token != null && token.trim().length() > 0) {
                    criteriaList.add(token.trim());
                }
            }
        }
        return criteriaList;
    }

    private String template;

    private String vacancy;

    private List<String> criteriaList;

    int programId;

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getVacancy() {
        return vacancy;
    }

    public void setVacancy(String vacancy) {
        this.vacancy = vacancy;
    }

    public List<String> getCriteriaList() {
        return criteriaList;
    }

    public void setCriteriaList(List<String> criteriaList) {
        this.criteriaList = criteriaList;
    }

    public int getProgramId() {
        return programId;
    }

    public void setProgramId(int programId) {
        this.programId = programId;
    }
}
