package rw.rra.roomiq.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @Test
    void successResponseUsesSharedContract() {
        ApiResponse<String> response = ApiResponse.success("Created", "value");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Created");
        assertThat(response.data()).isEqualTo("value");
        assertThat(response.timestamp()).isNotNull();
    }
}
