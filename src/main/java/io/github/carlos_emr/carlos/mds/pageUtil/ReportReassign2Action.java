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


package io.github.carlos_emr.carlos.mds.pageUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingFavoritesDao;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingFavorite;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Forwards labs and documents to other providers: the action behind {@code oscarMDS/ReportReassign}
 * (the Inbox and the patient lab list) and the lab pages' {@code oscarMDS/Forward},
 * {@code lab/CA/ON/Forward}, {@code lab/CA/BC/Forward} and {@code lab/CA/ALL/Forward}.
 *
 * <p><b>Request contract.</b> Every caller posts the same JSON-in-form-field payload, one value
 * per field:
 * <ul>
 *   <li>{@code flaggedLabs} {@code {"files":["<id>:<TYPE>", ...]}}: at least one entry, each a
 *       positive int id and a type {@link CommonLabResultData#getMatchingLabs} can resolve;</li>
 *   <li>{@code selectedProviders} {@code {"providers":["<provider_no>", ...]}}: at least one;</li>
 *   <li>{@code selectedFavorites} {@code {"favorites":[...]}}: optional. Present, it is the
 *       sender's complete forwarding-favourites list as the picker showed it, and the stored list is
 *       made to match it (an empty list clears it). Absent, the stored favourites are left alone:
 *       a caller with no favourites UI must not wipe them.</li>
 * </ul>
 * The whole request is validated before anything is written; a selection that does not parse
 * routes nothing (issue #4558: the lab pages posted bare id lists and every one of them was
 * dropped, while one shared array let a failed provider parse route the lab to the favourites).
 *
 * <p><b>Response.</b> {@code ajax=yes} (the Inbox) answers JSON {@code {"success":..,"files":[..]}}:
 * 200 routed, 400 refused before any write, 500 routing failed. A page post answers a redirect to
 * the page it came from: the lab page's own display for the routed lab, or the patient lab list
 * for {@code oscarMDS/ReportReassign}. The redirect target is chosen from a fixed table by route,
 * never from the request, so it is always a same-origin application path.
 *
 * <p>Only POST forwards. {@code HttpMethodGuardFilter} already refuses GET on these routes; the
 * check here keeps the action safe if a route is ever mapped outside that filter.
 */
public class ReportReassign2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /** Lab types whose version chain {@link CommonLabResultData#getMatchingLabs} resolves. */
    private static final Set<String> FORWARDABLE_TYPES = Set.of(LabResultData.HL7TEXT, LabResultData.MDS,
            LabResultData.CML, LabResultData.EXCELLERIS, LabResultData.DOCUMENT, LabResultData.HRM);
    private static final Pattern LAB_ID = Pattern.compile("[1-9][0-9]{0,9}");
    private static final Pattern PROVIDER_NO = Pattern.compile("[a-zA-Z0-9]{1,6}");

    /** Where each route's page post returns: the display of the page that posted it. */
    private static final Map<String, String> LAB_DISPLAYS = Map.of(
            "/lab/CA/ON/Forward", "/lab/CA/ON/ViewCMLDisplay",
            "/oscarMDS/Forward", "/oscarMDS/ViewSegmentDisplay",
            "/lab/CA/BC/Forward", "/lab/CA/BC/ViewLabDisplay",
            "/lab/CA/ALL/Forward", "/lab/CA/ALL/ViewLabDisplay");
    /** The patient lab list, the one page that posts {@code oscarMDS/ReportReassign} without AJAX. */
    private static final String PATIENT_LAB_LIST = "/lab/ViewDemographicLab";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = MiscUtils.getLogger();
    private final SecurityInfoManager securityInfoManager;
    private final ProviderLabRoutingFavoritesDao favoritesDao;

    /** Struts builds this action with its no-arg constructor; the beans are resolved here once. */
    public ReportReassign2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(ProviderLabRoutingFavoritesDao.class));
    }

    ReportReassign2Action(SecurityInfoManager securityInfoManager, ProviderLabRoutingFavoritesDao favoritesDao) {
        this.securityInfoManager = securityInfoManager;
        this.favoritesDao = favoritesDao;
    }

    /** A request the action refuses before any write; its message never reaches the browser. */
    private static final class InvalidSelection extends Exception {
        InvalidSelection(String message) {
            super(message, null, false, false);
        }
    }

    // FindSecBugs UNVALIDATED_REDIRECT: the redirect path comes from a fixed route table; every appended value is URI-component encoded.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "the redirect path comes from a fixed route table and every appended value is URI-component encoded")
    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        boolean ajax = "yes".equals(request.getParameter("ajax"));
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            return refuse(ajax, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        ArrayNode files;
        ArrayList<String[]> labs;
        String[] providers;
        List<String> newFavorites;
        try {
            files = requiredList("flaggedLabs", "files");
            labs = labSelection(files);
            providers = providerNumbers(requiredList("selectedProviders", "providers")).toArray(new String[0]);
            ArrayNode favoritesList = optionalList("selectedFavorites", "favorites");
            newFavorites = favoritesList == null ? null : providerNumbers(favoritesList);
        } catch (InvalidSelection invalid) {
            logger.warn("Lab forward refused: {}", invalid.getMessage());
            return refuse(ajax, HttpServletResponse.SC_BAD_REQUEST);
        }
        logger.debug("Forwarding {} lab(s) to {} provider(s)", labs.size(), providers.length);

        boolean success;
        try {
            success = CommonLabResultData.updateLabRouting(labs, providers);
        } catch (RuntimeException e) {
            logger.error("exception in ReportReassign2Action", e);
            return refuse(ajax, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        if (!success) {
            return refuse(ajax, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        // Routing has committed by now. The favourites are the sender's own preference, saved on their
        // own: a failure to save them must not report a forward that happened as one that did not.
        if (newFavorites != null) {
            try {
                syncFavorites(providerNo, newFavorites);
            } catch (RuntimeException e) {
                logger.error("Lab forwarded, but the forwarding favourites were not saved", e);
            }
        }

        if (ajax) {
            writeJson(HttpServletResponse.SC_OK, true, files);
            return NONE;
        }
        try {
            // FP for open-redirect scanners (CodeQL java/unvalidated-url-redirection, Semgrep
            // javasecurity:S5146): the path comes from a fixed route table and every appended
            // value is URI-component encoded, so the target is always this application's path.
            response.sendRedirect(returnUrl(labs.get(0)[0], providerNo)); // nosemgrep: javasecurity.S5146, java.lang.security.audit.servlets.unvalidated-redirect.unvalidated-redirect-java -- see comment above
        } catch (IOException e) {
            logger.error("Could not send the lab forward redirect", e);
        }
        return NONE;
    }

    /** The one value of a JSON-object field, and its named array (refused when absent or empty). */
    private ArrayNode requiredList(String parameter, String key) throws InvalidSelection {
        ArrayNode list = optionalList(parameter, key);
        if (list == null || list.isEmpty()) {
            throw new InvalidSelection(parameter + " is missing or empty");
        }
        return list;
    }

    /** As {@link #requiredList}, but null when the field was not posted at all. */
    private ArrayNode optionalList(String parameter, String key) throws InvalidSelection {
        String[] values = request.getParameterValues(parameter);
        if (values == null) {
            return null;
        }
        if (values.length != 1) {
            throw new InvalidSelection(parameter + " is repeated");
        }
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(values[0]);
        } catch (IOException e) {
            throw new InvalidSelection(parameter + " is not JSON");
        }
        if (parsed == null || !parsed.isObject() || !parsed.path(key).isArray()) {
            throw new InvalidSelection(parameter + " has no \"" + key + "\" list");
        }
        return (ArrayNode) parsed.get(key);
    }

    /** Each "id:TYPE" entry as the [id, type] pair {@link CommonLabResultData#updateLabRouting} takes. */
    private static ArrayList<String[]> labSelection(ArrayNode files) throws InvalidSelection {
        ArrayList<String[]> labs = new ArrayList<>();
        for (JsonNode entry : files) {
            String[] lab = entry.isTextual() ? entry.asText().split(":", -1) : new String[0];
            if (lab.length != 2 || !LAB_ID.matcher(lab[0]).matches() || !FORWARDABLE_TYPES.contains(lab[1])) {
                throw new InvalidSelection("flaggedLabs holds an entry that is not <id>:<forwardable type>");
            }
            try {
                Integer.parseInt(lab[0]);
            } catch (NumberFormatException e) {
                throw new InvalidSelection("flaggedLabs holds an id out of range");
            }
            labs.add(lab);
        }
        return labs;
    }

    private static List<String> providerNumbers(ArrayNode list) throws InvalidSelection {
        List<String> providers = new ArrayList<>();
        for (JsonNode entry : list) {
            if (!entry.isTextual() || !PROVIDER_NO.matcher(entry.asText()).matches()) {
                throw new InvalidSelection("a provider list holds an entry that is not a provider number");
            }
            providers.add(entry.asText());
        }
        return providers;
    }

    /** Make the sender's stored favourites match the posted list: add what is new, remove the rest by id. */
    private void syncFavorites(String providerNo, List<String> newFavorites) {
        List<ProviderLabRoutingFavorite> currentFavorites = favoritesDao.findFavorites(providerNo);
        for (String favorite : newFavorites) {
            boolean isNew = currentFavorites.stream().noneMatch(fav -> favorite.equals(fav.getRoute_to_provider_no()));
            if (isNew) {
                ProviderLabRoutingFavorite newFav = new ProviderLabRoutingFavorite();
                newFav.setProvider_no(providerNo);
                newFav.setRoute_to_provider_no(favorite);
                favoritesDao.persist(newFav);
            }
        }
        for (ProviderLabRoutingFavorite fav : currentFavorites) {
            if (!newFavorites.contains(fav.getRoute_to_provider_no())) {
                favoritesDao.remove(fav.getId());
            }
        }
    }

    /**
     * The page a page post returns to. A lab page's route returns to that page's own display for
     * the (first) routed lab; {@code oscarMDS/ReportReassign}, and any path this table does not
     * know, return to the patient lab list with the filters it posted.
     */
    private String returnUrl(String segmentId, String providerNo) {
        String display = LAB_DISPLAYS.get(route());
        Map<String, String> query = new LinkedHashMap<>();
        if (display != null) {
            query.put("segmentID", segmentId);
        }
        query.put("providerNo", providerNo);
        query.put("searchProviderNo", request.getParameter("searchProviderNo"));
        query.put("status", request.getParameter("status"));
        if (display == null) {
            query.put("lname", request.getParameter("lname"));
            query.put("fname", request.getParameter("fname"));
            query.put("hnum", request.getParameter("hnum"));
        }
        StringBuilder url = new StringBuilder(request.getContextPath()).append(display != null ? display : PATIENT_LAB_LIST);
        char separator = '?';
        for (Map.Entry<String, String> parameter : query.entrySet()) {
            if (parameter.getValue() == null || parameter.getValue().isEmpty()) {
                continue;
            }
            url.append(separator).append(parameter.getKey()).append('=').append(SafeEncode.forUriComponent(parameter.getValue()));
            separator = '&';
        }
        return url.toString();
    }

    /** The request's route, relative to the context, without path parameters. */
    private String route() {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (uri == null) {
            return "";
        }
        int parameters = uri.indexOf(';');
        if (parameters >= 0) {
            uri = uri.substring(0, parameters);
        }
        return context != null && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    /** Answer a refused or failed forward: JSON for the Inbox, an HTTP error for a page post. */
    private String refuse(boolean ajax, int status) {
        if (ajax) {
            writeJson(status, false, null);
            return NONE;
        }
        try {
            response.sendError(status);
        } catch (IOException e) {
            logger.error("Could not report the lab forward outcome", e);
            response.setStatus(status);
        }
        return NONE;
    }

    // FindSecBugs XSS_SERVLET: the body is Jackson-serialized JSON (a boolean and validated "<id>:<TYPE>" strings), sent as application/json, never HTML.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "the body is Jackson-serialized JSON of a boolean and validated <id>:<TYPE> strings, sent as application/json, never HTML")
    private void writeJson(int status, boolean success, ArrayNode files) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", success);
        if (files != null) {
            result.set("files", files);
        }
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try {
            response.getWriter().print(result);
            response.getWriter().flush();
        } catch (IOException e) {
            logger.error("Error with JSON response ", e);
        }
    }
}
