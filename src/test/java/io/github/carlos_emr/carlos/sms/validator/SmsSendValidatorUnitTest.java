package io.github.carlos_emr.carlos.sms.validator;

import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
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
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.messages()).isEmpty();
    }

    @Test
    @DisplayName("validation rejects missing patient, invalid phone, and blank body")
    void shouldRejectCommand_whenFieldsAreInvalid() {
        SmsSendValidator.Result result = validator.validate(
                SmsSendCommand.patientMessage(0, "not-a-phone", " ", "999998")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).containsExactly(
                "A valid patient demographic number is required.",
                "A valid recipient phone number is required.",
                "SMS message body is required."
        );
    }

    @Test
    @DisplayName("validation rejects missing recipient phone type")
    void shouldRejectCommand_whenRecipientPhoneTypeIsMissing() {
        SmsSendValidator.Result result = validator.validate(
                new SmsSendCommand(
                        123,
                        "416-555-1212",
                        null,
                        "Appointment reminder",
                        null,
                        "999998",
                        1001,
                        null
                )
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).containsExactly("SMS recipient phone type is required.");
    }

    @Test
    @DisplayName("validation limits SMS bodies to one standard segment")
    void shouldRejectCommand_whenBodyExceedsSingleSmsSegment() {
        SmsSendValidator.Result accepted = validator.validate(
                SmsSendCommand.patientMessage(123, "416-555-1212", "a".repeat(160), "999998")
        );
        SmsSendValidator.Result rejected = validator.validate(
                SmsSendCommand.patientMessage(123, "416-555-1212", "a".repeat(161), "999998")
        );

        assertThat(accepted.valid()).isTrue();
        assertThat(rejected.valid()).isFalse();
        assertThat(rejected.messages()).containsExactly(
                "SMS message body is too long for one text message (161 of 160 characters)."
        );
    }

    @Test
    @DisplayName("validation counts GSM-7 extension characters twice against the one-segment limit")
    void shouldRejectCommand_whenExtensionCharactersExceedSingleSegment() {
        SmsSendValidator.Result result = validator.validate(
                SmsSendCommand.patientMessage(123, "416-555-1212", "a".repeat(159) + "€", "999998")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).containsExactly(
                "SMS message body is too long for one text message (161 of 160 characters)."
        );
    }

    @Test
    @DisplayName("validation limits bodies that need Unicode to 70 characters")
    void shouldRejectCommand_whenUnicodeBodyExceedsSingleSegment() {
        SmsSendValidator.Result accepted = validator.validate(
                SmsSendCommand.patientMessage(123, "416-555-1212", "ô".repeat(70), "999998")
        );
        SmsSendValidator.Result rejected = validator.validate(
                SmsSendCommand.patientMessage(
                        123, "416-555-1212", "Appointment at the hôpital " + "a".repeat(44), "999998")
        );

        assertThat(accepted.valid()).isTrue();
        assertThat(rejected.valid()).isFalse();
        assertThat(rejected.messages()).containsExactly(
                "SMS message body is too long for one text message (71 of 70 characters; accented or special "
                        + "characters lower the limit from 160 to 70)."
        );
    }

    @Test
    @DisplayName("validation does not require a patient for synthetic system test messages")
    void shouldAcceptCommand_whenSystemTestHasNoPatient() {
        SmsSendValidator.Result result = validator.validate(systemTest(null));

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("validation still requires a patient for patient messages")
    void shouldRejectCommand_whenPatientMessageHasNoPatient() {
        SmsSendValidator.Result result = validator.validate(
                SmsSendCommand.patientMessage(null, "416-555-1212", "Appointment reminder", "999998")
        );

        assertThat(result.messages()).containsExactly("A valid patient demographic number is required.");
    }

    private static SmsSendCommand systemTest(Integer demographicNo) {
        return new SmsSendCommand(
                demographicNo,
                "416-555-1212",
                SmsRecipientPhoneType.CELL,
                "CARLOS SMS system test",
                SmsMessagePurpose.SYSTEM_TEST,
                "999998",
                1001,
                null
        );
    }
}
