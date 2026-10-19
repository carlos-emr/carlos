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
package io.github.carlos_emr.carlos.commn.web;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import io.github.carlos_emr.carlos.commn.model.DemographicExtArchive;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The Master Record's address and phone history ({@code Demographic2Action.getAddressAndPhoneHistoryAsJson}): built
 * from the patient's archived versions, it lists a value once for each time it changed, and a phone without an
 * extension shows no "x" (#5013).
 *
 * @since 2026-10-18
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("Master Record address and phone history")
class Demographic2ActionAddressPhoneHistoryUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 1001;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletResponse response;
    private DemographicArchiveDao archiveDao;
    private DemographicExtArchiveDao extArchiveDao;
    private Demographic2Action action;

    private final List<DemographicArchive> archives = new ArrayList<>();
    private List<Map<String, String>> items;

    @BeforeEach
    void setUp() {
        createAndRegisterMock(DemographicDao.class);
        archiveDao = createAndRegisterMock(DemographicArchiveDao.class);
        extArchiveDao = createAndRegisterMock(DemographicExtArchiveDao.class);
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull(String.class)))
                .thenReturn(true);
        when(archiveDao.findByDemographicNoChronologically(DEMOGRAPHIC_NO)).thenReturn(archives);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, mock(LoggedInInfo.class));
        request.setSession(session);
        request.setParameter("method", "getAddressAndPhoneHistoryAsJson");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        action = new Demographic2Action();
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
    }

    /** Adds an archived version of the FAKE patient with the given address and phones, and its extension rows. */
    private void version(String address, String phone, String phone2, String hPhoneExt, String wPhoneExt) {
        DemographicArchive archive = new DemographicArchive();
        long id = archives.size() + 1L;
        archive.setId(id);
        archive.setAddress(address);
        archive.setCity("Faketown");
        archive.setProvince("ON");
        archive.setPostal("K1A0A1");
        archive.setPhone(phone);
        archive.setPhone2(phone2);
        archive.setLastUpdateDate(new Date());
        archives.add(archive);
        List<DemographicExtArchive> exts = new ArrayList<>();
        exts.add(ext(id, "hPhoneExt", hPhoneExt));
        exts.add(ext(id, "wPhoneExt", wPhoneExt));
        when(extArchiveDao.getDemographicExtArchiveByArchiveId(id)).thenReturn(exts);
    }

    private static DemographicExtArchive ext(long archiveId, String key, String value) {
        DemographicExtArchive ext = new DemographicExtArchive();
        ext.setArchiveId(archiveId);
        ext.setKey(key);
        ext.setValue(value);
        return ext;
    }

    /** The listed values of the given type ("address", "phone" or "phone2"), in order; the history is built once. */
    private List<String> history(String type) throws Exception {
        if (items == null) {
            action.getAddressAndPhoneHistoryAsJson();
            items = new ObjectMapper().readValue(response.getContentAsString(), new TypeReference<List<Map<String, String>>>() { });
        }
        return items.stream().filter(item -> type.equals(item.get("type"))).map(item -> item.get("name")).toList();
    }

    /** Two saves that changed the address, then two that changed only the home phone. */
    private void twoAddressChangesThenTwoPhoneChanges() {
        version("20 FAKE Second Ave", "613-555-0100", "", "", "");
        version("30 FAKE Third St", "613-555-0100", "", "", "");
        version("30 FAKE Third St", "613-555-0177", "", "", "");
        version("30 FAKE Third St", "613-555-0188", "", "", "");
    }

    @Test
    void shouldListEachAddressOnce_whenLaterSavesChangedOnlyThePhone() throws Exception {
        twoAddressChangesThenTwoPhoneChanges();

        assertThat(history("address")).containsExactly(
                "20 FAKE Second Ave, Faketown,ON,K1A0A1", "30 FAKE Third St, Faketown,ON,K1A0A1");
    }

    @Test
    void shouldListEachHomePhoneOnceWithoutAnX_whenEarlierSavesChangedOnlyTheAddress() throws Exception {
        twoAddressChangesThenTwoPhoneChanges();

        assertThat(history("phone")).containsExactly("613-555-0100", "613-555-0177", "613-555-0188");
    }

    @Test
    void shouldShowTheExtension_whenAPhoneHasOne() throws Exception {
        version("20 FAKE Second Ave", "613-555-0100", "613-555-0300", "12", "345");

        assertThat(history("phone")).containsExactly("613-555-0100x12");
        assertThat(history("phone2")).containsExactly("613-555-0300x345");
    }

    @Test
    void shouldListThePhoneAgain_whenOnlyItsExtensionChanged() throws Exception {
        version("20 FAKE Second Ave", "613-555-0100", "", "", "");
        version("20 FAKE Second Ave", "613-555-0100", "", "12", "");

        assertThat(history("phone")).containsExactly("613-555-0100", "613-555-0100x12");
    }

    @Test
    void shouldListThePhones_whenAnExtensionRowHasNoValue() throws Exception {
        version("20 FAKE Second Ave", "613-555-0100", "613-555-0300", null, null);
        version("20 FAKE Second Ave", "613-555-0177", "613-555-0300", null, null);

        assertThat(history("phone")).containsExactly("613-555-0100", "613-555-0177");
        assertThat(history("phone2")).containsExactly("613-555-0300");
    }

    @Test
    void shouldListAPhoneOnce_whenAnEarlierVersionHadNoAddressOrPhone() throws Exception {
        version(null, null, null, "", "");
        version(null, null, null, "", "");
        version("20 FAKE Second Ave", "613-555-0100", "", "", "");
        version("20 FAKE Second Ave", "613-555-0100", "", "", "");

        assertThat(history("phone")).containsOnlyOnce("613-555-0100");
        assertThat(history("address")).containsOnlyOnce("20 FAKE Second Ave, Faketown,ON,K1A0A1");
    }
}
