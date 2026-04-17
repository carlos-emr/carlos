/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for hashed case note password storage and unlock verification.
 *
 * @since 2026-04-17
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CaseManagementManager unlockNote unit tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("casemgmt")
public class CaseManagementManagerUnlockNoteUnitTest extends CarlosUnitTestBase {

    private static final Long TEST_NOTE_ID = 42L;
    private static final String RAW_PASSWORD = "S3cret passphrase";

    @Mock
    private CaseManagementNoteDAO mockCaseManagementNoteDAO;

    @Mock
    private CaseManagementNoteLinkDAO mockCaseManagementNoteLinkDAO;

    private CaseManagementManagerImpl manager;

    @BeforeEach
    void setUp() {
        registerMock(CaseManagementNoteLinkDAO.class, mockCaseManagementNoteLinkDAO);

        manager = new CaseManagementManagerImpl();
        manager.setCaseManagementNoteDAO(mockCaseManagementNoteDAO);
    }

    @Nested
    @DisplayName("CaseManagementNote password storage")
    class CaseManagementNotePasswordStorage {

        @Test
        @DisplayName("should hash plaintext note passwords when set")
        void shouldHashPassword_whenSettingPlaintextPassword() {
            CaseManagementNote note = new CaseManagementNote();

            note.setPassword(RAW_PASSWORD);

            assertThat(note.getPassword()).startsWith("{bcrypt}");
            assertThat(note.getPassword()).isNotEqualTo(RAW_PASSWORD);
            assertThat(EncryptionUtils.verify(RAW_PASSWORD, note.getPassword())).isTrue();
        }

        @Test
        @DisplayName("should preserve existing bcrypt hashes when password is already encoded")
        void shouldPreserveHash_whenPasswordIsAlreadyEncoded() {
            CaseManagementNote note = new CaseManagementNote();
            String encodedPassword = EncryptionUtils.hash(RAW_PASSWORD);

            note.setPassword(encodedPassword);

            assertThat(note.getPassword()).isEqualTo(encodedPassword);
        }

        @Test
        @DisplayName("should hash passwords that only mimic the bcrypt prefix")
        void shouldHashPassword_whenPasswordHasFakeBcryptPrefix() {
            CaseManagementNote note = new CaseManagementNote();
            String prefixedPlaintextPassword = "{bcrypt}plaintext";

            note.setPassword(prefixedPlaintextPassword);

            assertThat(note.getPassword()).startsWith("{bcrypt}");
            assertThat(note.getPassword()).isNotEqualTo(prefixedPlaintextPassword);
            assertThat(EncryptionUtils.verify(prefixedPlaintextPassword, note.getPassword())).isTrue();
        }
    }

    @Nested
    @DisplayName("unlockNote verification")
    class UnlockNoteVerification {

        @Test
        @DisplayName("should return true when supplied password matches stored bcrypt hash")
        void shouldReturnTrue_whenPasswordMatchesStoredHash() {
            CaseManagementNote note = createLockedNote(EncryptionUtils.hash(RAW_PASSWORD));
            when(mockCaseManagementNoteDAO.getNote(TEST_NOTE_ID)).thenReturn(note);

            boolean unlocked = manager.unlockNote(TEST_NOTE_ID.intValue(), RAW_PASSWORD);

            assertThat(unlocked).isTrue();
            verify(mockCaseManagementNoteDAO, never()).updateNote(any(CaseManagementNote.class));
        }

        @Test
        @DisplayName("should return false when supplied password does not match stored bcrypt hash")
        void shouldReturnFalse_whenPasswordDoesNotMatchStoredHash() {
            CaseManagementNote note = createLockedNote(EncryptionUtils.hash(RAW_PASSWORD));
            when(mockCaseManagementNoteDAO.getNote(TEST_NOTE_ID)).thenReturn(note);

            boolean unlocked = manager.unlockNote(TEST_NOTE_ID.intValue(), "wrong password");

            assertThat(unlocked).isFalse();
            verify(mockCaseManagementNoteDAO, never()).updateNote(any(CaseManagementNote.class));
        }

        @Test
        @DisplayName("should upgrade legacy plaintext password when unlock succeeds")
        void shouldUpgradeLegacyPassword_whenPlaintextPasswordMatches() {
            CaseManagementNote note = createLockedNoteWithDirectPasswordInjection(RAW_PASSWORD);
            when(mockCaseManagementNoteDAO.getNote(TEST_NOTE_ID)).thenReturn(note);

            boolean unlocked = manager.unlockNote(TEST_NOTE_ID.intValue(), RAW_PASSWORD);

            assertThat(unlocked).isTrue();
            assertThat(note.getPassword()).startsWith("{bcrypt}");
            assertThat(EncryptionUtils.verify(RAW_PASSWORD, note.getPassword())).isTrue();
            verify(mockCaseManagementNoteDAO).updateNote(eq(note));
        }

        @Test
        @DisplayName("should return false when supplied password does not match legacy plaintext password")
        void shouldReturnFalse_whenLegacyPlaintextPasswordDoesNotMatch() {
            CaseManagementNote note = createLockedNoteWithDirectPasswordInjection(RAW_PASSWORD);
            when(mockCaseManagementNoteDAO.getNote(TEST_NOTE_ID)).thenReturn(note);

            boolean unlocked = manager.unlockNote(TEST_NOTE_ID.intValue(), "wrong password");

            assertThat(unlocked).isFalse();
            assertThat(note.getPassword()).isEqualTo(RAW_PASSWORD);
            verify(mockCaseManagementNoteDAO, never()).updateNote(any(CaseManagementNote.class));
        }

        @Test
        @DisplayName("should return false when supplied password is null")
        void shouldReturnFalse_whenPasswordIsNull() {
            boolean unlocked = manager.unlockNote(TEST_NOTE_ID.intValue(), null);

            assertThat(unlocked).isFalse();
            verify(mockCaseManagementNoteDAO, never()).getNote(any());
        }

        @Test
        @DisplayName("should return false when note is not found")
        void shouldReturnFalse_whenNoteIsNotFound() {
            when(mockCaseManagementNoteDAO.getNote(TEST_NOTE_ID)).thenReturn(null);

            boolean unlocked = manager.unlockNote(TEST_NOTE_ID.intValue(), RAW_PASSWORD);

            assertThat(unlocked).isFalse();
            verify(mockCaseManagementNoteDAO, never()).updateNote(any(CaseManagementNote.class));
        }

        @Test
        @DisplayName("should return false when note is not locked")
        void shouldReturnFalse_whenNoteIsNotLocked() {
            CaseManagementNote note = createLockedNoteWithDirectPasswordInjection(RAW_PASSWORD);
            note.setLocked(false);
            when(mockCaseManagementNoteDAO.getNote(TEST_NOTE_ID)).thenReturn(note);

            boolean unlocked = manager.unlockNote(TEST_NOTE_ID.intValue(), RAW_PASSWORD);

            assertThat(unlocked).isFalse();
            verify(mockCaseManagementNoteDAO, never()).updateNote(any(CaseManagementNote.class));
        }

        @Test
        @DisplayName("should return false when stored note password is null")
        void shouldReturnFalse_whenStoredPasswordIsNull() {
            CaseManagementNote note = createLockedNoteWithDirectPasswordInjection(null);
            when(mockCaseManagementNoteDAO.getNote(TEST_NOTE_ID)).thenReturn(note);

            boolean unlocked = manager.unlockNote(TEST_NOTE_ID.intValue(), RAW_PASSWORD);

            assertThat(unlocked).isFalse();
            verify(mockCaseManagementNoteDAO, never()).updateNote(any(CaseManagementNote.class));
        }
    }

    private CaseManagementNote createLockedNote(String storedPassword) {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(TEST_NOTE_ID);
        note.setLocked(true);
        note.setPassword(storedPassword);
        return note;
    }

    private CaseManagementNote createLockedNoteWithDirectPasswordInjection(String storedPassword) {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(TEST_NOTE_ID);
        note.setLocked(true);
        injectDependency(note, "password", storedPassword);
        return note;
    }
}
