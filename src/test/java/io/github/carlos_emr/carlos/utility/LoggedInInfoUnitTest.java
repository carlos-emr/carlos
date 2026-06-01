package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LoggedInInfo unit tests")
@Tag("unit")
@Tag("web")
class LoggedInInfoUnitTest {

    @Test
    @DisplayName("should return session LoggedInInfo when present")
    void shouldReturnLoggedInInfo_whenSessionValuePresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(new LoggedInInfo().getLoggedInInfoKey())).thenReturn(loggedInInfo);

        LoggedInInfo actual = LoggedInInfo.requireLoggedInInfoFromSession(request);

        assertThat(actual).isSameAs(loggedInInfo);
    }

    @Test
    @DisplayName("should throw SecurityException when session LoggedInInfo is missing")
    void shouldThrowSecurityException_whenSessionValueMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);

        assertThatThrownBy(() -> LoggedInInfo.requireLoggedInInfoFromSession(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required session");
    }
}
