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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimsErrorReportRecordDto;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards JSP Expression Language bindings that still use snake_case names.
 *
 * <p>Most Ontario DTOs were moved to camelCase records. JSP EL failures are
 * runtime-only, so a stale expression such as {@code itemData.ch1_id} can pass
 * compilation and then produce a 500 when the page renders. The remaining
 * snake_case expressions below are intentionally limited to legacy beans that
 * still expose matching getters, or {@code Properties}-backed maps whose keys
 * are populated with ministry/legacy database names.</p>
 */
@DisplayName("Billing ON JSP snake_case bindings")
@Tag("unit")
@Tag("billing")
class BillingOnJspSnakeCaseBindingUnitTest {

    private static final Path BILLING_ON_JSP_ROOT = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/ON");
    private static final Pattern EL_EXPRESSION = Pattern.compile("\\$\\{([^}]*)}");
    private static final Pattern SNAKE_CASE_PROPERTY = Pattern.compile(
            "\\b([A-Za-z_$][\\w$]*)\\.([A-Za-z][A-Za-z0-9]*_[A-Za-z0-9_]+)\\b");

    private static final Map<String, Set<String>> MAP_BACKED_PROPERTIES = Map.of(
            "__hist", Set.of("billing_no", "billing_date", "update_date"),
            "__histD", Set.of("service_code", "diagnostic_code"),
            "__loc", Set.of("clinic_location_no", "clinic_location_name"));

    private static final Map<String, Class<?>> BEAN_BACKED_PROPERTIES = Map.of(
            "billPayment", BillingONPayment.class,
            "claimsError", BillingClaimsErrorReportRecordDto.class);

    @Test
    void shouldKeepSnakeCaseElBindings_limitedToKnownLegacyOrMapBackedProperties() throws IOException {
        Set<String> observed = collectSnakeCaseElBindings();

        assertThat(observed)
                .containsExactlyInAnyOrderElementsOf(allowedBindings());
    }

    @Test
    void shouldExposeBeanGetters_forAllowedLegacySnakeCaseBindings() throws IntrospectionException {
        for (Map.Entry<String, Class<?>> beanEntry : BEAN_BACKED_PROPERTIES.entrySet()) {
            Set<String> beanProperties = readablePropertyNames(beanEntry.getValue());
            for (String binding : allowedBindingsFor(beanEntry.getKey())) {
                String property = binding.substring(binding.indexOf('.') + 1);
                assertThat(beanProperties)
                        .as("%s should expose readable bean property %s",
                                beanEntry.getValue().getSimpleName(), property)
                        .contains(property);
            }
        }
    }

    private static Set<String> collectSnakeCaseElBindings() throws IOException {
        Set<String> bindings = new TreeSet<>();
        try (Stream<Path> jspFiles = Files.walk(BILLING_ON_JSP_ROOT)) {
            for (Path jspFile : jspFiles.filter(path -> path.toString().endsWith(".jsp")).toList()) {
                for (String line : Files.readAllLines(jspFile)) {
                    Matcher expressionMatcher = EL_EXPRESSION.matcher(line);
                    while (expressionMatcher.find()) {
                        Matcher propertyMatcher = SNAKE_CASE_PROPERTY.matcher(expressionMatcher.group(1));
                        while (propertyMatcher.find()) {
                            bindings.add(propertyMatcher.group(1) + "." + propertyMatcher.group(2));
                        }
                    }
                }
            }
        }
        return bindings;
    }

    private static Set<String> allowedBindings() {
        Set<String> allowed = new TreeSet<>();
        MAP_BACKED_PROPERTIES.forEach((root, properties) ->
                properties.forEach(property -> allowed.add(root + "." + property)));
        BEAN_BACKED_PROPERTIES.keySet().forEach(root -> allowed.addAll(allowedBindingsFor(root)));
        return allowed;
    }

    private static Set<String> allowedBindingsFor(String root) {
        return switch (root) {
            case "billPayment" -> Set.of(
                    "billPayment.total_credit",
                    "billPayment.total_discount",
                    "billPayment.total_payment",
                    "billPayment.total_refund");
            case "claimsError" -> Set.of(
                    "claimsError.patient_first",
                    "claimsError.patient_last",
                    "claimsError.patient_sex",
                    "claimsError.province_code");
            default -> Set.of();
        };
    }

    private static Set<String> readablePropertyNames(Class<?> beanClass) throws IntrospectionException {
        Set<String> names = new HashSet<>();
        for (PropertyDescriptor descriptor : Introspector.getBeanInfo(beanClass).getPropertyDescriptors()) {
            if (descriptor.getReadMethod() != null) {
                names.add(descriptor.getName());
            }
        }
        return names;
    }
}
