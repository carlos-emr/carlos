/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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

/**
 * Atomically soft-deletes a CSS style and clears every BillingService that
 * referenced it. Pre-fix the cascade ran inline in
 * {@code ManageCss2Action.delete()} with no @Transactional boundary — a
 * mid-cascade DAO failure left the css_styles row marked DELETED while the
 * billing_service.display_style column was only partially nulled.
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
