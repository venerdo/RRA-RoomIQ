package rw.rra.roomiq.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    @Test
    void exposesStableCorrelationHeaderName() {
        assertThat(CorrelationIdFilter.HEADER_NAME).isEqualTo("X-Correlation-ID");
        assertThat(CorrelationIdFilter.MDC_KEY).isEqualTo("correlationId");
    }

    @Test
    void replacesInvalidHeaderAndUsesSameValueInRequestAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "invalid id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(
                        request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)));

        String responseId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(responseId).matches(Pattern.compile("[0-9a-f-]{36}"));
        assertThat(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)).isEqualTo(responseId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void preservesValidCallerCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "trace_123-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("trace_123-abc");
    }
}
