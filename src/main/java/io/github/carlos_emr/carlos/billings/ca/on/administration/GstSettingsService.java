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
package io.github.carlos_emr.carlos.billings.ca.on.administration;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.billing.CA.dao.GstControlDao;
import io.github.carlos_emr.carlos.billing.CA.model.GstControl;

/**
 * Typed read/write for the single-row GST control table. Extracted from
 * {@link GstControl2Action} so non-action callers (assemblers, managers)
 * can read the configured GST percent without instantiating a Struts
 * action class.
 *
 * <p>The legacy {@code Properties}-bag surface ({@code readDatabase()} +
 * {@code writeDatabase(String)}) was retired on 2026-04-27 in favour of
 * {@link BigDecimal} accessors so callers don't string-fumble a numeric
 * setting at every site.</p>
 *
 * @since 2026-04-27
 */
@Service
public class GstSettingsService {

    private final GstControlDao dao;

    public GstSettingsService(GstControlDao dao) {
        this.dao = dao;
    }

    /**
     * @return the configured GST percent (e.g. {@code 5.00} for 5%), or
     *         {@code null} if the {@code billing_gst} table is empty.
     */
    public BigDecimal getCurrentPercent() {
        for (GstControl g : dao.findAll()) {
            return g.getGstPercent();
        }
        return null;
    }

    /** Persists {@code percent} as the new GST percent for every existing row. */
    public void setCurrentPercent(BigDecimal percent) {
        for (GstControl g : dao.findAll()) {
            g.setGstPercent(percent);
            dao.merge(g);
        }
    }
}
