package io.github.carlos_emr.carlos.sms.validator;

import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SmsSendValidator {
    private static final int MAX_SMS_BODY_LENGTH = 160;

    public Result validate(SmsSendCommand command) {
        List<String> messages = new ArrayList<>();
        if (command == null) {
            return new Result(List.of("SMS send request is required."));
        }

        if (command.demographicNo() == null || command.demographicNo() <= 0) {
            messages.add("A valid patient demographic number is required.");
        }

        if (SmsPhoneNumbers.normalizeToE164(command.recipientPhoneNumber()).isEmpty()) {
            messages.add("A valid recipient phone number is required.");
        }

        if (command.recipientPhoneType() == null) {
            messages.add("SMS recipient phone type is required.");
        }

        String body = command.body();
        if (body == null || body.trim().isEmpty()) {
            messages.add("SMS message body is required.");
        } else if (body.length() > MAX_SMS_BODY_LENGTH) {
            messages.add("SMS message body exceeds the maximum supported length.");
        }

        return new Result(messages);
    }

    public record Result(List<String> messages) {
        public Result {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        public boolean valid() {
            return messages.isEmpty();
        }
    }
}
