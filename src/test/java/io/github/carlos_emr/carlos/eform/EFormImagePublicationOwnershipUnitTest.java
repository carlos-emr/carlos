/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private enum CopyFailure { READ, WRITE, CLOSE, MOVE }
    private enum CleanupFailure { IO, SECURITY }

    @Test
    void shouldRefuseReplacingUnwritableImage_withoutChangingItsBytesOrPermissions() throws Exception {
        Path images = Files.createDirectory(scratch.resolve("images"));
        Path shared = Files.writeString(images.resolve("existing.png"), "FAKE previously committed image");
        boolean posix = Files.getFileStore(images).supportsFileAttributeView(java.nio.file.attribute.PosixFileAttributeView.class);
        var originalPermissions = posix ? Files.getPosixFilePermissions(shared) : null;
        try (var folder = mockStatic(ImageUpload2Action.class);
             var files = mockStatic(Files.class, call -> {
                 // Model the filesystem's effective access denial, including under root CI.
                 if (call.getMethod().getName().equals("isWritable") && shared.equals(call.getArgument(0))) return false;
                 return call.callRealMethod();
             })) {
            folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(uploadReplacing(shared, "error")).isEqualTo("FAKE previously committed image");
        }
        if (posix) assertThat(Files.getPosixFilePermissions(shared)).isEqualTo(originalPermissions);
        try (var remaining = Files.list(images)) {
            assertThat(remaining.toList()).containsExactlyInAnyOrder(shared, images.resolve("extractFolder"));
        }
    }

    @ParameterizedTest
    @EnumSource(CleanupFailure.class)
    void shouldReportPublishedImageAsUploaded_whenStagingDirectoryCleanupFails(CleanupFailure mode) throws Exception {
        Path images = Files.createDirectory(scratch.resolve("images"));
        Path shared = Files.writeString(images.resolve("existing.png"), "FAKE previously committed image");
        Exception cleanup = mode == CleanupFailure.IO
                ? new IOException("FAKE staging cleanup refusal") : new SecurityException("FAKE staging cleanup refusal");
        var failures = new AtomicInteger();
        try (var folder = mockStatic(ImageUpload2Action.class);
             var capture = LogCapture.forLogger(ImageUpload2Action.class.getName() + "$StagingCleanup");
             var files = mockStatic(Files.class, call -> {
                 if (call.getMethod().getName().equals("deleteIfExists")) {
                     Path path = call.getArgument(0);
                     if (images.equals(path.getParent()) && path.getFileName().toString().startsWith(".carlos-image-")) {
                         failures.incrementAndGet();
                         throw cleanup;
                     }
                 }
                 return call.callRealMethod();
             })) {
            folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(uploadReplacing(shared)).isEqualTo("FAKE uploaded consumer image");
            assertThat(failures.get()).isEqualTo(1);
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(org.apache.logging.log4j.Level.WARN);
                assertThat(event.getMessage().getFormattedMessage()).contains("Image upload succeeded");
                assertThat(event.getThrown()).isSameAs(cleanup);
            });
        }
        assertThat(Files.readString(shared)).isEqualTo("FAKE uploaded consumer image");
    }

    @ParameterizedTest
    @EnumSource(CleanupFailure.class)
    void shouldPreserveCopyFailure_whenStagingDirectoryCleanupAlsoFails(CleanupFailure mode) throws Exception {
        Path images = Files.createDirectory(scratch.resolve("images"));
        Path shared = Files.writeString(images.resolve("existing.png"), "FAKE previously committed image");
        var primary = new IOException("FAKE primary copy refusal");
        Exception cleanup = mode == CleanupFailure.IO
                ? new IOException("FAKE staging cleanup refusal") : new SecurityException("FAKE staging cleanup refusal");
        try (var folder = mockStatic(ImageUpload2Action.class);
             var capture = LogCapture.forLogger(ImageUpload2Action.class);
             var paths = mockStatic(PathValidationUtils.class, call -> {
                 if (call.getMethod().getName().equals("openValidatedUploadInputStream")) {
                     return new java.io.FilterInputStream((java.io.InputStream) call.callRealMethod()) {
                         @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                             throw primary;
                         }
                     };
                 }
                 return call.callRealMethod();
             });
             var files = mockStatic(Files.class, call -> {
                 if (call.getMethod().getName().equals("deleteIfExists")) {
                     Path path = call.getArgument(0);
                     if (images.equals(path.getParent()) && path.getFileName().toString().startsWith(".carlos-image-")) {
                         throw cleanup;
                     }
                 }
                 return call.callRealMethod();
             })) {
            folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(uploadReplacing(shared, "error")).isEqualTo("FAKE previously committed image");
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(org.apache.logging.log4j.Level.ERROR);
                assertThat(event.getThrown()).isSameAs(primary);
                assertThat(event.getThrown().getSuppressed()).containsExactly(cleanup);
            });
        }
    }

    @Test
    void shouldRollbackOwnPublishedAssets_whenALaterPathValidationFails() throws Exception {
        Path images = Files.createDirectory(scratch.resolve("images"));
        Path existing = Files.writeString(images.resolve("existing.png"), "FAKE previously committed image");
        var failure = new SecurityException("FAKE later containment refusal");
        var validations = new AtomicInteger();
        try (var folder = mockStatic(ImageUpload2Action.class);
             var paths = mockStatic(PathValidationUtils.class, call -> {
                 if (call.getMethod().getName().equals("validateExistingPath")
                         && call.<java.io.File>getArgument(0).isFile()
                         && validations.incrementAndGet() == 2) throw failure;
                 return call.callRealMethod();
             })) {
            folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            byte[] archive = archive(Map.of("first.png", "FAKE first", "second.png", "FAKE second"));
            assertThatThrownBy(() -> new EFormExportZip().importForm(new ByteArrayInputStream(archive)))
                    .isSameAs(failure);
        }
        assertThat(validations.get()).isEqualTo(2);
        assertThat(images.resolve("first.png")).doesNotExist();
        assertThat(images.resolve("second.png")).doesNotExist();
        assertThat(Files.readString(existing)).isEqualTo("FAKE previously committed image");
    }

    @Test
    void shouldPreserveTheOriginalFailure_whenRollbackDeletionIsDenied() throws Exception {
        Path staged = Files.writeString(scratch.resolve("staged.png"), "FAKE staged image");
        Path published = Files.createLink(scratch.resolve("published.png"), staged);
        var primary = new IOException("FAKE original import failure");
        var cleanup = new SecurityException("FAKE rollback permission refusal");
        try (var files = mockStatic(Files.class, call -> {
            if (call.getMethod().getName().equals("delete") && published.equals(call.getArgument(0))) throw cleanup;
            return call.callRealMethod();
        })) {
            assertThatCode(() -> EFormExportZip.rollbackPublishedAssets(Map.of(published, staged), primary))
                    .doesNotThrowAnyException();
        }
        assertThat(primary.getSuppressed()).containsExactly(cleanup);
    }

    @ParameterizedTest
    @EnumSource(CopyFailure.class)
    void shouldKeepThePreviousImage_whenAnUploadCopyFails(CopyFailure failureMode) throws Exception {
        Path images = Files.createDirectory(scratch.resolve("images"));
        Path shared = Files.writeString(images.resolve("existing.png"), "FAKE previously committed image");
        var failure = new IOException("FAKE image copy refusal");
        try (var folder = mockStatic(ImageUpload2Action.class);
             var paths = mockStatic(PathValidationUtils.class, call -> {
                 if (call.getMethod().getName().equals("openValidatedUploadInputStream")) {
                     return new java.io.FilterInputStream((java.io.InputStream) call.callRealMethod()) {
                         private int reads;
                         @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                             if (failureMode == CopyFailure.READ && reads++ > 0) throw failure;
                             return super.read(bytes, offset, failureMode == CopyFailure.READ ? Math.min(5, length) : length);
                         }
                         @Override public void close() throws IOException {
                             super.close();
                             if (failureMode == CopyFailure.CLOSE) throw failure;
                         }
                     };
                 }
                 return call.callRealMethod();
             });
             var files = mockStatic(Files.class, call -> {
                 if (failureMode == CopyFailure.MOVE && call.getMethod().getName().equals("move")) {
                     throw new java.nio.file.AtomicMoveNotSupportedException(
                             call.getArgument(0).toString(), call.getArgument(1).toString(), "FAKE unsupported atomic publication");
                 }
                 if (failureMode == CopyFailure.WRITE && call.getMethod().getName().equals("newOutputStream")
                         && call.<Path>getArgument(0).startsWith(images)) {
                     return new java.io.FilterOutputStream((java.io.OutputStream) call.callRealMethod()) {
                         @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                             super.write(bytes, offset, length);
                             throw failure;
                         }
                     };
                 }
                 return call.callRealMethod();
             })) {
            folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(uploadReplacing(shared, "error")).isEqualTo("FAKE previously committed image");
        }
        try (var remaining = Files.list(images)) {
            assertThat(remaining.toList()).containsExactlyInAnyOrder(shared, images.resolve("extractFolder"));
        }
        try (var staging = Files.list(images.resolve("extractFolder"))) {
            assertThat(staging.toList()).containsExactly(images.resolve("extractFolder")
                    .resolve(EFormImagePublicationLock.LOCK_FILE_NAME));
        }
    }

    @Test
    void shouldPublishCompleteImageBytes_whenPosixPermissionsAreUnsupported() throws Exception {
        Path images = Files.createDirectory(scratch.resolve("images"));
        Path shared = Files.writeString(images.resolve("existing.png"), "FAKE previously committed image");
        var store = mock(java.nio.file.FileStore.class);
        when(store.supportsFileAttributeView(java.nio.file.attribute.PosixFileAttributeView.class)).thenReturn(false);
        try (var folder = mockStatic(ImageUpload2Action.class);
             var files = mockStatic(Files.class, call -> {
                 if (call.getMethod().getName().equals("getFileStore") && images.equals(call.getArgument(0))) return store;
                 return call.callRealMethod();
             })) {
            folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(uploadReplacing(shared)).isEqualTo("FAKE uploaded consumer image");
        }
    }

    @Test
    void shouldPreserveImagePermissions_andKeepThePreviousBytesUntilTheCopyCloses() throws Exception {
        Path images = Files.createDirectory(scratch.resolve("images"));
        Path shared = Files.writeString(images.resolve("existing.png"), "FAKE previously committed image");
        boolean posix = Files.getFileStore(images).supportsFileAttributeView(java.nio.file.attribute.PosixFileAttributeView.class);
        var permissions = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----");
        if (posix) Files.setPosixFilePermissions(shared, permissions);
        var closed = new AtomicInteger();
        try (var folder = mockStatic(ImageUpload2Action.class);
             var files = mockStatic(Files.class, call -> {
                 if (call.getMethod().getName().equals("newOutputStream") && call.<Path>getArgument(0).startsWith(images)) {
                     Path staged = call.getArgument(0);
                     assertThat(staged).isNotEqualTo(shared);
                     if (posix) assertThat(Files.getPosixFilePermissions(staged.getParent()))
                             .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
                     return new java.io.FilterOutputStream((java.io.OutputStream) call.callRealMethod()) {
                         @Override public void close() throws IOException {
                             super.close();
                             assertThat(Files.readString(shared)).isEqualTo("FAKE previously committed image");
                             closed.incrementAndGet();
                         }
                     };
                 }
                 return call.callRealMethod();
             })) {
            folder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(uploadReplacing(shared)).isEqualTo("FAKE uploaded consumer image");
        }
        assertThat(closed.get()).isEqualTo(1);
        if (posix) assertThat(Files.getPosixFilePermissions(shared)).isEqualTo(permissions);
    }

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
                     var _ = mockStatic(Files.class, call -> {
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
        return uploadReplacing(shared, "success");
    }

    private String uploadReplacing(Path shared, String expectedOutcome) throws Exception {
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
            assertThat(action.execute()).isEqualTo(expectedOutcome);
            assertThat(request.getAttribute("status")).isEqualTo(expectedOutcome.equals("success") ? "uploaded" : null);
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
