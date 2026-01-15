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

package ca.openosp.openo.commn.web;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.time.DateUtils;
import ca.openosp.openo.PMmodule.utility.DateTimeFormatUtils;
import ca.openosp.openo.commn.model.Mortalities;
import ca.openosp.openo.commn.model.ReportStatistic;
import ca.openosp.openo.commn.model.ShelterPopulation;
import ca.openosp.openo.commn.model.ShelterUsage;
import ca.openosp.openo.commn.service.PopulationReportManager;
import ca.openosp.openo.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class PopulationReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

    // Forwards
    private static final String REPORT = "report";

    private PopulationReportManager populationReportManager = SpringUtils.getBean(PopulationReportManager.class);

    private static long lastDataRetrievedTime = 0;
    private static Date currentDateTime = null;
    private static ShelterPopulation shelterPopulation = null;
    private static ShelterUsage shelterUsage = null;
    private static Mortalities mortalities = null;
    private static Map<String, ReportStatistic> majorMedicalConditions = null;
    private static Map<String, ReportStatistic> majorMentalIllnesses = null;
    private static Map<String, ReportStatistic> seriousMedicalConditions = null;
    private static Map<String, Map<String, String>> categoryCodeDescriptions = null;

    @Override
    public String execute() {
        return report();
    }

    public String report() {

        // simple caching mechanism (because this report is open to the public and we don't want the public smacking our server around)
        if (System.currentTimeMillis() - lastDataRetrievedTime > DateUtils.MILLIS_PER_HOUR) {
            lastDataRetrievedTime = System.currentTimeMillis();

            // get attributes
            currentDateTime = Calendar.getInstance().getTime();
            shelterPopulation = populationReportManager.getShelterPopulation();
            shelterUsage = populationReportManager.getShelterUsage();
            mortalities = populationReportManager.getMortalities();
            majorMedicalConditions = populationReportManager.getMajorMedicalConditions();
            majorMentalIllnesses = populationReportManager.getMajorMentalIllnesses();
            seriousMedicalConditions = populationReportManager.getSeriousMedicalConditions();
            categoryCodeDescriptions = populationReportManager.getCategoryCodeDescriptions();
        }

        // set attributes
        request.setAttribute("date", DateTimeFormatUtils.getStringFromDate(currentDateTime, DATE_FORMAT));
        request.setAttribute("time", DateTimeFormatUtils.getStringFromTime(currentDateTime));
        request.setAttribute("shelterPopulation", shelterPopulation);
        request.setAttribute("shelterUsage", shelterUsage);
        request.setAttribute("mortalities", mortalities);
        request.setAttribute("majorMedicalConditions", majorMedicalConditions);
        request.setAttribute("majorMentalIllnesses", majorMentalIllnesses);
        request.setAttribute("seriousMedicalConditions", seriousMedicalConditions);
        request.setAttribute("categoryCodeDescriptions", categoryCodeDescriptions);

        // forward to view page
        return REPORT;
    }

}
