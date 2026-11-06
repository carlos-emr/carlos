/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.location;

import static io.github.carlos_emr.carlos.appt.location.AppointmentLocationTestItems.LIST_ID;
import static io.github.carlos_emr.carlos.appt.location.AppointmentLocationTestItems.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListItemDao;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The Location List administration rules (#3982): privileges on {@code _admin.schedule} or
 * {@code _admin}, unique active names, the 255-character name limit, whitelisted styles, and
 * the booking order.
 */
@DisplayName("AppointmentLocationService")
@Tag("unit")
@Tag("appointment")
class AppointmentLocationServiceUnitTest {

    private LookupListDao lookupListDao;
    private LookupListItemDao lookupListItemDao;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private RecordingService service;
    private LookupList list;
    private List<LookupListItem> items;

    /** Records audit rows instead of writing them. */
    private static final class RecordingService extends AppointmentLocationService {
        private final List<String> audited = new ArrayList<>();

        RecordingService(LookupListDao lookupListDao, LookupListItemDao lookupListItemDao,
                         SecurityInfoManager securityInfoManager, AppointmentLocationResolver resolver) {
            super(lookupListDao, lookupListItemDao, securityInfoManager, resolver);
        }

        @Override
        protected void audit(LoggedInInfo loggedInInfo, String operation, String data) {
            audited.add(operation + "|" + data);
        }
    }

    @BeforeEach
    void setUp() {
        lookupListDao = mock(LookupListDao.class);
        lookupListItemDao = mock(LookupListItemDao.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        grant("_admin.schedule", "r", "w", "d");

        list = new LookupList();
        list.setId(LIST_ID);
        list.setName(AppointmentLocationResolver.LIST_NAME);
        items = new ArrayList<>(List.of(
                item(11, "Virtual", 1, true),
                item(12, "Room 2", 1, true),
                item(13, "Satellite office", 2, false)));
        list.setItems(items);
        when(lookupListDao.findByName(AppointmentLocationResolver.LIST_NAME)).thenReturn(list);
        when(lookupListItemDao.findByLookupListId(eq(LIST_ID), any(Boolean.class))).thenAnswer(inv -> {
            boolean active = inv.getArgument(1);
            return items.stream().filter(i -> i.isActive() == active).toList();
        });
        when(lookupListItemDao.find(anyInt())).thenAnswer(inv -> {
            Integer id = inv.getArgument(0);
            return items.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
        });
        doAnswer(inv -> {
            LookupListItem added = inv.getArgument(0);
            added.setId(100 + items.size());
            items.add(added);
            return null;
        }).when(lookupListItemDao).persist(any(LookupListItem.class));

        service = new RecordingService(lookupListDao, lookupListItemDao, securityInfoManager,
                new AppointmentLocationResolver(lookupListDao, () -> false));
    }

    private void grant(String object, String... privileges) {
        for (String privilege : privileges) {
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(object), eq(privilege), isNull())).thenReturn(true);
        }
    }

    private void revokeAll() {
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(false);
    }

    @Nested
    @DisplayName("privileges")
    class Privileges {

        @Test
        void shouldAllowChanges_withAdminWriteInsteadOfScheduleWrite() {
            revokeAll();
            grant("_admin", "w");

            service.updateColour(loggedInInfo, 11, "#123456");

            assertThat(items.get(0).getColour()).isEqualTo("#123456");
        }

        @Test
        void shouldRefuseChanges_withoutWriteOnEitherObject() {
            revokeAll();
            grant("_admin.schedule", "r");

            assertThatThrownBy(() -> service.addLocation(loggedInInfo, "Room 5"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin.schedule)");
            verify(lookupListItemDao, never()).persist(any());
            assertThat(service.audited).isEmpty();
        }

        @Test
        void shouldRequireDelete_toDeactivate() {
            revokeAll();
            grant("_admin.schedule", "r", "w");

            assertThatThrownBy(() -> service.deactivate(loggedInInfo, 11))
                    .isInstanceOf(SecurityException.class);
            assertThat(items.get(0).isActive()).isTrue();
            verify(lookupListItemDao, never()).merge(any());
        }

        @Test
        void shouldRequireRead_toLoad() {
            revokeAll();

            assertThatThrownBy(() -> service.load(loggedInInfo)).isInstanceOf(SecurityException.class);
        }
    }

    @Nested
    @DisplayName("adding")
    class Adding {

        @Test
        void shouldAddTrimmedName_atEndOfBookingOrder() {
            LookupListItem added = service.addLocation(loggedInInfo, "  Room 5 ");

            assertThat(added.getLabel()).isEqualTo("Room 5");
            assertThat(added.getValue()).isEqualTo("Room 5");
            assertThat(added.getLookupListId()).isEqualTo(LIST_ID);
            assertThat(added.isActive()).isTrue();
            assertThat(added.getDisplayOrder()).isEqualTo(2);
            assertThat(added.getCreatedBy()).isEqualTo("999998");
            assertThat(service.audited).containsExactly("addLocation|lookupListItem id=" + added.getId() + " added");
        }

        @Test
        void shouldRefuseName_thatAnActiveLocationHas() {
            assertThatThrownBy(() -> service.addLocation(loggedInInfo, " virtual "))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .satisfies(e -> {
                        AppointmentLocationChangeRefusedException refused = (AppointmentLocationChangeRefusedException) e;
                        assertThat(refused.getMessageKey()).isEqualTo("admin.appt.location.msg.nameInUse");
                        assertThat(refused.getMessageParam()).isEqualTo("virtual");
                    });
            verify(lookupListItemDao, never()).persist(any());
        }

        @Test
        void shouldAllowName_thatOnlyAnInactiveLocationHas() {
            assertThat(service.addLocation(loggedInInfo, "Satellite office").isActive()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void shouldRefuseName_whenBlank(String name) {
            assertThatThrownBy(() -> service.addLocation(loggedInInfo, name))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.nameRequired");
        }

        @Test
        void shouldRefuseName_longerThanLocationColumn() {
            String tooLong = "x".repeat(AppointmentLocationResolver.LABEL_MAX_LENGTH + 1);

            assertThatThrownBy(() -> service.addLocation(loggedInInfo, tooLong))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.nameTooLong");
            assertThat(service.addLocation(loggedInInfo, "x".repeat(AppointmentLocationResolver.LABEL_MAX_LENGTH))
                    .getValue()).hasSize(50);
        }

        @Test
        void shouldAddPastedLines_skippingBlanksAndDuplicates() {
            AppointmentLocationService.AddResult result = service.addLocations(loggedInInfo,
                    "Room 5\r\n\nVirtual\nroom 5\n  Lab  \n");

            assertThat(result.added()).isEqualTo(2);
            assertThat(result.skipped()).isEqualTo(2);
            assertThat(items).extracting(LookupListItem::getLabel).endsWith("Room 5", "Lab");
            assertThat(items.get(3).getDisplayOrder()).isEqualTo(2);
            assertThat(items.get(4).getDisplayOrder()).isEqualTo(3);
            assertThat(service.audited).containsExactly("addLocations|2 locations added, 2 skipped");
        }

        @Test
        void shouldAddNothing_whenOnePastedLineIsTooLong() {
            String pasted = "Room 5\n" + "x".repeat(AppointmentLocationResolver.LABEL_MAX_LENGTH + 1);

            assertThatThrownBy(() -> service.addLocations(loggedInInfo, pasted))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class);
            verify(lookupListItemDao, never()).persist(any());
        }

        @Test
        void shouldRefusePaste_overTheLineLimit() {
            String pasted = String.join("\n", java.util.stream.IntStream
                    .rangeClosed(1, AppointmentLocationService.PASTE_MAX_LINES + 1).mapToObj(i -> "Room " + i).toList());

            assertThatThrownBy(() -> service.addLocations(loggedInInfo, pasted))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.pasteTooLong");
        }

        @ParameterizedTest
        @ValueSource(strings = {"Room\t5", "Room\u202e5", "Room\u200b5", "Room\u00005"})
        void shouldRefuseName_withControlOrFormatCharacters(String name) {
            assertThatThrownBy(() -> service.addLocation(loggedInInfo, name))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.nameInvalid");
            verify(lookupListItemDao, never()).persist(any());
        }

        @Test
        void shouldRefusePaste_longerThanItCouldEverNeedBeforeSplitting() {
            String pasted = "a\n".repeat(AppointmentLocationService.PASTE_MAX_LENGTH);

            assertThatThrownBy(() -> service.addLocations(loggedInInfo, pasted))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.pasteTooLong");
            verify(lookupListItemDao, never()).persist(any());
        }

        @Test
        void shouldAcceptPaste_ofEveryLineAtTheNameLimit() {
            String pasted = String.join("\r\n", java.util.stream.IntStream
                    .rangeClosed(1, AppointmentLocationService.PASTE_MAX_LINES)
                    .mapToObj(i -> String.format("%03d", i) + "x".repeat(AppointmentLocationResolver.LABEL_MAX_LENGTH - 3)).toList());

            assertThat(pasted.length()).isLessThanOrEqualTo(AppointmentLocationService.PASTE_MAX_LENGTH);
            assertThat(service.addLocations(loggedInInfo, pasted).added()).isEqualTo(AppointmentLocationService.PASTE_MAX_LINES);
        }

        @Test
        void shouldRefusePaste_whenEmpty() {
            assertThatThrownBy(() -> service.addLocations(loggedInInfo, "\n \n"))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.nameRequired");
        }

        @Test
        void shouldCreateMissingList_beforeFirstAdd() {
            when(lookupListDao.findByName(AppointmentLocationResolver.LIST_NAME)).thenReturn(null);
            doAnswer(inv -> {
                ((LookupList) inv.getArgument(0)).setId(LIST_ID);
                return null;
            }).when(lookupListDao).persist(any(LookupList.class));

            service.addLocation(loggedInInfo, "Room 5");

            ArgumentCaptor<LookupList> created = ArgumentCaptor.forClass(LookupList.class);
            verify(lookupListDao).persist(created.capture());
            assertThat(created.getValue().getName()).isEqualTo(AppointmentLocationResolver.LIST_NAME);
            assertThat(created.getValue().isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("changing")
    class Changing {

        @Test
        void shouldRename_keepingOtherActiveNamesDistinct() {
            service.rename(loggedInInfo, 12, " Room Two ");
            assertThat(items.get(1).getLabel()).isEqualTo("Room Two");

            assertThatThrownBy(() -> service.rename(loggedInInfo, 12, "VIRTUAL"))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.nameInUse");
            assertThat(items.get(1).getLabel()).isEqualTo("Room Two");
        }

        @Test
        void shouldAllowRename_toOwnNameInDifferentCase() {
            service.rename(loggedInInfo, 11, "VIRTUAL");

            assertThat(items.get(0).getLabel()).isEqualTo("VIRTUAL");
        }

        @Test
        void shouldRefuseChange_toItemOfAnotherList() {
            LookupListItem reasonCode = item(77, "Follow-up", 1, true);
            reasonCode.setLookupListId(1);
            items.add(reasonCode);

            assertThatThrownBy(() -> service.rename(loggedInInfo, 77, "Hijacked"))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.notFound");
            assertThat(reasonCode.getLabel()).isEqualTo("Follow-up");
            assertThatThrownBy(() -> service.updateColour(loggedInInfo, 0, "#123456"))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class);
        }

        @Test
        void shouldSetAndClearColour_lowerCased() {
            service.updateColour(loggedInInfo, 11, "#ABCDEF");
            assertThat(items.get(0).getColour()).isEqualTo("#abcdef");

            service.updateColour(loggedInInfo, 11, "");
            assertThat(items.get(0).getColour()).isNull();
            assertThat(service.audited).containsExactly(
                    "updateColour|lookupListItem id=11 colour updated", "updateColour|lookupListItem id=11 colour cleared");
        }

        @Test
        void shouldRefuseColour_whenNotHexTriplet() {
            assertThatThrownBy(() -> service.updateColour(loggedInInfo, 11, "red"))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.invalidColour");
            verify(lookupListItemDao, never()).merge(any());
        }

        @Test
        void shouldSetIcon_onlyFromWhitelist() {
            service.updateIcon(loggedInInfo, 11, "fa-video");
            assertThat(items.get(0).getIcon()).isEqualTo("fa-video");

            assertThatThrownBy(() -> service.updateIcon(loggedInInfo, 11, "fa-video fa-spin"))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.invalidIcon");
            assertThat(items.get(0).getIcon()).isEqualTo("fa-video");
        }

        @Test
        void shouldMoveDown_renumberingRepeatedOrders() {
            assertThat(service.move(loggedInInfo, 11, false, 1)).isTrue();

            assertThat(items.get(1).getDisplayOrder()).isEqualTo(1);
            assertThat(items.get(0).getDisplayOrder()).isEqualTo(2);
            assertThat(service.audited).containsExactly("move|lookupListItem id=11 moved down");
        }

        @Test
        void shouldNotMove_pastEitherEnd() {
            assertThat(service.move(loggedInInfo, 11, true, null)).isFalse();
            assertThat(service.move(loggedInInfo, 12, false, null)).isFalse();
            assertThat(service.audited).isEmpty();
        }

        @Test
        void shouldRefuseMove_whenNoLongerAtShownPosition() {
            // A repeated click posts the place the page showed; the first click already moved it.
            assertThat(service.move(loggedInInfo, 11, false, 1)).isTrue();

            assertThatThrownBy(() -> service.move(loggedInInfo, 11, false, 1))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.listChanged");
            assertThat(items.get(0).getDisplayOrder()).isEqualTo(2);
            assertThat(service.audited).hasSize(1);
        }

        @Test
        void shouldRefuseMove_ofInactiveLocation() {
            assertThatThrownBy(() -> service.move(loggedInInfo, 13, true, null))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class);
        }

        @Test
        void shouldDeactivate_andRestoreAtEndOfOrder() {
            service.deactivate(loggedInInfo, 11);
            assertThat(items.get(0).isActive()).isFalse();

            service.restore(loggedInInfo, 13);
            assertThat(items.get(2).isActive()).isTrue();
            assertThat(items.get(2).getDisplayOrder()).isEqualTo(2);
            assertThat(service.audited).containsExactly(
                    "deactivate|lookupListItem id=11 deactivated", "restore|lookupListItem id=13 reactivated");
        }

        @Test
        void shouldRefuseRestore_whenAnActiveLocationHasItsName() {
            items.add(item(14, "satellite OFFICE", 3, true));

            assertThatThrownBy(() -> service.restore(loggedInInfo, 13))
                    .isInstanceOf(AppointmentLocationChangeRefusedException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.location.msg.nameInUse");
            assertThat(items.get(2).isActive()).isFalse();
        }

        @Test
        void shouldIgnoreDeactivate_ofAlreadyInactiveLocation() {
            service.deactivate(loggedInInfo, 13);

            verify(lookupListItemDao, never()).merge(any());
            assertThat(service.audited).isEmpty();
        }
    }
}
