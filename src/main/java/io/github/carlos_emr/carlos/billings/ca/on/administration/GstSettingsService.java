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
package io.github.carlos_emr.carlos.billings.ca.on.administration;

import java.math.BigDecimal;
import java.util.Properties;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.billing.CA.dao.GstControlDao;
import io.github.carlos_emr.carlos.billing.CA.model.GstControl;

/**
 * Read/write helper for the single-row GST control table. Extracted from
 * {@link GstControl2Action} so non-action callers (assemblers, managers)
 * can read the configured GST percent without instantiating a Struts
 * action class.
 *
 * @since 2026-04-27
 */
@Service
public class GstSettingsService {

    private final GstControlDao dao;

    public GstSettingsService(GstControlDao dao) {
        this.dao = dao;
    }

    public Properties readDatabase() {
        Properties props = new Properties();
        for (GstControl g : dao.findAll()) {
            props.setProperty("gstPercent", g.getGstPercent().toString());
        }
        return props;
    }

    public void writeDatabase(String percent) {
        for (GstControl g : dao.findAll()) {
            g.setGstPercent(BigDecimal.valueOf(Double.valueOf(percent)));
            dao.merge(g);
        }
    }
}
