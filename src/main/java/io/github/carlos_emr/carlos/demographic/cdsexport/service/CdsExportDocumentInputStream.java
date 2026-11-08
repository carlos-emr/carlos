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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import com.sun.security.auth.module.UnixSystem;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/**
 * Streams one native document through directory-relative, no-follow handles. File identity,
 * length and modification time must remain stable through complete EOF and close. No native
 * file or permission is changed; failures contain a fixed code rather than a protected path.
 */
final class CdsExportDocumentInputStream extends InputStream {
    private final List<DirectoryStream<Path>> directories = new ArrayList<>();
    private final List<PosixFileAttributeView> views = new ArrayList<>();
    private final List<PosixFileAttributes> identities = new ArrayList<>();
    private final Runnable checkpoint;
    private final Thread owner = Thread.currentThread();
    private SeekableByteChannel channel;
    private PosixFileAttributeView fileView;
    private PosixFileAttributes fileIdentity;
    private long delivered;
    private boolean closed, failed, verified;
    CdsExportDocumentInputStream(Path root, String filename, long maximumBytes, Runnable checkpoint) throws IOException {
        this.checkpoint = Objects.requireNonNull(checkpoint); checkpoint.run();
        try {
            if (root == null || !root.isAbsolute() || !root.equals(root.normalize()) || maximumBytes < 1 || filename == null
                    || filename.isEmpty() || filename.length() > 1024 || filename.contains("\\") || filename.contains(":")
                    || filename.codePoints().anyMatch(Character::isISOControl)) invalid();
            Path relative = Path.of(filename);
            if (relative.isAbsolute() || !relative.equals(relative.normalize()) || !relative.toString().equals(filename)
                    || relative.getNameCount() > 16 || relative.startsWith("..")) invalid();
            for (Path part : relative) if (part.toString().equals(".") || part.toString().equals("..") || part.toString().length() > 255) invalid();
            for (Path cursor = root; cursor != null; cursor = cursor.getParent()) {
                var view = Files.getFileAttributeView(cursor, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                var attributes = view.readAttributes(); trusted(attributes, true); views.add(view); identities.add(attributes);
            }
            var stream = Files.newDirectoryStream(root); directories.add(stream);
            if (!(stream instanceof SecureDirectoryStream<Path> secured)) invalid();
            @SuppressWarnings("unchecked") var current = (SecureDirectoryStream<Path>) stream;
            for (int i = 0; i < relative.getNameCount() - 1; i++) {
                Path part = relative.getName(i); var view = current.getFileAttributeView(part, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                var attributes = view.readAttributes(); trusted(attributes, true); views.add(view); identities.add(attributes);
                var next = current.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS); directories.add(next); current = next;
            }
            Path name = relative.getFileName(); fileView = current.getFileAttributeView(name, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            fileIdentity = fileView.readAttributes(); trusted(fileIdentity, false);
            if (fileIdentity.size() < 0 || fileIdentity.size() > maximumBytes || fileIdentity.fileKey() == null) invalid();
            channel = current.newByteChannel(name, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            if (channel.size() != fileIdentity.size()) invalid(); stable();
        } catch (IOException | RuntimeException failure) {
            failed = true; try { closeHandles(); } catch (IOException ignored) { }
            throw new IOException("CDS_EXPORT_DOCUMENT_SOURCE_UNAVAILABLE");
        } catch (Error failure) {
            failed = true; try { closeHandles(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); } throw failure;
        }
    }
    private static void trusted(PosixFileAttributes attributes, boolean directory) throws IOException {
        String account = new UnixSystem().getUsername();
        if (attributes.isSymbolicLink() || (directory ? !attributes.isDirectory() : !attributes.isRegularFile())
                || !("root".equals(attributes.owner().getName()) || account.equals(attributes.owner().getName()))
                || attributes.permissions().contains(PosixFilePermission.GROUP_WRITE) || attributes.permissions().contains(PosixFilePermission.OTHERS_WRITE)) invalid();
    }
    private void stable() throws IOException {
        for (int i = 0; i < views.size(); i++) {
            var actual = views.get(i).readAttributes(); trusted(actual, true);
            if (!Objects.equals(actual.fileKey(), identities.get(i).fileKey())) invalid();
        }
        var actual = fileView.readAttributes(); trusted(actual, false);
        if (!Objects.equals(actual.fileKey(), fileIdentity.fileKey()) || actual.size() != fileIdentity.size()
                || !actual.lastModifiedTime().equals(fileIdentity.lastModifiedTime()) || channel.size() != fileIdentity.size()) invalid();
    }
    @Override public int read() throws IOException { byte[] one = new byte[1]; int count = read(one, 0, 1); return count == -1 ? -1 : one[0] & 255; }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        try {
            Objects.checkFromIndexSize(offset, length, bytes.length); requireActive(); checkpoint.run(); if (length == 0) return 0;
            if (delivered == fileIdentity.size()) {
                if (!verified) { if (channel.read(ByteBuffer.allocate(1)) != -1) invalid(); stable(); checkpoint.run(); verified = true; }
                return -1;
            }
            int count = channel.read(ByteBuffer.wrap(bytes, offset, (int) Math.min(Math.min(length, 65536), fileIdentity.size() - delivered)));
            if (count < 1) invalid(); delivered += count; return count;
        } catch (IOException failure) { failed = true; throw new IOException("CDS_EXPORT_DOCUMENT_SOURCE_CHANGED_OR_UNSAFE"); }
        catch (RuntimeException | Error failure) { failed = true; throw failure; }
    }
    private void requireActive() throws IOException { if (closed || failed || Thread.currentThread() != owner) invalid(); }
    @Override public void close() throws IOException {
        if (closed) return; Throwable primary = null;
        try {
            if (!failed) {
                requireActive(); if (!verified) { byte[] buffer = new byte[65536]; try { while (read(buffer) != -1) { } } finally { Arrays.fill(buffer, (byte) 0); } }
                stable(); checkpoint.run();
            }
        } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { closed = true; try { closeHandles(); } catch (IOException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; } }
    }
    private void closeHandles() throws IOException {
        IOException failure = null;
        if (channel != null) try { channel.close(); } catch (IOException problem) { failure = problem; }
        for (var directory : directories.reversed()) try { directory.close(); } catch (IOException problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
        if (failure != null) throw failure;
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_DOCUMENT_SOURCE_CHANGED_OR_UNSAFE"); }
}
