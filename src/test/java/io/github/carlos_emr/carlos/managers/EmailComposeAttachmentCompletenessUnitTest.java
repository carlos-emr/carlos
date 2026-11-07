/* Copyright (c) 2026 CARLOS Contributors. Licensed under the GNU GPL. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("email")
class EmailComposeAttachmentCompletenessUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"EFORM", "DOC", "LAB", "HRM", "FORM"})
    void shouldStopCompose_whenSelectedAttachmentRendererReturnsNoPdf(DocumentType type) {
        EmailComposeManager manager = new EmailComposeManager();
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        injectDependency(manager, "securityInfoManager", security);
        // A renderer that returns null must not silently remove a selected attachment.
        injectDependency(manager, "documentAttachmentManager", mock(DocumentAttachmentManager.class));
        injectDependency(manager, "formsManager", mock(FormsManager.class));
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.isOntarioBillingRegion()).thenReturn(true);
        try (MockedStatic<CarlosProperties> config = mockStatic(CarlosProperties.class)) {
            config.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThatThrownBy(() -> prepareSelectedAttachment(manager, type, loggedInInfo, request))
                    .isInstanceOf(PDFGenerationException.class)
                    .hasMessage("A selected email attachment could not be rendered");
        }
    }
    private void prepareSelectedAttachment(EmailComposeManager manager, DocumentType type,
            LoggedInInfo loggedInInfo, MockHttpServletRequest request) throws PDFGenerationException {
        String[] selected = {"42"};
        switch (type) {
            case EFORM -> manager.prepareEFormAttachments(loggedInInfo, "", selected);
            case DOC -> manager.prepareEDocAttachments(loggedInInfo, selected);
            case LAB -> manager.prepareLabAttachments(loggedInInfo, selected);
            case HRM -> manager.prepareHRMAttachments(loggedInInfo, selected);
            case FORM -> manager.prepareFormAttachments(request, new MockHttpServletResponse(), selected, 123);
            default -> throw new AssertionError("Unexpected test type");
        }
    }

}
