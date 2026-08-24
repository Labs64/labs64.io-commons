package io.labs64.authcontext.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PublicPathMatcherTest {

    @Test
    void emptyMatcherMatchesNothing() {
        PublicPathMatcher matcher = PublicPathMatcher.empty();
        assertThat(matcher.matches("GET", "/payment-definitions")).isFalse();
    }

    @Test
    void matchesLiteralPathForDeclaredMethodOnly() {
        PublicPathMatcher matcher = PublicPathMatcher.fromLines(List.of("GET /payment-definitions"));
        assertThat(matcher.matches("GET", "/payment-definitions")).isTrue();
        // method-sensitive: a protected POST on the same path stays protected
        assertThat(matcher.matches("POST", "/payment-definitions")).isFalse();
        // no accidental prefix match
        assertThat(matcher.matches("GET", "/payment-definitions-secret")).isFalse();
    }

    @Test
    void methodComparisonIsCaseInsensitive() {
        PublicPathMatcher matcher = PublicPathMatcher.fromLines(List.of("get /health"));
        assertThat(matcher.matches("GET", "/health")).isTrue();
    }

    @Test
    void pathTemplateParamMatchesExactlyOneSegment() {
        PublicPathMatcher matcher = PublicPathMatcher.fromLines(
                List.of("POST /providers/{provider}/webhooks"));
        assertThat(matcher.matches("POST", "/providers/stripe/webhooks")).isTrue();
        // param is a single segment, not a slash-spanning wildcard
        assertThat(matcher.matches("POST", "/providers/stripe/extra/webhooks")).isFalse();
        // trailing segment must be present
        assertThat(matcher.matches("POST", "/providers/stripe")).isFalse();
    }

    @Test
    void toleratesSingleTrailingSlash() {
        PublicPathMatcher matcher = PublicPathMatcher.fromLines(List.of("GET /payment-definitions"));
        assertThat(matcher.matches("GET", "/payment-definitions/")).isTrue();
    }

    @Test
    void ignoresBlankLinesAndComments() {
        PublicPathMatcher matcher = PublicPathMatcher.fromLines(List.of(
                "# GENERATED — do not edit",
                "",
                "   ",
                "GET /payment-definitions"));
        assertThat(matcher.matches("GET", "/payment-definitions")).isTrue();
    }

    @Test
    void rejectsMalformedLineWithoutMethod() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> PublicPathMatcher.fromLines(List.of("/no-method")));
    }
}
