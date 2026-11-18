/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Provider/OS failure controls supplement the real unprivileged filesystem tests. */
@Tag("unit")
class EFormImageReplacementMetadataUnitTest {
    private final Path existing=Path.of("FAKE-existing.png"), staged=Path.of("FAKE-staged.png");
    private final FileStore store=mock(FileStore.class);
    private final UserPrincipal owner=()->"FAKE-owner", otherOwner=()->"FAKE-other-owner";
    private final GroupPrincipal group=()->"FAKE-group", otherGroup=()->"FAKE-other-group";
    private final PosixFileAttributes original=mock(PosixFileAttributes.class), replacement=mock(PosixFileAttributes.class);
    private final PosixFileAttributeView targetPosix=mock(PosixFileAttributeView.class);
    private final Set<PosixFilePermission> permissions=PosixFilePermissions.fromString("rw-r-----");

    private void preserve(boolean posix,boolean linux) throws Exception {
        var method=ImageUpload2Action.class.getDeclaredMethod("preserveReplacementMetadata",
                Path.class,Path.class,FileStore.class,boolean.class,boolean.class);
        method.setAccessible(true);
        try {method.invoke(null,existing,staged,store,posix,linux);}
        catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof Exception cause)throw cause;
            throw failure;
        }
    }
    private MockedStatic<Files> metadata() throws Exception {
        var files=mockStatic(Files.class);
        files.when(()->Files.isRegularFile(existing,LinkOption.NOFOLLOW_LINKS)).thenReturn(true);
        files.when(()->Files.isRegularFile(staged,LinkOption.NOFOLLOW_LINKS)).thenReturn(true);
        files.when(()->Files.readAttributes(existing,PosixFileAttributes.class,LinkOption.NOFOLLOW_LINKS)).thenReturn(original);
        files.when(()->Files.readAttributes(staged,PosixFileAttributes.class,LinkOption.NOFOLLOW_LINKS)).thenReturn(replacement);
        files.when(()->Files.getFileAttributeView(staged,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)).thenReturn(targetPosix);
        files.when(()->Files.getOwner(existing,LinkOption.NOFOLLOW_LINKS)).thenReturn(owner);
        files.when(()->Files.getOwner(staged,LinkOption.NOFOLLOW_LINKS)).thenReturn(owner);
        when(original.owner()).thenReturn(owner);when(replacement.owner()).thenReturn(owner);
        when(original.group()).thenReturn(group);when(replacement.group()).thenReturn(group);
        when(original.permissions()).thenReturn(permissions);when(replacement.permissions()).thenReturn(permissions);
        when(targetPosix.readAttributes()).thenReturn(replacement);
        return files;
    }
    private Process successfulCopy() throws Exception {
        Process process=mock(Process.class);when(process.waitFor(30,TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(0);return process;
    }
    @Test void shouldSkipMetadata_forMissingDestination() throws Exception {
        try(var files=metadata()) {
            files.when(()->Files.notExists(existing,LinkOption.NOFOLLOW_LINKS)).thenReturn(true);
            preserve(true,false);verifyNoInteractions(store);
        }
    }
    @Test void shouldRejectNonRegularDestination_beforeCopyingAnyMetadata() throws Exception {
        try(var files=metadata()) {
            files.when(()->Files.isRegularFile(existing,LinkOption.NOFOLLOW_LINKS)).thenReturn(false);
            assertThrows(IOException.class,()->preserve(true,false));verifyNoInteractions(store);
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldRetainLinuxMetadata_withAndWithoutPosixViews(boolean posix) throws Exception {
        var process=successfulCopy();
        try(var files=metadata();var builders=mockConstruction(ProcessBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF),(builder,context)->when(builder.start()).thenReturn(process))) {
            preserve(posix,true);assertEquals(1,builders.constructed().size());
            verify(process).waitFor(30,TimeUnit.SECONDS);verifyNoInteractions(store);
        }
    }
    @ParameterizedTest @ValueSource(strings={"owner","group","permissions"})
    void shouldRejectLinuxMetadata_thatCannotBePreservedExactly(String changed) throws Exception {
        var process=successfulCopy();
        try(var files=metadata();var builders=mockConstruction(ProcessBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF),(builder,context)->when(builder.start()).thenReturn(process))) {
            switch(changed) {
                case "owner"->when(replacement.owner()).thenReturn(otherOwner);
                case "group"->when(replacement.group()).thenReturn(otherGroup);
                case "permissions"->when(replacement.permissions()).thenReturn(Set.of(PosixFilePermission.OWNER_READ));
                default->throw new AssertionError(changed);
            }
            assertThrows(IOException.class,()->preserve(true,true));
        }
    }
    @Test void shouldRejectNonRegularStaging_afterLinuxMetadataCopy() throws Exception {
        var process=successfulCopy();
        try(var files=metadata();var builders=mockConstruction(ProcessBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF),(builder,context)->when(builder.start()).thenReturn(process))) {
            files.when(()->Files.isRegularFile(staged,LinkOption.NOFOLLOW_LINKS)).thenReturn(false);
            assertThrows(IOException.class,()->preserve(true,true));
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldPreservePortableOwnershipPermissionsAndAcl_whenViewsAreSupported(boolean changeOwnership) throws Exception {
        var sourceAcl=mock(AclFileAttributeView.class);var targetAcl=mock(AclFileAttributeView.class);
        var acl=List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(AclEntryPermission.READ_DATA).build());when(sourceAcl.getAcl()).thenReturn(acl);
        try(var files=metadata()) {
            when(store.supportsFileAttributeView(AclFileAttributeView.class)).thenReturn(true);
            when(store.supportsFileAttributeView(FileOwnerAttributeView.class)).thenReturn(true);
            files.when(()->Files.getFileAttributeView(existing,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)).thenReturn(sourceAcl);
            files.when(()->Files.getFileAttributeView(staged,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)).thenReturn(targetAcl);
            if(changeOwnership) {
                when(replacement.group()).thenReturn(otherGroup);
                files.when(()->Files.getOwner(staged,LinkOption.NOFOLLOW_LINKS)).thenReturn(otherOwner);
            }
            preserve(true,false);verify(targetPosix).setPermissions(permissions);verify(targetAcl).setAcl(acl);
            verify(targetPosix,times(changeOwnership?1:0)).setGroup(group);
            files.verify(()->Files.setOwner(staged,owner),times(changeOwnership?1:0));
        }
    }
    @Test void shouldSupportProviders_withoutOptionalMetadataViews() throws Exception {
        try(var files=metadata()){preserve(false,false);verifyNoInteractions(targetPosix);}
    }
    @ParameterizedTest @ValueSource(strings={"empty","complete","short-read","short-write"})
    void shouldCopyEachUserAttribute_orRefuseIncompleteTransfers(String mode) throws Exception {
        var source=mock(UserDefinedFileAttributeView.class);var target=mock(UserDefinedFileAttributeView.class);
        byte[] expected={1,2,3};when(source.list()).thenReturn(mode.equals("empty")?List.of():List.of("FAKE-attribute"));
        when(source.size("FAKE-attribute")).thenReturn(expected.length);
        when(source.read(eq("FAKE-attribute"),any(ByteBuffer.class))).thenAnswer(call->{
            call.<ByteBuffer>getArgument(1).put(expected);return mode.equals("short-read")?1:expected.length;
        });
        when(target.write(eq("FAKE-attribute"),any(ByteBuffer.class))).thenAnswer(call->{
            ByteBuffer buffer=call.getArgument(1);byte[] actual=new byte[buffer.remaining()];buffer.get(actual);
            assertArrayEquals(expected,actual);return mode.equals("short-write")?1:expected.length;
        });
        try(var files=metadata()) {
            when(store.supportsFileAttributeView(UserDefinedFileAttributeView.class)).thenReturn(true);
            files.when(()->Files.getFileAttributeView(existing,UserDefinedFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)).thenReturn(source);
            files.when(()->Files.getFileAttributeView(staged,UserDefinedFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)).thenReturn(target);
            if(mode.startsWith("short-"))assertThrows(IOException.class,()->preserve(false,false));
            else preserve(false,false);
            verify(target,times(mode.equals("empty")||mode.equals("short-read")?0:1))
                    .write(eq("FAKE-attribute"),any(ByteBuffer.class));
        }
    }
    @ParameterizedTest @ValueSource(strings={"success","failure","timeout","interrupted","unavailable"})
    void shouldRefuseFailedLinuxMetadataCopy_andTerminatePendingCopy(String mode) throws Exception {
        var process=mock(Process.class);var startFailure=new IOException("FAKE metadata tool unavailable");
        boolean alive=mode.equals("timeout")||mode.equals("interrupted");when(process.isAlive()).thenReturn(alive);
        if(mode.equals("interrupted"))when(process.waitFor(30,TimeUnit.SECONDS)).thenThrow(new InterruptedException("FAKE interrupt"));
        else when(process.waitFor(30,TimeUnit.SECONDS)).thenReturn(!mode.equals("timeout"));
        when(process.exitValue()).thenReturn(mode.equals("failure")?1:0);
        try(var builders=mockConstruction(ProcessBuilder.class,withSettings().defaultAnswer(RETURNS_SELF),(builder,context)->{
            if(mode.equals("unavailable"))when(builder.start()).thenThrow(startFailure);
            else when(builder.start()).thenReturn(process);
        })) {
            var method=ImageUpload2Action.class.getDeclaredMethod("copyLinuxMetadata",Path.class,Path.class);method.setAccessible(true);
            if(mode.equals("success"))assertDoesNotThrow(()->method.invoke(null,existing,staged));
            else {
                var failure=assertThrows(InvocationTargetException.class,()->method.invoke(null,existing,staged));
                assertInstanceOf(IOException.class,failure.getCause());
                if(mode.equals("unavailable"))assertSame(startFailure,failure.getCause());
            }
            if(mode.equals("interrupted"))assertTrue(Thread.currentThread().isInterrupted());
            verify(process,times(alive?1:0)).destroyForcibly();
        } finally {if(mode.equals("interrupted"))Thread.interrupted();}
    }
}
