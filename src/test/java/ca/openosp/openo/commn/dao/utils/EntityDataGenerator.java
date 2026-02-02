/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package ca.openosp.openo.commn.dao.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javassist.Modifier;

import javax.persistence.EmbeddedId;
import javax.persistence.Id;

import ca.openosp.openo.commn.model.DemographicExt;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.MiscUtils;

public class EntityDataGenerator {

    // Thread-safe counter to ensure uniqueness for composite unique constraints
    private static final AtomicInteger uniqueCounter = new AtomicInteger(0);

    public static Object generateTestDataForModelClass(Object model) throws Exception {

        Field f[] = model.getClass().getDeclaredFields();
        AccessibleObject.setAccessible(f, true);
        for (int i = 0; i < f.length; i++) {
            boolean isId = false;
            Annotation annotations[] = f[i].getAnnotations();
            for (int j = 0; j < annotations.length; j++) {
                if (annotations[j].annotationType() == Id.class) {
                    isId = true;
                }
                if (annotations[j].annotationType() == EmbeddedId.class) {
                    isId = true;
                }
            }
            if (isId)
                continue;

            int modifiers = f[i].getModifiers();
            if ((modifiers & Modifier.STATIC) == Modifier.STATIC) {
                continue;
            }

            if (f[i].getType() == String.class) {
                String fieldName = f[i].getName();
                String value;

                // Handle special fields that have known constraints
                if ("sex".equalsIgnoreCase(fieldName)) {
                    // Valid values: M, F, X (Intersex), U (Undisclosed)
                    String[] validSexValues = {"M", "F", "X", "U"};
                    value = validSexValues[(int) (Math.random() * validSexValues.length)];
                } else if ("ver".equalsIgnoreCase(fieldName)) {
                    value = "ON"; // Ontario version code
                } else if ("hc_type".equalsIgnoreCase(fieldName) || "hcType".equalsIgnoreCase(fieldName)) {
                    value = "ON"; // Ontario health card type
                } else if ("province".equalsIgnoreCase(fieldName)) {
                    value = "ON";
                } else if ("roster_status".equalsIgnoreCase(fieldName) || "rosterStatus".equalsIgnoreCase(fieldName)) {
                    value = "RO";
                } else if ("patient_status".equalsIgnoreCase(fieldName) || "patientStatus".equalsIgnoreCase(fieldName)) {
                    value = "AC";
                } else {
                    // For other string fields, generate a unique value using timestamp + counter to avoid constraint violations
                    // Counter ensures uniqueness even for composite keys created in the same millisecond
                    value = fieldName.substring(0, Math.min(3, fieldName.length())) + System.currentTimeMillis() + "_" + uniqueCounter.incrementAndGet();
                }
                f[i].set(model, value);
            } else if (f[i].getType() == int.class || f[i].getType() == Integer.class) {
                f[i].set(model, (int) (Math.random() * 10000));
            } else if (f[i].getType() == long.class || f[i].getType() == Long.class) {
                f[i].set(model, (long) (Math.random() * 10000));
            } else if (f[i].getType() == float.class || f[i].getType() == Float.class) {
                f[i].set(model, (float) (Math.random() * 100));
            } else if (f[i].getType() == double.class || f[i].getType() == Double.class) {
                f[i].set(model, Math.random() * 100);
            } else if (f[i].getType() == Date.class) {
                f[i].set(model, new Date());
            } else if (f[i].getType() == Timestamp.class) {
                f[i].set(model, Timestamp.valueOf("2010-10-23 12:05:16"));
            } else if (f[i].getType() == Calendar.class) {
                f[i].set(model, Calendar.getInstance());
            } else if (f[i].getType() == boolean.class || f[i].getType() == Boolean.class) {
                f[i].set(model, true);
            } else if (f[i].getType() == byte.class || f[i].getType() == Byte.class) {
                f[i].set(model, (byte) 0xAA);
            } else if (f[i].getType() == char.class || f[i].getType() == Character.class) {
                f[i].set(model, 'A');
            } else if (f[i].getType() == Set.class || f[i].getType() == List.class || f[i].getType() == Map.class) {
                //ignore
            } else if (f[i].getType() == Provider.class || f[i].getType() == DemographicExt[].class) {
                //ignore
            } else if (f[i].getType() == char.class || f[i].getType() == BigDecimal.class) {
                BigDecimal bd = new BigDecimal(Math.random() * 5000);
                f[i].set(model, bd);
            } else {
                MiscUtils.getLogger().warn("Can't generate test data for class type:" + f[i].getType());
            }

        }

        return model;
    }
}
