/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingDiskNameDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.DiskFilenameRow;
import io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewLoader;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnMriViewModel;
import io.github.carlos_emr.carlos.commn.dao.ProviderBillCenterDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderBillCenter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused tests for OHIP MRI view-model assembly guardrails.
 *
 * <p>The year parameter feeds date-range queries. Invalid years must fail before DAO access so
 * operators see a load error instead of an apparently empty OHIP history table.</p>
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BillingOnMriViewModelAssembler")
class BillingOnMriViewModelAssemblerTest {

    private ProviderDao providerDao;
    private BillActivityDao billActivityDao;
    private ProviderDataDao providerDataDao;
    private ProviderBillCenterDao providerBillCenterDao;
    private SecurityInfoManager securityInfoManager;
    private BillingReviewLoader reviewLoader;
    private BillingOnLookupService lookupService;
    private LoggedInInfo loggedInInfo;
    private LogCapture logCapture;

    @BeforeEach
    void setUp() {
        providerDao = mock(ProviderDao.class);
        billActivityDao = mock(BillActivityDao.class);
        providerDataDao = mock(ProviderDataDao.class);
        providerBillCenterDao = mock(ProviderBillCenterDao.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        reviewLoader = mock(BillingReviewLoader.class);
        lookupService = mock(BillingOnLookupService.class);
        loggedInInfo = mock(LoggedInInfo.class);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(reviewLoader.getProviderBillingStr()).thenReturn(List.of());
        when(reviewLoader.getMRIList(anyString(), anyString(), eq("U"))).thenReturn(List.of());
        when(lookupService.getPropProviderName()).thenReturn(new Properties());
        when(providerDao.getBillableProviders()).thenReturn(List.of());
        when(providerDao.getActiveProviders()).thenReturn(List.of());
        when(billActivityDao.findCurrentByDateRange(any(), any())).thenReturn(List.of());

        logCapture = LogCapture.forLogger(BillingOnMriViewModelAssembler.class);
    }

    @AfterEach
    void tearDown() {
        if (logCapture != null) {
            logCapture.close();
        }
    }

    @Test
    @DisplayName("should reject malformed year before billing queries")
    void shouldRejectMalformedYear_beforeBillingQueries() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("year", "20xx");

        assertThatThrownBy(() -> assembler().assemble(request, loggedInInfo))
                .isInstanceOfSatisfying(BillingDataLoadException.class, exception -> {
                    assertThat(exception.phase()).isEqualTo(BillingDataLoadException.Phase.DATE_PARSE);
                    assertThat(exception.context()).containsEntry("year", "20xx");
                });
        verifyNoInteractions(providerDao, billActivityDao, providerDataDao, providerBillCenterDao,
                reviewLoader, lookupService);
    }

    @Test
    @DisplayName("should assemble model when inputs are valid")
    void shouldAssembleModel_whenInputsAreValid() {
        Provider provider = provider("100", "Alpha", "Anne", "OHIP100");
        ProviderBillCenter billCenter = billCenter("100", "BC1");
        BillingDiskNameDto disk = disk("7", "2026-02-03 04:05:06", "2026-02-03 01:02:03",
                new DiskFilenameRow("11", "claim.html", "OHIP100", "100", "5", "U", "123.45"));
        BillActivity activity = billActivity("OHIP100", new Date(1_779_999_999_000L), "9",
                "123.456", "old.ohip", "old.html");
        Properties providerNames = new Properties();
        providerNames.setProperty("100", "Alpha, Anne");

        when(reviewLoader.getProviderBillingStr()).thenReturn(List.of(
                new ProviderDropdownEntry("100", "Alpha", "Anne", "OHIP100", "GRP", "SPEC")));
        when(providerDao.getBillableProviders()).thenReturn(List.of(provider));
        when(providerBillCenterDao.find("100")).thenReturn(billCenter);
        when(reviewLoader.getMRIList("2026-01-01 00:00:01", "2026-12-31 23:59:59", "U"))
                .thenReturn(List.of(disk));
        when(lookupService.getPropProviderName()).thenReturn(providerNames);
        when(providerDao.getActiveProviders()).thenReturn(List.of(provider));
        when(billActivityDao.findCurrentByDateRange(any(), any())).thenReturn(List.of(activity));

        BillingOnMriViewModel model = assembler().assemble(requestForYear("2026"), loggedInInfo);

        assertThat(model.getSelectedYear()).isEqualTo("2026");
        assertThat(model.getProviderOptions())
                .extracting(BillingOnMriViewModel.ProviderEntry::providerNo)
                .containsExactly("100");
        assertThat(model.getProviderBillCenterMap()).containsEntry("100", "BC1");
        assertThat(model.getMriRows()).singleElement().satisfies(row -> {
            assertThat(row.diskId()).isEqualTo(7);
            assertThat(row.providerName()).isEqualTo("Alpha, Anne");
            assertThat(row.updateDate()).isEqualTo("2026-02-03 04:05");
            assertThat(row.rowBgColor()).isEqualTo("silver");
        });
        assertThat(model.getBillActivityRows()).singleElement().satisfies(row -> {
            assertThat(row.providerName()).isEqualTo("Alpha, Anne");
            assertThat(row.claimRecord()).isEqualTo("9");
            assertThat(row.formattedTotal()).isEqualTo("123.45");
        });
    }

    @Test
    @DisplayName("should warn and exclude bill center when code is null")
    void shouldWarnAndExcludeBillCenter_whenCodeIsNull() {
        Provider provider = provider("777", "Missing", "Center", "OHIP777");
        when(providerDao.getBillableProviders()).thenReturn(List.of(provider));
        when(providerBillCenterDao.find("777")).thenReturn(billCenter("777", null));

        BillingOnMriViewModel model = assembler().assemble(requestForYear("2026"), loggedInInfo);

        assertThat(model.getProviderBillCenterMap()).doesNotContainKey("777");
        assertThat(logCapture.messages()).anySatisfy(message -> {
            assertThat(message).contains("missing bill-center code");
            assertThat(message).contains("777");
        });
    }

    @Test
    @DisplayName("should warn and exclude bill center when code is blank")
    void shouldWarnAndExcludeBillCenter_whenCodeIsBlank() {
        Provider provider = provider("778", "Blank", "Center", "OHIP778");
        when(providerDao.getBillableProviders()).thenReturn(List.of(provider));
        when(providerBillCenterDao.find("778")).thenReturn(billCenter("778", "   "));

        BillingOnMriViewModel model = assembler().assemble(requestForYear("2026"), loggedInInfo);

        assertThat(model.getProviderBillCenterMap()).doesNotContainKey("778");
        assertThat(logCapture.messages()).anySatisfy(message -> {
            assertThat(message).contains("missing bill-center code");
            assertThat(message).contains("778");
        });
    }

    @Test
    @DisplayName("should drop MRI row when disk id is malformed")
    void shouldDropMriRow_whenDiskIdIsMalformed() {
        DiskFilenameRow row = new DiskFilenameRow("11", "file.html", "OHIP100", "100", "claim", "U", "12.00");
        when(reviewLoader.getMRIList(anyString(), anyString(), eq("U")))
                .thenReturn(List.of(disk("not-a-number", "2026-02-03 04:05:06", "2026-02-03 04:05:06", row)));

        BillingOnMriViewModel model = assembler().assemble(requestForYear("2026"), loggedInInfo);

        assertThat(model.getMriRows()).isEmpty();
        assertThat(logCapture.messages()).anySatisfy(message -> {
            assertThat(message).contains("dropping MRI row with invalid disk id");
            assertThat(message).contains("not-a-number");
        });
    }

    @Test
    @DisplayName("should sort bill activities when update date is null")
    void shouldSortBillActivities_whenUpdateDateIsNull() {
        Provider provider = provider("100", "Alpha", "Anne", "OHIP100");
        BillActivity nullDate = billActivity("OHIP100", null, "null-date", "1.00", "null.ohip", "null.html");
        BillActivity dated = billActivity("OHIP100", new Date(1_779_999_999_000L), "dated", "2.00",
                "dated.ohip", "dated.html");
        when(providerDao.getActiveProviders()).thenReturn(List.of(provider));
        when(billActivityDao.findCurrentByDateRange(any(), any()))
                .thenReturn(new ArrayList<>(List.of(nullDate, dated)));

        BillingOnMriViewModel model = assembler().assemble(requestForYear("2026"), loggedInInfo);

        assertThat(model.getBillActivityRows())
                .extracting(BillingOnMriViewModel.BillActivityRow::claimRecord)
                .containsExactly("dated", "null-date");
    }

    private BillingOnMriViewModelAssembler assembler() {
        return new BillingOnMriViewModelAssembler(
                providerDao,
                billActivityDao,
                providerDataDao,
                providerBillCenterDao,
                securityInfoManager,
                reviewLoader,
                lookupService);
    }

    private MockHttpServletRequest requestForYear(String year) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("year", year);
        request.addParameter("xml_vdate", year + "-01-01");
        request.addParameter("xml_appointment_date", year + "-01-31");
        request.addParameter("useProviderMOH", "true");
        return request;
    }

    private Provider provider(String providerNo, String lastName, String firstName, String ohipNo) {
        Provider provider = new Provider(providerNo, lastName, "doctor", "M", "00", firstName);
        provider.setOhipNo(ohipNo);
        return provider;
    }

    private ProviderBillCenter billCenter(String providerNo, String billCenterCode) {
        ProviderBillCenter billCenter = new ProviderBillCenter();
        billCenter.setProviderNo(providerNo);
        billCenter.setBillCenterCode(billCenterCode);
        return billCenter;
    }

    private BillingDiskNameDto disk(String id, String updatedAt, String createdAt, DiskFilenameRow filenameRow) {
        BillingDiskNameDto disk = new BillingDiskNameDto();
        disk.setId(id);
        disk.setUpdatedatetime(updatedAt);
        disk.setCreatedatetime(createdAt);
        disk.setOhipfilename("claim.ohip");
        disk.setFilenames(List.of(filenameRow));
        return disk;
    }

    private BillActivity billActivity(String ohipNo, Date updateDate, String claimRecord, String total,
                                      String ohipFile, String htmlFile) {
        BillActivity activity = new BillActivity();
        activity.setProviderOhipNo(ohipNo);
        activity.setUpdateDateTime(updateDate);
        activity.setClaimRecord(claimRecord);
        activity.setTotal(total);
        activity.setOhipFilename(ohipFile);
        activity.setHtmlFilename(htmlFile);
        return activity;
    }

}
