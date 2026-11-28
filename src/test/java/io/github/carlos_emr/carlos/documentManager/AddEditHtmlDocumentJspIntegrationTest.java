/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocClassDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocTypeDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.documentManager.data.AddEditDocument2Form;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders the HTML document editor, documentManager/addedithtmldocument.jsp: the {@code success}
 * result of the {@code documentManager/ViewAddEditHtml} gate, and the {@code failed} result of
 * {@code documentManager/addEditHtml} after a validation retry.
 *
 * <p>4f6f848e7ca3 (#6135) made the editor post the stored document's content and metadata
 * revisions, read from {@link DocumentReviewService#baseline} when it opens a document and echoed
 * from the request when AddEditHtml2Action sends the form back with errors, so the save it
 * finally makes is checked against what the clinician saw. Reviewed is disabled when no content
 * baseline can be read, and it now validates and confirms before submitting. Before #6336 the
 * packaged document-add-link check exercised the inputs only indirectly, by saving successfully,
 * and never the disabled branch or the retried values.</p>
 */
@Tag("integration")
@Tag("documentManager")
class AddEditHtmlDocumentJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/documentManager/addedithtmldocument.jsp";
    private static final String PROVIDER = "999998";
    private static final int READABLE = 52;
    private static final int SOURCE_UNAVAILABLE = 53;
    private static final int RETRIED = 54;

    @TempDir
    static Path directory;
    private static JspRenderServer server;
    private static final DocumentReviewService REVIEWS = mock(DocumentReviewService.class);

    @BeforeAll
    static void startServer() throws Exception {
        DocumentDao documents = mock(DocumentDao.class);
        for (int documentNo : new int[] {READABLE, SOURCE_UNAVAILABLE}) {
            when(documents.findCtlDocsAndDocsByDocNo(documentNo)).thenReturn(List.<Object[]>of(storedHtmlDocument(documentNo)));
        }
        when(REVIEWS.baseline(any(), eq(READABLE)))
                .thenReturn(new DocumentReviewService.Baseline("FAKE-html-content-rev-52", "FAKE-html-metadata-rev-52"));
        when(REVIEWS.baseline(any(), eq(SOURCE_UNAVAILABLE)))
                .thenReturn(new DocumentReviewService.Baseline("", "FAKE-html-metadata-rev-53"));
        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .bean(DocumentReviewService.class, REVIEWS)
                .bean(DocumentDao.class, documents)
                .bean(ProviderDataDao.class, mock(ProviderDataDao.class))
                .bean(CtlDocTypeDao.class, mock(CtlDocTypeDao.class))
                .bean(CtlDocClassDao.class, mock(CtlDocClassDao.class))
                .bean(ProviderDao.class, mock(ProviderDao.class))
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldPostTheStoredRevisions_whenTheEditorOpensAnHtmlDocument() throws Exception {
        JspRenderServer.RenderedPage page = server.request(PAGE)
                .parameter("editDocumentNo", String.valueOf(READABLE))
                .render(AddEditHtmlDocumentJspIntegrationTest::signIn)
                .assertOk();

        Element form = editorForm(page);
        assertThat(only(form, "input[name=contentRevision]").val()).isEqualTo("FAKE-html-content-rev-52");
        assertThat(only(form, "input[name=metadataRevision]").val()).isEqualTo("FAKE-html-metadata-rev-52");
        assertThat(form.selectFirst("input[name=mode]").val()).isEqualTo(String.valueOf(READABLE));
        assertThat(form.selectFirst("textarea[name=html]").text()).isEqualTo("<p>FAKE html 52</p>");
        assertThat(reviewedButton(form).hasAttr("disabled")).isFalse();
        // reviewed() sets review intent only for a submit that validates, and clears it otherwise.
        assertThat(page.body())
                .contains("if (submitUpload(thisForm) && thisForm.reportValidity()) {")
                .contains("thisForm.reviewDoc.value = false;");
        verify(REVIEWS).baseline(any(), eq(READABLE));
    }

    @Test
    void shouldDisableReviewed_whenTheStoredSourceCannotBeRead() throws Exception {
        JspRenderServer.RenderedPage page = server.request(PAGE)
                .parameter("editDocumentNo", String.valueOf(SOURCE_UNAVAILABLE))
                .render(AddEditHtmlDocumentJspIntegrationTest::signIn)
                .assertOk();

        Element form = editorForm(page);
        assertThat(reviewedButton(form).hasAttr("disabled")).isTrue();
        assertThat(only(form, "input[name=contentRevision]").val()).isEmpty();
        assertThat(only(form, "input[name=metadataRevision]").val()).isEqualTo("FAKE-html-metadata-rev-53");
        // The HTML itself can still be saved: only the review needs the stored content's revision.
        assertThat(form.selectFirst("input[type=submit][name=Submit]").hasAttr("disabled")).isFalse();
    }

    @Test
    void shouldKeepThePostedRevisionsAndMetadata_whenAValidationRetryReturnsTheForm() throws Exception {
        String postedContent = "FAKE-content\" onfocus=\"alert(1)";
        String postedMetadata = "FAKE-metadata&<rev>";

        JspRenderServer.RenderedPage page = server.request(PAGE)
                .post()
                .parameter("mode", String.valueOf(RETRIED))
                .parameter("contentRevision", postedContent)
                .parameter("metadataRevision", postedMetadata)
                .render(request -> {
                    signIn(request);
                    // What AddEditHtml2Action sets when the description is missing.
                    Hashtable<String, String> errors = new Hashtable<>();
                    errors.put("descmissing", "dms.error.descriptionInvalid");
                    request.setAttribute("linkhtmlerrors", errors);
                    request.setAttribute("completedForm", submittedForm());
                    request.setAttribute("editDocumentNo", String.valueOf(RETRIED));
                })
                .assertOk();

        Element form = editorForm(page);
        List<Element> contentRevisions = form.select("input[name=contentRevision]");
        assertThat(contentRevisions).hasSize(1);
        assertThat(contentRevisions.get(0).val()).isEqualTo(postedContent);
        assertThat(contentRevisions.get(0).hasAttr("onfocus")).isFalse();
        assertThat(only(form, "input[name=metadataRevision]").val()).isEqualTo(postedMetadata);
        assertThat(form.selectFirst("input[name=mode]").val()).isEqualTo(String.valueOf(RETRIED));
        assertThat(form.selectFirst("input[name=sourceFacility]").val()).isEqualTo("FAKE Facility");
        assertThat(form.selectFirst("textarea[name=html]").text()).isEqualTo("<p>FAKE retried html</p>");
        assertThat(reviewedButton(form).hasAttr("disabled")).isFalse();
        verify(REVIEWS, never()).baseline(any(), eq(RETRIED));
    }

    @Test
    void shouldNotAskForABaseline_whenANewHtmlDocumentIsAdded() throws Exception {
        JspRenderServer.RenderedPage page = server.request(PAGE)
                .parameter("function", "demographic")
                .parameter("functionid", "1001")
                .render(AddEditHtmlDocumentJspIntegrationTest::signIn)
                .assertOk();

        // No document number, so no baseline: the page would fail parsing one if it asked.
        Element form = editorForm(page);
        assertThat(only(form, "input[name=contentRevision]").val()).isEmpty();
        assertThat(only(form, "input[name=metadataRevision]").val()).isEmpty();
        assertThat(form.selectFirst("input[name=mode]").val()).isEmpty();
        assertThat(form.select("input[type=button][value=Reviewed]")).isEmpty();
    }

    private static Element editorForm(JspRenderServer.RenderedPage page) {
        List<Element> forms = page.document().select("form[action$=/documentManager/addEditHtml]");
        assertThat(forms).hasSize(1);
        return forms.get(0);
    }

    /** The one element {@code selector} matches in {@code scope}; fails if there is none or more. */
    private static Element only(Element scope, String selector) {
        List<Element> matches = scope.select(selector);
        assertThat(matches).as(selector).hasSize(1);
        return matches.get(0);
    }

    private static Element reviewedButton(Element form) {
        List<Element> buttons = form.select("input[type=button][value=Reviewed]");
        assertThat(buttons).hasSize(1);
        return buttons.get(0);
    }

    /** A provider with document write rights; the baseline is read for this user. */
    private static void signIn(HttpServletRequest request) {
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", PROVIDER);
        LoggedInInfo provider = new LoggedInInfo();
        provider.setLoggedInProvider(new Provider(PROVIDER));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), provider);
    }

    /** The form AddEditHtml2Action.submittedForm() rebuilds from the refused POST. */
    private static AddEditDocument2Form submittedForm() {
        AddEditDocument2Form form = new AddEditDocument2Form();
        form.setFunction("demographic");
        form.setFunctionId("1001");
        form.setDocType("note");
        form.setDocDesc("");
        form.setDocCreator(PROVIDER);
        form.setResponsibleId(PROVIDER);
        form.setSource("FAKE Source");
        form.setSourceFacility("FAKE Facility");
        form.setObservationDate("2026/01/01");
        form.setReviewerId("");
        form.setReviewDateTime("");
        form.setHtml("<p>FAKE retried html</p>");
        return form;
    }

    /** An HTML document's rows as DocumentDao.findCtlDocsAndDocsByDocNo returns them for EDocUtil.getDoc. */
    private static Object[] storedHtmlDocument(int documentNo) {
        Document document = new Document();
        document.setDocumentNo(documentNo);
        document.setDocdesc("FAKE html document " + documentNo);
        document.setDoctype("note");
        document.setDocfilename("");
        document.setDocxml("<p>FAKE html " + documentNo + "</p>");
        document.setDoccreator(PROVIDER);
        document.setResponsible(PROVIDER);
        document.setSource("FAKE Source");
        document.setStatus('H');
        document.setContenttype("text/html");
        document.setPublic1(0);
        document.setObservationdate(new Date(1767225600000L));
        document.setUpdatedatetime(new Date(1767225600000L));
        CtlDocument link = new CtlDocument();
        link.setId(new CtlDocumentPK("demographic", 1001, documentNo));
        link.setStatus("H");
        return new Object[] {document, link};
    }
}
