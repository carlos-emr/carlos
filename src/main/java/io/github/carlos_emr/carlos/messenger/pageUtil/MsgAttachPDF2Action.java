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


package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.pageUtil.MsgPdfAttachmentResolver.Attachment;
import io.github.carlos_emr.carlos.messenger.pageUtil.MsgPdfAttachmentResolver.Item;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver;
import io.github.carlos_emr.carlos.util.Doc2PDF;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Renders a patient's chart items to PDF for the Messenger attachment chooser
 * ({@code messenger/Doc2PDF}): either streams one item back as a preview, or stores every
 * ticked item on the compose session's {@link MsgSessionBean} for the message being written.
 *
 * <p><b>The request names items, never HTML.</b> The chooser posts the patient
 * ({@code demographic_no}) and item keys ({@code item}, or {@code previewItem} with
 * {@code isPreview=true}); {@link MsgPdfAttachmentResolver} maps each key to a fixed internal
 * route, which is included in this request and captured, and the captured page is what becomes
 * the PDF. This replaces the legacy design in which the browser loaded each page into a hidden
 * frame and posted its {@code innerHTML} back as {@code srcText}: that let any {@code _msg}
 * writer store arbitrary markup as a "chart PDF", and the front-door WAF refused the posted page
 * (it carries its own {@code <script>} blocks) so the feature 403'd on packaged installs
 * (carlos-emr/carlos#4133). Nothing here needs a WAF exclusion.</p>
 *
 * <p><b>Authorization.</b> {@code _msg} write and POST, as before; access to the named patient's
 * record; and, per item, read on the security object that item's route requires
 * ({@link Item#securityObject()}). The per-item check runs here, before the include, because an
 * included gate action that refuses would render the error page into the capture and that page
 * would be stored as the "PDF".</p>
 *
 * @since 2005
 * @see MsgPdfAttachmentResolver
 * @see MsgSessionBean
 */
public class MsgAttachPDF2Action extends ActionSupport {

    /** Renders an application route inside the current request and returns its HTML. */
    @FunctionalInterface
    interface RouteRenderer {
        String render(HttpServletRequest request, HttpServletResponse response, String route)
                throws ServletException, IOException;
    }

    /** HTML-to-PDF conversion, separated so the action can be unit tested without a renderer. */
    interface PdfConverter {
        void streamPdf(HttpServletRequest request, HttpServletResponse response, String html);

        String toBase64Pdf(HttpServletRequest request, HttpServletResponse response, String html);
    }

    private static final Logger logger = MiscUtils.getLogger();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final SecurityInfoManager securityInfoManager;
    private final DemographicManager demographicManager;
    private final MsgPdfAttachmentResolver resolver;
    private final RouteRenderer routeRenderer;
    private final PdfConverter pdfConverter;

    public MsgAttachPDF2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(DemographicManager.class),
                new MsgPdfAttachmentResolver(SpringUtils.getBean(EChartDao.class)),
                (req, res, route) -> new FormTransportContainer(res, new IncludedViewRequest(req, route), route).getHTML(),
                new PdfConverter() {
                    @Override
                    public void streamPdf(HttpServletRequest req, HttpServletResponse res, String html) {
                        Doc2PDF.parseString2PDF(req, res, html);
                    }

                    @Override
                    public String toBase64Pdf(HttpServletRequest req, HttpServletResponse res, String html) {
                        return Doc2PDF.parseString2Bin(req, res, html);
                    }
                });
    }

    MsgAttachPDF2Action(SecurityInfoManager securityInfoManager, DemographicManager demographicManager,
                        MsgPdfAttachmentResolver resolver, RouteRenderer routeRenderer, PdfConverter pdfConverter) {
        this.securityInfoManager = securityInfoManager;
        this.demographicManager = demographicManager;
        this.resolver = resolver;
        this.routeRenderer = routeRenderer;
        this.pdfConverter = pdfConverter;
    }

    /**
     * Previews one item, or attaches every ticked item.
     *
     * @return {@link #SUCCESS} after attaching (closes the chooser and refreshes compose), or
     *         {@link #NONE} when the response was written here (a PDF preview or an error status)
     * @throws IOException if an error status cannot be written
     * @throws SecurityException if the user lacks {@code _msg} write, access to the patient, or
     *         read on an item's security object
     */
    @Override
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "w", null)) {
            logger.warn("MsgAttachPDF2Action denied: provider {} lacks _msg write", providerNo(loggedInInfo));
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Preview streams PHI and attach mutates the compose session, so both are POST-only.
        if (!"POST".equals(request.getMethod())) {
            logger.warn("MsgAttachPDF2Action refused {} from provider {}",
                    LogSafe.sanitize(request.getMethod()), providerNo(loggedInInfo));
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        int demographicNo = parsePositiveInt(demographicNoParam);
        if (demographicNo <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A patient is required");
            return NONE;
        }
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("missing required patient access");
        }
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, String.valueOf(demographicNo));
        if (demographic == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Patient not found");
            return NONE;
        }
        String patientName = demographic.getLastName() + ", " + demographic.getFirstName();
        ResourceBundle labels = MsgPdfAttachmentResolver.labels(request.getLocale());

        if (isPreview) {
            return preview(loggedInInfo, demographicNo, patientName, labels);
        }
        return attach(loggedInInfo, demographicNo, patientName, labels);
    }

    private String preview(LoggedInInfo loggedInInfo, int demographicNo, String patientName, ResourceBundle labels)
            throws IOException {
        Optional<Attachment> attachment = Item.fromKey(previewItem)
                .flatMap(item -> resolver.resolve(item, demographicNo, patientName, labels));
        if (attachment.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown attachment item");
            return NONE;
        }
        requireItemPrivilege(loggedInInfo, attachment.get().item(), demographicNo);
        String html = render(attachment.get(), demographicNo, loggedInInfo);
        if (html == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "PDF generation failed");
            return NONE;
        }
        pdfConverter.streamPdf(request, response, html);
        return NONE;
    }

    private String attach(LoggedInInfo loggedInInfo, int demographicNo, String patientName, ResourceBundle labels)
            throws IOException {
        MsgSessionBean bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");
        if (bean == null) {
            // The chooser is opened from a compose window, which creates the bean; without one
            // there is no message to attach to.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No message is being composed");
            return NONE;
        }

        // Resolve and authorize every ticked item before touching the bean, so a refusal leaves
        // the message's existing attachments as they were. Unknown keys are ignored, duplicates
        // collapse, and items render in the chooser's order.
        Set<Item> selected = EnumSet.noneOf(Item.class);
        if (items != null) {
            for (String key : items) {
                Item.fromKey(key).ifPresent(selected::add);
            }
        }
        for (Item item : selected) {
            requireItemPrivilege(loggedInInfo, item, demographicNo);
        }

        // Render every item first and only then replace the set, so a failure part-way through
        // (an exception from a lookup, say) leaves the message's existing attachments as they were.
        List<String[]> rendered = new ArrayList<>();
        for (Item item : selected) {
            Optional<Attachment> attachment = resolver.resolve(item, demographicNo, patientName, labels);
            if (attachment.isEmpty()) {
                continue;
            }
            String html = render(attachment.get(), demographicNo, loggedInInfo);
            String pdf = html == null ? null : pdfConverter.toBase64Pdf(request, response, html);
            rendered.add(new String[]{pdf, attachment.get().title()});
        }

        // Attach replaces the message's chart-PDF set with exactly what is ticked now (ticking
        // nothing clears it). Only the PDFs: transferred chart items attached another way stay.
        // The counter numbers each entry's FILE_ID (0, 1, ...), which the recipient's viewer uses
        // to tell the stored PDFs apart; it is reset once the set is complete, as before.
        bean.nullPDFAttachment();
        for (String[] entry : rendered) {
            // A failed render is still recorded (status BAD, "(N/A)" title) so the sender sees it;
            // empty content, not null, or the stored <CONTENT> would read "null".
            bean.setAppendPDFAttachment(entry[0] == null ? "" : entry[0], entry[1]);
            bean.setCurrentAttachmentCount(bean.getCurrentAttachmentCount() + 1);
        }
        bean.setCurrentAttachmentCount(0);
        return SUCCESS;
    }

    /**
     * Read on the item's module, both globally and for this patient (a patient-specific
     * restriction on, say, {@code _rx} must refuse the item here rather than surface as a failed
     * render once the included gate redirects).
     */
    private void requireItemPrivilege(LoggedInInfo loggedInInfo, Item item, int demographicNo) {
        if (!canReadItem(securityInfoManager, loggedInInfo, item, demographicNo)) {
            throw new SecurityException("missing required sec object (" + item.securityObject() + ")");
        }
    }

    /**
     * Whether the user may read one chart item for one patient: read on the item's own security
     * object, both globally and for this patient. The chooser page uses the same rule to decide
     * which rows it offers (and whether it may even look up the encounter), so it never shows an
     * item, or metadata about one, that this action would refuse.
     *
     * @param securityInfoManager the privilege service
     * @param loggedInInfo the acting user
     * @param item the chart item
     * @param demographicNo the patient
     * @return {@code true} when both the global and the patient-specific read check pass
     */
    public static boolean canReadItem(SecurityInfoManager securityInfoManager, LoggedInInfo loggedInInfo,
                                      Item item, int demographicNo) {
        return securityInfoManager.hasPrivilege(loggedInInfo, item.securityObject(), SecurityInfoManager.READ, null)
                && securityInfoManager.hasPrivilege(loggedInInfo, item.securityObject(), SecurityInfoManager.READ,
                        demographicNo);
    }

    /**
     * Includes the item's route and returns its HTML, or {@code null} if it did not render.
     * The route never comes from the request: it is built by {@link MsgPdfAttachmentResolver}
     * from an item key and the validated patient number.
     */
    private String render(Attachment attachment, int demographicNo, LoggedInInfo loggedInInfo) {
        try {
            if (attachment.item() == Item.PRESCRIPTIONS) {
                // The drug-profile page reads this patient's Rx session bean; make sure the
                // session holds one for THIS patient (#3875), as the chooser page also does.
                RxSessionBeanResolver.ensure(request, demographicNo, loggedInInfo.getLoggedInProviderNo());
            }
            return routeRenderer.render(request, response, attachment.route());
        } catch (ServletException | IOException | RuntimeException e) {
            // No PHI: the item kind only, never the rendered content or the patient.
            logger.error("Could not render Messenger PDF attachment item {}", attachment.item().key(), e);
            return null;
        }
    }

    /**
     * Presents the include to the item's route as a plain GET of that route: the method reads
     * {@code GET} and the only parameters visible are the route's own query parameters. The
     * item pages are read-only views behind GET-only gates (the encounter print's
     * {@code ViewClinical2Action} refuses anything else, because some gated JSPs act on a
     * self-POST); without this the outer POST would be refused with 405, and this action's own
     * parameters would leak into the included page.
     *
     * <p>The parameters are parsed from the route here rather than left to the container's
     * include merge: Tomcat inserts its include wrapper beneath application wrappers, so this
     * wrapper is what the included page actually reads.</p>
     */
    static final class IncludedViewRequest extends HttpServletRequestWrapper {
        private final Map<String, String[]> parameters;

        IncludedViewRequest(HttpServletRequest request, String route) {
            super(request);
            this.parameters = Collections.unmodifiableMap(queryParameters(route));
        }

        private static Map<String, String[]> queryParameters(String route) {
            Map<String, java.util.List<String>> collected = new java.util.LinkedHashMap<>();
            int query = route == null ? -1 : route.indexOf('?');
            if (query >= 0) {
                for (String pair : route.substring(query + 1).split("&")) {
                    if (pair.isEmpty()) {
                        continue;
                    }
                    int eq = pair.indexOf('=');
                    String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
                    String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                    collected.computeIfAbsent(name, key -> new java.util.ArrayList<>()).add(value);
                }
            }
            Map<String, String[]> result = new java.util.LinkedHashMap<>();
            collected.forEach((name, values) -> result.put(name, values.toArray(new String[0])));
            return result;
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parameters;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = parameters.get(name);
            return values == null ? null : values.clone();
        }
    }

    /** The acting provider's number for a security log line, sanitized; "unknown" without a session. */
    private static String providerNo(LoggedInInfo loggedInInfo) {
        return loggedInInfo == null ? "unknown" : LogSafe.sanitize(loggedInInfo.getLoggedInProviderNo());
    }

    private static int parsePositiveInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(parsed, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String demographicNoParam;
    private String[] items;
    private String previewItem;
    private boolean isPreview = false;

    public String getDemographic_no() {
        return demographicNoParam;
    }

    /** @param demographicNo the patient whose chart items are rendered */
    @StrutsParameter
    public void setDemographic_no(String demographicNo) {
        this.demographicNoParam = demographicNo;
    }

    public String[] getItem() {
        return items == null ? null : items.clone();
    }

    /** @param items keys of the ticked items ({@link Item#key()}) to attach */
    @StrutsParameter
    public void setItem(String[] items) {
        this.items = items == null ? null : items.clone();
    }

    public String getPreviewItem() {
        return previewItem;
    }

    /** @param previewItem key of the single item to preview */
    @StrutsParameter
    public void setPreviewItem(String previewItem) {
        this.previewItem = previewItem;
    }

    public boolean isPreview() {
        return isPreview;
    }

    /** @param preview {@code true} to stream one item back instead of attaching */
    @StrutsParameter
    public void setIsPreview(boolean preview) {
        isPreview = preview;
    }
}
