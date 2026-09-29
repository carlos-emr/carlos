package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDao;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueCountDto;
import io.github.carlos_emr.carlos.sms.dto.SmsQueuePatientCountDto;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueRowDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SmsTransactionDao extends AbstractDao<SmsTransaction> {
    List<SmsTransaction> findByDemographicNo(Integer demographicNo, int limit);

    Optional<SmsTransaction> findByProviderMessageId(SmsProviderType providerType, String providerMessageId);

    Optional<SmsTransaction> findByClientReferenceId(SmsProviderType providerType, String clientReferenceId);

    List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date claimAt, int limit);

    List<SmsTransaction> claimStaleOutboundSendingForRecovery(
            SmsProviderType providerType,
            Date staleBefore,
            Date recoveryAt,
            int limit
    );

    /** The most patients one per-patient count query names; longer lists are queried in parts. */
    int PATIENT_COUNT_CHUNK_SIZE = 1000;

    // Read-only queries for the Administration > SMS queue view (#3841). All of them look at outbound rows
    // only, and the row lists are projections that never load the message body.

    /**
     * @return outbound row counts grouped by SMS provider and status; the count's code is the status name
     */
    List<SmsQueueCountDto> countOutboundByProviderAndStatus();

    /**
     * Counts queued outbound rows that became due before {@code dueBefore}. A message never attempted
     * ({@code attemptCount = 0}) is due since {@code createdAt}; an attempted one is due at
     * {@code nextAttemptAt}, or {@code createdAt} when that is empty.
     *
     * @param dueBefore rows due strictly before this time count; {@code null} counts nothing
     * @return counts per SMS provider; providers with no such rows are absent
     */
    Map<SmsProviderType, Long> countOverdueQueuedOutboundByProvider(Date dueBefore);

    /**
     * Counts outbound rows still {@code SENDING} whose last attempt started before {@code staleBefore}: the
     * same rows the queue worker's stale recovery would claim.
     *
     * @param staleBefore rows whose last attempt is strictly before this time count; {@code null} counts nothing
     * @return counts per SMS provider; providers with no such rows are absent
     */
    Map<SmsProviderType, Long> countStaleSendingOutboundByProvider(Date staleBefore);

    /**
     * @param since only rows last updated at or after this time count; {@code null} counts rows of all time
     * @return {@code FAILED} outbound row counts grouped by SMS provider and error code (the code may be null)
     */
    List<SmsQueueCountDto> countFailedOutboundByProviderAndErrorCode(Date since);

    /**
     * @param since only rows last updated at or after this time count; {@code null} counts rows of all time
     * @return {@code CONSENT_BLOCKED} and {@code OPTOUT_BLOCKED} outbound row counts grouped by SMS provider
     *         and consent reason code (the code may be null)
     */
    List<SmsQueueCountDto> countConsentBlockedOutboundByProviderAndReason(Date since);

    /**
     * The part of {@link #countFailedOutboundByProviderAndErrorCode(Date)} that belongs to the given
     * patients, counted per patient. The queue view uses it to take the messages of patients the viewer is
     * restricted from out of the counts by error code.
     *
     * @param since          only rows last updated at or after this time count; {@code null} counts rows
     *                       of all time
     * @param demographicNos the patients to count for; empty or {@code null} runs no query and returns
     *                       nothing. Long lists are queried in parts of at most
     *                       {@link #PATIENT_COUNT_CHUNK_SIZE} patients
     * @return {@code FAILED} outbound row counts of those patients, grouped by SMS provider, error code
     *         (may be null) and patient
     */
    List<SmsQueuePatientCountDto> countFailedOutboundByProviderErrorCodeAndPatient(
            Date since, Collection<Integer> demographicNos);

    /**
     * The part of {@link #countConsentBlockedOutboundByProviderAndReason(Date)} that belongs to the given
     * patients, counted per patient. The queue view uses it to take the messages of patients the viewer is
     * restricted from out of the counts by consent reason code.
     *
     * @param since          only rows last updated at or after this time count; {@code null} counts rows
     *                       of all time
     * @param demographicNos the patients to count for; empty or {@code null} runs no query and returns
     *                       nothing. Long lists are queried in parts of at most
     *                       {@link #PATIENT_COUNT_CHUNK_SIZE} patients
     * @return {@code CONSENT_BLOCKED} and {@code OPTOUT_BLOCKED} outbound row counts of those patients,
     *         grouped by SMS provider, consent reason code (may be null) and patient
     */
    List<SmsQueuePatientCountDto> countConsentBlockedOutboundByProviderReasonAndPatient(
            Date since, Collection<Integer> demographicNos);

    /**
     * The overdue queued rows {@link #countOverdueQueuedOutboundByProvider(Date)} counts, for one SMS provider,
     * oldest due time first.
     *
     * @param limit maximum rows; non-positive values use the default and large values are capped
     */
    List<SmsQueueRowDto> findOverdueQueuedOutbound(SmsProviderType providerType, Date dueBefore, int limit);

    /**
     * The stale {@code SENDING} rows {@link #countStaleSendingOutboundByProvider(Date)} counts, for one SMS
     * provider, oldest last attempt first.
     *
     * @param limit maximum rows; non-positive values use the default and large values are capped
     */
    List<SmsQueueRowDto> findStaleSendingOutbound(SmsProviderType providerType, Date staleBefore, int limit);

    /**
     * Outbound rows of one SMS provider in any of the given statuses, most recently updated first.
     *
     * @param statuses the statuses to include; empty or {@code null} returns nothing
     * @param since    only rows last updated at or after this time are returned; {@code null} returns rows
     *                 of all time
     * @param limit    maximum rows; non-positive values use the default and large values are capped
     */
    List<SmsQueueRowDto> findRecentOutboundByStatuses(
            SmsProviderType providerType,
            Collection<SmsStatus> statuses,
            Date since,
            int limit
    );
}
