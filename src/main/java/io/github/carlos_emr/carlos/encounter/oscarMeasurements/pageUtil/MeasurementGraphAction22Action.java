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

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.A1cPercent;
import java.io.IOException;
import io.github.carlos_emr.carlos.eform.util.BrowserChartRenderer;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart.Day;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart.Series;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart.SeriesCollection;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart.ReferenceRange;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;

import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import io.github.carlos_emr.carlos.demographic.PatientReadAccess;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PatientRecordAccess;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabTestValues;

/**
 * @author jaygallagher
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MeasurementGraphAction22Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger log = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final String NUMERIC_REGEX = "[^-.\\d]";

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String execute() throws IOException, ParseException {
        log.debug("In MeasurementGraphAction22Action");
        String userrole = (String) request.getSession().getAttribute("userrole");
        if (userrole == null) {
            response.sendRedirect(request.getContextPath() + "/logoutPage");
            return NONE;
        }

        final String patient;
        try {
            patient = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException invalidPatient) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidPatient.getMessage());
            return NONE;
        }
        PatientRecordAccess.require(securityInfoManager, LoggedInInfo.getLoggedInInfoFromSession(request),
                patient, "_measurement", "r");

        Integer demographicNo = Integer.valueOf(patient);
        // Every graph draws this patient's name, age and values (measurements, labs, medications).
        // A module right does not override a patient lock (_demographic$N / _eChart$N).
        PatientReadAccess.requirePatient(securityInfoManager, LoggedInInfo.getLoggedInInfoFromSession(request),
                demographicNo, "_measurement", SecurityInfoManager.READ);
        String typeIdName = request.getParameter("type");
        String typeIdName2 = request.getParameter("type2");

        String patientName = DemographicNameAgeString.getInstance().getNameAgeString(LoggedInInfo.getLoggedInInfoFromSession(request), demographicNo);
        String chartTitle = "Data Graph for " + patientName;
        int width = 1000;
        int height = 600;

        String method = request.getParameter("method");

        log.debug("Creating graph for demo {} type1: {} type2: {}", LogSafe.sanitizeObject(demographicNo), LogSafe.sanitize(typeIdName), LogSafe.sanitize(typeIdName2));
        MeasurementChart chart = null;
        if (method == null) {
            log.debug("Calling DefaultChart");
            chart = defaultChart(demographicNo, typeIdName, typeIdName2, patientName, chartTitle);
        } else if (method.equals("inclRef")) {
            chart = referenceRangeChart(demographicNo, typeIdName, typeIdName2, patientName, chartTitle);

        } else if (method.equals("rxincl")) {
            chart = rxAndLabChart(demographicNo, typeIdName, typeIdName2, patientName, chartTitle);
        } else if (method.equals("lab")) {
            chart = labChart(demographicNo, typeIdName, typeIdName2, patientName, chartTitle);
        } else if (method.equals("labRef")) {
            chart = labChartRef(demographicNo, typeIdName, typeIdName2, patientName, chartTitle);
        } else if (method.equals("actualLab")) {
            String labType = request.getParameter("labType");
            String identifier = request.getParameter("identifier");
            String testName = request.getParameter("testName");
            String drugs[] = request.getParameterValues("drug");
            if (drugs == null) {
                chart = actualLabChartRef(demographicNo, labType, identifier, testName, patientName, chartTitle);
            } else {
                chart = actualLabChartRefPlusMeds(demographicNo, labType, identifier, testName, patientName, chartTitle, drugs);
            }
        } else if (method.equals("ChartMeds")) {
            String drugs[] = request.getParameterValues("drug");
            chart = ChartMeds(demographicNo, patientName, chartTitle, drugs);
            if (drugs != null && drugs.length > 10) {
                height = (drugs.length * 30);
            }
        } else {
            chart = defaultChart(demographicNo, typeIdName, typeIdName2, patientName, chartTitle);
        }


        // Direct image response: render into a buffer first so a renderer failure answers an
        // HTTP error, never image headers followed by an HTML error page (CLAUDE.md, direct responses).
        java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
        try {
            BrowserChartRenderer.write(request.getServletContext(), image, chart, width, height, "png");
        } catch (IOException | IllegalArgumentException unavailable) {
            log.warn("Measurement graph unavailable: {}", unavailable.getMessage());
            if (!response.isCommitted()) {
                response.resetBuffer();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
            return NONE;
        }
        response.setContentType("image/png");
        response.setContentLength(image.size());
        OutputStream o = response.getOutputStream();
        image.writeTo(o);
        o.flush();
        return NONE;
    }

    ArrayList<EctMeasurementsDataBean> getList(Integer demographicNo, String typeIdName) {
        EctMeasurementsDataBeanHandler ectMeasure = new EctMeasurementsDataBeanHandler(demographicNo, typeIdName);
        Collection<EctMeasurementsDataBean> dataVector = ectMeasure.getMeasurementsDataVector();
        ArrayList<EctMeasurementsDataBean> list = new ArrayList<EctMeasurementsDataBean>(dataVector);
        return list;
    }

    static List<Map<String, Object>> getDrugDataSet(Integer demographicId, String[] dins) {
        List<Map<String, Object>> values = new ArrayList<>();
        RxPrescriptionData prescriptData = new RxPrescriptionData();
        if (dins != null) {
            for (String din : dins) {
                if (din == null) continue;
                RxPrescriptionData.Prescription[] prescriptions = prescriptData.getPrescriptionScriptsByPatientRegionalIdentifier(demographicId, din);
                if (prescriptions == null) continue;
                for (RxPrescriptionData.Prescription prescription : prescriptions) {
                    Date start = prescription.getRxDate() != null ? prescription.getRxDate() : prescription.getWrittenDate();
                    if (start == null) throw new IllegalArgumentException("Prescription has no interval start date");
                    Date end = prescription.getEndDate() != null ? prescription.getEndDate() : new Date();
                    values.add(Map.of("name", Objects.toString(prescription.getBrandName(), "Medication"),
                            "start", start.getTime(), "end", end.getTime()));
                }
            }
        }
        return values;
    }

    private MeasurementChart referenceRangeChart(Integer demographicNo, String typeIdName, String typeIdName2, String patientName, String chartTitle) {
        SeriesCollection dataset = new SeriesCollection();

        ArrayList<EctMeasurementsDataBean> list = getList(demographicNo, typeIdName);
        ArrayList<ReferenceRange> dataItems = new ArrayList<ReferenceRange>();

        if (typeIdName.equals("BP")) {
            log.debug("Using BP LOGIC FOR type 1 ");
            EctMeasurementsDataBean sampleLine = list.get(0);
            Series systolic = new Series("Systolic");
            Series diastolic = new Series("Diastolic");
            for (EctMeasurementsDataBean mdb : list) { // dataVector) {
                String[] str = mdb.getDataField().split("/");

                systolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[0]));
                diastolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[1]));
            }
            dataset.addSeries(diastolic);
            dataset.addSeries(systolic);


        } else {
            log.debug("Not Using BP LOGIC FOR type 1 ");
            // get the name from the Series
            EctMeasurementsDataBean sampleLine = list.get(0);
            String typeLegendName = sampleLine.getTypeDisplayName();
            Series newSeries = new Series(typeLegendName);
            for (EctMeasurementsDataBean mdb : list) { //dataVector) {
                String result = (String) mdb.getDataField();
                if (StringUtils.isNotEmpty(result)) {
                    result = result.replaceAll(NUMERIC_REGEX, "");
                    if (NumberUtils.isParsable(result)) {
                        newSeries.addOrUpdate(new Day(mdb.getDateObservedAsDate()),
                                plottedValue(mdb.getType(), result));
                    }
                }
                try {
                    Hashtable h = getMeasurementsExt(mdb.getId());
                    if (h != null && h.containsKey("minimum")) {
                        String min = (String) h.get("minimum");
                        String max = (String) h.get("maximum");
                        double open = Double.parseDouble(min.trim());
                        double high = Double.parseDouble(max.trim());
                        double low = Double.parseDouble(min.trim());
                        double close = Double.parseDouble(max.trim());
                        double volume = 1045;
                        dataItems.add(new ReferenceRange(mdb.getDateObservedAsDate(), low, high));
                    }
                } catch (Exception et) {
                    MiscUtils.getLogger().error("Error", et);
                }

            }
            dataset.addSeries(newSeries);
        }

        // The former chart was a high-low chart built from its "DREFERENCE RANGE" dataset, titled by a
        // leftover demo string; kept as it was, since this change replaces the library, not the chart.
        return MeasurementChart.highLow("HighLowChartDemo2", "Time", "Value", dataset, "DREFERENCE RANGE", dataItems);
    }

    MeasurementChart rxAndLabChart(Integer demographicNo, String typeIdName, String typeIdName2, String patientName, String chartTitle) {
        SeriesCollection dataset = new SeriesCollection();

        ArrayList<EctMeasurementsDataBean> list = getList(demographicNo, typeIdName);

        String typeYAxisName = "";

        if (typeIdName.equals("BP")) {
            log.debug("Using BP LOGIC FOR type 1 ");
            EctMeasurementsDataBean sampleLine = list.get(0);
            typeYAxisName = sampleLine.getTypeDescription();
            Series systolic = new Series("Systolic");
            Series diastolic = new Series("Diastolic");
            for (EctMeasurementsDataBean mdb : list) { // dataVector) {
                String[] str = mdb.getDataField().split("/");

                systolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[0]));
                diastolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[1]));
            }
            dataset.addSeries(diastolic);
            dataset.addSeries(systolic);


        } else {
            log.debug("Not Using BP LOGIC FOR type 1 ");
            // get the name from the Series
            EctMeasurementsDataBean sampleLine = list.get(0);
            String typeLegendName = sampleLine.getTypeDisplayName();
            typeYAxisName = sampleLine.getTypeDescription(); // this should be the type of measurement
            Series newSeries = new Series(typeLegendName);
            for (EctMeasurementsDataBean mdb : list) { //dataVector) {
                String result = (String) mdb.getDataField();
                if (StringUtils.isNotEmpty(result)) {
                    result = result.replaceAll(NUMERIC_REGEX, "");
                    if (NumberUtils.isParsable(result)) {
                        newSeries.addOrUpdate(new Day(mdb.getDateObservedAsDate()),
                                plottedValue(mdb.getType(), result));
                    }
                }
            }
            dataset.addSeries(newSeries);
        }

        // The former chart replaced its title with "MED + LAB CHART" when it combined the panels.
        return MeasurementChart.lines("MED + LAB CHART", "Days", typeYAxisName, dataset)
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.2).withPlotBackground("#ffffff")
                .withShapes().withItemLabels("{1} \n {2}", "0.00", 8)
                .withMedications(getDrugDataSet(demographicNo, request.getParameterValues("drug")));
    }


    MeasurementChart labChart(Integer demographicNo, String typeIdName, String typeIdName2, String patientName, String chartTitle) {
        SeriesCollection dataset = new SeriesCollection();
        ArrayList<EctMeasurementsDataBean> list = getList(demographicNo, typeIdName);
        String typeYAxisName = "";

        if (typeIdName.equals("BP")) {
            log.debug("Using BP LOGIC FOR type 1 ");
            EctMeasurementsDataBean sampleLine = list.get(0);
            typeYAxisName = sampleLine.getTypeDescription();
            Series systolic = new Series("Systolic");
            Series diastolic = new Series("Diastolic");
            for (EctMeasurementsDataBean mdb : list) { // dataVector) {
                String[] str = mdb.getDataField().split("/");

                systolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[0]));
                diastolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[1]));
            }
            dataset.addSeries(diastolic);
            dataset.addSeries(systolic);
        } else {
            log.debug("Not Using BP LOGIC FOR type 1 ");
            // get the name from the Series
            EctMeasurementsDataBean sampleLine = list.get(0);
            String typeLegendName = sampleLine.getTypeDisplayName();
            typeYAxisName = sampleLine.getTypeDescription(); // this should be the type of measurement
            Series newSeries = new Series(typeLegendName);
            for (EctMeasurementsDataBean mdb : list) { //dataVector) {
                String result = (String) mdb.getDataField();
                if (StringUtils.isNotEmpty(result)) {
                    result = result.replaceAll(NUMERIC_REGEX, "");
                    if (NumberUtils.isParsable(result)) {
                        newSeries.addOrUpdate(new Day(mdb.getDateObservedAsDate()),
                                plottedValue(mdb.getType(), result));
                    }
                }
            }
            dataset.addSeries(newSeries);
        }

        return MeasurementChart.lines(chartTitle, "Days", typeYAxisName, dataset)
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.34).withPlotBackground("#ffffff")
                .withShapes().withItemLabels("{1} \n {2}", "0.00", 8);
    }


    MeasurementChart actualLabChartRef(Integer demographicNo, String labType, String identifier, String testName, String patientName, String chartTitle) throws ParseException {
        SeriesCollection dataset = new SeriesCollection();

        ArrayList<Map<String, Serializable>> list = CommonLabTestValues.findValuesForTest(labType, demographicNo, testName, identifier);

        String typeYAxisName = "";
        ArrayList<ReferenceRange> dataItems = new ArrayList<ReferenceRange>();


        String typeLegendName = "Lab Value";
        typeYAxisName = "type Y";

        boolean nameSet = false;
        Series newSeries = new Series(typeLegendName);
        for (Map<String, Serializable> mdb : list) {
            if (!nameSet) {
                typeYAxisName = (String) mdb.get("units");
                typeLegendName = (String) mdb.get("testName");
                newSeries.setKey(typeLegendName);
                nameSet = true;
            }
            String result = (String) mdb.get("result");
            if (StringUtils.isNotEmpty(result)) {
                result = result.replaceAll(NUMERIC_REGEX, "");
                if (NumberUtils.isParsable(result)) {
                    String collectionDate = (String) mdb.get("collDate");
                    if (collectionDate != null) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        Day regularTimePeriod = new Day(simpleDateFormat.parse(collectionDate));
                        newSeries.addOrUpdate(regularTimePeriod, Double.parseDouble(result));
                    }
                }
            }

            if (mdb.get("range") != null) {
                String range = (String) mdb.get("range");
                if (range.indexOf("-") != -1) {
                    String[] sp = range.split("-");
                    String sp0 = sp[0].replaceAll("%", "").trim();
                    String sp1 = sp[1].replaceAll("%", "").trim();
                    double open = Double.parseDouble(sp0);
                    double high = Double.parseDouble(sp1);
                    double low = Double.parseDouble(sp0);
                    double close = Double.parseDouble(sp1);
                    double volume = 1045;
                    String collectionDate = (String) mdb.get("collDate");
                    if (collectionDate != null) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        Day regularTimePeriod = new Day(simpleDateFormat.parse(collectionDate));
                        dataItems.add(new ReferenceRange(regularTimePeriod.getStart(), low, high));
                    }
                }
            }

        }
        dataset.addSeries(newSeries);

        return MeasurementChart.lines(chartTitle, "Days", typeYAxisName, dataset)
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.34).withPlotBackground("#ffffff").withGridlines("#c0c0c0")
                .withShapes().withItemLabels(MeasurementChart.labItemLabels(newSeries.getItemCount()))
                .withReferenceRanges("Normal Reference Range", dataItems);
    }


    MeasurementChart actualLabChartRefPlusMeds(Integer demographicNo, String labType, String identifier, String testName, String patientName, String chartTitle, String[] drugs) throws ParseException {
        SeriesCollection dataset = new SeriesCollection();


        List<Map<String, Serializable>> list = new ArrayList<>();
        boolean loincLab = "loinc".equals(labType);
        MiscUtils.getLogger().debug("lab type >{}< >{}< {} {}",
                LogSafe.sanitize(labType), loincLab, LogSafe.sanitize(testName), LogSafe.sanitize(identifier));
        if (loincLab) {
            try {
                List<Map<String, Serializable>> loincValues =
                        CommonLabTestValues.findValuesByLoinc2(demographicNo.toString(), identifier);
                if (loincValues != null) {
                    list = loincValues;
                }
                MiscUtils.getLogger().debug("List ->{}", list.size());
            } catch (Exception ed) {
                MiscUtils.getLogger().error("Error", ed);
            }
        } else {
            ArrayList<Map<String, Serializable>> testValues =
                    CommonLabTestValues.findValuesForTest(labType, demographicNo, testName, identifier);
            if (testValues != null) {
                list = testValues;
            }
        }
        String typeYAxisName = "";
        ArrayList<ReferenceRange> dataItems = new ArrayList<ReferenceRange>();


        String typeLegendName = "Lab Value";
        typeYAxisName = "type Y";

        boolean nameSet = false;
        Series newSeries = new Series(typeLegendName);
        for (Map mdb : list) {
            if (!nameSet) {
                typeYAxisName = (String) mdb.get("units");
                typeLegendName = (String) mdb.get("testName");
                if (typeLegendName == null) {
                    typeLegendName = testName;
                }

                newSeries.setKey(typeLegendName);
                nameSet = true;
            }
            String result = (String) mdb.get("result");
            if (StringUtils.isNotEmpty(result)) {
                result = result.replaceAll(NUMERIC_REGEX, "");
                if (NumberUtils.isParsable(result)) {
                    String collectionDate = (String) mdb.get("collDate");
                    if (collectionDate != null) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        Day regularTimePeriod = new Day(simpleDateFormat.parse(collectionDate));
                        newSeries.addOrUpdate(regularTimePeriod, Double.parseDouble(result));
                    }
                }
            }


            if (mdb.get("range") != null) {
                String range = (String) mdb.get("range");
                if (range.indexOf("-") != -1) {
                    String[] sp = range.split("-");
                    String sp0 = sp[0].replaceAll("%", "").trim();
                    String sp1 = sp[1].replaceAll("%", "").trim();
                    double open = Double.parseDouble(sp0);
                    double high = Double.parseDouble(sp1);
                    double low = Double.parseDouble(sp0);
                    double close = Double.parseDouble(sp1);
                    double volume = 1045;
                    String collectionDate = (String) mdb.get("collDate");
                    if (collectionDate != null) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        Day regularTimePeriod = new Day(simpleDateFormat.parse(collectionDate));
                        dataItems.add(new ReferenceRange(regularTimePeriod.getStart(), low, high));
                    }
                }
            }

        }
        dataset.addSeries(newSeries);

        return MeasurementChart.lines(chartTitle, "Days", typeYAxisName, dataset)
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.34).withPlotBackground("#ffffff").withGridlines("#c0c0c0")
                .withShapes().withItemLabels(MeasurementChart.labItemLabels(newSeries.getItemCount()))
                .withReferenceRanges("Normal Reference Range", dataItems)
                .withMedications(getDrugDataSet(demographicNo, drugs));
    }


    MeasurementChart labChartRef(Integer demographicNo, String typeIdName, String typeIdName2, String patientName, String chartTitle) {
        SeriesCollection dataset = new SeriesCollection();
        ArrayList<EctMeasurementsDataBean> list = getList(demographicNo, typeIdName);
        String typeYAxisName = "";
        ArrayList<ReferenceRange> dataItems = new ArrayList<ReferenceRange>();
        if (typeIdName.equals("BP")) {
            log.debug("Using BP LOGIC FOR type 1 ");
            EctMeasurementsDataBean sampleLine = list.get(0);
            typeYAxisName = sampleLine.getTypeDescription();
            Series systolic = new Series("Systolic");
            Series diastolic = new Series("Diastolic");
            for (EctMeasurementsDataBean mdb : list) { // dataVector) {
                String[] str = mdb.getDataField().split("/");

                systolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[0]));
                diastolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[1]));
            }
            dataset.addSeries(diastolic);
            dataset.addSeries(systolic);
        } else {
            log.debug("Not Using BP LOGIC FOR type 1 ");
            // get the name from the Series
            EctMeasurementsDataBean sampleLine = list.get(0);
            String typeLegendName = sampleLine.getTypeDisplayName();
            typeYAxisName = sampleLine.getTypeDescription(); // this should be the type of measurement
            Series newSeries = new Series(typeLegendName);
            for (EctMeasurementsDataBean mdb : list) { //dataVector) {
                String result = (String) mdb.getDataField();
                if (StringUtils.isNotEmpty(result)) {
                    result = result.replaceAll(NUMERIC_REGEX, "");
                    if (NumberUtils.isParsable(result)) {
                        newSeries.addOrUpdate(new Day(mdb.getDateObservedAsDate()),
                                plottedValue(mdb.getType(), result));
                    }
                }
                try {
                    Hashtable h = getMeasurementsExt(mdb.getId());
                    if (h != null && h.containsKey("minimum")) {
                        String min = (String) h.get("minimum");
                        String max = (String) h.get("maximum");
                        double open = Double.parseDouble(min.trim());
                        double high = Double.parseDouble(max.trim());
                        double low = Double.parseDouble(min.trim());
                        double close = Double.parseDouble(max.trim());
                        double volume = 1045;
                        dataItems.add(new ReferenceRange(mdb.getDateObservedAsDate(), low, high));
                    }
                } catch (Exception et) {
                    MiscUtils.getLogger().error("Error", et);
                }
            }
            dataset.addSeries(newSeries);
        }


        return MeasurementChart.lines(chartTitle, "Days", typeYAxisName, dataset)
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.34).withPlotBackground("#ffffff")
                .withShapes().withItemLabels("{1} \n {2}", "0.00", 12)
                .withGreenReferenceRanges("Reference Range", dataItems);
    }

    MeasurementChart defaultChart(Integer demographicNo, String typeIdName,
                            String typeIdName2, String patientName,
                            String chartTitle) {
        SeriesCollection dataset = new SeriesCollection();

        ArrayList<EctMeasurementsDataBean> list = getList(demographicNo, typeIdName);
        String typeYAxisName = "";

        if (typeIdName.equals("BP")) {
            log.debug("Using BP LOGIC FOR type 1 ");
            EctMeasurementsDataBean sampleLine = list.get(0);
            typeYAxisName = sampleLine.getTypeDescription();
            Series systolic = new Series("Systolic");
            Series diastolic = new Series("Diastolic");
            for (EctMeasurementsDataBean mdb : list) { // dataVector) {
                if (!mdb.getDataField().equals("")) {
                    String[] str = mdb.getDataField().split("/");

                    systolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[0]));
                    diastolic.addOrUpdate(new Day(mdb.getDateObservedAsDate()), Double.parseDouble(str[1]));
                } else {
                    log.debug("Error passing measurement value to chart. DataField is empty for ID: {}", LogSafe.sanitize(String.valueOf(mdb.getId())));
                }
            }
            dataset.addSeries(diastolic);
            dataset.addSeries(systolic);

        } else {
            log.debug("Not Using BP LOGIC FOR type 1 ");
            // get the name from the Series
            EctMeasurementsDataBean sampleLine = list.get(0);
            String typeLegendName = sampleLine.getTypeDisplayName();
            typeYAxisName = sampleLine.getTypeDescription(); // this should be the type of measurement

            Series newSeries = new Series(typeLegendName);
            for (EctMeasurementsDataBean mdb : list) { //dataVector) {
                String result = (String) mdb.getDataField();
                if (StringUtils.isNotEmpty(result)) {
                    result = result.replaceAll(NUMERIC_REGEX, "");
                    if (NumberUtils.isParsable(result)) {
                        newSeries.addOrUpdate(new Day(mdb.getDateObservedAsDate()),
                                plottedValue(mdb.getType(), result));
                    }
                } else {
                    log.debug("Error passing measurement value to chart. DataField is empty for ID: {}", LogSafe.sanitize(String.valueOf(mdb.getId())));
                }
            }
            dataset.addSeries(newSeries);
        }

        MeasurementChart chart = MeasurementChart.lines(chartTitle, "Days", typeYAxisName, dataset);
        if (typeIdName2 != null) {
            log.debug("type id name 2: {}", LogSafe.sanitize(typeIdName2));

            ArrayList<EctMeasurementsDataBean> list2 = getList(demographicNo, typeIdName2);
            SeriesCollection dataset2 = new SeriesCollection();


            EctMeasurementsDataBean sampleLine2 = list2.get(0);
            String typeLegendName = sampleLine2.getTypeDisplayName();
            String typeYAxisName2 = sampleLine2.getTypeDescription(); // this should be the type of measurement

            Series newSeries = new Series(typeLegendName);
            for (EctMeasurementsDataBean mdb : list2) { //dataVector) {
                String result = (String) mdb.getDataField();
                if (StringUtils.isNotEmpty(result)) {
                    result = result.replaceAll(NUMERIC_REGEX, "");
                    if (NumberUtils.isParsable(result)) {
                        newSeries.addOrUpdate(new Day(mdb.getDateObservedAsDate()),
                                plottedValue(mdb.getType(), result));
                    }
                } else {
                    log.debug("Error passing measurement value to chart. DataField is empty for ID: {}", LogSafe.sanitize(String.valueOf(mdb.getId())));
                }
            }
            dataset2.addSeries(newSeries);

            chart.withSecondAxis(typeYAxisName2, dataset2);
        }
        return chart;
    }


    /*
     * Just Drugs
     */
    MeasurementChart ChartMeds(Integer demographicNo, String patientName, String chartTitle, String[] drugs) {
        MiscUtils.getLogger().debug("In ChartMeds");
        SeriesCollection dataset = new SeriesCollection();
        MeasurementChart chart = MeasurementChart.lines(chartTitle, "Days", "MEDS", dataset);
        chart.withMedications(getDrugDataSet(demographicNo, drugs));
        return chart;
    }


    private Hashtable getMeasurementsExt(Integer measurementId) {
        Hashtable<String, String> hash = new Hashtable<String, String>();
        if (measurementId != null) {
            MeasurementsExtDao dao = SpringUtils.getBean(MeasurementsExtDao.class);
            MeasurementsExt m = dao.find(measurementId);
            if (m != null) {
                hash.put(m.getKeyVal(), m.getVal());
            }
        }
        return hash;
    }

    /**
     * Parses a measurement result for plotting. An A1C reported as a fraction (0.095) is plotted
     * as percent (9.5) so labs that report either form share one axis (issue #4614).
     */
    private static double plottedValue(String measurementType, String result) {
        double value = Double.parseDouble(result);
        return A1cPercent.appliesTo(measurementType) ? A1cPercent.toPercent(value) : value;
    }
}
