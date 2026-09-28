/* Copyright (c) 2008-2012 Indivica Inc.; 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderInboxItem;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.IncomingDocumentCapacityResponse;
import io.github.carlos_emr.carlos.documentManager.IncomingDocumentMutationLock;
import io.github.carlos_emr.carlos.documentManager.StoredDocumentRevision;
import io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask;
import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stored-document page operations. A capacity refusal always precedes publication/persistence. */
public class SplitDocument2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final DocumentDao documentDao = SpringUtils.getBean(DocumentDao.class);
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override public String execute() throws IOException {
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            return reject(405, false, false, "Document changes require POST");
        }
        String method = request.getParameter("method");
        if ("split".equals(method)) return split();
        if ("rotate180".equals(method)) return rotate180();
        if ("rotate90".equals(method)) return rotate90();
        if ("removeFirstPage".equals(method)) return removeFirstPage();
        return reject(400, false, false, "Unknown document operation");
    }

    public String split() throws IOException { return perform(SplitDocumentPdfWork.Operation.SPLIT); }
    public String rotate180() throws IOException { return perform(SplitDocumentPdfWork.Operation.ROTATE_180); }
    public String rotate90() throws IOException { return perform(SplitDocumentPdfWork.Operation.ROTATE_90); }
    public String removeFirstPage() throws IOException { return perform(SplitDocumentPdfWork.Operation.REMOVE_FIRST); }

    private String perform(SplitDocumentPdfWork.Operation operation) throws IOException {
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            return reject(405, false, false, "Document changes require POST");
        }
        SplitDocumentPdfWork.Publication publication = null;
        try {
            LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);
            if (info == null || !securityInfoManager.hasPrivilege(info, "_edoc", "w", null)) {
                throw new SecurityException("Document write access is required");
            }
            String documentValue = request.getParameter("document");
            if (!IncomingDocumentCapacityResponse.positiveId(documentValue)) throw new IllegalArgumentException("Invalid document");
            int documentNo = Integer.parseInt(documentValue);
            String[] revisionValues = request.getParameterValues("sourceRevision");
            if (revisionValues == null || revisionValues.length != 1 || !StoredDocumentRevision.valid(revisionValues[0])) {
                throw new StoredDocumentRevision.ConflictException();
            }
            String observedRevision = revisionValues[0];
            boolean split = operation == SplitDocumentPdfWork.Operation.SPLIT;
            String queue = request.getParameter("queueID");
            if (queue == null || queue.isEmpty()) queue = "1";
            if (split) IncomingDocumentCapacityResponse.requireQueueAccess(securityInfoManager, info, queue);
            List<SplitDocumentPdfWork.PageSelection> selections = split
                    ? SplitDocumentPdfWork.selections(request.getParameterValues("page")) : List.of();
            Document document = requireDocument(documentValue);
            authorize(info, documentNo);
            File directory = PathValidationUtils.validateConfiguredDirectory(
                    CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
            File source = PathValidationUtils.validateExistingPath(new File(directory, document.getDocfilename()), directory);
            try (IncomingDocumentMutationLock.Lease lease = IncomingDocumentMutationLock.acquire(source, directory)) {
                document = requireDocument(documentValue);
                File current = PathValidationUtils.validateExistingPath(new File(directory, document.getDocfilename()), directory);
                if (!current.getCanonicalFile().equals(lease.source())) throw new SecurityException("Document changed while waiting");
                authorize(info, documentNo);
                StoredDocumentRevision.requireMatch(lease.source().toPath(), observedRevision);
                try (SplitDocumentPdfWork.Prepared prepared = prepare(lease.source().toPath(), directory.toPath(), operation, selections)) {
                    String committedRevision = split ? observedRevision : StoredDocumentRevision.sha256(prepared.pdf);
                    // Under the source lease, renderers cannot republish old pages. A
                    // cache deletion failure must refuse the edit before changing bytes.
                    if (!split) invalidateCaches(document, prepared.originalPageCount);
                    publication = new SplitDocumentPdfWork.Publication(prepared, lease.source().toPath(), !split);
                    int newDocumentNo = persist(documentNo, split ? Integer.parseInt(queue) : 1, info, publication, observedRevision);
                    if (!publication.committed || publication.uncertain) throw new IOException("Document transaction outcome is unconfirmed");
                    ObjectNode result = JSON.createObjectNode().put("success", true).put("accepted", true)
                            .put("sourceRevision", committedRevision);
                    if (split) result.put("newDocNum", newDocumentNo);
                    else result.put("pageCount", prepared.pageCount).put("document", documentNo);
                    // Cleanup failure must be visible before reporting success. close is idempotent.
                    prepared.close();
                    write(200, result);
                    return NONE;
                }
            }
        } catch (StoredDocumentRevision.ConflictException changed) {
            if (publication != null && publication.mutationStarted) {
                return reject(500, true, false, "Document outcome is unconfirmed; do not submit again");
            }
            write(409, JSON.createObjectNode().put("success", false).put("accepted", false)
                    .put("retryable", false).put("sourceChanged", true)
                    .put("error", "The document changed. Refresh it before submitting a new selection."));
            return NONE;
        } catch (BoundedPdfTask.BusyException busy) {
            boolean accepted = publication != null && publication.mutationStarted;
            if (!accepted) response.setHeader("Retry-After", "1");
            return reject(accepted ? 500 : 503, accepted, !accepted,
                    accepted ? "Document outcome is unconfirmed; do not submit again" : "Document server is busy; waiting is safe");
        } catch (SecurityException denied) {
            return reject(403, publication != null && publication.mutationStarted, false, "Document access denied");
        } catch (IllegalArgumentException invalid) {
            return reject(400, publication != null && publication.mutationStarted, false, "Invalid document or page selection");
        } catch (NoSuchFileException missing) {
            return reject(404, publication != null && publication.mutationStarted, false, "Document is no longer available");
        } catch (IOException | RuntimeException failure) {
            boolean accepted = publication != null && publication.mutationStarted;
            MiscUtils.getLogger().error("Stored document page operation failed; acceptance uncertain: " + accepted, failure);
            if (response.isCommitted()) throw new IOException("Document response could not be delivered", failure);
            return reject(accepted ? 500 : 422, accepted, false,
                    accepted ? "Document outcome is unconfirmed; do not submit again" : "Document could not be changed");
        }
    }

    private Document requireDocument(String number) throws NoSuchFileException {
        Document document = documentDao.getDocument(number);
        if (document == null || document.getStatus() == 'D') throw new NoSuchFileException("Document unavailable");
        return document;
    }

    private void authorize(LoggedInInfo info, int documentNo) {
        IncomingDocumentCapacityResponse.requireRefileSourceAccess(securityInfoManager, info, documentNo);
        Set<Integer> patients = new HashSet<>();
        for (CtlDocument link : SpringUtils.getBean(CtlDocumentDao.class).findByDocumentNoAndModule(documentNo, "demographic")) {
            if (link.getId().getModuleId() != null) patients.add(link.getId().getModuleId());
        }
        for (PatientLabRouting route : SpringUtils.getBean(PatientLabRoutingDao.class).findDocByDemographic(documentNo)) {
            patients.add(route.getDemographicNo());
        }
        for (Integer patient : patients) {
            if (patient != null && patient > 0 && (!securityInfoManager.isAllowedAccessToPatientRecord(info, patient)
                    || !securityInfoManager.hasPrivilege(info, "_edoc", "w", patient.toString()))) {
                throw new SecurityException("Patient document write access denied");
            }
        }
    }

    protected SplitDocumentPdfWork.Prepared prepare(Path source, Path directory, SplitDocumentPdfWork.Operation operation,
                                                    List<SplitDocumentPdfWork.PageSelection> selections) throws IOException {
        return SplitDocumentPdfWork.prepare(source, directory, operation, selections);
    }

    private int persist(int sourceNo, int queue, LoggedInInfo info,
                        SplitDocumentPdfWork.Publication publication, String observedRevision) {
        TransactionTemplate transaction = new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        Integer result = transaction.execute(status -> {
            TransactionSynchronizationManager.registerSynchronization(publication);
            Document current = documentDao.findForPageMutation(sourceNo);
            if (current == null || current.getStatus() == 'D') throw new IllegalArgumentException("Document no longer available");
            File root = publication.prepared.directory.getParent().toFile();
            File currentFile = PathValidationUtils.validateExistingPath(new File(root, current.getDocfilename()), root);
            try {
                if (!currentFile.getCanonicalFile().toPath().equals(publication.source)) throw new SecurityException("Document identity changed");
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            authorize(info, sourceNo);
            if (!publication.replacement) IncomingDocumentCapacityResponse.requireQueueAccess(securityInfoManager, info, String.valueOf(queue));
            try {
                // Also cover other document-replacement paths that do not use this
                // source lease. A changed file must never receive prepared stale pages.
                StoredDocumentRevision.requireMatch(publication.source, observedRevision);
                publication.publish();
            }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
            if (publication.replacement) {
                documentDao.updatePageCount(sourceNo, publication.prepared.pageCount);
                return sourceNo;
            }
            return persistSplit(current, sourceNo, queue, info, publication);
        });
        if (result == null) throw new IllegalStateException("Document transaction did not return a result");
        return result;
    }

    private int persistSplit(Document source, int sourceNo, int queue, LoggedInInfo info,
                             SplitDocumentPdfWork.Publication publication) {
        String provider = info.getLoggedInProviderNo();
        CtlDocumentDao links = SpringUtils.getBean(CtlDocumentDao.class);
        CtlDocument primary = links.getCtrlDocument(sourceNo);
        String module = primary == null ? "demographic" : primary.getId().getModule();
        String moduleId = primary == null ? "-1" : String.valueOf(primary.getId().getModuleId());
        EDoc copy = new EDoc("", "", publication.target.getFileName().toString(), "", provider,
                source.getDoccreator(), "", 'A', LocalDate.now().toString(), "", "", module, moduleId,
                publication.prepared.pageCount);
        // EDoc's constructor generates a filename; persistence must use our exact publication.
        copy.setFileName(publication.target.getFileName().toString());
        copy.setDocPublic("0");
        copy.setContentType("application/pdf");
        copy.setProgramId(source.getProgramId());
        copy.setRestrictToProgram(Boolean.TRUE.equals(source.isRestrictToProgram()));
        int newNo = Integer.parseInt(EDocUtil.addDocumentSQL(copy));
        if (newNo <= 0) throw new IllegalStateException("Invalid persisted document identity");
        ProviderInboxRoutingDao inbox = SpringUtils.getBean(ProviderInboxRoutingDao.class);
        Set<String> providers = new HashSet<>();
        for (ProviderInboxItem item : inbox.getProvidersWithRoutingForDocument("DOC", sourceNo)) providers.add(item.getProviderNo());
        providers.add(provider);
        for (String recipient : providers) inbox.addToProviderInboxStrict(recipient, newNo, "DOC");
        SpringUtils.getBean(QueueDocumentLinkDao.class).addActiveQueueDocumentLink(queue, newNo);
        List<ProviderLabRoutingModel> routes = SpringUtils.getBean(ProviderLabRoutingDao.class).getProviderLabRoutingDocuments(sourceNo);
        if (!routes.isEmpty()) routeProvider(String.valueOf(newNo), routes.get(0).getProviderNo());
        PatientLabRoutingDao patientRoutes = SpringUtils.getBean(PatientLabRoutingDao.class);
        Set<Integer> patients = new HashSet<>();
        for (PatientLabRouting original : patientRoutes.findDocByDemographic(sourceNo)) {
            if (!patients.add(original.getDemographicNo())) continue;
            PatientLabRouting route = new PatientLabRouting();
            route.setDemographicNo(original.getDemographicNo());
            route.setLabNo(newNo);
            route.setLabType("DOC");
            patientRoutes.persist(route);
        }
        // Preserve the kind of the primary link (including provider/case modules),
        // and every demographic link rather than silently dropping all but one.
        if (primary != null) copyLink(links, primary, newNo);
        for (CtlDocument link : links.findByDocumentNoAndModule(sourceNo, "demographic")) copyLink(links, link, newNo);
        return newNo;
    }

    protected void routeProvider(String documentNo, String provider) { new ProviderLabRouting().routeMagic(Integer.parseInt(documentNo), provider, "DOC"); }

    private static void copyLink(CtlDocumentDao dao, CtlDocument source, int documentNo) {
        CtlDocument copy = new CtlDocument();
        copy.setId(new CtlDocumentPK(source.getId().getModule(), source.getId().getModuleId(), documentNo));
        copy.setStatus(source.getStatus());
        dao.merge(copy);
    }

    protected void invalidateCaches(Document document, int originalPages) throws IOException {
        for (int page = 1; page <= originalPages; page++) ManageDocument2Action.deleteCacheVersionChecked(document, page);
    }

    private String reject(int status, boolean accepted, boolean retryable, String message) throws IOException {
        write(status, JSON.createObjectNode().put("success", false).put("accepted", accepted)
                .put("retryable", retryable).put("error", message));
        return NONE;
    }

    private void write(int status, ObjectNode payload) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        JSON.writeValue(response.getWriter(), payload);
    }
}
