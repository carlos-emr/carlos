//CHECKSTYLE:OFF
package ca.openosp.openo.encounter.oceanEReferal.pageUtil;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.EReferAttachmentDao;
import ca.openosp.openo.commn.model.EReferAttachment;
import ca.openosp.openo.commn.model.EReferAttachmentData;
import ca.openosp.openo.commn.model.enumerator.DocumentType;
import ca.openosp.openo.documentManager.DocumentAttachmentManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for managing Ocean eReferral consultation attachments.
 * <p>
 * This action handles the attachment and editing of medical documents (including clinical documents,
 * lab results, eforms, and hospital report manager records) to Ocean eReferral consultation requests.
 * Ocean eReferral is an integrated healthcare referral management system used in Ontario for
 * electronic specialist referrals. This action specifically manages the association of internal
 * OpenO EMR documents with outgoing referral requests.
 * </p>
 * <p>
 * The action supports two primary operations via method-based routing:
 * </p>
 * <ul>
 *   <li><b>attachOceanEReferralConsult</b> - Creates new eReferral attachment records linking
 *       internal documents to a demographic (patient) for referral purposes</li>
 *   <li><b>editOceanEReferralConsult</b> - Updates existing consultation requests by attaching
 *       additional documents organized by document type</li>
 * </ul>
 * <p>
 * Document attachments are categorized by type using single-character prefixes:
 * </p>
 * <ul>
 *   <li><b>D</b> - Clinical documents (Document Manager)</li>
 *   <li><b>L</b> - Laboratory results</li>
 *   <li><b>E</b> - Electronic forms (eForms)</li>
 *   <li><b>H</b> - Hospital Report Manager records</li>
 * </ul>
 * <p>
 * This is a 2Action implementation following OpenO EMR's Struts2 migration pattern,
 * coexisting with legacy Struts 1.x actions during the framework transition.
 * </p>
 *
 * @see EReferAttachment
 * @see EReferAttachmentData
 * @see DocumentAttachmentManager
 * @see EReferAttachmentDao
 * @since 2026-01-24
 */
public class ERefer2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);

    /**
     * Main execution method for this Struts2 action.
     * <p>
     * Routes incoming requests to the appropriate handler method based on the "method" request parameter.
     * Supports method-based routing pattern common in OpenO EMR's 2Action implementations.
     * </p>
     * <p>
     * Supported method parameter values:
     * </p>
     * <ul>
     *   <li><code>attachOceanEReferralConsult</code> - Routes to {@link #attachOceanEReferralConsult()}</li>
     *   <li><code>editOceanEReferralConsult</code> - Routes to {@link #editOceanEReferralConsult()}</li>
     * </ul>
     *
     * @return String always returns {@link ActionSupport#SUCCESS} regardless of routing or execution outcome
     */
    public String execute() {
        String method = request.getParameter("method");

        if (method != null) {
            if (method.equalsIgnoreCase("attachOceanEReferralConsult"))
                attachOceanEReferralConsult();
            else if (method.equalsIgnoreCase("editOceanEReferralConsult"))
                editOceanEReferralConsult();
        }

        return SUCCESS;
    }

    /**
     * Creates new eReferral attachment records for Ocean consultation requests.
     * <p>
     * This method processes document attachments selected from the consultation request window's
     * attachment GUI. All documents originate from OpenO EMR's internal document management system
     * and are associated with a specific patient (demographic) for inclusion in an outgoing
     * Ocean eReferral.
     * </p>
     * <p>
     * The method expects a pipe-delimited (|) string of document identifiers in the format
     * <code>{type}{id}</code>, where:
     * </p>
     * <ul>
     *   <li><b>type</b> - Single character prefix indicating document type (D/L/E/H)</li>
     *   <li><b>id</b> - Numeric identifier for the specific document</li>
     * </ul>
     * <p>
     * Example document string: <code>D123|L456|E789</code> would attach document 123,
     * lab result 456, and eform 789.
     * </p>
     * <p>
     * Upon successful creation, the generated eReferral attachment ID is written to the
     * HTTP response output stream for client-side processing.
     * </p>
     * <p>
     * <b>Request Parameters:</b>
     * </p>
     * <ul>
     *   <li><code>demographicNo</code> - String representation of the patient's demographic number (required)</li>
     *   <li><code>documents</code> - Pipe-delimited String of document identifiers in {type}{id} format (required)</li>
     * </ul>
     * <p>
     * If either required parameter is missing or empty, the method returns silently without
     * processing or error reporting.
     * </p>
     * <p>
     * <b>Response:</b> Writes the newly created EReferAttachment ID as a String to the HTTP response
     * </p>
     */
    public void attachOceanEReferralConsult() {
        String demographicNo = StringUtils.isNullOrEmpty(request.getParameter("demographicNo")) ? "" : request.getParameter("demographicNo");
        String documents = StringUtils.isNullOrEmpty(request.getParameter("documents")) ? "" : request.getParameter("documents");
        if (documents.isEmpty() || demographicNo.isEmpty()) {
            return;
        }

        EReferAttachment eReferAttachment = new EReferAttachment(Integer.parseInt(demographicNo));
        List<EReferAttachmentData> attachments = new ArrayList<>();

        for (String document : documents.split("\\|")) {
            String type = document.replaceAll("\\d", "");
            Integer id = Integer.parseInt(document.substring(type.length()));
            EReferAttachmentData attachmentData = new EReferAttachmentData(eReferAttachment, id, type);
            attachments.add(attachmentData);
        }
        eReferAttachment.setAttachments(attachments);
        EReferAttachmentDao eReferAttachmentDao = SpringUtils.getBean(EReferAttachmentDao.class);
        eReferAttachmentDao.persist(eReferAttachment);

        try (PrintWriter writer = response.getWriter()) {
            writer.write(eReferAttachment.getId().toString());
        } catch (IOException e) {
            logger.error("Failed to write the eReferAttachment ID to the response", e);
        }
    }

    /**
     * Updates an existing Ocean eReferral consultation request by attaching additional documents.
     * <p>
     * This method processes document attachments from the consultation request window's attachment GUI
     * and associates them with an existing consultation request. Documents are organized by type
     * and attached using the {@link DocumentAttachmentManager} to ensure proper linkage with the
     * consultation, provider, and patient records.
     * </p>
     * <p>
     * The method parses a pipe-delimited (|) string of document identifiers, categorizes them by
     * type prefix, and then performs batch attachment operations for each document category:
     * </p>
     * <ul>
     *   <li><b>D</b> - Clinical documents (DOC) from Document Manager</li>
     *   <li><b>L</b> - Laboratory results (LAB)</li>
     *   <li><b>E</b> - Electronic forms (EFORM)</li>
     *   <li><b>H</b> - Hospital Report Manager records (HRM)</li>
     * </ul>
     * <p>
     * Example document string: <code>D123|D456|L789|E101</code> would attach two clinical documents,
     * one lab result, and one eform to the consultation.
     * </p>
     * <p>
     * All attachments are marked as active (Boolean.TRUE) and associated with the logged-in provider,
     * specified consultation request, and patient demographic.
     * </p>
     * <p>
     * <b>Request Parameters:</b>
     * </p>
     * <ul>
     *   <li><code>demographicNo</code> - String representation of the patient's demographic number (required)</li>
     *   <li><code>requestId</code> - String representation of the consultation request ID (required)</li>
     *   <li><code>documents</code> - Pipe-delimited String of document identifiers in {type}{id} format (required)</li>
     * </ul>
     * <p>
     * If any required parameter is missing or empty, the method returns silently without
     * processing or error reporting.
     * </p>
     * <p>
     * <b>Session Requirements:</b> Requires valid {@link LoggedInInfo} in HTTP session to identify
     * the provider performing the attachment operation.
     * </p>
     */
    public void editOceanEReferralConsult() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String demographicNo = StringUtils.isNullOrEmpty(request.getParameter("demographicNo")) ? "" : request.getParameter("demographicNo");
        String requestId = StringUtils.isNullOrEmpty(request.getParameter("requestId")) ? "" : request.getParameter("requestId");
        String documents = StringUtils.isNullOrEmpty(request.getParameter("documents")) ? "" : request.getParameter("documents");
        if (documents.isEmpty() || demographicNo.isEmpty() || requestId.isEmpty()) {
            return;
        }

        List<String> docs = new ArrayList<>();
        List<String> labs = new ArrayList<>();
        List<String> eforms = new ArrayList<>();
        List<String> hrms = new ArrayList<>();

        for (String document : documents.split("\\|")) {
            String type = document.replaceAll("\\d", "");
            switch (type) {
                case "D":
                    docs.add(document.substring(type.length()));
                    break;
                case "L":
                    labs.add(document.substring(type.length()));
                    break;
                case "E":
                    eforms.add(document.substring(type.length()));
                    break;
                case "H":
                    hrms.add(document.substring(type.length()));
                    break;
            }
        }

        documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.DOC, docs.toArray(new String[0]), providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo), Boolean.TRUE);
        documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.LAB, labs.toArray(new String[0]), providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo), Boolean.TRUE);
        documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.EFORM, eforms.toArray(new String[0]), providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo), Boolean.TRUE);
        documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.HRM, hrms.toArray(new String[0]), providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo), Boolean.TRUE);
    }
}
