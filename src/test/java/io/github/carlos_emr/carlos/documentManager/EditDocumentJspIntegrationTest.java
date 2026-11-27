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
import io.github.carlos_emr.carlos.commn.dao.DocumentExtraReviewerDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Renders the Edit Document popup, documentManager/editDocument.jsp: the {@code success} result of
 * the {@code documentManager/ViewEditDocument} gate, and the {@code failEdit} result of
 * {@code documentManager/addEditDocument} after a refused save.
 *
 * <p>4f6f848e7ca3 (#6135) made the page read the stored document's content and metadata
 * revisions from {@link DocumentReviewService#baseline} and post them back, so a review or a file
 * replacement from an out-of-date popup is refused. When the stored file cannot be read the
 * baseline's content revision is empty: the page then says so in an alert and disables Reviewed
 * and the replacement file input. A popup re-rendered after a refused save echoes the revisions it
 * was posted rather than reading new ones. The review button now validates and confirms before it
 * submits, and the {@code <font>} markup became spans and a label. Before #6336 the disabled
 * controls and the alert were asserted only at action level ({@code AddEditDocument2ActionUnitTest})
 * and never in a rendered page.</p>
 */
@Tag("integration")
@Tag("documentManager")
class EditDocumentJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/documentManager/editDocument.jsp";
    private static final String PROVIDER = "999998";
    private static final int READABLE = 42;
    private static final int SOURCE_UNAVAILABLE = 43;
    private static final int RETRIED = 44;

    @TempDir
    static Path directory;
    private static JspRenderServer server;
    private static final DocumentReviewService REVIEWS = mock(DocumentReviewService.class);

    @BeforeAll
    static void startServer() throws Exception {
        DocumentDao documents = mock(DocumentDao.class);
        for (int documentNo : new int[] {READABLE, SOURCE_UNAVAILABLE, RETRIED}) {
            when(documents.findCtlDocsAndDocsByDocNo(documentNo)).thenReturn(List.<Object[]>of(storedDocument(documentNo)));
        }
        when(REVIEWS.baseline(any(), eq(READABLE)))
                .thenReturn(new DocumentReviewService.Baseline("FAKE-content-rev-42", "FAKE-metadata-rev-42"));
        when(REVIEWS.baseline(any(), eq(SOURCE_UNAVAILABLE)))
                .thenReturn(new DocumentReviewService.Baseline("", "FAKE-metadata-rev-43"));
        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .bean(DocumentReviewService.class, REVIEWS)
                .bean(DocumentDao.class, documents)
                .bean(DocumentExtraReviewerDao.class, mock(DocumentExtraReviewerDao.class))
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
    void shouldPostTheStoredRevisions_whenTheEditorOpensADocument() throws Exception {
        JspRenderServer.RenderedPage page = server.request(PAGE)
                .parameter("editDocumentNo", String.valueOf(READABLE))
                .render(EditDocumentJspIntegrationTest::signIn)
                .assertOk();

        Element form = page.document().selectFirst("form[action$=/documentManager/addEditDocument]");
        assertThat(form.attr("method")).isEqualToIgnoringCase("post");
        assertThat(only(form, "input[name=contentRevision]").val()).isEqualTo("FAKE-content-rev-42");
        assertThat(only(form, "input[name=metadataRevision]").val()).isEqualTo("FAKE-metadata-rev-42");
        assertThat(form.selectFirst("input[name=mode]").val()).isEqualTo(String.valueOf(READABLE));
        assertThat(form.selectFirst("input[name=docDesc]").val()).isEqualTo("FAKE description 42");
        assertThat(page.document().select("div[role=alert]")).isEmpty();
        assertThat(reviewedButton(form).hasAttr("disabled")).isFalse();
        assertThat(form.getElementById("replacementDocFile").hasAttr("disabled")).isFalse();
        assertThat(form.selectFirst("label[for=replacementDocFile] span.comment").text()).isEqualTo("(blank to keep file)");
        assertThat(page.document().select("font")).isEmpty();
        // reviewed() sets review intent only for a submit that validates, and clears it otherwise.
        assertThat(page.body())
                .contains("if (submitUpload(thisForm) && thisForm.reportValidity()) {")
                .contains("thisForm.reviewDoc.value = false;");
        verify(REVIEWS).baseline(any(), eq(READABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr-CA"})
    void shouldDisableReviewAndReplacement_whenTheStoredSourceCannotBeRead(String language) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));

        JspRenderServer.RenderedPage page = server.request(PAGE)
                .header("Accept-Language", language)
                .parameter("editDocumentNo", String.valueOf(SOURCE_UNAVAILABLE))
                .render(EditDocumentJspIntegrationTest::signIn)
                .assertOk();

        List<Element> alerts = page.document().select("div[role=alert]");
        assertThat(alerts).hasSize(1);
        Element alert = alerts.get(0);
        assertThat(alert.hasClass("warning")).isTrue();
        assertThat(alert.wholeText()).isEqualTo(bundle.getString("documentMutation.sourceUnavailable"));
        Element form = page.document().selectFirst("form[action$=/documentManager/addEditDocument]");
        assertThat(alert.nextElementSibling()).isSameAs(form);
        assertThat(reviewedButton(form).hasAttr("disabled")).isTrue();
        assertThat(form.getElementById("replacementDocFile").hasAttr("disabled")).isTrue();
        assertThat(only(form, "input[name=contentRevision]").val()).isEmpty();
        assertThat(only(form, "input[name=metadataRevision]").val()).isEqualTo("FAKE-metadata-rev-43");
        // Metadata stays editable: only attestation and replacement need the stored bytes.
        assertThat(form.selectFirst("input[type=submit][name=Submit]").hasAttr("disabled")).isFalse();
    }

    @Test
    void shouldEchoThePostedRevisionsWithoutReadingNewOnes_whenASaveWasRefused() throws Exception {
        String postedContent = "FAKE-content\" onfocus=\"alert(1)";
        String postedMetadata = "FAKE-metadata&<rev>";
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);

        JspRenderServer.RenderedPage page = server.request(PAGE)
                .post()
                .parameter("mode", String.valueOf(RETRIED))
                .parameter("contentRevision", postedContent)
                .parameter("metadataRevision", postedMetadata)
                .render(request -> {
                    signIn(request);
                    // What AddEditDocument2Action sets when the stored source changed under the editor.
                    Hashtable<String, String> errors = new Hashtable<>();
                    errors.put("sourcechanged", "dms.error.documentChanged");
                    request.setAttribute("docerrors", errors);
                    request.setAttribute("editDocumentNo", String.valueOf(RETRIED));
                })
                .assertOk();

        Element form = page.document().selectFirst("form[action$=/documentManager/addEditDocument]");
        List<Element> contentRevisions = form.select("input[name=contentRevision]");
        assertThat(contentRevisions).hasSize(1);
        assertThat(contentRevisions.get(0).val()).isEqualTo(postedContent);
        assertThat(contentRevisions.get(0).hasAttr("onfocus")).isFalse();
        assertThat(only(form, "input[name=metadataRevision]").val()).isEqualTo(postedMetadata);
        assertThat(page.document().select("span.warning")).hasSize(1);
        assertThat(page.document().selectFirst("span.warning").wholeText())
                .isEqualTo("Error: " + bundle.getString("dms.error.documentChanged"));
        assertThat(page.document().select("font")).isEmpty();
        assertThat(page.document().select("div[role=alert]")).isEmpty();
        assertThat(reviewedButton(form).hasAttr("disabled")).isFalse();
        verify(REVIEWS, never()).baseline(any(), eq(RETRIED));
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

    /** The document and control rows DocumentDao.findCtlDocsAndDocsByDocNo returns for EDocUtil.getDoc. */
    private static Object[] storedDocument(int documentNo) {
        Document document = new Document();
        document.setDocumentNo(documentNo);
        document.setDocdesc("FAKE description " + documentNo);
        document.setDoctype("lab");
        document.setDocfilename("FAKE-document-" + documentNo + ".pdf");
        document.setDoccreator(PROVIDER);
        document.setResponsible(PROVIDER);
        document.setSource("FAKE Source");
        document.setStatus('A');
        document.setContenttype("application/pdf");
        document.setPublic1(0);
        document.setObservationdate(new Date(1767225600000L));
        document.setUpdatedatetime(new Date(1767225600000L));
        CtlDocument link = new CtlDocument();
        link.setId(new CtlDocumentPK("demographic", 1001, documentNo));
        link.setStatus("A");
        return new Object[] {document, link};
    }
}
