/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Atomic Linux {@code renameat2(RENAME_NOREPLACE)} fallback for {@code form_record_path} mounts
 * that permit no-replace rename without POSIX hard links (such as CIFS shares).
 *
 * @since 2026-11-27
 */
final class FormNoReplaceMove {
    private static final int AT_FDCWD = -100;
    private static final int RENAME_NOREPLACE = 1;

    private record KernelRename(MethodHandle handle, MemoryLayout stateLayout, long errnoOffset) { }

    private static final class Holder {
        private static final KernelRename INSTANCE = resolveKernelRename();

        private static KernelRename resolveKernelRename() {
            if (!"Linux".equals(System.getProperty("os.name"))) {
                return null;
            }
            try {
                Linker linker = Linker.nativeLinker();
                var symbol = linker.defaultLookup().find("renameat2").orElse(null);
                if (symbol == null) {
                    return null;
                }
                MemoryLayout layout = Linker.Option.captureStateLayout();
                long offset = layout.byteOffset(MemoryLayout.PathElement.groupElement("errno"));
                MethodHandle downcall = linker.downcallHandle(
                        symbol,
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT),
                        Linker.Option.captureCallState("errno"));
                return new KernelRename(downcall, layout, offset);
            } catch (IllegalCallerException | UnsupportedOperationException _) {
                return null;
            }
        }
    }

    private FormNoReplaceMove() {
    }

    static void move(Path source, Path target) throws IOException {
        KernelRename kernel = Holder.INSTANCE;
        if (kernel == null) {
            throw unsupported(source, target);
        }
        int errno = invokeRenameNoReplace(kernel, source, target);
        if (errno == 0) {
            return;
        }
        throw mapErrno(errno, source, target);
    }

    private static int invokeRenameNoReplace(KernelRename kernel, Path source, Path target) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            var state = arena.allocate(kernel.stateLayout());
            var srcPath = arena.allocateFrom(source.toAbsolutePath().toString());
            var dstPath = arena.allocateFrom(target.toAbsolutePath().toString());
            int rc = (int) kernel.handle().invokeExact(state, AT_FDCWD, srcPath, AT_FDCWD, dstPath, RENAME_NOREPLACE);
            return rc == 0 ? 0 : state.get(JAVA_INT, kernel.errnoOffset());
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable invocationFailure) {
            throw new IOException("Atomic form XML archive rename failed", invocationFailure);
        }
    }

    private static IOException mapErrno(int errno, Path source, Path target) {
        return switch (errno) {
            case 17 -> new FileAlreadyExistsException(target.toString());
            case 2 -> new NoSuchFileException(source.toString());
            case 18, 22, 38, 95 -> unsupported(source, target);
            default -> new IOException("Atomic form XML archive publication failed (errno " + errno + ")");
        };
    }

    static AtomicMoveNotSupportedException unsupported(Path source, Path target) {
        return new AtomicMoveNotSupportedException(
                source.toString(), target.toString(), FormXmlArchive.STORAGE_REQUIREMENT_MESSAGE);
    }
}
