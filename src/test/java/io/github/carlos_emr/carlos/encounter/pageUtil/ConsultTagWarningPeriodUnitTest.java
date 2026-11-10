/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.tld.ConsultTag;
import jakarta.servlet.ServletContext;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.context.WebApplicationContext;

import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
class ConsultTagWarningPeriodUnitTest {
    @ParameterizedTest
    @CsvSource(value = {"6|6", "' 6 '|6", "6 months|1", "0|1", "''|1"}, delimiter = '|')
    void shouldDisplayReferralCount_whenStoredPeriodRequiresNormalization(String stored, int months) throws Exception {
        var page = mock(PageContext.class);
        var servlet = mock(ServletContext.class);
        var context = mock(WebApplicationContext.class);
        var writer = mock(JspWriter.class);
        var referrals = mock(ConsultationRequestDao.class);
        var properties = mock(UserPropertyDAO.class);
        when(page.getServletContext()).thenReturn(servlet);
        when(page.getOut()).thenReturn(writer);
        when(servlet.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE)).thenReturn(context);
        when(context.getBean(ConsultationRequestDao.class)).thenReturn(referrals);
        when(context.getBean(UserPropertyDAO.class)).thenReturn(properties);
        var period = new UserProperty();
        period.setValue(stored);
        var team = new UserProperty();
        team.setValue("-1");
        when(properties.getProp("999998", UserProperty.CONSULTATION_TIME_PERIOD_WARNING)).thenReturn(period);
        when(properties.getProp("999998", UserProperty.CONSULTATION_TEAM_WARNING)).thenReturn(team);
        when(referrals.getCountReferralsAfterCutOffDateAndNotCompleted(any(Date.class))).thenReturn(7);
        var tag = new ConsultTag();
        tag.setProviderNo("999998");
        tag.setPageContext(page);

        tag.doStartTag();
        tag.doEndTag();

        var cutoff = ArgumentCaptor.forClass(Date.class);
        verify(referrals).getCountReferralsAfterCutOffDateAndNotCompleted(cutoff.capture());
        var expected = Calendar.getInstance();
        expected.add(Calendar.MONTH, -months);
        assertThat(Math.abs(expected.getTimeInMillis() - cutoff.getValue().getTime())).isLessThan(5000);
        verify(writer).print("<span class='tabalert'>  ");
        verify(writer).print("<sup>7</sup></span>");
    }
}
