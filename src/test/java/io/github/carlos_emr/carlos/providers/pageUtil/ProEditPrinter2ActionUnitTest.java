/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.providers.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ProEditPrinter2ActionUnitTest extends CarlosWebTestBase {
    private UserPropertyDAO properties;

    private ProEditPrinter2Action action(String method, boolean allowed) {
        mockRequest.setMethod(method);
        properties = mock(UserPropertyDAO.class);
        replaceSpringUtilsBean(UserPropertyDAO.class, properties);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_pref"), eq("w"), isNull())).thenReturn(allowed);
        return new ProEditPrinter2Action();
    }

    @Test void openingPreferencesDoesNotReadOrOverwriteAnyPrinterSetting() throws Exception {
        assertThat(action("GET", true).execute()).isEqualTo("success");
        assertThat(mockRequest.getAttribute("status")).isNull();
        verifyNoInteractions(properties);
    }

    @Test void unsupportedMethodDoesNotChangeSettings() throws Exception {
        assertThat(action("PUT", true).execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        verifyNoInteractions(properties);
    }

    @Test void deniedSaveDoesNotChangeSettings() {
        ProEditPrinter2Action action = action("POST", false);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(properties);
    }

    @Test void explicitSavePersistsPrinterAndReportsSuccess() throws Exception {
        ProEditPrinter2Action action = action("POST", true);
        action.setDefaultPrinterNamePDFLabel("Clinic's Label Printer");
        action.setSilentPrintPDFLabel(true);
        assertThat(action.execute()).isEqualTo("success");
        ArgumentCaptor<UserProperty> saved = ArgumentCaptor.forClass(UserProperty.class);
        verify(properties, times(12)).saveProp(saved.capture());
        assertThat(saved.getAllValues()).anySatisfy(prop -> {
            assertThat(prop.getName()).isEqualTo(UserProperty.DEFAULT_PRINTER_PDF_LABEL);
            assertThat(prop.getValue()).isEqualTo("Clinic's Label Printer");
        }).anySatisfy(prop -> {
            assertThat(prop.getName()).isEqualTo(UserProperty.DEFAULT_PRINTER_PDF_LABEL_SILENT_PRINT);
            assertThat(prop.getValue()).isEqualTo("yes");
        });
        assertThat(mockRequest.getAttribute("status")).isEqualTo("complete");
    }
}
