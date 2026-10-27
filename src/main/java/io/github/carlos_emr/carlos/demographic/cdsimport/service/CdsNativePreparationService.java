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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPreparationDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportPreparation;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded immutable native DTOs keep unbounded source scans outside clinical locks without a file per unit. */
@Service
public class CdsNativePreparationService {
    private static final int MAXIMUM=1048576;
    private static final ObjectMapper JSON=new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final CdsImportLedgerService ledger;
    private final CdsImportPreparationDao preparations;
    private final TransactionTemplate transaction;
    private final CdsImportConfigurationService configuration;

    public CdsNativePreparationService(CdsImportLedgerService ledger, CdsImportPreparationDao preparations,
            PlatformTransactionManager manager, CdsImportConfigurationService configuration) {
        this.ledger=ledger; this.preparations=preparations;
        transaction=CdsImportLedgerService.template(manager);
        this.configuration=configuration;
    }

    /** Serialization and all source IO finish before this short metadata-only transaction. */
    public void store(String run, long epoch, String unit, String sourceDigest, CdsImportCommand command, Object value)
            throws IOException {
        CdsImportLedgerService.requireNoTransaction(); Objects.requireNonNull(value, "prepared value");
        CdsImportKeys.digestValue(sourceDigest, "source record digest");
        String type=value.getClass().getName();
        CdsImportKeys.required(type, "prepared type", 250);
        ByteArrayOutputStream output=new ByteArrayOutputStream(4096) {
            @Override public synchronized void write(int value) {
                if (count>=MAXIMUM) throw new CdsImportException("NATIVE_PREPARATION_CAPACITY_EXCEEDED");
                super.write(value);
            }
            @Override public synchronized void write(byte[] bytes, int offset, int length) {
                if (length>MAXIMUM-count) throw new CdsImportException("NATIVE_PREPARATION_CAPACITY_EXCEEDED");
                super.write(bytes, offset, length);
            }
        };
        JSON.writeValue(output, value);
        String payload=output.toString(StandardCharsets.UTF_8);
        String options=CdsImportJobService.optionsDigest(command);
        String ruleset=CdsImportJobService.rulesetDigest(command, configuration);
        String digest=payloadDigest(type, sourceDigest, options, ruleset, payload);
        String id=CdsImportKeys.hash(run, unit);
        transaction.executeWithoutResult(status-> {
            ledger.lockOwned(run, epoch); ledger.unit(run, unit);
            CdsImportPreparation existing=preparations.get(id);
            if (existing!=null) {
                if (!Objects.equals(existing.getRunId(), run) || !Objects.equals(existing.getUnitKey(), unit)
                        || !Objects.equals(existing.getSourceDigest(), sourceDigest)
                        || !Objects.equals(existing.getOptionsDigest(), options)
                        || !Objects.equals(existing.getRulesetDigest(), ruleset)
                        || !Objects.equals(existing.getPayloadType(), type)
                        || !Objects.equals(existing.getDigest(), digest)) {
                    throw new CdsImportException("NATIVE_PREPARATION_CONFLICT");
                }
                if (!Objects.equals(existing.getPayload(), payload)) {
                    // This is a derived cache, regenerated from the same immutable source
                    // and identical decision digest. Never alter a checkpointed/native unit.
                    ledger.requireExpectedCheckpointFree(run, epoch, unit);
                    existing.setPayload(payload);
                    ledger.diagnostic(run, epoch, ledger.unit(run, unit).getPatientKey(), unit, "INFO",
                            "NATIVE_PREPARATION_CACHE_REGENERATED", "RECOVERY", null,
                            "An uncommitted derived preparation was regenerated from its identical immutable decision", null, false);
                }
                return;
            }
            CdsImportPreparation prepared=new CdsImportPreparation(); prepared.setId(id);
            prepared.setRunId(run); prepared.setUnitKey(unit); prepared.setSourceDigest(sourceDigest);
            prepared.setOptionsDigest(options); prepared.setRulesetDigest(ruleset);
            prepared.setPayloadType(type); prepared.setPayload(payload); prepared.setDigest(digest);
            preparations.persist(prepared);
        });
    }

    /** A single at-most-1MiB row is decoded in the already-owned clinical unit. */
    public <T> T load(String run, long epoch, String unit, String sourceDigest, CdsImportCommand command, Class<T> type)
            throws IOException {
        CdsImportUnitService.requireActive(run, epoch, unit); ledger.lockOwned(run, epoch);
        CdsImportPreparation prepared=preparations.get(CdsImportKeys.hash(run, unit));
        if (prepared==null || !prepared.getSourceDigest().equals(sourceDigest)
                || !prepared.getOptionsDigest().equals(CdsImportJobService.optionsDigest(command))
                || !prepared.getRulesetDigest().equals(CdsImportJobService.rulesetDigest(command, configuration))
                || !prepared.getPayloadType().equals(type.getName())
                || prepared.getPayload().getBytes(StandardCharsets.UTF_8).length>MAXIMUM
                || !prepared.getDigest().equals(payloadDigest(prepared.getPayloadType(), sourceDigest,
                        prepared.getOptionsDigest(), prepared.getRulesetDigest(), prepared.getPayload()))) {
            throw new CdsImportException("NATIVE_PREPARATION_VERIFICATION_FAILED");
        }
        return JSON.readValue(prepared.getPayload(), type);
    }

    private static String payloadDigest(String type, String source, String options, String ruleset, String payload) {
        String bytes=java.util.HexFormat.of().formatHex(CdsImportKeys.digest().digest(payload.getBytes(StandardCharsets.UTF_8)));
        return CdsImportKeys.hash(type, source, options, ruleset, bytes);
    }
}
