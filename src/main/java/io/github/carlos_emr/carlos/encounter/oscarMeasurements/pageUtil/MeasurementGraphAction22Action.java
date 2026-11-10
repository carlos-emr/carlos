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


    private static final Logger log = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static final String WHITE = "#ffffff";
    private static final String VALUE_DATE_LABEL = "{1} \n {2}";
    private static final String SYSTOLIC = "Systolic";
    private static final String DIASTOLIC = "Diastolic";

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
        if ("actualLab".equals(method)) {
            PatientReadAccess.requirePatient(securityInfoManager, LoggedInInfo.getLoggedInInfoFromSession(request),
                    demographicNo, "_lab", SecurityInfoManager.READ);
        }

        log.debug("Creating graph for demo {} type1: {} type2: {}", LogSafe.sanitizeObject(demographicNo), LogSafe.sanitize(typeIdName), LogSafe.sanitize(typeIdName2));
        // Assemble and render before sending image headers. Invalid stored values must produce
        // an explicit error, not a partial chart or an HTML page advertised as an image.
        java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
        try {
            MeasurementChart chart = requestedChart(demographicNo, typeIdName, typeIdName2, chartTitle, method);
            String[] drugs = request.getParameterValues("drug");
            if ("ChartMeds".equals(method) && drugs != null && drugs.length > 10) {
                height = drugs.length * 30;
            }
            BrowserChartRenderer.write(request.getServletContext(), image, chart, width, height, "png");
        } catch (IOException | ParseException | RuntimeException unavailable) {
            // Parse/DAO/driver messages can include clinical values; retain only the failure type.
            log.warn("Measurement graph unavailable: reason={}", io.github.carlos_emr.carlos.eform.util.ChartRenderFailure.code(unavailable));
            if (!response.isCommitted()) {
                response.resetBuffer();
                int status = io.github.carlos_emr.carlos.eform.util.ChartRenderFailure.status(unavailable);
                if (status == 503) response.setHeader("Retry-After", "5");
                response.sendError(status, "Graph unavailable; " + (status == 503 ? "retry shortly." : "review the source history or contact your administrator."));
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

    private MeasurementChart requestedChart(Integer demographicNo, String typeIdName, String typeIdName2,
                                             String chartTitle, String method) throws ParseException {
        if (method == null) {
            return defaultChart(demographicNo, typeIdName, typeIdName2, chartTitle);
        }
        return switch (method) {
            case "inclRef" -> referenceRangeChart(demographicNo, typeIdName);
            case "rxincl" -> rxAndLabChart(demographicNo, typeIdName);
            case "lab" -> labChart(demographicNo, typeIdName, chartTitle);
            case "labRef" -> labChartRef(demographicNo, typeIdName, chartTitle);
            case "actualLab" -> requestedLabChart(demographicNo, chartTitle);
            case "ChartMeds" -> chartMeds(demographicNo, chartTitle, request.getParameterValues("drug"));
            default -> defaultChart(demographicNo, typeIdName, typeIdName2, chartTitle);
        };
    }

    private MeasurementChart requestedLabChart(Integer demographicNo, String chartTitle) throws ParseException {
        String labType = request.getParameter("labType");
        String identifier = request.getParameter("identifier");
        String testName = request.getParameter("testName");
        String[] drugs = request.getParameterValues("drug");
        return drugs == null
                ? actualLabChartRef(demographicNo, labType, identifier, testName, chartTitle)
                : actualLabChartRefPlusMeds(demographicNo, labType, identifier, testName, chartTitle, drugs);
    }

    ArrayList<EctMeasurementsDataBean> getList(Integer demographicNo, String typeIdName) {
        EctMeasurementsDataBeanHandler ectMeasure = new EctMeasurementsDataBeanHandler(demographicNo, typeIdName);
        return new ArrayList<>(ectMeasure.getMeasurementsDataVector());
    }

    static List<Map<String, Object>> getDrugDataSet(Integer demographicId, String[] dins) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (dins == null || dins.length == 0) {
            return values;
        }
        RxPrescriptionData prescriptData = new RxPrescriptionData();
        for (int index = 0; index < dins.length; index++) {
            if (dins[index] != null) {
                addPrescriptionIntervals(values, String.valueOf(index),
                        prescriptData.getPrescriptionScriptsByPatientRegionalIdentifier(demographicId, dins[index]));
            }
        }
        return values;
    }

    /**
     * One medication row per requested DIN, as the former chart's TaskSeries per DIN: the row is named
     * by the DIN's first prescription, so a DIN whose brand text changed stays on one row and two DINs
     * sharing a brand name keep two rows.
     */
    private static void addPrescriptionIntervals(List<Map<String, Object>> values, String row,
                                                 RxPrescriptionData.Prescription[] prescriptions) {
        if (prescriptions == null || prescriptions.length == 0) {
            return;
        }
        String name = Objects.toString(prescriptions[0].getBrandName(), "Medication");
        for (RxPrescriptionData.Prescription prescription : prescriptions) {
            Date start = prescription.getRxDate() != null ? prescription.getRxDate() : prescription.getWrittenDate();
            if (start == null) {
                throw new IllegalArgumentException("Prescription has no interval start date");
            }
            Date end = prescription.getEndDate() != null ? prescription.getEndDate() : new Date();
            values.add(Map.of("row", row, "name", name, "start", start.getTime(), "end", end.getTime()));
        }
    }

    private MeasurementChart referenceRangeChart(Integer demographicNo, String typeIdName) {
        List<EctMeasurementsDataBean> measurements = getList(demographicNo, typeIdName);
        SeriesCollection dataset = measurementSeries(measurements, typeIdName);
        // Preserve the legacy high-low chart's title and dataset order.
        return MeasurementChart.highLow("HighLowChartDemo2", "Time", "Value", dataset,
                "DREFERENCE RANGE", measurementReferenceRanges(measurements, typeIdName));
    }

    private MeasurementChart rxAndLabChart(Integer demographicNo, String typeIdName) {
        List<EctMeasurementsDataBean> measurements = getList(demographicNo, typeIdName);
        return MeasurementChart.lines("MED + LAB CHART", "Days", measurementUnits(measurements),
                        measurementSeries(measurements, typeIdName))
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.2).withPlotBackground(WHITE)
                .withShapes().withItemLabels(VALUE_DATE_LABEL, "0.00", 8)
                .withMedications(getDrugDataSet(demographicNo, request.getParameterValues("drug")));
    }

    private MeasurementChart labChart(Integer demographicNo, String typeIdName, String chartTitle) {
        List<EctMeasurementsDataBean> measurements = getList(demographicNo, typeIdName);
        return MeasurementChart.lines(chartTitle, "Days", measurementUnits(measurements),
                        measurementSeries(measurements, typeIdName))
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.34).withPlotBackground(WHITE)
                .withShapes().withItemLabels(VALUE_DATE_LABEL, "0.00", 8);
    }

    private MeasurementChart labChartRef(Integer demographicNo, String typeIdName, String chartTitle) {
        List<EctMeasurementsDataBean> measurements = getList(demographicNo, typeIdName);
        return MeasurementChart.lines(chartTitle, "Days", measurementUnits(measurements),
                        measurementSeries(measurements, typeIdName))
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.34).withPlotBackground(WHITE)
                .withShapes().withItemLabels(VALUE_DATE_LABEL, "0.00", 12)
                .withGreenReferenceRanges("Reference Range", measurementReferenceRanges(measurements, typeIdName));
    }

    MeasurementChart defaultChart(Integer demographicNo, String typeIdName, String typeIdName2, String chartTitle) {
        List<EctMeasurementsDataBean> measurements = getList(demographicNo, typeIdName);
        MeasurementChart chart = MeasurementChart.lines(chartTitle, "Days", measurementUnits(measurements),
                measurementSeries(measurements, typeIdName));
        if (typeIdName2 != null) {
            List<EctMeasurementsDataBean> secondary = getList(demographicNo, typeIdName2);
            // The legacy secondary axis is a single numeric series, including for a BP request.
            chart.withSecondAxis(measurementUnits(secondary), numericMeasurementSeries(secondary));
        }
        return chart;
    }

    private static String measurementUnits(List<EctMeasurementsDataBean> measurements) {
        return measurements.isEmpty() ? "" : measurements.getFirst().getTypeDescription();
    }

    private static SeriesCollection measurementSeries(List<EctMeasurementsDataBean> measurements, String typeIdName) {
        if (!"BP".equals(typeIdName)) {
            return numericMeasurementSeries(measurements);
        }
        Series systolic = new Series(SYSTOLIC);
        Series diastolic = new Series(DIASTOLIC);
        for (EctMeasurementsDataBean measurement : measurements) {
            if (StringUtils.isNotEmpty(measurement.getDataField())) {
                String[] values = measurement.getDataField().split("/");
                if (values.length != 2) {
                    throw new IllegalArgumentException("Invalid blood pressure measurement");
                }
                Day day = new Day(measurement.getDateObservedAsDate().toInstant());
                systolic.addOrUpdate(day, Double.parseDouble(values[0]));
                diastolic.addOrUpdate(day, Double.parseDouble(values[1]));
            }
        }
        SeriesCollection dataset = new SeriesCollection();
        dataset.addSeries(diastolic);
        dataset.addSeries(systolic);
        return dataset;
    }

    private static SeriesCollection numericMeasurementSeries(List<EctMeasurementsDataBean> measurements) {
        SeriesCollection dataset = new SeriesCollection();
        if (measurements.isEmpty()) {
            return dataset;
        }
        Series series = new Series(measurements.getFirst().getTypeDisplayName());
        for (EctMeasurementsDataBean measurement : measurements) {
            String result = numericResult(measurement.getDataField());
            if (result != null) {
                series.addOrUpdate(new Day(measurement.getDateObservedAsDate().toInstant()), plottedValue(measurement.getType(), result));
            }
        }
        dataset.addSeries(series);
        return dataset;
    }

    /**
     * Accepts a complete finite signed ASCII decimal without extracting numbers from clinical qualifiers or text.
     * @param result source measurement literal
     * @return the trimmed decimal, or null if it cannot safely represent a plotted numeric value
     */
    static String numericResult(String result) {
        if (StringUtils.isEmpty(result)) {
            return null;
        }
        String numeric = result.trim();
        int digits = 0;
        boolean decimal = false;
        int start = !numeric.isEmpty() && (numeric.charAt(0) == '+' || numeric.charAt(0) == '-') ? 1 : 0;
        for (int i = start; i < numeric.length(); i++) {
            char character = numeric.charAt(i);
            if (character >= '0' && character <= '9') {
                digits++;
            } else if (character == '.' && !decimal) {
                decimal = true;
            } else {
                return null;
            }
        }
        if (digits == 0) return null;
        try {
            return Double.isFinite(Double.parseDouble(numeric)) ? numeric : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private List<ReferenceRange> measurementReferenceRanges(List<EctMeasurementsDataBean> measurements, String typeIdName) {
        List<ReferenceRange> ranges = new ArrayList<>();
        if ("BP".equals(typeIdName)) {
            return ranges;
        }
        for (EctMeasurementsDataBean measurement : measurements) {
            Map<String, String> extensions = getMeasurementsExt(measurement.getId());
            Double minimum = storedBound(extensions.get("minimum"));
            Double maximum = storedBound(extensions.get("maximum"));
            if (minimum != null && maximum != null) {
                ranges.add(new ReferenceRange(measurement.getDateObservedAsDate().toInstant(), minimum, maximum));
            }
        }
        return ranges;
    }

    /**
     * A stored reference bound, or null when it is absent or not a number. These charts drew no ranges
     * before (the former lookup never found the bounds), so a malformed bound leaves that range out
     * rather than failing the whole image.
     */
    private static Double storedBound(String value) {
        if (value == null || !NumberUtils.isParsable(value.trim())) {
            if (value != null) log.warn("Measurement reference bound is not numeric; the range is not drawn");
            return null;
        }
        return Double.parseDouble(value.trim());
    }

    private MeasurementChart actualLabChartRef(Integer demographicNo, String labType, String identifier,
                                               String testName, String chartTitle) throws ParseException {
        List<Map<String, Serializable>> values = CommonLabTestValues.findValuesForTest(labType, demographicNo, testName, identifier);
        return actualLabChart(values, null, chartTitle, 10);
    }

    private MeasurementChart actualLabChartRefPlusMeds(Integer demographicNo, String labType, String identifier,
                                                       String testName, String chartTitle, String[] drugs) throws ParseException {
        List<Map<String, Serializable>> values = List.of();
        if ("loinc".equals(labType)) {
            // As before, a failed LOINC lookup leaves the lab panel empty above the medications.
            try {
                List<Map<String, Serializable>> loinc = CommonLabTestValues.findValuesByLoinc2(demographicNo.toString(), identifier);
                if (loinc != null) values = loinc;
            } catch (RuntimeException lookup) {
                log.warn("LOINC lab values unavailable for the medication chart ({})", lookup.getClass().getSimpleName());
            }
        } else {
            List<Map<String, Serializable>> found = CommonLabTestValues.findValuesForTest(labType, demographicNo, testName, identifier);
            if (found != null) values = found;
        }
        return actualLabChart(values, testName, chartTitle, 6)
                .withMedications(getDrugDataSet(demographicNo, drugs));
    }

    /** {@code labelBand}: the former lab-only chart chose item labels by tens of points, the lab-plus-medication chart by sixes. */
    private static MeasurementChart actualLabChart(List<Map<String, Serializable>> values, String fallbackName,
                                                   String chartTitle, int labelBand) throws ParseException {
        String units = "type Y";
        String name = "Lab Value";
        if (!values.isEmpty()) {
            units = (String) values.getFirst().get("units");
            name = (String) values.getFirst().get("testName");
            if (name == null) {
                name = fallbackName;
            }
        }
        Series series = new Series(name);
        List<ReferenceRange> ranges = new ArrayList<>();
        for (Map<String, Serializable> value : values) {
            addLabValue(series, value);
            addLabReferenceRange(ranges, value);
        }
        SeriesCollection dataset = new SeriesCollection();
        dataset.addSeries(series);
        return MeasurementChart.lines(chartTitle, "Days", units, dataset)
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.34).withPlotBackground(WHITE).withGridlines("#c0c0c0")
                .withShapes().withItemLabels(MeasurementChart.labItemLabels(series.getItemCount(), labelBand))
                .withReferenceRanges("Normal Reference Range", ranges);
    }

    private static void addLabValue(Series series, Map<String, Serializable> value) throws ParseException {
        String result = numericResult((String) value.get("result"));
        Day collectionDate = collectionDay(value);
        if (result != null && collectionDate != null) {
            series.addOrUpdate(collectionDate, Double.parseDouble(result));
        }
    }

    private static void addLabReferenceRange(List<ReferenceRange> ranges, Map<String, Serializable> value) throws ParseException {
        String range = (String) value.get("range");
        Day collectionDate = collectionDay(value);
        if (range == null || !range.contains("-") || collectionDate == null) {
            return;
        }
        String[] bounds = range.split("-");
        if (bounds.length != 2) {
            throw new IllegalArgumentException("Invalid laboratory reference range");
        }
        double low = Double.parseDouble(bounds[0].replace("%", "").trim());
        double high = Double.parseDouble(bounds[1].replace("%", "").trim());
        ranges.add(new ReferenceRange(collectionDate.getStart(), low, high));
    }

    private static Day collectionDay(Map<String, Serializable> value) throws ParseException {
        // CML and HL7 already supply a normalized Date; LOINC supplies its timestamp as text.
        // Prefer the typed date because the provider's display date may use a different format.
        Serializable normalized = value.get("collDateDate");
        if (normalized instanceof Date observed) {
            return new Day(observed.toInstant());
        }
        String collectionDate = (String) value.get("collDate");
        if (collectionDate == null && normalized instanceof String observed) {
            // LOINC values carry the observed timestamp as text, "null" when the column was empty.
            if ("null".equals(observed)) return null;
            collectionDate = observed;
        }
        if (collectionDate == null) return null;
        try {
            // CDS timestamps can carry explicit offsets and nanosecond fractions.
            // Use their instant, just as for a normalized Date supplied by a handler.
            return new Day(java.time.OffsetDateTime.parse(collectionDate.replace(' ', 'T'),
                    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
        } catch (java.time.format.DateTimeParseException ignored) {
            // Offset-free producer formats below still require a strict, full-input match.
        }
        for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH", "yyyy-MM-dd"}) {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            format.setLenient(false);
            java.text.ParsePosition position = new java.text.ParsePosition(0);
            Date date = format.parse(collectionDate, position);
            if (date != null && position.getIndex() == collectionDate.length()) return new Day(date.toInstant());
        }
        throw new ParseException("Unsupported observation date", 0);
    }

    private MeasurementChart chartMeds(Integer demographicNo, String chartTitle, String[] drugs) {
        return MeasurementChart.lines(chartTitle, "Days", "MEDS", new SeriesCollection())
                .withMedications(getDrugDataSet(demographicNo, drugs));
    }

    private Map<String, String> getMeasurementsExt(Integer measurementId) {
        Map<String, String> values = new HashMap<>();
        if (measurementId != null) {
            MeasurementsExtDao dao = SpringUtils.getBean(MeasurementsExtDao.class);
            for (MeasurementsExt extension : dao.getMeasurementsExtByMeasurementId(measurementId)) {
                values.put(extension.getKeyVal(), extension.getVal());
            }
        }
        return values;
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
