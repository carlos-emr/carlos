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
package io.github.carlos_emr.carlos.demographic.cdsimport;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CdsFieldControlUnitTest {
    @TempDir Path temp;

    @Test void shouldThrottleDurableProbes_andRestoreNestedPreviousScope() throws Exception {
        CdsRecordDto record=record(); CdsFieldParser fields=new CdsFieldParser();
        AtomicInteger outer=new AtomicInteger(),inner=new AtomicInteger();
        try (var scope=fields.control(outer::incrementAndGet)) {
            for (int i=0;i<100;i++) assertNull(fields.firstText(record,"Missing",10));
            assertEquals(1,outer.get());
            try (var nested=fields.control(inner::incrementAndGet)) { assertNull(fields.firstText(record,"Missing",10)); }
            assertEquals(1,inner.get());
            Thread.sleep(275);
            assertNull(fields.firstText(record,"Missing",10)); assertEquals(2,outer.get());
        }
        int count=outer.get(); Thread.sleep(275);
        assertNull(fields.firstText(record,"Missing",10)); assertEquals(count,outer.get());
    }

    @Test void shouldPreserveTypedOwnerFailure_andClearScopeAfterException() throws Exception {
        CdsRecordDto record=record(); CdsFieldParser fields=new CdsFieldParser();
        try (var scope=fields.control(()->{throw new CdsImportException("OWNER_FENCED");})) {
            assertEquals("OWNER_FENCED",assertThrows(CdsImportException.class,()->fields.firstText(record,"Missing",10)).getCode());
        }
        assertNull(fields.firstText(record,"Missing",10));
    }

    @Test void shouldStopAnInterruptedMetadataScan_beforePublishingItsFirstDescriptorIndex() throws Exception {
        CdsRecordDto record=record(); AtomicBoolean interrupt=new AtomicBoolean(true);
        CdsFieldParser fields=new CdsFieldParser() {
            @Override public void forEachField(CdsRecordDto selected,java.util.function.Consumer<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto> consumer)
                    throws java.io.IOException {
                super.forEachField(selected,field->{ consumer.accept(field); if (interrupt.getAndSet(false)) Thread.currentThread().interrupt(); });
            }
        };
        try { assertEquals("CDS_INPUT_CANCELLED",assertThrows(CdsInputException.class,()->fields.firstText(record,"Missing",10)).code()); }
        finally { Thread.interrupted(); }
        assertNull(fields.firstText(record,"Missing",10));
    }

    @Test void shouldRefuseParseSeal_whenCancellationArrivesAfterTheLastSourceRecord() throws Exception {
        Path source=Files.writeString(temp.resolve("source.xml"),CdsXmlParserUnitTest.fixture());
        AtomicBoolean cancel=new AtomicBoolean(); Path spool=temp.resolve("cancelled");
        CdsInputException failure=assertThrows(CdsInputException.class,()->new CdsXmlParser().parse(source,spool,CdsInputLimitsDto.defaults(),cancel::get,
                record->{ if (record.section().equals("ClinicalNotes")) cancel.set(true); }));
        assertEquals("CDS_INPUT_CANCELLED",failure.code()); assertFalse(Files.exists(spool.resolve("parse.seal")));
    }

    private CdsRecordDto record() throws Exception {
        Path source=Files.writeString(temp.resolve("source.xml"),CdsXmlParserUnitTest.fixture());
        var records=new ArrayList<CdsRecordDto>();
        new CdsXmlParser().parse(source,temp.resolve("parsed"),CdsInputLimitsDto.defaults(),()->false,records::add);
        return records.get(0);
    }
}
