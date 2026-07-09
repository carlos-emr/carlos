package io.github.carlos_emr.carlos.consultation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.consultation.ConsultationDemographicResolver.FailureReason;
import io.github.carlos_emr.carlos.consultation.ConsultationDemographicResolver.Resolution;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultationDemographicResolverUnitTest {

    @Test
    @DisplayName("resolves the persisted consultation demographic when the submitted demographic differs")
    void shouldResolvePersistedDemographic_whenSubmittedDemographicDiffers() {
        ConsultationRequestDao consultationRequestDao = mock(ConsultationRequestDao.class);
        Logger logger = mock(Logger.class);
        ConsultationRequest consultationRequest = new ConsultationRequest();
        consultationRequest.setDemographicId(1);
        when(consultationRequestDao.find(9)).thenReturn(consultationRequest);
        when(logger.isWarnEnabled()).thenReturn(true);

        Resolution resolution = ConsultationDemographicResolver.resolve(consultationRequestDao, "9", "999",
                "preview", logger);

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.demographicId()).isEqualTo("1");
        assertThat(resolution.failureReason()).isNull();
        verify(consultationRequestDao).find(9);
        verify(logger).warn(eq("Ignoring mismatched consultation {} demographic requestId={} submittedDemographic={} consultationDemographic={}"),
                eq("preview"), eq("9"), eq("999"), eq("1"));
    }

    @Test
    @DisplayName("returns missing request id when the request id is blank")
    void shouldReturnMissingRequestId_whenRequestIdBlank() {
        ConsultationRequestDao consultationRequestDao = mock(ConsultationRequestDao.class);

        Resolution resolution = ConsultationDemographicResolver.resolve(consultationRequestDao, " ", "1",
                "PDF", mock(Logger.class));

        assertThat(resolution.isResolved()).isFalse();
        assertThat(resolution.failureReason()).isEqualTo(FailureReason.MISSING_REQUEST_ID);
        assertThat(resolution.cause()).isNull();
        verify(consultationRequestDao, never()).find(9);
    }

    @Test
    @DisplayName("returns invalid request id when the request id cannot be parsed")
    void shouldReturnInvalidRequestId_whenRequestIdCannotBeParsed() {
        ConsultationRequestDao consultationRequestDao = mock(ConsultationRequestDao.class);
        Logger logger = mock(Logger.class);
        when(logger.isWarnEnabled()).thenReturn(true);

        Resolution resolution = ConsultationDemographicResolver.resolve(consultationRequestDao, "../9", "1",
                "print", logger);

        assertThat(resolution.isResolved()).isFalse();
        assertThat(resolution.failureReason()).isEqualTo(FailureReason.INVALID_REQUEST_ID);
        assertThat(resolution.cause()).isInstanceOf(NumberFormatException.class);
        verify(consultationRequestDao, never()).find(9);
        verify(logger).warn(eq("Invalid consultation {} request id while resolving demographic requestId={}"),
                eq("print"), eq("../9"));
    }

    @Test
    @DisplayName("returns missing consultation request when no persisted demographic is available")
    void shouldReturnMissingConsultationRequest_whenNoPersistedDemographicAvailable() {
        ConsultationRequestDao consultationRequestDao = mock(ConsultationRequestDao.class);
        Logger logger = mock(Logger.class);
        when(logger.isWarnEnabled()).thenReturn(true);
        when(consultationRequestDao.find(9)).thenReturn(new ConsultationRequest());

        Resolution resolution = ConsultationDemographicResolver.resolve(consultationRequestDao, "9", "1",
                "PDF", logger);

        assertThat(resolution.isResolved()).isFalse();
        assertThat(resolution.failureReason()).isEqualTo(FailureReason.MISSING_CONSULTATION_REQUEST);
        assertThat(resolution.cause()).isNull();
        verify(logger).warn(eq("Unable to resolve consultation {} demographic for requestId={}"),
                eq("PDF"), eq("9"));
    }
}
