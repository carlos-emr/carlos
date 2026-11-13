/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.integration.mchcv;

import java.util.stream.Stream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class ValidateSwipeCard2ActionUnitTest extends CarlosUnitTestBase {
    private static final String TRACK = "%b6100549267294685^FOX/AMANDA                ^1501799219800407DKJACOB10010101?5";
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final HCValidator validator = mock(HCValidator.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockedStatic<HCValidationFactory> factory;
    private ValidateSwipeCard2Action action;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        factory = mockStatic(HCValidationFactory.class);
        factory.when(HCValidationFactory::getHCValidator).thenReturn(validator);
        when(security.hasPrivilege(user, "_demographic", "r", null)).thenReturn(true);
        action = new ValidateSwipeCard2Action();
    }

    @AfterEach
    void tearDown() {
        factory.close();
        login.close();
        servlet.close();
    }

    static Stream<String> malformedTracks() {
        return Stream.of(null, "", TRACK.substring(0, 40), TRACK + "xx", TRACK.replace('/', ' '));
    }

    @ParameterizedTest
    @MethodSource("malformedTracks")
    void malformedTrackReturnsBadRequestWithoutValidationOrDisclosure(String track) throws Exception {
        action.setMagneticStripe(track);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST);
        verify(request, never()).setAttribute(anyString(), any());
        factory.verifyNoInteractions();
        verifyNoInteractions(validator);
    }

    @Test
    void validTrackPreservesParsedCardAndValidatorResult() throws Exception {
        HCValidationResult result = new HCValidationResult();
        result.setResponseCode("51");
        when(validator.validate("9267294685", "DK")).thenReturn(result);
        action.setMagneticStripe(TRACK);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        ArgumentCaptor<HCMagneticStripe> card = ArgumentCaptor.forClass(HCMagneticStripe.class);
        verify(request).setAttribute(eq("hcMagneticStripe"), card.capture());
        verify(request).setAttribute("validationResult", result);
        assertThat(card.getValue().getHealthNumber()).isEqualTo("9267294685");
        assertThat(card.getValue().getFirstName()).isEqualTo("AMANDA");
        assertThat(card.getValue().getBirthDate()).isEqualTo("19800407");
        verifyNoInteractions(response);
    }

    @Test
    void wellFormedTrackWithInvalidHealthNumberStillRendersValidatorVerdict() throws Exception {
        HCValidationResult result = new HCValidationResult();
        result.setResponseCode("05");
        when(validator.validate("92672X4685", "DK")).thenReturn(result);
        action.setMagneticStripe(TRACK.replace("9267294685", "92672X4685"));
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(request).setAttribute("validationResult", result);
        verifyNoInteractions(response);
    }

    @Test
    void deniedPrivilegeIsCheckedBeforeMalformedInput() {
        when(security.hasPrivilege(user, "_demographic", "r", null)).thenReturn(false);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        factory.verifyNoInteractions();
        verifyNoInteractions(response);
    }

    @Test
    void validatorFailureIsNotMisreportedAsMalformedInput() {
        action.setMagneticStripe(TRACK);
        IllegalArgumentException failure = new IllegalArgumentException("Validator configuration failure");
        when(validator.validate("9267294685", "DK")).thenThrow(failure);
        assertThatThrownBy(action::execute).isSameAs(failure);
        verifyNoInteractions(response);
    }
}
