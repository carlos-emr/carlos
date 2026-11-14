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
package io.github.carlos_emr.carlos.webserv.rest.conversion.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.prevention.Prevention;
import io.github.carlos_emr.carlos.prevention.PreventionDS;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.PreventionDateService;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import io.github.carlos_emr.carlos.prevention.dto.PreventionSourceUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryItemTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryTo1;

/**
 * The chart's Preventions summary when a prevention read fails: the summary keeps its normal
 * shape and holds one explicit item saying so, instead of an HTTP 500, an empty summary, or the
 * items gathered before the failure.
 */
@Tag("unit")
@DisplayName("Preventions summary when preventions cannot be read")
class PreventionsSummaryUnreadableUnitTest {

    private static final Integer PATIENT = 7;

    @Test
    @DisplayName("should return one explicit unreadable item in place of the partial list")
    void shouldReturnOneUnreadableItem_whenAPreventionReadFails() {
        LoggedInInfo user = new LoggedInInfo();
        user.setLocale(Locale.ENGLISH);

        SummaryTo1 summary = summarize(user, true);

        assertThat(summary.getDisplayName()).isEqualTo("Preventions");
        assertThat(summary.getSummaryCode()).isEqualTo(SummaryTo1.PREVENTIONS);
        // Flu was read first and flagged by decision support; it is not shown on its own.
        assertThat(summary.getSummaryItem()).hasSize(1);
        SummaryItemTo1 item = summary.getSummaryItem().get(0);
        ResourceBundle english = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);
        assertThat(item.getDisplayName()).isEqualTo(english.getString("prevention.unreadable.label"))
                .isEqualTo("Preventions could not be read");
        assertThat(item.getWarning()).isEqualTo(english.getString("prevention.unreadable.message"))
                .contains("could not be read", "Reload", "contact support");
        assertThat(item.getIndicatorClass()).isEqualTo("highlight");
        assertThat(item.getType()).isEqualTo("prevention");
        assertThat(item.getAction()).isEqualTo("../prevention/ViewPreventionIndex?demographic_no=7");
        assertThat(item.getDate()).isNull();
    }

    @Test
    @DisplayName("should word the unreadable item in the user's language")
    void shouldLocalizeTheUnreadableItem_forTheUsersLocale() {
        LoggedInInfo user = new LoggedInInfo();
        user.setLocale(Locale.FRENCH);

        SummaryItemTo1 item = summarize(user, true).getSummaryItem().get(0);

        ResourceBundle french = ResourceBundle.getBundle("oscarResources", Locale.FRENCH);
        assertThat(item.getDisplayName()).isEqualTo(french.getString("prevention.unreadable.label"))
                .startsWith("Les pr");
        assertThat(item.getWarning()).isEqualTo(french.getString("prevention.unreadable.message"));
    }

    @Test
    @DisplayName("should list the configured preventions as before when every read succeeds")
    void shouldListConfiguredPreventions_whenEveryReadSucceeds() {
        LoggedInInfo user = new LoggedInInfo();

        SummaryTo1 summary = summarize(user, false);

        assertThat(summary.getSummaryItem()).extracting(SummaryItemTo1::getDisplayName)
                .containsExactly("Flu", "PAP");
        assertThat(summary.getSummaryItem().get(0).getWarning()).isEqualTo("Flu is overdue");
    }

    /** Runs the real summary over two configured types; the second read fails when asked to. */
    private static SummaryTo1 summarize(LoggedInInfo user, boolean papUnreadable) {
        // SpringUtils is mocked first: PreventionData and PreventionDisplayConfig resolve beans in
        // their static initializers, and a failed class initialization poisons the whole fork.
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
             MockedStatic<PreventionData> data = mockStatic(PreventionData.class);
             MockedStatic<PreventionDisplayConfig> display = mockStatic(PreventionDisplayConfig.class)) {
            PreventionManager manager = mock(PreventionManager.class);
            when(manager.getPreventionsByDemographicNo(user, PATIENT)).thenReturn(List.of());
            Prevention decisionSupport = mock(Prevention.class);
            Hashtable<String, String> warnings = new Hashtable<>();
            warnings.put("Flu", "Flu is overdue");
            when(decisionSupport.getWarningMsgs()).thenReturn(warnings);
            PreventionDisplayConfig config = mock(PreventionDisplayConfig.class);
            when(config.getPreventions()).thenReturn(new ArrayList<>(List.of(configured("Flu"), configured("PAP"))));
            when(config.display(eq(user), any(), eq("7"), anyInt())).thenReturn(true);

            spring.when(() -> SpringUtils.getBean(PreventionDS.class)).thenReturn(mock(PreventionDS.class));
            display.when(PreventionDisplayConfig::getInstance).thenReturn(config);
            data.when(() -> PreventionData.getPrevention(user, PATIENT)).thenReturn(decisionSupport);
            data.when(() -> PreventionData.getPreventionData(user, "Flu", PATIENT))
                    .thenReturn(new ArrayList<Map<String, Object>>());
            if (papUnreadable) {
                data.when(() -> PreventionData.getPreventionData(user, "PAP", PATIENT))
                        .thenThrow(new PreventionSourceUnavailableException(
                                new java.io.IOException("synthetic source failure")));
            } else {
                data.when(() -> PreventionData.getPreventionData(user, "PAP", PATIENT))
                        .thenReturn(new ArrayList<Map<String, Object>>());
            }

            return new PreventionsSummary(manager, new PreventionDateService(mock(PartialDateDao.class)))
                    .getSummary(user, PATIENT, "preventions");
        }
    }

    private static HashMap<String, String> configured(String name) {
        HashMap<String, String> prevention = new HashMap<>();
        prevention.put("name", name);
        return prevention;
    }
}
