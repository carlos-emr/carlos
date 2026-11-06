/**
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.RBTGroupDao;
import io.github.carlos_emr.carlos.commn.model.RBTGroup;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RBTGroupManager}.
 *
 * <p>Pins #5639: a Report by Template group name is persisted exactly as typed (trimmed), never
 * run through {@code Encode.forJava}, which turned {@code O'Brien} into {@code O\'Brien}.</p>
 *
 * @since 2026-11-05
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RBT Group Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
class RBTGroupManagerUnitTest extends CarlosUnitTestBase {

    @Mock
    private RBTGroupDao mockRbtGroupDao;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private RBTGroupManager manager;

    @BeforeEach
    void setUp() {
        manager = new RBTGroupManager();
        injectDependency(manager, "rbtGroupDao", mockRbtGroupDao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
    }

    /** Grants the {@code _admin} write privilege that {@code addTemplateToGroup} requires. */
    private void allowAdminWrite() {
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_admin", SecurityInfoManager.WRITE, null))
                .thenReturn(true);
    }

    /** Captures the single group the manager persisted, failing if it persisted none or several. */
    private RBTGroup persistedGroup() {
        ArgumentCaptor<RBTGroup> captor = ArgumentCaptor.forClass(RBTGroup.class);
        verify(mockRbtGroupDao).persist(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("should persist special characters byte for byte")
    void shouldPersistNameAsTyped_withSpecialCharacters() {
        allowAdminWrite();
        String typed = "O'Brien-Smith Zoë 山田太郎 &amp; a\\b \"q\" <b>";

        manager.addTemplateToGroup(mockLoggedInInfo, typed, 0);

        assertThat(persistedGroup().getGroupName()).isEqualTo(typed);
    }

    @Test
    @DisplayName("should trim surrounding whitespace only")
    void shouldTrimName_beforePersisting() {
        allowAdminWrite();

        manager.addTemplateToGroup(mockLoggedInInfo, "  Diabetes  Clinic \t", 7);

        RBTGroup saved = persistedGroup();
        assertThat(saved.getGroupName()).isEqualTo("Diabetes  Clinic");
        assertThat(saved.getTemplateId()).isEqualTo(7);
    }

    @Test
    @DisplayName("should accept a name of exactly the column length")
    void shouldPersistName_atMaximumLength() {
        allowAdminWrite();
        String max = "é".repeat(RBTGroupManager.MAX_GROUP_NAME_LENGTH);

        manager.addTemplateToGroup(mockLoggedInInfo, max, 0);

        assertThat(persistedGroup().getGroupName()).isEqualTo(max);
    }

    @Test
    @DisplayName("should count the column length in code points, not UTF-16 units")
    void shouldPersistName_atMaximumCodePointsOfSupplementaryCharacters() {
        allowAdminWrite();
        String emoji = "\uD83D\uDE00".repeat(RBTGroupManager.MAX_GROUP_NAME_LENGTH);

        manager.addTemplateToGroup(mockLoggedInInfo, emoji, 0);

        assertThat(persistedGroup().getGroupName()).isEqualTo(emoji);
    }

    @Test
    @DisplayName("should refuse a supplementary-character name over the column length")
    void shouldThrowIllegalArgument_whenCodePointsExceedColumn() {
        allowAdminWrite();
        String emoji = "\uD83D\uDE00".repeat(RBTGroupManager.MAX_GROUP_NAME_LENGTH + 1);

        assertThatThrownBy(() -> manager.addTemplateToGroup(mockLoggedInInfo, emoji, 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mockRbtGroupDao, never()).persist(any(RBTGroup.class));
    }

    @Test
    @DisplayName("should refuse a name longer than the column")
    void shouldThrowIllegalArgument_whenNameTooLong() {
        allowAdminWrite();
        String tooLong = "a".repeat(RBTGroupManager.MAX_GROUP_NAME_LENGTH + 1);

        assertThatThrownBy(() -> manager.addTemplateToGroup(mockLoggedInInfo, tooLong, 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mockRbtGroupDao, never()).persist(any(RBTGroup.class));
    }

    @Test
    @DisplayName("should refuse a null or blank name")
    void shouldThrowIllegalArgument_whenNameNullOrBlank() {
        allowAdminWrite();

        assertThatThrownBy(() -> manager.addTemplateToGroup(mockLoggedInInfo, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> manager.addTemplateToGroup(mockLoggedInInfo, " \t ", 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mockRbtGroupDao, never()).persist(any(RBTGroup.class));
    }

    @Test
    @DisplayName("should strip Unicode spaces the browser also strips")
    void shouldStripUnicodeSpaces_beforePersisting() {
        allowAdminWrite();

        manager.addTemplateToGroup(mockLoggedInInfo, "\u00A0\u3000Diabetes\u00A0", 0);

        assertThat(persistedGroup().getGroupName()).isEqualTo("Diabetes");
    }

    @Test
    @DisplayName("should strip a very long whitespace run in linear time")
    void shouldStripLongWhitespaceRun_inLinearTime() {
        allowAdminWrite();
        String padding = " ".repeat(300_000);

        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> manager.addTemplateToGroup(mockLoggedInInfo, padding + "Diabetes" + padding + "\uFEFF", 0));

        assertThat(persistedGroup().getGroupName()).isEqualTo("Diabetes");
    }

    @Test
    @DisplayName("should refuse a name that is only Unicode spaces")
    void shouldThrowIllegalArgument_whenNameOnlyUnicodeSpaces() {
        allowAdminWrite();

        assertThatThrownBy(() -> manager.addTemplateToGroup(mockLoggedInInfo, "\u00A0\u3000", 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mockRbtGroupDao, never()).persist(any(RBTGroup.class));
    }

    @Test
    @DisplayName("should refuse control characters that do not survive the HTML round trip")
    void shouldThrowIllegalArgument_whenNameHasControlCharacters() {
        allowAdminWrite();

        assertThatThrownBy(() -> manager.addTemplateToGroup(mockLoggedInInfo, "Diab\netes", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> manager.addTemplateToGroup(mockLoggedInInfo, "Diab\u0000etes", 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mockRbtGroupDao, never()).persist(any(RBTGroup.class));
    }

    @Test
    @DisplayName("should refuse to create a group the database cannot tell apart from an existing one")
    void shouldThrowIllegalArgument_whenGroupAlreadyExists() {
        allowAdminWrite();
        when(mockRbtGroupDao.getByGroupName("Zoe")).thenReturn(java.util.List.of(new RBTGroup()));

        assertThatThrownBy(() -> manager.addTemplateToGroup(mockLoggedInInfo, " Zoe ", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(mockRbtGroupDao, never()).persist(any(RBTGroup.class));
    }

    @Test
    @DisplayName("should not run the duplicate check when adding a template to an existing group")
    void shouldSkipDuplicateCheck_whenAddingTemplate() {
        allowAdminWrite();

        manager.addTemplateToGroup(mockLoggedInInfo, "Diabetes", 12);

        verify(mockRbtGroupDao, never()).getByGroupName(any());
        assertThat(persistedGroup().getTemplateId()).isEqualTo(12);
    }

    @Test
    @DisplayName("should not touch the DAO without admin write privilege")
    void shouldThrowSecurityRuntimeException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_admin", SecurityInfoManager.WRITE, null))
                .thenReturn(false);

        assertThatThrownBy(() -> manager.addTemplateToGroup(mockLoggedInInfo, "x", 0))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(mockRbtGroupDao);
    }
}
