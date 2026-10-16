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
package io.github.carlos_emr.carlos.encounter.display;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.UnaryOperator;

/**
 * Writes the eChart section of the provider Preferences form to the {@code property} table.
 *
 * <p>Each setting is a Yes/No radio pair posting {@code "true"} or {@code "false"} under the
 * setting's {@link EchartDisplayPreference#propertyName()}. A setting that was not posted at all
 * (an older cached form, a partial post) or that carries any other value is left exactly as stored,
 * so a malformed request can neither switch part of the chart off nor turn Ocean on.</p>
 *
 * <p>Callers own authorization: the only caller, {@code ProviderPropertyAction}, has already
 * checked {@code _pref} write for the logged-in provider and passes that provider's number.</p>
 *
 * @since 2026-10-16
 */
@Service
public class EchartDisplayPreferencePersister {

    private final UserPropertyDAO userPropertyDao;

    public EchartDisplayPreferencePersister(UserPropertyDAO userPropertyDao) {
        this.userPropertyDao = userPropertyDao;
    }

    /**
     * Saves every eChart display setting present in the submission, all or nothing.
     *
     * @param providerNo      the provider whose settings change (the logged-in provider)
     * @param submittedValues reads one submitted form value by parameter name, {@code null} when absent
     * @return how many settings were written
     */
    @Transactional
    public int persist(String providerNo, UnaryOperator<String> submittedValues) {
        int saved = 0;
        for (EchartDisplayPreference preference : EchartDisplayPreference.values()) {
            Boolean value = EchartDisplayPreference.parse(submittedValues.apply(preference.propertyName()));
            if (value == null) {
                continue;
            }
            userPropertyDao.saveProp(providerNo, preference.propertyName(), value.toString());
            saved++;
        }
        return saved;
    }
}
