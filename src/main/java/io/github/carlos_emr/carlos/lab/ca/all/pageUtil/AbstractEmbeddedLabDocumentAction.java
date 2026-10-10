/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 *
 * The inline lab PDF display is adapted from open-osp/Open-O commits
 * 4b5a3d62e6 and b63af33e90 (GPL); this CARLOS implementation adds the
 * per-OBX detection, PDF signature check and size handling below.
 */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Serves the PDF embedded in one HL7 {@code ED} OBX of a lab. Subclasses choose only the
 * {@code Content-Disposition} and whether the preview size limit applies.
 *
 * <p>Request parameters: {@code labNo} (the {@code hl7TextMessage} id), {@code segment} (OBR
 * group index) and {@code group} (OBX index within it), all non-negative integers of at most nine
 * digits. {@code legacy=true} is accepted for links built before the legacy PATHL7 shape was
 * detected server-side, and refused on any other lab type.</p>
 *
 * <p>Contract, in order; nothing is written to the body until every check has passed:</p>
 * <ol>
 *   <li>Methods other than GET and HEAD: 405 with {@code Allow: GET, HEAD}.</li>
 *   <li>No {@code _lab} read: {@link SecurityException}.</li>
 *   <li>Bad parameters, or {@code legacy=true} on a non-PATHL7 lab: 400.</li>
 *   <li>A lab matched to a patient also needs {@code _lab} and {@code _demographic} read scoped to
 *       that patient, or {@link SecurityException}. An unmatched lab keeps the global
 *       {@code _lab} check, as the inbox does.</li>
 *   <li>Route turned off (the inline preview preference), unknown lab, OBR/OBX index out of range, an OBX that is not {@code ED}, or an empty
 *       payload: 404.</li>
 *   <li>Decoded content that is not a PDF ({@code %PDF-}): 415.</li>
 *   <li>A PDF over the preview limit (inline only): 413.</li>
 *   <li>A GET whose read audit record cannot be persisted: 500, before any PDF header.</li>
 *   <li>Otherwise {@code application/pdf} with {@code nosniff}, {@code no-store} and a
 *       restrictive per-response CSP; a GET writes the read audit record, then the bytes.</li>
 * </ol>
 *
 * <p>Error statuses are set without a body (and without {@code sendError}, which would render the
 * HTML error page into a PDF frame). Every path returns {@link #NONE}.</p>
 *
 * @since 2026-09-30
 */
public abstract class AbstractEmbeddedLabDocumentAction extends ActionSupport {

    /** Audit {@code content} of the read record written for each served document. */
    public static final String AUDIT_CONTENT = "LabEmbeddedDocument";

    /**
     * Per-response policy for the PDF itself. The browser renders the PDF in its own viewer; the
     * policy keeps anything else in the response from running as a CARLOS page ({@code sandbox}
     * gives the document an opaque origin, {@code default-src 'none'} loads nothing), and
     * {@code frame-ancestors 'self'} lets only CARLOS pages frame it. Chromium's PDF viewer still
     * renders under this policy (checked by {@code scripts/lab-embedded-pdf-playwright-checks.js}).
     * The {@code %PDF-} check and {@code nosniff} are what stop a crafted payload from being
     * rendered as HTML in the first place.
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'self'; sandbox";

    // At most nine digits, so Integer.valueOf below cannot overflow (max 999,999,999).
    private static final Pattern INDEX = Pattern.compile("\\d{1,9}");

    private static final Logger logger = MiscUtils.getLogger();

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient Hl7TextMessageDao hl7TextMessageDao;
    private final transient PatientLabRoutingDao patientLabRoutingDao;

    protected AbstractEmbeddedLabDocumentAction(SecurityInfoManager securityInfoManager,
            Hl7TextMessageDao hl7TextMessageDao, PatientLabRoutingDao patientLabRoutingDao) {
        this.securityInfoManager = securityInfoManager;
        this.hl7TextMessageDao = hl7TextMessageDao;
        this.patientLabRoutingDao = patientLabRoutingDao;
    }

    /** {@code "inline"} or {@code "attachment"}. */
    protected abstract String disposition();

    /** The largest PDF served, in bytes; {@code 0} or less for no limit. */
    protected abstract long maxBytes();

    /** Whether the route is available at all; checked after authorization. */
    protected boolean enabled() {
        return true;
    }

    // FindSecBugs HRS_REQUEST_PARAMETER_TO_HTTP_HEADER: labNo reaches the Content-Disposition filename only
    // after parsing as an int, so the header carries digits the server formatted, never request text.
    @SuppressFBWarnings(value = "HRS_REQUEST_PARAMETER_TO_HTTP_HEADER",
            justification = "labNo is parsed to an int and re-formatted before it reaches the Content-Disposition filename")
    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        String method = request.getMethod();
        boolean head = "HEAD".equals(method);
        if (!"GET".equals(method) && !head) {
            response.setHeader("Allow", "GET, HEAD");
            return status(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        Integer labNo = index(request.getParameter("labNo"));
        Integer segment = index(request.getParameter("segment"));
        Integer group = index(request.getParameter("group"));
        String legacy = request.getParameter("legacy");
        // segment/group are zero-based indexes, but labNo is an hl7TextMessage primary key: 0 is
        // never a stored identity, so refuse it before either DAO is queried.
        if (labNo == null || labNo <= 0 || segment == null || group == null
                || (legacy != null && !"true".equals(legacy) && !"false".equals(legacy))) {
            return status(response, HttpServletResponse.SC_BAD_REQUEST);
        }

        String demographicNo = authorizeMatchedPatient(loggedInInfo, labNo);
        if (!enabled()) {
            return status(response, HttpServletResponse.SC_NOT_FOUND);
        }

        Hl7TextMessage message = hl7TextMessageDao.find(labNo.intValue());
        MessageHandler handler = message == null ? null : handler(message);
        if (handler == null) {
            return status(response, HttpServletResponse.SC_NOT_FOUND);
        }
        if ("true".equals(legacy) && !(handler instanceof PATHL7Handler)) {
            return status(response, HttpServletResponse.SC_BAD_REQUEST);
        }
        if (segment >= handler.getOBRCount() || group >= handler.getOBXCount(segment)
                || !handler.isOBXEmbeddedDocument(segment, group)) {
            return status(response, HttpServletResponse.SC_NOT_FOUND);
        }

        // HEAD writes no body, so it classifies through the non-retaining inspect() (a streaming
        // byte count) rather than load(): the download has no size cap, and a HEAD must not cost a
        // full in-memory copy of a large report just to answer with headers.
        EmbeddedLabDocumentLoader.Document document = null;
        EmbeddedLabDocumentLoader.Status status;
        long contentLength;
        if (head) {
            EmbeddedLabDocumentLoader.Inspection inspection =
                    EmbeddedLabDocumentLoader.inspect(handler, segment, group, maxBytes());
            status = inspection.status();
            contentLength = inspection.sizeBytes();
        } else {
            document = EmbeddedLabDocumentLoader.load(handler, segment, group, maxBytes());
            status = document.status();
            contentLength = document.bytes() == null ? 0 : document.bytes().length;
        }
        switch (status) {
            case EMPTY:
                return status(response, HttpServletResponse.SC_NOT_FOUND);
            case NOT_PDF:
            case TEXT:
                logger.warn("Refused embedded lab document that is not a PDF: labNo={}", LogSafe.sanitize(String.valueOf(labNo)));
                return status(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            case TOO_LARGE:
                return status(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            case PDF:
            default:
                break;
        }

        // Audit the read before any header or body is written (direct-response contract), with
        // the strict variant: the best-effort LogAction.addLog never throws, so a failed audit
        // would otherwise still serve the PDF. An unaudited read is refused with a bare 500, like
        // every other error path here (no sendError, whose HTML page would land in the frame).
        if (!head) {
            try {
                LogAction.addLogStrict(loggedInInfo, LogConst.READ, AUDIT_CONTENT, String.valueOf(labNo), demographicNo,
                        "segment=" + segment + ",group=" + group + ",disposition=" + disposition());
            } catch (RuntimeException e) {
                logger.error("Refused embedded lab document: the read audit failed for labNo={}",
                        LogSafe.sanitize(String.valueOf(labNo)), e);
                return status(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        response.setContentType("application/pdf");
        response.setContentLengthLong(contentLength);
        response.setHeader("Content-Disposition", disposition() + "; filename=\"Lab-" + labNo.intValue() + ".pdf\"");
        noStore(response);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        if (head) {
            return NONE;
        }

        OutputStream output = response.getOutputStream();
        output.write(document.bytes()); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- verified %PDF- bytes served as application/pdf with nosniff
        output.flush();
        return NONE;
    }

    /**
     * Parses the lab through {@link Factory}; package-private so a unit test can substitute a
     * handler without a lab-type configuration.
     */
    MessageHandler handler(Hl7TextMessage message) {
        return Factory.getHandler(message);
    }

    /**
     * Requires patient-scoped read access when the lab is matched to one or more patients.
     *
     * @return the first matched demographic number for the audit record, or {@code null} when
     *         the lab is not matched
     */
    private String authorizeMatchedPatient(LoggedInInfo loggedInInfo, int labNo) {
        List<PatientLabRouting> routings = patientLabRoutingDao.findByLabNoAndLabType(labNo, "HL7");
        String auditDemographicNo = null;
        for (PatientLabRouting routing : routings) {
            Integer demographicNo = routing.getDemographicNo();
            if (demographicNo == null || demographicNo <= 0) {
                continue;
            }
            String demographic = String.valueOf(demographicNo);
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", demographic)) {
                throw new SecurityException("missing required sec object (_lab)");
            }
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", demographic)) {
                throw new SecurityException("missing required sec object (_demographic)");
            }
            if (auditDemographicNo == null) {
                auditDemographicNo = demographic;
            }
        }
        return auditDemographicNo;
    }

    private static Integer index(String value) {
        return value != null && INDEX.matcher(value).matches() ? Integer.valueOf(value) : null;
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }

    private String status(HttpServletResponse response, int status) {
        noStore(response);
        response.setStatus(status);
        return NONE;
    }
}
