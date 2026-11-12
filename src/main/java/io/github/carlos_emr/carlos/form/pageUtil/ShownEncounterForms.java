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
package io.github.carlos_emr.carlos.form.pageUtil;

import java.util.Comparator;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;

/**
 * The encounter forms shown in the eChart's form menu, as Administration ▸ Select Forms lists and orders them.
 *
 * <p>A form's display order ({@code encounterForm.hidden}) is 0 when it is hidden and otherwise its place in the menu.
 * Seeds and imports can give several forms the same place (the BC seed puts four forms at 1). Select Forms keyed its
 * list by that number, so all but one of them disappeared from the page, and its Hide, Up and Down acted on every form
 * sharing the number (issue #4945). Here every shown form is listed, ties in name order, and the places can be made
 * distinct before Select Forms changes them.</p>
 *
 * @since 2026-10-18
 */
public final class ShownEncounterForms {

    private static final Comparator<EncounterForm> MENU_ORDER = Comparator.comparingInt(EncounterForm::getDisplayOrder)
            .thenComparing(EncounterForm::getFormName, Comparator.nullsLast(Comparator.naturalOrder()));

    private ShownEncounterForms() {
    }

    /**
     * @param forms every encounter form
     * @return the shown forms (display order above 0, not a removed CAISI form), by place and then by name
     */
    public static List<EncounterForm> inDisplayOrder(List<EncounterForm> forms) {
        return forms.stream()
                .filter(form -> !form.isHidden() && !EctFormData.isRemovedCaisiForm(form.getFormName()))
                .sorted(MENU_ORDER)
                .toList();
    }

    /**
     * Gives the shown forms the places 1..n in the order {@link #inDisplayOrder} lists them, so each form has a place
     * of its own, and hides the removed CAISI forms (no page lists them, so a place they hold would be shared with a
     * form that is listed). Only forms whose place changes are saved.
     *
     * @param encounterFormDao the encounter form DAO
     */
    public static void renumber(EncounterFormDao encounterFormDao) {
        List<EncounterForm> forms = encounterFormDao.findAll();
        int place = 0;
        for (EncounterForm form : inDisplayOrder(forms)) {
            place++;
            if (form.getDisplayOrder() != place) {
                form.setDisplayOrder(place);
                encounterFormDao.merge(form);
            }
        }
        for (EncounterForm form : forms) {
            if (!form.isHidden() && EctFormData.isRemovedCaisiForm(form.getFormName())) {
                form.setDisplayOrder(0);
                encounterFormDao.merge(form);
            }
        }
    }
}
