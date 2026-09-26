/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.documentManager.data.AttachmentLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The picker's lab grouping must key labs by source and segment id: HL7, MDS, CML and BCP each
 * number their own tables, so an HL7 lab and an MDS lab with the same id are two attachable
 * labs, and only HL7 ids may be resolved through the HL7 version chain.
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("fast")
@DisplayName("DocumentAttachmentManagerImpl lab grouping by source")
class DocumentAttachmentManagerImplLabGroupingUnitTest extends CarlosUnitTestBase {

    private final DocumentAttachmentManagerImpl manager = new DocumentAttachmentManagerImpl();

    private static LabResultData lab(String labType, String segmentId, String label, long time) {
        LabResultData lab = new LabResultData(labType);
        lab.setSegmentID(segmentId);
        lab.setLabel(label);
        lab.setDateObj(new Date(time));
        return lab;
    }

    @Test
    @DisplayName("should offer an HL7 lab and an MDS lab that share a segment id as two labs")
    void shouldKeepBothLabs_whenSourcesShareASegmentId() {
        List<LabResultData> labs = new ArrayList<>(List.of(
                lab("HL7", "5", "CBC", 2_000L),
                lab("MDS", "5", "Lipids", 1_000L)));
        List<String> chainLookups = new ArrayList<>();

        List<AttachmentLabResultData> grouped = manager.groupLabsByVersion(labs, id -> {
            chainLookups.add(id);
            return id;
        });

        assertThat(grouped).extracting(AttachmentLabResultData::getLabType).containsExactlyInAnyOrder("HL7", "MDS");
        assertThat(grouped).extracting(AttachmentLabResultData::getSegmentID).containsExactly("5", "5");
        assertThat(grouped).allMatch(entry -> entry.getLabVersionIds().isEmpty());
        // Only the HL7 lab is resolved through the HL7 version chain.
        assertThat(chainLookups).containsExactly("5");
    }

    @Test
    @DisplayName("should fold older HL7 versions under the newest one without touching a same-numbered MDS lab")
    void shouldAttachHl7Versions_bySourceAndId() {
        List<LabResultData> labs = new ArrayList<>(List.of(
                lab("HL7", "9", "CBC v2", 3_000L),
                lab("HL7", "7", "CBC v1", 2_000L),
                lab("MDS", "7", "Lipids", 1_000L)));

        List<AttachmentLabResultData> grouped = manager.groupLabsByVersion(labs,
                id -> "9".equals(id) ? "7,9" : id);

        AttachmentLabResultData newest = grouped.stream()
                .filter(entry -> "HL7".equals(entry.getLabType())).findFirst().orElseThrow();
        assertThat(newest.getSegmentID()).isEqualTo("9");
        assertThat(newest.getLabVersionIds()).containsOnlyKeys("7");
        // The MDS lab numbered 7 is not swallowed as a version of HL7 lab 9.
        assertThat(grouped).extracting(AttachmentLabResultData::getLabType).containsExactlyInAnyOrder("HL7", "MDS");
        Map<String, String> versions = newest.getLabVersionIds();
        assertThat(versions).hasSize(1);
    }
}
