/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by Magenta Health in 2024.
 * Modifications by CARLOS Contributors, 2026.
 */


package io.github.carlos_emr.carlos.prevention;

import org.springframework.stereotype.Component;

/**
 * Spring service interface for the CARLOS EMR immunization and prevention decision support engine.
 *
 * <p>Defines the contract for loading Drools rule bases that encode immunization schedules
 * and prevention guidelines, and for evaluating those rules against a patient's
 * {@link Prevention} fact object to produce clinical warnings and reminders.</p>
 *
 * <p>The default implementation, {@link PreventionDSImpl}, supports a three-tier rule loading
 * strategy (filesystem, database {@link io.github.carlos_emr.carlos.commn.model.ResourceStorage},
 * and classpath fallback) and uses the KIE API (Drools 7.74.1) for rule evaluation.</p>
 *
 * <h3>Usage</h3>
 * <p>Inject this interface via Spring and call {@link #getMessages(Prevention)} with a
 * {@code Prevention} object populated with patient demographics and immunization history.
 * The returned object will contain any applicable warnings and reminders determined by the
 * currently loaded prevention rules.</p>
 *
 * @since 2001-2002 (McMaster University); migrated to KIE API 2026-01-06
 * @see PreventionDSImpl
 * @see Prevention
 * @see io.github.carlos_emr.carlos.drools.DroolsHelper
 * @see io.github.carlos_emr.carlos.decisionSupport.prevention.DSPreventionDrools
 * @see org.kie.api.KieBase
 * @see org.kie.api.runtime.KieSession
 */
@Component
public interface PreventionDS {


    /**
     * Forces a reload of the prevention Drools rule base from its configured source.
     *
     * <p>Re-executes the three-tier loading strategy (filesystem property path, database
     * {@link io.github.carlos_emr.carlos.commn.model.ResourceStorage}, classpath fallback)
     * and replaces the cached {@link org.kie.api.KieBase} with the freshly compiled rules.
     * This allows administrators to update prevention guidelines at runtime without
     * restarting the application.</p>
     *
     * @see PreventionDSImpl#reloadRuleBase()
     */
    public void reloadRuleBase();


    /**
     * Evaluates the loaded prevention rules against the given patient prevention data and
     * returns the enriched {@link Prevention} object with clinical decision support messages.
     *
     * <p>Creates a new {@link org.kie.api.runtime.KieSession} from the cached
     * {@link org.kie.api.KieBase}, inserts the {@code Prevention} fact, fires all matching
     * rules, and then disposes of the session. After execution, the returned
     * {@code Prevention} object will contain any warnings (via
     * {@link Prevention#getWarnings()}) and reminders (via {@link Prevention#getReminder()})
     * triggered by the rules.</p>
     *
     * @param p Prevention the patient prevention fact object containing demographics (sex,
     *          date of birth) and immunization history ({@link PreventionItem} entries);
     *          must not be {@code null}
     * @return Prevention the same object passed in, now enriched with any warning and
     *         reminder messages added by the fired Drools rules
     * @throws Exception if the KieSession cannot be created or rule execution fails
     *                   (wraps the underlying Drools exception with an "ERROR: Drools" message)
     * @see Prevention#getWarnings()
     * @see Prevention#getReminder()
     */
    public Prevention getMessages(Prevention p) throws Exception;

}
 