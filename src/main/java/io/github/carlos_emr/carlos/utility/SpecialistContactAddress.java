// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import java.util.Arrays;
import java.util.Objects;

/** The legacy specialist writer's five fields; ambiguous free text stays intact. */
public record SpecialistContactAddress(String street, String city, String postal, String province, String country) {
    public static SpecialistContactAddress parse(String original) {
        String raw = Objects.toString(original, "");
        String[] fields = raw.split(",", -1);
        if (fields.length == 5 || (fields.length == 6 && fields[5].isBlank())) {
            return new SpecialistContactAddress(fields[0].trim(), fields[1].trim(), fields[2].trim(),
                    fields[3].trim(), fields[4].trim());
        }
        return new SpecialistContactAddress(raw, "", "", "", "");
    }

    public static SpecialistContactAddress from(ProfessionalContact contact) {
        String street = Objects.toString(contact.getAddress(), "");
        if (!text(contact.getAddress2()).isEmpty()) street = (street + " " + text(contact.getAddress2())).trim();
        return new SpecialistContactAddress(street,
                text(contact.getCity()), text(contact.getPostal()), text(contact.getProvince()), text(contact.getCountry()));
    }

    /** Keeps the original string byte-for-byte when the address components were not edited. */
    public static String update(String original, ProfessionalContact submitted) {
        SpecialistContactAddress changed = from(submitted);
        if (parse(original).normalisedLines().equals(changed.normalisedLines())) return original;
        // A street-only edit remains free text, including commas and newlines.
        if (changed.city.isEmpty() && changed.postal.isEmpty() && changed.province.isEmpty() && changed.country.isEmpty()) {
            return changed.street;
        }
        String[] fields = {changed.street, changed.city, changed.postal, changed.province, changed.country};
        if (Arrays.stream(fields).anyMatch(value -> value.contains(","))) {
            throw new IllegalArgumentException("Address components cannot contain commas");
        }
        return String.join(", ", fields) + ", ";
    }

    private static String text(String value) { return Objects.toString(value, "").trim(); }

    private SpecialistContactAddress normalisedLines() {
        return new SpecialistContactAddress(street.replace("\r\n", "\n").replace('\r', '\n'), city, postal, province, country);
    }
}
