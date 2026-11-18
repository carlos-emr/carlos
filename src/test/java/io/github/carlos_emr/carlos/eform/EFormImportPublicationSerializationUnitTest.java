/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

@Tag("unit")
class EFormImportPublicationSerializationUnitTest {
    @TempDir Path images;

    private byte[] archive(String name, String diagram) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("eform.properties"));
            zip.write(("form.name="+name+"\nform.htmlFilename=form.html\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("form.html"));
            zip.write(("<img src='" + diagram + "'>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(diagram));
            zip.write(diagram.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private List<String> importForm(byte[] bytes, CountDownLatch saving, CountDownLatch release,
            AtomicReference<Thread> thread, Set<String> registered) throws Exception {
        thread.set(Thread.currentThread());
        try (var folders=mockStatic(ImageUpload2Action.class);var forms=mockStatic(EFormUtil.class)) {
            folders.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            forms.when(()->EFormUtil.formExistsInDB(any())).thenAnswer(call->registered.contains(call.getArgument(0)));
            forms.when(()->EFormUtil.saveEForm(any(EForm.class))).thenAnswer(call->{
                saving.countDown();
                assertThat(release.await(15,TimeUnit.SECONDS)).isTrue();
                registered.add(call.<EForm>getArgument(0).getFormName());
                return "fixture";
            });
            return new EFormExportZip(Runnable::run).importForm(new ByteArrayInputStream(bytes));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void shouldWaitForRegistration_beforeLoadingAnotherImportsHtml(boolean sameName) throws Exception {
        byte[] firstArchive = archive("first", "first.png");
        byte[] secondArchive = archive(sameName ? "first" : "second", "second.png");
        var firstSaving=new CountDownLatch(1);var secondSaving=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var firstThread=new AtomicReference<Thread>();var secondThread=new AtomicReference<Thread>();
        Set<String> registered=ConcurrentHashMap.newKeySet();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->importForm(firstArchive,firstSaving,release,firstThread,registered));
            try {
                assertThat(firstSaving.await(15,TimeUnit.SECONDS)).isTrue();
                var second=pool.submit(()->importForm(secondArchive,secondSaving,release,secondThread,registered));
                boolean observedOwner=false;
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(System.nanoTime()<deadline && secondSaving.getCount()!=0) {
                    Thread contender=secondThread.get();
                    var info=contender==null?null:ManagementFactory.getThreadMXBean().getThreadInfo(contender.threadId());
                    if(info!=null && info.getThreadState()==Thread.State.WAITING
                            && info.getLockOwnerId()==firstThread.get().threadId()) {
                        observedOwner=true;
                        break;
                    }
                    TimeUnit.MILLISECONDS.sleep(10);
                }
                assertThat(observedOwner).as("second import waits for the first registration, across importer instances").isTrue();
                assertThat(secondSaving.getCount()).isEqualTo(1);
                release.countDown();
                assertThat(first.get(15,TimeUnit.SECONDS)).isEmpty();
                List<String> secondErrors=second.get(15,TimeUnit.SECONDS);
                if(sameName) {
                    assertThat(secondErrors).containsExactly("Skipped form 'first', form already exists");
                    assertThat(secondSaving.getCount()).isEqualTo(1);
                    assertThat(registered).containsExactly("first");
                    assertThat(images.resolve("second.png")).doesNotExist();
                } else {
                    assertThat(secondErrors).isEmpty();
                    assertThat(registered).containsExactlyInAnyOrder("first","second");
                    assertThat(images.resolve("second.png")).isRegularFile();
                }
                assertThat(images.resolve("first.png")).isRegularFile();
            } finally {release.countDown();}
        }
        // A cooperating publication implementation may retain its empty lock inode.
        // Every extracted file/directory must still be gone; never permit a symlink.
        try (var staged=java.nio.file.Files.list(images.resolve("extractFolder"))) {
            for (Path retained : staged.toList()) {
                assertThat(retained.getFileName().toString()).isEqualTo(".publication.lock");
                assertThat(java.nio.file.Files.isSymbolicLink(retained)).isFalse();
                assertThat(retained).isRegularFile();
                assertThat(java.nio.file.Files.size(retained)).isZero();
            }
        }
    }
}
