/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.upload;

import io.github.carlos_emr.carlos.casemgmt.web.ClientImage2Action;
import io.github.carlos_emr.carlos.commn.dao.MeasurementCSSLocationDao;
import io.github.carlos_emr.carlos.demographic.pageUtil.ImportDemographicDataAction42Action;
import io.github.carlos_emr.carlos.documentManager.actions.AddEditDocument2Action;
import io.github.carlos_emr.carlos.documentManager.actions.DocumentUpload2Action;
import io.github.carlos_emr.carlos.eform.actions.ManageEForm2Action;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementStyleSheet2Action;
import io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action;
import io.github.carlos_emr.carlos.integration.mcedt.Update2Action;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.Upload2Action;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.InsideLabUpload2Action;
import io.github.carlos_emr.carlos.login.UploadLoginText2Action;
import io.github.carlos_emr.carlos.provider.web.ProviderSignatureStamp2Action;
import io.github.carlos_emr.carlos.report.reportByTemplate.actions.UploadTemplates2Action;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Upload Parameter Binding Regression Unit Tests")
@Tag("unit")
class UploadParameterBindingRegressionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("upload metadata setters should not be Struts parameter-bound")
    void uploadMetadataSettersShouldNotBeStrutsParameterBound() throws Exception {
        assertNotParameterBound(AddEditDocument2Action.class, "setDocFile", File.class);
        assertNotParameterBound(AddEditDocument2Action.class, "setFiledata", File.class);
        assertNotParameterBound(AddEditDocument2Action.class, "setDocFileFileName", String.class);
        assertNotParameterBound(AddEditDocument2Action.class, "setDocFileContentType", String.class);

        assertNotParameterBound(DocumentUpload2Action.class, "setDocFile", File.class);
        assertNotParameterBound(DocumentUpload2Action.class, "setFiledata", File.class);
        assertNotParameterBound(DocumentUpload2Action.class, "setFiledataFileName", String.class);
        assertNotParameterBound(DocumentUpload2Action.class, "setFiledataContentType", String.class);

        assertNotParameterBound(HtmlUpload2Action.class, "setFormHtml", File.class);
        assertNotParameterBound(HtmlUpload2Action.class, "setFormHtmlContentType", String.class);
        assertNotParameterBound(HtmlUpload2Action.class, "setFormHtmlFileName", String.class);

        assertNotParameterBound(ImageUpload2Action.class, "setImage", File.class);
        assertNotParameterBound(ImageUpload2Action.class, "setImageFileName", String.class);
        assertNotParameterBound(ImageUpload2Action.class, "setImageFileContentType", String.class);

        assertNotParameterBound(ClientImage2Action.class, "setClientImage", File.class);
        assertNotParameterBound(ManageEForm2Action.class, "setZippedForm", File.class);
        assertNotParameterBound(UploadTemplates2Action.class, "setTemplateFile", File.class);
        assertNotParameterBound(FrmXmlUpload2Action.class, "setFile1", File.class);
        assertNotParameterBound(FrmXmlUpload2Action.class, "setFile1FileName", String.class);
        assertNotParameterBound(FrmXmlUpload2Action.class, "setFile1ContentType", String.class);
        assertNotParameterBound(EctAddMeasurementStyleSheet2Action.class, "setFile", File.class);
        assertNotParameterBound(EctAddMeasurementStyleSheet2Action.class, "setFileFileName", String.class);
        assertNotParameterBound(ImportDemographicDataAction42Action.class, "setImportFile", File.class);
        assertNotParameterBound(ImportDemographicDataAction42Action.class, "setImportFileFileName", String.class);
        assertNotParameterBound(Update2Action.class, "setContent", File.class);
        assertNotParameterBound(UploadLoginText2Action.class, "setImportFile", File.class);
        assertNotParameterBound(ProviderSignatureStamp2Action.class, "setImage", File.class);
        assertNotParameterBound(ProviderSignatureStamp2Action.class, "setImageFileName", String.class);
        assertNotParameterBound(ProviderSignatureStamp2Action.class, "setImageFileContentType", String.class);
        assertNotParameterBound(Upload2Action.class, "setAddUploadFile", File.class);
        assertNotParameterBound(Upload2Action.class, "setAddUploadFileFileName", String.class);
        assertNotParameterBound(Upload2Action.class, "setAddUploadFileContentType", String.class);
        assertNotParameterBound(InsideLabUpload2Action.class, "setImportFiles", List.class);
        assertNotParameterBound(InsideLabUpload2Action.class, "setImportFilesFileName", List.class);
        assertNotParameterBound(InsideLabUpload2Action.class, "setImportFilesContentType", List.class);
        assertGetterNotParameterBound(InsideLabUpload2Action.class, "getImportFiles");
        assertNotParameterBound(io.github.carlos_emr.carlos.lab.ca.bc.PathNet.pageUtil.LabUpload2Action.class,
                "setImportFile", File.class);
        assertNotParameterBound(io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabUpload2Action.class,
                "setImportFile", File.class);
        assertNotParameterBound(io.github.carlos_emr.carlos.lab.ca.on.CML.Upload.LabUpload2Action.class,
                "setImportFile", File.class);
    }

    @Test
    @DisplayName("UploadedFilesAware actions should bind only their expected upload field")
    void uploadedFilesAwareActionsShouldBindOnlyTheirExpectedUploadField(@TempDir Path tempDir) throws IOException {
        registerMock(MeasurementCSSLocationDao.class, mock(MeasurementCSSLocationDao.class));

        File ignoredFile = Files.createFile(tempDir.resolve("ignored-upload.tmp")).toFile();
        File measurementFile = Files.createFile(tempDir.resolve("measurement.css")).toFile();
        File importFile = Files.createFile(tempDir.resolve("import.zip")).toFile();
        File contentFile = Files.createFile(tempDir.resolve("content.bin")).toFile();

        EctAddMeasurementStyleSheet2Action measurementAction = mock(EctAddMeasurementStyleSheet2Action.class, CALLS_REAL_METHODS);
        measurementAction.withUploadedFiles(List.of(
                uploadedFile("ignored", ignoredFile, "ignored.txt"),
                uploadedFile("file", measurementFile, "measurement.css")));
        assertThat(measurementAction.getFile()).isEqualTo(measurementFile);
        assertThat(measurementAction.getFileName()).isEqualTo("measurement.css");

        ImportDemographicDataAction42Action importAction = mock(ImportDemographicDataAction42Action.class, CALLS_REAL_METHODS);
        importAction.withUploadedFiles(List.of(
                uploadedFile("ignored", ignoredFile, "ignored.txt"),
                uploadedFile("importFile", importFile, "import.zip")));
        assertThat(importAction.getImportFile()).isEqualTo(importFile);
        assertThat(importAction.getImportFileFileName()).isEqualTo("import.zip");

        Update2Action updateAction = mock(Update2Action.class, CALLS_REAL_METHODS);
        updateAction.withUploadedFiles(List.of(
                uploadedFile("ignored", ignoredFile, "ignored.txt"),
                uploadedFile("content", contentFile, "content.bin")));
        assertThat(updateAction.getContent()).isEqualTo(contentFile.getCanonicalFile());
    }

    private static void assertNotParameterBound(Class<?> actionClass, String methodName, Class<?> parameterType)
            throws NoSuchMethodException {
        Method method;
        try {
            method = actionClass.getMethod(methodName, parameterType);
        } catch (NoSuchMethodException e) {
            return;
        }
        assertThat(method.isAnnotationPresent(StrutsParameter.class))
                .as("%s#%s should only be populated by UploadedFilesAware", actionClass.getSimpleName(), methodName)
                .isFalse();
    }

    private static void assertGetterNotParameterBound(Class<?> actionClass, String methodName)
            throws NoSuchMethodException {
        Method method = actionClass.getMethod(methodName);
        assertThat(method.isAnnotationPresent(StrutsParameter.class))
                .as("%s#%s should only be populated by UploadedFilesAware", actionClass.getSimpleName(), methodName)
                .isFalse();
    }

    private static UploadedFile uploadedFile(String inputName, File file, String originalName) {
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn(inputName);
        when(uploadedFile.getAbsolutePath()).thenReturn(file.getAbsolutePath());
        when(uploadedFile.getContent()).thenReturn(file);
        when(uploadedFile.getOriginalName()).thenReturn(originalName);
        return uploadedFile;
    }
}
