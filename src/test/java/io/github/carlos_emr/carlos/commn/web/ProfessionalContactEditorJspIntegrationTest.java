/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Stream;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Renders the professional contact editor, the {@code pForm} result of {@code demographic/Contact},
 * with the request {@link Contact2Action} leaves when it opens a contact and when it refuses a
 * stale or invalid specialist edit. 9c2014ef3867 (#6134) made the page carry
 * {@code specialistEditVersion} back to the server and show the localized
 * {@code contact.specialist.editRefused} alert on a refusal, take {@code contactType} from the
 * request attribute the action sets before the posted parameter, write the address into a
 * textarea so its line breaks survive, and keep a stored province the list does not know (or a
 * compound one) as the selected option instead of silently picking another.
 *
 * <p>{@code Contact2ActionUnitTest} pins the attributes and the 409 status; the packaged
 * consultation-correctness check saves through the page in Ontario but never reads the alert.
 * Before #6336 nothing rendered the refused form.</p>
 */
@Tag("integration")
@Tag("demographic")
class ProfessionalContactEditorJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/demographic/addEditProfessionalContact.jsp";
    private static final String ADDRESS = "1 Fake St\nTestville ON";
    private static final String RENDERED_VERSION = "FAKE-rendered-version-0f3a";

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory).jsp(PAGE).start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr-CA"})
    void shouldShowTheRefusalAndKeepTheEntries_whenAStaleSpecialistEditIsRefused(String language)
            throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));

        Document html = server.request(PAGE)
                .header("Accept-Language", language)
                // The action-resolved specialist type takes precedence over the posted contact type.
                .parameter("contactType", String.valueOf(DemographicContact.TYPE_CONTACT))
                .post()
                .render(ProfessionalContactEditorJspIntegrationTest::refuseStaleSpecialistEdit)
                .assertOk()
                .document();

        Elements alerts = html.select("form#addEditProfessionalForm div.alert-danger[role=alert]");
        assertThat(alerts).hasSize(1);
        assertThat(alerts.first().text()).isEqualTo(bundle.getString("contact.specialist.editRefused"));
        assertThat(field(html, "specialistEditVersion").val()).isEqualTo(RENDERED_VERSION);
        assertThat(html.getElementById("contactType").val())
                .isEqualTo(String.valueOf(DemographicContact.TYPE_PROFESSIONALSPECIALIST));
        assertThat(field(html, "pcontact.id").val()).isEqualTo("37");
        assertThat(field(html, "demographicContactId").val()).isEqualTo("71");
        assertThat(html.getElementById("pcontact.firstName").val()).isEqualTo("FAKE-Edited");
        assertThat(html.getElementById("pcontact.workPhone").val()).isEqualTo("555-0100");
        Element address = html.getElementById("pcontact.address");
        assertThat(address.tagName()).isEqualTo("textarea");
        assertThat(address.wholeText()).isEqualTo(ADDRESS);
        assertThat(selectedProvinces(html)).containsExactly("ZZ");
    }

    @Test
    void shouldPostTheContactTypeParameterAndNoAlert_whenOpeningANewContact() throws Exception {
        Document html = server.request(PAGE)
                .parameter("contactType", String.valueOf(DemographicContact.TYPE_CONTACT))
                .render(request -> {
                    // What Contact2Action sets before showing an empty form.
                    request.setAttribute("region", "ON");
                    request.setAttribute("specialties", List.of());
                })
                .assertOk()
                .document();

        assertThat(html.select("div.alert-danger")).isEmpty();
        assertThat(html.getElementById("contactType").val()).isEqualTo(String.valueOf(DemographicContact.TYPE_CONTACT));
        // A new contact has no version yet, but the form always carries the field back.
        assertThat(html.select("form#addEditProfessionalForm input[name='specialistEditVersion']")).hasSize(1);
        assertThat(field(html, "specialistEditVersion").val()).isEmpty();
        assertThat(html.select("input[name='pcontact.id']")).isEmpty();
        assertThat(html.getElementById("pcontact.address").tagName()).isEqualTo("textarea");
    }

    static Stream<Arguments> storedProvinces() {
        return Stream.of(
                Arguments.of(37, "ZZ", "ZZ", true),
                Arguments.of(37, "ON|PE", "ON|PE", true),
                Arguments.of(37, "ON", "ON", false),
                // An existing contact without a province keeps none rather than taking the clinic's region.
                Arguments.of(37, "", "", true),
                // A new contact starts from the clinic's region.
                Arguments.of(null, null, "ON", false));
    }

    @ParameterizedTest(name = "contact {0}, province \"{1}\"")
    @MethodSource("storedProvinces")
    void shouldSelectExactlyTheStoredProvince_whenTheFormOpens(Integer contactId, String province,
            String selected, boolean keptAsExtraOption) throws Exception {
        ProfessionalContact contact = new ProfessionalContact();
        contact.setId(contactId);
        contact.setLastName("FAKE-Specialist");
        contact.setProvince(province);

        Document html = server.render(PAGE, request -> {
            request.setAttribute("pcontact", contact);
            request.setAttribute("region", "ON");
            request.setAttribute("specialties", List.of());
        }).assertOk().document();

        assertThat(selectedProvinces(html)).containsExactly(selected);
        Element first = html.getElementById("pcontact.province").child(0);
        if (keptAsExtraOption) {
            assertThat(first.attr("value")).isEqualTo(selected);
            assertThat(first.text()).isEqualTo(selected);
        } else {
            assertThat(first.attr("value")).isEqualTo("AB");
        }
    }

    /**
     * The attributes Contact2Action.saveProContact sets before re-showing the form when
     * SpecialistContactEditPersister refuses the posted contact (stale version or bad address).
     */
    private static void refuseStaleSpecialistEdit(HttpServletRequest request) {
        ProfessionalContact posted = new ProfessionalContact();
        posted.setId(37);
        posted.setLastName("FAKE-Specialist");
        posted.setFirstName("FAKE-Edited");
        posted.setAddress(ADDRESS);
        posted.setProvince("ZZ");
        posted.setWorkPhone("555-0100");
        posted.setSpecialty("Cardiology");
        request.setAttribute("pcontact", posted);
        request.setAttribute("specialistEditVersion", RENDERED_VERSION);
        request.setAttribute("specialistContactConflict", Boolean.TRUE);
        request.setAttribute("specialties", List.of());
        request.setAttribute("contactRole", posted.getSpecialty());
        request.setAttribute("demographicContactId", "71");
        request.setAttribute("contactType", DemographicContact.TYPE_PROFESSIONALSPECIALIST);
    }

    /** The values of the province options the page marks selected. */
    private static List<String> selectedProvinces(Document html) {
        return html.getElementById("pcontact.province").select("option[selected]").stream()
                .map(option -> option.attr("value"))
                .toList();
    }

    private static Element field(Document html, String name) {
        return html.selectFirst("form#addEditProfessionalForm input[name='" + name + "']");
    }
}
