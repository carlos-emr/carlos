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
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises real signed request preparation, saved snapshots, contract refusal and admin scoping. */
@Tag("unit")
class PortalEmailFooterAuditUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 9);
    private static final String ID = "01791547200000000000-00000000000000000000000000000001";
    private static final String OLDER = "01791547199000000000-00000000000000000000000000000002";
    private static final String TOKEN = "FAKE-portal-service-token-value-000001";
    private static final String TEXT = "FAKE Original Clinic <script>alert('literal')</script>";

    private ObjectNode page() {
        ObjectNode page = JSON.createObjectNode();page.put("date", DATE.toString());page.putNull("next_before");
        ObjectNode a = page.putArray("attempts").addObject();
        a.put("attempt_id", ID);a.put("kind", "mfa");a.put("prepared_at", "2026-10-09T01:00:00.123456Z");
        a.put("status", "prepared");a.putNull("status_at");a.put("clinic_id", "clinic-a");
        a.put("revision", "a".repeat(64));a.put("footer_text", TEXT);a.putNull("logo_sha256");
        return page;
    }
    private ObjectNode attempt(ObjectNode page) { return (ObjectNode)page.get("attempts").get(0); }
    private PortalEmailFooterAuditPage parse(ObjectNode node) {
        return PortalEmailFooterAuditPage.fromJson(node, DATE, 50, null, "clinic-a");
    }
    private PatientPortalStaffContext staff() {
        return new PatientPortalStaffContext("999998", "Dr FAKE", Set.of(PatientPortalStaffContext.PERMISSION_EMAIL_AUDIT_READ));
    }
    private PatientPortalService service(PatientPortalHttpExchange exchange) {
        return new PatientPortalService(PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, "https://portal.clinic.example",
                PatientPortalSettings.CLINIC_ID_KEY, "clinic-a",
                PatientPortalSettings.SERVICE_TOKEN_KEY, TOKEN,
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "primary",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN)), exchange);
    }
    @Test void shouldKeepSavedPlaintext_andTruthfulPreparedOutcome_withoutCurrentFooter() {
        var saved = parse(page());
        assertThat(saved.attempts().get(0).footerText()).isEqualTo(TEXT);
        assertThat(saved.attempts().get(0).status()).isEqualTo("prepared");
        assertThat(saved.attempts().get(0).statusAt()).isNull();
        assertThat(saved.toString()).doesNotContain(TEXT);
        assertThat(saved.attempts().get(0).toString()).doesNotContain(TEXT);
        assertThatThrownBy(() -> saved.attempts().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void shouldAcceptAllSevenProductionKinds_andSeparateRecordedOutcomes() {
        for (String kind : List.of("mfa", "password_reset", "contact_change", "email_change_confirmation",
                "email_change_requested", "booking_prompt", "booking_prompt_update")) {
            for (String status : List.of("accepted", "failed", "unknown")) {
                var p = page();var a = attempt(p);a.put("kind",kind);a.put("status",status);
                a.put("status_at","2026-10-09T01:00:01.123456Z");a.put("logo_sha256","b".repeat(64));
                assertThat(parse(p).attempts().get(0).status()).isEqualTo(status);
            }
        }
    }
    @Test void shouldPreserveEarlierRecordedOutcome_afterWallClockCorrection() {
        var p=page();var a=attempt(p);a.put("status","accepted");
        a.put("status_at","2026-10-09T00:59:59.123456Z");
        var saved=parse(p).attempts().get(0);
        assertThat(saved.statusAt()).isBefore(saved.preparedAt());
        assertThat(saved.status()).isEqualTo("accepted");
        assertThat(saved.footerText()).isEqualTo(TEXT);
    }
    @Test void shouldRefuseInvalidSavedAttemptContract() {
        List<Consumer<ObjectNode>> invalid = List.of(
                a->a.put("clinic_id","other-clinic"),a->a.put("attempt_id","../file"),
                a->a.put("attempt_id","01791547200000000000-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                a->a.put("kind","arbitrary"),a->a.put("status","sent"),
                a->a.put("prepared_at","2026-10-09T01:00:00+00:00"),
                a->a.put("prepared_at","2026-10-08T01:00:00Z"),a->a.put("status_at","2026-10-09T01:00:01Z"),
                a->a.put("status","accepted"),a->a.put("revision","f".repeat(63)),
                a->a.put("logo_sha256","bad"),a->a.put("footer_text","x".repeat(2001)),
                a->a.put("footer_text","\u200b"),a->a.put("footer_text",String.valueOf((char)0xd800)),
                a->a.put("recipient","fake@example.test"),a->a.remove("logo_sha256"));
        for (Consumer<ObjectNode> mutation : invalid) {
            var p=page();mutation.accept(attempt(p));
            assertThatThrownBy(()->parse(p)).isInstanceOf(PortalContractException.class);
        }
    }
    @Test void shouldRefuseWrongDatesOrderCursorAndExtraPageData() {
        var wrongDate=page();wrongDate.put("date","2026-10-08");assertThatThrownBy(()->parse(wrongDate)).isInstanceOf(PortalContractException.class);
        var wrongNext=page();wrongNext.put("next_before",OLDER);
        assertThatThrownBy(()->parse(wrongNext)).isInstanceOf(PortalContractException.class);
        var duplicate=page();((com.fasterxml.jackson.databind.node.ArrayNode)duplicate.get("attempts")).add(attempt(duplicate).deepCopy());
        assertThatThrownBy(()->parse(duplicate)).isInstanceOf(PortalContractException.class);
        assertThatThrownBy(()->PortalEmailFooterAuditPage.fromJson(page(),DATE,50,ID,"clinic-a")).isInstanceOf(PortalContractException.class);
        var extra=page();extra.put("html","<b>untrusted</b>");
        assertThatThrownBy(()->parse(extra)).isInstanceOf(PortalContractException.class);
        var tooMany=page();assertThatThrownBy(()->PortalEmailFooterAuditPage.fromJson(tooMany,DATE,0,null,"clinic-a")).isInstanceOf(PortalContractException.class);
    }
    @Test void shouldSendOnlySignedAdminRead_andBindExactValidatedQuery() throws Exception {
        AtomicReference<ClassicHttpRequest> request = new AtomicReference<>();
        var svc = service(r->{request.set(r);return new PatientPortalHttpResponse(200,page().toString());});
        var result=svc.listEmailFooterAttempts(DATE,50,null,staff());
        assertThat(result.attempts()).hasSize(1);
        var first=request.get();assertThat(first.getMethod()).isEqualTo("GET");
        assertThat(first.getRequestUri()).isEqualTo("/internal/carlos/email-footer-attempts?date=2026-10-09&limit=50");
        assertThat(first.getFirstHeader("Authorization").getValue()).isEqualTo("Bearer "+TOKEN);
        String assertion=first.getFirstHeader(PortalStaffAssertionSigner.HEADER).getValue();
        var claims=JSON.readTree(Base64.getUrlDecoder().decode(assertion.split("\\.")[0]));
        assertThat(claims.get("permissions").toString()).isEqualTo("[\"portal.email.audit.read\"]");
        assertThat(first.getEntity()).isNull();
        var response=page();response.putArray("attempts");
        var secondService=service(r->{request.set(r);return new PatientPortalHttpResponse(200,response.toString());});
        secondService.listEmailFooterAttempts(DATE,50,ID,staff());
        var secondClaims=JSON.readTree(Base64.getUrlDecoder().decode(request.get().getFirstHeader(PortalStaffAssertionSigner.HEADER).getValue().split("\\.")[0]));
        assertThat(secondClaims.get("request_hash")).isNotEqualTo(claims.get("request_hash"));
    }
    @Test void shouldRefuseForgedQueryOrOtherPermissions_beforeTransport() {
        AtomicInteger calls=new AtomicInteger();var svc=service(r->{calls.incrementAndGet();return new PatientPortalHttpResponse(200,"{}");});
        assertThatThrownBy(()->svc.listEmailFooterAttempts(DATE,50,"../../other",staff())).isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(()->svc.listEmailFooterAttempts(DATE,101,null,staff())).isInstanceOf(PortalRequestPreparationException.class);
        var other=new PatientPortalStaffContext("999998","Dr FAKE",Set.of(PatientPortalStaffContext.PERMISSION_INVITE_MANAGE));
        assertThatThrownBy(()->svc.listEmailFooterAttempts(DATE,50,null,other)).isInstanceOf(PortalRequestPreparationException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void shouldRefuseOversizeUtf8DuplicateJsonAndWrongSuccessStatus() {
        for (var response : List.of(new PatientPortalHttpResponse(200,"é".repeat(270000)),
                new PatientPortalHttpResponse(200,"{\"date\":\"2026-10-09\",\"date\":\"2026-10-08\"}"),
                new PatientPortalHttpResponse(201,page().toString()))) {
            assertThatThrownBy(()->service(r->response).listEmailFooterAttempts(DATE,50,null,staff()))
                    .isInstanceOf(PatientPortalException.class)
                    .satisfies(e->assertThat(((PatientPortalException)e).kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE));
        }
    }
    @Test void shouldRequireActualAdminRead_beforeClientLookup_andAttestOnlyAudit() {
        SecurityInfoManager security=mock(SecurityInfoManager.class);LoggedInInfo user=mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        AtomicInteger lookups=new AtomicInteger();AtomicReference<PatientPortalStaffContext> scope=new AtomicReference<>();
        var client=mock(PatientPortalService.class);
        when(client.listEmailFooterAttempts(eq(DATE),eq(50),isNull(),any())).thenAnswer(i->{scope.set(i.getArgument(3));return parse(page());});
        var reader=new PortalEmailFooterAuditService(security,()->{lookups.incrementAndGet();return client;});
        assertThatThrownBy(()->reader.read(user,DATE,null)).isInstanceOf(SecurityException.class);
        assertThat(lookups).hasValue(0);verifyNoInteractions(client);
        when(security.hasPrivilege(user,"_admin",SecurityInfoManager.READ,null)).thenReturn(true);
        assertThat(reader.read(user,DATE,null).attempts()).hasSize(1);
        assertThat(scope.get().permissions()).containsExactly(PatientPortalStaffContext.PERMISSION_EMAIL_AUDIT_READ);
        verify(security,never()).hasPrivilege(eq(user),eq("_email"),anyString(),nullable(String.class));
    }
}
