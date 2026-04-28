package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BillingONFormServiceGridComposer")
@Tag("unit")
@Tag("billing")
class BillingONFormServiceGridComposerUnitTest {

    @Test
    void shouldAcceptSimpleInlineStyleDeclarations() {
        assertThat(BillingONFormServiceGridComposer.isSafeInlineStyle("color:red;"))
                .isTrue();
        assertThat(BillingONFormServiceGridComposer.isSafeInlineStyle("font-weight: bold; background-color:#fff"))
                .isTrue();
        assertThat(BillingONFormServiceGridComposer.isSafeInlineStyle("color:red;  "))
                .isTrue();
    }

    @Test
    void shouldRejectMalformedOrDangerousInlineStylesWithoutRegexBacktracking() {
        assertThat(BillingONFormServiceGridComposer.isSafeInlineStyle("color"))
                .isFalse();
        assertThat(BillingONFormServiceGridComposer.isSafeInlineStyle("color:"))
                .isFalse();
        assertThat(BillingONFormServiceGridComposer.isSafeInlineStyle("color:\"red\";"))
                .isFalse();
        assertThat(BillingONFormServiceGridComposer.isSafeInlineStyle("color:\\red;"))
                .isFalse();
        assertThat(BillingONFormServiceGridComposer.isSafeInlineStyle("-:!-:!-:!-:!-:!-:!-:!-:!-:!"))
                .isFalse();
    }
}
