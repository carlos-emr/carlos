package io.github.carlos_emr.carlos.sms.validator;

import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("validator")
class SmsSendValidatorUnitTest {
    private final SmsSendValidator validator = new SmsSendValidator();

    @Test
    @DisplayName("valid SMS send commands pass validation")
    void shouldAcceptCommand_whenFieldsAreValid() {
        SmsSendValidator.Result result = validator.validate(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.messages()).isEmpty();
    }

    @Test
    @DisplayName("validation rejects missing patient, invalid phone, and blank body")
    void shouldRejectCommand_whenFieldsAreInvalid() {
        SmsSendValidator.Result result = validator.validate(
                SmsSendCommand.direct(0, "not-a-phone", " ", "999998")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).containsExactly(
                "A valid patient demographic number is required.",
                "A valid recipient phone number is required.",
                "SMS message body is required."
        );
    }
}
