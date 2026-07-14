package io.labs64.authcontext.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches a request (method + URI) against the build-generated set of public
 * OpenAPI operations. The generated {@code auth-public-paths} resource carries
 * one {@code <METHOD> <path-template>} line per operation declaring
 * {@code x-labs64-auth.public: true}, so OpenAPI stays the single source of
 * truth for the {@link AuthContextFilter}'s public-path decision — no
 * hand-maintained list.
 *
 * <p>Matching mirrors the traefik-authproxy's template semantics exactly so the
 * edge PEP and the backend fail-closed guard agree: {@code {param}} matches one
 * path segment ({@code [^/]+}), everything else is literal, a single trailing
 * slash is tolerated, and the method must match (case-insensitive). Paths that
 * live outside the OpenAPI surface (actuator, docs, error) are NOT here — those
 * stay configured prefixes on {@link AuthContextProperties#getPublicPaths()}.
 */
public final class PublicPathMatcher {

    private static final Pattern TEMPLATE_PARAM = Pattern.compile("\\{[^/{}]+\\}");

    private record Route(String method, Pattern pattern) {
    }

    private final List<Route> routes;

    private PublicPathMatcher(final List<Route> routes) {
        this.routes = routes;
    }

    /** A matcher with no public operations (module has none, or resource absent). */
    public static PublicPathMatcher empty() {
        return new PublicPathMatcher(List.of());
    }

    /**
     * Builds a matcher from generated {@code <METHOD> <path-template>} lines.
     * Blank lines and {@code #} comments are ignored.
     */
    public static PublicPathMatcher fromLines(final Iterable<String> lines) {
        List<Route> routes = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int sep = line.indexOf(' ');
            if (sep < 0) {
                throw new IllegalArgumentException("Malformed public-path line (expected '<METHOD> <path>'): " + raw);
            }
            String method = line.substring(0, sep).strip().toUpperCase(Locale.ROOT);
            String template = line.substring(sep + 1).strip();
            if (method.isEmpty() || template.isEmpty()) {
                throw new IllegalArgumentException("Malformed public-path line (expected '<METHOD> <path>'): " + raw);
            }
            routes.add(new Route(method, compile(template)));
        }
        return new PublicPathMatcher(routes);
    }

    /** True when the given request method + URI matches a public operation. */
    public boolean matches(final String method, final String uri) {
        String normalizedMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
        for (Route route : routes) {
            if (route.method().equals(normalizedMethod) && route.pattern().matcher(uri).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compiles an OpenAPI path template into an anchored matcher: {@code {param}}
     * becomes one non-slash segment, literals are quoted, and a single trailing
     * slash is tolerated.
     */
    private static Pattern compile(final String template) {
        StringBuilder pattern = new StringBuilder("^");
        Matcher matcher = TEMPLATE_PARAM.matcher(template);
        int last = 0;
        while (matcher.find()) {
            pattern.append(Pattern.quote(template.substring(last, matcher.start())));
            pattern.append("[^/]+");
            last = matcher.end();
        }
        pattern.append(Pattern.quote(template.substring(last)));
        pattern.append("/?$");
        return Pattern.compile(pattern.toString());
    }
}
