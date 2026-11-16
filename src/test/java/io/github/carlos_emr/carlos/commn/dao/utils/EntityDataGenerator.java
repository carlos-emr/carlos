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
package io.github.carlos_emr.carlos.commn.dao.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

import javassist.Modifier;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.MiscUtils;

public class EntityDataGenerator {

    // Thread-safe counter to ensure uniqueness for composite unique constraints
    private static final AtomicInteger uniqueCounter = new AtomicInteger(0);

    public static Object generateTestDataForModelClass(Object model) throws Exception {
        return generateTestDataForModelClass(model, Math::random);
    }

    /**
     * Fills the model's fields with test data, taking every random draw from {@code random}.
     *
     * <p>Tests pass a fixed supplier to check the worst-case draws (issue #6168).</p>
     *
     * @param model the entity to fill
     * @param random source of draws in [0, 1), like {@link Math#random()}
     * @return the same model
     * @throws Exception if a field cannot be set
     */
    static Object generateTestDataForModelClass(Object model, DoubleSupplier random) throws Exception {

        // Some entities use property-access JPA annotations (on getters, not fields).
        // Pre-scan getter methods to detect @Id/@GeneratedValue/@EmbeddedId on them.
        Set<String> methodIdFields = new HashSet<>();
        Set<String> methodGeneratedValueFields = new HashSet<>();
        Set<String> methodEmbeddedIdFields = new HashSet<>();
        for (Method m : model.getClass().getMethods()) {
            String mName = m.getName();
            if (mName.startsWith("get") && mName.length() > 3 && m.getParameterCount() == 0) {
                String fieldName = Character.toLowerCase(mName.charAt(3)) + mName.substring(4);
                if (m.isAnnotationPresent(Id.class)) {
                    methodIdFields.add(fieldName);
                }
                if (m.isAnnotationPresent(GeneratedValue.class)) {
                    methodGeneratedValueFields.add(fieldName);
                }
                if (m.isAnnotationPresent(EmbeddedId.class)) {
                    methodEmbeddedIdFields.add(fieldName);
                }
            }
        }

        Field f[] = model.getClass().getDeclaredFields();
        AccessibleObject.setAccessible(f, true);
        for (int i = 0; i < f.length; i++) {
            boolean isId = false;
            boolean hasGeneratedValue = false;
            String fieldName = f[i].getName();
            Annotation annotations[] = f[i].getAnnotations();
            for (int j = 0; j < annotations.length; j++) {
                if (annotations[j].annotationType() == Id.class) {
                    isId = true;
                }
                if (annotations[j].annotationType() == EmbeddedId.class) {
                    isId = true;
                }
                if (annotations[j].annotationType() == GeneratedValue.class) {
                    hasGeneratedValue = true;
                }
            }
            // Also check getter-level annotations (property-access JPA entities)
            if (methodIdFields.contains(fieldName)) {
                isId = true;
            }
            if (methodGeneratedValueFields.contains(fieldName)) {
                hasGeneratedValue = true;
            }
            // Skip @Id fields when @GeneratedValue is present (auto-assigned by DB).
            // For manually-assigned IDs (no @GeneratedValue), generate a value.
            // Always skip @EmbeddedId fields (complex composite keys).
            if (isId && hasGeneratedValue)
                continue;
            boolean isEmbeddedId = false;
            for (Annotation a : annotations) {
                if (a.annotationType() == EmbeddedId.class) {
                    isEmbeddedId = true;
                    break;
                }
            }
            if (isEmbeddedId || methodEmbeddedIdFields.contains(fieldName))
                continue;

            int modifiers = f[i].getModifiers();
            if ((modifiers & Modifier.STATIC) == Modifier.STATIC) {
                continue;
            }

            if (f[i].getType() == String.class) {
                String value;

                // Determine max column length from @Column annotation (default 255)
                // Check both field and getter for property-access entities
                int maxLength = 255;
                Column colAnnotation = f[i].getAnnotation(Column.class);
                if (colAnnotation == null) {
                    try {
                        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                        Method getter = model.getClass().getMethod(getterName);
                        colAnnotation = getter.getAnnotation(Column.class);
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (colAnnotation != null && colAnnotation.length() > 0) {
                    maxLength = colAnnotation.length();
                }

                // Handle special fields that have known constraints
                if ("sex".equalsIgnoreCase(fieldName)) {
                    // Valid values: M, F, X (Intersex), U (Undisclosed)
                    String[] validSexValues = {"M", "F", "X", "U"};
                    value = validSexValues[(int) (random.getAsDouble() * validSexValues.length)];
                } else if ("ver".equalsIgnoreCase(fieldName)) {
                    value = "ON"; // Ontario version code
                } else if ("hc_type".equalsIgnoreCase(fieldName) || "hcType".equalsIgnoreCase(fieldName)) {
                    value = "ON"; // Ontario health card type
                } else if ("dateOfBirth".equalsIgnoreCase(fieldName) || "date_of_birth".equalsIgnoreCase(fieldName)) {
                    value = String.format("%02d", (int) (random.getAsDouble() * 28) + 1); // "01"-"28"
                } else if ("monthOfBirth".equalsIgnoreCase(fieldName) || "month_of_birth".equalsIgnoreCase(fieldName)) {
                    value = String.format("%02d", (int) (random.getAsDouble() * 12) + 1); // "01"-"12"
                } else if ("yearOfBirth".equalsIgnoreCase(fieldName) || "year_of_birth".equalsIgnoreCase(fieldName)) {
                    value = String.valueOf(1950 + (int) (random.getAsDouble() * 70)); // "1950"-"2019"
                } else if ("rosterTerminationReason".equalsIgnoreCase(fieldName) || "roster_termination_reason".equalsIgnoreCase(fieldName)) {
                    value = "NR"; // Not Rostered
                } else if ("postal".equalsIgnoreCase(fieldName) || "residentialPostal".equalsIgnoreCase(fieldName)) {
                    value = "M5V2T6"; // Valid Canadian postal code, fits VARCHAR(9)
                } else if ("chartNo".equalsIgnoreCase(fieldName) || "chart_no".equalsIgnoreCase(fieldName)) {
                    int counter = uniqueCounter.incrementAndGet();
                    value = "CH" + (counter % 100000); // Fits VARCHAR(10)
                } else if ("sin".equalsIgnoreCase(fieldName)) {
                    int counter = uniqueCounter.incrementAndGet();
                    value = String.valueOf(100000000 + (counter % 900000000)); // 9-digit SIN, fits VARCHAR(15)
                } else if ("province".equalsIgnoreCase(fieldName) || "residentialProvince".equalsIgnoreCase(fieldName)) {
                    value = "ON";
                } else if ("roster_status".equalsIgnoreCase(fieldName) || "rosterStatus".equalsIgnoreCase(fieldName)) {
                    value = "RO";
                } else if ("patient_status".equalsIgnoreCase(fieldName) || "patientStatus".equalsIgnoreCase(fieldName)) {
                    value = "AC";
                } else {
                    // Generate a unique value, respecting column length constraints.
                    // Note: maxLength defaults to 255 for HBM-mapped entities where
                    // @Column is not present, but HBM may define shorter lengths.
                    // Use a compact format (prefix + counter) that fits most HBM
                    // column lengths while still being unique.
                    int counter = uniqueCounter.incrementAndGet();
                    if (maxLength <= 5) {
                        // Very short columns: use counter-based value
                        value = String.valueOf(counter % 100000);
                        if (value.length() > maxLength) {
                            value = value.substring(0, maxLength);
                        }
                    } else {
                        // Use compact format: 3-char prefix + counter (typically 8-12 chars)
                        // This fits most HBM column lengths (>= 10) while staying unique
                        value = fieldName.substring(0, Math.min(3, fieldName.length())) + counter;
                        if (value.length() > maxLength) {
                            value = value.substring(0, maxLength);
                        }
                    }
                }
                f[i].set(model, value);
            } else if (f[i].getType() == int.class || f[i].getType() == Integer.class) {
                double draw = random.getAsDouble();
                f[i].set(model, isId ? manualIdFrom(draw) : (int) (draw * 10000));
            } else if (f[i].getType() == long.class || f[i].getType() == Long.class) {
                double draw = random.getAsDouble();
                f[i].set(model, isId ? (long) manualIdFrom(draw) : (long) (draw * 10000));
            } else if (f[i].getType() == float.class || f[i].getType() == Float.class) {
                f[i].set(model, (float) (random.getAsDouble() * 100));
            } else if (f[i].getType() == double.class || f[i].getType() == Double.class) {
                f[i].set(model, random.getAsDouble() * 100);
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
                BigDecimal bd = new BigDecimal(random.getAsDouble() * 5000);
                f[i].set(model, bd);
            } else {
                MiscUtils.getLogger().warn("Can't generate test data for class type:" + f[i].getType());
            }

        }

        return model;
    }

    /**
     * Turns a draw in [0, 1) into a hand-set id from 1 to 9999.
     *
     * <p>An {@code @Id} without {@code @GeneratedValue} keeps the value the generator sets, and
     * DAO tests check that the saved id is positive. The plain number draw, 0 to 9999, gave id 0
     * about once in 10,000 runs (issue #6168).</p>
     *
     * @param draw a value in [0, 1)
     * @return an id from 1 to 9999
     */
    static int manualIdFrom(double draw) {
        return 1 + (int) (draw * 9999);
    }
}
