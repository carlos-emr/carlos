/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.upload;

import io.github.carlos_emr.carlos.casemgmt.web.ClientImage2Action;
import io.github.carlos_emr.carlos.documentManager.actions.AddEditDocument2Action;
import io.github.carlos_emr.carlos.documentManager.actions.DocumentUpload2Action;
import io.github.carlos_emr.carlos.eform.actions.ManageEForm2Action;
import io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.Upload2Action;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.InsideLabUpload2Action;
import io.github.carlos_emr.carlos.login.UploadLoginText2Action;
import io.github.carlos_emr.carlos.provider.web.ProviderSignatureStamp2Action;
import io.github.carlos_emr.carlos.report.reportByTemplate.actions.UploadTemplates2Action;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Upload Parameter Binding Regression Unit Tests")
@Tag("unit")
class UploadParameterBindingRegressionUnitTest {

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

    private static void assertNotParameterBound(Class<?> actionClass, String methodName, Class<?> parameterType)
            throws NoSuchMethodException {
        Method method = actionClass.getMethod(methodName, parameterType);
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
}
