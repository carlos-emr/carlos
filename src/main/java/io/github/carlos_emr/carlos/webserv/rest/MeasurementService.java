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
 * Modifications made by CARLOS Contributors in 2026: enforce CDS maintenance and disclosure compatibility.
 */
package io.github.carlos_emr.carlos.webserv.rest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctValidation;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots;
import io.github.carlos_emr.carlos.webserv.rest.to.MeasurementResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EmbeddedMeasurementTo1;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

/**
 * RESTful web service for measurement data operations in CARLOS EMR.
 * This service provides authenticated access to measurement data through the UI
 * session or OAuth for external integrations, mobile applications, and API consumers.
 * 
 * <p>The service follows REST conventions and provides JSON-based endpoints for:</p>
 * <ul>
 *   <li><strong>Data retrieval</strong>: Getting measurements by type, patient, and date ranges</li>
 *   <li><strong>Data recording</strong>: Adding a new measurement without changing existing clinical history</li>
 *   <li><strong>Security enforcement</strong>: Session/OAuth authentication and patient-scoped authorization</li>
 *   <li><strong>Data transformation</strong>: Converting internal models to API-friendly formats</li>
 *   <li><strong>Type filtering</strong>: Selective measurement retrieval by clinical type</li>
 * </ul>
 * 
 * <p>Security model:</p>
 * <ul>
 *   <li>All endpoints require an authenticated UI session or OAuth 1.0a authentication</li>
 *   <li>Users must have "_measurement" read privileges</li>
 *   <li>New measurements require "_measurement" write privileges and patient-record access</li>
 *   <li>Patient demographic access is validated per request</li>
 *   <li>Audit logging tracks all API access</li>
 * </ul>
 * 
 * <p>Data formats:</p>
 * <ul>
 *   <li>Input: JSON with measurement type arrays for reads, or measurement data for writes</li>
 *   <li>Output: {@link MeasurementResponse} objects with measurement collections for reads, or detached {@link EmbeddedMeasurementTo1} snapshots for writes</li>
 *   <li>All responses include metadata and status information</li>
 * </ul>
 * 
 * <p>Example API usage:</p>
 * <pre>{@code
 * POST /ws/rs/measurements/{demographicNo}
 * Content-Type: application/json
 * 
 * {
 *   "types": ["BP", "WT", "HT"]
 * }
 *
 * POST /ws/services/measurements/{demographicNo}/new
 * Content-Type: application/json
 *
 * {
 *   "type": "BP",
 *   "dataField": "122/80",
 *   "measuringInstruction": "sitting position",
 *   "comments": "Seated clinic reading",
 *   "dateObserved": 1770000000000
 * }
 * }</pre>
 * 
 * @since 2006
 * @see Measurement
 * @see MeasurementResponse
 * @see EmbeddedMeasurementTo1
 * @see MeasurementManager
 * @see AbstractServiceImpl
 */
@Path("/measurements")
@Component("measurementService")
@Consumes(MediaType.APPLICATION_JSON)
public class MeasurementService extends AbstractServiceImpl {
    /** Security manager for measurement and patient-record authorization. */
    private SecurityInfoManager securityInfoManager;
    /** Business logic manager for measurement operations. */
    private MeasurementManager measurementManager;
    private DemographicDao demographicDao;
    private MeasurementTypeDao measurementTypeDao;
    private ValidationsDao validationsDao;
    private CdsLegacyProjectionService projections;
    private final EctValidation ectValidation = new EctValidation();

    private static final long MIN_DATETIME_MILLIS = -30610224000000L;
    private static final long MAX_DATETIME_MILLIS = 253402300799000L;
    /**
     * How far ahead of the server clock a dateObserved may be. Readers take the latest dateObserved as
     * the patient's current reading, so a future-dated row would stay "current" until that date passed.
     * The allowance absorbs clock skew between a client that stamps "now" and this server.
     */
    static final long MAX_FUTURE_SKEW_MILLIS = 5L * 60L * 1000L;
    /** The yyyy-MM-dd shape a date-only measurement validation rule accepts. */
    private static final java.util.regex.Pattern DATE_ONLY = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    /** A plain decimal number: optional sign, digits, optional fraction; no exponent, hex or suffix. */
    private static final java.util.regex.Pattern PLAIN_DECIMAL = java.util.regex.Pattern.compile("[-+]?(\\d+(\\.\\d*)?|\\.\\d+)");

    /** Request fields accepted by {@link #addMeasurement}. */
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_DATA = "dataField";
    private static final String FIELD_COMMENTS = "comments";
    private static final String FIELD_INSTRUCTION = "measuringInstruction";
    private static final String FIELD_DATE_OBSERVED = "dateObserved";
    private static final Set<String> ACCEPTED_FIELDS =
            Set.of(FIELD_TYPE, FIELD_DATA, FIELD_COMMENTS, FIELD_INSTRUCTION, FIELD_DATE_OBSERVED);
    private static final int TYPE_MAX_LENGTH = 50;
    /** Width of the comments and measuringInstruction columns. */
    private static final int TEXT_COLUMN_MAX_LENGTH = 255;
    private static final String INVALID_DATE_OBSERVED = "INVALID_DATE_OBSERVED";

    /**
     * The REST and SOAP endpoints that carry the CDS projection guard keep a public no-argument
     * constructor (CdsEndpointProjectionInjectionUnitTest pins it), so collaborators arrive through
     * the required setters below rather than through a constructor.
     */
    public MeasurementService() {
        // Collaborators are set by Spring through the @Autowired setters.
    }

    @Autowired
    public void setSecurityInfoManager(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = Objects.requireNonNull(securityInfoManager);
    }

    @Autowired
    public void setMeasurementManager(MeasurementManager measurementManager) {
        this.measurementManager = Objects.requireNonNull(measurementManager);
    }

    @Autowired
    public void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = Objects.requireNonNull(demographicDao);
    }

    @Autowired
    public void setMeasurementTypeDao(MeasurementTypeDao measurementTypeDao) {
        this.measurementTypeDao = Objects.requireNonNull(measurementTypeDao);
    }

    @Autowired
    public void setValidationsDao(ValidationsDao validationsDao) {
        this.validationsDao = Objects.requireNonNull(validationsDao);
    }

    /** Required: every patient-scoped read and write passes the CDS projection guard. */
    @Autowired
    public void setCdsLegacyProjectionService(CdsLegacyProjectionService projections) {
        this.projections = Objects.requireNonNull(projections);
    }

    /**
     * Retrieves measurements of specified types for a patient via REST API.
     * This endpoint accepts a JSON payload containing measurement type codes and returns
     * matching measurement data for the specified patient.
     * 
     * <p>Request format:</p>
     * <pre>{@code
     * {
     *   "types": ["BP", "WT", "HT", "GLU"]
     * }
     * }</pre>
     * 
     * <p>Security requirements:</p>
     * <ul>
     *   <li>Authenticated UI session or valid OAuth 1.0a authentication token</li>
     *   <li>"_measurement" read privilege</li>
     *   <li>Access to the specified patient's demographic data</li>
     * </ul>
     * 
     * <p>Response includes all measurements of the requested types for the patient,
     * transformed into API-friendly format with metadata.</p>
     * 
     * @param json ObjectNode containing array of measurement type codes to retrieve
     * @param demoId Integer the patient's demographic ID from the URL path
     * @return MeasurementResponse containing matching measurements and metadata
     * @throws BadRequestException (HTTP 400) if the patient identifier is missing or nonpositive
     * @throws ForbiddenException if measurement or patient-record access is denied
     * 
     * @see MeasurementResponse
     * @see MeasurementManager#getMeasurementByType(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer, List)
     */
    @POST
    @Path("/{demographicNo}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public MeasurementResponse getMeasurements(ObjectNode json, @PathParam("demographicNo") Integer demoId) {
        if (demoId == null || demoId <= 0) {
            throw new BadRequestException("Invalid demographic identifier");
        }
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        // The URL is client-controlled. Enforce patient-specific measurement rights
        // and demographic/eChart record restrictions before accessing any data.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "r", demoId.toString())
                || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demoId)) {
            throw new ForbiddenException("Access Denied: measurement or patient-record access is restricted");
        }
        projections.requireRestSupported(demoId, Set.of("CareElements"));
        MeasurementResponse response = new MeasurementResponse();
        ArrayNode jsonArray = (ArrayNode) json.get("types");
        List<String> typesList = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            typesList.add(jsonArray.get(i).asText());
        }
        String[] types = typesList.toArray(new String[0]);
        if (types.length < 1) {
            return response;
        }

        List<Measurement> measurements = measurementManager.getMeasurementByType(loggedInInfo, demoId, new ArrayList<String>(Arrays.asList(types)));
        response.addMeasurements(measurements);
        return response;
    }

    /**
     * Records a new measurement for a patient without changing any existing measurement.
     *
     * <p>{@code dataField} may be up to {@link Measurement#DATA_FIELD_MAX_LENGTH} characters, further
     * limited by the type's configured validation rule; {@code comments} and
     * {@code measuringInstruction} are limited to 255, the width of their columns.</p>
     *
     * <p>The type is resolved against the configured measurement types. With a
     * {@code measuringInstruction}, the type and instruction must name one configured type. Without
     * one, the type alone must: a type configured with several instructions (the seeded FEET, for one) is refused
     * with {@code INSTRUCTION_REQUIRED}. The reading is stored under the configured spelling of the
     * type and instruction, not the caller's.</p>
     *
     * <p>{@code dateObserved} defaults to now, so a new reading becomes the patient's current one.
     * A value more than five minutes ahead of the server clock is refused with
     * {@code DATE_OBSERVED_IN_FUTURE}, since it would rank as the current reading until that time.
     * It is stored to the whole second, the precision of its column. A request that repeats a
     * reading already stored with the same patient, type, instruction, value, comments and
     * {@code dateObserved} returns that reading instead of storing it twice, so a client that sends
     * {@code dateObserved} can safely retry.</p>
     *
     * <p>An optional field sent as JSON {@code null} is treated as left out. {@code type} and
     * {@code dataField} are required and are refused when {@code null}.</p>
     *
     * <p>A 400 refusal is answered with a JSON body {@code {"code": ..., "message": ...}}. The other
     * refusals do not use that shape: 403 (privilege, patient access or provider identity), 404
     * (patient) and 500 (invalid validation configuration) use the server's standard error response,
     * and 409 (the patient's structured care cannot be projected) a plain-text explanation.</p>
     *
     * @param json measurement fields: required {@code type} and {@code dataField}, and optional
     *             {@code comments}, {@code measuringInstruction}, and epoch-millisecond
     *             {@code dateObserved}
     * @param demoId patient identifier
     * @return a detached snapshot of the recorded (or already stored) measurement
     * @throws BadRequestException (HTTP 400) if identifiers, request fields, type, or value are invalid
     * @throws ForbiddenException (HTTP 403) if the caller lacks measurement write privilege, patient access, or a provider identity
     * @throws NotFoundException (HTTP 404) if the patient does not exist
     * @throws WebApplicationException (HTTP 409) if the patient's structured care cannot be projected
     * @throws InternalServerErrorException (HTTP 500) if the type's validation configuration is invalid
     */
    @POST
    @Path("/{demographicNo}/new")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public EmbeddedMeasurementTo1 addMeasurement(ObjectNode json, @PathParam("demographicNo") Integer demoId) {
        // Order matters: the request shape is refused before any privilege check or lookup, and the
        // caller's rights are checked before the patient's existence is revealed.
        requireWellFormedRequest(json, demoId);
        String comments = optionalText(json, FIELD_COMMENTS, TEXT_COLUMN_MAX_LENGTH);
        String instruction = optionalText(json, FIELD_INSTRUCTION, TEXT_COLUMN_MAX_LENGTH);
        Long observedAt = optionalDateObserved(json);
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        String providerNo = requireWriteAccess(loggedInInfo, demoId);
        projections.requireRestSupported(demoId, Set.of("CareElements"));
        String value = json.get(FIELD_DATA).textValue();
        MeasurementType configured = resolveType(json.get(FIELD_TYPE).textValue(), instruction);
        validateMeasurementValue(configured, value);

        Measurement measurement = new Measurement();
        measurement.setType(configured.getType());
        measurement.setDemographicId(demoId);
        measurement.setProviderNo(providerNo);
        measurement.setDataField(value);
        measurement.setComments(comments == null ? "" : comments);
        measurement.setMeasuringInstruction(configured.getMeasuringInstruction() == null
                ? "" : configured.getMeasuringInstruction());
        // measurements.dateObserved is DATETIME, which keeps whole seconds. Truncating via Instant
        // makes the stored value, the echoed value and the manager's retry lookup all agree.
        long observedMillis = observedAt == null ? System.currentTimeMillis() : observedAt;
        measurement.setDateObserved(Date.from(Instant.ofEpochSecond(Math.floorDiv(observedMillis, 1000L))));
        // A retry of a request whose response was lost repeats its dateObserved, so only a dated
        // request can be a repeat. The manager checks and inserts under the patient's row lock, so two
        // overlapping identical requests still store one reading.
        Measurement saved;
        if (observedAt == null) {
            saved = measurementManager.addMeasurement(loggedInInfo, measurement);
        } else {
            try {
                saved = measurementManager.addMeasurementUnlessStored(loggedInInfo, measurement);
            } catch (IllegalArgumentException _) {
                throw new NotFoundException("Patient not found");
            }
        }
        return EmbeddedRecordSnapshots.measurement(loggedInInfo, saved);
    }

    /** Refuses a bad patient id, a non-object body, an unknown field, or a missing type or value. */
    private static void requireWellFormedRequest(ObjectNode json, Integer demoId) {
        if (demoId == null || demoId <= 0) {
            throw badRequest("INVALID_PATIENT", "Invalid demographic identifier");
        }
        if (json == null || !json.isObject()) {
            throw badRequest("INVALID_REQUEST", "Measurement data is required");
        }
        for (var field = json.fieldNames(); field.hasNext();) {
            if (!ACCEPTED_FIELDS.contains(field.next())) {
                throw badRequest("UNSUPPORTED_FIELD", "Unsupported measurement field");
            }
        }
        if (!isNonBlankText(json, FIELD_TYPE, TYPE_MAX_LENGTH)) {
            throw badRequest("INVALID_TYPE", "A valid measurement type is required");
        }
        if (!isNonBlankText(json, FIELD_DATA, Measurement.DATA_FIELD_MAX_LENGTH)) {
            throw badRequest("INVALID_VALUE", "A valid measurement dataField is required");
        }
    }

    private static boolean isNonBlankText(ObjectNode json, String field, int maxLength) {
        return json.has(field) && json.get(field).isTextual()
                && !json.get(field).textValue().isBlank() && json.get(field).textValue().length() <= maxLength;
    }

    /** The caller's dateObserved in epoch milliseconds, or {@code null} when it was left out. */
    private static Long optionalDateObserved(ObjectNode json) {
        if (!json.hasNonNull(FIELD_DATE_OBSERVED)) {
            return null;
        }
        if (!json.get(FIELD_DATE_OBSERVED).isIntegralNumber() || !json.get(FIELD_DATE_OBSERVED).canConvertToLong()) {
            throw badRequest(INVALID_DATE_OBSERVED, "dateObserved must be epoch milliseconds");
        }
        long timestamp = json.get(FIELD_DATE_OBSERVED).longValue();
        if (timestamp < MIN_DATETIME_MILLIS || timestamp > MAX_DATETIME_MILLIS) {
            throw badRequest(INVALID_DATE_OBSERVED, "dateObserved is outside the supported database DATETIME range");
        }
        if (timestamp > System.currentTimeMillis() + MAX_FUTURE_SKEW_MILLIS) {
            throw badRequest("DATE_OBSERVED_IN_FUTURE", "dateObserved is in the future");
        }
        return timestamp;
    }

    /**
     * Checks measurement write and patient-record access, then that the patient exists and the caller
     * has a provider identity to file the reading under.
     *
     * @return the caller's provider number
     */
    private String requireWriteAccess(LoggedInInfo loggedInInfo, Integer demoId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "w", demoId.toString())
                || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demoId)) {
            throw new ForbiddenException("Access Denied: measurement or patient-record access is restricted");
        }
        if (demographicDao.getDemographicById(demoId) == null) {
            throw new NotFoundException("Patient not found");
        }
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        if (providerNo == null || providerNo.isBlank()) {
            throw new ForbiddenException("A provider identity is required to record a measurement");
        }
        return providerNo;
    }

    /** A 400 whose JSON body names the refusal, so a client can tell its causes apart. */
    private static BadRequestException badRequest(String code, String message) {
        return new BadRequestException(jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(java.util.Map.of("code", code, "message", message))
                .build());
    }

    private static String optionalText(ObjectNode json, String field, int maxLength) {
        // JSON null is "not sent", as many client serializers write an unset optional field.
        if (!json.hasNonNull(field)) {
            return null;
        }
        if (!json.get(field).isTextual() || json.get(field).textValue().length() > maxLength) {
            throw badRequest("INVALID_" + field.toUpperCase(java.util.Locale.ROOT), "Invalid measurement " + field);
        }
        return json.get(field).textValue();
    }

    /**
     * Finds the one configured type a request names. Rows that agree on instruction and validation
     * rule are the same type configured twice (the seeds carry LMP twice) and count as one.
     */
    private MeasurementType resolveType(String type, String instruction) {
        List<MeasurementType> rows = instruction == null
                ? measurementTypeDao.findByType(type)
                : measurementTypeDao.findByTypeAndMeasuringInstruction(type, instruction);
        if (rows == null || rows.isEmpty()) {
            throw badRequest("UNKNOWN_TYPE", "Unknown measurement type or measuringInstruction");
        }
        java.util.Map<List<String>, MeasurementType> distinct = new java.util.LinkedHashMap<>();
        for (MeasurementType row : rows) {
            distinct.putIfAbsent(Arrays.asList(
                    row.getMeasuringInstruction() == null ? "" : row.getMeasuringInstruction().toLowerCase(java.util.Locale.ROOT),
                    row.getValidation() == null ? "" : row.getValidation().trim()), row);
        }
        if (distinct.size() != 1) {
            throw instruction == null
                    ? badRequest("INSTRUCTION_REQUIRED", "measuringInstruction is required for this type")
                    : badRequest("AMBIGUOUS_TYPE", "The type and measuringInstruction match more than one configured type");
        }
        return distinct.values().iterator().next();
    }

    private void validateMeasurementValue(MeasurementType configured, String value) {
        String validationId = configured.getValidation();

        // A configured type without a validation rule is permitted.
        if (validationId == null || validationId.isBlank()) {
            return;
        }

        final int id;
        try {
            id = Integer.parseInt(validationId.trim());
        } catch (NumberFormatException _) {
            throw new InternalServerErrorException("Invalid measurement validation configuration");
        }

        Validations rule = validationsDao.find(id);
        if (rule == null) {
            throw new InternalServerErrorException("Measurement validation rule not found");
        }

        Double maxValue = rule.getMaxValue() == null ? 0.0 : rule.getMaxValue();
        Double minValue = rule.getMinValue() == null ? 0.0 : rule.getMinValue();
        Integer maxLength = rule.getMaxLength() == null ? 0 : rule.getMaxLength();
        Integer minLength = rule.getMinLength() == null ? 0 : rule.getMinLength();
        String regex = rule.getRegularExp();
        Boolean numeric = rule.isNumeric();
        boolean requiresNumeric = Boolean.TRUE.equals(numeric) || maxValue != 0.0 || minValue != 0.0;
        boolean dateOnly = Boolean.TRUE.equals(rule.isDate());
        boolean bloodPressureRule = isBloodPressureValidation(configured, rule)
                && regex != null && !regex.isEmpty();

        try {
            if (!ectValidation.isInRange(maxValue, minValue, value)
                    || !ectValidation.maxLength(maxLength, value)
                    || !ectValidation.minLength(minLength, value)
                    || !ectValidation.matchRegExp(regex, value)
                    || (bloodPressureRule && !ectValidation.isValidBloodPressure(regex, value))
                    || !ectValidation.isNumeric(numeric, value)
                    // isNumeric and isInRange use Double.valueOf, which also takes NaN, Infinity,
                    // hex ("0x1p3") and type suffixes ("5d"). An API value must be a plain decimal number.
                    || (requiresNumeric && !isPlainDecimal(value))
                    // SimpleDateFormat.parse stops at the first unparsed character, so the shape is
                    // checked too: "2026-01-01x" must not pass as a date.
                    || (dateOnly && !(DATE_ONLY.matcher(value).matches() && ectValidation.isDate(value)))) {
                throw badRequest("VALUE_REJECTED", "Measurement value violates its configured validation rules");
            }
        } catch (java.util.regex.PatternSyntaxException _) {
            throw new InternalServerErrorException("Invalid measurement validation configuration");
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static boolean isBloodPressureValidation(MeasurementType configured, Validations rule) {
        return "BP".equalsIgnoreCase(configured.getType())
                || (rule.getName() != null && "Blood Pressure".equalsIgnoreCase(rule.getName().trim()));
    }

    private static boolean isPlainDecimal(String value) {
        String trimmed = value.strip();
        return PLAIN_DECIMAL.matcher(trimmed).matches() && Double.isFinite(Double.parseDouble(trimmed));
    }
}
