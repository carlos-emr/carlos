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
package io.github.carlos_emr.carlos.email.core;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit") @Tag("fast") @Tag("email")
class EmailFooterServiceUnitTest {
    private final UserPropertyDAO dao = mock(UserPropertyDAO.class);
    private final EmailFooterService service = new EmailFooterService(dao);
    private UserProperty row(int id, String owner, String value) {
        var row = new UserProperty();
        ReflectionTestUtils.setField(row, "id", id);
        row.setProviderNo(owner);
        row.setName("email_footer");
        row.setValue(value);
        return row;
    }

    @Test void shouldReturnEmptyPersonal_whenNoRowOrLegacyBlank() {
        when(dao.getAllProperties("email_footer", List.of("101"))).thenReturn(List.of());
        assertThat(service.ownFooter("101")).isEmpty();
        when(dao.getAllProperties("email_footer", List.of("101"))).thenReturn(List.of(row(1,"101","<b> </b>")));
        assertThat(service.ownFooter("101")).isEmpty();
        verify(dao, never()).findClinicEmailFooter();
        verify(dao, never()).lockClinicEmailFooterSettings();
    }

    @Test void shouldReadOnlyLoggedInOwner_andChooseOldestDuplicate() {
        when(dao.getAllProperties("email_footer", List.of("101"))).thenReturn(List.of(
                row(3,"101","Later"), row(2,"101","<b>Own</b><script>alert(1)</script>")));
        assertThat(service.ownFooter("101")).isEqualTo("<b>Own</b>");
        verify(dao).getAllProperties("email_footer", List.of("101"));
        verify(dao, never()).findClinicEmailFooter();
    }

    @Test void shouldIgnoreMissingOwner_withoutGlobalPropertyLookup() {
        assertThat(service.ownFooter(null)).isEmpty();
        assertThat(service.ownFooter(" ")).isEmpty();
        verifyNoInteractions(dao);
    }

    @Test void shouldSerializeFirstSave_onExistingOwnerWithoutTouchingClinic() {
        when(dao.findPersonalEmailFooterForUpdate("101")).thenReturn(List.of());
        service.saveOwnFooter("101", "<b>Own</b><img src=x>", EmailFooterService.revisionOf("101", ""));
        var order = inOrder(dao);
        order.verify(dao).lockPersonalEmailFooterOwner("101");
        order.verify(dao).findPersonalEmailFooterForUpdate("101");
        order.verify(dao).savePersonalEmailFooterRow(eq("101"), argThat((UserProperty r) -> "101".equals(r.getProviderNo())
                && "email_footer".equals(r.getName()) && "<b>Own</b>".equals(r.getValue())));
        verify(dao, never()).findClinicEmailFooter();
        verify(dao, never()).lockClinicEmailFooterSettings();
    }

    @Test void shouldClearOnlyPersonalRows_andNeverCopyClinic() {
        var first=row(1,"101","Own");
        var second=row(2,"101","Duplicate");
        when(dao.findPersonalEmailFooterForUpdate("101")).thenReturn(List.of(first,second));
        service.saveOwnFooter("101", "<div> </div>", EmailFooterService.revisionOf("101", "Own"));
        verify(dao).deletePersonalEmailFooterRow("101",1);
        verify(dao).deletePersonalEmailFooterRow("101",2);
        verify(dao,never()).savePersonalEmailFooterRow(anyString(),any(UserProperty.class));
        verify(dao,never()).findClinicEmailFooter();
    }

    @Test void shouldCleanDuplicatesAfterUpdatingOldestPersonalRow() {
        var first=row(1,"101","Own");
        var second=row(2,"101","Duplicate");
        when(dao.findPersonalEmailFooterForUpdate("101")).thenReturn(List.of(first,second));
        service.saveOwnFooter("101", "New personal", EmailFooterService.revisionOf("101", "Own"));
        assertThat(first.getValue()).isEqualTo("New personal");
        verify(dao).savePersonalEmailFooterRow("101",first);
        verify(dao).deletePersonalEmailFooterRow("101",2);
    }

    @Test void shouldRefuseOversizeBeforeAnyDatabaseWork_includingExpandedLinkAddress() {
        assertThatThrownBy(()->service.saveOwnFooter("101","x".repeat(2001), EmailFooterService.revisionOf("101", "")))
                .isInstanceOf(EmailFooterService.FooterTooLongException.class);
        assertThatThrownBy(()->service.saveOwnFooter("101","<a href=https://example.test/"+"x".repeat(2000)+">Short</a>", EmailFooterService.revisionOf("101", "")))
                .isInstanceOf(EmailFooterService.FooterTooLongException.class);
        assertThatThrownBy(()->service.saveOwnFooter("101","x".repeat(40001), EmailFooterService.revisionOf("101", "")))
                .isInstanceOf(EmailFooterService.FooterTooLongException.class);
        verifyNoInteractions(dao);
    }

    @Test void shouldRejectMissingFieldWithoutInterpretingItAsClear() {
        assertThatThrownBy(()->service.saveOwnFooter("101",null, EmailFooterService.revisionOf("101", ""))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dao);
    }
    @Test void shouldAcceptSamePersonal_andStillCleanDuplicatesWithoutChangedRowCountDependence(){
        var first=row(1,"101","Own");
        var duplicate=row(2,"101","Later duplicate");
        when(dao.findPersonalEmailFooterForUpdate("101")).thenReturn(List.of(first,duplicate));
        service.saveOwnFooter("101","Own", EmailFooterService.revisionOf("101", "Own"));
        verify(dao,never()).savePersonalEmailFooterRow(anyString(),any(UserProperty.class));
        verify(dao).deletePersonalEmailFooterRow("101",2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "Stale replacement"})
    void shouldRefuseAnOlderEditorIncludingClear_withoutWriting(String submitted) {
        when(dao.findPersonalEmailFooterForUpdate("101"))
                .thenReturn(List.of(row(1, "101", "Newer personal")));
        assertThatThrownBy(() -> service.saveOwnFooter("101", submitted,
                EmailFooterService.revisionOf("101", "Older personal")))
                .isInstanceOf(EmailFooterService.StaleFooterException.class);
        verify(dao).lockPersonalEmailFooterOwner("101");
        verify(dao, never()).savePersonalEmailFooterRow(any(), any());
        verify(dao, never()).deletePersonalEmailFooterRow(any(), any());
    }

    @Test
    void shouldRefuseMissingRevision_withoutDatabaseWork() {
        assertThatThrownBy(() -> service.saveOwnFooter("101", "Stale replacement", null))
                .isInstanceOf(EmailFooterService.StaleFooterException.class);
        verifyNoInteractions(dao);
    }

}
