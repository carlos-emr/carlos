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

package io.github.carlos_emr.carlos.lab.ca.all;

import io.github.carlos_emr.carlos.util.NativeQueryValues;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.ObxResultStatus;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

public class Hl7textResultsData {

    /** Property: file final word results the result filter does not match (default on; see {@link #fileTextResults()}). */
    static final String FILE_TEXT_RESULTS_PROPERTY = "HL7_LAB_FILE_TEXT_RESULTS";

    private static Logger logger = MiscUtils.getLogger();
    private static ConsultDocsDao consultDocsDao = SpringUtils.getBean(ConsultDocsDao.class);
    private static ConsultResponseDocDao consultResponseDocDao = SpringUtils.getBean(ConsultResponseDocDao.class);
    private static Hl7TextInfoDao hl7TxtInfoDao = SpringUtils.getBean(Hl7TextInfoDao.class);
    private static PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
    private static EFormDocsDao eformDocsDao = SpringUtils.getBean(EFormDocsDao.class);

    private Hl7textResultsData() {
        // no one should instantiate this
    }

    // The reads that decide what a report files look their DAO up here, when they run, and not in the
    // static fields above: the report's own row, the chain reader (getMatchingLabs and its CLS twin,
    // which the display code shares: same bean, same behaviour), the stored source of a CML version
    // and the measurement mapping. A static field is bound when this class loads, which in a test JVM
    // may be in another class's mocked or different Spring context, and a DAO of another context cannot
    // see the row the current transaction has just written (#6391: "Lab report is not stored" in an
    // unrelated test class). The display and list readers further down keep their static fields.
    private static Hl7TextInfoDao currentInfoDao() {
        return SpringUtils.getBean(Hl7TextInfoDao.class);
    }

    private static Hl7TextMessageDao currentMessageDao() {
        return SpringUtils.getBean(Hl7TextMessageDao.class);
    }

    private static MeasurementMapDao currentMeasurementMapDao() {
        return SpringUtils.getBean(MeasurementMapDao.class);
    }

    /**
     * Parses a handler-formatted observation time, accepting a missing seconds, minutes or time
     * component so a result observed in the past is never re-dated to the moment it was filed
     * (issue #4619). Each layout must match the whole value and be a real calendar time, so a short
     * layout can never silently swallow the date prefix of a longer or malformed value.
     *
     * @param observationDate the handler's formatted timestamp, may be null
     * @return the parsed instant, or {@code null} when no supported layout matches
     */
    public static Date parseObservationDate(String observationDate) {
        return io.github.carlos_emr.carlos.lab.ca.all.util.ObservationTime.parse(observationDate);
    }

    /**
     * Files a report's results as the patient's measurements when the report is the current version
     * of its accession, and archives the measurements of every other version, so the chart holds
     * exactly one version's values.
     *
     * <p>Which version is current depends on what the source tells us (see
     * {@link #currentVersion(String[], boolean)}). A report that is not current is a late delivery of
     * history: nothing is archived and nothing is filed for it.</p>
     *
     * <p>The filing is {@link LabMeasurementFilingService}'s (#6391): one transaction, serialized per
     * lab chain by row locks, that archives every version's values (this report's own included) and
     * writes one complete set. Called inside a transaction, as every upload entry does, it files
     * after that transaction commits, in one of its own; a failure then is retried, logged and
     * audited, and does not undo the upload. Called with no transaction it files at once and
     * throws.</p>
     *
     * @param lab_no the report to file: at upload, the report just received
     * @param demographic_no the patient the report was matched to
     * @throws IllegalStateException if the report cannot be read or stored; with no transaction
     *                               around the call also if it is missing from its own chain.
     *                               Nothing has been archived when it is thrown
     */
    public static void populateMeasurementsTable(String lab_no, String demographic_no) {
        String labNo = lab_no.trim();
        MessageHandler h = Factory.getHandler(labNo);
        if (h == null) {
            // Refuse before the previous version's measurements are archived: with nothing
            // readable to file in their place, the chart would be left with neither.
            throw new IllegalStateException("Lab report could not be read; its measurements were not filed");
        }
        // The report's own row names the accession and filler order number that lock its chain.
        Hl7TextInfo own = currentInfoDao().findLabId(Integer.parseInt(labNo));
        if (own == null) {
            throw new IllegalStateException("Lab report is not stored; its measurements were not filed");
        }
        // Parsing and building the rows touch no measurement tables, so they happen before any lock.
        // The chain check ("absent from its chain", "not the current version"), the archive of the
        // replaced versions' values and the filing itself run in LabMeasurementFilingService, under
        // the chain's row locks (#6391), after the upload that called this has committed.
        LabMeasurementFilingDto filing = new LabMeasurementFilingDto(
                labNo,
                patientNumber(demographic_no),
                h instanceof io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler,
                own.getAccessionNumber(),
                own.getFillerOrderNum(),
                buildFiledMeasurements(h, labNo, demographic_no));
        // Looked up here, in the method, never held in a static field: a static field would resolve at
        // class load, before the Spring context exists in tests and with more than one context.
        SpringUtils.getBean(LabMeasurementFilingService.class).fileAfterCommit(filing);
    }

    /**
     * The versions of a CLS report that belong to this patient (see {@link #getMatchingLabs(String, Integer)}).
     * A CLS chain is not ranked, its current version being the highest lab number, so another patient's
     * versions can be left out after it is resolved. A version routed to a different patient is left out; a
     * version with no routing (not yet matched), and the incoming report itself, stay. The chain's order is kept.
     */
    static String[] ownVersions(String[] chain, String incoming, Integer patient) {
        List<String> own = new ArrayList<>();
        for (String version : chain) {
            String trimmed = version.trim();
            if (!trimmed.equals(incoming.trim()) && routedToAnotherPatient(trimmed, patient)) {
                continue;
            }
            own.add(version);
        }
        return own.toArray(new String[0]);
    }

    private static boolean routedToAnotherPatient(String labNo, Integer patient) {
        int number;
        try {
            number = Integer.parseInt(labNo);
        } catch (NumberFormatException notANumber) {
            return false;
        }
        for (PatientLabRouting routing : patientLabRoutingDao.findByLabNoAndLabType(number, "HL7")) {
            Integer routed = routing.getDemographicNo();
            if (routed != null && routed > 0 && !routed.equals(patient)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Files the current version of a report that has no measurements yet, whichever of its versions
     * the caller names. A patient match made after upload uses this: the version to file is decided
     * here, by the same rule as at upload, not by the caller's position in the display order.
     *
     * @param lab_no any version of the report
     * @param demographic_no the patient the report was matched to
     * @throws IllegalArgumentException if the patient identifier is not a positive integer
     * @throws IllegalStateException if the report cannot be read; nothing has been archived
     */
    public static void populateCurrentVersionMeasurements(String lab_no, String demographic_no) {
        MessageHandler named = Factory.getHandler(lab_no);
        if (named == null) {
            throw new IllegalStateException("Lab report could not be read; its measurements were not filed");
        }
        Integer patient = ConversionUtils.fromIntString(demographic_no);
        String[] matchingLabs = getMatchingLabs(lab_no, patient).split(",");
        populateMeasurementsTable(
                currentVersion(matchingLabs, named instanceof io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler),
                demographic_no);
    }

    /**
     * The version of a report whose results are the patient's measurements.
     *
     * <p>A CML chain is ordered by the lab's own revision evidence ({@code CmlVersionPolicy}), so
     * its last version is current even when an older version was delivered after it (#5820).</p>
     *
     * <p>No other source gives CARLOS revision evidence. Their chain is ordered for display by the
     * count of final results, the report date and the lab number, and that order must not decide
     * which version is current: a correction commonly reports fewer "final" results than the
     * report it corrects (a corrected result has status C, which most handlers do not count), so
     * it sorts before the original, and treating it as history left the uncorrected values on
     * flowsheets, graphs and decision support. For these sources the current version is the one
     * received last, which is the one with the highest lab number: lab numbers are assigned in the
     * order reports arrive, and the newest delivery for an accession is the lab's latest word on
     * it. This is the rule CARLOS applied to every source before the CML protection was added,
     * with one difference: every other version is superseded, so two versions' values never stay
     * on the chart together.</p>
     *
     * @param chain the report's versions as {@link #getMatchingLabs(String, Integer)} resolved them
     * @param revisionOrdered true when the chain's order is the source's revision order (CML)
     * @return the lab number of the current version
     */
    static String currentVersion(String[] chain, boolean revisionOrdered) {
        if (revisionOrdered) {
            return chain[chain.length - 1];
        }
        String newest = chain[0];
        for (String version : chain) {
            if (Integer.parseInt(version.trim()) > Integer.parseInt(newest.trim())) {
                newest = version;
            }
        }
        return newest;
    }

    /**
     * Builds every viewable result of one report as a measurement to file, with its extension
     * values. Reads the report and the measurement map only; nothing is stored here.
     */
    static List<FiledMeasurementDto> buildFiledMeasurements(MessageHandler h, String lab_no, String demographic_no) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calender = Calendar.getInstance();
        String day = Integer.toString(calender.get(Calendar.DAY_OF_MONTH));
        String month = Integer.toString(calender.get(Calendar.MONTH) + 1);
        String year = Integer.toString(calender.get(Calendar.YEAR));
        String hour = Integer.toString(calender.get(Calendar.HOUR_OF_DAY));
        String min = Integer.toString(calender.get(Calendar.MINUTE));
        String second = Integer.toString(calender.get(Calendar.SECOND));
        String dateEntered = year + "-" + month + "-" + day + " " + hour + ":" + min + ":" + second + ":";

        /*
         * find a regex filter in properties -or- set default to find everything
         * This OSCAR property determines what is allowed to be written to the measurements
         * table.  No more entire PDF documents written into measurements.
         */

        /*
         * Setting in OSCAR properties that toggles if a code
         * should be mapped to a measurement by Name AND identifier.
         * Excelleris uses only LOINC identifiers for the most part.
         */
        boolean isSearchName = Boolean.parseBoolean(CarlosProperties.getInstance().getProperty("MAP_BY_IDENTIFIER_AND_NAME", "false"));

        List<FiledMeasurementDto> filed = new ArrayList<>();
        for (int i = 0; i < h.getOBRCount(); i++) {
            for (int j = 0; j < h.getOBXCount(i); j++) {
                String result = h.getOBXResult(i, j);

                if (result == null) {
                    continue;
                }

                // only add if there is a result and it is supposed to be viewed
                if ("".equals(result) || "DNR".equals(result) || "".equals(h.getOBXName(i, j)) || "DNS".equals(h.getOBXResultStatus(i, j))) {
                    continue;
                }

                logger.debug("obx(" + j + ") should be added");
                String identifier = h.getOBXIdentifier(i, j);
                String name = h.getOBXName(i, j);
                String unit = h.getOBXUnits(i, j);
                String labname = h.getPatientLocation();
                String accession = h.getAccessionNum();
                String req_datetime = h.getRequestDate(i);
                String observationDate = h.getTimeStamp(i, j);

                // temporary for Excelleris labs. This method be added to the MessageHandler interface if
                // it is useful for other HL7 labs
                if (h instanceof PATHL7Handler) {
                    observationDate = ((PATHL7Handler) h).getObservationDate(i);
                }

                String olis_status = h.getOBXResultStatus(i, j);
                String abnormal = h.getOBXAbnormalFlag(i, j);
                if (abnormal != null && (abnormal.equals("A") || abnormal.startsWith("H"))) {
                    abnormal = "A";
                } else if (abnormal != null && abnormal.startsWith("L")) {
                    abnormal = "L";
                } else {
                    abnormal = "N";
                }
                String[] refRange = splitRefRange(h.getOBXReferenceRange(i, j));
                String blocked = h.isTestResultBlocked(i, j) ? "BLOCKED" : null;
                String comments = "";
                for (int l = 0; l < h.getOBXCommentCount(i, j); l++) {
                    comments += comments.length() > 0 ? "\n" + h.getOBXComment(i, j, l) : h.getOBXComment(i, j, l);
                }

                String measType = "";
                String measInst = "";

                identifier = StringUtils.trimToEmpty(identifier);
                name = StringUtils.trimToEmpty(name);

                if (!identifier.isEmpty()) {

                    String searchName = "%";

                    if (isSearchName) {
                        searchName = name;
                    }

                    List<Object[]> measurements = currentMeasurementMapDao().findMeasurements("FLOWSHEET", identifier, searchName);

                    if (measurements.isEmpty()) {
                        logger.debug("CODE:" + identifier + " needs to be mapped");
                    } else {
                        for (Object[] o : measurements) {
                            MeasurementMap mm = (MeasurementMap) o[1];
                            MeasurementType type = (MeasurementType) o[2];

                            measType = mm.getIdentCode();
                            measInst = type.getMeasuringInstruction();
                        }
                    }
                }


                String value = projectableValue(result, lab_no, i, j);
                // A final or corrected result the filter does not match is filed whole, as text (#6122). One the
                // filter matches but cannot file (too long, or splitting a character) stays unfiled (#6142 review).
                boolean textResult = false;
                if (value == null && fileTextResults() && filterFindsNothing(result)) {
                    value = textResultValue(result, olis_status, "lab " + LogSafe.sanitize(lab_no) + ", OBX " + i + "-" + j);
                    textResult = value != null;
                }
                if (value != null) {
                    Measurement m = new Measurement();
                    m.setType(measType);
                    m.setDemographicId(patient);
                    m.setProviderNo("0");
                    m.setDataField(value);
                    m.setMeasuringInstruction(measInst);
                    try {
                        m.setCreateDate(simpleDateFormat.parse(dateEntered));
                    } catch (Exception e) {
                        m.setCreateDate(new Date());
                    }

                    /*
                     * This should be the OBR date - not the date the lab or measurement was received.
                     */
                    Date observed = parseObservationDate(observationDate);
                    if (observed == null) {
                        logger.warn("Unparseable HL7 observation time for lab " + LogSafe.sanitize(lab_no)
                                + "; filing the measurement as observed now");
                        observed = new Date();
                    }
                    m.setDateObserved(observed);

                    m.setAppointmentNo(0);

                    /*
                     * Remove HTML
                     */
                    m.setComments(measurementComment(comments));

                    // The extension values in the order they are written. "blocked" is in the same list
                    // as the rest, so one failure rolls back all of them (it used to be persisted alone).
                    java.util.Map<String, String> extensions = new java.util.LinkedHashMap<>();
                    extensions.put("lab_no", lab_no);
                    extensions.put("abnormal", abnormal);
                    extensions.put("identifier", identifier);
                    extensions.put("name", name);
                    extensions.put("labname", labname);
                    extensions.put("accession", accession);
                    extensions.put("request_datetime", req_datetime);
                    extensions.put("datetime", observationDate);
                    if (olis_status != null && olis_status.length() > 0) {
                        extensions.put("olis_status", olis_status);
                    }
                    if (unit != null && unit.length() > 0) {
                        extensions.put("unit", unit);
                    }
                    if (refRange[0].length() > 0) {
                        extensions.put("range", refRange[0]);
                    } else {
                        if (refRange[1].length() > 0) {
                            extensions.put("minimum", refRange[1]);
                        }
                        if (refRange[2].length() > 0) {
                            extensions.put("maximum", refRange[2]);
                        }
                    }
                    if (blocked != null) {
                        extensions.put("blocked", blocked);
                    }
                    extensions.put("other_id", i + "-" + j);
                    // A final or corrected word result filed whole is marked as text (#6122), so the chart
                    // and the graphs treat it as a word and not a number.
                    if (textResult) {
                        extensions.put(
                                io.github.carlos_emr.carlos.commn.model.MeasurementsExt.VALUE_TYPE_KEY,
                                io.github.carlos_emr.carlos.commn.model.MeasurementsExt.TEXT_VALUE_TYPE);
                    }
                    filed.add(new FiledMeasurementDto(m, extensions));
                }
            }
        }
        return filed;
    }

    /**
     * A result's joined comment as a measurement stores it: handler line-break markers removed and
     * cut to the column width, which the packaged database would otherwise do silently and a
     * strict one would refuse. The full comment remains in the report.
     */
    private static String measurementComment(String joinedComments) {
        String comment = joinedComments.replaceAll("\\<br\\s?/?\\>", "");
        return comment.codePointCount(0, comment.length()) <= Measurement.COMMENTS_MAX_LENGTH ? comment
                : comment.substring(0, comment.offsetByCodePoints(0, Measurement.COMMENTS_MAX_LENGTH));
    }

    /**
     * The value a result is filed as: the text {@code HL7_LAB_MEASUREMENT_FILTER} matches, in the
     * lab's own case. {@code null} when the filter does not match or the match is longer than a
     * measurement holds; the full result stays in the report.
     */
    private static String projectableValue(String result, String lab, int obr, int obx) {
        return projectableValue(result, "lab " + LogSafe.sanitize(lab) + ", OBX " + obr + "-" + obx);
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Locale.ROOT shadow matches the clinic's result filter; not an identity or authorization decision")
    // Locale.ROOT folding is only for the clinic's result filter, never identity or authorization.
    private static String projectableValue(String result, String context) {
        if (result == null || result.isEmpty() || "DNR".equals(result)) return null;
        String trimmed = filterInput(result);
        Locale locale = Locale.ROOT;
        String shadow = trimmed.toLowerCase(locale);
        Matcher match = measurementFilter(shadow);
        if (!match.find()) return null;
        int start = originalBoundary(trimmed, shadow, match.start(), locale);
        int end = originalBoundary(trimmed, shadow, match.end(), locale);
        if (start < 0 || end < 0) {
            if (context != null) logger.warn("Result filter split a Unicode case mapping; not filed as a measurement ({})", context);
            return null;
        }
        String value = trimmed.substring(start, end);
        int characters = value.codePointCount(0, value.length());
        if (characters > Measurement.DATA_FIELD_MAX_LENGTH) {
            if (context != null) logger.warn("A result of {} characters matched HL7_LAB_MEASUREMENT_FILTER but is longer than a measurement holds; original remains in the source report ({})", characters, context);
            return null;
        }
        return value;
    }

    /** A result as {@code HL7_LAB_MEASUREMENT_FILTER} reads it: trimmed, without its line-break markers. */
    private static String filterInput(String result) {
        return StringUtils.trimToEmpty(result).replaceAll("\\<br\\s?/?\\>", "").trim();
    }

    private static Matcher measurementFilter(String shadow) {
        return Pattern.compile(CarlosProperties.getInstance().getProperty("HL7_LAB_MEASUREMENT_FILTER", ".*")).matcher(shadow);
    }

    /**
     * Whether {@code HL7_LAB_MEASUREMENT_FILTER} finds nothing in the result, read as {@code projectableValue}
     * reads it. Only such a result may be filed as text (#6122). A result the filter does match but that cannot
     * be filed (longer than a measurement holds, or a match that would split a character) stays unfiled, as it
     * was before (#6142 review).
     */
    static boolean filterFindsNothing(String result) {
        return !measurementFilter(filterInput(result).toLowerCase(Locale.ROOT)).find();
    }

    /**
     * The operator's switch for filing final word results the filter does not match (#6122): the property
     * {@value #FILE_TEXT_RESULTS_PROPERTY}. On unless it is set to {@code false}, {@code no} or {@code off}, so an
     * install that never sets it, or sets something else, files them. Off keeps exactly the filing from before
     * #6122: only what {@code HL7_LAB_MEASUREMENT_FILTER} matches becomes a measurement.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an operator's on/off word in the properties file; not a security or authorization decision. See docs/static-analysis-workflows.md
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Case-insensitive comparison of an operator's on/off word in the properties file; not a security or authorization decision")
    static boolean fileTextResults() {
        String configured = CarlosProperties.getInstance().getProperty(FILE_TEXT_RESULTS_PROPERTY, "true");
        if (configured == null) {
            return true;
        }
        String value = configured.trim();
        return !(value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no") || value.equalsIgnoreCase("off"));
    }

    /**
     * The value a final or corrected result is filed as when {@code HL7_LAB_MEASUREMENT_FILTER} does
     * not match it (#6122): the lab's own words, whole, each line-break marker ({@code <br />}, in any
     * case) read as one space.
     * Such a result ({@code Pending}, {@code Not detected}, {@code Resistant}) was left unfiled
     * before, so the cumulative grid and the value history showed nothing for it. Final means what
     * {@link ObxResultStatus#isFinal} says, in the handler's own words ({@code F}, {@code Final},
     * {@code Corrected}, ...). A result with any other status (preliminary included) stays unfiled,
     * as does an empty one and one longer than a measurement holds; the full result always stays
     * in the report.
     *
     * @param result the OBX result as the handler reads it
     * @param status the OBX result status as the handler reports it
     * @param context where the result is, for the log; never the result itself. May be {@code null}
     * @return the text to file, marked {@link MeasurementsExt#TEXT_VALUE_TYPE}, or {@code null}
     */
    static String textResultValue(String result, String status, String context) {
        if (result == null || !ObxResultStatus.isFinal(status)) return null;
        // Handlers mark line breaks as <br />; a lab may send its own in capitals.
        // Start at the marker: a leading \\s* makes a failed search rescan every whitespace suffix.
        Matcher breaks = Pattern.compile("(?i)<br\\s?/?>\\s*").matcher(result);
        StringBuilder normalized = new StringBuilder();
        int start = 0;
        while (breaks.find()) {
            int end = breaks.start();
            while (end > start) {
                char c = result.charAt(end - 1);
                // Preserve the regex's default ASCII whitespace semantics.
                if (c != ' ' && (c < '\t' || c > '\r')) break;
                end--;
            }
            normalized.append(result, start, end).append(' ');
            start = breaks.end();
        }
        String text = normalized.append(result, start, result.length()).toString().trim();
        if (text.isEmpty() || "DNR".equals(text)) return null;
        int characters = text.codePointCount(0, text.length());
        if (characters > Measurement.DATA_FIELD_MAX_LENGTH) {
            if (context != null) logger.warn("A final text result of {} characters is longer than a measurement holds; original remains in the source report ({})", characters, context);
            return null;
        }
        return text;
    }

    /** Map a lowercase-shadow boundary without cutting a source character in half. */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Maps the Locale.ROOT filter shadow back onto original result text; not an identity or authorization decision")
    // This fold maps the Locale.ROOT filter shadow to original text, never identity or authorization.
    private static int originalBoundary(String source, String shadow, int boundary, Locale locale) {
        if (boundary == 0) return 0;
        if (boundary == shadow.length()) return source.length();
        if (source.length() == shadow.length()) return boundary;
        int original = 0, folded = 0;
        while (original < source.length()) {
            int next = original + Character.charCount(source.codePointAt(original));
            folded += source.substring(original, next).toLowerCase(locale).length();
            if (folded == boundary) return next;
            if (folded > boundary) return -1;
            original = next;
        }
        return -1;
    }

    public static String getMatchingLabs_CLS(String lab_no) {
        String ret = "";
        Hl7TextInfo self = null;
        List<Integer> idList = new ArrayList<Integer>();

        for (Object[] o : currentInfoDao().findByLabIdViaMagic(ConversionUtils.fromIntString(lab_no))) {
            Hl7TextInfo a = (Hl7TextInfo) o[0];
            int labNo = a.getLabNumber();
            if (lab_no.equals(String.valueOf(labNo))) {
                self = a;

            }
            ret = ret + "," + labNo;
            idList.add(labNo);
        }

        //nothing but itself was found, but we have a special case for glucose tolerance tests
        //they come in with different accessions but same filler order no.
        if (self != null && ret.length() > 0 && ret.substring(1).indexOf(",") == -1) {
            ret = "";
            for (Hl7TextInfo info : currentInfoDao().findByFillerOrderNumber(self.getFillerOrderNum())) {
                ret = ret + "," + info.getLabNumber();
                idList.add(info.getLabNumber());
            }
        }

        if (idList.isEmpty()) {
            idList.add(Integer.parseInt(lab_no));
        }

        Collections.sort(idList);

        StringBuilder sb = new StringBuilder();
        for (Integer id : idList) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(String.valueOf(id));
        }

        return sb.toString();
    }

    /** Missing CML source evidence prevents chain-dependent writes, but not viewing the selected report. */
    public static final class IncompleteCmlVersionChainException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private IncompleteCmlVersionChainException(String message) {
            super(message);
        }
    }

    /** A display fallback never certifies the complete chain for a mutation. */
    public record DisplayLabVersions(String labIds, boolean reliable) { }

    /**
     * Retains the selected report when a related CML source is unavailable.
     * Callers must explain the incomplete history and disable mutation controls.
     * Database failures and invalid source identifiers still propagate.
     */
    public static DisplayLabVersions getMatchingLabsForDisplay(String labNo) {
        try {
            return new DisplayLabVersions(getMatchingLabs(labNo), true);
        } catch (IncompleteCmlVersionChainException incomplete) {
            logger.warn("CML version chain is incomplete; only the selected report may be displayed read-only");
            return new DisplayLabVersions(labNo, false);
        }
    }

    public static String getMatchingLabs(String lab_no) {
        return getMatchingLabs(lab_no, null);
    }

    /**
     * The versions of a report, as {@link #getMatchingLabs(String)} resolves them, that can be the given
     * patient's: the chain a report is filed by.
     *
     * <p>The chain is found by accession number alone, so a recycled or colliding accession can put another
     * patient's report in it. A version routed to a different patient is left out as it is found, before any
     * version is read or ranked (#6142 review). Ranked with this patient's CML revisions, one such report
     * without a revision time sent the whole chain back to arrival order, so a stale revision delivered late
     * could replace newer measurements; one that cannot be read would refuse this patient's filing. A version
     * with no routing yet, which may be this patient's own newer delivery waiting for its match, and
     * {@code lab_no} itself stay.</p>
     *
     * @param lab_no any version of the report
     * @param patient the patient the report is filed for; {@code null} keeps every version (the display chain)
     * @return the chain's lab numbers, comma-separated, in the order {@link #getMatchingLabs(String)} gives them
     */
    static String getMatchingLabs(String lab_no, Integer patient) {
        String ret = "";
        int monthsBetween = 0;

        Hl7TextMessage hl7Msg = currentMessageDao().find(Integer.parseInt(lab_no));
        if (hl7Msg != null && "CLS".equals(hl7Msg.getType())) {
            String chain = getMatchingLabs_CLS(lab_no);
            return patient == null ? chain : String.join(",", ownVersions(chain.split(","), lab_no, patient));
        }

        boolean cml = hl7Msg != null && "CML".equals(hl7Msg.getType());
        java.util.Map<Integer, io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler> cmlVersions = new java.util.LinkedHashMap<>();
        java.util.Set<Integer> seenVersions = new java.util.HashSet<>();

        for (Object[] o : currentInfoDao().findByLabIdViaMagic(ConversionUtils.fromIntString(lab_no))) {
            Hl7TextInfo a = (Hl7TextInfo) o[0];
            Hl7TextInfo b = (Hl7TextInfo) o[1];

            int labNo = a.getLabNumber();


            //Accession numbers may be recycled, accession
            //numbers for a lab should have lab dates within less than 4
            //months of each other even this is a large time span
            Date dateA = ConversionUtils.fromTimestampString(a.getObrDate());
            Date dateB = ConversionUtils.fromTimestampString(b.getObrDate());
            if (dateA == null || dateB == null) continue;

            if (dateA.before(dateB)) {
                monthsBetween = UtilDateUtilities.getNumMonths(dateA, dateB);
            } else {
                monthsBetween = UtilDateUtilities.getNumMonths(dateB, dateA);
            }

            logger.debug("monthsBetween: " + monthsBetween);
            logger.debug("lab_no: " + labNo + " lab: " + lab_no);

            if (monthsBetween < 4) {
                if (patient != null && labNo != incoming && routedToAnotherPatient(String.valueOf(labNo), patient)) {
                    continue;
                }
                if (cml) {
                    if (!seenVersions.add(labNo)) continue;
                    Hl7TextMessage version = labNo == Integer.parseInt(lab_no)
                            ? hl7Msg : currentMessageDao().find(labNo);
                    if (version == null) throw new IncompleteCmlVersionChainException("A CML version has no stored source message");
                    if ("CML".equals(version.getType())) {
                        MessageHandler parser = Factory.getHandler(version);
                        if (!(parser instanceof io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler cmlHandler)) {
                            throw new IncompleteCmlVersionChainException("A stored CML version cannot be parsed");
                        }
                        // Order by the lab's revision evidence, never stale persisted completeness.
                        cmlVersions.put(labNo, cmlHandler);
                    }
                } else {
                    if (ret.equals("")) ret = "" + labNo;
                    else ret = ret + "," + labNo;
                }
            }
        }

        if (cml) {
            ret = io.github.carlos_emr.carlos.lab.service.CmlVersionPolicy.order(cmlVersions).stream()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        }
        if (ret.equals("")) return (lab_no);
        else return (ret);
    }

    /**
     * Populates ArrayList with labs attached to a consultation request
     */
    // Populates labs to consult request
    public static ArrayList<LabResultData> populateHL7ResultsData(String demographicNo, String consultationId, boolean attached) {
        List<LabResultData> attachedLabs = new ArrayList<LabResultData>();
        for (Object[] o : consultDocsDao.findLabs(ConversionUtils.fromIntString(consultationId))) {
            if (!"HL7".equals(((PatientLabRouting) o[1]).getLabType())) continue;
            ConsultDocs c = (ConsultDocs) o[0];
            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labPatientId = ConversionUtils.toIntString(c.getDocumentNo());
            attachedLabs.add(lbData);
        }
        List<Object[]> labsHl7 = hl7TxtInfoDao.findByDemographicId(ConversionUtils.fromIntString(demographicNo));
        return populateHL7ResultsData(attachedLabs, labsHl7, attached);
    }

    public static ArrayList<LabResultData> populateHL7ResultsDataEForm(String demographicNo, String fdid, boolean attached) {
        List<LabResultData> attachedLabs = new ArrayList<LabResultData>();
        for (Object[] o : eformDocsDao.findLabs(ConversionUtils.fromIntString(fdid))) {
            if (!"HL7".equals(((PatientLabRouting) o[1]).getLabType())) continue;
            EFormDocs c = (EFormDocs) o[0];
            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labPatientId = ConversionUtils.toIntString(c.getDocumentNo());
            attachedLabs.add(lbData);
        }
        List<Object[]> labsHl7 = hl7TxtInfoDao.findByDemographicId(ConversionUtils.fromIntString(demographicNo));
        return populateHL7ResultsData(attachedLabs, labsHl7, attached);
    }

    // Populates labs to consult response
    public static ArrayList<LabResultData> populateHL7ResultsDataConsultResponse(String demographicNo, String consultationId, boolean attached) {
        List<LabResultData> attachedLabs = new ArrayList<LabResultData>();
        for (Object[] o : consultResponseDocDao.findLabs(ConversionUtils.fromIntString(consultationId))) {
            if (!"HL7".equals(((PatientLabRouting) o[1]).getLabType())) continue;
            ConsultResponseDoc c = (ConsultResponseDoc) o[0];
            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labPatientId = ConversionUtils.toIntString(c.getDocumentNo());
            attachedLabs.add(lbData);
        }
        List<Object[]> labsHl7 = hl7TxtInfoDao.findByDemographicId(ConversionUtils.fromIntString(demographicNo));
        return populateHL7ResultsData(attachedLabs, labsHl7, attached);
    }

    // Populates labs private shared method
    private static ArrayList<LabResultData> populateHL7ResultsData(List<LabResultData> attachedLabs, List<Object[]> labsHl7, boolean attached) {
        ArrayList<LabResultData> labResults = new ArrayList<LabResultData>();

        LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
        LabResultData.CompareId c = lbData.getComparatorId();
        for (Object[] o : labsHl7) {
            Hl7TextInfo i = (Hl7TextInfo) o[0];
            PatientLabRouting p = (PatientLabRouting) o[1];

            lbData.segmentID = ConversionUtils.toIntString(i.getLabNumber());
            lbData.labPatientId = ConversionUtils.toIntString(p.getLabNo());
            lbData.dateTime = i.getObrDate();
            lbData.discipline = i.getDiscipline();
            lbData.accessionNumber = i.getAccessionNumber();
            lbData.finalResultsCount = i.getFinalResultCount();
            lbData.label = i.getLabel();

            if (attached && Collections.binarySearch(attachedLabs, lbData, c) >= 0) labResults.add(lbData);
            else if (!attached && Collections.binarySearch(attachedLabs, lbData, c) < 0) labResults.add(lbData);

            lbData = new LabResultData(LabResultData.HL7TEXT);
        }

        return labResults;
    }

    /**
     * End Populates labs attached to consultation
     */


    public static ArrayList<LabResultData> getNotAckLabsFromLabNos(List<String> labNos) {
        ArrayList<LabResultData> ret = new ArrayList<LabResultData>();
        LabResultData lrd = new LabResultData();
        for (String labNo : labNos) {
            lrd = getNotAckLabResultDataFromLabNo(labNo);
            ret.add(lrd);
        }
        return ret;
    }

    public static LabResultData getNotAckLabResultDataFromLabNo(String labNo) {
        LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
        // note to self: lab reports not found in the providerLabRouting table will not show up - need to ensure every lab is entered in providerLabRouting, with '0'
        // for the providers number if unable to find correct providers

        List<Hl7TextInfo> infos = hl7TxtInfoDao.findByLabId(ConversionUtils.fromIntString(labNo));
        if (infos.isEmpty()) return lbData;

        Hl7TextInfo info = infos.get(0);

        lbData.labType = LabResultData.HL7TEXT;
        lbData.segmentID = "" + info.getLabNumber();
        //check if any demographic is linked to this lab
        if (lbData.isMatchedToPatient()) {
            //get matched demographic no
            List<PatientLabRouting> rs = patientLabRoutingDao.findByLabNoAndLabType(Integer.parseInt(lbData.segmentID), lbData.labType);
            if (!rs.isEmpty()) {
                lbData.setLabPatientId("" + rs.get(0).getDemographicNo());
            } else {
                lbData.setLabPatientId("-1");
            }
        } else {
            lbData.setLabPatientId("-1");
        }
        lbData.acknowledgedStatus = "U";
        lbData.accessionNumber = info.getAccessionNumber();
        lbData.healthNumber = info.getHealthNumber();
        lbData.patientName = info.getLastName() + ", " + info.getFirstName();
        lbData.sex = info.getSex();

        lbData.resultStatus = info.getResultStatus();
        if (lbData.resultStatus != null && lbData.resultStatus.equals("A")) {
            lbData.abn = true;
        }

        lbData.dateTime = info.getObrDate();

        //priority
        String priority = info.getPriority();

        if (priority != null && !priority.equals("")) {
            switch (priority.charAt(0)) {
                case 'C':
                    lbData.priority = "Critical";
                    break;
                case 'S':
                    lbData.priority = "Stat/Urgent";
                    break;
                case 'U':
                    lbData.priority = "Unclaimed";
                    break;
                case 'A':
                    lbData.priority = "ASAP";
                    break;
                case 'L':
                    lbData.priority = "Alert";
                    break;
                default:
                    lbData.priority = "Routine";
                    break;
            }
        } else {
            lbData.priority = "----";
        }

        lbData.requestingClient = info.getRequestingProvider();
        lbData.reportStatus = info.getReportStatus();

        // the "C" is for corrected excelleris labs
        if (lbData.reportStatus != null && (lbData.reportStatus.equals("F") || lbData.reportStatus.equals("C"))) {
            lbData.finalRes = true;
        } else {
            lbData.finalRes = false;
        }

        lbData.discipline = info.getDiscipline();
        lbData.finalResultsCount = info.getFinalResultCount();

        return lbData;
    }

    public static ArrayList<LabResultData> populateHl7ResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status, Integer labNo) {

        if (providerNo == null) {
            providerNo = "";
        }
        if (patientFirstName == null) {
            patientFirstName = "";
        }
        if (patientLastName == null) {
            patientLastName = "";
        }
        if (status == null) {
            status = "";
        }

        patientHealthNumber = StringUtils.trimToNull(patientHealthNumber);

        ArrayList<LabResultData> labResults = new ArrayList<LabResultData>();

        List<Object[]> routings = null;

        if (labNo != null && labNo.intValue() > 0) {
            routings = new ArrayList<Object[]>();
            for (Hl7TextInfo info : hl7TxtInfoDao.findByLabId(labNo)) {
                routings.add(new Object[]{info});
            }
        } else if (demographicNo == null) {
            // note to self: lab reports not found in the providerLabRouting table will not show up -
            // need to ensure every lab is entered in providerLabRouting, with '0'
            // for the providers number if unable to find correct providers
            routings = hl7TxtInfoDao.findLabsViaMagic(status, providerNo, patientFirstName, patientLastName, patientHealthNumber);
        } else {
            routings = hl7TxtInfoDao.findByDemographicId(ConversionUtils.fromIntString(demographicNo));
        }

        for (Object[] o : routings) {
            Hl7TextInfo hl7 = (Hl7TextInfo) o[0];
            //PatientLabRouting p = (PatientLabRouting) o[1];

            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labType = LabResultData.HL7TEXT;
            lbData.segmentID = "" + hl7.getLabNumber();

            //if the demographic number is null find a matching demo
            if (demographicNo == null) {
                //check if any demographic is linked to this lab
                if (lbData.isMatchedToPatient()) {
                    //get matched demographic no
                    List<PatientLabRouting> lst = patientLabRoutingDao.findByLabNoAndLabType(Integer.parseInt(lbData.segmentID), lbData.labType);

                    if (!lst.isEmpty()) {
                        lbData.setLabPatientId("" + lst.get(0).getDemographicNo());
                    } else {
                        lbData.setLabPatientId("-1");
                    }
                } else {
                    lbData.setLabPatientId("-1");
                }
            } else {
                lbData.setLabPatientId(demographicNo);
            }

            if (o.length == 1) {
                lbData.acknowledgedStatus = "U";
            } else {
                if (demographicNo == null && !providerNo.equals("0")) {
                    lbData.acknowledgedStatus = hl7.getResultStatus();
                } else {
                    lbData.acknowledgedStatus = "U";
                }
            }

            lbData.accessionNumber = hl7.getAccessionNumber();
            lbData.healthNumber = hl7.getHealthNumber();
            lbData.patientName = hl7.getLastName() + ", " + hl7.getFirstName();
            lbData.sex = hl7.getSex();
            lbData.label = hl7.getLabel();

            lbData.resultStatus = hl7.getResultStatus();
            if (lbData.resultStatus != null && lbData.resultStatus.equals("A")) {
                lbData.abn = true;
            }

            lbData.dateTime = hl7.getObrDate();

            //priority
            String priority = hl7.getPriority();

            if (priority != null && !priority.equals("")) {
                switch (priority.charAt(0)) {
                    case 'C':
                        lbData.priority = "Critical";
                        break;
                    case 'S':
                        lbData.priority = "Stat/Urgent";
                        break;
                    case 'U':
                        lbData.priority = "Unclaimed";
                        break;
                    case 'A':
                        lbData.priority = "ASAP";
                        break;
                    case 'L':
                        lbData.priority = "Alert";
                        break;
                    default:
                        lbData.priority = "Routine";
                        break;
                }
            } else {
                lbData.priority = "----";
            }

            lbData.requestingClient = hl7.getRequestingProvider();
            lbData.reportStatus = hl7.getReportStatus();

            // the "C" is for corrected excelleris labs
            if (lbData.reportStatus != null && (lbData.reportStatus.equals("F") || lbData.reportStatus.equals("C"))) {
                lbData.finalRes = true;
            } else {
                lbData.finalRes = false;
            }

            lbData.discipline = hl7.getDiscipline();
            lbData.finalResultsCount = hl7.getFinalResultCount();
            labResults.add(lbData);
        }

        return labResults;
    }

    public static ArrayList<LabResultData> populateHl7ResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber,
                                                                  String status, boolean isPaged, Integer page, Integer pageSize, boolean mixLabsAndDocs, Boolean isAbnormal) {
        return populateHl7ResultsData(providerNo, demographicNo, patientFirstName, patientLastName, patientHealthNumber, status, isPaged, page, pageSize, mixLabsAndDocs, isAbnormal, null, null);
    }

    public static ArrayList<LabResultData> populateHl7ResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status, boolean isPaged, Integer page, Integer pageSize, boolean mixLabsAndDocs, Boolean isAbnormal, Date startDate, Date endDate) {
        return new ArrayList<>(populateHl7ResultsData(providerNo, demographicNo, patientFirstName, patientLastName, patientHealthNumber, status, isPaged, page, pageSize, mixLabsAndDocs, isAbnormal, startDate, endDate, null));
    }

    /**
     * As above, additionally keeping only labs whose routing row changed at or after
     * {@code acknowledgedSince}; see {@link Hl7TextInfoDao#findLabAndDocsViaMagic}.
     *
     * @param acknowledgedSince inclusive lower bound on the routing row's timestamp, or null for none
     */
    public static List<LabResultData> populateHl7ResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status, boolean isPaged, Integer page, Integer pageSize, boolean mixLabsAndDocs, Boolean isAbnormal, Date startDate, Date endDate, Date acknowledgedSince) {

        if (providerNo == null) {
            providerNo = "";
        }
        boolean searchProvider = !"-1".equals(providerNo) && !"".equals(providerNo);
        if (patientFirstName == null) {
            patientFirstName = "";
        }
        if (patientLastName == null) {
            patientLastName = "";
        }
        if (patientHealthNumber == null) {
            patientHealthNumber = "";
        }
        if (status == null || "U".equals(status)) {
            status = "";
        }

        boolean patientSearch = !"".equals(patientFirstName) || !"".equals(patientLastName) || !"".equals(patientHealthNumber);

        ArrayList<LabResultData> labResults = new ArrayList<LabResultData>();
        // note to self: lab reports not found in the providerLabRouting table will not show up - need to ensure every lab is entered in providerLabRouting, with '0'
        // for the providers number if unable to find correct providers

        for (Object[] i : hl7TxtInfoDao.findLabAndDocsViaMagic(providerNo, demographicNo, patientFirstName, patientLastName, patientHealthNumber, status, isPaged, page, pageSize, mixLabsAndDocs, isAbnormal, searchProvider, patientSearch, startDate, endDate, acknowledgedSince)) {

            String label = String.valueOf(i[0]);
            String lab_no = String.valueOf(i[1]);
            String sex = String.valueOf(i[2]);
            String health_no = String.valueOf(i[3]);
            String result_status = String.valueOf(i[4]);
            String observationDate = String.valueOf(NativeQueryValues.asString(i[5]));
            String priority = String.valueOf(i[6]);
            String requesting_client = String.valueOf(i[7]);
            String discipline = String.valueOf(i[8]);
            String last_name = String.valueOf(i[9]);
            String first_name = String.valueOf(i[10]);
            String report_status = String.valueOf(i[11]);
            String accessionNum = String.valueOf(i[12]);
            String final_result_count = String.valueOf(i[13]);
            String routingStatus = String.valueOf(i[14]);

            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labType = LabResultData.HL7TEXT;
            lbData.segmentID = lab_no;

            if (demographicNo == null && !providerNo.equals("0")) {
                lbData.acknowledgedStatus = routingStatus;
            } else {
                lbData.acknowledgedStatus = "U";
            }

            lbData.accessionNumber = accessionNum;
            if (health_no != null && health_no.equals("null")) {
                health_no = "";
            }
            lbData.healthNumber = StringUtils.trimToEmpty(health_no);
            lbData.patientName = last_name + ", " + first_name;
            lbData.sex = sex;
            lbData.label = label;

            lbData.resultStatus = result_status;
            if (lbData.resultStatus != null && lbData.resultStatus.equals("A")) {
                lbData.abn = true;
            }

            lbData.dateTime = observationDate;

            if (priority != null && !priority.equals("")) {
                switch (priority.charAt(0)) {
                    case 'C':
                        lbData.priority = "Critical";
                        break;
                    case 'S':
                        lbData.priority = "Stat/Urgent";
                        break;
                    case 'U':
                        lbData.priority = "Unclaimed";
                        break;
                    case 'A':
                        lbData.priority = "ASAP";
                        break;
                    case 'L':
                        lbData.priority = "Alert";
                        break;
                    default:
                        lbData.priority = "Routine";
                        break;
                }
            } else {
                lbData.priority = "----";
            }

            lbData.requestingClient = requesting_client;
            lbData.reportStatus = report_status;

            // the "C" is for corrected excelleris labs
            if (lbData.reportStatus != null && (lbData.reportStatus.equals("F") || lbData.reportStatus.equals("C"))) {
                lbData.finalRes = true;
            } else if (lbData.reportStatus != null && lbData.reportStatus.equals("X")) {
                lbData.cancelledReport = true;
            } else {
                lbData.finalRes = false;
            }

            lbData.discipline = discipline;
            lbData.finalResultsCount = ConversionUtils.fromIntString(final_result_count);
            labResults.add(lbData);
        }

        return labResults;
    }

    private static String[] splitRefRange(String refRangeTxt) {
        refRangeTxt = refRangeTxt.trim();
        String[] refRange = {"", "", ""};
        String numeric = "-. 0123456789";
        boolean textual = false;
        if (refRangeTxt == null || refRangeTxt.length() == 0) return refRange;

        for (int i = 0; i < refRangeTxt.length(); i++) {
            if (!numeric.contains(refRangeTxt.subSequence(i, i + 1))) {
                if (i > 0 || (refRangeTxt.charAt(i) != '>' && refRangeTxt.charAt(i) != '<')) {
                    textual = true;
                    break;
                }
            }
        }
        if (textual) {
            refRange[0] = refRangeTxt;
        } else {
            if (refRangeTxt.charAt(0) == '>') {
                refRange[1] = refRangeTxt.substring(1).trim();
            } else if (refRangeTxt.charAt(0) == '<') {
                refRange[2] = refRangeTxt.substring(1).trim();
            } else {
                String[] tmp = refRangeTxt.split("-");
                if (tmp.length == 2) {
                    refRange[1] = tmp[0].trim();
                    refRange[2] = tmp[1].trim();
                } else {
                    refRange[0] = refRangeTxt;
                }
            }
        }
        return refRange;
    }
}
