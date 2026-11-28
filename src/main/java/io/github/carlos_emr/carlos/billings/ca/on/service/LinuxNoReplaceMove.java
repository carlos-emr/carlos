/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** Linux renameat2(RENAME_NOREPLACE) fallback for HOME_DIR mounts without hard links, including CIFS. */
final class LinuxNoReplaceMove {
    private LinuxNoReplaceMove() { }

    static void move(Path source, Path target) throws IOException {
        if (!"Linux".equals(System.getProperty("os.name"))) throw unsupported(source, target);
        try (var arena = Arena.ofConfined()) {
            var linker = Linker.nativeLinker();
            var symbol = linker.defaultLookup().find("renameat2").orElseThrow(() -> unsupported(source, target));
            var layout = Linker.Option.captureStateLayout();
            var captured = arena.allocate(layout);
            var rename = linker.downcallHandle(symbol,
                    FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT),
                    Linker.Option.captureCallState("errno"));
            // AT_FDCWD (-100), absolute paths, RENAME_NOREPLACE (1). No ordinary rename fallback.
            int result = (int) rename.invokeExact(captured, -100, arena.allocateFrom(source.toAbsolutePath().toString()),
                    -100, arena.allocateFrom(target.toAbsolutePath().toString()), 1);
            if (result == 0) return;
            int errno = captured.get(JAVA_INT, layout.byteOffset(MemoryLayout.PathElement.groupElement("errno")));
            switch (errno) {
                case 17 -> throw new FileAlreadyExistsException(target.toString());
                case 2 -> throw new NoSuchFileException(source.toString());
                case 22, 38, 95 -> throw unsupported(source, target); // EINVAL, ENOSYS, EOPNOTSUPP
                default -> throw new IOException("Atomic OHIP publication failed (errno " + errno + ")");
            }
        } catch (IllegalCallerException | UnsupportedOperationException unavailable) {
            var failure = unsupported(source, target);
            failure.initCause(unavailable);
            throw failure;
        } catch (IOException | RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable invocationFailure) {
            // MethodHandle.invokeExact declares Throwable even with this fixed C signature.
            throw new IOException("Atomic OHIP publication was unavailable", invocationFailure);
        }
    }

    private static AtomicMoveNotSupportedException unsupported(Path source, Path target) {
        return new AtomicMoveNotSupportedException(source.toString(), target.toString(), "Atomic no-replace rename unavailable");
    }
}
