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
package io.github.carlos_emr.carlos.sms.assembler;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.sms.service.SmsConfigService;
import io.github.carlos_emr.carlos.sms.service.SmsDefaultProviderResolver;
import io.github.carlos_emr.carlos.sms.service.SmsProviderClientResolver;
import io.github.carlos_emr.carlos.sms.service.SmsQueueScheduler;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsConfigViewModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Assembles {@link SmsConfigViewModel} for Administration &gt; SMS ({@code admin/configureSms.jsp}).
 * <p>
 * Shows the saved settings, or while nothing is saved the values the {@code sms.*} properties give.
 * Secrets appear only as "set" flags. The result message comes from a fixed list of codes, so a
 * crafted {@code result} parameter cannot put text on the page.
 *
 * @since 2026-09-24
 */
@Service
public class SmsConfigViewModelAssembler {
    static final String SYSTEM_TEST_ENABLED_PROPERTY = "sms.systemTest.enabled";
    private static final Set<String> RESULT_CODES =
            Set.of("saved", "testSent", "testBlocked", "testInvalid", "testFailed");

    private final SmsConfigService configService;
    private final SmsProviderClientResolver providerClients;
    private final SmsDefaultProviderResolver providerResolver;
    private final SmsQueueScheduler scheduler;
    private final BooleanSupplier systemTestEnabled;

    @Autowired
    public SmsConfigViewModelAssembler(SmsConfigService configService, SmsProviderClientResolver providerClients,
                                       SmsDefaultProviderResolver providerResolver, SmsQueueScheduler scheduler) {
        this(configService, providerClients, providerResolver, scheduler,
                () -> CarlosProperties.getInstance().isPropertyActive(SYSTEM_TEST_ENABLED_PROPERTY));
    }

    SmsConfigViewModelAssembler(SmsConfigService configService, SmsProviderClientResolver providerClients,
                                SmsDefaultProviderResolver providerResolver, SmsQueueScheduler scheduler,
                                BooleanSupplier systemTestEnabled) {
        this.configService = configService;
        this.providerClients = providerClients;
        this.providerResolver = providerResolver;
        this.scheduler = scheduler;
        this.systemTestEnabled = systemTestEnabled;
    }

    /**
     * @param resultCode the {@code result} request parameter after a save or system test; unknown codes are
     *                   ignored
     * @param errorKeys  validation message keys to show
     * @return the page model
     */
    public SmsConfigViewModel assemble(String resultCode, List<String> errorKeys) {
        Optional<SmsConfig> stored = configService.current();
        List<String> messageKeys = new ArrayList<>(errorKeys == null ? List.of() : errorKeys);
        SmsProviderType providerType;
        if (stored.isPresent()) {
            providerType = stored.get().getProviderType();
        } else {
            try {
                providerType = providerResolver.configuredDefault();
            } catch (IllegalStateException e) {
                // An unknown sms.provider.default must not lock the admin out of the page that replaces it.
                providerType = SmsProviderType.STUB;
                messageKeys.add("sms.config.error.invalidPropertyProvider");
            }
        }
        boolean schedulerRunning = scheduler.isRunning();
        List<SmsConfigViewModel.CredentialField> credentialFields = configService.credentialFields(providerType)
                .stream()
                .map(field -> new SmsConfigViewModel.CredentialField(
                        field, stored.map(config -> config.hasCredential(field)).orElse(false)))
                .toList();
        return new SmsConfigViewModel(
                providerType.name(),
                providerClients.registeredProviderTypes().stream().map(Enum::name).sorted().toList(),
                stored.map(SmsConfig::isEnabled).orElse(true),
                stored.map(SmsConfig::isSchedulerEnabled).orElse(schedulerRunning),
                schedulerRunning,
                stored.map(SmsConfig::getSenderNumber).orElse(""),
                stored.map(SmsConfig::hasWebhookSecret).orElse(false),
                credentialFields,
                stored.isPresent(),
                systemTestEnabled.getAsBoolean(),
                resultCode != null && RESULT_CODES.contains(resultCode) ? "sms.config.result." + resultCode : "",
                messageKeys
        );
    }
}
