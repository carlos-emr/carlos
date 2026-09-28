/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("APCache lookup parameter resolver")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class ApCacheLookupParameterResolverUnitTest {

    @Test
    @DisplayName("should resolve the form id from the add/edit viewer's plain parameter")
    void shouldResolveFid_fromPlainParameter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("fid", "12");

        assertThat(ApCacheLookupParameterResolver.resolveFid(request)).isEqualTo("12");
    }

    @Test
    @DisplayName("should resolve the form id from the save result view's efm-prefixed parameter")
    void shouldResolveFid_fromEfmPrefixedParameter() {
        // The form action EForm writes is addEForm?efmfid=..&efmdemographic_no=..&efmprovider_no=..,
        // so this is the only spelling a post-save lookup carries.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("efmfid", "12");
        request.setParameter("efmdemographic_no", "1");
        request.setParameter("efmprovider_no", "999998");

        assertThat(ApCacheLookupParameterResolver.resolveFid(request)).isEqualTo("12");
        assertThat(ApCacheLookupParameterResolver.resolveDemographicNo(request)).isEqualTo("1");
    }

    @Test
    @DisplayName("should prefer the plain form id when both spellings are present")
    void shouldPreferPlainFid_whenBothPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("fid", "12");
        request.setParameter("efmfid", "34");

        assertThat(ApCacheLookupParameterResolver.resolveFid(request)).isEqualTo("12");
    }

    @Test
    @DisplayName("should refuse a form id that is not all digits under either spelling")
    void shouldReturnNullFid_whenNotNumeric() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("fid", "12; drop table");
        request.setParameter("efmfid", "");

        assertThat(ApCacheLookupParameterResolver.resolveFid(request)).isNull();
    }

    @Test
    @DisplayName("should fall through to a numeric efm-prefixed form id when the plain one is malformed")
    void shouldResolveFid_fromEfmPrefixedParameterWhenPlainMalformed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("fid", "abc");
        request.setParameter("efmfid", "7");

        assertThat(ApCacheLookupParameterResolver.resolveFid(request)).isEqualTo("7");
    }

    @Test
    @DisplayName("should resolve no patient when neither spelling is present or non-blank")
    void shouldReturnNullDemographicNo_whenAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("fid", "12");
        request.setParameter("demographic_no", "  ");

        assertThat(ApCacheLookupParameterResolver.resolveDemographicNo(request)).isNull();
    }

    @Test
    @DisplayName("should prefer the plain patient parameter when both spellings are present")
    void shouldPreferPlainDemographicNo_whenBothPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographic_no", "1");
        request.setParameter("efmdemographic_no", "2");

        assertThat(ApCacheLookupParameterResolver.resolveDemographicNo(request)).isEqualTo("1");
    }
}
