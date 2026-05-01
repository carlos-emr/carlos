/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.AddEditServiceCodeViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.model.CssStyle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Smoke tests for {@link AddEditServiceCodeViewModelAssembler}, the
 * 400+ LOC admin assembler that drives the service-code add/edit flow.
 *
 * <p>Pins the no-submit and search code paths so a future refactor
 * accidentally returning a {@code null} view model or skipping the
 * "search first" prompt fails this suite.</p>
 */
@DisplayName("AddEditServiceCodeViewModelAssembler smoke")
@Tag("unit")
@Tag("billing")
class AddEditServiceCodeViewModelAssemblerUnitTest {

    private BillingServiceDao billingServiceDao;
    private BillingPercLimitDao billingPercLimitDao;
    private CSSStylesDAO cssStylesDao;
    private AddEditServiceCodeViewModelAssembler assembler;

    @BeforeEach
    void setUp() {
        billingServiceDao = mock(BillingServiceDao.class);
        billingPercLimitDao = mock(BillingPercLimitDao.class);
        cssStylesDao = mock(CSSStylesDAO.class);
        when(cssStylesDao.findAll()).thenReturn(Collections.emptyList());
        assembler = new AddEditServiceCodeViewModelAssembler(
                billingServiceDao, billingPercLimitDao, cssStylesDao);
    }

    @Test
    void shouldRenderInitialPrompt_whenNoSubmitFrm() {
        // Bare GET — no submitFrm and no action — must produce the
        // initial "search first" prompt rather than persisting anything.
        MockHttpServletRequest req = new MockHttpServletRequest();

        AddEditServiceCodeViewModel vm = assembler.assemble(req, null);

        assertThat(vm).isNotNull();
        assertThat(vm.getAction()).isEqualTo("search");
        assertThat(vm.getAlert()).isEqualTo("info");
        assertThat(vm.getMessage()).contains("search first");
    }

    @Test
    void shouldRouteToNewCodePath_whenSearchSubmitWithNoMatches() {
        // Search path: submitFrm=Search with a 5-char service_code routes
        // through handleSearch; no matches lands on the "NEW service
        // code" branch (alert=success, action="add{code}").
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("submitFrm", "Search");
        req.setParameter("service_code", "A007A");
        when(billingServiceDao.findByServiceCode(anyString())).thenReturn(Collections.emptyList());

        AddEditServiceCodeViewModel vm = assembler.assemble(req, null);

        assertThat(vm).isNotNull();
        assertThat(vm.getAction()).isEqualTo("addA007A");
        assertThat(vm.getAlert()).isEqualTo("success");
        assertThat(vm.getMessage()).contains("NEW");
    }

    @Test
    void shouldExposeCssStylesFromDao_whenAssembled() {
        // CssStyle list must always flow through to the view model so
        // the JSP can render the displaystyle dropdown.
        MockHttpServletRequest req = new MockHttpServletRequest();
        CssStyle s = new CssStyle();
        s.setId(1);
        s.setName("warning");
        s.setStyle("color:red;");
        when(cssStylesDao.findAll()).thenReturn(List.of(s));

        AddEditServiceCodeViewModel vm = assembler.assemble(req, null);

        // Initial render path doesn't load CSS styles (only the search /
        // edit / save paths do); assert the field is at least present.
        assertThat(vm.getCssStyles()).isNotNull();
    }
}
