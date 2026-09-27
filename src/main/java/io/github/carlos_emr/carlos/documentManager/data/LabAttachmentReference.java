// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager.data;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Source-qualified identity for independently numbered laboratory systems. */
public record LabAttachmentReference(String source, int id) {
    public static final String UNRESOLVED = "UNRESOLVED";
    public static final Set<String> SOURCES = Set.of("HL7", "MDS", "CML", "BCP");

    public LabAttachmentReference {
        if (source == null || id <= 0) throw new IllegalArgumentException("Invalid lab attachment");
        source = source.toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(source) && !UNRESOLVED.equals(source)) {
            throw new IllegalArgumentException("Unsupported lab attachment source");
        }
    }

    public static LabAttachmentReference stored(String source, int id) {
        return new LabAttachmentReference(source == null || source.isBlank() ? UNRESOLVED : source, id);
    }

    public static LabAttachmentReference parse(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9]*:[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException("Lab attachment requires a source and positive ID");
        }
        int separator = value.indexOf(':');
        return new LabAttachmentReference(value.substring(0, separator), Integer.parseInt(value.substring(separator + 1)));
    }

    /** Legacy bare IDs are accepted only when their source is unambiguous for this patient. */
    public static LabAttachmentReference resolve(String value, int demographicNo, PatientLabRoutingDao routingDao) {
        LabAttachmentReference reference;
        if (value != null && value.matches("[1-9][0-9]{0,9}")) {
            int id = Integer.parseInt(value);
            List<String> sources = routingDao.findLabSourcesForPatient(id, demographicNo);
            if (sources.size() != 1) {
                throw new IllegalArgumentException("Lab source is missing or ambiguous; select the lab again");
            }
            reference = new LabAttachmentReference(sources.get(0), id);
        } else {
            reference = parse(value);
        }
        if (UNRESOLVED.equals(reference.source()) || routingDao.findByLabNoAndLabType(reference.id(), reference.source())
                .stream().noneMatch(row -> Integer.valueOf(demographicNo).equals(row.getDemographicNo()))) {
            throw new IllegalArgumentException("Lab attachment does not belong to this patient");
        }
        return reference;
    }

    public String key() {
        return source + ":" + id;
    }

    public String storageSource() {
        return UNRESOLVED.equals(source) ? null : source;
    }
}
