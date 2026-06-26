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

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Atomically soft-deletes a CSS style and nulls
 * {@code billing_service.display_style} on every referencing code under a
 * single transaction so a mid-cascade failure leaves both tables consistent.
 *
 * @since 2026-04-30
 */
@Service
@Transactional
public class CssStyleDeletionService {

    private final CSSStylesDAO cssStylesDao;
    private final BillingServiceDao billingServiceDao;

    public CssStyleDeletionService(CSSStylesDAO cssStylesDao, BillingServiceDao billingServiceDao) {
        this.cssStylesDao = cssStylesDao;
        this.billingServiceDao = billingServiceDao;
    }

    /**
     * Soft-delete the CSS style identified by {@code styleId} and null the
     * {@code display_style} column on every {@link BillingService} that
     * referenced it. Returns {@code true} when a row was found and deleted;
     * {@code false} when no css_styles row matches {@code styleId}.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public boolean deleteByStyleId(String styleId) {
        if (styleId == null) {
            return false;
        }
        List<CssStyle> styles = cssStylesDao.findAll();
        for (CssStyle current : styles) {
            if (styleId.equalsIgnoreCase(current.getStyle())) {
                current.setStatus(CssStyle.DELETED);
                cssStylesDao.merge(current);

                List<BillingService> codes = billingServiceDao.findBillingCodesByFontStyle(current.getId());
                for (BillingService code : codes) {
                    code.setDisplayStyle(null);
                    billingServiceDao.merge(code);
                }
                return true;
            }
        }
        return false;
    }
}
