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


package io.github.carlos_emr.carlos.eform.data;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Element;
import org.jsoup.parser.TokenQueue;
import org.jsoup.select.Elements;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.ui.servlet.ImageRenderingServlet;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import io.github.carlos_emr.carlos.util.StringBuilderUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class EForm extends EFormBase {
    // Lazy lookup keeps class loading independent of Spring bean initialization.
    private static EFormDataDao eFormDataDao() { return SpringUtils.getBean(EFormDataDao.class); }
    private static Logger log = MiscUtils.getLogger();

    private String appointment_no = "-1";
    private HashMap<String, String> sql_params = new HashMap<String, String>();
    private String parentAjaxId = null;
    private String eform_link = null;
    private HashMap<String, String> fieldValues = new HashMap<String, String>();
    private int needValueInForm = 0;
    private boolean setAP2nd = false;
    private static final String SCRIPT_TAG = "script";
    private static final String LEGACY_JQUERY_SOURCE = "jquery-1.12.0.min.js";
    private static final String LEGACY_JQUERY_DISPLAY_PATH = "/eform/jquery-1.12.0.min.js";
    /** A whole opening &lt;script ...&gt; tag, for the SRI strip in the CDN alias. */
    private static final Pattern ALIASED_SCRIPT_TAG =
            Pattern.compile("<script\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    /** An {@code integrity=} or {@code crossorigin=} attribute, either quoting style. */
    private static final Pattern SRI_ATTRIBUTE = Pattern.compile(
            "\\s+(?:integrity|crossorigin)\\s*=\\s*(?:\"[^\"]*\"|'[^']*')",
            Pattern.CASE_INSENSITIVE);

    /** jQuery build actually deployed under the webapp's library path. */
    private static final String DEPLOYED_LIBRARY_JQUERY_PATH = "/library/jquery/jquery-3.7.1.min.js";
    /**
     * Webapp-relative jQuery paths clinic forms pin that this CARLOS no longer ships. Exact
     * versions only: an unknown build must 404 visibly rather than be silently swapped.
     */
    private static final java.util.List<String> SUPERSEDED_LIBRARY_JQUERY_PATHS = java.util.List.of(
            "/library/jquery/jquery-3.6.4.min.js");

    /**
     * Public CDN jQuery URLs observed in the shared-eForm corpus, aliased to the local bundle by
     * {@link #rewriteLegacyRelativeJqueryReferences}. See that method for why this is an alias and
     * not an egress allowance.
     *
     * <p>EXACT full URLs only — adding a host or a prefix pattern here would silently start
     * redirecting scripts nobody has looked at. Both {@code http} and {@code https} spellings are
     * listed because corpus forms use both. Extend only with a URL seen in a real form.</p>
     */
    private static final java.util.List<String> CDN_JQUERY_URLS = java.util.List.of(
            "https://code.jquery.com/jquery-1.7.1.min.js",
            "http://code.jquery.com/jquery-1.7.1.min.js",
            "https://code.jquery.com/jquery-1.12.0.min.js",
            "http://code.jquery.com/jquery-1.12.0.min.js",
            "https://code.jquery.com/jquery-2.2.1.min.js",
            "http://code.jquery.com/jquery-2.2.1.min.js",
            "https://code.jquery.com/jquery-3.7.1.min.js",
            "http://code.jquery.com/jquery-3.7.1.min.js",
            "https://ajax.googleapis.com/ajax/libs/jquery/1.7.1/jquery.min.js",
            "http://ajax.googleapis.com/ajax/libs/jquery/1.7.1/jquery.min.js",
            "https://ajax.googleapis.com/ajax/libs/jquery/1.12.0/jquery.min.js",
            "http://ajax.googleapis.com/ajax/libs/jquery/1.12.0/jquery.min.js",
            "https://ajax.googleapis.com/ajax/libs/jquery/3.7.1/jquery.min.js",
            "http://ajax.googleapis.com/ajax/libs/jquery/3.7.1/jquery.min.js");
    private static final String LOAD_SIG_CALL = "loadSig()";
    // The guard preserves an existing loadSig implementation and supplies a no-op when absent.
    private static final String LOAD_SIG_FALLBACK = "window.loadSig = window.loadSig || function loadSig() {};";
    /**
     * Element/attribute pairs that load a subresource during a render, paired for the viewer-relative
     * re-anchoring in {@link #rewriteViewerRelativeAssetReferences(String)}. Navigation targets
     * ({@code a[href]}, {@code form[action]}) are deliberately absent: they fetch nothing during a
     * render, so rewriting them would mutate clinic-authored links for no rendering benefit.
     */
    private static final String[][] VIEWER_RELATIVE_ASSET_ATTRIBUTES = {
            {"script[src]", "src"},
            {"link[href]", "href"},
            {"img[src]", "src"},
            {"iframe[src]", "src"},
            {"embed[src]", "src"},
            {"source[src]", "src"},
            {"track[src]", "src"},
            {"video[src]", "src"},
            {"audio[src]", "src"},
            {"input[src]", "src"},
            {"object[data]", "data"},
    };

    private String runtimeContextPath;
    /**
     * Limits jsoup DOM normalization to callers that explicitly prepare browser-render HTML.
     */
    private boolean renderNormalizationEnabled;
    /** True once the DOM normalization pass has run for the current formHtml content. */
    private boolean runtimeAssetsNormalized;
    /** Caps normalization failure warnings to one per form HTML generation. */
    private boolean normalizationFailureLogged;

    private static final String EFORM_DEMOGRAPHIC = "eform_demographic";
    private static final String VAR_NAME = "var_name";
    private static final String VAR_VALUE = "var_value";
    private static final String REF_FID = "fid";
    private static final String REF_VAR_NAME = "ref_var_name";
    private static final String REF_VAR_VALUE = "ref_var_value";
    private static final String TABLE_NAME = "table_name";
    private static final String TABLE_ID = "table_id";
    private static final String OTHER_KEY = "other_key";
    private static final String OPENER_VALUE = "link$eform";

    public EForm() {
    }

    public EForm(String fid, String demographicNo) {
        loadEForm(fid, demographicNo);
    }

    public EForm(String fid, String demographicNo, String providerNo) {
        // Loads an uploaded eForm for the selected provider.
        loadEForm(fid, demographicNo);
        this.providerNo = providerNo;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public EForm(String fdid) {
        if (!StringUtils.isBlank(fdid) && !"null".equalsIgnoreCase(fdid)) {
            EFormData eFormData = eFormDataDao().find(Integer.valueOf(fdid));
            if (eFormData != null) {
                this.fdid = fdid;
                this.fid = eFormData.getFormId().toString();
                this.providerNo = eFormData.getProviderNo();
                this.demographicNo = eFormData.getDemographicId().toString();
                this.formName = eFormData.getFormName();
                this.formSubject = eFormData.getSubject();
                this.formDate = eFormData.getFormDate().toString();
                this.formHtml = eFormData.getFormData();
                this.showLatestFormOnly = eFormData.isShowLatestFormOnly();
                this.patientIndependent = eFormData.isPatientIndependent();
                this.roleType = eFormData.getRoleType();
            }
        } else {
            this.formName = "";
            this.formSubject = "";
            this.formHtml = "No Such Form in Database";
        }
    }

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public void loadEForm(String fid, String demographicNo) {
        HashMap loaded = EFormUtil.loadEForm(fid);
        this.fid = fid;
        this.formName = (String) loaded.get("formName");
        this.formHtml = (String) loaded.get("formHtml");
        this.formSubject = (String) loaded.get("formSubject");
        this.formDate = (String) loaded.get("formDate");
        this.formFileName = (String) loaded.get("formFileName");
        this.formCreator = (String) loaded.get("formCreator");
        this.demographicNo = demographicNo;
        // Null-safe unboxing: Boolean.TRUE.equals() returns false for null,
        // avoiding NPE when the eForm is not found and the HashMap is missing these keys.
        // The false default is correct for both fields (show all forms, not patient-independent).
        this.showLatestFormOnly = Boolean.TRUE.equals(loaded.get("showLatestFormOnly"));
        this.patientIndependent = Boolean.TRUE.equals(loaded.get("patientIndependent"));
        this.roleType = (String) loaded.get("roleType");
    }

    public String getAppointmentNo() {
        return this.appointment_no;
    }

    public void setAppointmentNo(String appt_no) {
        this.appointment_no = StringUtils.isBlank(appt_no) ? "-1" : appt_no;
    }

    public String getEformLink() {
        return this.eform_link;
    }

    public void setEformLink(String el) {
        this.eform_link = el;
    }

    public void setAction(String pAjaxId) {
        parentAjaxId = pAjaxId;
        setAction();
    }

    public void setAction() {
        setAction(false);
    }

    public void setAction(boolean unset) {
        // sets action= in the form
        StringBuilder html = new StringBuilder(this.formHtml);
        int index = StringBuilderUtils.indexOfIgnoreCase(html, "<form", 0);
        int endtag = html.indexOf(">", index + 1);
        // Remove existing action, method, and name attributes from the form tag.
        if (index < 0) return;

        int pointer, pointer2;
        while (((pointer = StringBuilderUtils.indexOfIgnoreCase(html, " action=", index)) >= 0) && (pointer < endtag)) {
            pointer2 = nextSpot(html, pointer + 1);
            html = html.delete(pointer, pointer2);
            endtag = html.indexOf(">", index + 1);
        }
        while (((pointer = StringBuilderUtils.indexOfIgnoreCase(html, " method=", index)) >= 0) && (pointer < endtag)) {
            pointer2 = nextSpot(html, pointer + 1);
            html = html.delete(pointer, pointer2);
            endtag = html.indexOf(">", index + 1);
        }
        pointer = StringBuilderUtils.indexOfIgnoreCase(html, " name=", index);
        String name = "name=\"saveEForm\" ";
        if ((pointer >= 0) && (pointer < endtag)) {
            pointer2 = nextSpot(html, pointer + 1);
            endtag = html.indexOf(">", index + 1);
            name = "";
        }
        if (index < 0) return;
        if (unset) {
            this.formHtml = html.toString();
            return;
        }
        index += 5;
        StringBuilder action = new StringBuilder("action=\"../eform/addEForm?efmfid=" + Encode.forUriComponent(this.fid) + "&efmdemographic_no=" + Encode.forUriComponent(this.demographicNo) + "&efmprovider_no=" + Encode.forUriComponent(this.providerNo));
        if (this.eform_link != null) action.append("&eform_link=" + Encode.forUriComponent(this.eform_link));
        if (this.parentAjaxId != null) action.append("&parentAjaxId=" + Encode.forUriComponent(this.parentAjaxId));

        action.append("\"");

        String method = "method=\"POST\"";
        html.insert(index, " " + action.toString() + " " + name + method);
        this.formHtml = html.toString();
    }

    // ------------------Saving the Form (inserting value= statements)---------------------
    public void setValues(ArrayList<String> names, ArrayList<String> values) {
        if (names.size() != values.size()) return;
        StringBuilder html = new StringBuilder(this.formHtml);
        int pointer = -1;
        while ((pointer = getFieldIndex(html, pointer + 1)) >= 0) {
            String fieldHeader = getFieldHeader(html, pointer);
            String fieldName = EFormUtil.removeQuotes(EFormUtil.getAttribute("name", fieldHeader));
            int i;
            if ((i = names.indexOf(fieldName)) < 0) continue;

            String val = values.get(i);
            pointer = nextSpot(html, pointer);

				/*
				 * TODO: Remove the use of pointer completely from this method and update values using the org.jsoup.nodes.Document
				 */
                html = putValue(val, getFieldType(fieldHeader), fieldName, pointer, html);
        }
        this.formHtml = html.toString();
    }

    // ------------------Saving the Form (inserting fdid$value= statements)---------------------
    public void setOpenerValues(ArrayList<String> names, ArrayList<String> values) {
        StringBuilder html = new StringBuilder(this.formHtml);
        EFormLoader.getInstance();
        String opener = EFormLoader.getOpener(); // default: opener: "oscarOPEN="
        int markerLoc = -1;
        while ((markerLoc = getFieldIndex(html, markerLoc + 1)) >= 0) {
            String fieldHeader = getFieldHeader(html, markerLoc);
            if (StringUtils.isBlank(EFormUtil.getAttribute(opener, fieldHeader))) continue;

            String fieldName = EFormUtil.removeQuotes(EFormUtil.getAttribute("name", fieldHeader));
            int i;
            if (StringUtils.isBlank(fieldName)) continue;
            if ((i = names.indexOf(fieldName)) < 0) continue;
            if (StringUtils.isBlank(values.get(i))) continue;

            // sets up the pointer where to write the value
            int pointer = nextSpot(html, markerLoc + EFormUtil.getAttributePos(opener, fieldHeader));
            int offset = EFormUtil.getAttributePos(OPENER_VALUE, fieldHeader);
            if (offset >= 0) {
                //delete current OPENER_VALUE
                pointer = markerLoc + offset;
                int valueEnd = nextSpot(html, pointer);
                html.delete(pointer - 1, valueEnd);
            }

                        /*
                         * TODO: Remove the use of pointer completely from this method and update values using the org.jsoup.nodes.Document
                         */
                        html = putValue(values.get(i), OPENER_VALUE, fieldName, pointer, html);
        }
        formHtml = html.toString();
    }

    // --------------------------Setting APs utilities----------------------------------------
    public void setDatabaseAPs() {
        StringBuilder html = new StringBuilder(this.formHtml);
        EFormLoader.getInstance();
        String marker = EFormLoader.getMarker(); // default: marker: "oscarDB="
        for (int i = 0; i < 2; i++) { // run the following twice if "count"-type field is found
            int markerLoc = -1;
            while ((markerLoc = getFieldIndex(html, markerLoc + 1)) >= 0) {
                log.debug("===============START CYCLE===========");
                String fieldHeader = getFieldHeader(html, markerLoc);
                String apName = EFormUtil.getAttribute(marker, fieldHeader); // gets varname from oscarDB=varname
                if (StringUtils.isBlank(apName)) {
                    if (!setAP2nd) saveFieldValue(html, markerLoc);
                    continue;
                }
                apName = EFormUtil.removeQuotes(apName);

                log.debug("AP ==== {}", LogSafe.sanitize(apName));
                if (setAP2nd && !apName.startsWith("e$")) continue; // ignore non-e$ oscarDB on 2nd run

                int needing = needValueInForm;
                String fieldType = getFieldType(fieldHeader); // textarea, text, hidden etc..
                if ((fieldType == null || fieldType.equals("")) || (apName == null || apName.equals(""))) continue;

                // Position pointer right after the oscardb attribute's closing quote
                // This works for all field types, which then handle insertion differently in putValuesFromAP():
                // - textarea: searches forward for closing > and inserts content inside the tags
                // - select: searches forward for matching option value and adds "selected" attribute
                // - input: directly inserts value="" attribute at this position
                int attributeEndPos = EFormUtil.getAttributeEndPos(marker, fieldHeader);
                if (attributeEndPos == -1) {
                    // Log the marker and its field index only — never fieldHeader, which is the raw
                    // eForm field HTML and can carry a PHI-bearing value attribute.
                    log.error("Failed to find attribute end position for marker: {} at field index: {}",
                            LogSafe.sanitize(marker), markerLoc);
                    continue;
                }
                int pointer = markerLoc + attributeEndPos;
                EFormLoader.getInstance();
                DatabaseAP curAP = EFormLoader.getAP(apName);

                if (curAP == null) curAP = getAPExtra(apName, fieldHeader);
                if (curAP == null) continue;
                if (!setAP2nd) { // 1st run
                    html = putValuesFromAP(curAP, fieldType, pointer, html);
                    saveFieldValue(html, markerLoc);
                } else { // 2nd run
                    if (needing > needValueInForm) html = putValuesFromAP(curAP, fieldType, pointer, html);
                }

                log.debug("Marker ==== {}", markerLoc);
                log.debug("FIELD TYPE ===={}", LogSafe.sanitize(fieldType));
                log.debug("=================End Cycle==============");
            }
            formHtml = html.toString();
            if (needValueInForm > 0) setAP2nd = true;
            else i = 2;
        }
    }

    // --------------------------Setting oscarOPEN behaviours ----------------------------------------
    public void setOscarOPEN(String requestURI) {
        StringBuilder html = new StringBuilder(this.formHtml);
        EFormLoader.getInstance();
        String opener = EFormLoader.getOpener(); // default: opener: "oscarOPEN="
        int markerLoc = -1;
        while ((markerLoc = getFieldIndex(html, markerLoc + 1)) >= 0) {
            log.debug("=============START OPENER CYCLE===========");
            String fieldHeader = getFieldHeader(html, markerLoc);
            String efmName = EFormUtil.getAttribute(opener, fieldHeader); // gets eform name from oscarOPEN=rname
            if (StringUtils.isBlank(efmName)) continue;

            String fieldName = EFormUtil.removeQuotes(EFormUtil.getAttribute("name", fieldHeader));
            if (StringUtils.isBlank(fieldName)) continue;

            log.debug("OPEN ==== {}", LogSafe.sanitize(efmName));
            // sets up the pointer where to write the value
            String fdid = EFormUtil.removeQuotes(EFormUtil.getAttribute(OPENER_VALUE, fieldHeader));
            EFormLoader.getInstance();
            String onclick = EFormLoader.getOpenEform(requestURI, fdid, EFormUtil.removeQuotes(efmName), fieldName, this);
            int pointer = EFormUtil.getAttributePos("onclick", fieldHeader);
            String type = pointer < 0 ? "onclick" : "onclick_append";
            if (pointer < 0) {
                pointer = nextSpot(html, markerLoc + EFormUtil.getAttributePos(opener, fieldHeader));
            } else {
                pointer = nextSpot(html, markerLoc + pointer);
            }

                        /*
                         * TODO: Remove the use of pointer completely from this method and update values using the org.jsoup.nodes.Document
                         */
                        html = putValue(onclick, type, fieldName, pointer, html);

            log.debug("Opener ==== {}", markerLoc);
            log.debug("=================End Opener Cycle==============");
        }
        formHtml = html.toString();
    }

    public void setNowDateTime() {
        this.formTime = UtilDateUtilities.DateToString(new Date(), "HH:mm:ss");
        this.formDate = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");
    }

    /**
     * Applies the active servlet context path to the runtime eForm HTML.
     *
     * <p>The supplied context path is a browser-facing servlet URL prefix (for example {@code /carlos}),
     * not a filesystem path. This method rewrites the library marker, normalizes legacy relative jQuery
     * asset references, and injects an idempotent {@code loadSig} fallback when needed. Stored
     * JavaScript source is preserved.</p>
     *
     * <p><strong>Do not route this value through {@code PathValidationUtils}.</strong> Despite the
     * project-wide rule that request-derived values feeding file operations must be path-validated,
     * this one never reaches the filesystem: it is spliced into browser-facing asset URLs. Filesystem
     * validation would inject OS separators into the URL and reject some valid context paths.</p>
     *
     * <p>A {@code null} context path (no servlet environment) is a no-op. An empty string ({@code ""})
     * is a valid root-context (ROOT.war) deployment and is normalized like any other context path.
     * Leading/trailing whitespace is stripped before use, so a whitespace-only value (never produced
     * by {@code HttpServletRequest.getContextPath()}, which only returns {@code ""} or {@code "/path"},
     * but defended against here regardless) collapses to {@code ""} and is treated as root context
     * rather than being spliced raw into a browser-facing asset URL.</p>
     *
     * @param contextPath servlet context path used to build browser-facing runtime asset URLs;
     *                     {@code ""} (or a whitespace-only value) for a root-context deployment,
     *                     {@code null} to skip normalization
     */
    public void setContextPath(String contextPath) {
        if (contextPath == null) return;
        String strippedContextPath = contextPath.strip();
        String normalizedContextPath = strippedContextPath.endsWith("/")
                ? strippedContextPath.substring(0, strippedContextPath.length() - 1)
                : strippedContextPath;
        this.runtimeContextPath = normalizedContextPath;
        this.runtimeAssetsNormalized = false;
        if (this.formHtml == null) {
            // A numeric fdid with no saved row leaves formHtml null (see the EForm(String) constructor,
            // which only substitutes the "No Such Form in Database" placeholder for a blank/"null" id).
            // Callers detect that case deliberately -- EFormRenderPdfHtmlComposer.buildPdfHtml raises a
            // descriptive IllegalStateException -- so return quietly instead of NPEing here first and
            // burying the real cause. Reachable for a root-context ("") deployment in particular, which
            // no longer short-circuits on the blank-contextPath check above.
            return;
        }
        this.formHtml = this.formHtml.replace(jsMarker, normalizedContextPath + "/library/");
        this.formHtml = rewriteLegacyRelativeJqueryReferences(this.formHtml, normalizedContextPath);
        this.formHtml = injectLoadSigFallback(this.formHtml);
    }

    /**
     * Points known jQuery references at the locally deployed bundle.
     *
     * <p>Covers two families: the legacy relative spellings a clinic form uses when it expects
     * jQuery beside itself in the eForm image directory, and the public CDN URLs that much of the
     * shared-eForm corpus loads jQuery from.</p>
     *
     * <p>The CDN case is a <em>local alias, never an egress allowance</em>. The PDF render browser
     * is deliberately unable to reach any off-origin host — a dead proxy plus a loopback-only bypass
     * list, with CSP {@code script-src 'self'} on top — because it executes clinic-authored content
     * while displaying PHI. Opening a hole for a CDN would mean loosening the proxy bypass (which is
     * {@code host:port} scoped and so cannot be narrowed to a single file), the CSP, and the network
     * gate, and would still leave a top-level-navigation exfiltration path that CSP has no directive
     * to close. Serving our own copy gives these forms the same jQuery with none of that, works
     * offline, and renders deterministically.</p>
     *
     * <p>Matching is EXACT and by full URL, never by host or prefix. An unrecognised third-party
     * script stays untouched and fails visibly at the render gate — the correct outcome for a script
     * nobody has vetted. Note the served bundle is jQuery 3.7.1 plus the compat shim (see
     * {@code EFormAssetDeployer}) regardless of the version a form asked for: the same trade the
     * legacy-filename aliasing already makes.</p>
     */
    private String rewriteLegacyRelativeJqueryReferences(String html, String contextPath) {
        if (StringUtils.isBlank(html)) return html;

        String assetUrl = contextPath + "/eform/displayImage?imagefile=jquery-1.12.0.min.js";
        // Both quoting styles for every spelling: corpus forms use them interchangeably, and a
        // single-quoted src='jquery-1.12.0.min.js' (observed in real packages) previously slipped
        // through and 404'd.
        String rewritten = html;
        for (String legacy : java.util.List.of(LEGACY_JQUERY_SOURCE, LEGACY_JQUERY_DISPLAY_PATH)) {
            rewritten = rewritten
                    .replace(attributeReference(legacy), attributeReference(assetUrl))
                    .replace(singleQuotedAttributeReference(legacy), singleQuotedAttributeReference(assetUrl));
        }
        // A webapp-relative reference to a jQuery build this CARLOS no longer ships. 28 of 199
        // shared-corpus packages pin /library/jquery/jquery-3.6.4.min.js, which 404s and takes the
        // form's scripts down with it. This is CARLOS's own library path, not a third-party host,
        // so pointing it at the deployed build is a version alias rather than a redirect to
        // unvetted code -- but it stays an exact-version match, so an unrecognised build still
        // fails visibly instead of being silently upgraded.
        for (String supersededPath : SUPERSEDED_LIBRARY_JQUERY_PATHS) {
            rewritten = rewritten.replace(supersededPath, DEPLOYED_LIBRARY_JQUERY_PATH);
        }
        boolean aliasedFromCdn = false;
        for (String cdnUrl : CDN_JQUERY_URLS) {
            String before = rewritten;
            rewritten = rewritten
                    .replace(attributeReference(cdnUrl), attributeReference(assetUrl))
                    .replace(singleQuotedAttributeReference(cdnUrl), singleQuotedAttributeReference(assetUrl));
            aliasedFromCdn |= !before.equals(rewritten);
        }
        return aliasedFromCdn ? stripSubresourceIntegrity(rewritten, assetUrl) : rewritten;
    }

    /**
     * Drops {@code integrity}/{@code crossorigin} from script tags whose src we just re-pointed at
     * the local bundle.
     *
     * <p>Corpus forms pin their CDN jQuery with Subresource Integrity, e.g.
     * {@code integrity="sha256-gvQgAFz…" crossorigin="anonymous"}. That hash describes the CDN's
     * bytes, so once the src points at our own bundle the browser finds no valid digest and
     * <em>refuses to execute the script at all</em> — leaving the form worse off than before the
     * alias. The integrity guarantee is not lost, only relocated: the replacement is a local file
     * we ship and serve ourselves, not something fetched over the network.</p>
     *
     * <p>Scoped to tags carrying the alias URL so a form's own SRI on any other resource is left
     * intact.</p>
     */
    private static String stripSubresourceIntegrity(String html, String assetUrl) {
        Matcher scriptTag = ALIASED_SCRIPT_TAG.matcher(html);
        StringBuilder out = new StringBuilder();
        while (scriptTag.find()) {
            String tag = scriptTag.group();
            if (!tag.contains(assetUrl)) {
                scriptTag.appendReplacement(out, Matcher.quoteReplacement(tag));
                continue;
            }
            String cleaned = SRI_ATTRIBUTE.matcher(tag).replaceAll("");
            scriptTag.appendReplacement(out, Matcher.quoteReplacement(cleaned));
        }
        scriptTag.appendTail(out);
        return out.toString();
    }

    /** {@code src="<url>"} — built via format so no value is concatenated between quote literals. */
    private static String attributeReference(String url) {
        return String.format("src=\"%s\"", url);
    }

    /** Single-quoted variant; corpus forms use both spellings. */
    private static String singleQuotedAttributeReference(String url) {
        return String.format("src=%1$s%2$s%1$s", "'", url);
    }

    private String injectLoadSigFallback(String html) {
        if (StringUtils.isBlank(html)) return html;
        if (!html.contains(LOAD_SIG_CALL)) return html;
        String fallback = "<" + SCRIPT_TAG + ">" + LOAD_SIG_FALLBACK + "</" + SCRIPT_TAG + ">";
        int bodyClose = StringUtils.lastIndexOfIgnoreCase(html, "</body>");
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + fallback + html.substring(bodyClose);
        }
        return html + fallback;
    }



    /**
     * Returns eForm HTML after optional render-only DOM normalization of legacy jQuery references
     * and the {@code loadSig} fallback. Normalization failures retain the string-normalized HTML and
     * emit one operator warning per content generation.
     *
     * @return the normalized eForm HTML, or {@code null} when the fdid is numeric but has no saved
     *         row. The {@code "No Such Form in Database"} placeholder covers only a blank or literal
     *         {@code "null"} fdid, so callers must still null-check -- {@code
     *         EFormRenderPdfHtmlComposer.buildPdfHtml} depends on exactly that.
     */
    @Override
    public String getFormHtml() {
        if (renderNormalizationEnabled && runtimeContextPath != null && !runtimeAssetsNormalized) {
            try {
                normalizeLegacyRuntimeAssetsInDocument(runtimeContextPath);
                runtimeAssetsNormalized = true;
            } catch (RuntimeException | LinkageError e) {
                if (!normalizationFailureLogged) {
                    normalizationFailureLogged = true;
                    log.warn("DOM-based eForm runtime normalization failed ({}); using string-level HTML fallback for this form",
                            e.getClass().getSimpleName());
                }
                log.debug("Skipping DOM-based eForm runtime normalization; falling back to string-level HTML", e);
            }
        }
        return super.getFormHtml();
    }

    @Override
    public void setFormHtml(String formHtml) {
        runtimeAssetsNormalized = false;
        normalizationFailureLogged = false;
        super.setFormHtml(formHtml);
    }

    /**
     * Opts this EForm in to the lazy jsoup DOM normalization pass performed by {@link #getFormHtml()}.
     *
     * <p>Only the PDF render/compose path ({@code EFormRenderPdfHtmlComposer}) needs the DOM pass, so
     * it calls this before reading the HTML. Every other caller (interactive display, ZIP export,
     * save-time dedup) reads the string-level-normalized HTML from {@link #setContextPath} without the
     * jsoup round-trip, which keeps jsoup's error-recovery restructuring off the live display paths.</p>
     */
    public void enableRenderNormalization() {
        this.renderNormalizationEnabled = true;
        this.runtimeAssetsNormalized = false; // force a fresh DOM pass on the next getFormHtml()
    }

    private void normalizeLegacyRuntimeAssetsInDocument(String contextPath) {
        rewriteViewerRelativeAssetReferences(contextPath);

        String assetUrl = contextPath + "/eform/displayImage?imagefile=jquery-1.12.0.min.js";
        for (Element script : getDocument().select("script[src]")) {
            String src = script.attr("src").trim();
            if (LEGACY_JQUERY_SOURCE.equals(src) || LEGACY_JQUERY_DISPLAY_PATH.equals(src)) {
                script.attr("src", assetUrl);
            }
        }

        Element body = getDocument().body();
        if (!body.attr("onload").contains(LOAD_SIG_CALL)) return;

        body.appendElement(SCRIPT_TAG).append(LOAD_SIG_FALLBACK);
    }

    /**
     * Re-anchors viewer-relative ({@code ../}) asset references to the webapp context.
     *
     * <p>Stored eForm HTML is authored against the interactive viewer URL
     * ({@code /<context>/eform/efmshowform_data}), which sits two segments below the origin, so a
     * clinic-authored {@code ../css/x.css} resolves to {@code /<context>/css/x.css}. The PDF render
     * page is served one segment below the origin ({@code /<context>/EFormViewForPdfGenerationServlet}),
     * where that same reference resolves to the origin ROOT ({@code /css/x.css}) and 404s. The render
     * then reports missing content for an asset that is present and correctly referenced.</p>
     *
     * <p>This generalizes the per-asset {@code ../share/} and {@code ../eform/displayImage} rewrites the
     * render composer already performs: any leading {@code ../} chain is stripped and the remainder is
     * anchored to the context path. It runs only on the render/compose path (see
     * {@link #enableRenderNormalization()}), so clinic-authored HTML is never rewritten in the
     * database — the interactive viewer keeps resolving the original relative reference itself.</p>
     */
    private void rewriteViewerRelativeAssetReferences(String contextPath) {
        String base = contextPath == null ? "" : contextPath.trim();
        for (String[] selectorAndAttribute : VIEWER_RELATIVE_ASSET_ATTRIBUTES) {
            for (Element element : getDocument().select(selectorAndAttribute[0])) {
                String attribute = selectorAndAttribute[1];
                String rewritten = anchorViewerRelativePath(element.attr(attribute), base);
                if (rewritten != null) {
                    element.attr(attribute, rewritten);
                }
            }
        }
    }

    /**
     * Anchors one viewer-relative reference to {@code contextPath}, or returns {@code null} when the
     * value is not a {@code ../} reference and must be left untouched (absolute URLs, context-rooted
     * paths, {@code data:}/{@code javascript:} values, and same-directory references).
     *
     * @return the rewritten path, or {@code null} to leave the attribute as authored
     */
    static String anchorViewerRelativePath(String value, String contextPath) {
        if (value == null) {
            return null;
        }
        String remainder = value.trim();
        if (!remainder.startsWith("../")) {
            return null;
        }
        // A form authored with more ../ segments than the viewer has depth is already malformed; the
        // clinic's intent is still "the webapp root", so every leading hop collapses to the context.
        while (remainder.startsWith("../")) {
            remainder = remainder.substring(3);
        }
        if (remainder.isEmpty()) {
            return null;
        }
        return contextPath + "/" + remainder;
    }


    public void setFdid(String fdid) {
        if (this.formHtml != null && this.fdidMarker != null && fdid != null) {
            this.formHtml = this.formHtml.replace(fdidMarker, Encode.forHtmlAttribute(fdid));
        }
    }

    public void setSource(String source) {
        if (StringUtils.isBlank(source)) source = "";

        this.formHtml = this.formHtml.replace(sourceMarker, Encode.forHtmlAttribute(source));
    }


    public ArrayList<String> getOpenerNames() {
        ArrayList<String> openerNames = new ArrayList<String>();
        EFormLoader.getInstance();
        String opener = EFormLoader.getOpener(); // default: opener: "oscarOPEN="
        StringBuilder html = new StringBuilder(this.formHtml);
        int markerLoc = -1;
        while ((markerLoc = getFieldIndex(html, markerLoc + 1)) >= 0) {
            String fieldHeader = getFieldHeader(html, markerLoc);
            String efmName = EFormUtil.getAttribute(opener, fieldHeader); // gets eform name from oscarOPEN=rname
            if (StringUtils.isBlank(efmName)) continue;

            String fieldName = EFormUtil.removeQuotes(EFormUtil.getAttribute("name", fieldHeader));
            if (!StringUtils.isBlank(fieldName)) openerNames.add(fieldName);
        }
        return openerNames;
    }

    public String getTemplate() {
        // Get content between "<!-- <template>" & "</template> -->"
        if (StringUtils.isBlank(formHtml)) return "";

        String sTemplateBegin = "<template>";
        String sTemplateEnd = "</template>";
        String sCommentBegin = "<!--", sCommentEnd = "-->";
        String text = "";

        int templateBegin = -1, templateEnd = -1;
        boolean searching = true;
        while (searching) {
            templateBegin = EFormUtil.findIgnoreCase(sTemplateBegin, formHtml, templateBegin + 1);
            templateEnd = EFormUtil.findIgnoreCase(sTemplateEnd, formHtml, templateBegin);
            int commentBegin = formHtml.lastIndexOf(sCommentBegin, templateBegin);
            int commentEnd = formHtml.indexOf(sCommentEnd, commentBegin);
            if (templateBegin == -1 || templateEnd == -1 || commentBegin == -1 || commentEnd == -1) {
                searching = false;
            } else if (commentEnd > templateEnd) {
                text += formHtml.substring(templateBegin + sTemplateBegin.length(), templateEnd);
            }
        }
        return text;
    }

    // ----------------------------------private
    // -----------------------------------------
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private DatabaseAP getAPExtra(String apName, String fieldHeader) {
        // --------------------------Process extra attributes for APs --------------------------------
        Pattern p = Pattern.compile("\\b[a-z]\\$[^ \\$#]+#[^\n]+");
        Matcher m = p.matcher(apName);
        if (!m.matches()) return null;

        String module = apName.substring(0, apName.indexOf("$"));
        String type = apName.substring(apName.indexOf("$") + 1, apName.indexOf("#"));
        String field = apName.substring(apName.indexOf("#") + 1, apName.length());
        DatabaseAP curAP = null;
        if (module.equals("m")) {
            log.debug("SWITCHING TO MEASUREMENTS");
            Hashtable data = EctMeasurementsDataBeanHandler.getLast(this.demographicNo, type);
            if (!data.isEmpty()) {
                curAP = new DatabaseAP();
                curAP.setApName(apName);
                curAP.setApOutput((String) data.get(field));
            }
        } else if (module.equals("e")) {
            log.debug("SWITCHING TO EFORM_VALUES");
            String eform_name = EFormUtil.removeQuotes(EFormUtil.getAttribute("eform$name", fieldHeader));
            String var_value = EFormUtil.removeQuotes(EFormUtil.getAttribute("var$value", fieldHeader));
            String ref = EFormUtil.removeQuotes(EFormUtil.getAttribute("ref$", fieldHeader, true));

            String eform_demographic = this.demographicNo;
            if (this.patientIndependent) eform_demographic = "%";

            String ref_name = null, ref_value = null, ref_fid = fid;
            if (!StringUtils.isBlank(ref) && ref.contains("=")) {
                ref_name = ref.substring(4, ref.indexOf("="));
                ref_value = EFormUtil.removeQuotes(ref.substring(ref.indexOf("=") + 1));
            } else {
                ref_name = StringUtils.isBlank(ref) ? "" : ref.substring(4);
            }
            if (!StringUtils.isBlank(eform_name)) ref_fid = getRefFid(eform_name);
            if ((!StringUtils.isBlank(var_value) && var_value.trim().startsWith("{")) || (!StringUtils.isBlank(ref_value) && ref_value.trim().startsWith("{"))) {
                if (setAP2nd) { // 2nd run, put value in required field
                    var_value = findValueInForm(var_value);
                    ref_value = findValueInForm(ref_value);
                    needValueInForm--;
                } else { // 1st run, note the need to reference other value in form
                    needValueInForm++;
                    return null;
                }
            }

            if (type.equalsIgnoreCase("count") && var_value == null) {
                type = "countname";
            } else if ((type.equalsIgnoreCase("first") || type.equalsIgnoreCase("last")) && field.equals("*")) {
                type += "_all_json";
            }
            if (!ref_name.equals("")) {
                type += "_ref";
                if (ref_value == null) type += "name";
            }

            EFormLoader.getInstance();
            curAP = EFormLoader.getAP("_eform_values_" + type);

            if (curAP != null) {
                setSqlParams(EFORM_DEMOGRAPHIC, eform_demographic);
                setSqlParams(VAR_NAME, field);
                setSqlParams(REF_VAR_NAME, ref_name);
                setSqlParams(VAR_VALUE, var_value);
                setSqlParams(REF_VAR_VALUE, ref_value);
                setSqlParams(REF_FID, ref_fid);
            }
        } else if (module.equals("o")) {
            log.debug("SWITCHING TO OTHER_ID");
            String table_name = "", table_id = "";
            EFormLoader.getInstance();
            curAP = EFormLoader.getAP("_other_id");
            if (type.equalsIgnoreCase("patient")) {
                table_name = OtherIdManager.DEMOGRAPHIC.toString();
                table_id = this.demographicNo;
            } else if (type.equalsIgnoreCase("appointment")) {
                table_name = OtherIdManager.APPOINTMENT.toString();
                table_id = appointment_no;
                if (StringUtils.isBlank(table_id)) table_id = "-1";
            }
            setSqlParams(OTHER_KEY, field);
            setSqlParams(TABLE_NAME, table_name);
            setSqlParams(TABLE_ID, table_id);
        }
        return curAP;
    }

    /*
    * TODO: Remove the use of pointer completely from this method and update values using the org.jsoup.nodes.Document
    */
	private StringBuilder putValue(String value, String type, String fieldName, int pointer, StringBuilder html) {
        // inserts value= into tag or textarea
        if (type.equals("onclick") || type.equals("onclick_append")) {
            if (type.equals("onclick_append")) {
                if (html.charAt(pointer - 1) == '"') pointer -= 1;
                if (html.charAt(pointer - 1) != ';') value = ";" + value;
            } else {
                value = "onclick=\"" + value + "\"";
            }
            html.insert(pointer, " " + value);
        } else if (type.equals(OPENER_VALUE)) {
            html.insert(pointer, " " + OPENER_VALUE + "=\"" + value + "\"");
		} else {
            html = putValueUsingDocument(fieldName, type, value);
        }
        return html;
    }

    private StringBuilder putValueUsingDocument(String fieldName, String type, String value) {
        Element field = getDocument().selectFirst("[name=\"" + TokenQueue.escapeCssIdentifier(fieldName) + "\"]");
        if (field == null) return new StringBuilder(getFormHtml());
        switch (type) {
            case "text":
            case "hidden":
            case "date":
                field.attr("value", value);
                break;
            case "textarea":
                field.text(value);
                break;
            case "checkbox":
                field.attr("checked", "checked");
                break;
            case "radio":
                // For radio buttons, find all radios with the same name and check the correct one based on value
                for (Element radio : getDocument().select("input[type=radio][name=\"" + TokenQueue.escapeCssIdentifier(fieldName) + "\"]")) {
                    if (value.equals(radio.attr("value"))) {
                        radio.attr("checked", "checked");
                    } else {
                        radio.removeAttr("checked");
                    }
                }
                break;
            case "select":
                for (Element option : field.select("option")) {
                    if (option.attr("value").equals(value)) {
                        option.attr("selected", "selected");
                    } else {
                        option.removeAttr("selected");
                    }
                }
                break;
            default:
                // Do nothing for unknown types
        }

		return new StringBuilder(getFormHtml());
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private int nextIndex(StringBuilder text, String option1, String option2, int pointer) {
        // converts text content to lowercase
        text = new StringBuilder(text.toString().toLowerCase());
        option1 = option1.toLowerCase();
        option2 = option2.toLowerCase();

        // returns the index of option1 or option2 whichever one is closer and exists
        int index;
        int option1i = text.indexOf(option1, pointer);
        int option2i = text.indexOf(option2, pointer);
        if (option1i < 0) index = option2i;
        else if (option2i < 0 || option1i < option2i) index = option1i;
        else index = option2i;
        return index;
    }

    private int nextSpot(StringBuilder text, int pointer) {
        //nextSport: \n, \r, >, ' '
        int end = nextIndex(text, "\n", "\r", pointer);
        if (end < 0) end = text.length();
        int index = text.substring(pointer, end).indexOf('=');
        if (index >= 0) {
            index = pointer + index;
            //deal with cases of quoted values with spaces ("xx xx" / 'xx xx')
            if (text.charAt(index + 1) == '"') {
                int close = text.substring(index + 2, end).indexOf("\"") + (index + 2);
                if (close > 0) return close + 1;
            }
            if (text.charAt(index + 1) == '\'') {
                int close = text.substring(index + 2, end).indexOf("'") + (index + 2);
                if (close > 0) return close + 1;
            }
            pointer = index;
        }
        return nextIndex(text, " ", ">", pointer);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private String getFieldType(String fieldHeader) {
        if (fieldHeader.substring(1, 9).equalsIgnoreCase("textarea")) return "textarea";
        if (fieldHeader.substring(1, 7).equalsIgnoreCase("select")) return "select";

        String type = EFormUtil.removeQuotes(EFormUtil.getAttribute("type", fieldHeader));

        if (null == type) {
            // Browsers should default to text if type is missing
            type = "text";
        }

        return type;
    }
/*
	private String getFieldType(StringBuilder html, int pointer) {
		// pointer can be any place in the tag - isolates tag and sends back field type
		int open = html.substring(0, pointer).lastIndexOf("<");
		int close = html.substring(pointer).indexOf(">") + pointer + 1;
		String tag = html.substring(open, close);
		log.debug("TAG ===={}", LogSafe.sanitize(tag));
		int start; // <input type="^text".....
		int end; // <input type="text^"....
		if (tag.substring(1, 9).equalsIgnoreCase("textarea")) return "textarea";
		if (tag.substring(1, 7).equalsIgnoreCase("select")) return "select";

		log.debug("TAG PROCESS ===={}", LogSafe.sanitize(tag.substring(1, 9)));
		if ((start = tag.toLowerCase().indexOf(" type=")) >= 0) {
			start += 6; // account for type=...
			if (tag.charAt(start) == '\"') { // account for type="..."
				start += 1;
				end = tag.indexOf("\"", start);
			} else {
				int nextSpace = tag.indexOf(" ", start);
				int nextBracket = tag.indexOf(">", start);
				if (nextSpace < 0) end = nextBracket;
				else if ((nextBracket < 0) || (nextSpace < nextBracket)) end = nextSpace;
				else end = nextBracket;

			}
			return tag.substring(start, end).toLowerCase();
		}
		return "";
	}
 *
 */

    private StringBuilder putValuesFromAP(DatabaseAP ap, String type, int pointer, StringBuilder html) {
        //prepare all sql & output
        String sql = ap.getApSQL();
        String output = ap.getApOutput();
        if (!StringUtils.isBlank(sql)) {
            ParameterizedSql query;
            try {
                query = parameterizeAllFields(sql);
            } catch (IllegalArgumentException e) {
                log.error("Invalid placeholder value in eForm AP query, skipping: {}", e.getMessage());
                return html;
            }
            log.debug("SQL---- [eform AP query executed]");
            ArrayList<String> names = DatabaseAP.parserGetNames(output); // a list of ${apName} --> apName
            if (ap.isJsonOutput()) {
                ArrayNode values = EFormUtil.getJsonValues(names, query);
                output = values.toString(); //in case of JsonOutput, return the whole JSONArray and let the javascript deal with it
            } else {
                ArrayList<String> values = EFormUtil.getValues(names, query);
                if (values.size() != names.size()) {
                    output = "";
                } else {
                    for (int i = 0; i < names.size(); i++) {
                        output = DatabaseAP.parserReplace(names.get(i), values.get(i), output);
                    }
                }
            }
        }
        //put values into according controls
        if (type.equals("textarea")) {
            pointer = html.indexOf(">", pointer) + 1;
            html.insert(pointer, SafeEncode.forHtml(output));
        } else if (type.equals("select")) {
            int selectEnd = StringBuilderUtils.indexOfIgnoreCase(html, "</select>", pointer);
            if (selectEnd >= 0) {
                int valueLoc = nextIndex(html, " value=" + output, " value=\"" + output, pointer);
                if (valueLoc < 0 || valueLoc > selectEnd) return html;
                pointer = nextSpot(html, valueLoc);
                html = html.insert(pointer, " selected");
            }
        } else { //type=input
            html.insert(pointer, " value=\"" + SafeEncode.forHtmlAttribute(output) + "\"");
        }
        return (html);
    }

    /**
     * Converts legacy DatabaseAP placeholders that can contain request or form
     * state into JDBC bind parameters. This keeps the admin-authored SQL shape
     * intact while preventing values from being concatenated into the query text.
     */
    public ParameterizedSql parameterizeAllFields(String sql) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("demographic", requireDigitsOnly("demographic", demographicNo));
        replacements.put("appt_no", requireDigitsOnly("appt_no", appointment_no));
        String eformDemographic = getSqlParams(EFORM_DEMOGRAPHIC);
        validateDigitsOrWildcard(EFORM_DEMOGRAPHIC, eformDemographic);
        replacements.put(EFORM_DEMOGRAPHIC, eformDemographic);
        replacements.put(REF_FID, requireDigitsOnly(REF_FID, getSqlParams(REF_FID)));
        replacements.put(TABLE_ID, requireDigitsOnly(TABLE_ID, getSqlParams(TABLE_ID)));
        replacements.put("provider", stringParam(providerNo));
        replacements.put("providers", stringParam(providerNo));
        replacements.put(VAR_NAME, stringParam(getSqlParams(VAR_NAME)));
        replacements.put(VAR_VALUE, stringParam(getSqlParams(VAR_VALUE)));
        replacements.put(REF_VAR_NAME, stringParam(getSqlParams(REF_VAR_NAME)));
        replacements.put(REF_VAR_VALUE, stringParam(getSqlParams(REF_VAR_VALUE)));
        replacements.put(TABLE_NAME, stringParam(getSqlParams(TABLE_NAME)));
        replacements.put(OTHER_KEY, stringParam(getSqlParams(OTHER_KEY)));
        return DatabaseAP.parameterizeSql(sql, replacements);
    }

    /**
     * Validates that a value intended for an unquoted numeric SQL placeholder
     * contains only digits (with optional leading minus sign for negative IDs).
     * Throws {@link IllegalArgumentException} if the value is non-numeric and non-empty,
     * preventing injection in unquoted contexts like {@code WHERE id = ${placeholder}}.
     *
     * @param placeholderName the name of the placeholder (for error messages)
     * @param value           the value to validate
     * @return the original value if valid
     * @throws IllegalArgumentException if the value contains non-numeric characters
     */
    private static String requireDigitsOnly(String placeholderName, String value) {
        if (value == null || value.isEmpty()) return value;
        if (!value.matches("-?\\d+")) {
            throw new IllegalArgumentException("Non-numeric value for placeholder: " + placeholderName);
        }
        return value;
    }

    private static void validateDigitsOrWildcard(String placeholderName, String value) {
        if (!"%".equals(value)) {
            requireDigitsOnly(placeholderName, value);
        }
    }

    private static String stringParam(String value) {
        return value == null ? "" : value;
    }

    private String getSqlParams(String key) {
        if (sql_params.containsKey(key)) {
            String val = sql_params.get(key);
            return val == null ? "" : val;
        }
        return "";
    }

    private void setSqlParams(String key, String value) {
        if (sql_params.containsKey(key)) sql_params.remove(key);
        sql_params.put(key, value);
    }

    private String findValueInForm(String name) {
        // name format = {xxx}
        if (StringUtils.isBlank(name) || !name.trim().startsWith("{") || !name.trim().endsWith("}")) return name;

        // extract content from brackets {}
        name = name.trim().substring(1, name.trim().length() - 1).toLowerCase();
        if (StringUtils.isBlank(name)) return "";

        String value = fieldValues.get(name);
        return value == null ? "" : value;
    }

    private int getFieldIndex(StringBuilder html, int from) {
        if (html == null) return -1;
        Pattern p = Pattern.compile("<input|<select|<textarea|<div");
        Matcher matcher = p.matcher(html);
        if (matcher.find(from)) {
            int start = matcher.start();
            return start;
        } else {
            return -1;
        }


		/*  Code too slow, replaced with regex
		if (html == null) return -1;

		Integer[] index = new Integer[4];
		index[0] = StringBuilderUtils.indexOfIgnoreCase(html, "<input", from);
		index[1] = StringBuilderUtils.indexOfIgnoreCase(html, "<select", from);
		index[2] = StringBuilderUtils.indexOfIgnoreCase(html, "<textarea", from);
                index[3] = StringBuilderUtils.indexOfIgnoreCase(html, "<div", from);

		ArrayList<Integer> list = new ArrayList<Integer>();
		for (int i = 0; i < index.length; i++)
			if (index[i] >= 0) list.add(index[i]);

		int min = list.isEmpty() ? -1 : list.get(0);
		for (int i = 1; i < list.size(); i++)
			min = min > list.get(i) ? list.get(i) : min;

		return min;
		*/
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private String getFieldName(StringBuilder html, int pointer) {
        //pointer can be any place in the tag - isolates tag and sends back field type
        int open = html.substring(0, pointer).lastIndexOf("<");
        int close = html.substring(pointer).indexOf(">") + pointer + 1;
        String tag = html.substring(open, close);
        log.debug("TAG ===={}", LogSafe.sanitize(tag));
        int start;  //<input type="^text".....
        int end;    //<input type="text^"....
        if ((start = tag.toLowerCase().indexOf(" name=")) >= 0) {
            start += 6;
            if (tag.charAt(start) == '"') {
                start += 1;
                end = tag.indexOf('"', start);
            } else if (tag.charAt(start) == '\'') {
                start += 1;
                end = tag.indexOf('\'', start);
            } else {
                int nextSpace = tag.indexOf(" ", start);
                int nextBracket = tag.indexOf(">", start);
                if (nextSpace < 0) end = nextBracket;
                else if ((nextBracket < 0) || (nextSpace < nextBracket)) end = nextSpace;
                else end = nextBracket;
            }

            return tag.substring(start, end);
        } else {
            return "";
        }
    }

    private String getFieldHeader(String html, int fieldIndex) {
        StringBuilder sb_html = new StringBuilder(html);
        return getFieldHeader(sb_html, fieldIndex);
    }

    private String getFieldHeader(StringBuilder html, int fieldIndex) {
        //fieldHeader: <input name=... type=... ... >, <select name=... ...>, etc.
        if (html == null || fieldIndex < 0) return "";
        if (html.charAt(fieldIndex) != '<') return ""; // field header must be "<...>"

        // look for char '>' which is NOT inside quotes ("..." or '...')
        int end = fieldIndex;
        boolean quoteOpen = false, quote2Open = false;
        for (int i = fieldIndex + 1; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '"' && !quoteOpen) quote2Open = !quote2Open;
            if (c == '\'' && !quote2Open) quoteOpen = !quoteOpen;
            if (!quoteOpen && !quote2Open && c == '>') {
                end = i + 1;
                break;
            }
        }
        return html.substring(fieldIndex, end);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private void saveFieldValue(StringBuilder html, int fieldIndex) {
        String header = getFieldHeader(html, fieldIndex);
        if (StringUtils.isBlank(header)) return;

        String name = EFormUtil.removeQuotes(EFormUtil.getAttribute("name", header));
        String value = EFormUtil.removeQuotes(EFormUtil.getAttribute("value", header));
        if (StringUtils.isBlank(name)) return;

        if (header.toLowerCase().startsWith("<input ")) {
            String type = EFormUtil.removeQuotes(EFormUtil.getAttribute("type", header));
            if (StringUtils.isBlank(type)) return;

            if (type.equalsIgnoreCase("radio")) {
                String checked = EFormUtil.removeQuotes(EFormUtil.getAttribute("checked", header));
                if (StringUtils.isBlank(checked) || !checked.equalsIgnoreCase("checked")) return;
            }
        } else if (header.toLowerCase().startsWith("<select ")) {
            String selects = html.substring(fieldIndex, html.indexOf("</select>", fieldIndex));
            int pos = selects.indexOf("<option ", 0);
            while (pos >= 0) {
                String option = getFieldHeader(selects, pos);
                String selected = EFormUtil.removeQuotes(EFormUtil.getAttribute("selected", option));
                if (!StringUtils.isBlank(selected) && selected.equalsIgnoreCase("selected")) {
                    value = EFormUtil.removeQuotes(EFormUtil.getAttribute("value", option));
                    break;
                }
                pos = selects.indexOf("<option ", pos + 1);
            }
        } else if (header.toLowerCase().startsWith("<textarea ")) {
            int fieldEnd = html.indexOf("</textarea>", fieldIndex);
            value = html.substring(fieldIndex + header.length(), fieldEnd).trim();
            if (value.startsWith("\n")) value = value.substring(1); // remove 1st line break, UNIX style
            else if (value.startsWith("\r\n")) value = value.substring(2); // remove 1st line break, WINDOWS style
        }
        name = name.toLowerCase();
        if (!StringUtils.isBlank(value)) fieldValues.put(name, value);
    }

    private String getRefFid(String eform_name) {
        if (StringUtils.isBlank(eform_name)) return fid;

        String refFid = EFormUtil.getEFormIdByName(eform_name);
        if (StringUtils.isBlank(refFid)) refFid = fid;
        return refFid;
    }

    // FindSecBugs IMPROPER_UNICODE: case-fold in a trust path; locale-safe hardening tracked in #2496. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold in a trust path; locale-safe hardening tracked in #2496")
    public void setSignatureCode(String contextPath, String userAgent, String demographicNo, String providerId) {
        String signatureRequestId = DigitalSignatureUtils.generateSignatureRequestId(providerId);
        String imageUrl = contextPath + "/imageRenderingServlet?source=" + ImageRenderingServlet.Source.signature_preview.name() + "&" + DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY + "=" + signatureRequestId;
        String storedImgUrl = contextPath + "/imageRenderingServlet?source=" + ImageRenderingServlet.Source.signature_stored.name() + "&digitalSignatureId=";

        StringBuilder html = new StringBuilder(this.formHtml);
        int signatureLoc = StringBuilderUtils.indexOfIgnoreCase(html, signatureMarker, 0);

        if (signatureLoc > -1) {
            String browserType = "";
            if (userAgent != null) {
                if (userAgent.toLowerCase().indexOf("ipad") > -1) {
                    browserType = "IPAD";
                } else {
                    browserType = "ALL";
                }
            }

            // Encode dynamic values for safe embedding into JavaScript string literals
            String jsSignatureRequestId = Encode.forJavaScript(signatureRequestId);
            String jsImageUrl = Encode.forJavaScript(imageUrl);
            String jsStoredImgUrl = Encode.forJavaScript(storedImgUrl);
            String jsContextPath = Encode.forJavaScript(contextPath);
            String jsSignatureRequestIdKey = Encode.forJavaScript(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY);
            String jsBrowserType = Encode.forJavaScript(browserType);
            String jsDemographicNo = Encode.forJavaScript(demographicNo);

            String signatureCode = "<script type='text/javascript' src='oscar/library/jquery/jquery-3.7.1.min.js'></script>" +
                    "<script type='text/javascript' src='${oscar_javascript_path}signature.js'></script>" +
                    "<script type='text/javascript'>\n" +
                    "var _signatureRequestId = '" + jsSignatureRequestId + "';\n" +
                    "var _imageUrl = '" + jsImageUrl + "';\n" +
                    "var _storedImgUrl = '" + jsStoredImgUrl + "';\n" +
                    "var _contextPath = '" + jsContextPath + "';\n" +
                    "var _digitalSignatureRequestIdKey = '" + jsSignatureRequestIdKey + "';\n" +
                    "var _browserType = '" + jsBrowserType + "';\n" +
                    "var _demographicNo = '" + jsDemographicNo + "';\n" +
                    "</script>";


            html.replace(signatureLoc, signatureLoc + signatureMarker.length(), signatureCode);
            this.formHtml = html.toString();
        }
    }

    /**
     * For overriding or adding Javascript to every eform
     * Add path to Javascript resource in OSCAR source code.
     */
    public void addHeadJavascript(String javascriptPath) {
        Element script = getDocument().createElement(SCRIPT_TAG);
        script.attr("type", "text/javascript");
        script.attr("src", javascriptPath);
        addHeadElement(script);
    }

    /**
     * Adds javascript files to the end of the body tag.
     * Useful if there is a dependency on previous javascript in the window load
     */
    public void addBodyJavascript(String javascriptPath) {
        Element script = getDocument().createElement(SCRIPT_TAG);
        script.attr("type", "text/javascript");
        script.attr("src", javascriptPath);
        addBodyElement(script);
    }

    /* For overriding or adding CSS to every eform
     * Add path to CSS resource in OSCAR source code.
     */
    public void addCSS(String cssPath, String mediaType) {
        Element link = getDocument().createElement("link");
        link.attr(ConvertToEdoc.ElementAttribute.href.name(), cssPath);
        link.attr(ConvertToEdoc.ElementAttribute.rel.name(), "stylesheet");
        link.attr(ConvertToEdoc.ElementAttribute.media.name(), mediaType);
        link.attr(ConvertToEdoc.ElementAttribute.type.name(), "text/css");
        addHeadElement(link);
    }

    /*
     * Adds use of custom font library such as
     */
    public void addFontLibrary(String fontPath) {
        String stringBuilder = "@font-face { font-family: dejavu; src: url('" + fontPath + "'); }";
        Element style = getDocument().createElement("style");
        style.text(stringBuilder);
        addHeadElement(style);
    }

	private void addHeadElement(Element element) {
		Element headElement = getDocument().head();
		Iterator<Element> iterator = headElement.children().iterator();
        if (iterator.hasNext()) {
            Element beforeElement;

            while (iterator.hasNext()) {
                beforeElement = iterator.next();

                // always after the meta and title tags.
                if (! beforeElement.nameIs("title") && ! beforeElement.nameIs("meta")
                && ! beforeElement.nameIs("style")) {
                    beforeElement.before(element);
                    break;
                } else {
                    headElement.prependChild(element);
                }

            }

        } else {
            headElement.appendChild(element);
        }
	}

    public void addHiddenAttachments(List<String> attachedDocumentIds, List<String> attachedEFormIds, List<String> attachedHRMDocumentIds, List<String> attachedLabIds, List<EctFormData.PatientForm> attachedForms) {
        for (String docId : attachedDocumentIds) {
            addHiddenInputElement("delegate_docNo" + docId, "docNo", "delegateAttachment", docId, null);
        }

        for (String eformId : attachedEFormIds) {
            addHiddenInputElement("delegate_eFormNo" + eformId, "eFormNo", "delegateAttachment", eformId, null);
        }

        for (String hrmId : attachedHRMDocumentIds) {
            addHiddenInputElement("delegate_hrmNo" + hrmId, "hrmNo", "delegateAttachment", hrmId, null);
        }

        for (String labId : attachedLabIds) {
            addHiddenInputElement("delegate_labNo" + labId, "labNo", "delegateAttachment", labId, null);
        }

        for (EctFormData.PatientForm form : attachedForms) {
            Map<String, String> additionalProperties = new HashMap<>();
            additionalProperties.put("data-formName", form.getFormName());
            additionalProperties.put("data-formDate", form.getEdited());
            addHiddenInputElement("entry_formNo" + form.getFormId(), null, "delegateOldFormAttachment", null, additionalProperties);
            addHiddenInputElement("delegate_formNo" + form.getFormId(), "formNo", "delegateAttachment", form.getFormId(), null);
        }
    }

    public void addHiddenInputElement(String id, String value) {
        addHiddenInputElement(id, null, null, value, null);
    }

    public void addHiddenInputElement(String id, String name, String className, String value, Map<String, String> additionalProperties) {
        Element input = getDocument().createElement("input");

        input.attr(ConvertToEdoc.ElementAttribute.type.name(), "hidden");

        if (id != null && !id.isEmpty()) {
            input.attr(ConvertToEdoc.ElementAttribute.id.name(), Encode.forHtmlAttribute(id));
        }

        if (name != null && !name.isEmpty()) {
            input.attr(ConvertToEdoc.ElementAttribute.name.name(), Encode.forHtmlAttribute(name));
        }

        if (value != null && !value.isEmpty()) {
            input.attr(ConvertToEdoc.ElementAttribute.value.name(), Encode.forHtmlAttribute(value));
        }

        if (className != null && !className.isEmpty()) {
            input.attr("class", Encode.forHtmlAttribute(className));
        }

        if (additionalProperties != null) {
            Set<Map.Entry<String, String>> properties = additionalProperties.entrySet();
            for (Map.Entry<String, String> property : properties) {
                input.attr(Encode.forHtmlAttribute(property.getKey()), Encode.forHtmlAttribute(property.getValue()));
            }
        }

        addBodyElement(input);
    }

    private void addBodyElement(Element element) {
        Element bodyElement = getDocument().body();

        // check if this element doesnt pre-exist first
        Element existing = null;
        String elementId = element.id();
        if (elementId != null && !elementId.isEmpty()) {
            existing = bodyElement.getElementById(elementId);
        }
        if (existing != null) {
            existing.remove();
        }
        bodyElement.appendChild(element);
    }

    /**
     * Part 3 of "counter hack for a hack" initialized in Javascript file
     * eform_floating_toolbar.js
     * This method fetches image paths stored in hidden fields and restores them
     * into empty image src values.
     * Empty image src values are the result of using Javascript in the eForm to dynamically
     * set paths for images.
     */
    // FindSecBugs IMPROPER_UNICODE: case-fold in a trust path; locale-safe hardening tracked in #2496. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold in a trust path; locale-safe hardening tracked in #2496")
    public void addImagePathPlaceholders(String[] imagePathPlaceholders)
        throws JsonProcessingException, JsonMappingException {
        if (imagePathPlaceholders != null && imagePathPlaceholders.length > 0) {
            Elements imageElements = getDocument().getElementsByTag("img");
            for (String jsonString : imagePathPlaceholders) {
                JsonNode parsedNode = objectMapper.readTree(jsonString);
                if (parsedNode.isObject()) {
                    ObjectNode placeHolder = (ObjectNode) parsedNode;
                    JsonNode idNode = placeHolder.get("id");
                    JsonNode valueNode = placeHolder.get("value");

                    if (idNode != null && valueNode != null) {
                        String id = idNode.asText();
                        String value = valueNode.asText();

                        if (!id.isEmpty() && !value.isEmpty() && !value.toLowerCase().startsWith("http")) {
                            for (Element imageElement : imageElements) {
                                if (id.equalsIgnoreCase(imageElement.id())) {
                                    imageElement.attr("src", value);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
