package io.github.carlos_emr.carlos.encounter.pageUtil;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.github.carlos_emr.carlos.encounter.pageUtil.EctSaveEncounter2Action.DEFAULT_ROW_ONE_SIZE;
import static io.github.carlos_emr.carlos.encounter.pageUtil.EctSaveEncounter2Action.parseLayoutSize;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("fast")
@Tag("encounter")
class EctSaveEncounter2ActionUnitTest {

    @Test
    void shouldReturnDefault_whenParamIsNull() {
        assertThat(parseLayoutSize(null, DEFAULT_ROW_ONE_SIZE)).isEqualTo(DEFAULT_ROW_ONE_SIZE);
    }

    @Test
    void shouldReturnDefault_whenParamIsNonNumeric() {
        assertThat(parseLayoutSize("abc", DEFAULT_ROW_ONE_SIZE)).isEqualTo(DEFAULT_ROW_ONE_SIZE);
    }

    @Test
    void shouldReturnDefault_whenParamIsNegative() {
        assertThat(parseLayoutSize("-5", DEFAULT_ROW_ONE_SIZE)).isEqualTo(DEFAULT_ROW_ONE_SIZE);
    }

    @Test
    void shouldReturnDefault_whenValueIsBelowMinimum() {
        assertThat(parseLayoutSize("9", DEFAULT_ROW_ONE_SIZE)).isEqualTo(DEFAULT_ROW_ONE_SIZE);
    }

    @Test
    void shouldReturnValue_whenParamEqualsMinimum() {
        assertThat(parseLayoutSize("10", DEFAULT_ROW_ONE_SIZE)).isEqualTo(10);
    }

    @Test
    void shouldReturnValue_whenParamIsValid() {
        assertThat(parseLayoutSize("50", DEFAULT_ROW_ONE_SIZE)).isEqualTo(50);
    }
}
