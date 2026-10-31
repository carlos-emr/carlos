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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Test-only whole-original-input validation through the shipped parser; no Spring/DB/clinical import. */
public final class CdsWorkloadValidator {
    private CdsWorkloadValidator() { }

    public static void main(String[] args) {
        try {
            if (args.length!=2) throw new IllegalArgumentException("Expected original XML and fresh private spool");
            Path source=Path.of(args[0]), spool=Path.of(args[1]);
            if (!source.isAbsolute() || !spool.isAbsolute() || Files.isSymbolicLink(source)
                    || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(spool, LinkOption.NOFOLLOW_LINKS) || source.startsWith(spool)
                    || spool.getParent()==null || !Files.isDirectory(spool.getParent(),LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("Invalid trusted workload validation paths");
            AtomicLong emitted=new AtomicLong();
            var parsed=new CdsXmlParser().parse(source,spool,CdsInputLimitsDto.defaults(),()->false,
                    ignored->emitted.incrementAndGet());
            if (parsed.records()!=emitted.get()) throw new IllegalStateException("Record stream count differs");
            System.out.println("CDS_WORKLOAD_VALIDATED:"+new ObjectMapper().writeValueAsString(Map.of(
                    "sourceSha256",parsed.sourceSha256(),"sourceBytes",parsed.sourceBytes(),
                    "records",parsed.records(),"fields",parsed.fields(),
                    "decodedBinaryBytes",parsed.decodedBinaryBytes(),"valuesBytes",parsed.valuesBytes())));
        } catch (Exception failure) {
            String code=failure instanceof CdsInputException input?input.code():"WORKLOAD_VALIDATION_FAILED";
            System.err.println("CDS_WORKLOAD_REFUSED:"+code+":"+failure.getClass().getSimpleName());
            System.exit(2);
        }
    }
}
