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

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.commn.model.ScheduleHoliday;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplate;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplatePrimaryKey;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.util.Calendar;
import java.util.Date;

/**
 * Base class for Schedule-related unit tests providing common mocks and test data builders.
 *
 * @since 2026-02-09
 * @see OpenOUnitTestBase
 */
@Tag("unit")
@Tag("fast")
@Tag("schedule")
public abstract class ScheduleUnitTestBase extends OpenOUnitTestBase {

    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;
    protected Facility mockFacility;

    protected static final Integer TEST_DEMO_NO = 12345;
    protected static final String TEST_PROVIDER = "999990";
    protected static final String TEST_TEMPLATE_NAME = "P";

    @BeforeEach
    void setUpScheduleMocks() {
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        mockFacility = Mockito.mock(Facility.class);

        Mockito.lenient().when(mockLoggedInInfo.getCurrentFacility()).thenReturn(mockFacility);
        Mockito.lenient().when(mockFacility.isIntegratorEnabled()).thenReturn(false);
        Mockito.lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    /**
     * Creates a test ScheduleDate.
     *
     * @return A ScheduleDate instance for testing
     */
    protected ScheduleDate createTestScheduleDate() {
        ScheduleDate scheduleDate = new ScheduleDate();
        scheduleDate.setDate(new Date());
        scheduleDate.setProviderNo(TEST_PROVIDER);
        scheduleDate.setAvailable('1');
        scheduleDate.setHour(TEST_TEMPLATE_NAME);
        scheduleDate.setPriority('a');
        return scheduleDate;
    }

    /**
     * Creates a test ScheduleTemplate.
     *
     * @param providerNo The provider number
     * @param name The template name
     * @param timecode The time code string (one char per time slot)
     * @return A ScheduleTemplate instance for testing
     */
    protected ScheduleTemplate createTestScheduleTemplate(String providerNo, String name, String timecode) {
        ScheduleTemplate template = new ScheduleTemplate();
        template.setId(new ScheduleTemplatePrimaryKey(providerNo, name));
        template.setTimecode(timecode);
        template.setSummary("Test template");
        return template;
    }

    /**
     * Creates a test ScheduleTemplateCode.
     *
     * @param code The single-character code
     * @param description The description
     * @param color The display color
     * @return A ScheduleTemplateCode instance for testing
     */
    protected ScheduleTemplateCode createTestScheduleTemplateCode(Character code, String description, String color) {
        ScheduleTemplateCode templateCode = new ScheduleTemplateCode();
        templateCode.setCode(code);
        templateCode.setDescription(description);
        templateCode.setColor(color);
        return templateCode;
    }

    /**
     * Creates a test ScheduleHoliday.
     *
     * @param holidayDate The holiday date
     * @param holidayName The holiday name
     * @return A ScheduleHoliday instance for testing
     */
    protected ScheduleHoliday createTestScheduleHoliday(Date holidayDate, String holidayName) {
        ScheduleHoliday holiday = new ScheduleHoliday();
        holiday.setId(holidayDate);
        holiday.setHolidayName(holidayName);
        return holiday;
    }

    /**
     * Creates a Date representing a specific time of day.
     */
    protected Date createTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}
