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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.prevention.PreventionDS;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PreventionManagerImpl} business logic.
 *
 * <p>This test class verifies prevention management operations including CRUD,
 * security privilege enforcement, stop-sign logic for disabling prevention warnings,
 * and hide-item property management. All tests run without a Spring context
 * or database using mocked DAOs.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>Security privilege verification for prevention access</li>
 *   <li>Prevention extension (PreventionExt) persistence</li>
 *   <li>Property-based configuration for hiding items and stop signs</li>
 *   <li>Regex-based parsing of disabled prevention lists</li>
 *   <li>Manager-DAO interaction patterns</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see PreventionManagerImpl
 * @see PreventionUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Prevention Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("prevention")
public class PreventionManagerUnitTest extends PreventionUnitTestBase {

    @Mock
    private PreventionDao mockPreventionDao;

    @Mock
    private PreventionExtDao mockPreventionExtDao;

    @Mock
    private PropertyDao mockPropertyDao;

    @Mock
    private PreventionDS mockPreventionDS;

    private PreventionManagerImpl preventionManager;

    /**
     * Initializes the test environment before each test method.
     *
     * <p>Registers mock DAOs with SpringUtils (including PreventionDS used by
     * getCustomPreventionItems), stubs both SecurityInfoManager hasPrivilege
     * overloads (String and int) to grant all privileges by default, creates a
     * fresh {@link PreventionManagerImpl} instance, and injects mock dependencies
     * (PreventionDao, PreventionExtDao, PropertyDao, PreventionDS,
     * SecurityInfoManager) via reflection.</p>
     */
    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils (used by getCustomPreventionItems via SpringUtils.getBean)
        registerMock(PreventionDao.class, mockPreventionDao);
        registerMock(PreventionExtDao.class, mockPreventionExtDao);
        registerMock(PropertyDao.class, mockPropertyDao);
        registerMock(PreventionDS.class, mockPreventionDS);

        // Security manager returns true for all privilege checks by default
        // Must stub BOTH overloads: hasPrivilege(..., String) and hasPrivilege(..., int)
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
            .thenReturn(true);
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt()))
            .thenReturn(true);

        // Create manager instance and inject dependencies
        preventionManager = new PreventionManagerImpl();

        injectDependency(preventionManager, "preventionDao", mockPreventionDao);
        injectDependency(preventionManager, "preventionExtDao", mockPreventionExtDao);
        injectDependency(preventionManager, "propertyDao", mockPropertyDao);
        injectDependency(preventionManager, "preventionDS", mockPreventionDS);
        injectDependency(preventionManager, "securityInfoManager", mockSecurityInfoManager);
    }

    /**
     * Tests for {@link PreventionManagerImpl#getPrevention(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer)}.
     */
    @Nested
    @DisplayName("getPrevention")
    @Tag("read")
    class GetPrevention {

        @Test
        @DisplayName("should return prevention when valid ID is provided")
        void shouldReturnPrevention_whenValidIdProvided() {
            // Given
            Prevention expected = createTestPreventionWithId(TEST_PREVENTION_ID);
            when(mockPreventionDao.find(TEST_PREVENTION_ID)).thenReturn(expected);

            // When
            Prevention result = preventionManager.getPrevention(mockLoggedInInfo, TEST_PREVENTION_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_PREVENTION_ID);
            assertThat(result.getDemographicId()).isEqualTo(TEST_DEMO_NO);
            verify(mockPreventionDao).find(TEST_PREVENTION_ID);
        }

        @Test
        @DisplayName("should return null when prevention not found")
        void shouldReturnNull_whenPreventionNotFound() {
            // Given
            when(mockPreventionDao.find(999)).thenReturn(null);

            // When
            Prevention result = preventionManager.getPrevention(mockLoggedInInfo, 999);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should delegate to DAO find method with correct ID")
        void shouldDelegateToDao_whenCalled() {
            // Given
            Integer id = 42;
            when(mockPreventionDao.find(id)).thenReturn(null);

            // When
            preventionManager.getPrevention(mockLoggedInInfo, id);

            // Then
            verify(mockPreventionDao).find(id);
        }
    }

    /**
     * Tests for {@link PreventionManagerImpl#getPreventionsByDemographicNo(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer)}.
     * This method includes a mandatory security privilege check.
     */
    @Nested
    @DisplayName("getPreventionsByDemographicNo")
    @Tag("read")
    class GetPreventionsByDemographicNo {

        @Test
        @DisplayName("should return preventions when authorized")
        void shouldReturnPreventions_whenAuthorized() {
            // Given
            List<Prevention> expected = List.of(
                createTestPreventionWithId(1),
                createTestPreventionWithId(2)
            );
            when(mockPreventionDao.findUniqueByDemographicId(TEST_DEMO_NO)).thenReturn(expected);

            // When
            List<Prevention> results = preventionManager.getPreventionsByDemographicNo(
                mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(results).hasSize(2);
            verify(mockPreventionDao).findUniqueByDemographicId(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should throw RuntimeException when security check fails")
        void shouldThrowException_whenSecurityCheckFails() {
            // Given
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_prevention"), anyString(), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() ->
                preventionManager.getPreventionsByDemographicNo(mockLoggedInInfo, TEST_DEMO_NO)
            ).isInstanceOf(RuntimeException.class)
             .hasMessageContaining("missing required sec object");
        }

        @Test
        @DisplayName("should not call DAO when security check fails")
        void shouldNotCallDao_whenSecurityCheckFails() {
            // Given
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_prevention"), anyString(), any()))
                .thenReturn(false);

            // When
            try {
                preventionManager.getPreventionsByDemographicNo(mockLoggedInInfo, TEST_DEMO_NO);
            } catch (RuntimeException ignored) {
                // expected
            }

            // Then
            verify(mockPreventionDao, never()).findUniqueByDemographicId(any());
        }

        @Test
        @DisplayName("should return empty list when no preventions exist")
        void shouldReturnEmptyList_whenNoPreventionsExist() {
            // Given
            when(mockPreventionDao.findUniqueByDemographicId(TEST_DEMO_NO))
                .thenReturn(Collections.emptyList());

            // When
            List<Prevention> results = preventionManager.getPreventionsByDemographicNo(
                mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@link PreventionManagerImpl#getPreventionExtByPrevention(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer)}.
     */
    @Nested
    @DisplayName("getPreventionExtByPrevention")
    class GetPreventionExtByPrevention {

        @Test
        @DisplayName("should return extensions when prevention ID is valid")
        void shouldReturnExtensions_whenPreventionIdValid() {
            // Given
            List<PreventionExt> expected = List.of(
                createTestPreventionExt(TEST_PREVENTION_ID, "lot", "ABC123"),
                createTestPreventionExt(TEST_PREVENTION_ID, "route", "IM")
            );
            when(mockPreventionExtDao.findByPreventionId(TEST_PREVENTION_ID)).thenReturn(expected);

            // When
            List<PreventionExt> results = preventionManager.getPreventionExtByPrevention(
                mockLoggedInInfo, TEST_PREVENTION_ID);

            // Then
            assertThat(results).hasSize(2);
            verify(mockPreventionExtDao).findByPreventionId(TEST_PREVENTION_ID);
        }

        @Test
        @DisplayName("should return empty list when no extensions exist")
        void shouldReturnEmptyList_whenNoExtensionsExist() {
            // Given
            when(mockPreventionExtDao.findByPreventionId(TEST_PREVENTION_ID))
                .thenReturn(Collections.emptyList());

            // When
            List<PreventionExt> results = preventionManager.getPreventionExtByPrevention(
                mockLoggedInInfo, TEST_PREVENTION_ID);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@link PreventionManagerImpl#addPreventionWithExts(Prevention, HashMap)}.
     */
    @Nested
    @DisplayName("addPreventionWithExts")
    @Tag("create")
    class AddPreventionWithExts {

        @Test
        @DisplayName("should persist prevention and all extensions")
        void shouldPersistPreventionAndExtensions_whenExtsProvided() {
            // Given
            Prevention prevention = createTestPreventionWithId(TEST_PREVENTION_ID);
            HashMap<String, String> exts = new HashMap<>();
            exts.put("lot", "ABC123");
            exts.put("route", "IM");

            // When
            preventionManager.addPreventionWithExts(prevention, exts);

            // Then
            verify(mockPreventionDao).persist(prevention);
            verify(mockPreventionExtDao, times(2)).persist(any(PreventionExt.class));
        }

        @Test
        @DisplayName("should persist prevention only when extensions are null")
        void shouldPersistPreventionOnly_whenExtsNull() {
            // Given
            Prevention prevention = createTestPrevention();

            // When
            preventionManager.addPreventionWithExts(prevention, null);

            // Then
            verify(mockPreventionDao).persist(prevention);
            verify(mockPreventionExtDao, never()).persist(any());
        }

        @Test
        @DisplayName("should not persist anything when prevention is null")
        void shouldNotPersist_whenPreventionNull() {
            // When
            preventionManager.addPreventionWithExts(null, new HashMap<>());

            // Then
            verify(mockPreventionDao, never()).persist(any());
            verify(mockPreventionExtDao, never()).persist(any());
        }

        @Test
        @DisplayName("should skip extensions with empty keys or values")
        void shouldSkipExtensions_whenKeysOrValuesEmpty() {
            // Given
            Prevention prevention = createTestPreventionWithId(TEST_PREVENTION_ID);
            HashMap<String, String> exts = new HashMap<>();
            exts.put("lot", "ABC123");
            exts.put("", "shouldSkip");
            exts.put("emptyVal", "");

            // When
            preventionManager.addPreventionWithExts(prevention, exts);

            // Then
            verify(mockPreventionDao).persist(prevention);
            // Only "lot" -> "ABC123" should be persisted; empty key and empty value are skipped
            verify(mockPreventionExtDao, times(1)).persist(any(PreventionExt.class));
        }

        @Test
        @DisplayName("should persist prevention with empty extensions map")
        void shouldPersistPrevention_whenExtsMapEmpty() {
            // Given
            Prevention prevention = createTestPrevention();
            HashMap<String, String> exts = new HashMap<>();

            // When
            preventionManager.addPreventionWithExts(prevention, exts);

            // Then
            verify(mockPreventionDao).persist(prevention);
            verify(mockPreventionExtDao, never()).persist(any());
        }
    }

    /**
     * Tests for hide prevention item methods:
     * {@link PreventionManagerImpl#isHidePrevItemExist()},
     * {@link PreventionManagerImpl#hideItem(String)},
     * {@link PreventionManagerImpl#getCustomPreventionItems()},
     * {@link PreventionManagerImpl#addCustomPreventionItems(String)}.
     */
    @Nested
    @DisplayName("Hide Prevention Items")
    class HidePreventionItems {

        @Test
        @DisplayName("should return true when hidden items property exists")
        void shouldReturnTrue_whenHiddenItemsExist() {
            // Given
            Property prop = new Property();
            prop.setName("hide_prevention_item");
            prop.setValue("Flu,Td");
            when(mockPropertyDao.findByName("hide_prevention_item"))
                .thenReturn(List.of(prop));

            // When
            boolean result = preventionManager.isHidePrevItemExist();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when no hidden items property exists")
        void shouldReturnFalse_whenNoHiddenItemsExist() {
            // Given
            when(mockPropertyDao.findByName("hide_prevention_item"))
                .thenReturn(Collections.emptyList());

            // When
            boolean result = preventionManager.isHidePrevItemExist();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when item is in hidden list")
        void shouldReturnTrue_whenItemIsHidden() {
            // Given
            Property prop = new Property();
            prop.setName("hide_prevention_item");
            prop.setValue("Flu, Td, HPV");
            when(mockPropertyDao.checkByName("hide_prevention_item")).thenReturn(prop);

            // When
            boolean result = preventionManager.hideItem("Td");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when item is not in hidden list")
        void shouldReturnFalse_whenItemNotHidden() {
            // Given
            Property prop = new Property();
            prop.setName("hide_prevention_item");
            prop.setValue("Flu, Td, HPV");
            when(mockPropertyDao.checkByName("hide_prevention_item")).thenReturn(prop);

            // When
            boolean result = preventionManager.hideItem("MMR");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when no hide property exists for hideItem")
        void shouldReturnFalse_whenNoPropertyExistsForHideItem() {
            // Given
            when(mockPropertyDao.checkByName("hide_prevention_item")).thenReturn(null);

            // When
            boolean result = preventionManager.hideItem("Flu");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return items from property via SpringUtils for getCustomPreventionItems")
        void shouldReturnItems_whenPropertyExists() {
            // Given - getCustomPreventionItems uses SpringUtils.getBean(PropertyDao.class)
            Property prop = new Property();
            prop.setName("hide_prevention_item");
            prop.setValue("Flu,Td");
            when(mockPropertyDao.checkByName("hide_prevention_item")).thenReturn(prop);

            // When
            String result = preventionManager.getCustomPreventionItems();

            // Then
            assertThat(result).isEqualTo("Flu,Td");
        }

        @Test
        @DisplayName("should return empty string when no custom items property exists")
        void shouldReturnEmptyString_whenNoCustomItemsExist() {
            // Given
            when(mockPropertyDao.checkByName("hide_prevention_item")).thenReturn(null);

            // When
            String result = preventionManager.getCustomPreventionItems();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should update existing property when adding custom items")
        void shouldUpdateExistingProperty_whenAddingCustomItems() {
            // Given
            Property existingProp = new Property();
            existingProp.setName("hide_prevention_item");
            existingProp.setValue("Flu");
            when(mockPropertyDao.findByName("hide_prevention_item"))
                .thenReturn(List.of(existingProp));
            when(mockPropertyDao.checkByName("hide_prevention_item"))
                .thenReturn(existingProp);

            // When
            preventionManager.addCustomPreventionItems("Flu,Td,HPV");

            // Then
            verify(mockPropertyDao).merge(existingProp);
            assertThat(existingProp.getValue()).isEqualTo("Flu,Td,HPV");
        }

        @Test
        @DisplayName("should create new property when no existing property for custom items")
        void shouldCreateNewProperty_whenNoExistingProperty() {
            // Given
            when(mockPropertyDao.findByName("hide_prevention_item"))
                .thenReturn(Collections.emptyList());

            // When
            preventionManager.addCustomPreventionItems("Flu,Td");

            // Then
            verify(mockPropertyDao).persist(argThat(prop ->
                prop instanceof Property
                && "hide_prevention_item".equals(((Property) prop).getName())
                && "Flu,Td".equals(((Property) prop).getValue())
            ));
        }
    }

    /**
     * Tests for prevention stop-sign logic:
     * {@link PreventionManagerImpl#isDisabled()},
     * {@link PreventionManagerImpl#isCreated()},
     * {@link PreventionManagerImpl#getPreventionStopSigns()},
     * {@link PreventionManagerImpl#isPrevDisabled(String)}.
     */
    @Nested
    @DisplayName("Prevention Stop Signs")
    class PreventionStopSigns {

        @Test
        @DisplayName("should report disabled when master stop sign is set")
        void shouldReturnDisabled_whenMasterIsSet() {
            // Given
            Property masterProp = new Property();
            masterProp.setName("hide_prevention_stop_signs");
            masterProp.setValue("master");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(masterProp));

            // When
            boolean result = preventionManager.isDisabled();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should report not disabled when false stop sign is set")
        void shouldReturnNotDisabled_whenFalseIsSet() {
            // Given
            Property falseProp = new Property();
            falseProp.setName("hide_prevention_stop_signs");
            falseProp.setValue("false");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(falseProp));

            // When
            boolean result = preventionManager.isDisabled();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should report disabled when no stop sign properties exist")
        void shouldReturnDisabled_whenNoPropertiesExist() {
            // Given - no properties in database
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(Collections.emptyList());

            // When
            boolean result = preventionManager.isDisabled();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should report not disabled when specific items are set")
        void shouldReturnNotDisabled_whenSpecificItemsExist() {
            // Given
            Property itemsProp = new Property();
            itemsProp.setName("hide_prevention_stop_signs");
            itemsProp.setValue("[Flu][Td]");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(itemsProp));

            // When
            boolean result = preventionManager.isDisabled();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should parse specific disabled prevention names from bracket format")
        void shouldParsePreventionNames_whenBracketFormatUsed() {
            // Given
            Property itemsProp = new Property();
            itemsProp.setName("hide_prevention_stop_signs");
            itemsProp.setValue("[Flu][Td][HPV]");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(itemsProp));

            // When
            Set<String> stopSigns = preventionManager.getPreventionStopSigns();

            // Then
            assertThat(stopSigns).containsExactlyInAnyOrder("Flu", "Td", "HPV");
        }

        @Test
        @DisplayName("should return true when specific prevention is disabled")
        void shouldReturnTrue_whenSpecificPreventionIsDisabled() {
            // Given
            Property itemsProp = new Property();
            itemsProp.setName("hide_prevention_stop_signs");
            itemsProp.setValue("[Flu][Td]");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(itemsProp));

            // When
            boolean result = preventionManager.isPrevDisabled("Flu");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when specific prevention is not disabled")
        void shouldReturnFalse_whenSpecificPreventionIsNotDisabled() {
            // Given
            Property itemsProp = new Property();
            itemsProp.setName("hide_prevention_stop_signs");
            itemsProp.setValue("[Flu][Td]");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(itemsProp));

            // When
            boolean result = preventionManager.isPrevDisabled("MMR");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should report created when stop sign properties exist")
        void shouldReturnTrue_whenStopSignPropertiesExist() {
            // Given
            Property prop = new Property();
            prop.setName("hide_prevention_stop_signs");
            prop.setValue("false");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(prop));

            // When
            boolean result = preventionManager.isCreated();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should report not created when no stop sign properties exist")
        void shouldReturnFalse_whenNoStopSignPropertiesExist() {
            // Given
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(Collections.emptyList());

            // When
            boolean result = preventionManager.isCreated();

            // Then
            assertThat(result).isFalse();
        }
    }

    /**
     * Tests for {@link PreventionManagerImpl#setDisabledPreventions(List)} and
     * {@link PreventionManagerImpl#getDisabledPreventions()}.
     */
    @Nested
    @DisplayName("Set and Get Disabled Preventions")
    class DisabledPreventions {

        @Test
        @DisplayName("should return false when null list is provided")
        void shouldReturnFalse_whenListIsNull() {
            // When
            boolean result = preventionManager.setDisabledPreventions(null);

            // Then
            assertThat(result).isFalse();
            verify(mockPropertyDao, never()).persist(any());
        }

        @Test
        @DisplayName("should save master value when master is provided")
        void shouldSaveMasterValue_whenMasterProvided() {
            // When
            boolean result = preventionManager.setDisabledPreventions(List.of("master"));

            // Then
            assertThat(result).isTrue();
            verify(mockPropertyDao).removeByName("hide_prevention_stop_signs");
            verify(mockPropertyDao).persist(argThat(prop ->
                prop instanceof Property
                && "hide_prevention_stop_signs".equals(((Property) prop).getName())
                && "master".equals(((Property) prop).getValue())
            ));
        }

        @Test
        @DisplayName("should save false value when false is provided")
        void shouldSaveFalseValue_whenFalseProvided() {
            // When
            boolean result = preventionManager.setDisabledPreventions(List.of("false"));

            // Then
            assertThat(result).isTrue();
            verify(mockPropertyDao).removeByName("hide_prevention_stop_signs");
            verify(mockPropertyDao).persist(argThat(prop ->
                prop instanceof Property
                && "false".equals(((Property) prop).getValue())
            ));
        }

        @Test
        @DisplayName("should save prevention names in bracket format")
        void shouldSavePreventionNames_whenListProvided() {
            // When
            boolean result = preventionManager.setDisabledPreventions(
                List.of("Flu", "Td", "HPV"));

            // Then
            assertThat(result).isTrue();
            verify(mockPropertyDao).removeByName("hide_prevention_stop_signs");
            verify(mockPropertyDao).persist(argThat(prop ->
                prop instanceof Property
                && "[Flu][Td][HPV]".equals(((Property) prop).getValue())
            ));
        }

        @Test
        @DisplayName("should parse disabled preventions from bracket format")
        void shouldParseDisabledPreventions_whenBracketFormatStored() {
            // Given
            Property prop = new Property();
            prop.setName("hide_prevention_stop_signs");
            prop.setValue("[Flu][Td][HPV]");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(prop));

            // When
            List<String> result = preventionManager.getDisabledPreventions();

            // Then
            assertThat(result).containsExactly("Flu", "Td", "HPV");
        }
    }

    /**
     * Tests for {@link PreventionManagerImpl#checkNames(String)}.
     * This method filters out disabled prevention warning names from a bracketed string.
     */
    @Nested
    @DisplayName("checkNames")
    class CheckNames {

        @Test
        @DisplayName("should filter out disabled preventions and keep enabled ones")
        void shouldFilterOutPreventions_whenDisabled() {
            // Given - "Flu" is disabled, "MMR" is not
            Property itemsProp = new Property();
            itemsProp.setName("hide_prevention_stop_signs");
            itemsProp.setValue("[Flu]");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(itemsProp));

            String input = "[Flu=Flu vaccine overdue][MMR=MMR vaccine overdue]";

            // When
            String result = preventionManager.checkNames(input);

            // Then
            assertThat(result).isEqualTo("[MMR vaccine overdue]");
        }

        @Test
        @DisplayName("should return empty string when all preventions are disabled")
        void shouldReturnEmptyString_whenAllDisabled() {
            // Given
            Property itemsProp = new Property();
            itemsProp.setName("hide_prevention_stop_signs");
            itemsProp.setValue("[Flu][Td]");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(itemsProp));

            String input = "[Flu=Flu vaccine overdue][Td=Td booster overdue]";

            // When
            String result = preventionManager.checkNames(input);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return all items when none are disabled")
        void shouldReturnAllItems_whenNoneDisabled() {
            // Given - module enabled with no specific items disabled
            Property prop = new Property();
            prop.setName("hide_prevention_stop_signs");
            prop.setValue("false");
            when(mockPropertyDao.findByName("hide_prevention_stop_signs"))
                .thenReturn(List.of(prop));

            String input = "[Flu=Flu overdue][MMR=MMR overdue]";

            // When
            String result = preventionManager.checkNames(input);

            // Then
            assertThat(result).isEqualTo("[Flu overdue][MMR overdue]");
        }
    }

    /**
     * Tests for date-based query methods:
     * {@link PreventionManagerImpl#getUpdatedAfterDate},
     * {@link PreventionManagerImpl#getByDemographicIdUpdatedAfterDate},
     * {@link PreventionManagerImpl#getPreventionsByProgramProviderDemographicDate}.
     */
    @Nested
    @DisplayName("Date-Based Queries")
    class DateBasedQueries {

        @Test
        @DisplayName("should return preventions updated after specified date")
        void shouldReturnPreventions_whenUpdatedAfterDate() {
            // Given
            Date cutoffDate = new Date();
            List<Prevention> expected = List.of(createTestPrevention());
            when(mockPreventionDao.findByUpdateDate(cutoffDate, 10)).thenReturn(expected);

            // When
            List<Prevention> results = preventionManager.getUpdatedAfterDate(
                mockLoggedInInfo, cutoffDate, 10);

            // Then
            assertThat(results).hasSize(1);
            verify(mockPreventionDao).findByUpdateDate(cutoffDate, 10);
        }

        @Test
        @DisplayName("should return preventions by demographic and date")
        void shouldReturnPreventions_whenFilteredByDemographicAndDate() {
            // Given
            Date cutoffDate = new Date();
            List<Prevention> expected = List.of(createTestPrevention());
            when(mockPreventionDao.findByDemographicIdAfterDatetimeExclusive(TEST_DEMO_NO, cutoffDate))
                .thenReturn(expected);

            // When
            List<Prevention> results = preventionManager.getByDemographicIdUpdatedAfterDate(
                mockLoggedInInfo, TEST_DEMO_NO, cutoffDate);

            // Then
            assertThat(results).hasSize(1);
            verify(mockPreventionDao).findByDemographicIdAfterDatetimeExclusive(TEST_DEMO_NO, cutoffDate);
        }

        @Test
        @DisplayName("should return preventions by program provider demographic and date")
        void shouldReturnPreventions_whenFilteredByProgramProviderDemographicDate() {
            // Given
            Calendar cal = Calendar.getInstance();
            List<Prevention> expected = List.of(createTestPrevention());
            when(mockPreventionDao.findByProviderDemographicLastUpdateDate(
                eq(TEST_PROVIDER), eq(TEST_DEMO_NO), any(Date.class), eq(50)))
                .thenReturn(expected);

            // When
            List<Prevention> results = preventionManager.getPreventionsByProgramProviderDemographicDate(
                mockLoggedInInfo, 1, TEST_PROVIDER, TEST_DEMO_NO, cal, 50);

            // Then
            assertThat(results).hasSize(1);
            verify(mockPreventionDao).findByProviderDemographicLastUpdateDate(
                eq(TEST_PROVIDER), eq(TEST_DEMO_NO), any(Date.class), eq(50));
        }
    }

    /**
     * Tests for {@link PreventionManagerImpl#getImmunizationsByDemographic(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer)}.
     * This method delegates to getPreventionsByDemographicNo and filters for immunizations.
     */
    @Nested
    @DisplayName("getImmunizationsByDemographic")
    class GetImmunizationsByDemographic {

        @Test
        @DisplayName("should return empty list when no preventions exist for demographic")
        void shouldReturnEmptyList_whenNoPreventionsExist() {
            // Given
            when(mockPreventionDao.findUniqueByDemographicId(TEST_DEMO_NO))
                .thenReturn(Collections.emptyList());

            // When
            List<Prevention> results = preventionManager.getImmunizationsByDemographic(
                mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should enforce security check before retrieving immunizations")
        void shouldEnforceSecurityCheck_whenRetrievingImmunizations() {
            // Given
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_prevention"), anyString(), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() ->
                preventionManager.getImmunizationsByDemographic(mockLoggedInInfo, TEST_DEMO_NO)
            ).isInstanceOf(RuntimeException.class)
             .hasMessageContaining("missing required sec object");
        }
    }
}
