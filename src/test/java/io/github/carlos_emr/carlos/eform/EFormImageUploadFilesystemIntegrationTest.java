/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Run as an unprivileged user to exercise real filesystem access refusal. */
@Tag("integration")
@org.junit.jupiter.api.parallel.Isolated("Temporarily selects the portable metadata path")
class EFormImageUploadFilesystemIntegrationTest extends CarlosUnitTestBase {
    @TempDir Path scratch;

    @ParameterizedTest @ValueSource(strings={"missing","writable","read-only","write-only"})
    void shouldHonorEffectiveDestinationAccess_beforeAtomicReplacement(String mode) throws Exception {
        Path images=Files.createDirectory(scratch.resolve("images"));
        Path destination=images.resolve("owned.png");
        boolean exists=!mode.equals("missing");
        if(exists)Files.writeString(destination,"FAKE previous image");
        boolean posix=Files.getFileStore(images).supportsFileAttributeView("posix");
        if(posix && mode.equals("read-only"))Files.setPosixFilePermissions(destination,
                PosixFilePermissions.fromString("r--r--r--"));
        if(posix && mode.equals("write-only"))Files.setPosixFilePermissions(destination,
                PosixFilePermissions.fromString("-w-------"));
        var originalPermissions=posix && exists ? Files.getPosixFilePermissions(destination) : null;
        boolean unreadable="Linux".equals(System.getProperty("os.name")) && exists && !Files.isReadable(destination);
        boolean allowed=!exists || Files.isWritable(destination) && !unreadable;
        Path upload=Files.writeString(scratch.resolve("upload.png"),"FAKE replacement image");
        var request=new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),new LoggedInInfo());
        var security=mock(SecurityInfoManager.class);registerMock(SecurityInfoManager.class,security);
        when(security.hasPrivilege(any(),eq("_eform"),eq("w"),isNull())).thenReturn(true);
        try(var servlet=mockStatic(ServletActionContext.class);var folders=mockStatic(ImageUpload2Action.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            folders.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            var action=new ImageUpload2Action();action.setImage(upload.toFile());action.setImageFileName("owned.png");
            assertEquals(allowed?"success":"error",action.execute());
            assertEquals(allowed?"uploaded":null,request.getAttribute("status"));
            assertEquals(!allowed,!action.getActionErrors().isEmpty());
            if(unreadable)assertTrue(action.getActionErrors().stream().anyMatch(message -> message.contains("read and write access")));
        }
        if(originalPermissions!=null)assertEquals(originalPermissions,Files.getPosixFilePermissions(destination));
        if(posix && !Files.isReadable(destination))Files.setPosixFilePermissions(destination,
                PosixFilePermissions.fromString("rw-------"));
        assertEquals(allowed?"FAKE replacement image":"FAKE previous image",Files.readString(destination));
        try(var entries=Files.list(images)) {
            assertEquals(java.util.Set.of(destination,images.resolve("extractFolder")),new java.util.HashSet<>(entries.toList()));
        }
    }

    @Test
    void shouldKeepPortableReplacement_whenTheLinuxCopierDoesNotApply() throws Exception {
        String existingOs = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Portable test");
            shouldHonorEffectiveDestinationAccess_beforeAtomicReplacement("writable");
        } finally {
            System.setProperty("os.name", existingOs);
        }
    }
}
