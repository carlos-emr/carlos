//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.documentManager.actions;

import ca.openosp.OscarProperties;
import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.commn.model.*;
import com.itextpdf.text.pdf.PdfReader;
import com.sun.pdfview.PDFFile;
import com.sun.pdfview.PDFPage;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jpedal.PdfDecoder;
import org.jpedal.fonts.FontMappings;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.caisi_integrator.IntegratorFallBackManager;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicDocument;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicDocumentContents;
import ca.openosp.openo.caisi_integrator.ws.DemographicWs;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdIntegerCompositePk;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteLink;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.documentManager.EDoc;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.documentManager.IncomingDocUtil;
import ca.openosp.openo.managers.ProgramManager2;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.encounter.data.EctProgram;
import ca.openosp.openo.lab.ca.on.LabResultData;
import ca.openosp.openo.util.UtilDateUtilities;
import org.apache.commons.io.FilenameUtils;
import org.owasp.encoder.Encode;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;

/**
 * @author jaygallagher
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ManageDocument2Action extends ActionSupport {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final Logger log = MiscUtils.getLogger();

    private final DocumentDao documentDao = SpringUtils.getBean(DocumentDao.class);
    private final CtlDocumentDao ctlDocumentDao = SpringUtils.getBean(CtlDocumentDao.class);
    private final ProviderInboxRoutingDao providerInboxRoutingDAO = SpringUtils.getBean(ProviderInboxRoutingDao.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final String DOCUMENT_DIR = OscarProperties.getInstance().getDocumentDirectory();
    private static final String DOCUMENT_CACHE_DIR = OscarProperties.getInstance().getDocumentCacheDirectory();

    private static final Map<String, ActionHandler> ACTIONS = new HashMap<>();

    // Static initializer used to set the actions of the map for the execute function
    static {
        ACTIONS.put("refileDocumentAjax", ctx -> ctx.refileDocumentAjax());
        ACTIONS.put("viewDocPage", ctx -> { ctx.viewDocPage(); return null; });
        ACTIONS.put("display", ctx -> { ctx.display(); return null; });
        ACTIONS.put("viewAnnotationAcknowledgementTickler", ctx -> { ctx.viewAnnotationAcknowledgementTickler(); return null; });
        ACTIONS.put("viewDocumentDescription", ctx -> { ctx.viewDocumentDescription(); return null; });
        ACTIONS.put("viewIncomingDocPageAsPdf", ctx -> { ctx.viewIncomingDocPageAsPdf(); return null; });
        ACTIONS.put("viewIncomingDocPageAsImage", ctx -> { ctx.viewIncomingDocPageAsImage(); return null; });
        ACTIONS.put("displayIncomingDocs", ctx -> { ctx.displayIncomingDocs(); return null; });
        ACTIONS.put("documentUpdate", ctx -> { ctx.documentUpdate(); return null; });
        ACTIONS.put("documentUpdateAjax", ctx -> { ctx.documentUpdateAjax(); return null; });
        ACTIONS.put("getDemoNameAjax", ctx -> { ctx.getDemoNameAjax(); return null; });
        ACTIONS.put("showPage", ctx -> { ctx.showPage(); return null; });
        ACTIONS.put("view", ctx -> { ctx.view(); return null; });
        ACTIONS.put("addIncomingDocument", ctx -> ctx.addIncomingDocument());
        //  Enable calling the method to remove providers
        ACTIONS.put("removeLinkFromDocument", new ActionHandler() {
            public String handle(ManageDocument2Action action) {
                action.removeLinkFromDocument();
                return null;
            }
        });
        ACTIONS.put("viewDocumentInfo", ctx -> { ctx.viewDocumentInfo(); return null; });
    }

    // Called on default by struts.xml, finds the correct method to use by finding what the URL "method" param is equal to
    public String execute() {
        String method = request.getParameter("method");
        ActionHandler handler = ACTIONS.get(method);

        if (handler != null) {
            try {
                return handler.handle(this);
            } catch (Exception e) {
                log.error("Error in " + method + "():", e);
                addActionError("An error occurred while processing the document. Please try again or contact your system administrator.");
                return "error";
            }
        }

        // Only call documentUpdate() if no method is specified and required parameters are present
        if (method == null || method.trim().isEmpty()) {
            String documentId = request.getParameter("documentId");
            String documentDescription = request.getParameter("documentDescription");

            // Check if this looks like a documentUpdate request
            if (documentId != null || documentDescription != null) {
                return documentUpdate();
            }
        }

        log.error("No valid method found and insufficient parameters for documentUpdate. Method: " + method);
        addActionError("Invalid request. The requested operation could not be performed.");
        return "error";
    }

    // Functional interface for our handlers.
    @FunctionalInterface
    private interface ActionHandler {
        String handle(ManageDocument2Action ctx) throws Exception;
    }

    public void documentUpdateAjax() {
        String observationDate = request.getParameter("observationDate");// :2008-08-22<
        String documentDescription = request.getParameter("documentDescription");// :test2<
        String documentId = request.getParameter("documentId");// :29<
        String docType = request.getParameter("docType");// :consult<
        String demog = request.getParameter("demog");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_DOCUMENT, documentId, request.getRemoteAddr(), demog);

        String[] flagproviders = request.getParameterValues("flagproviders");
        // String demoLink=request.getParameter("demoLink");

        // TODO: if demoLink is "on", check if msp is in flagproviders, if not save to providerInboxRouting, if yes, don't save.

        // DONT COPY THIS !!!
        if (flagproviders != null && flagproviders.length > 0) { // TODO: THIS NEEDS TO RUN THRU THE lab forwarding rules!
            try {
                for (String proNo : flagproviders) {
                    // Sanitize provider number to prevent any potential header injection
                    // Provider numbers should only contain alphanumeric characters, hyphens, and underscores
                    if (proNo != null && proNo.matches("^[a-zA-Z0-9_-]+$")) {
                        providerInboxRoutingDAO.addToProviderInbox(proNo, Integer.parseInt(documentId), LabResultData.DOCUMENT);
                    } else {
                        log.warn("Invalid provider number format: " + (proNo != null ? proNo.replaceAll("[\r\n]", "") : "null"));
                    }
                }

                // Removes the link to the "0" providers so that the document no longer shows up as "unclaimed"
                providerInboxRoutingDAO.removeLinkFromDocument("DOC", Integer.parseInt(documentId), "0");
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }

        //Check to see if we have to route document to patient
        PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
        List<PatientLabRouting> patientLabRoutingList = patientLabRoutingDao.findByLabNoAndLabType(Integer.parseInt(documentId), docType);
        if (patientLabRoutingList == null || patientLabRoutingList.size() == 0) {
            PatientLabRouting patientLabRouting = new PatientLabRouting();
            patientLabRouting.setDemographicNo(Integer.parseInt(demog));
            patientLabRouting.setLabNo(Integer.parseInt(documentId));
            patientLabRouting.setLabType("DOC");
            patientLabRoutingDao.persist(patientLabRouting);
        }


        Document d = documentDao.getDocument(documentId);

        if (d != null) {
            d.setDocdesc(documentDescription);
            d.setDoctype(docType);
            Date obDate = UtilDateUtilities.StringToDate(observationDate);

            if (obDate != null) {
                d.setObservationdate(obDate);
            }

            documentDao.merge(d);
        }


        try {

            CtlDocument ctlDocument = ctlDocumentDao.getCtrlDocument(Integer.parseInt(documentId));
            int demographicNumber = Integer.parseInt(demog);
            // If this ctlDocument is a document module type and is not for the demographic being saved then create a new entry and remove the old one
            if (ctlDocument != null && (ctlDocument.isDemographicDocument() && demographicNumber != ctlDocument.getId().getModuleId())) {

                CtlDocument matchedCtlDocument = new CtlDocument();
                matchedCtlDocument.getId().setDocumentNo(ctlDocument.getId().getDocumentNo());
                matchedCtlDocument.getId().setModule(ctlDocument.getId().getModule());
                matchedCtlDocument.getId().setModuleId(Integer.parseInt(demog));
                matchedCtlDocument.setStatus(ctlDocument.getStatus());

                ctlDocumentDao.persist(matchedCtlDocument);

                ctlDocumentDao.remove(ctlDocument.getId());

                // save a document created note
                if (ctlDocument.isDemographicDocument()) {
                    // save note
                    saveDocNote(request, d.getDocdesc(), demog, documentId);
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        HashMap hm = new HashMap();
        hm.put("patientId", demog);
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }

    }

    public void getDemoNameAjax() {
        String dn = request.getParameter("demo_no");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", dn)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        HashMap hm = new HashMap();
        hm.put("demoName", getDemoName(LoggedInInfo.getLoggedInInfoFromSession(request), dn));
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    public void removeLinkFromDocument() {
        String docType = request.getParameter("docType");
        String docId = request.getParameter("docId");
        String providerNo = request.getParameter("providerNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        providerInboxRoutingDAO.removeLinkFromDocument(docType, Integer.parseInt(docId), providerNo);
        HashMap hm = new HashMap();
        hm.put("linkedProviders", providerInboxRoutingDAO.getProvidersWithRoutingForDocument(docType, Integer.parseInt(docId)));

        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    public String refileDocumentAjax() {

        String documentId = request.getParameter("documentId");
        String queueId = request.getParameter("queueId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        try {
            EDocUtil.refileDocument(documentId, queueId);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return null;
    }

    public String documentUpdate() {
        String observationDate = request.getParameter("observationDate");// :2008-08-22<
        String documentDescription = request.getParameter("documentDescription");// :test2<
        String documentId = request.getParameter("documentId");// :29<
        // Also check for doc_no parameter (used by display method URLs)
        if (documentId == null || documentId.trim().isEmpty()) {
            documentId = request.getParameter("doc_no");
        }
        String docType = request.getParameter("docType");// :consult<

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        if (documentId == null || documentId.trim().isEmpty()) {
            log.error("Document ID is null or empty, cannot process document update");
            addActionError("Document ID is missing. Cannot process document update.");
            return "error";
        }

        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_DOCUMENT, documentId, request.getRemoteAddr());

        String demog = request.getParameter("demog");

        String[] flagproviders = request.getParameterValues("flagproviders");
        // String demoLink=request.getParameter("demoLink");

        // TODO: if demoLink is "on", check if msp is in flagproviders, if not save to providerInboxRouting, if yes, don't save.

        // DONT COPY THIS !!!
        if (flagproviders != null && flagproviders.length > 0) { // TODO: THIS NEEDS TO RUN THRU THE lab forwarding rules!
            try {
                for (String proNo : flagproviders) {
                    // Sanitize provider number to prevent any potential header injection
                    // Provider numbers should only contain alphanumeric characters
                    if (proNo != null && proNo.matches("^[a-zA-Z0-9_-]+$")) {
                        providerInboxRoutingDAO.addToProviderInbox(proNo, Integer.parseInt(documentId), LabResultData.DOCUMENT);
                    } else {
                        log.warn("Invalid provider number format: " + (proNo != null ? proNo.replaceAll("[\r\n]", "") : "null"));
                    }
                }
            } catch (NumberFormatException e) {
                log.error("Invalid document ID format: " + documentId, e);
                addActionError("Invalid document ID format. Please check the document ID and try again.");
                return "error";
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }
        Document d = documentDao.getDocument(documentId);

        if (d != null) {
            d.setDocdesc(documentDescription);
            d.setDoctype(docType);
            Date obDate = UtilDateUtilities.StringToDate(observationDate);

            if (obDate != null) {
                d.setObservationdate(obDate);
            }

            documentDao.merge(d);
        }

        if (documentId != null && !documentId.trim().isEmpty()) {
            try {
                CtlDocument ctlDocument = ctlDocumentDao.getCtrlDocument(Integer.parseInt(documentId));
                if (ctlDocument != null) {
                    if (demog != null && !demog.trim().isEmpty()) {
                        ctlDocument.getId().setModuleId(Integer.parseInt(demog));
                        ctlDocumentDao.merge(ctlDocument);
                        // save a document created note
                        if (ctlDocument.isDemographicDocument() && d != null) {
                            // save note
                            saveDocNote(request, d.getDocdesc(), demog, documentId);
                        }
                    } else {
                        log.warn("Demographics parameter is null or empty, skipping ctlDocument update");
                    }
                }
            } catch (NumberFormatException e) {
                log.error("Invalid number format for documentId: " + documentId + " or demog: " + demog, e);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        } else {
            log.warn("Document ID is null or empty, skipping ctlDocument operations");
        }

        String providerNo = request.getParameter("providerNo");
        String searchProviderNo = request.getParameter("searchProviderNo");
        String ackStatus = request.getParameter("status");
        String demoName = getDemoName(LoggedInInfo.getLoggedInInfoFromSession(request), demog);
        request.setAttribute("demoName", demoName);
        request.setAttribute("segmentID", documentId);
        request.setAttribute("providerNo", providerNo);
        request.setAttribute("searchProviderNo", searchProviderNo);
        request.setAttribute("status", ackStatus);

        return "displaySingleDoc";

    }

    private String getDemoName(LoggedInInfo loggedInInfo, String demog) {
        // Get demographic name based on login info and the demographic number of patient
        return EDocUtil.getDemographicName(loggedInInfo, demog);
    }

    private void saveDocNote(final HttpServletRequest request, String docDesc, String demog, String documentId) {

        Date now = EDocUtil.getDmsDateTimeAsDate();
        // String docDesc=d.getDocdesc();
        CaseManagementNote cmn = new CaseManagementNote();
        cmn.setUpdate_date(now);
        cmn.setObservation_date(now);
        cmn.setDemographic_no(demog);
        HttpSession se = request.getSession();
        String user_no = (String) se.getAttribute("user");
        String prog_no = new EctProgram(se).getProgram(user_no);
        WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(se.getServletContext());
        CaseManagementManager cmm = (CaseManagementManager) ctx.getBean(CaseManagementManager.class);
        cmn.setProviderNo("-1");// set the providers no to be -1 so the editor appear as 'System'.
        Provider provider = EDocUtil.getProvider(user_no);
        String provFirstName = "";
        String provLastName = "";
        if (provider != null) {
            provFirstName = provider.getFirstName();
            provLastName = provider.getLastName();
        }
        String strNote = "Document" + " " + docDesc + " " + "created at " + now + " by " + provFirstName + " " + provLastName + ".";

        // String strNote="Document"+" "+docDesc+" "+ "created at "+now+".";
        cmn.setNote(strNote);
        cmn.setSigned(true);
        cmn.setSigning_provider_no("-1");
        cmn.setProgram_no(prog_no);

        SecRoleDao secRoleDao = (SecRoleDao) SpringUtils.getBean(SecRoleDao.class);
        SecRole doctorRole = secRoleDao.findByName("doctor");
        cmn.setReporter_caisi_role(doctorRole.getId().toString());

        cmn.setReporter_program_team("0");
        cmn.setPassword("NULL");
        cmn.setLocked(false);
        cmn.setHistory(strNote);
        cmn.setPosition(0);

        Long note_id = cmm.saveNoteSimpleReturnID(cmn);
        // Debugging purposes on the live server
        MiscUtils.getLogger().info("Document Note ID: " + note_id.toString());

        // Add a noteLink to casemgmt_note_link
        CaseManagementNoteLink cmnl = new CaseManagementNoteLink();
        cmnl.setTableName(CaseManagementNoteLink.DOCUMENT);
        cmnl.setTableId(Long.parseLong(documentId));
        cmnl.setNoteId(note_id);
        EDocUtil.addCaseMgmtNoteLink(cmnl);
    }

    /*
     * private void savePatientLabRouting(String demog,String docId,String docType){ CommonLabResultData.updatePatientLabRouting(docId, demog, docType); }
     */

    private static String getDocumentCacheDir() {
        if (DOCUMENT_CACHE_DIR != null && !DOCUMENT_CACHE_DIR.isEmpty()) {
            return DOCUMENT_CACHE_DIR;
        }
        return getDocumentCacheDir(DOCUMENT_DIR).getAbsolutePath();
    }

    private static File getDocumentCacheDir(String docdownload) {
        File docDir = new File(docdownload);
        String documentDirName = docDir.getName();
        File parentDir = docDir.getParentFile();

        // Sanitize the cache directory name to prevent path traversal
        String safeCacheDirName = MiscUtils.sanitizeFileName(documentDirName + "_cache");

        // Use validatePath to create a validated cache directory path
        File cacheDir = PathValidationUtils.validatePath(safeCacheDirName, parentDir);

        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }
        return cacheDir;
    }

    private File hasCacheVersion2(Document d, Integer pageNum) {
        Path outFile = Paths.get(getDocumentCacheDir(), d.getDocfilename() + "_" + pageNum + ".png");
        if (!Files.exists(outFile)) {
            return null;
        }
        return outFile.toFile();
    }

    public static void deleteCacheVersion(Document d, int pageNum) {
        Path documentCacheDir = Paths.get(getDocumentCacheDir(), d.getDocfilename() + "_" + pageNum + ".png");
        if (Files.exists(documentCacheDir)) {
            try {
                Files.delete(documentCacheDir);
            } catch (IOException e) {
                MiscUtils.getLogger().error("Failed to delete cache file: " + documentCacheDir.getFileName(), e);
            }
        }
    }

    private File hasCacheVersion(Document d, int pageNum) {
        return hasCacheVersion2(d, pageNum);
    }

    public byte[] createCacheVersion2(Document d, Integer pageNum) {
        Path pdfPath = Paths.get(DOCUMENT_DIR, d.getDocfilename());
        Path pngFile = Paths.get(getDocumentCacheDir(), d.getDocfilename() + "_" + pageNum + ".png");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDFParser parser = new PDFParser(new RandomAccessFile(pdfPath.toFile(), "rw"), new ScratchFile(MemoryUsageSetting.setupTempFileOnly()));
            parser.parse();
            PDDocument pdf = parser.getPDDocument();

            PDFRenderer rend = new PDFRenderer(pdf);
            //Page index starts at 0, subtracts 1 to account for that
            BufferedImage image = rend.renderImageWithDPI(pageNum - 1, 90, ImageType.RGB);

            // write cache file
            ImageIO.write(image, "png", pngFile.toFile());
            ImageIO.write(image, "png", baos);

            pdf.close();
            image.flush();

            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error decoding pdf file " + d.getDocfilename(), e);
            return null;
        }
    }

    /**
     * @Deprecated : use createCacheVersion2
     */
    public File createCacheVersion(Document d) throws Exception {

        String docdownload = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        File documentDir = new File(docdownload);
        File documentCacheDir = getDocumentCacheDir(docdownload);
        log.debug("Document Dir is a dir" + documentDir.isDirectory());

        File file = new File(documentDir, d.getDocfilename());
        PDFFile pdffile = null;

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()
        ) {
            ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            pdffile = new PDFFile(buf);
        } catch (Exception e) {
            throw new Exception(e);
        }

        // long readfile = System.currentTimeMillis() - start;
        // draw the first page to an image
        PDFPage ppage = pdffile.getPage(0);

        log.debug("WIDTH " + (int) ppage.getBBox().getWidth() + " height " + (int) ppage.getBBox().getHeight());

        // get the width and height for the doc at the default zoom
        Rectangle rect = new Rectangle(0, 0, (int) ppage.getBBox().getWidth(), (int) ppage.getBBox().getHeight());

        log.debug("generate the image");
        Image img = ppage.getImage(rect.width, rect.height, // width & height
                rect, // clip rect
                null, // null for the ImageObserver
                true, // fill background with white
                true // block until drawing is done
        );

        log.debug("about to Print to stream");
        File outfile = new File(documentCacheDir, d.getDocfilename() + ".png");

        try (OutputStream outs = new FileOutputStream(outfile)) {
            RenderedImage rendImage = (RenderedImage) img;
            ImageIO.write(rendImage, "png", outs);
            outs.flush();
        }

        return outfile;

    }

    public void showPage() throws Exception {
        getPage(Integer.parseInt(request.getParameter("page")));
    }

    public void view() {
        getPage(1);
    }

    // PNG version
    public void getPage(int pageNum) {

        String doc_no = request.getParameter("doc_no");
        log.debug("Document No :" + doc_no);
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());
        Document d = documentDao.getDocument(doc_no);

        log.debug("Document Name :" + d.getDocfilename());

        File outfile = hasCacheVersion(d, pageNum);

        if (outfile != null) {
            setResponse(response, outfile);
        } else {
            byte[] pdfBytes = createCacheVersion2(d, pageNum);
            setResponse(response, pdfBytes);
        }

        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "attachment;filename=\"" + sanitizeHeaderValue(d.getDocfilename()) + "\"");
    }

    public void viewDocPage() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        log.debug("in viewDocPage");

        String doc_no = request.getParameter("doc_no");
        String pageNum = request.getParameter("curPage");
        if (pageNum == null) {
            pageNum = "1";
        }
        Integer pn = Integer.parseInt(pageNum);
        log.debug("Document No :" + doc_no);
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());

        Document d = documentDao.getDocument(doc_no);
        if (d == null) {
            log.error("Document not found for ID: " + doc_no);
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");
            } catch (IOException e) {
                log.error("Error sending error response", e);
            }
            return;
        }

        log.debug("Document Name :" + d.getDocfilename());
        //if the file is not a pdf, use display function
        if (!(d.getContenttype().equals("application/pdf") || d.getDocfilename().endsWith(".pdf"))) {
            try {
                display();
            } catch (Exception e) {
                log.error("Error while displaying document ", e);
            }
            return;
        }

        String name = d.getDocfilename() + "_" + pn + ".png";
        log.debug("name " + name);

        File outfile = hasCacheVersion2(d, pn);
        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "attachment;filename=\"" + sanitizeHeaderValue(name) + "\"");

        if (outfile != null) {
            setResponse(response, outfile);
        } else {
            byte[] pdfBytes = createCacheVersion2(d, pn);
            setResponse(response, pdfBytes);
        }

    }

    public void view2() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String doc_no = request.getParameter("doc_no");
        log.debug("Document No :" + doc_no);

        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());

        String docdownload = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        File documentDir = new File(docdownload);
        log.debug("Document Dir is a dir" + documentDir.isDirectory());

        Document d = documentDao.getDocument(doc_no);
        log.debug("Document Name :" + d.getDocfilename());

        // TODO: Right now this assumes it's a pdf which it shouldn't

        response.setContentType("image/png");
        // response.setHeader("Content-Disposition", "attachment;filename=\"" + filename+ "\"");
        // read the file name.
        File file = new File(documentDir, d.getDocfilename());

        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
        FileChannel channel = raf.getChannel();
        ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        PDFFile pdffile = new PDFFile(buf);
        // long readfile = System.currentTimeMillis() - start;
        // draw the first page to an image
        PDFPage ppage = pdffile.getPage(0);

        log.debug("WIDTH " + (int) ppage.getBBox().getWidth() + " height " + (int) ppage.getBBox().getHeight());

        // get the width and height for the doc at the default zoom
        Rectangle rect = new Rectangle(0, 0, (int) ppage.getBBox().getWidth(), (int) ppage.getBBox().getHeight());

        log.debug("generate the image");
        Image img = ppage.getImage(rect.width, rect.height, // width & height
                rect, // clip rect
                null, // null for the ImageObserver
                true, // fill background with white
                true // block until drawing is done
        );

        log.debug("about to Print to stream");
        ServletOutputStream outs = response.getOutputStream();

        RenderedImage rendImage = (RenderedImage) img;
        ImageIO.write(rendImage, "png", outs);
        outs.flush();
        outs.close();

    }

    public void getDocPageNumber() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String doc_no = request.getParameter("doc_no");
        String docdownload = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        // File documentDir = new File(docdownload);
        Document d = documentDao.getDocument(doc_no);
        String filePath = docdownload + d.getDocfilename();

        int numOfPage = 0;
        try {
            PdfReader reader = new PdfReader(filePath);
            numOfPage = reader.getNumberOfPages();

            HashMap hm = new HashMap();
            hm.put("numOfPage", numOfPage);
            ObjectNode jsonObject = objectMapper.valueToTree(hm);
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    public String downloadCDS() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        // Sharing Center functionality has been removed
        log.warn("CDS download no longer available - Sharing Center has been removed");
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "CDS download functionality is no longer available");
        return null;
    }

    public void display() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String temp = request.getParameter("remoteFacilityId");
        Integer remoteFacilityId = null;
        if (temp != null) {
            remoteFacilityId = Integer.parseInt(temp);
        }

        String doc_no = request.getParameter("doc_no");
        log.debug("Document No :" + doc_no);
        String demoNo = request.getParameter("demoNo");

        String docxml = null;
        String contentType = null;
        byte[] contentBytes = null;
        String filename = null;

        // local document
        if (remoteFacilityId == null) {
            CtlDocument ctld = ctlDocumentDao.getCtrlDocument(Integer.parseInt(doc_no));
            if (ctld.isDemographicDocument()) {
                LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr(), "" + ctld.getId().getModuleId());
            } else {
                LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());
            }

            Document d = documentDao.getDocument(doc_no);

            log.debug("Document Name :" + d.getDocfilename());

            docxml = d.getDocxml();
            contentType = d.getContenttype();
            filename = d.getDocfilename();

            Path file = Paths.get(DOCUMENT_DIR, filename);

            if (Files.exists(file)) {
                contentBytes = Files.readAllBytes(file);
            } else {
                if (docxml == null || docxml.trim().equals("")) {
                    // Only throw exception if the file does not exist and the docxml is null/empty to serve HTML files that were uploaded in OSCAR 12,
                    // where HTML file uploads contents were stored in the docxml field of the document table, and the file was never saved.
                    throw new IllegalStateException("Local document doesn't exist for eDoc (ID " + d.getId() + "): " + file.getFileName());
                }
            }
        } else // remote document
        {
            FacilityIdIntegerCompositePk remotePk = new FacilityIdIntegerCompositePk();
            remotePk.setIntegratorFacilityId(remoteFacilityId);
            remotePk.setCaisiItemId(Integer.parseInt(doc_no));


            CachedDemographicDocument remoteDocument = null;
            CachedDemographicDocumentContents remoteDocumentContents = null;

            try {
                if (!CaisiIntegratorManager.isIntegratorOffline(request.getSession())) {
                    DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
                    remoteDocument = demographicWs.getCachedDemographicDocument(remotePk);
                    remoteDocumentContents = demographicWs.getCachedDemographicDocumentContents(remotePk);
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Unexpected error.", e);
                CaisiIntegratorManager.checkForConnectionError(request.getSession(), e);
            }

            if (CaisiIntegratorManager.isIntegratorOffline(request.getSession())) {
                Integer demographicId = IntegratorFallBackManager.getDemographicNoFromRemoteDocument(loggedInInfo, remotePk);
                MiscUtils.getLogger().debug("got demographic no from remote document " + demographicId);
                List<CachedDemographicDocument> remoteDocuments = IntegratorFallBackManager.getRemoteDocuments(loggedInInfo, demographicId);
                for (CachedDemographicDocument demographicDocument : remoteDocuments) {
                    if (demographicDocument.getFacilityIntegerPk().getIntegratorFacilityId() == remotePk.getIntegratorFacilityId() && demographicDocument.getFacilityIntegerPk().getCaisiItemId() == remotePk.getCaisiItemId()) {
                        remoteDocument = demographicDocument;
                        remoteDocumentContents = IntegratorFallBackManager.getRemoteDocument(loggedInInfo, demographicId, remotePk);
                        break;
                    }
                    MiscUtils.getLogger().error("End of the loop and didn't find the remoteDocument");
                }
            }

            if (remoteDocument == null || remoteDocumentContents == null) {
                throw new IllegalStateException("Remote document not found or contents unavailable for document ID: " + doc_no);
            }

            docxml = remoteDocument.getDocXml();
            contentType = remoteDocument.getContentType();
            filename = remoteDocument.getDocFilename();
            contentBytes = remoteDocumentContents.getFileContents().getContent().toString().getBytes(StandardCharsets.UTF_8);
        }

        if (docxml != null && !docxml.trim().equals("")) {
            setResponse(response, docxml.getBytes());
            return;
        }

        // TODO: Right now this assumes it's a pdf which it shouldn't
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        String data = "doc_no=" + doc_no;
        LogAction.addLog(loggedInInfo, LogConst.READ, "Document", null, demoNo, data);

        response.setContentType(contentType);
        response.setContentLength(contentBytes.length);
        response.setHeader("Content-Disposition", "inline; filename=\"" + sanitizeHeaderValue(filename) + "\"");
        log.debug("about to Print to stream");
        try (ServletOutputStream outs = response.getOutputStream()) {
            outs.write(contentBytes);
            outs.flush();
        }
    }

    public void viewDocumentInfo() throws Exception {
        response.setContentType("text/html");
        doViewDocumentInfo(request, response.getWriter(), true, true);

    }

    public void viewDocumentDescription() throws Exception {
        response.setContentType("text/html");
        doViewDocumentInfo(request, response.getWriter(), false, true);
    }

    public void viewAnnotationAcknowledgementTickler() throws Exception {
        response.setContentType("text/html");
        doViewDocumentInfo(request, response.getWriter(), true, false);
    }

    public void doViewDocumentInfo(HttpServletRequest request, PrintWriter out, boolean viewAnnotationAcknowledgementTicklerFlag, boolean viewDocumentDescriptionFlag) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String doc_no = request.getParameter("doc_no");
        Locale locale = request.getLocale();

        String annotation = "", acknowledgement = "", tickler = "";
        if (doc_no != null && doc_no.length() > 0) {
            annotation = EDocUtil.getHtmlAnnotation(doc_no);
            acknowledgement = EDocUtil.getHtmlAcknowledgement(locale, doc_no);
            if (acknowledgement == null) {
                acknowledgement = "";
            }
            tickler = EDocUtil.getHtmlTicklers(loggedInInfo, doc_no);
        }

        out.println("<!DOCTYPE html><html><head><meta http-equiv='Content-Type' content='text/html; charset=UTF-8'></head><body>");

        if (viewAnnotationAcknowledgementTicklerFlag) {
            if (annotation.length() > 0) {
                out.println(annotation);
            }
            if (tickler.length() > 0) {
                out.println(tickler + "<br>");
            }
            if (acknowledgement.length() > 0) {
                out.println(acknowledgement + "<br>");
            }
        }

        if (viewDocumentDescriptionFlag) {
            EDoc curDoc = EDocUtil.getDoc(doc_no);
            ResourceBundle props = ResourceBundle.getBundle("oscarResources", locale);
            out.println("<br>" + props.getString("dms.documentBrowser.DocumentUpdated") + ": " + curDoc.getDateTimeStamp());
            out.println("<br>" + props.getString("dms.documentBrowser.ContentUpdated") + ": " + curDoc.getContentDateTime());
            out.println("<br>" + props.getString("dms.documentBrowser.ObservationDate") + ": " + curDoc.getObservationDate());
            out.println("<br>" + props.getString("dms.documentBrowser.Type") + ": " + curDoc.getType());
            out.println("<br>" + props.getString("dms.documentBrowser.Class") + ": " + curDoc.getDocClass());
            out.println("<br>" + props.getString("dms.documentBrowser.Subclass") + ": " + curDoc.getDocSubClass());
            out.println("<br>" + props.getString("dms.documentBrowser.Description") + ": " + curDoc.getDescription());
            out.println("<br>" + props.getString("dms.documentBrowser.Creator") + ": " + curDoc.getCreatorName());
            out.println("<br>" + props.getString("dms.documentBrowser.Responsible") + ": " + curDoc.getResponsibleName());
            out.println("<br>" + props.getString("dms.documentBrowser.Reviewer") + ": " + curDoc.getReviewerName());
            out.println("<br>" + props.getString("dms.documentBrowser.Source") + ": " + curDoc.getSource());
        }

        out.println("</body></html>");
        out.flush();
        out.close();

    }

    public String addIncomingDocument() throws Exception {

        String pdfDir = request.getParameter("pdfDir");
        String pdfName = request.getParameter("pdfName");
        String demographic_no = request.getParameter("demog");
        String observationDate = request.getParameter("observationDate");
        String documentDescription = request.getParameter("documentDescription");
        String docType = request.getParameter("docType");
        String docClass = request.getParameter("docClass");
        String docSubClass = request.getParameter("docSubClass");
        String[] flagproviders = request.getParameterValues("flagproviders");
        String queueId1 = request.getParameter("queueId");
        String sourceFilePath = IncomingDocUtil.getIncomingDocumentFilePathName(queueId1, pdfDir, pdfName);
        String destFilePath;
        
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }
        
        // Validate input parameters to prevent path traversal
        if (queueId1 == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId1.contains("..") || queueId1.contains("/") || queueId1.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }

        String savePath = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (!savePath.endsWith(File.separator)) {
            savePath += File.separator;
        }

        Date obDate = UtilDateUtilities.StringToDate(observationDate);
        String formattedDate = UtilDateUtilities.DateToString(obDate, EDocUtil.DMS_DATE_FORMAT);
        String source = "";


        int numberOfPages = 0;
        String fileName = sanitizedPdfName;
        String user = (String) request.getSession().getAttribute("user");
        EDoc newDoc = new EDoc(documentDescription, docType, fileName, "", user, user, source, 'A', formattedDate, "", "", "demographic", demographic_no, 0);

        // if the document was added in the context of a program
        ProgramManager2 programManager = SpringUtils.getBean(ProgramManager2.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        ProgramProvider pp = programManager.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
        if (pp != null && pp.getProgramId() != null) {
            newDoc.setProgramId(pp.getProgramId().intValue());
        }

        newDoc.setDocClass(docClass);
        newDoc.setDocSubClass(docSubClass);
        newDoc.setDocPublic("0");
        fileName = newDoc.getFileName();
        // Sanitize the filename to match what the file system will actually create
        String sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "");

        // Ensure sanitized filename is not empty and has a minimum length
        if (sanitizedFileName.trim().isEmpty() || sanitizedFileName.length() < 1) {
            // Generate a fallback filename with timestamp
            String timestamp = String.valueOf(System.currentTimeMillis());
            sanitizedFileName = "document_" + timestamp + ".dat";
        }

        // Ensure filename uniqueness by checking if file already exists
        File destFile = new File(savePath + sanitizedFileName);
        String originalSanitized = sanitizedFileName;
        int counter = 1;
        while (destFile.exists()) {
            String nameWithoutExt = originalSanitized;
            String extension = "";
            int lastDot = originalSanitized.lastIndexOf(".");
            if (lastDot > 0) {
                nameWithoutExt = originalSanitized.substring(0, lastDot);
                extension = originalSanitized.substring(lastDot);
            }
            sanitizedFileName = nameWithoutExt + "_" + counter + extension;
            destFile = new File(savePath + sanitizedFileName);
            counter++;
        }

        newDoc.setFileName(sanitizedFileName);
        destFilePath = savePath + sanitizedFileName;
        String doc_no = "";

        // Validate destination path is within allowed directory using PathValidationUtils
        File saveDir = new File(savePath);
        File finalDestFile = PathValidationUtils.validatePath(sanitizedFileName, saveDir);
        destFilePath = finalDestFile.getPath();

        newDoc.setContentType(docType);
        File f1 = new File(sourceFilePath);

        // Validate source file is within INCOMINGDOCUMENT_DIR to prevent path traversal
        String incomingDocDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (incomingDocDir != null && !incomingDocDir.isEmpty()) {
            File incomingDir = new File(incomingDocDir);
            f1 = PathValidationUtils.validateExistingPath(f1, incomingDir);
        }

        boolean success = f1.renameTo(new File(destFilePath));
        if (!success) {
            log.error("Not able to move " + f1.getName() + " to " + destFilePath);
            // File was not successfully moved - attempt to delete temp file to prevent orphaned files
            boolean deleted = f1.delete();
            if (!deleted) {
                log.warn("Failed to delete temporary file: " + f1.getAbsolutePath());
            }
            String documentId = request.getParameter("documentId");
            log.error("Failed to save document file for document ID: " + documentId);
            addActionError("Failed to save document file. Please try again or contact your system administrator.");
            return "error";
        } else {

            newDoc.setContentType("application/pdf");
            if (fileName.endsWith(".PDF") || fileName.endsWith(".pdf")) {
                newDoc.setContentType("application/pdf");
                numberOfPages = countNumOfPages(fileName);
            }
            newDoc.setNumberOfPages(numberOfPages);
            doc_no = EDocUtil.addDocumentSQL(newDoc);
            LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());


            if (flagproviders != null && flagproviders.length > 0) {
                try {
                    for (String proNo : flagproviders) {
                        // Sanitize provider number to prevent any potential header injection
                        // Provider numbers should only contain alphanumeric characters
                        if (proNo != null && proNo.matches("^[a-zA-Z0-9_-]+$")) {
                            providerInboxRoutingDAO.addToProviderInbox(proNo, Integer.parseInt(doc_no), LabResultData.DOCUMENT);
                        } else {
                            log.warn("Invalid provider number format: " + (proNo != null ? proNo.replaceAll("[\r\n]", "") : "null"));
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                }
            }

            //Check to see if we have to route document to patient
            PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
            List<PatientLabRouting> patientLabRoutingList = patientLabRoutingDao.findByLabNoAndLabType(Integer.parseInt(doc_no), docType);
            if (patientLabRoutingList == null || patientLabRoutingList.isEmpty()) {
                PatientLabRouting patientLabRouting = new PatientLabRouting();
                patientLabRouting.setDemographicNo(Integer.parseInt(demographic_no));
                patientLabRouting.setLabNo(Integer.parseInt(doc_no));
                patientLabRouting.setLabType("DOC");
                patientLabRoutingDao.persist(patientLabRouting);
            }

            try {

                CtlDocument ctlDocument = ctlDocumentDao.getCtrlDocument(Integer.parseInt(doc_no));

                ctlDocument.getId().setModuleId(Integer.parseInt(demographic_no));
                ctlDocumentDao.merge(ctlDocument);
                //save a document created note
                if (ctlDocument.isDemographicDocument()) {
                    //save note
                    saveDocNote(request, documentDescription, demographic_no, doc_no);
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }

        return "nextIncomingDoc";
    }

    public void viewIncomingDocPageAsPdf() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String pageNum = request.getParameter("curPage");
        String queueId = request.getParameter("queueId");
        String pdfDir = request.getParameter("pdfDir");
        String pdfName = request.getParameter("pdfName");
        
        // Validate input parameters to prevent path traversal
        if (queueId == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId.contains("..") || queueId.contains("/") || queueId.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }
        
        String filePath = IncomingDocUtil.getIncomingDocumentFilePathName(queueId, pdfDir, sanitizedPdfName);
        
        // Validate canonical path to ensure file is within allowed directory
        String incomingDocDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (incomingDocDir == null || incomingDocDir.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR not configured");
        }
        
        // Validate file path using PathValidationUtils
        File baseDir = new File(incomingDocDir);
        File file = new File(filePath);
        PathValidationUtils.validateExistingPath(file, baseDir);

        Locale locale = request.getLocale();
        ResourceBundle props = ResourceBundle.getBundle("oscarResources", locale);

        if (pageNum == null) {
            pageNum = "0";
        }

        int pageNumber = Integer.parseInt(pageNum);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"" + sanitizeHeaderValue(sanitizedPdfName + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf") + "\"");

        try {
            PDDocument reader = PDDocument.load(file);
            PDDocument extractedPage = new PDDocument();
            extractedPage.addPage(reader.getDocumentCatalog().getPages().get(pageNumber - 1));
            extractedPage.save(response.getOutputStream());
            extractedPage.close();
            reader.close();
        } catch (Exception ex) {
            response.setContentType("text/html");
            // Sanitize the filename to prevent XSS and response splitting
            response.getWriter().print(props.getString("dms.incomingDocs.errorInOpening") + Encode.forHtml(sanitizedPdfName));
            response.getWriter().print("<br>" + props.getString("dms.incomingDocs.PDFCouldBeCorrupted"));

            MiscUtils.getLogger().error("Error", ex);
        }

    }

    public int countNumOfPages(String fileName) { // count number of pages in a pdf file
        int numOfPage = 0;
        String docdownload = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");

        if (!docdownload.endsWith(File.separator)) {
            docdownload += File.separator;
        }

        String filePath = docdownload + fileName;

        try {
            PDDocument reader = PDDocument.load(new File(filePath));
            numOfPage = reader.getNumberOfPages();

            reader.close();
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return numOfPage;
    }

    public void displayIncomingDocs() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String queueId = request.getParameter("queueId");
        String pdfDir = request.getParameter("pdfDir");
        String pdfName = request.getParameter("pdfName");
        
        // Validate input parameters to prevent path traversal
        if (queueId == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId.contains("..") || queueId.contains("/") || queueId.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }
        
        String filePath = IncomingDocUtil.getIncomingDocumentFilePathName(queueId, pdfDir, sanitizedPdfName);
        
        // Validate canonical path to ensure file is within allowed directory
        String incomingDocDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (incomingDocDir == null || incomingDocDir.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR not configured");
        }
        
        // Validate file path using PathValidationUtils
        File baseDir = new File(incomingDocDir);
        File file = new File(filePath);
        PathValidationUtils.validateExistingPath(file, baseDir);

        String contentType = "application/pdf";
        response.setContentType(contentType);
        response.setContentLength((int) file.length());
        response.setHeader("Content-Disposition", "inline; filename=\"" + sanitizeHeaderValue(sanitizedPdfName) + "\"");

        BufferedInputStream bfis = null;
        ServletOutputStream outs = response.getOutputStream();

        try {
            // Re-validate file path at point of use for static analysis visibility
            File validatedFile = PathValidationUtils.validateExistingPath(file, baseDir);
            bfis = new BufferedInputStream(new FileInputStream(validatedFile));

            org.apache.commons.io.IOUtils.copy(bfis, outs);
            outs.flush();

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            if (bfis != null) {
                bfis.close();
            }
        }
    }

    public void viewIncomingDocPageAsImage() throws Exception {


        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String pageNum = request.getParameter("curPage");
        String queueId = request.getParameter("queueId");
        String pdfDir = request.getParameter("pdfDir");
        String pdfName = request.getParameter("pdfName");
        
        // Validate input parameters to prevent path traversal
        if (queueId == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId.contains("..") || queueId.contains("/") || queueId.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }

        if (pageNum == null) {
            pageNum = "0";
        }

        BufferedInputStream bfis = null;
        ServletOutputStream outs = null;

        try {
            Integer pn = Integer.parseInt(pageNum);
            File outfile = createIncomingCacheVersion(queueId, pdfDir, sanitizedPdfName, pn);
            outs = response.getOutputStream();

            if (outfile != null) {
                // Security: Validate the file path before accessing
                validateFilePath(outfile);
                bfis = new BufferedInputStream(new FileInputStream(outfile));


                response.setContentType("image/png");
                response.setHeader("Content-Disposition", "inline;filename=\"" + sanitizeHeaderValue(sanitizedPdfName) + "\"");
                org.apache.commons.io.IOUtils.copy(bfis, outs);
                outs.flush();

            } else {
                log.info("Unable to retrieve content for " + queueId + "/" + pdfDir + "/" + pdfName);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);

        } finally {
            if (bfis != null) {
                bfis.close();
            }
        }
    }

    public File createIncomingCacheVersion(String queueId, String pdfDir, String pdfName, Integer pageNum) throws Exception {
        
        // Validate input parameters to prevent path traversal
        if (queueId == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId.contains("..") || queueId.contains("/") || queueId.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }
        
        // Ensure the file has a .pdf extension
        if (!sanitizedPdfName.toLowerCase().endsWith(".pdf")) {
            throw new SecurityException("Invalid file type - only PDF files are allowed");
        }

        String incomingDocPath = IncomingDocUtil.getIncomingDocumentFilePath(queueId, pdfDir);
        
        // Validate canonical path to ensure file is within allowed directory
        String incomingDocDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (incomingDocDir == null || incomingDocDir.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR not configured");
        }
        
        // Validate file path using PathValidationUtils
        File baseDir = new File(incomingDocDir);
        File documentDir = new File(incomingDocPath);
        File documentCacheDir = getDocumentCacheDir(incomingDocPath);
        File file = new File(documentDir, sanitizedPdfName);
        PathValidationUtils.validateExistingPath(file, baseDir);

        PdfDecoder decode_pdf = new PdfDecoder(true);

        // Re-validate file path at point of use for static analysis visibility
        File validatedFile = PathValidationUtils.validateExistingPath(file, baseDir);
        try (FileInputStream is = new FileInputStream(validatedFile)) {

            FontMappings.setFontReplacements();

            decode_pdf.useHiResScreenDisplay(true);

            decode_pdf.setExtractionMode(0, 96, 96 / 72f);

            decode_pdf.openPdfFileFromInputStream(is, false);

            BufferedImage image_to_save = decode_pdf.getPageAsImage(pageNum);

            // Use sanitized filename for cache file and validate path
            String cacheFileName = sanitizedPdfName.substring(0, sanitizedPdfName.lastIndexOf('.')) + "_" + pageNum + ".png";
            File cacheFile = PathValidationUtils.validatePath(cacheFileName, documentCacheDir);

            decode_pdf.getObjectStore().saveStoredImage(cacheFile.getCanonicalPath(), image_to_save, true, false, "png");

            decode_pdf.flushObjectValues(true);

            return cacheFile;
        } catch (Exception e) {
            log.error("Error decoding pdf file " + pdfDir + sanitizedPdfName);
            return null;
        } finally {
            if (decode_pdf != null) {
                decode_pdf.closePdfFile();
            }
        }
    }

    private HttpServletResponse setResponse(HttpServletResponse response, byte[] pdfBytes) {
        try (ServletOutputStream outs = response.getOutputStream();
             ByteArrayInputStream fileInputStream = new ByteArrayInputStream(pdfBytes)) {
            org.apache.commons.io.IOUtils.copy(fileInputStream, outs);
        } catch (Exception e) {
            log.error("Error retrieving PDF document", e);
        }
        return response;
    }

    private HttpServletResponse setResponse(HttpServletResponse response, File output) {
        try (ServletOutputStream outs = response.getOutputStream();
             FileInputStream fileInputStream = new FileInputStream(output)
        ) {
            org.apache.commons.io.IOUtils.copy(fileInputStream, outs);
        } catch (Exception e) {
            log.error("Error retrieving document: " + output.getPath(), e);
        }
        return response;
    }
    
    /**
     * Validates that a file path is safe to access and within allowed directories.
     * Prevents path traversal attacks by ensuring the file's canonical path
     * is within the expected document or cache directories.
     * 
     * @param file The file to validate
     * @throws SecurityException if the file path is invalid or potentially malicious
     */
    private void validateFilePath(File file) throws SecurityException {
        if (file == null) {
            throw new SecurityException("File is null");
        }

        // Get all allowed directories
        File documentDir = new File(DOCUMENT_DIR);
        File documentCacheDir = new File(getDocumentCacheDir());
        String incomingDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        File incomingDirFile = new File(incomingDir);
        File incomingCacheDir = getDocumentCacheDir(incomingDir);

        // Try each directory - file must be in at least one
        File[] allowedDirs = {documentDir, documentCacheDir, incomingDirFile, incomingCacheDir};

        for (File allowedDir : allowedDirs) {
            try {
                PathValidationUtils.validateExistingPath(file, allowedDir);
                return; // Valid if we get here without exception
            } catch (SecurityException e) {
                // File not in this directory, try next
            }
        }

        // If we get here, file wasn't in any allowed directory
        throw new SecurityException("File path is outside allowed directories");
    }
    
    /**
     * Sanitizes a header value to prevent HTTP response splitting attacks.
     * Removes all control characters including CR (\r) and LF (\n) that could
     * be used to inject additional headers or split the HTTP response.
     * 
     * @param value The header value to sanitize
     * @return The sanitized header value safe for use in HTTP headers
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        
        // Remove all control characters including CR (\r) and LF (\n)
        // This prevents HTTP response splitting attacks
        // Also remove other control characters that could cause issues
        String sanitized = value.replaceAll("[\r\n\u0000-\u001F\u007F-\u009F]", "");
        
        // Ensure the filename is not empty after sanitization
        if (sanitized.trim().isEmpty()) {
            return "document";
        }
        
        return sanitized;
    }
}
