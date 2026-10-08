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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Keeps SMS transactions in a list and applies each change to the row itself, as the JPA service would, so
 * the send path, queue worker and callback service can run together in a unit test.
 */
final class InMemorySmsTransactionService implements SmsTransactionService {
    private final List<SmsTransaction> transactions = new ArrayList<>();

    /** @return every row, in the order it was added */
    List<SmsTransaction> transactions() {
        return List.copyOf(transactions);
    }

    /** Adds a due, consented outbound row for {@code providerType}, as if it was queued earlier. */
    SmsTransaction addQueued(SmsSendCommand command, SmsProviderType providerType, SmsConsentDecisionDto consent) {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
        assignId(transaction, transactions.size() + 1L);
        transaction.recordConsentDecision(consent);
        transactions.add(transaction);
        return transaction;
    }

    @Override
    public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType,
                                                SmsConsentDecisionDto decision) {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
        assignId(transaction, transactions.size() + 1L);
        transactions.add(transaction);
        if (decision.allowed()) {
            transaction.recordConsentDecision(decision);
        } else {
            transaction.markConsentBlocked(decision);
        }
        return transaction;
    }

    @Override
    public SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision) {
        transaction.markConsentBlocked(decision);
        return transaction;
    }

    @Override
    public SmsTransaction recordConsentDecision(SmsTransaction transaction, SmsConsentDecisionDto decision) {
        transaction.recordConsentDecision(decision);
        return transaction;
    }

    @Override
    public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
        transaction.markSending(attemptAt);
        return transaction;
    }

    @Override
    public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult) {
        transaction.markProviderResult(providerResult);
        return transaction;
    }

    @Override
    public SmsTransaction markRetryScheduled(SmsTransaction transaction, SmsProviderSendResultDto providerResult,
                                             Date nextAttemptAt) {
        transaction.markRetryScheduled(providerResult, nextAttemptAt);
        return transaction;
    }

    @Override
    public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
        transaction.markClaimReleased(dueAt);
        return transaction;
    }

    @Override
    public SmsTransaction renewClaim(SmsTransaction transaction, Date attemptAt) {
        transaction.renewSendingClaim(attemptAt);
        return transaction;
    }

    @Override
    public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
        SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
        transactions.add(transaction);
        return transaction;
    }

    @Override
    public SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook) {
        SmsTransaction transaction = SmsTransaction.deliveryEvent(webhook);
        transactions.add(transaction);
        return transaction;
    }

    @Override
    public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
        return transactions.stream()
                .filter(transaction -> transaction.getProviderType() == providerType)
                .filter(transaction -> transaction.getStatus() == SmsStatus.QUEUED)
                .filter(transaction -> transaction.getNextAttemptAt() == null
                        || !transaction.getNextAttemptAt().after(now))
                .limit(limit)
                .peek(transaction -> transaction.markSending(now))
                .toList();
    }

    @Override
    public List<SmsTransaction> claimStaleSendingForRecovery(SmsProviderType providerType, Date staleBefore,
                                                             Date recoveryAt, int limit) {
        List<SmsTransaction> stale = transactions.stream()
                .filter(transaction -> transaction.getProviderType() == providerType)
                .filter(transaction -> transaction.getStatus() == SmsStatus.SENDING)
                .filter(transaction -> transaction.getLastAttemptAt() != null
                        && transaction.getLastAttemptAt().before(staleBefore))
                .limit(limit)
                .toList();
        stale.forEach(transaction -> transaction.markStaleRecoveryStarted(recoveryAt));
        return stale;
    }

    private static void assignId(SmsTransaction transaction, long id) {
        try {
            Field idField = SmsTransaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(transaction, id);
            transaction.assignClientReferenceId(SmsTransaction.clientReferenceIdFor(id));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to assign SMS transaction id for test", e);
        }
    }
}
