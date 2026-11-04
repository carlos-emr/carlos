/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Exercise the actual title producers: the navbar intentionally renders their formatting as HTML. */
@Tag("unit")
@Tag("security")
@Tag("encounter")
class EctDisplayStoredTitleEncodingUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private LoggedInInfo user;
    private EctSessionBean bean;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void prepare() {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        user = mock(LoggedInInfo.class);
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), isNull())).thenReturn(true);
        request = new MockHttpServletRequest();
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        bean = mock(EctSessionBean.class);
        bean.demographicNo = "101";
        bean.appointmentNo = "0";
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
    }

    @AfterEach
    void closeServletContext() {
        servlet.close();
    }

    @ParameterizedTest
    @EnumSource(Tickler.PRIORITY.class)
    void shouldEncodeStoredTicklerTextAndKeepPriorityFormatting(Tickler.PRIORITY priority) {
        var manager = createAndRegisterMock(TicklerManager.class);
        var action = spy(new EctDisplayTickler2Action());
        doReturn("Tickler").when(action).getText(anyString());
        for (String message : List.of("<img src=x onerror=alert(1)> &", "Élodie & O'Neil", "literal &lt;b&gt;")) {
            var tickler = new Tickler();
            tickler.setId(123);
            tickler.setMessage(message);
            tickler.setPriority(priority);
            tickler.setServiceDate(new Date(1000));
            when(manager.findActiveByDemographicNo(user, 101)).thenReturn(List.of(tickler));
            var panel = new NavBarDisplayDAO();

            assertThat(action.getInfo(bean, request, panel)).isTrue();
            var item = panel.getItem(0);
            var rendered = Jsoup.parseBodyFragment(item.getTitle());
            assertThat(rendered.select("img, script, i")).isEmpty();
            assertThat(item.getLinkTitle()).contains(message);
            assertThat(item.getURL()).contains("tickler_no=123");
            if (priority == Tickler.PRIORITY.High) {
                assertThat(rendered.select("b").text()).isEqualTo(message);
                assertThat(rendered.select("span").text()).isEqualTo("⚑");
            } else {
                assertThat(rendered.body().text()).isEqualTo(message);
                assertThat(rendered.select("b, span")).isEmpty();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"<img src=x onerror=alert(1)>", "Élodie & O'Neil", "literal &lt;b&gt;"})
    void shouldEncodeEformNameAndSubjectExactlyOnce(String stored) {
        var action = spy(new EctDisplayEForm2Action());
        doReturn("eForms").when(action).getText(anyString());
        var form = new EFormData();
        form.setId(123);
        form.setFormName(stored);
        form.setSubject("<b>&</b>");
        form.setFormDate(new Date(1000));
        try (var forms = mockStatic(EFormUtil.class)) {
            forms.when(() -> EFormUtil.listEForms(EFormUtil.DATE, EFormUtil.CURRENT, "doctor,999998"))
                    .thenReturn(new ArrayList<>());
            forms.when(() -> EFormUtil.listPatientEformsCurrent(101, true, 0, 100)).thenReturn(List.of(form));
            var panel = new NavBarDisplayDAO();

            assertThat(action.getInfo(bean, request, panel)).isTrue();
            var item = panel.getItem(0);
            var rendered = Jsoup.parseBodyFragment(item.getTitle());
            assertThat(rendered.body().children()).isEmpty();
            assertThat(rendered.body().text()).isEqualTo(stored + ": <b>&</b>");
            assertThat(item.getLinkTitle()).isEqualTo("<b>&</b>");
            assertThat(item.getURL()).contains("fdid=123");
        }
    }
}
