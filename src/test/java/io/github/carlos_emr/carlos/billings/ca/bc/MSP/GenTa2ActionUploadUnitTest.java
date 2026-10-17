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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.TeleplanC12Dao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.TeleplanS00Dao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.TeleplanS21Dao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.TeleplanS22Dao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.TeleplanS23Dao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.TeleplanS25Dao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS21;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Issue #4806: BC Upload Remittance. {@code genTA.jsp} stores the uploaded remittance and forwards to
 * {@code ProcessRemittance} ({@link GenTa2Action}), which reads the file's name from the {@code filename}
 * request attribute. The page never set it, so the action failed on a null name (HTTP 500).
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC Upload Remittance hand-off to ProcessRemittance (#4806)")
class GenTa2ActionUploadUnitTest extends CarlosUnitTestBase {

    private static final Path GEN_TA_JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/genTA.jsp");
    private static final String FILE_NAME = "FAKE-remittance.TXT";
    private static final String PAYMENT = "20261016";
    private static final String PAYEE = "12345";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedConstruction<MSPReconcile> mspReconcileConstruction;
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/billing/CA/BC/ProcessRemittance");
    private TeleplanS21Dao s21Dao;
    private Path documentDir;
    private String originalDocumentDir;
    private boolean originalDocumentDirPresent;

    @BeforeEach
    void setUp() throws IOException {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull())).thenReturn(true);
        s21Dao = createAndRegisterMock(TeleplanS21Dao.class);
        createAndRegisterMock(TeleplanS00Dao.class);
        createAndRegisterMock(TeleplanS23Dao.class);
        createAndRegisterMock(TeleplanS25Dao.class);
        createAndRegisterMock(TeleplanS22Dao.class);
        createAndRegisterMock(TeleplanC12Dao.class);

        originalDocumentDirPresent = CarlosProperties.getInstance().containsKey("DOCUMENT_DIR");
        originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        documentDir = Files.createTempDirectory("remittance-store");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mock(LoggedInInfo.class));
        mspReconcileConstruction = mockConstruction(MSPReconcile.class);
    }

    @AfterEach
    void tearDown() throws IOException {
        mspReconcileConstruction.close();
        loggedInInfoMock.close();
        servletActionContextMock.close();
        if (originalDocumentDirPresent) {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
        } else {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        }
        try (Stream<Path> files = Files.walk(documentDir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    /** One S21 (remittance header) record, fixed width as both genTA.jsp and GenTa2Action parse it. */
    private static String s21Line() {
        String line = "S21" + "T9999" + "0000001" + PAYMENT + "1" + PAYEE + "999999" + String.format("%-25s", "FAKE-PAYEE")
                + "000012345" + "000012345" + "000000000" + "000012345" + "000000000" + " ".repeat(60);
        assertThat(line).hasSize(165);
        return line;
    }

    @Test
    @DisplayName("should open the reconciliation list and store nothing again for a remittance genTA.jsp already stored")
    void shouldStoreNothingAgain_whenRemittanceIsAlreadyStored() throws Exception {
        Files.writeString(documentDir.resolve(FILE_NAME), s21Line() + "\r\n", StandardCharsets.ISO_8859_1);
        TeleplanS21 stored = new TeleplanS21();
        stored.setId(10);
        when(s21Dao.findByFilenamePaymentPayeeNo(FILE_NAME, PAYMENT, PAYEE)).thenReturn(List.of(stored));
        request.setAttribute("filename", FILE_NAME);

        assertThat(new GenTa2Action().execute()).isEqualTo("S21");

        verify(s21Dao, never()).persist(any());
        verifyNoInteractions(mspReconcileConstruction.constructed().get(0));
    }

    @Test
    @DisplayName("should hand the uploaded file's name to ProcessRemittance before forwarding")
    void shouldPassFileName_toProcessRemittance() throws IOException {
        String page = Files.readString(GEN_TA_JSP, StandardCharsets.UTF_8);
        int setName = page.indexOf("request.setAttribute(\"filename\", filename);");
        int forward = page.indexOf("<jsp:forward page=\"<%=forwardPage%>\"/>");
        assertThat(setName).as("genTA.jsp sets the filename attribute").isGreaterThan(-1);
        assertThat(forward).isGreaterThan(setName);
        assertThat(page).contains("String forwardPage = \"/billing/CA/BC/ProcessRemittance\";");
    }
}
