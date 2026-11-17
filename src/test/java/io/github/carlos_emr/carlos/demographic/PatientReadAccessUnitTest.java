/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic;

import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.demographic.data.DemographicListSearch;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PatientReadAccessUnitTest {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach void setup() {
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(user),anyString(),anyString(),(String)isNull())).thenReturn(true);
        when(security.hasPrivilege(eq(user),anyString(),anyString(),anyString())).thenReturn(true);
        when(security.hasPrivilegeReadOnly(eq(user),anyString(),anyString(),anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(user),anyInt())).thenReturn(true);
        when(security.getSecurityObjects(user)).thenReturn(List.of());
    }

    @ParameterizedTest @ValueSource(strings={"1","2147483647","000001"})
    void acceptsTheFullNativeSignedIntegerRangeAndCanonicalAliases(String value) {
        int patient=Integer.parseInt(value);request.setParameter("demographicNo",value);
        request.setParameter("demographic_no",Integer.toString(patient));
        assertThat(PatientReadAccess.requestedPatient(request,true)).isEqualTo(patient);
    }
    @ParameterizedTest @ValueSource(strings={"-1","2147483648","+1","1.0","1e2","x"," ","0",""})
    void refusesMalformedOrMissingRequiredPatientWithoutAQuery(String value) {
        request.setParameter("demographicNo",value);
        assertThatThrownBy(()->PatientReadAccess.requestedPatient(request,true)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(security);
    }
    @Test void refusesAConflictingRepeatedValueAndEitherConflictingAlias() {
        request.setParameter("demographicNo",new String[]{"7","8"});
        assertThatThrownBy(()->PatientReadAccess.requestedPatient(request,true)).isInstanceOf(IllegalArgumentException.class);
        request.setParameter("demographicNo","8");request.setParameter("demographic_no","7");
        assertThatThrownBy(()->PatientReadAccess.requestedPatient(request,true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void retainsAnUnselectedTicklerFormAndTheAllClientsWorklistWithoutCreatingAPatient() {
        assertThat(PatientReadAccess.requestedPatient(request,false)).isZero();
        request.setParameter("demographicNo","All Clients");
        assertThat(PatientReadAccess.requestedListPatient(request)).isZero();
        assertThatThrownBy(()->PatientReadAccess.requestedPatient(request,false)).isInstanceOf(IllegalArgumentException.class);
        request.setParameter("demographic_no","8");
        assertThatThrownBy(()->PatientReadAccess.requestedListPatient(request)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void checksCanonicalRecordAccessBeforeAnyModulePatientDataPermission() {
        when(security.isAllowedAccessToPatientRecord(user,7)).thenReturn(false);
        assertThatThrownBy(()->PatientReadAccess.requirePatient(security,user,7,"_demographic","r")).isInstanceOf(SecurityException.class);
        verify(security,never()).hasPrivilege(user,"_demographic","r","7");
    }
    @Test void aPatientSpecificModuleDenialStillWinsForAnOtherwiseOpenPatient() {
        when(security.hasPrivilege(user,"_tickler","r","7")).thenReturn(false);
        assertThat(PatientReadAccess.mayReadPatient(security,user,7,"_tickler","r")).isFalse();
        assertThat(PatientReadAccess.mayReadPatient(security,user,8,"_tickler","r")).isTrue();
    }
    @Test void metadataDiscoversCandidatesButCanonicalPriorityDecidesTheOutcome() {
        when(security.getSecurityObjects(user)).thenReturn(List.of(
            new Secobjprivilege("999998","_demographic$7","|o|",0,"999998"),
            new Secobjprivilege("doctor","_eChart$8","|o|",0,"999998"),
            new Secobjprivilege("doctor","_tickler$9","|r|",0,"999998"),
            new Secobjprivilege("doctor","_rx$10","|o|",0,"999998")));
        when(security.isAllowedAccessToPatientRecord(user,7)).thenReturn(false);
        when(security.hasPrivilegeReadOnly(user,"_tickler","r","9")).thenReturn(false);
        assertThat(PatientReadAccess.deniedPatients(security,user,"_tickler","r")).containsExactlyInAnyOrder(7,9);
        verify(security).isAllowedAccessToPatientRecord(user,9);
        verify(security).hasPrivilegeReadOnly(user,"_tickler","r","9");
        verify(security,never()).hasPrivilegeReadOnly(user,"_tickler","r","7");
        verify(security,never()).hasPrivilege(eq(user),anyString(),anyString(),anyString());
        verify(security).isAllowedAccessToPatientRecord(user,8);
        verify(security,never()).isAllowedAccessToPatientRecord(user,10);
    }
    @Test void duplicateRoleRowsCauseOnlyOneCanonicalPatientDecisionAndMaximumIdIsSupported() {
        when(security.getSecurityObjects(user)).thenReturn(List.of(
            new Secobjprivilege("doctor","_eChart$2147483647","|o|",0,"999998"),
            new Secobjprivilege("999998","_demographic$2147483647","|o|",0,"999998")));
        when(security.isAllowedAccessToPatientRecord(user,Integer.MAX_VALUE)).thenReturn(false);
        assertThat(PatientReadAccess.deniedPatients(security,user,"_demographic","r")).containsExactly(Integer.MAX_VALUE);
        verify(security,times(1)).isAllowedAccessToPatientRecord(user,Integer.MAX_VALUE);
    }
    @Test void missingSecurityMetadataFailsClosedRatherThanBecomingAnEmptyDenyList() {
        when(security.getSecurityObjects(user)).thenReturn(null);
        assertThatThrownBy(()->PatientReadAccess.deniedPatients(security,user,"_demographic","r")).isInstanceOf(SecurityException.class);
    }
    @Test void requestCacheDoesNotCrossSecurityObjectsPrivilegesOrProviders() {
        PatientReadAccess.deniedPatientsFromRequest(request,security,user,"_demographic","r");
        PatientReadAccess.deniedPatientsFromRequest(request,security,user,"_demographic","r");
        verify(security,times(1)).getSecurityObjects(user);
        PatientReadAccess.deniedPatientsFromRequest(request,security,user,"_tickler","r");
        PatientReadAccess.deniedPatientsFromRequest(request,security,user,"_tickler","w");
        when(user.getLoggedInProviderNo()).thenReturn("999999");
        PatientReadAccess.deniedPatientsFromRequest(request,security,user,"_tickler","w");
        verify(security,times(4)).getSecurityObjects(user);
    }
    @Test void filtersCannotSerializeOrAcceptClientSuppliedPatientExclusions() throws Exception {
        CustomFilter filter=new CustomFilter();filter.setExcludedDemographicNos(Set.of(7));
        ObjectMapper mapper=new ObjectMapper();
        assertThat(mapper.writeValueAsString(filter)).doesNotContain("excludedDemographicNos");
        CustomFilter client=mapper.readValue("{\"excludedDemographicNos\":[8]}",CustomFilter.class);
        assertThat(client.getExcludedDemographicNos()).isEmpty();
        assertThatThrownBy(()->filter.getExcludedDemographicNos().add(8)).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void oldSearchConstructorRemainsCompatibleAndServerExclusionsAreImmutable() {
        var old=new DemographicListSearch("search_name","FAKE","last_name",null,false,0,1);
        assertThat(old.deniedPatients()).isEmpty();
        var limited=new DemographicListSearch("search_name","FAKE","last_name",null,false,0,1,Set.of(7));
        assertThat(limited.deniedPatients()).containsExactly(7);
        assertThatThrownBy(()->limited.deniedPatients().add(8)).isInstanceOf(UnsupportedOperationException.class);
    }
    @ParameterizedTest @ValueSource(strings={"","0"})
    void neutralNativeViewDoesNotAttemptPatientAuthorizationOrLookup(String selection) {
        request.setParameter("demographic_no",selection);
        assertThat(PatientReadAccess.authorizedOptionalPatient(request,security,user,"_tickler","w")).isNull();
        verifyNoInteractions(security);
    }
    @Test void optionalNativeForwardStillChecksTheActualPatientAndCanonicalizesItsIdentity() {
        request.setParameter("demographic_no","000008");
        assertThat(PatientReadAccess.authorizedOptionalPatient(request,security,user,"_tickler","w")).isEqualTo("8");
        request.setParameter("demographic_no","7");when(security.isAllowedAccessToPatientRecord(user,7)).thenReturn(false);
        assertThatThrownBy(()->PatientReadAccess.authorizedOptionalPatient(request,security,user,"_tickler","w")).isInstanceOf(SecurityException.class);
    }

}
