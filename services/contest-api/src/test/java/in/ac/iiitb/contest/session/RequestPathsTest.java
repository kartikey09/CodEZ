package in.ac.iiitb.contest.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The regression test for the Day-15 authorisation-bypass fix.
 *
 * Each string below is routed by Spring to an admin handler, but none of them literally starts with
 * "/api/admin/". Before canonicalisation the session filter compared the raw URI against that
 * prefix, so every one of them reached an admin endpoint with the admin check skipped. The
 * assertion that matters is the last one in {@link #bypassVectorsAreRecognisedAsAdminRoutes}:
 * after canonicalisation the gate sees them for what they are.
 */
class RequestPathsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/admin/contests",            // the honest form
        "/api/%61dmin/contests",          // percent-encoded 'a'
        "/api/%2561dmin/contests",        // double-encoded
        "/api//admin/contests",           // duplicate slash
        "/api/./admin/contests",          // dot segment
        "/api/problems/../admin/contests",// parent segment
        "/api/admin/contests;jsessionid=x", // path parameter
        "/api/admin/contests/",           // trailing slash
    })
    void bypassVectorsAreRecognisedAsAdminRoutes(String raw) {
        String canonical = RequestPaths.canonical(raw);
        assertEquals("/api/admin/contests", canonical, "canonical form of " + raw);
        assertTrue(AuthPaths.isAdminOnly(canonical), raw + " must be gated as an admin route");
    }

    @Test
    void ordinaryPathsAreUnchanged() {
        assertEquals("/api/problems", RequestPaths.canonical("/api/problems"));
        assertEquals("/api/problems/12", RequestPaths.canonical("/api/problems/12"));
        assertEquals("/api/health", RequestPaths.canonical("/api/health"));
        assertEquals("/actuator/health", RequestPaths.canonical("/actuator/health"));
    }

    @Test
    void publicAndAdminClassificationSurvivesCanonicalisation() {
        assertTrue(AuthPaths.isPublic(RequestPaths.canonical("/api/health")));
        assertTrue(AuthPaths.isPublic(RequestPaths.canonical("/actuator/health")));
        assertFalse(AuthPaths.isAdminOnly(RequestPaths.canonical("/api/problems")));
        // "/api/administration" must NOT be treated as an admin route by a sloppy prefix match
        assertFalse(AuthPaths.isAdminOnly(RequestPaths.canonical("/api/administration")));
    }

    @Test
    void plusIsNotRewrittenToSpace() {
        // URLDecoder would turn '+' into a space; in a path it is a literal plus.
        assertEquals("/api/problems/a+b", RequestPaths.canonical("/api/problems/a+b"));
    }

    @Test
    void traversalCannotEscapeAboveRoot() {
        assertEquals("/api/problems", RequestPaths.canonical("/../../../api/problems"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/%2fadmin/contests",   // encoded slash
        "/api/%5Cadmin/contests",   // encoded backslash
        "/api/admin%00/contests",   // null byte
        "/api\\admin/contests",     // raw backslash
    })
    void encodedSeparatorsAreRejectedOutright(String raw) {
        assertTrue(RequestPaths.isSuspicious(raw), raw + " should be refused with 400");
    }

    @Test
    void normalPathsAreNotSuspicious() {
        assertFalse(RequestPaths.isSuspicious("/api/admin/contests"));
        assertFalse(RequestPaths.isSuspicious("/api/problems/12"));
    }

    @Test
    void nullAndEmptyAreHandled() {
        assertTrue(RequestPaths.isSuspicious(null));
        assertEquals("/", RequestPaths.canonical(""));
        assertEquals("/", RequestPaths.canonical("/"));
    }
}
