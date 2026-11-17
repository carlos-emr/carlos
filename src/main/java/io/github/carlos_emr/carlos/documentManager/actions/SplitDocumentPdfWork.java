/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.transaction.support.TransactionSynchronization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Private, disk-backed PDF preparation followed by an explicitly tracked publication. */
final class SplitDocumentPdfWork {
    enum Operation { SPLIT, ROTATE_90, ROTATE_180, REMOVE_FIRST }
    record PageSelection(int page, int rotation) { }

    private SplitDocumentPdfWork() { }

    static List<PageSelection> selections(String[] commands) {
        if (commands == null || commands.length == 0) throw new IllegalArgumentException("Select at least one page");
        List<PageSelection> selections = new ArrayList<>();
        for (String command : commands) {
            if (command == null || !command.matches("[1-9][0-9]{0,9},-?[0-9]{1,10}")) {
                throw new IllegalArgumentException("Invalid page selection");
            }
            String[] parts = command.split(",", -1);
            int page = Integer.parseInt(parts[0]);
            int rotation = Integer.parseInt(parts[1]);
            if (page < 1 || rotation % 90 != 0) throw new IllegalArgumentException("Invalid page selection");
            selections.add(new PageSelection(page, Math.floorMod(rotation, 360)));
        }
        return List.copyOf(selections);
    }

    static Prepared prepare(Path source, Path directory, Operation operation, List<PageSelection> selections)
            throws IOException {
        // The worker can outlive a timeout. Its ONLY output is private staging; a closed
        // handoff disposes of late results and can never publish or change database rows.
        try (Handoff handoff = new Handoff()) {
            BoundedPdfTask.runWithin(60, "stored-document-pages", () -> {
                handoff.offer(prepareNow(source, directory, operation, selections));
                return null;
            });
            return handoff.take();
        }
    }

    static Prepared prepareNow(Path source, Path directory, Operation operation, List<PageSelection> selections)
            throws IOException {
        Path privateDirectory = Files.createTempDirectory(directory, ".document-pages-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Prepared result = new Prepared(privateDirectory);
        boolean complete = false;
        try {
            Files.createFile(result.pdf, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try (PDDocument input = Loader.loadPDF(source.toFile(), IOUtils.createTempFileOnlyStreamCache());
                 PDDocument output = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
                result.originalPageCount = input.getNumberOfPages();
                if (result.originalPageCount < 1) throw new IllegalArgumentException("The document has no pages");
                if (operation == Operation.REMOVE_FIRST && result.originalPageCount < 2) {
                    throw new IllegalArgumentException("The only page cannot be removed");
                }
                if (operation == Operation.SPLIT) {
                    if (selections.isEmpty()) throw new IllegalArgumentException("Select at least one page");
                    for (PageSelection selection : selections) {
                        if (selection.page() > result.originalPageCount) throw new IllegalArgumentException("Page is no longer available");
                        PDPage original = input.getPage(selection.page() - 1);
                        PDPage copy = output.importPage(original);
                        // importPage deliberately omits inherited resources. Preserve those,
                        // and use independent page dictionaries for repeated selections.
                        copy.setResources(original.getResources());
                        // The sorter rotates an already rendered source page; its
                        // command is a visual delta, not an absolute PDF rotation.
                        copy.setRotation(Math.floorMod(original.getRotation() + selection.rotation(), 360));
                    }
                    result.pageCount = output.getNumberOfPages();
                    output.save(result.pdf.toFile());
                } else {
                    // Retain the original catalog, metadata, forms and other document
                    // structures when changing pages of the existing document.
                    if (operation == Operation.REMOVE_FIRST) input.removePage(0);
                    else for (PDPage page : input.getPages()) {
                        int delta = operation == Operation.ROTATE_90 ? 90 : 180;
                        page.setRotation(Math.floorMod(page.getRotation() + delta, 360));
                    }
                    result.pageCount = input.getNumberOfPages();
                    input.save(result.pdf.toFile());
                }
            }
            complete = true;
            return result;
        } finally {
            if (!complete) result.close();
        }
    }

    static final class Handoff implements AutoCloseable {
        private Prepared result;
        private boolean closed;

        synchronized void offer(Prepared prepared) throws IOException {
            if (closed) {
                try { prepared.close(); }
                catch (IOException cleanup) {
                    MiscUtils.getLogger().error("Unable to clean abandoned private document preparation", cleanup);
                    throw cleanup;
                }
            } else result = prepared;
        }

        synchronized Prepared take() throws IOException {
            if (closed || result == null) throw new IOException("Document preparation was cancelled");
            Prepared taken = result;
            result = null;
            return taken;
        }

        @Override public synchronized void close() throws IOException {
            closed = true;
            if (result != null) {
                Prepared abandoned = result;
                result = null;
                abandoned.close();
            }
        }
    }

    static final class Prepared implements AutoCloseable {
        final Path directory;
        final Path pdf;
        int originalPageCount;
        int pageCount;
        private boolean retain;

        Prepared(Path directory) { this.directory = directory; this.pdf = directory.resolve("prepared.pdf"); }

        @Override public void close() throws IOException {
            if (retain) return;
            IOException failure = null;
            for (String name : List.of("prepared.pdf", "original.pdf", "published.pdf", "recovery.txt")) {
                try { Files.deleteIfExists(directory.resolve(name)); }
                catch (IOException problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
            }
            try { Files.deleteIfExists(directory); }
            catch (IOException problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
            if (failure != null) throw failure;
        }
    }

    /**
     * Database and filesystem commits are separate. Only a confirmed pre-commit rollback
     * restores/removes our own inode. An uncertain commit retains complete recovery bytes.
     * The caller holds the source mutation lease throughout this object's lifetime.
     */
    static final class Publication implements TransactionSynchronization {
        final Prepared prepared;
        final Path source;
        final Path target;
        final boolean replacement;
        boolean mutationStarted;
        boolean committed;
        boolean uncertain;
        private boolean published;
        private boolean commitStarted;

        Publication(Prepared prepared, Path source, boolean replacement) {
            this.prepared = prepared;
            this.source = source;
            this.replacement = replacement;
            this.target = replacement ? source : prepared.directory.getParent().resolve("split-" + UUID.randomUUID() + ".pdf");
        }

        void publish() throws IOException {
            if (replacement) {
                var attributes = Files.readAttributes(target, java.nio.file.attribute.PosixFileAttributes.class);
                var preparedAttributes = Files.getFileAttributeView(prepared.pdf, java.nio.file.attribute.PosixFileAttributeView.class);
                // Preserve existing access restrictions and ownership. If the service
                // cannot preserve them, refuse before replacing the original inode.
                if (!Files.getOwner(prepared.pdf).equals(attributes.owner())) preparedAttributes.setOwner(attributes.owner());
                if (!preparedAttributes.readAttributes().group().equals(attributes.group())) preparedAttributes.setGroup(attributes.group());
                preparedAttributes.setPermissions(attributes.permissions());
                Files.createLink(prepared.directory.resolve("original.pdf"), target);
                Files.createLink(prepared.directory.resolve("published.pdf"), prepared.pdf);
                // No unsafe non-atomic replacement fallback: an unsupported filesystem
                // refuses this operation while the original is still intact.
                Files.move(prepared.pdf, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // A hard link publishes only the fully closed file, with no clobber window.
                Files.createLink(target, prepared.pdf);
            }
            published = true;
            mutationStarted = true;
        }

        @Override public void beforeCommit(boolean readOnly) { commitStarted = true; }

        @Override public void afterCompletion(int status) {
            if (status == STATUS_COMMITTED) { committed = true; return; }
            if (!published) return;
            if (status == STATUS_ROLLED_BACK && !commitStarted) {
                try {
                    Path ours = replacement ? prepared.directory.resolve("published.pdf") : prepared.pdf;
                    if (!Files.isSameFile(target, ours)) throw new IOException("Published document identity changed during rollback");
                    if (replacement) Files.move(prepared.directory.resolve("original.pdf"), target,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    else Files.delete(target);
                    mutationStarted = false;
                    return;
                } catch (IOException | RuntimeException cleanup) {
                    MiscUtils.getLogger().error("Document publication rollback could not be confirmed", cleanup);
                }
            }
            retainRecovery();
        }

        void retainRecovery() {
            uncertain = true;
            prepared.retain = true;
            try {
                Files.writeString(prepared.directory.resolve("recovery.txt"),
                        "Unconfirmed database/filesystem outcome. Do not replay. Target: " + target.getFileName()
                                + "\nReplacement: " + replacement + "\n",
                        java.nio.file.StandardOpenOption.CREATE_NEW);
            } catch (IOException problem) {
                MiscUtils.getLogger().error("Unable to record document recovery metadata", problem);
            }
            MiscUtils.getLogger().error("Document publication requires reconciliation; private recovery directory: " + prepared.directory);
        }
    }
}
