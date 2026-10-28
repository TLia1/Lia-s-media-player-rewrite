package com.lia.mediaplayer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiVersionTest {

    @Test
    void parsesAndPrintsTheSameVersion() {
        assertThat(ApiVersion.parse("4.2.1")).isEqualTo(new ApiVersion(4, 2, 1));
        assertThat(ApiVersion.parse("4.2.1")).hasToString("4.2.1");
    }

    @Test
    void ignoresPreReleaseAndBuildSuffixes() {
        assertThat(ApiVersion.parse("4.0.0-beta.2")).isEqualTo(new ApiVersion(4, 0, 0));
        assertThat(ApiVersion.parse(" 4.0.3+build.7 ")).isEqualTo(new ApiVersion(4, 0, 3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "4", "4.0", "4.0.0.0", "a.b.c", "4..0", "-1.0.0"})
    void rejectsWhatIsNotAVersion(String text) {
        assertThatThrownBy(() -> ApiVersion.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeComponents() {
        assertThatThrownBy(() -> new ApiVersion(1, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordersByMajorThenMinorThenPatch() {
        assertThat(new ApiVersion(4, 1, 0)).isGreaterThan(new ApiVersion(4, 0, 9));
        assertThat(new ApiVersion(5, 0, 0)).isGreaterThan(new ApiVersion(4, 9, 9));
        assertThat(new ApiVersion(4, 0, 2)).isGreaterThan(new ApiVersion(4, 0, 1));
    }

    @Test
    void aNewerMinorSatisfiesAnOlderRequirementOfTheSameMajor() {
        var installed = new ApiVersion(4, 3, 0);
        assertThat(installed.satisfies(new ApiVersion(4, 1, 5))).isTrue();
        assertThat(installed.satisfies(new ApiVersion(4, 3, 0))).isTrue();
        assertThat(installed.satisfies(new ApiVersion(4, 3, 1))).isFalse();
        assertThat(installed.satisfies(new ApiVersion(3, 0, 0))).isFalse();
        assertThat(installed.satisfies(new ApiVersion(5, 0, 0))).isFalse();
    }
}
