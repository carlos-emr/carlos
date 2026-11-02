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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;
import io.github.carlos_emr.carlos.eform.util.BrowserChartRenderer;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart.Series;
import java.util.Date;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PatientRecordAccess;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Renders scatter plot and line chart images (JPEG) for clinical measurements
 * such as blood pressure and vitals in the encounter view.
 *
 * <p>Chart.js renders through the confined browser service; callers still receive JPEG images.
 */
public class ScatterPlotChartServlet extends HttpServlet {

    protected int width = 550;
    protected int height = 360;

    @Override
    public void service(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        String type = request.getParameter("type");
        String mInstrc = request.getParameter("mInstrc");
        String demographicNo;
        try {
            demographicNo = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException invalidPatient) {
            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidPatient.getMessage());
            return;
        }
        SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);
        try {
            PatientRecordAccess.require(security, LoggedInInfo.getLoggedInInfoFromSession(request),
                    demographicNo, "_measurement", "r");
        } catch (SecurityException deniedRecord) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        try {
            MeasurementChart chart;
            if ("BP".equals(type)) {
                chart = createBloodPressureChart(demographicNo, type, mInstrc);
            } else {
                chart = createScatterPlotChart(demographicNo, type, mInstrc);
            }

            if (chart != null) {
                java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
                BrowserChartRenderer.write(request.getServletContext(), image, chart, width, height, "jpeg");
                httpServletResponse.setContentType("image/jpeg");
                image.writeTo(httpServletResponse.getOutputStream());
            }
        } catch (Exception t) {
            MiscUtils.getLogger().error("Error", t);
            if (!httpServletResponse.isCommitted()) {
                httpServletResponse.reset();
                httpServletResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
        }
    }

    private MeasurementChart createScatterPlotChart(String demo, String type, String mInstrc) {
        long[][] results = generateResult(demo, type, mInstrc);
        if (results == null || results[0].length == 0) {
            return null;
        }

        String chartTitle = type + "-" + mInstrc;
        Series series = new Series(chartTitle);

        long baseDay = results[0][0];
        for (int x = 0; x < results[0].length; x++) {
            series.add(results[0][x] - baseDay, results[1][x]);
        }

        MeasurementChart chart = MeasurementChart.scatter(chartTitle,
                "Day (note: only the last data on the same observation date is plotted)",
                "Test Results", List.of(series), false);

        return chart;
    }

    private MeasurementChart createBloodPressureChart(String demo, String type, String mInstrc) {
        long[][] results = generateResult(demo, type, mInstrc);
        if (results == null) {
            return null;
        }

        String chartTitle = type + "-" + mInstrc;
        int halfLen = results[1].length / 2;

        Series systolicSeries = new Series("Systolic");
        Series diastolicSeries = new Series("Diastolic");

        for (int x = 0; x < halfLen; x++) {
            int testNum = x + 1;
            systolicSeries.add(testNum, results[1][x]);
            diastolicSeries.add(testNum, results[1][x + halfLen]);
        }

        MeasurementChart chart = MeasurementChart.scatter(chartTitle,
                "Tests (note: only the last data on the same observation date is plotted)",
                "Hgmm", List.of(systolicSeries, diastolicSeries), true);

        return chart;
    }

    private long[][] generateResult(String demo, String type, String mInstrc) {
        long[][] points = null;

        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        if (isNumeric(type, mInstrc)) {
            List<Object> dates = dao.findObservationDatesByDemographicNoTypeAndMeasuringInstruction(ConversionUtils.fromIntString(demo), type, mInstrc);
            int nbData = dates.size();
            points = new long[2][nbData];
            for (int i = 0; i < nbData; i++) {
                Measurement m = dao.findByDemographicNoTypeAndDate(ConversionUtils.fromIntString(demo), type, (java.util.Date) dates.get(i));

                if (m != null) {
                    java.util.Date dateObserved = m.getDateObserved();
                    points[0][i] = dateObserved.getTime() / 1000 / 60 / 60 / 24;
                    points[1][i] = ConversionUtils.fromLongString(m.getDataField());
                    MiscUtils.getLogger().debug("Date: " + points[0][i] + " Value: " + points[1][i]);
                }
            }
        } else if ("BP".equals(type)) {
            List<Date> measurements = dao.findByDemographicNoTypeAndMeasuringInstruction(ConversionUtils.fromIntString(demo), type, mInstrc);
            int nbPatient = measurements.size();
            points = new long[2][nbPatient * 2];
            for (int i = 0; i < nbPatient; i++) {
                Measurement mm = dao.findByDemographicNoTypeAndDate(ConversionUtils.fromIntString(demo), type, measurements.get(i));
                if (mm != null) {
                    String bloodPressure = mm.getDataField();
                    MiscUtils.getLogger().debug("bloodPressure: " + bloodPressure);
                    int slashIndex = bloodPressure.indexOf("/");
                    if (slashIndex >= 0) {
                        String systolic = bloodPressure.substring(0, slashIndex);
                        java.util.Date dateObserved = mm.getDateObserved();
                        points[0][i] = dateObserved.getTime() / 1000 / 60 / 60 / 24;
                        points[1][i] = Long.parseLong(systolic);
                        MiscUtils.getLogger().debug("systolic: " + i + " " + systolic);

                        String diastolic = bloodPressure.substring(slashIndex + 1);
                        points[0][i + nbPatient] = dateObserved.getTime() / 1000 / 60 / 60 / 24;
                        points[1][i + nbPatient] = Long.parseLong(diastolic);
                        MiscUtils.getLogger().debug("diastolic: " + points[1][i + nbPatient]);
                    }
                }
            }

            MiscUtils.getLogger().debug("Store blood pressure data to a new array successfully");
        }

        return points;
    }

    private boolean isNumeric(String type, String mInstrc) {
        boolean result = false;
        MeasurementTypeDao dao = SpringUtils.getBean(MeasurementTypeDao.class);
        List<MeasurementType> measurementTypes = dao.findByTypeAndMeasuringInstruction(type, mInstrc);

        if (!measurementTypes.isEmpty()) {
            String validation = measurementTypes.get(0).getValidation();

            ValidationsDao valDao = SpringUtils.getBean(ValidationsDao.class);
            Validations v = valDao.find(Integer.parseInt(validation));
            if (v != null && v.isNumeric() != null && v.isNumeric()) {
                result = true;
            }
        }

        return result;
    }
}
