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
package io.github.carlos_emr.carlos.billings.ca.bc.data;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingNoteDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingNotes;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regression for issue #4427: parameterized note writes must not store SQL escapes. */
@DisplayName("BC billing note literal text")
@Tag("unit")
@Tag("billing")
class BillingNoteUnitTest extends CarlosUnitTestBase {
    private BillingNoteDao noteDao;
    private BillingNote notes;

    @BeforeEach
    void setUp() {
        noteDao = mock(BillingNoteDao.class);
        registerMock(BillingNoteDao.class, noteDao);
        notes = new BillingNote();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "patient's card re-read",
            "O'Neil's path C:\\clinic\\notes",
            "literal \\ existing backslash",
            "François: 'quoted'; UPDATE billingnote SET note='literal' --"
    })
    void shouldPersistLiteralText_whenNoteContainsSqlMetacharacters(String text) {
        notes.addNote("101", "999998", text);

        ArgumentCaptor<BillingNotes> saved = ArgumentCaptor.forClass(BillingNotes.class);
        verify(noteDao).persist(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo(text);
        assertThat(saved.getValue().getBillingmasterNo()).isEqualTo(101);
        assertThat(saved.getValue().getProviderNo()).isEqualTo("999998");
        assertThat(saved.getValue().getNoteType()).isEqualTo(BillingNotes.DEFAULT_NOTE_TYPE);
        assertThat(saved.getValue().getCreatedate()).isNotNull();
    }

    @Test
    void shouldKeepSameLiteralText_whenReadBackAndSavedRepeatedly() {
        String original = "patient's card: C:\\clinic\\notes; already\\'literal";
        AtomicReference<BillingNotes> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(noteDao).persist(any(BillingNotes.class));
        when(noteDao.findSingleNote(101, BillingNotes.DEFAULT_NOTE_TYPE))
                .thenAnswer(invocation -> stored.get());

        notes.addNote("101", "999998", original);
        for (int save = 0; save < 3; save++) {
            String reopened = notes.getNote("101");
            assertThat(reopened).as("note after save %s", save + 1).isEqualTo(original);
            notes.addNote("101", "999998", reopened);
        }

        assertThat(notes.getNote("101")).isEqualTo(original);
        verify(noteDao, times(4)).persist(any(BillingNotes.class));
    }

    @Test
    void shouldKeepExistingLineNormalization_whenNoteSpansLines() {
        notes.addNote("101", "999998", "first\r\nsecond\nthird");

        ArgumentCaptor<BillingNotes> saved = ArgumentCaptor.forClass(BillingNotes.class);
        verify(noteDao).persist(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo("first  second third");
    }

    @Test
    void shouldCopyLiteralNoteToEachClaim_whenInvoiceHasMultipleClaims() {
        BillingmasterDAO masterDao = mock(BillingmasterDAO.class);
        registerMock(BillingmasterDAO.class, masterDao);
        Billingmaster first = mock(Billingmaster.class);
        Billingmaster second = mock(Billingmaster.class);
        when(first.getBillingmasterNo()).thenReturn(101);
        when(second.getBillingmasterNo()).thenReturn(102);
        when(masterDao.getBillingmasterByBillingNo(77)).thenReturn(List.of(first, second));
        String original = "patient's shared note C:\\clinic";

        notes.addNoteFromBillingNo("77", "999998", original);

        ArgumentCaptor<BillingNotes> saved = ArgumentCaptor.forClass(BillingNotes.class);
        verify(noteDao, times(2)).persist(saved.capture());
        assertThat(saved.getAllValues()).extracting(BillingNotes::getBillingmasterNo).containsExactly(101, 102);
        assertThat(saved.getAllValues()).extracting(BillingNotes::getNote).containsExactly(original, original);
    }
}
