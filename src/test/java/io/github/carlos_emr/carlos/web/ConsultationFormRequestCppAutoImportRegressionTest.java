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
package io.github.carlos_emr.carlos.web;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source regression coverage for CPP auto-import in consultation requests.
 *
 * @since 2026-05-30
 */
@DisplayName("Consultation request CPP auto-import")
@Tag("unit")
@Tag("web")
@Tag("regression")
class ConsultationFormRequestCppAutoImportRegressionTest {
    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path RELEASE_CONFIG = resolveProjectPath(Path.of("archive/release/config"));
    private static final Path JSP_ROOT = resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp"));
    private static final Path RESOURCES_ROOT = resolveProjectPath(Path.of("src/main/resources"));

    @Test
    @DisplayName("should gate every CPP auto-import section with independent properties")
    void shouldGateEveryCppAutoImportSection_withIndependentProperties() throws Exception {
        String jsp = readJsp("encounter/oscarConsultationRequest/ConsultationFormRequest.jsp");

        assertThat(jsp)
                .contains("CONSULTATION_AUTO_INCLUDE_PAST_MEDICAL_HISTORY")
                .contains("CONSULTATION_AUTO_INCLUDE_SOCIAL_HISTORY")
                .contains("CONSULTATION_AUTO_INCLUDE_FAMILY_HISTORY")
                .contains("CONSULTATION_AUTO_INCLUDE_ONGOING_CONCERNS")
                .contains("CONSULTATION_AUTO_INCLUDE_REMINDERS")
                .contains("props.getProperty(\"CONSULTATION_AUTO_INCLUDE_PAST_MEDICAL_HISTORY\", \"false\")")
                .contains("props.getProperty(\"CONSULTATION_AUTO_INCLUDE_SOCIAL_HISTORY\", \"false\")")
                .contains("props.getProperty(\"CONSULTATION_AUTO_INCLUDE_FAMILY_HISTORY\", \"false\")")
                .contains("props.getProperty(\"CONSULTATION_AUTO_INCLUDE_ONGOING_CONCERNS\", \"false\")")
                .contains("props.getProperty(\"CONSULTATION_AUTO_INCLUDE_REMINDERS\", \"false\")")
                .contains("issueType: \"MedHistory\", label: \"Past Medical History\"")
                .contains("issueType: \"SocHistory\", label: \"Social History\"")
                .contains("issueType: \"FamHistory\", label: \"Family History\"")
                .contains("issueType: \"Concerns\", label: \"Ongoing Concerns\"")
                .contains("issueType: \"Reminders\", label: \"Reminders\"");
    }

    @Test
    @DisplayName("should define default CPP auto-import properties in carlos.properties")
    void shouldDefineDefaultCppAutoImportProperties_inCarlosProperties() throws Exception {
        String properties = Files.readString(RESOURCES_ROOT.resolve("carlos.properties"));

        assertThat(properties)
                .contains("CONSULTATION_AUTO_INCLUDE_PAST_MEDICAL_HISTORY=false")
                .contains("CONSULTATION_AUTO_INCLUDE_SOCIAL_HISTORY=false")
                .contains("CONSULTATION_AUTO_INCLUDE_FAMILY_HISTORY=false")
                .contains("CONSULTATION_AUTO_INCLUDE_ONGOING_CONCERNS=false")
                .contains("CONSULTATION_AUTO_INCLUDE_REMINDERS=false");
    }

    @Test
    @DisplayName("should define default CPP auto-import properties in release config")
    void shouldDefineDefaultCppAutoImportProperties_inReleaseConfig() throws Exception {
        String releaseConfig = Files.readString(RELEASE_CONFIG);

        assertThat(releaseConfig)
                .contains("echo \"CONSULTATION_AUTO_INCLUDE_PAST_MEDICAL_HISTORY=false\"")
                .contains("echo \"CONSULTATION_AUTO_INCLUDE_SOCIAL_HISTORY=false\"")
                .contains("echo \"CONSULTATION_AUTO_INCLUDE_FAMILY_HISTORY=false\"")
                .contains("echo \"CONSULTATION_AUTO_INCLUDE_ONGOING_CONCERNS=false\"")
                .contains("echo \"CONSULTATION_AUTO_INCLUDE_REMINDERS=false\"");
    }

    @Test
    @DisplayName("should auto-import CPP sections only on initial new request render")
    void shouldAutoImportCppSections_onlyOnInitialNewRequestRender() throws Exception {
        String jsp = readJsp("encounter/oscarConsultationRequest/ConsultationFormRequest.jsp");

        assertThat(jsp)
                .contains("requestId == null && demo != null && request.getAttribute(\"validateError\") == null")
                .contains("autoImportClinicalHistory(<carlos:encode value='<%= demo %>' context=\"javaScript\"/>);");
    }

    @Test
    @DisplayName("should format auto-imported CPP sections with editable labels")
    void shouldFormatAutoImportedCppSections_withEditableLabels() throws Exception {
        String jsp = readJsp("encounter/oscarConsultationRequest/ConsultationFormRequest.jsp");

        assertThat(jsp)
                .contains("function appendAutoImportedClinicalSection(target, label, note)")
                .contains("var section = \"**\" + label + \":** \" + note.trim();")
                .contains("appendAutoImportedClinicalSection(target, section.label, data.note);");
    }

    private static String readJsp(String relativePath) throws Exception {
        return Files.readString(JSP_ROOT.resolve(relativePath));
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int traversalDepth = 0; current != null && traversalDepth < 6; traversalDepth++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")));
    }
}
