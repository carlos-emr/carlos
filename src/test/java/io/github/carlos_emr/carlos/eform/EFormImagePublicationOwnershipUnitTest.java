/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A completed import or image upload must keep the image it accepted, even when another
 * import fails after exposing that same public name.
 *
 * @since 2026-11-17
 */
@Tag("unit")
@Tag("eform")
@Tag("security")
class EFormImagePublicationOwnershipUnitTest {
    @TempDir
    Path scratch;

    private enum Consumer { FORM_IMPORT, IMAGE_UPLOAD }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Consumer.class)
    @DisplayName("should retain a successful consumer's image when an overlapping import rolls back")
    void shouldRetainCommittedImage_whenAnOverlappingImportRollsBack(Consumer consumer) throws Exception {
        Path images = Files.createDirectory(scratch.resolve("images"));
        byte[] archive = archive(Map.of("first.png", "FAKE first image", "second.png", "FAKE second image"));
        var firstPublished = new CountDownLatch(1);
        var consumerFinished = new CountDownLatch(1);
        var publishedName = new AtomicReference<Path>();
        var publicationCount = new AtomicInteger();
        var failure = new IOException("FAKE later publication failure");

        try (var workers = Executors.newFixedThreadPool(2)) {
            var failingImport = workers.submit(() -> {
                try (MockedStatic<ImageUpload2Action> folder = mockStatic(ImageUpload2Action.class);
                     MockedStatic<Files> filesystem = mockStatic(Files.class, call -> {
                         if (call.getMethod().getName().equals("createLink")) {
                             if (publicationCount.incrementAndGet() > 1) throw failure;
                             Object linked = call.callRealMethod();
                             publishedName.set(call.getArgument(0));
                             firstPublished.countDown();
                             // An unguarded consumer can commit here. A guarded one waits until
                             // this import's failure/rollback completes; either ordering must
                             // leave its eventually successful image intact.
                             if (!consumerFinished.await(5, TimeUnit.SECONDS)) {
                                 assertThat(publicationCount.get()).isEqualTo(1);
                             }
                             return linked;
                         }
                         return call.callRealMethod();
                     })) {
                    folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
                    try {
                        new EFormExportZip().importForm(new ByteArrayInputStream(archive));
                        throw new AssertionError("The later publication was not refused");
                    } catch (IOException expected) {
                        return expected;
                    }
                }
            });

            assertThat(firstPublished.await(15, TimeUnit.SECONDS)).isTrue();
            Path shared = publishedName.get();
            var successfulConsumer = workers.submit(() -> {
                try (MockedStatic<ImageUpload2Action> folder = mockStatic(ImageUpload2Action.class)) {
                    folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
                    return consumer == Consumer.FORM_IMPORT ? importReferencing(shared) : uploadReplacing(shared);
                } finally {
                    consumerFinished.countDown();
                }
            });

            String acceptedBytes = successfulConsumer.get(25, TimeUnit.SECONDS);
            assertThat(failingImport.get(25, TimeUnit.SECONDS)).isSameAs(failure);
            assertThat(publicationCount.get()).isEqualTo(2);
            assertThat(shared).as("the completed consumer's public image").exists();
            assertThat(Files.readString(shared)).isEqualTo(acceptedBytes);
        }
    }

    private String importReferencing(Path shared) throws Exception {
        var accepted = new AtomicReference<String>();
        var registrations = new AtomicInteger();
        String html = "<img src=\"${oscar_image_path}" + shared.getFileName() + "\">";
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("eform.properties", "form.name=FAKE adopting form\nform.htmlFilename=form.html\n");
        entries.put("form.html", html);
        entries.put(shared.getFileName().toString(), "FAKE importing consumer image");
        try (MockedStatic<EFormUtil> forms = mockStatic(EFormUtil.class)) {
            forms.when(() -> EFormUtil.saveEForm(any(EForm.class))).thenAnswer(call -> {
                assertThat(call.<EForm>getArgument(0).getFormHtml()).isEqualTo(html);
                accepted.set(Files.readString(shared));
                registrations.incrementAndGet();
                return "FAKE committed form";
            });
            new EFormExportZip().importForm(new ByteArrayInputStream(archive(entries)));
        }
        assertThat(registrations.get()).isEqualTo(1);
        return accepted.get();
    }

    private String uploadReplacing(Path shared) throws Exception {
        Path source = Files.writeString(scratch.resolve("upload.png"), "FAKE uploaded consumer image");
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_eform"), eq("w"), isNull())).thenReturn(true);
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            var action = new ImageUpload2Action();
            action.setImage(source.toFile());
            action.setImageFileName(shared.getFileName().toString());
            assertThat(action.execute()).isEqualTo("success");
            assertThat(request.getAttribute("status")).isEqualTo("uploaded");
        }
        return Files.readString(shared);
    }

    private static byte[] archive(Map<String, String> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
