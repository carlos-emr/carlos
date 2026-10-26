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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.web;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.time.DateUtils;
import io.github.carlos_emr.carlos.PMmodule.utility.DateTimeFormatUtils;
import io.github.carlos_emr.carlos.commn.model.Mortalities;
import io.github.carlos_emr.carlos.commn.model.ReportStatistic;
import io.github.carlos_emr.carlos.commn.model.ShelterPopulation;
import io.github.carlos_emr.carlos.commn.model.ShelterUsage;
import io.github.carlos_emr.carlos.commn.service.PopulationReportManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class PopulationReport2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

    // Forwards
    private static final String REPORT = "report";

    private PopulationReportManager populationReportManager = SpringUtils.getBean(PopulationReportManager.class);

    /**
     * One complete computation of the report, cached for an hour. It is published as a whole, so a
     * request never reads figures from two different computations, and only once every figure has
     * been computed: stamping the cache before computing let one failed compute serve an hour of
     * partial figures to everyone (issue #4855).
     */
    private record Figures(Date computedAt, ShelterPopulation shelterPopulation,
                           ShelterUsage shelterUsage, Mortalities mortalities,
                           Map<String, ReportStatistic> majorMedicalConditions,
                           Map<String, ReportStatistic> majorMentalIllnesses,
                           Map<String, ReportStatistic> seriousMedicalConditions,
                           Map<String, Map<String, String>> categoryCodeDescriptions,
                           long retrievedAt) {
    }

    private static final AtomicReference<Figures> CACHED_FIGURES = new AtomicReference<>();

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        return report();
    }

    public String report() {

        // simple caching mechanism (because this report is open to the public and we don't want the public smacking our server around)
        Figures figures = CACHED_FIGURES.get();
        if (figures == null || System.currentTimeMillis() - figures.retrievedAt() > DateUtils.MILLIS_PER_HOUR) {
            // Keep one refresh in flight. Atomic publication alone lets every concurrent cache
            // miss run all of the report's expensive queries before any result has been cached.
            synchronized (CACHED_FIGURES) {
                figures = CACHED_FIGURES.get();
                if (figures == null || System.currentTimeMillis() - figures.retrievedAt() > DateUtils.MILLIS_PER_HOUR) {
                    // Arguments are evaluated left to right: the cache time is taken after every
                    // figure, and a failing figure leaves nothing published.
                    figures = new Figures(Calendar.getInstance().getTime(),
                            populationReportManager.getShelterPopulation(),
                            populationReportManager.getShelterUsage(),
                            populationReportManager.getMortalities(),
                            populationReportManager.getMajorMedicalConditions(),
                            populationReportManager.getMajorMentalIllnesses(),
                            populationReportManager.getSeriousMedicalConditions(),
                            populationReportManager.getCategoryCodeDescriptions(),
                            System.currentTimeMillis());
                    CACHED_FIGURES.set(figures);
                }
            }
        }

        // set attributes
        request.setAttribute("date", DateTimeFormatUtils.getStringFromDate(figures.computedAt(), DATE_FORMAT));
        request.setAttribute("time", DateTimeFormatUtils.getStringFromTime(figures.computedAt()));
        request.setAttribute("shelterPopulation", figures.shelterPopulation());
        request.setAttribute("shelterUsage", figures.shelterUsage());
        request.setAttribute("mortalities", figures.mortalities());
        request.setAttribute("majorMedicalConditions", figures.majorMedicalConditions());
        request.setAttribute("majorMentalIllnesses", figures.majorMentalIllnesses());
        request.setAttribute("seriousMedicalConditions", figures.seriousMedicalConditions());
        request.setAttribute("categoryCodeDescriptions", figures.categoryCodeDescriptions());

        // forward to view page
        return REPORT;
    }

}
