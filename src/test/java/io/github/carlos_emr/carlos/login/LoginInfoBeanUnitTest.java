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
package io.github.carlos_emr.carlos.login;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("login")
@DisplayName("LoginInfoBean")
class LoginInfoBeanUnitTest {

    @Test
    @DisplayName("should reset start time, attempt count, and status when initialized")
    void shouldResetStartTimeAndAttemptCountAndStatus_whenInitialized() {
        LoginInfoBean bean = new LoginInfoBean();
        GregorianCalendar startTime = new GregorianCalendar(2026, GregorianCalendar.MAY, 31);

        bean.setTimes(4);
        bean.setStatus(0);

        bean.initialLoginInfoBean(startTime);

        assertThat(bean.getStarttime()).isSameAs(startTime);
        assertThat(bean.getTimes()).isZero();
        assertThat(bean.getStatus()).isOne();
    }
}
