package in.ac.iiitb.auth.session;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Canonicalises the request path before any authorisation decision is made.
 *
 * WHY THIS EXISTS. The session filter used to match on {@code HttpServletRequest#getRequestURI()},
 * which is the RAW path exactly as the client sent it — not decoded, not normalised. Spring maps
 * the request to a controller using the DECODED, NORMALISED path. Any difference between those two
 * strings is an authorisation bypass: a request can be routed to an admin controller while the
 * filter's {@code startsWith("/api/admin/")} test sees something else and waves it through.
 * Concretely, all of these reach an admin handler but do not literally start with the admin prefix:
 *
 *   /api/%61dmin/contests      percent-encoded 'a'
 *   /api//admin/contests       duplicate slash
 *   /api/./admin/contests      dot segment
 *   /api/x/../admin/contests   parent segment
 *   /api/admin/contests;a=b    path parameter
 *
 * So the filter now decides on {@link #canonical(String)}, and refuses outright anything whose raw
 * form contains an encoded path separator or a null byte ({@link #isSuspicious(String)}) — those
 * have no legitimate use here and are only ever seen in traversal attempts.
 */
public final class RequestPaths {

    /** Decoding is repeated until stable so double-encoding (%2561 -> %61 -> a) cannot slip past. */
    private static final int MAX_DECODE_PASSES = 3;

    private RequestPaths() {
    }

    /** Encoded separators / null bytes: never legitimate here, so fail closed with a 400. */
    public static boolean isSuspicious(String rawUri) {
        if (rawUri == null) {
            return true;
        }
        String lower = rawUri.toLowerCase(Locale.ROOT);
        return lower.contains("%2f")      // encoded '/'
            || lower.contains("%5c")      // encoded '\'
            || lower.contains("%25")      // encoded '%' — the double-encoding vector
            || lower.contains("%00")      // null byte
            || rawUri.indexOf('\\') >= 0
            || rawUri.indexOf('\0') >= 0;
    }

    /**
     * The path an authorisation rule should be evaluated against: query and path parameters removed,
     * percent-decoded, duplicate slashes collapsed, and '.'/'..' segments resolved.
     */
    public static String canonical(String rawUri) {
        if (rawUri == null || rawUri.isEmpty()) {
            return "/";
        }
        String p = rawUri;

        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        int semi = p.indexOf(';');          // /path;jsessionid=... and friends
        if (semi >= 0) {
            p = p.substring(0, semi);
        }
        for (int i = 0; i < MAX_DECODE_PASSES; i++) {
            String decoded = percentDecode(p);
            if (decoded.equals(p)) {
                break;
            }
            p = decoded;
        }
        p = p.replace('\\', '/');

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : p.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;                    // collapses // and /./
            }
            if ("..".equals(segment)) {
                segments.pollLast();         // never escapes above root
                continue;
            }
            segments.addLast(segment);
        }
        return "/" + String.join("/", segments);
    }

    /**
     * Percent-decoding for PATHS. Deliberately not URLDecoder: that treats '+' as a space, which is
     * a query-string rule — in a path '+' is a literal plus, and rewriting it would corrupt the path.
     */
    private static String percentDecode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) + lo);
                    i += 2;
                    continue;
                }
            }
            out.write(String.valueOf(c).getBytes(StandardCharsets.UTF_8), 0,
                String.valueOf(c).getBytes(StandardCharsets.UTF_8).length);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
