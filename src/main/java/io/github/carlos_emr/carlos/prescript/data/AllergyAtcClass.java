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
package io.github.carlos_emr.carlos.prescript.data;

import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.commn.model.Allergy;

/**
 * ATC class allergens: allergies recorded against a level of the WHO Anatomical Therapeutic
 * Chemical (ATC) classification rather than against one DrugRef product, and the check of a
 * prescribed drug against them (issue #4576).
 *
 * <p>The Penicillin and Sulfa shortcuts on the allergy pages record such an allergen. Before
 * #4576 they posted the fixed DrugRef search ids 44452 and 44159 as an AHFS class (type 10).
 * Those ids are row numbers of one DrugRef build: in the packaged DrugRef they name a multivitamin
 * brand and a fluoxetine product, so the allergy was filed under another product, and the AHFS
 * class name they relied on can vanish from a Drug Product Database (DPD) extract, because Health
 * Canada deprecated AHFS in 2022. The class is now named by its ATC code, which the DPD assigns to
 * every product and which does not change between DrugRef builds: penicillins are {@code J01C},
 * sulfonamides {@code J01E}. Such an allergy is stored with type {@value #TYPE_CODE} ("ATC
 * Class"), the class code in {@code allergies.atc}, and no DrugRef id.</p>
 *
 * <p>ATC encodes its hierarchy in the code, so a drug is in a class when its code starts with the
 * class code: Amoxil ({@code J01CA04}) and Clavulin ({@code J01CR02}) are under {@code J01C}, and
 * Septra ({@code J01EE01}) is under {@code J01E}. That comparison needs no DrugRef data, so
 * {@link RxDrugData#getAllergyWarnings(String, Allergy[], java.util.List)} makes it in CARLOS
 * instead of relying on DrugRef resolving the class by name. It is only as complete as the
 * classification, though: the DPD files some products that contain a class member under another
 * branch, such as an H. pylori pack containing amoxicillin ({@code A02BD07}), sulfasalazine
 * ({@code A07EC01}) or sulfacetamide eye drops with prednisolone ({@code S01CA02}). For a shortcut
 * allergy DrugRef is therefore also asked about the class under both of its names, the AHFS class
 * the old shortcut used and the ATC class, and a warning from any of the three checks is a
 * warning ({@link #shortcutOf(Allergy)}). When DrugRef resolves neither name, a drug the ATC code
 * places outside the class is reported as checked and clear: on a DPD extract without AHFS that
 * includes the products above. Covering them there would need more class codes per shortcut,
 * which is a clinical decision.</p>
 *
 * <p>Allergies recorded through the old shortcuts keep their stored identity (the clinical
 * record is not rewritten in place), but they are recognised by the exact identity the shortcut
 * wrote, the legacy id with type 10 and the shortcut's own description, and checked as their ATC
 * class. OSCAR posted the same ids, so allergies imported from it are covered too. An amendment
 * of such an allergy is saved in the new form.</p>
 *
 * @since 2026-10-15
 */
public final class AllergyAtcClass {

    /** The allergy type code of an ATC class allergen, shown as "ATC Class" ({@link Allergy#getTypeDesc(int)}). */
    public static final int TYPE_CODE = 8;

    /**
     * The AHFS class type code ("AHFS Class"): the type the old shortcuts recorded their allergies
     * with, and one of the two names a shortcut allergy is checked under in DrugRef.
     */
    public static final int AHFS_TYPE_CODE = 10;

    /**
     * An ATC code a class allergen may be filed under: levels 3 to 5 (for example {@code J01C},
     * {@code J01CA}, {@code J01CA04}). Levels 1 and 2 are refused because a code that broad ("J",
     * "J01": every systemic anti-infective, every antibacterial) would warn on whole branches of
     * prescribing, which is a data error and not an allergy. Upper case only: the DPD and the
     * shortcuts write upper case, and the check is a plain prefix comparison.
     */
    private static final Pattern CLASS_CODE = Pattern.compile("[A-Z][0-9]{2}[A-Z](?:[A-Z](?:[0-9]{2})?)?");

    /** Any well-formed ATC code (levels 1 to 5), which is what a prescribed drug carries. */
    private static final Pattern DRUG_CODE = Pattern.compile("[A-Z](?:[0-9]{2}(?:[A-Z](?:[A-Z](?:[0-9]{2})?)?)?)?");

    private AllergyAtcClass() {
    }

    /**
     * The drug classes the allergy pages offer as one-click shortcuts, each filed under its ATC
     * class. The pages render their buttons' parameters from here, so the class code lives in one
     * place.
     */
    public enum Shortcut {
        /** Penicillins: ATC J01C, beta-lactam antibacterials, penicillins. */
        PENICILLINS("PENICILLINS", "J01C", "44452"),
        /** Sulfonamides: ATC J01E, sulfonamides and trimethoprim. */
        SULFONAMIDES("SULFONAMIDES", "J01E", "44159");

        private final String allergen;
        private final String atcClass;
        private final String legacyDrugrefId;

        Shortcut(String allergen, String atcClass, String legacyDrugrefId) {
            this.allergen = allergen;
            this.atcClass = atcClass;
            this.legacyDrugrefId = legacyDrugrefId;
        }

        /** @return the allergen description the shortcut records, for example {@code PENICILLINS} */
        public String allergen() {
            return allergen;
        }

        /** @return the ATC class code the shortcut files the allergy under, for example {@code J01C} */
        public String atcClass() {
            return atcClass;
        }

        /**
         * The shortcut that recorded an allergy with this identity before #4576, judged on the
         * exact values the old shortcut posted: its fixed DrugRef id, type 10 and its own
         * description. Only that combination is recognised. The id alone names an unrelated
         * product in current DrugRef data, and matching the description alone would be free-text
         * matching.
         *
         * @param drugrefId the allergy's DrugRef id, as stored or posted
         * @param typeCode the allergy's type code; may be null
         * @param description the allergy's description, as stored or posted
         * @return the shortcut, or null when the identity is not one an old shortcut wrote
         */
        public static Shortcut recordedByLegacyShortcut(String drugrefId, Integer typeCode, String description) {
            if (drugrefId == null || typeCode == null || typeCode != AHFS_TYPE_CODE || description == null) {
                return null;
            }
            for (Shortcut shortcut : values()) {
                if (shortcut.legacyDrugrefId.equals(drugrefId.trim()) && shortcut.allergen.equals(description.trim())) {
                    return shortcut;
                }
            }
            return null;
        }
    }

    /**
     * The shortcut an allergy was recorded through: an ATC class allergen whose description and
     * class code are a shortcut's own, or the exact identity an old shortcut wrote
     * ({@link Shortcut#recordedByLegacyShortcut}).
     *
     * @param allergy the allergy; may be null
     * @return the shortcut, or null for any other allergy
     */
    public static Shortcut shortcutOf(Allergy allergy) {
        if (allergy == null) {
            return null;
        }
        Integer typeCode = allergy.getTypeCode();
        if (typeCode != null && typeCode == TYPE_CODE && allergy.getDescription() != null) {
            String code = classCode(allergy.getAtc());
            for (Shortcut shortcut : Shortcut.values()) {
                if (shortcut.atcClass.equals(code) && shortcut.allergen.equals(allergy.getDescription().trim())) {
                    return shortcut;
                }
            }
            return null;
        }
        return Shortcut.recordedByLegacyShortcut(allergy.getDrugrefId(), typeCode, allergy.getDescription());
    }

    /**
     * Normalises a submitted or stored class code.
     *
     * @param raw the code; may be null or padded with whitespace
     * @return the trimmed code when it is an ATC code of level 3 to 5, otherwise null
     */
    public static String classCode(String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.trim();
        return CLASS_CODE.matcher(code).matches() ? code : null;
    }

    /**
     * The ATC class an allergy is checked as, if it is an ATC class allergen: one of type
     * {@value #TYPE_CODE} with a class code stored in its ATC column, or one recorded through an
     * old Penicillin or Sulfa shortcut ({@link Shortcut#recordedByLegacyShortcut}).
     *
     * @param allergy the allergy; may be null
     * @return the class code, or null when the allergy is not an ATC class allergen
     */
    public static String classCodeOf(Allergy allergy) {
        if (allergy == null) {
            return null;
        }
        Integer typeCode = allergy.getTypeCode();
        if (typeCode != null && typeCode == TYPE_CODE) {
            return classCode(allergy.getAtc());
        }
        Shortcut legacy = Shortcut.recordedByLegacyShortcut(allergy.getDrugrefId(), typeCode, allergy.getDescription());
        return legacy == null ? null : legacy.atcClass();
    }

    /**
     * Whether a prescribed drug's code is one the class check can compare.
     *
     * @param drugAtc the drug's ATC code; may be null
     * @return true when the trimmed code is a well-formed ATC code
     */
    public static boolean isDrugCode(String drugAtc) {
        return drugAtc != null && DRUG_CODE.matcher(drugAtc.trim()).matches();
    }

    /**
     * Whether a drug's code places it inside or outside an allergen's ATC class. A code above the
     * class in the same branch ({@code J01} against {@code J01C}) does not: the product may or may
     * not be in the class, so the class check has no answer for it.
     *
     * @param classCode the allergen's class code, as returned by {@link #classCode(String)}
     * @param drugAtc the prescribed drug's ATC code
     * @return true when both codes are well formed and the drug's code is not an ancestor of the class
     */
    public static boolean decides(String classCode, String drugAtc) {
        String allergenClass = classCode(classCode);
        if (allergenClass == null || !isDrugCode(drugAtc)) {
            return false;
        }
        String drug = drugAtc.trim();
        return !(drug.length() < allergenClass.length() && allergenClass.startsWith(drug));
    }

    /**
     * Whether a drug falls in an allergen's ATC class: its code starts with the class code.
     *
     * @param classCode the allergen's class code, as returned by {@link #classCode(String)}
     * @param drugAtc the prescribed drug's ATC code
     * @return true when both codes are well formed and the drug's code is at or under the class
     */
    public static boolean covers(String classCode, String drugAtc) {
        String allergenClass = classCode(classCode);
        return allergenClass != null && isDrugCode(drugAtc) && drugAtc.trim().startsWith(allergenClass);
    }
}
