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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
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
 *   <li>Output: {@link MeasurementResponse} objects with measurement collections</li>
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
 * }</pre>
 * 
 * @since 2006
 * @see Measurement
 * @see MeasurementResponse
 * @see MeasurementManager
 * @see AbstractServiceImpl
 */
@Path("/measurements")
@Component("measurementService")
@Consumes(MediaType.APPLICATION_JSON)
public class MeasurementService extends AbstractServiceImpl {
    /** Security manager for measurement and patient-record authorization. */
    @Autowired
    private SecurityInfoManager securityInfoManager;
    
    /** Business logic manager for measurement operations. */
    @Autowired
    private MeasurementManager measurementManager;

    @Autowired
    private DemographicDao demographicDao;

    @Autowired
    private MeasurementTypeDao measurementTypeDao;

    @Autowired
    private ValidationsDao validationsDao;

    @Autowired
    private MeasurementDao measurementDao;

    private io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService projections;
    private final EctValidation ectValidation = new EctValidation();

    private static final long MIN_DATETIME_MILLIS = -30610224000000L;
    private static final long MAX_DATETIME_MILLIS = 253402300799000L;
    /** The yyyy-MM-dd shape a date-only measurement validation rule accepts. */
    private static final java.util.regex.Pattern DATE_ONLY = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    /** A plain decimal number: optional sign, digits, optional fraction; no exponent, hex or suffix. */
    private static final java.util.regex.Pattern PLAIN_DECIMAL = java.util.regex.Pattern.compile("[-+]?(\\d+(\\.\\d*)?|\\.\\d+)");

    /** Keeps the framework's public no-argument endpoint lifecycle and requires its projection guard. */
    @Autowired
    public void setCdsLegacyProjectionService(io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService service) {
        this.projections = java.util.Objects.requireNonNull(service);
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
        projections.requireRestSupported(demoId, java.util.Set.of("CareElements"));
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
     * one, the type alone must: a type configured with several instructions (BP, WT, ...) is refused
     * with {@code INSTRUCTION_REQUIRED}. The reading is stored under the configured spelling of the
     * type and instruction, not the caller's.</p>
     *
     * <p>{@code dateObserved} defaults to now, so a new reading becomes the patient's current one.
     * It is stored to the whole second, the precision of its column. A request that repeats a
     * reading already stored with the same patient, type, instruction, value, comments and
     * {@code dateObserved} returns that reading instead of storing it twice, so a client that sends
     * {@code dateObserved} can safely retry.</p>
     *
     * <p>A refused request is answered with a JSON body {@code {"code": ..., "message": ...}}.</p>
     *
     * @param json measurement fields: required {@code type} and {@code dataField}, and optional
     *             {@code comments}, {@code measuringInstruction}, and epoch-millisecond
     *             {@code dateObserved}
     * @param demoId patient identifier
     * @return a detached snapshot of the recorded (or already stored) measurement
     * @throws BadRequestException if identifiers or measurement fields are invalid
     * @throws ForbiddenException if the caller lacks measurement write or patient access
     */
    @POST
    @Path("/{demographicNo}/new")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public EmbeddedMeasurementTo1 addMeasurement(ObjectNode json, @PathParam("demographicNo") Integer demoId) {
        if (demoId == null || demoId <= 0) {
            throw badRequest("INVALID_PATIENT", "Invalid demographic identifier");
        }
        if (json == null || !json.isObject()) {
            throw badRequest("INVALID_REQUEST", "Measurement data is required");
        }
        for (var field = json.fieldNames(); field.hasNext();) {
            String name = field.next();
            if (!List.of("type", "dataField", "comments", "measuringInstruction", "dateObserved").contains(name)) {
                throw badRequest("UNSUPPORTED_FIELD", "Unsupported measurement field");
            }
        }
        if (!json.has("type") || !json.get("type").isTextual()
                || json.get("type").textValue().isBlank() || json.get("type").textValue().length() > 50) {
            throw badRequest("INVALID_TYPE", "A valid measurement type is required");
        }
        if (!json.has("dataField") || !json.get("dataField").isTextual()
                || json.get("dataField").textValue().isBlank()
                || json.get("dataField").textValue().length() > Measurement.DATA_FIELD_MAX_LENGTH) {
            throw badRequest("INVALID_VALUE", "A valid measurement dataField is required");
        }
        String comments = optionalText(json, "comments", 255);
        String instruction = optionalText(json, "measuringInstruction", 255);
        Long observedAt = null;
        if (json.has("dateObserved")) {
            if (!json.get("dateObserved").isIntegralNumber()
                    || !json.get("dateObserved").canConvertToLong()) {
                throw badRequest("INVALID_DATE_OBSERVED", "dateObserved must be epoch milliseconds");
            }

            long timestamp = json.get("dateObserved").longValue();
            if (timestamp < MIN_DATETIME_MILLIS || timestamp > MAX_DATETIME_MILLIS) {
                throw badRequest("INVALID_DATE_OBSERVED", "dateObserved is outside the supported database DATETIME range");
            }
            observedAt = timestamp;
        }

        LoggedInInfo loggedInInfo = getLoggedInInfo();
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

        projections.requireRestSupported(demoId, java.util.Set.of("CareElements"));
        String value = json.get("dataField").textValue();
        MeasurementType configured = resolveType(json.get("type").textValue(), instruction);
        validateMeasurementValue(configured, value);

        String type = configured.getType();
        String configuredInstruction = configured.getMeasuringInstruction() == null
                ? "" : configured.getMeasuringInstruction();
        String storedComments = comments == null ? "" : comments;
        // measurements.dateObserved is DATETIME, which keeps whole seconds. Truncating here makes the
        // stored value, the echoed value and the retry lookup below all agree.
        Date dateObserved = new Date(observedAt == null
                ? truncateToSecond(System.currentTimeMillis()) : truncateToSecond(observedAt));

        if (observedAt != null) {
            // A retry of a request whose response was lost repeats its dateObserved. The encounter
            // form skips an identical reading the same way (EctMeasurements2Action).
            MeasurementDaoImpl.SearchCriteria criteria = new MeasurementDaoImpl.SearchCriteria();
            criteria.setDemographicNo(demoId);
            criteria.setType(type);
            criteria.setDataField(value);
            criteria.setMeasuringInstrc(configuredInstruction);
            criteria.setComments(storedComments);
            criteria.setDateObserved(dateObserved);
            List<Measurement> stored = measurementDao.find(criteria);
            if (stored != null && !stored.isEmpty()) {
                return EmbeddedRecordSnapshots.measurement(loggedInInfo, stored.get(0));
            }
        }

        Measurement measurement = new Measurement();
        measurement.setType(type);
        measurement.setDemographicId(demoId);
        measurement.setProviderNo(providerNo);
        measurement.setDataField(value);
        measurement.setComments(storedComments);
        measurement.setMeasuringInstruction(configuredInstruction);
        measurement.setDateObserved(dateObserved);
        return EmbeddedRecordSnapshots.measurement(loggedInInfo,
                measurementManager.addMeasurement(loggedInInfo, measurement));
    }

    private static long truncateToSecond(long millis) {
        return millis - Math.floorMod(millis, 1000L);
    }

    /** A 400 whose JSON body names the refusal, so a client can tell its causes apart. */
    private static BadRequestException badRequest(String code, String message) {
        return new BadRequestException(jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(java.util.Map.of("code", code, "message", message))
                .build());
    }

    private String optionalText(ObjectNode json, String field, int maxLength) {
        if (!json.has(field)) {
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
        } catch (NumberFormatException e) {
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
        boolean dateOnly = Boolean.TRUE.equals(rule.isDate());

        try {
            if (!ectValidation.isInRange(maxValue, minValue, value)
                    || !ectValidation.maxLength(maxLength, value)
                    || !ectValidation.minLength(minLength, value)
                    || !ectValidation.matchRegExp(regex, value)
                    || !ectValidation.isValidBloodPressure(regex, value)
                    || !ectValidation.isNumeric(numeric, value)
                    // isNumeric is Double.valueOf, which also takes NaN, Infinity, hex ("0x1p3") and
                    // type suffixes ("5d"). An API value must be a plain decimal number.
                    || (Boolean.TRUE.equals(numeric) && !isPlainDecimal(value))
                    // SimpleDateFormat.parse stops at the first unparsed character, so the shape is
                    // checked too: "2026-01-01x" must not pass as a date.
                    || (dateOnly && !(DATE_ONLY.matcher(value).matches() && ectValidation.isDate(value)))) {
                throw badRequest("VALUE_REJECTED", "Measurement value violates its configured validation rules");
            }
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new InternalServerErrorException("Invalid measurement validation configuration");
        }
    }

    private static boolean isPlainDecimal(String value) {
        String trimmed = value.strip();
        return PLAIN_DECIMAL.matcher(trimmed).matches() && Double.isFinite(Double.parseDouble(trimmed));
    }
}
