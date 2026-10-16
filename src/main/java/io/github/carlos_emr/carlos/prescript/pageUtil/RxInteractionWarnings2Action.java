/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningLevelResolver;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService.Result;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService.StagedDrug;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService.Status;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService.Warning;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * {@code GET rx/interactionWarnings?demographicNo=N}: the drug-drug interaction warnings for the
 * prescriptions staged on the named patient's Rx page, as JSON.
 *
 * <p>The Rx page ({@code SearchDrug3.jsp}) asks again whenever a card is staged or removed and
 * fills each card's {@code major_/moderate_/minor_/unknown_<randomId>} marker from the answer
 * ({@code share/javascript/rx-interaction-warnings.js}). Each staged drug is checked against the
 * other staged drugs and the patient's current medications through DrugRef, and only the
 * interactions the applicable warning level allows are returned
 * ({@link RxInteractionWarningLevelResolver}). Before issue #4506 the page never asked: the
 * interaction calls were commented out and the session field the old view rendered was never
 * filled, so no interaction was ever shown.</p>
 *
 * <p>Response: {@code {"status":"checked"|"off"|"unavailable","level":0-4,"warnings":[{"randomId",
 * "severity","partner","summary","evidence","comment"}],"unchecked":["randomId"]}}. {@code unchecked}
 * lists the staged drugs DrugRef cannot classify (no ATC code), which the page marks as not checked. {@code unavailable} means DrugRef could
 * not be asked and nothing is known; the page says so instead of showing an empty, reassuring
 * result. Read-only: nothing is stored or changed, so the page uses GET; any other method gets the
 * same answer.</p>
 *
 * <p>Authorisation: global {@code _rx} read, then {@code _rx} read on the named patient and access
 * to the patient's record ({@link RxRequestedPatientAccess#requirePatient}). The patient must be
 * named; the session's active Rx patient is never used as a fallback, because the answer
 * describes one patient's medications and the page that asked may be another patient's (#3875).</p>
 *
 * @since 2026-10-15
 */
public final class RxInteractionWarnings2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final transient SecurityInfoManager securityInfoManager;
    private final transient RxInteractionWarningLevelResolver levelResolver;
    private final transient RxInteractionWarningService warningService;
    private final transient IntFunction<Collection<String>> currentAtcCodes;

    /**
     * Struts creates the action through this constructor.
     */
    public RxInteractionWarnings2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                new RxInteractionWarningLevelResolver(SpringUtils.getBean(UserPropertyDAO.class),
                        SpringUtils.getBean(DemographicExtDao.class)),
                new RxInteractionWarningService(),
                RxInteractionWarnings2Action::activeAtcCodes);
    }

    /**
     * @param securityInfoManager authorisation service
     * @param levelResolver       resolves the facility/provider/patient warning level
     * @param warningService      checks the staged drugs against DrugRef
     * @param currentAtcCodes     the ATC codes of a patient's active medications
     */
    RxInteractionWarnings2Action(SecurityInfoManager securityInfoManager,
                                 RxInteractionWarningLevelResolver levelResolver,
                                 RxInteractionWarningService warningService,
                                 IntFunction<Collection<String>> currentAtcCodes) {
        this.securityInfoManager = securityInfoManager;
        this.levelResolver = levelResolver;
        this.warningService = warningService;
        this.currentAtcCodes = currentAtcCodes;
    }

    /**
     * The ATC codes of the patient's active medications: not discontinued, not archived and still
     * current. {@code RxPrescriptionData.getCurrentATCCodesByPatient} is not used: it keeps every
     * long-term row and any row whose end date has not passed, so a drug discontinued minutes ago
     * would go on warning.
     */
    private static Collection<String> activeAtcCodes(int demographicNo) {
        return Arrays.stream(new RxPrescriptionData().getActivePrescriptionsByPatient(demographicNo))
                .filter(RxPrescriptionData.Prescription::isValidAtcCode)
                .map(RxPrescriptionData.Prescription::getAtcCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Writes the named patient's interaction warnings.
     *
     * @return {@link #NONE}; the response is written directly
     * @throws SecurityException when the caller may not read the module or the named patient
     * @throws IOException       if the response cannot be written
     */
    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        // NOT_REQUESTED (0) and INVALID (-1) alike: the answer is about one named patient.
        int demographicNo = RxSessionBeanResolver.requestedDemographicNo(request);
        if (demographicNo <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        RxRequestedPatientAccess.requirePatient(securityInfoManager, loggedInInfo, demographicNo, "_rx", "r");
        RxSessionBean bean = RxSessionBeanResolver.resolve(request);
        if (bean == null || bean.getDemographicNo() != demographicNo) {
            // Rx is not open for this patient in this session: there is no staged card to warn on.
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        // A custom note is free text, not a drug: there is nothing to check and nothing to flag.
        List<StagedDrug> staged = Arrays.stream(bean.getStash())
                .filter(prescription -> !prescription.isCustomNote())
                .map(prescription -> new StagedDrug(prescription.getRandomId(), prescription.getAtcCode()))
                .toList();
        writeJson(response, check(loggedInInfo, demographicNo, staged));
        return NONE;
    }

    private Result check(LoggedInInfo loggedInInfo, int demographicNo, List<StagedDrug> staged) {
        int level = RxInteractionWarningLevelResolver.NOT_SPECIFIED;
        try {
            level = levelResolver.resolve(loggedInInfo, demographicNo);
            Collection<String> current = staged.isEmpty() || level == RxInteractionWarningLevelResolver.NONE
                    ? List.of() : currentAtcCodes.apply(demographicNo);
            return warningService.check(level, staged, current);
        } catch (RuntimeException e) {
            // Reading the level or the current medications failed: the check did not run.
            logger.error("Drug interaction check unavailable ({})", e.getClass().getSimpleName());
            return new Result(Status.UNAVAILABLE, level, List.of());
        }
    }

    private static void writeJson(HttpServletResponse response, Result result) throws IOException {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("status", switch (result.status()) {
            case CHECKED -> "checked";
            case OFF -> "off";
            case UNAVAILABLE -> "unavailable";
        });
        json.put("level", result.level());
        ArrayNode warnings = json.putArray("warnings");
        for (Warning warning : result.warnings()) {
            ObjectNode item = warnings.addObject();
            // A string: the page matches it against element ids, never does arithmetic with it.
            item.put("randomId", Long.toString(warning.randomId()));
            item.put("severity", warning.severity().code());
            item.put("partner", warning.partner());
            item.put("summary", warning.summary());
            item.put("evidence", warning.evidence());
            item.put("comment", warning.comment());
        }
        ArrayNode unchecked = json.putArray("unchecked");
        result.unchecked().forEach(randomId -> unchecked.add(Long.toString(randomId)));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(MAPPER.writeValueAsBytes(json));
    }
}
