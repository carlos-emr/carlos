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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingServiceLine;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingClaimSubmissionService} claim-save submission rules and legacy compatibility. */
@DisplayName("BillingClaimSubmissionService")
@Tag("unit")
@Tag("billing")
class BillingClaimSubmissionServiceUnitTest extends CarlosUnitTestBase {

    @Mock private BillingOnClaimPersister mockPersister;
    @Mock private BillingOnLookupService mockLookupService;
    @Mock private HttpServletRequest mockRequest;

    private AutoCloseable mockitoCloseable;
    private BillingClaimSubmissionService service;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        service = new BillingClaimSubmissionService(mockPersister, mockLookupService);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldReturnGeneratedBillingId_fromAddABillingRecord() {
        BillingClaimHeaderDto header = new BillingClaimHeaderDto();
        List<BillingClaimItemDto> items = List.of(new BillingClaimItemDto());
        ArrayList<Object> claim = new ArrayList<>();
        claim.add(header);
        claim.add(items);

        when(mockPersister.addOneClaimHeaderRecord(header)).thenReturn(1234);

        BillingClaimSubmissionService.SaveResult result = service.addABillingRecord(claim);

        assertThat(result.saved()).isTrue();
        assertThat(result.billingId()).isEqualTo(1234);
        assertThat((BillingClaimHeaderDto) claim.get(0))
                .extracting(BillingClaimHeaderDto::getId)
                .isEqualTo("1234");
        verify(mockPersister).addItemRecord(items, 1234);
    }

    @Test
    void shouldReturnZeroBillingIdAndSkipItems_whenHeaderPersistFails() {
        BillingClaimHeaderDto header = new BillingClaimHeaderDto();
        List<BillingClaimItemDto> items = List.of(new BillingClaimItemDto());
        ArrayList<Object> claim = new ArrayList<>();
        claim.add(header);
        claim.add(items);

        when(mockPersister.addOneClaimHeaderRecord(header)).thenReturn(0);

        BillingClaimSubmissionService.SaveResult result = service.addABillingRecord(claim);

        assertThat(result.saved()).isFalse();
        assertThat(result.billingId()).isZero();
        verify(mockPersister, never()).addItemRecord(items, 0);
    }

    @Test
    void shouldPassExplicitBillingId_toPrivateBillExtPersist() {
        ArrayList<Object> claim = new ArrayList<>();
        when(mockRequest.getParameter("submit")).thenReturn("Save");
        when(mockPersister.add3rdBillExt(anyMap(), eq(4321), same(claim))).thenReturn(true);

        boolean saved = service.addPrivateBillExtRecord(mockRequest, claim, 4321);

        assertThat(saved).isTrue();
        verify(mockPersister).add3rdBillExt(anyMap(), eq(4321), same(claim));
    }

    @Test
    void shouldBuildHospitalItemTotalsWithoutBinaryFloatingPointRounding() {
        MockHttpServletRequest request = hospitalBillingRequest();

        List<BillingServiceLine> lines = List.of(
                new BillingServiceLine("A001", "Office visit", "1", "1.005"));

        ArrayList claim = service.getBillingClaimHospObj(request, "2026-04-28", "1.01", lines);
        List<BillingClaimItemDto> items = (List<BillingClaimItemDto>) claim.get(1);

        assertThat(items).singleElement()
                .satisfies(item -> assertThat(item.getFee()).isEqualTo("1.01"));
    }

    @Test
    void shouldBuildStandardOhipClaimHeaderAndItems_fromRequest() {
        MockHttpServletRequest request = standardBillingRequest("HCP", "Save");
        request.setParameter("totalItem", "2");
        request.setParameter("xserviceCode_0", "A001A");
        request.setParameter("xsliCode_0", "OFF");
        request.setParameter("percCodeSubtotal_0", "33.70");
        request.setParameter("xserviceUnit_0", "");
        request.setParameter("paid_0", "1.00");
        request.setParameter("discount_0", "2.00");
        request.setParameter("xserviceCode_1", "K005A");
        request.setParameter("percCodeSubtotal_1", "62.75");
        request.setParameter("xserviceUnit_1", "3");

        ArrayList claim = service.getBillingClaimObj(request);

        BillingClaimHeaderDto header = (BillingClaimHeaderDto) claim.get(0);
        assertThat(header.getHin()).isEqualTo("1234567890");
        assertThat(header.getVer()).isEqualTo("AB");
        assertThat(header.demographicName()).isEqualTo("Doe,Jane");
        assertThat(header.lastName()).isEqualTo("Doe");
        assertThat(header.firstName()).isEqualTo("Jane");
        assertThat(header.getProviderNo()).isEqualTo("999998");
        assertThat(header.providerOhipNo()).isEqualTo("123456");
        assertThat(header.facilityNumber()).isEqualTo("0000");
        assertThat(header.visitType()).isEqualTo("00");
        assertThat(header.getPaid()).isEqualTo("5.00");
        assertThat(header.getStatus()).isEqualTo("O");
        assertThat(header.getCreator()).isEqualTo("999998");

        List<BillingClaimItemDto> items = (List<BillingClaimItemDto>) claim.get(1);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).serviceCode()).isEqualTo("A001A");
        assertThat(items.get(0).serviceNumber()).isEqualTo("1");
        assertThat(items.get(0).getLocation()).isEqualTo("OFF");
        assertThat(items.get(0).getPaid()).isEqualTo("1.00");
        assertThat(items.get(0).getDiscount()).isEqualTo("2.00");
        assertThat(items.get(0).getStatus()).isEqualTo("O");
        assertThat(items.get(1).serviceNumber()).isEqualTo("3");
        assertThat(items.get(1).getPaid()).isEqualTo("0.00");
        assertThat(items.get(1).getDiscount()).isEqualTo("0.00");
    }

    @Test
    void shouldBuildPrivateClaimItemsAsPaidStatus() {
        MockHttpServletRequest request = standardBillingRequest("PAT", "Settle");
        request.setParameter("totalItem", "1");
        request.setParameter("xserviceCode_0", "P001");
        request.setParameter("percCodeSubtotal_0", "100.00");
        request.setParameter("xserviceUnit_0", "1");

        ArrayList claim = service.getBillingClaimObj(request);

        BillingClaimHeaderDto header = (BillingClaimHeaderDto) claim.get(0);
        List<BillingClaimItemDto> items = (List<BillingClaimItemDto>) claim.get(1);
        assertThat(header.payProgram()).isEqualTo("PAT");
        assertThat(header.getPaid()).isEqualTo("100.00");
        assertThat(header.getStatus()).isEqualTo("S");
        assertThat(items).singleElement()
                .satisfies(item -> assertThat(item.getStatus()).isEqualTo("P"));
    }

    @Test
    void shouldBuildBonClaimWithoutPatientIdentityFields() {
        MockHttpServletRequest request = standardBillingRequest("BON", "Save");
        request.setParameter("totalItem", "1");
        request.setParameter("xserviceCode_0", "B001");
        request.setParameter("percCodeSubtotal_0", "10.00");
        request.setParameter("xserviceUnit_0", "1");

        ArrayList claim = service.getBillingClaimObj(request);

        BillingClaimHeaderDto header = (BillingClaimHeaderDto) claim.get(0);
        assertThat(header.getHin()).isEmpty();
        assertThat(header.getVer()).isEmpty();
        assertThat(header.demographicName()).isEmpty();
        assertThat(header.getProvince()).isEqualTo("ON");
        assertThat(header.getStatus()).isEqualTo("I");
    }

    @Test
    void shouldBuildHospitalClaimHeaderAndItems_fromLines() {
        MockHttpServletRequest request = hospitalBillingRequest();
        List<BillingServiceLine> lines = List.of(
                new BillingServiceLine("A001", "Visit", "2", "10.00"),
                new BillingServiceLine("K005", "Consult", "1", "5.25"));

        ArrayList claim = service.getBillingClaimHospObj(request, "2026-04-28", "25.25", lines);

        BillingClaimHeaderDto header = (BillingClaimHeaderDto) claim.get(0);
        assertThat(header.getHin()).isEqualTo("1234567890");
        assertThat(header.getVer()).isEqualTo("AB");
        assertThat(header.billingDate()).isEqualTo("2026-04-28");
        assertThat(header.getTotal()).isEqualTo("25.25");
        assertThat(header.getProviderNo()).isEqualTo("999998|123456");
        assertThat(header.providerOhipNo()).isEqualTo("123456");
        assertThat(header.getStatus()).isEqualTo("O");

        List<BillingClaimItemDto> items = (List<BillingClaimItemDto>) claim.get(1);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).serviceCode()).isEqualTo("A001");
        assertThat(items.get(0).getFee()).isEqualTo("20.00");
        assertThat(items.get(0).serviceNumber()).isEqualTo("2");
        assertThat(items.get(1).serviceNumber()).isEqualTo("1");
    }

    @Test
    void shouldDelegateAppointmentStatusUpdateToLookupService() {
        when(mockLookupService.updateApptStatus("456", "B", "999998")).thenReturn(true);

        boolean updated = service.updateApptStatus("456", "B", "999998");

        assertThat(updated).isTrue();
        verify(mockLookupService).updateApptStatus("456", "B", "999998");
    }

    @Test
    void shouldNotExposePublicRawArrayListClaimApis() {
        assertThat(BillingClaimSubmissionService.class.getMethods())
                .filteredOn(method -> method.getDeclaringClass().equals(BillingClaimSubmissionService.class))
                .allSatisfy(this::assertDoesNotExposeArrayList);
    }

    private void assertDoesNotExposeArrayList(Method method) {
        assertThat(method.getReturnType())
                .as(method.getName() + " return type")
                .isNotEqualTo(ArrayList.class);
        assertThat(method.getParameterTypes())
                .as(method.getName() + " parameter types")
                .doesNotContain(ArrayList.class);
    }

    private MockHttpServletRequest standardBillingRequest(String billType, String submit) {
        // Keep this fixture close to the legacy form contract: tests mutate a
        // few fields per scenario, but the shared baseline mirrors the request
        // shape the Struts action/service boundary normally receives.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("user", "999998");
        request.setParameter("xml_billtype", billType);
        request.setParameter("hin", "1234567890");
        request.setParameter("ver", "AB");
        request.setParameter("demographic_dob", "1980-01-01");
        request.setParameter("appointment_no", "456");
        request.setParameter("demographic_name", "Doe,Jane");
        request.setParameter("sex", "F");
        request.setParameter("hc_type", "ON");
        request.setParameter("referralCode", "REF123");
        request.setParameter("xml_location", "0000 Clinic");
        request.setParameter("xml_vdate", "2026-04-28");
        request.setParameter("xml_slicode", "SLI");
        request.setParameter("demographic_no", "123");
        request.setParameter("xml_provider", "999998|123456");
        request.setParameter("service_date", "2026-04-28");
        request.setParameter("start_time", "09:00");
        request.setParameter("total", "100.00");
        request.setParameter("total_payment", "5.00");
        request.setParameter("submit", submit);
        request.setParameter("comment", "comment");
        request.setParameter("xml_visittype", "00");
        request.setParameter("apptProvider_no", "999998");
        request.setParameter("site", "site");
        request.setParameter("dxCode", "250");
        request.setParameter("dxCode1", "401");
        request.setParameter("dxCode2", "272");
        return request;
    }

    private MockHttpServletRequest hospitalBillingRequest() {
        // Hospital/ODP billing uses a slightly different field mix than the
        // standard office-billing path, so it gets its own baseline fixture.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("user", "999998");
        request.setParameter("hin", "1234567890AB");
        request.setParameter("demographic_dob", "1980-01-01");
        request.setParameter("xml_billtype", "ODP");
        request.setParameter("hc_type", "ON");
        request.setParameter("payMethod", "P");
        request.setParameter("referralCode", "");
        request.setParameter("xml_location", "0000 Hospital");
        request.setParameter("xml_vdate", "2026-04-28");
        request.setParameter("xml_slicode", "00");
        request.setParameter("demographic_no", "123");
        request.setParameter("xml_provider", "999998|123456");
        request.setParameter("appointment_no", "456");
        request.setParameter("demographic_name", "Doe,Jane");
        request.setParameter("sex", "F");
        request.setParameter("start_time", "09:00");
        request.setParameter("xml_visittype", "00");
        request.setParameter("proOHIPNO", "123456");
        request.setParameter("apptProvider_no", "999998");
        request.setParameter("site", "site");
        request.setParameter("dxCode", "250");
        request.setParameter("dxCode1", "");
        request.setParameter("dxCode2", "");
        request.setParameter("payment", "");
        request.setParameter("refund", "");
        request.setParameter("discount", "");
        return request;
    }
}
