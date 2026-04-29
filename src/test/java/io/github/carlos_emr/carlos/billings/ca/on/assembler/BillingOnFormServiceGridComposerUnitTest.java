package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BillingOnFormServiceGridComposer")
@Tag("unit")
@Tag("billing")
class BillingOnFormServiceGridComposerUnitTest {

    @Test
    void shouldAcceptSimpleInlineStyleDeclarations() {
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:red;"))
                .isTrue();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("font-weight: bold; background-color:#fff"))
                .isTrue();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:red;  "))
                .isTrue();
    }

    @Test
    void shouldRejectMalformedOrDangerousInlineStylesWithoutRegexBacktracking() {
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color"))
                .isFalse();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:"))
                .isFalse();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:\"red\";"))
                .isFalse();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:\\red;"))
                .isFalse();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("-:!-:!-:!-:!-:!-:!-:!-:!-:!"))
                .isFalse();
    }
}
