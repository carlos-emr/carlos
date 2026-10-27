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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsContactDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPhoneComponents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Streams repeated fields once; every source purpose/phone remains in its protected category. */
@Service
public class CdsContactNormalizationService {
    public static final String MULTIPLE_PURPOSES = "Multiple source purposes; see imported Contact details";
    private static final Map<String, String> ROLES = Map.of("NK", "Next of Kin", "AS", "Administrative Staff",
            "CG", "Care Giver", "PA", "Power of Attorney", "IN", "Insurance", "GT", "Guarantor");
    private static final java.util.regex.Pattern PHONE_SCOPE = java.util.regex.Pattern.compile("PhoneNumber\\[[0-9]+\\]");
    private final CdsFieldParser fields;
    public CdsContactNormalizationService(CdsFieldParser fields) { this.fields = fields; }

    public CdsContactDto normalize(CdsRecordDto record) throws IOException {
        if (!"Contact".equals(record.section())) throw new IllegalArgumentException("Wrong CDS contact section");
        class State {
            final Map<String, CdsContactDto.Phone> phones = new LinkedHashMap<>();
            final Set<String> unprojectableSlots = new LinkedHashSet<>();
            final Map<String, String> phone = new LinkedHashMap<>();
            final Map<String, CdsClinicalDiagnosticDto> diagnostics = new LinkedHashMap<>();
            String scope, first, middle, last, email, note, role;
            Boolean emergency, substitute;
            boolean roleSeen, multipleRoles, phoneUnavailable;
            void notice(String code, String key, String sourcePath) {
                diagnostics.putIfAbsent(code + "/" + key, new CdsClinicalDiagnosticDto(code, sourcePath));
            }
            void finishPhone() {
                if (scope == null) return;
                String type = phone.get("@phoneNumberType");
                if (!Set.of("R", "C", "W").contains(type == null ? "" : type)) {
                    throw new CdsClinicalException("CDS_CONTACT_PHONE_TYPE_REQUIRED", "PhoneNumber");
                }
                String number = phone.get("phoneNumber");
                if (number != null && (phone.containsKey("areaCode") || phone.containsKey("number"))) {
                    throw new CdsClinicalException("CDS_CONTACT_PHONE_CHOICE_CONFLICT", "PhoneNumber");
                }
                if (!phoneUnavailable && number == null) {
                    if (!phone.containsKey("areaCode") || !phone.containsKey("number")) {
                        throw new CdsClinicalException("CDS_CONTACT_PHONE_REQUIRED", "PhoneNumber");
                    }
                    number = CdsPhoneComponents.combine(phone.get("areaCode"), phone.get("exchange"), phone.get("number"));
                    if (number == null) phoneUnavailable = true;
                    else notice("CDS_PHONE_COMPONENTS_COMBINED", type, scope);
                }
                if (number != null && number.codePointCount(0, number.length()) > 30) phoneUnavailable = true;
                if (phoneUnavailable) {
                    phones.remove(type); unprojectableSlots.add(type);
                    notice("CDS_CONTACT_PHONE_SOURCE_VIEW", type, scope);
                } else if (!unprojectableSlots.contains(type)) {
                    var candidate = new CdsContactDto.Phone(number, phone.get("extension"));
                    var prior = phones.putIfAbsent(type, candidate);
                    if (prior != null && !prior.equals(candidate)) {
                        phones.remove(type); unprojectableSlots.add(type);
                        notice("CDS_CONTACT_AMBIGUOUS_PHONE_SOURCE_VIEW", type, scope);
                    }
                }
                scope = null; phone.clear(); phoneUnavailable = false;
            }
            void field(CdsFieldDto field) throws IOException {
                if (!field.namespace().equals("cds") && !field.namespace().equals("cds_dt")
                        && !(field.kind().equals("ATTRIBUTE") && field.namespace().isEmpty())) return;
                if (field.kind().equals("BINARY")) return;
                int slash = field.path().indexOf('/');
                String top = slash < 0 ? field.path() : field.path().substring(0, slash);
                if (PHONE_SCOPE.matcher(top).matches()) {
                    if (!top.equals(scope)) { finishPhone(); scope = top; }
                    String leaf = slash < 0 ? "" : CdsFieldParser.unindexed(field.path().substring(slash + 1));
                    int capacity = switch (leaf) {
                        case "@phoneNumberType" -> 1;
                        case "extension" -> 5;
                        case "phoneNumber" -> 25;
                        case "areaCode", "number", "exchange" -> 30;
                        default -> -1;
                    };
                    if (capacity >= 0) {
                        String actual;
                        try { actual = fields.readToken(record, field, capacity); }
                        catch (CdsInputException over) {
                            if (!over.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED") || leaf.equals("@phoneNumberType")) throw over;
                            phoneUnavailable = true; return;
                        }
                        String previous = phone.putIfAbsent(leaf, actual);
                        if (previous != null && !previous.equals(actual)) {
                            throw new CdsClinicalException("CDS_CONTACT_PHONE_VALUE_CONFLICT", "PhoneNumber");
                        }
                    }
                    return;
                }
                finishPhone();
                String path = CdsFieldParser.unindexed(field.path());
                switch (path) {
                    case "Name/FirstName" -> first = merge(first, fields.readLogicalText(record, field, 50), path);
                    case "Name/MiddleName" -> middle = merge(middle, fields.readLogicalText(record, field, 50), path);
                    case "Name/LastName" -> last = merge(last, fields.readLogicalText(record, field, 50), path);
                    case "EmailAddress" -> email = merge(email, fields.readToken(record, field, 50), path);
                    case "Note" -> note = merge(note, fields.readLogicalText(record, field, 200), path);
                    default -> { }
                }
                if (path.equals("ContactPurpose/PurposeAsEnum")) {
                    String purpose = fields.readLogicalText(record, field, 3);
                    if (purpose.equals("EC")) emergency = Boolean.TRUE;
                    else if (purpose.equals("SDM")) substitute = Boolean.TRUE;
                    else if (ROLES.containsKey(purpose)) addRole(ROLES.get(purpose));
                    else throw new CdsClinicalException("CDS_CONTACT_PURPOSE_UNRECOGNIZED", "ContactPurpose");
                } else if (path.equals("ContactPurpose/PurposeAsPlainText")) {
                    addRole(fields.readLogicalText(record, field, 50));
                }
            }
            String merge(String previous, String actual, String path) {
                if (previous != null && !previous.equals(actual)) {
                    throw new CdsClinicalException("CDS_CONTACT_SCALAR_CONFLICT_REQUIRES_REVIEW", path);
                }
                return actual;
            }
            void addRole(String actual) {
                if (!roleSeen) { role = actual; roleSeen = true; }
                else if (!multipleRoles && !role.equals(actual)) {
                    multipleRoles = true; role = MULTIPLE_PURPOSES;
                    notice("CDS_CONTACT_MULTIPLE_PURPOSES_SOURCE_VIEW", "ContactPurpose", "ContactPurpose");
                }
            }
        }
        State state = new State();
        try { fields.forEachField(record, field -> {
            try { state.field(field); } catch (IOException failed) { throw new UncheckedIOException(failed); }
        }); } catch (UncheckedIOException failed) { throw failed.getCause(); }
        state.finishPhone();
        state.notice("CDS_CONTACT_CONSENT_NOT_ASSERTED", "consent", "");
        String combined = state.first == null || state.first.isBlank() ? state.middle
                : state.middle == null || state.middle.isBlank() ? state.first : state.first + " " + state.middle;
        return new CdsContactDto(combined, state.last, state.email, state.note,
                state.phones, List.of(state.roleSeen ? state.role : ""),
                state.emergency, state.substitute, List.copyOf(state.diagnostics.values()));
    }
}
