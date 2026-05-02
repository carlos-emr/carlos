/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCodeSearchViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingDiagCodeViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingInrReportViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOhipBillingHistoryViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOhipSimulationViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnDisplayViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnEditPrivateCodeViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnHistorySpecialistViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnHistoryViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnMriViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnNewReportViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnThirdPartyInvoiceViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingReportControlViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingReportFragmentViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.GenerateRaDescriptionViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.GenerateRaSummaryViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.GstReportViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.ManageBillingFormBillTypeViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.ManageBillingFormViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.OnRaErrorViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.OnRaSummaryViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.OnRaViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnDiskService;
import io.github.carlos_emr.carlos.billings.ca.on.service.OhipReportGenerationService;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnRaImportService;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnRaSettlementService;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnRaSummaryTotalsService;
import io.github.carlos_emr.carlos.billings.ca.on.service.RaHeaderTotalsPersister;
import io.github.carlos_emr.carlos.commn.dao.Billing3rdPartyAddressDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ReportProviderDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Missing ON billing action privilege coverage")
@Tag("unit")
@Tag("billing")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MissingBillingActionPrivilegeUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private LoggedInInfo loggedInInfo;
    private SecurityInfoManager securityInfoManager;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");
        mockResponse = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(false);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        ActionContext.of().bind();
    }

    @AfterEach
    void tearDown() {
        ActionContext.clear();
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    Stream<Arguments> actionFactories() {
        return Stream.of(
                Arguments.of("BillingLegacyReport2Action", "_admin.billing",
                        (ActionHarnessFactory) security -> harness(
                                () -> new BillingLegacyReport2Action(security).execute())),
                Arguments.of("ViewBenefitScheduleUpload2Action", "_admin.billing",
                        (ActionHarnessFactory) security -> harness(
                                () -> new ViewBenefitScheduleUpload2Action(security).execute())),
                Arguments.of("BillingOB2View2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOhipBillingHistoryViewModelAssembler assembler =
                                    mock(BillingOhipBillingHistoryViewModelAssembler.class);
                            return harness(() -> new BillingOB2View2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("GstReport2Action", "_admin.billing",
                        (ActionHarnessFactory) security -> {
                            GstReportViewModelAssembler assembler = mock(GstReportViewModelAssembler.class);
                            return harness(() -> new GstReport2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ManageBillingForm2Action", "_admin.billing",
                        (ActionHarnessFactory) security -> {
                            ManageBillingFormViewModelAssembler assembler = mock(ManageBillingFormViewModelAssembler.class);
                            return harness(() -> new ManageBillingForm2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ManageBillingFormBillType2Action", "_admin.billing",
                        (ActionHarnessFactory) security -> {
                            ManageBillingFormBillTypeViewModelAssembler assembler =
                                    mock(ManageBillingFormBillTypeViewModelAssembler.class);
                            return harness(() -> new ManageBillingFormBillType2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingCodeSearch2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingCodeSearchViewModelAssembler assembler = mock(BillingCodeSearchViewModelAssembler.class);
                            return harness(() -> new ViewBillingCodeSearch2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingDiagSearch2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingDiagCodeViewModelAssembler assembler = mock(BillingDiagCodeViewModelAssembler.class);
                            return harness(() -> new ViewBillingDiagSearch2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOhipSimulation2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOhipSimulationViewModelAssembler assembler =
                                    mock(BillingOhipSimulationViewModelAssembler.class);
                            return harness(() -> new ViewBillingOhipSimulation2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOnDiagDesc2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingDiagCodeViewModelAssembler assembler = mock(BillingDiagCodeViewModelAssembler.class);
                            return harness(() -> new ViewBillingOnDiagDesc2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOnDisplay2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOnDisplayViewModelAssembler assembler = mock(BillingOnDisplayViewModelAssembler.class);
                            return harness(() -> new ViewBillingOnDisplay2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOnEditPrivateCode2Action", "_admin.billing",
                        (ActionHarnessFactory) security -> {
                            BillingOnEditPrivateCodeViewModelAssembler assembler =
                                    mock(BillingOnEditPrivateCodeViewModelAssembler.class);
                            return harness(() -> new ViewBillingOnEditPrivateCode2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOnHistory2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOnHistoryViewModelAssembler assembler = mock(BillingOnHistoryViewModelAssembler.class);
                            return harness(() -> new ViewBillingOnHistory2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOnHistorySpecialist2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOnHistorySpecialistViewModelAssembler assembler =
                                    mock(BillingOnHistorySpecialistViewModelAssembler.class);
                            return harness(() -> new ViewBillingOnHistorySpecialist2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOnMri2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOnMriViewModelAssembler assembler = mock(BillingOnMriViewModelAssembler.class);
                            return harness(() -> new ViewBillingOnMri2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOnNewReport2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOnNewReportViewModelAssembler assembler = mock(BillingOnNewReportViewModelAssembler.class);
                            return harness(() -> new ViewBillingOnNewReport2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOnThirdPartyInvoice2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOnThirdPartyInvoiceViewModelAssembler assembler =
                                    mock(BillingOnThirdPartyInvoiceViewModelAssembler.class);
                            return harness(() -> new ViewBillingOnThirdPartyInvoice2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewBillingOnThirdPartyPayments2Action", "_billing",
                        (ActionHarnessFactory) security -> harness(
                                () -> new ViewBillingOnThirdPartyPayments2Action(security).execute())),
                Arguments.of("ViewBillingReportCenter2Action", "_report",
                        (ActionHarnessFactory) security -> {
                            ReportProviderDao dao = mock(ReportProviderDao.class);
                            return harness(() -> new ViewBillingReportCenter2Action(security, dao).execute(), dao);
                        }),
                Arguments.of("ViewBillingReportControl2Action", "_report",
                        (ActionHarnessFactory) security -> {
                            BillingReportFragmentViewModelAssembler fragmentAssembler =
                                    mock(BillingReportFragmentViewModelAssembler.class);
                            BillingReportControlViewModelAssembler controlAssembler =
                                    mock(BillingReportControlViewModelAssembler.class);
                            return harness(
                                    () -> new ViewBillingReportControl2Action(security, fragmentAssembler, controlAssembler)
                                            .execute(),
                                    fragmentAssembler, controlAssembler);
                        }),
                Arguments.of("ViewBillingResearchCodeSearch2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingCodeSearchViewModelAssembler assembler = mock(BillingCodeSearchViewModelAssembler.class);
                            return harness(
                                    () -> new ViewBillingResearchCodeSearch2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewGenGroupReport2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            OhipReportGenerationService service = mock(OhipReportGenerationService.class);
                            return harness(() -> new ViewGenGroupReport2Action(security, service).execute(), service);
                        }),
                Arguments.of("ViewGenRaDesc2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            GenerateRaDescriptionViewModelAssembler assembler =
                                    mock(GenerateRaDescriptionViewModelAssembler.class);
                            RaHeaderTotalsPersister persister = mock(RaHeaderTotalsPersister.class);
                            return harness(() -> new ViewGenRaDesc2Action(security, assembler, persister).execute(),
                                    assembler, persister);
                        }),
                Arguments.of("ViewGenRaSummary2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            GenerateRaSummaryViewModelAssembler assembler = mock(GenerateRaSummaryViewModelAssembler.class);
                            RaHeaderTotalsPersister persister = mock(RaHeaderTotalsPersister.class);
                            return harness(() -> new ViewGenRaSummary2Action(security, assembler, persister).execute(),
                                    assembler, persister);
                        }),
                Arguments.of("ViewGenRaSummaryDetail2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            GenerateRaSummaryViewModelAssembler assembler = mock(GenerateRaSummaryViewModelAssembler.class);
                            RaHeaderTotalsPersister persister = mock(RaHeaderTotalsPersister.class);
                            return harness(
                                    () -> new ViewGenRaSummaryDetail2Action(security, assembler, persister).execute(),
                                    assembler, persister);
                        }),
                Arguments.of("ViewInrReport2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingInrReportViewModelAssembler assembler = mock(BillingInrReportViewModelAssembler.class);
                            return harness(() -> new ViewInrReport2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewOnGenRa2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            OnRaViewModelAssembler assembler = mock(OnRaViewModelAssembler.class);
                            OnRaImportService service = mock(OnRaImportService.class);
                            return harness(() -> new ViewOnGenRa2Action(security, assembler, service).execute(),
                                    assembler, service);
                        }),
                Arguments.of("ViewOnGenRaError2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            OnRaErrorViewModelAssembler assembler = mock(OnRaErrorViewModelAssembler.class);
                            return harness(() -> new ViewOnGenRaError2Action(security, assembler).execute(), assembler);
                        }),
                Arguments.of("ViewOnGenRaSettle2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            OnRaSettlementService service = mock(OnRaSettlementService.class);
                            return harness(() -> new ViewOnGenRaSettle2Action(security, service).execute(), service);
                        }),
                Arguments.of("ViewOnGenRaSettle352Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            OnRaSettlementService service = mock(OnRaSettlementService.class);
                            return harness(() -> new ViewOnGenRaSettle352Action(security, service).execute(), service);
                        }),
                Arguments.of("ViewOnGenRaSummary2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            OnRaSummaryViewModelAssembler assembler = mock(OnRaSummaryViewModelAssembler.class);
                            OnRaSummaryTotalsService service = mock(OnRaSummaryTotalsService.class);
                            return harness(() -> new ViewOnGenRaSummary2Action(security, assembler, service).execute(),
                                    assembler, service);
                        }),
                Arguments.of("ViewOnReportGeneration2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOnDiskService service = mock(BillingOnDiskService.class);
                            return harness(() -> new ViewOnReportGeneration2Action(security, service).execute(), service);
                        }),
                Arguments.of("ViewOnReportRegeneration2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            BillingOnDiskService service = mock(BillingOnDiskService.class);
                            return harness(() -> new ViewOnReportRegeneration2Action(security, service).execute(), service);
                        }),
                Arguments.of("ViewOnThirdPartyBillingAddressSearch2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            Billing3rdPartyAddressDao dao = mock(Billing3rdPartyAddressDao.class);
                            return harness(() -> new ViewOnThirdPartyBillingAddressSearch2Action(security, dao).execute(), dao);
                        }),
                Arguments.of("ViewSearchRefDoc2Action", "_billing",
                        (ActionHarnessFactory) security -> {
                            ProfessionalSpecialistDao dao = mock(ProfessionalSpecialistDao.class);
                            return harness(() -> new ViewSearchRefDoc2Action(security, dao).execute(), dao);
                        })
        );
    }

    @org.junit.jupiter.api.Test
    void shouldIncludeBenefitScheduleViewActionInPrivilegeCoverage() {
        assertThat(actionFactories().map(arguments -> (String) arguments.get()[0]))
                .contains("ViewBenefitScheduleUpload2Action");
    }

    @ParameterizedTest(name = "{0} rejects denied {1} privilege")
    @MethodSource("actionFactories")
    void shouldThrowSecurityException_whenPrivilegeMissing(
            String label, String privilegeObject, ActionHarnessFactory factory) {
        ActionHarness harness = factory.create(securityInfoManager);

        assertThatThrownBy(harness.invocation()::call)
                .as("%s must reject denied %s privilege", label, privilegeObject)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining(privilegeObject);
    }

    @ParameterizedTest(name = "{0} does not reach collaborators when privilege is missing")
    @MethodSource("actionFactories")
    void shouldNotCallBusinessCollaborators_whenPrivilegeMissing(
            String label, String privilegeObject, ActionHarnessFactory factory) {
        ActionHarness harness = factory.create(securityInfoManager);

        assertThatThrownBy(harness.invocation()::call)
                .as("%s must reject denied %s privilege", label, privilegeObject)
                .isInstanceOf(SecurityException.class);
        if (harness.collaborators().length > 0) {
            verifyNoInteractions(harness.collaborators());
        }
    }

    private static ActionHarness harness(Callable<String> invocation, Object... collaborators) {
        return new ActionHarness(invocation, collaborators);
    }

    @FunctionalInterface
    private interface ActionHarnessFactory {
        ActionHarness create(SecurityInfoManager securityInfoManager);
    }

    private record ActionHarness(Callable<String> invocation, Object[] collaborators) {
    }
}
