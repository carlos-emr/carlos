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
package io.github.carlos_emr.carlos.commn.model;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the {@code birthDay} JavaBean property on {@link Demographic}.
 *
 * <p>{@code getBirthDay} once returned {@link GregorianCalendar} while {@code setBirthDay}
 * accepted the wider {@link Calendar}. A property whose accessors disagree on type is
 * malformed: introspectors resolve the setter against the GETTER's return type, so the
 * setter became invisible and the property read-only. That surfaced in production as
 * EclipseLink MOXy refusing to build the JAXB context at all ("No JAXB context can be
 * created", NoSuchMethodException setBirthDay(GregorianCalendar)) on deployments that
 * exercised it, and silently affected every other introspection-based consumer.
 *
 * @since 2026-08-25
 */
@DisplayName("Demographic birthDay JavaBean property")
@Tag("unit")
@Tag("demographic")
class DemographicBirthDayPropertyUnitTest {

    @Test
    @DisplayName("should expose a writable birthDay property when introspected as a JavaBean")
    void shouldExposeWritableBirthDayProperty_whenIntrospectedAsJavaBean() throws IntrospectionException {
        PropertyDescriptor birthDay = birthDayPropertyDescriptor();

        assertThat(birthDay).as("birthDay property descriptor").isNotNull();
        assertThat(birthDay.getReadMethod()).as("birthDay getter").isNotNull();
        assertThat(birthDay.getWriteMethod())
                .as("birthDay setter — null here means the accessor types have drifted apart "
                        + "again and JAXB context creation will fail")
                .isNotNull();
    }

    @Test
    @DisplayName("should declare the same type on the birthDay getter and setter")
    void shouldDeclareMatchingAccessorTypes_whenBirthDayAccessorsAreReflected() throws NoSuchMethodException {
        Method getter = Demographic.class.getMethod("getBirthDay");
        Method setter = Demographic.class.getMethod("setBirthDay", Calendar.class);

        assertThat(getter.getReturnType())
                .as("getBirthDay must return exactly what setBirthDay accepts")
                .isEqualTo(setter.getParameterTypes()[0]);
    }

    @Test
    @DisplayName("should round trip the stored date when a birth day is set and read back")
    void shouldRoundTripStoredDate_whenBirthDayIsSetAndReadBack() {
        Demographic demographic = new Demographic();
        Calendar birthDate = new GregorianCalendar(1980, Calendar.MARCH, 15);

        demographic.setBirthDay(birthDate);
        Calendar readBack = demographic.getBirthDay();

        assertThat(readBack).as("birthDay read back after set").isNotNull();
        assertThat(readBack.get(Calendar.YEAR)).isEqualTo(1980);
        assertThat(readBack.get(Calendar.MONTH)).isEqualTo(Calendar.MARCH);
        assertThat(readBack.get(Calendar.DAY_OF_MONTH)).isEqualTo(15);
    }

    private static PropertyDescriptor birthDayPropertyDescriptor() throws IntrospectionException {
        BeanInfo beanInfo = Introspector.getBeanInfo(Demographic.class);
        for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
            if ("birthDay".equals(descriptor.getName())) {
                return descriptor;
            }
        }
        return null;
    }
}
