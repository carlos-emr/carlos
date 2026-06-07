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
 */
package io.github.carlos_emr.carlos.app.security;

import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("upload action binding security")
@Tag("unit")
@Tag("security")
class UploadActionBindingSecurityUnitTest {

    @Test
    @DisplayName("should not expose upload file setters to Struts parameter binding")
    void shouldOmitStrutsParameter_fromUploadBindingSetters() throws Exception {
        for (BindingMethod binding : uploadBindingMethods().toList()) {
            Method method = Class.forName(binding.className(), false, Thread.currentThread().getContextClassLoader())
                    .getMethod(binding.methodName(), binding.parameterType());

            assertThat(method.getAnnotation(StrutsParameter.class))
                    .as("%s#%s must rely on UploadedFilesAware instead of direct request binding",
                            binding.className(), binding.methodName())
                    .isNull();
        }
    }

    @Test
    @DisplayName("should classify every Upload2Action and keep multipart uploads on PathValidationUtils")
    void shouldKeepUploadActionsConsistent_withPathValidationUtilsPattern() throws Exception {
        Set<Path> classifiedActions = Stream.concat(
                        multipartUploadActionSources().map(Path::of),
                        nonMultipartUploadActionSources().map(Path::of))
                .collect(Collectors.toSet());
        Set<Path> discoveredActions;
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            discoveredActions = paths
                    .filter(path -> path.getFileName().toString().endsWith("Upload2Action.java"))
                    .collect(Collectors.toSet());
        }

        assertThat(classifiedActions)
                .as("Every *Upload2Action must be classified as multipart-upload or documented non-multipart")
                .containsExactlyInAnyOrderElementsOf(discoveredActions);

        for (String sourcePath : multipartUploadActionSources().toList()) {
            String source = Files.readString(Path.of(sourcePath));

            assertThat(source)
                    .as("%s must validate uploaded temp files through PathValidationUtils", sourcePath)
                    .containsPattern("PathValidationUtils\\.(validateUpload|validateUploadContent)");
            assertThat(source)
                    .as("%s must validate client filenames through approved filename validators", sourcePath)
                    .containsPattern("PathValidationUtils\\.(validateStrictFileName|validateFileName|validateGeneratedFileName)");
        }
    }

    private static Stream<String> multipartUploadActionSources() {
        return Stream.of(
                "src/main/java/io/github/carlos_emr/carlos/admin/web/ManageFlowsheetsUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on/web/BillingDocumentErrorReportUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on/web/ScheduleOfBenefitsUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/documentManager/actions/DocumentUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/eform/upload/HtmlUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/eform/upload/ImageUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/form/pageUtil/FrmXmlUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/integration/mcedt/mailbox/Upload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/lab/ca/all/pageUtil/InsideLabUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/lab/ca/all/pageUtil/LabUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/lab/ca/bc/PathNet/pageUtil/LabUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/lab/ca/on/CML/Upload/LabUpload2Action.java"
        );
    }

    private static Stream<String> nonMultipartUploadActionSources() {
        return Stream.of(
                // Raw Base64/request-stream signature upload; validates its generated temp path.
                "src/main/java/io/github/carlos_emr/carlos/signature/action/SaveSignatureUpload2Action.java",
                // MCEDT session submitter; actual mailbox file upload is the sibling mailbox action.
                "src/main/java/io/github/carlos_emr/carlos/integration/mcedt/Upload2Action.java",
                // View gates that render upload forms but do not receive multipart files.
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on/web/ViewBenefitScheduleUpload2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on/web/BillingOnUpload2Action.java"
        );
    }

    private static Stream<BindingMethod> uploadBindingMethods() {
        return Stream.of(
                binding("io.github.carlos_emr.carlos.admin.web.ManageFlowsheetsUpload2Action", "withUploadedFiles", List.class),
                binding("io.github.carlos_emr.carlos.demographic.pageUtil.ImportDemographicDataAction42Action", "setImportFile", File.class),
                binding("io.github.carlos_emr.carlos.demographic.pageUtil.ImportDemographicDataAction42Action", "setImportFileFileName", String.class),
                binding("io.github.carlos_emr.carlos.eform.upload.HtmlUpload2Action", "setFormHtml", File.class),
                binding("io.github.carlos_emr.carlos.eform.upload.HtmlUpload2Action", "setFormHtmlContentType", String.class),
                binding("io.github.carlos_emr.carlos.eform.upload.HtmlUpload2Action", "setFormHtmlFileName", String.class),
                binding("io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action", "setImage", File.class),
                binding("io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action", "setImageFileContentType", String.class),
                binding("io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action", "setImageFileName", String.class),
                binding("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementStyleSheet2Action", "setFile", File.class),
                binding("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementStyleSheet2Action", "setFileFileName", String.class),
                binding("io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action", "setFile1", File.class),
                binding("io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action", "setFile1ContentType", String.class),
                binding("io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action", "setFile1FileName", String.class),
                binding("io.github.carlos_emr.carlos.integration.mcedt.mailbox.Upload2Action", "setAddUploadFile", File.class),
                binding("io.github.carlos_emr.carlos.integration.mcedt.mailbox.Upload2Action", "setAddUploadFileContentType", String.class),
                binding("io.github.carlos_emr.carlos.integration.mcedt.mailbox.Upload2Action", "setAddUploadFileFileName", String.class),
                binding("io.github.carlos_emr.carlos.lab.ca.all.pageUtil.InsideLabUpload2Action", "setImportFiles", List.class),
                binding("io.github.carlos_emr.carlos.lab.ca.all.pageUtil.InsideLabUpload2Action", "setImportFilesContentType", List.class),
                binding("io.github.carlos_emr.carlos.lab.ca.all.pageUtil.InsideLabUpload2Action", "setImportFilesFileName", List.class),
                binding("io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabUpload2Action", "setImportFile", File.class),
                binding("io.github.carlos_emr.carlos.lab.ca.bc.PathNet.pageUtil.LabUpload2Action", "setImportFile", File.class),
                binding("io.github.carlos_emr.carlos.lab.ca.on.CML.Upload.LabUpload2Action", "setImportFile", File.class),
                binding("io.github.carlos_emr.carlos.login.UploadLoginText2Action", "setImportFile", File.class),
                binding("io.github.carlos_emr.carlos.provider.web.ProviderSignatureStamp2Action", "setImage", File.class),
                binding("io.github.carlos_emr.carlos.provider.web.ProviderSignatureStamp2Action", "setImageFileContentType", String.class),
                binding("io.github.carlos_emr.carlos.provider.web.ProviderSignatureStamp2Action", "setImageFileName", String.class),
                binding("io.github.carlos_emr.carlos.report.reportByTemplate.actions.UploadTemplates2Action", "setTemplateFile", File.class)
        );
    }

    private static BindingMethod binding(String className, String methodName, Class<?> parameterType) {
        return new BindingMethod(className, methodName, parameterType);
    }

    private record BindingMethod(String className, String methodName, Class<?> parameterType) {
    }
}
