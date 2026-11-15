package io.github.yatab1.vigie.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GradeDropAlertsTest {

    @Test
    void onlyAWorseGradeCountsAsADrop() {
        assertThat(GradeDropAlerts.isWorse("C", "B")).isTrue();
        assertThat(GradeDropAlerts.isWorse("F", "A")).isTrue();
        assertThat(GradeDropAlerts.isWorse("B", "B")).isFalse();
        assertThat(GradeDropAlerts.isWorse("A", "D")).isFalse();
    }

    @Test
    void unknownGradesNeverTriggerAnAlert() {
        assertThat(GradeDropAlerts.isWorse(null, "A")).isFalse();
        assertThat(GradeDropAlerts.isWorse("F", null)).isFalse();
        assertThat(GradeDropAlerts.isWorse("Z", "A")).isFalse();
    }
}
