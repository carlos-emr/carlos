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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Teleplan Activity List's MSP and HTML file links named {@code billing/CA/BC/DownloadBilling}, which nothing was
 * mapped to, so every link answered 404 (issue #4439 D5). The action serves a file only to a user with
 * {@code _billing} read, only when a batch in billactivity names it, and only from HOME_DIR.
 *
 * @since 2026-10-22
 */
@DisplayName("BC Teleplan batch file download")
@Tag("unit")
@Tag("billing")
@Tag("security")
class DownloadBilling2ActionUnitTest {

    private static final String MSP_FILE = "HJ261022_101500_001";
    private static final String MSP_TEXT = "VS1FAKE00001\r\nC02FAKE0000100100\r\n";

    @TempDir
    Path homeDir;

    private Object previousHome;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private BillActivityDao billActivityDao;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void setUp() throws IOException {
        previousHome = CarlosProperties.getInstance().put("HOME_DIR", homeDir.toString());
        Files.writeString(homeDir.resolve(MSP_FILE), MSP_TEXT, StandardCharsets.ISO_8859_1);

        request = new MockHttpServletRequest("GET", "/carlos/billing/CA/BC/DownloadBilling");
        response = new MockHttpServletResponse();
        LoggedInInfo info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_billing", "r", null)).thenReturn(true);
        billActivityDao = mock(BillActivityDao.class);
        when(billActivityDao.findCurrentByFilename(MSP_FILE)).thenReturn(List.of(new BillActivity()));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servlet.close();
        if (previousHome == null) {
            CarlosProperties.getInstance().remove("HOME_DIR");
        } else {
            CarlosProperties.getInstance().put("HOME_DIR", previousHome);
        }
    }

    private String download(String filename) throws IOException {
        if (filename != null) {
            request.addParameter("filename", filename);
        }
        return new DownloadBilling2Action(security, billActivityDao).execute();
    }

    @Test
    void shouldStreamTheBatchFile_asAnAttachment() throws IOException {
        assertThat(download(MSP_FILE)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentAsByteArray()).isEqualTo(MSP_TEXT.getBytes(StandardCharsets.ISO_8859_1));
        assertThat(response.getContentType()).isEqualTo("application/octet-stream");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"" + MSP_FILE + "\"");
        assertThat(response.getContentLength()).isEqualTo(MSP_TEXT.length());
    }

    @Test
    void shouldRefuse_withoutBillingRead() {
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), nullable(String.class))).thenReturn(false);

        assertThatThrownBy(() -> download(MSP_FILE)).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_billing)");
        verifyNoInteractions(billActivityDao);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    /** Anything but a single file name is refused before the batch list or the disk is consulted. */
    @ParameterizedTest
    @ValueSource(strings = {"", "../" + MSP_FILE, "sub/" + MSP_FILE, "..", ".hidden", "/etc/passwd", "a\\b"})
    void shouldAnswerBadRequest_forANameThatIsNotASingleFileName(String filename) throws IOException {
        assertThat(download(filename)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(billActivityDao);
    }

    @Test
    void shouldAnswerBadRequest_whenNoFileIsNamed() throws IOException {
        assertThat(download(null)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(billActivityDao);
    }

    /** HOME_DIR holds other billing output too; only a file a Teleplan batch names is served. */
    @Test
    void shouldAnswerNotFound_forAFileInHomeDirThatNoBatchNames() throws IOException {
        Files.writeString(homeDir.resolve("OTHER_FILE"), "FAKE other billing output", StandardCharsets.ISO_8859_1);

        assertThat(download("OTHER_FILE")).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void shouldAnswerNotFound_whenTheBatchFileIsNotOnDisk() throws IOException {
        Files.delete(homeDir.resolve(MSP_FILE));

        assertThat(download(MSP_FILE)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void shouldAnswerNotFound_whenHomeDirIsNotConfigured() throws IOException {
        CarlosProperties.getInstance().remove("HOME_DIR");

        assertThat(download(MSP_FILE)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void shouldLinkEachBatchFile_toTheMappedDownloadWithItsNameEncoded() throws IOException {
        String page = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/TeleplanSubmission.jsp"),
                StandardCharsets.UTF_8);
        assertThat(page)
                .contains("/billing/CA/BC/DownloadBilling?filename=${carlos:forUriComponent(billAct.ohipfilename)}")
                .contains("/billing/CA/BC/DownloadBilling?filename=${carlos:forUriComponent(billAct.htmlfilename)}")
                .doesNotContain("filename=${billAct.");
        assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml"), StandardCharsets.UTF_8))
                .contains("<action name=\"billing/CA/BC/DownloadBilling\" "
                        + "class=\"io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.DownloadBilling2Action\"/>");
    }
}
