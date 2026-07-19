package in.ac.iiitb.auth.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import in.ac.iiitb.auth.error.LoginThrottledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Unit test for the login throttle: the policy is pure logic over a mocked Redis. */
class LoginThrottleTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private LoginThrottle throttle;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        // 3 account failures, 10 IP failures, 5-minute window, 15-minute lockout
        throttle = new LoginThrottle(redis, 3, 10, 300L, 900L);
    }

    private void noLocks() {
        when(redis.getExpire(anyString())).thenReturn(-2L);   // Redis: key does not exist
    }

    @Test
    void allowsLoginWhenNothingIsLocked() {
        noLocks();
        assertDoesNotThrow(() -> throttle.assertNotLocked("stud001", "10.0.0.1"));
    }

    @Test
    void refusesWhileAccountLockoutIsInForce() {
        when(redis.getExpire("login:lock:acct:stud001")).thenReturn(842L);
        when(redis.getExpire("login:lock:ip:10.0.0.1")).thenReturn(-2L);

        LoginThrottledException ex =
            assertThrows(LoginThrottledException.class, () -> throttle.assertNotLocked("stud001", "10.0.0.1"));
        assertTrue(ex.retryAfterSeconds() == 842L);
    }

    @Test
    void refusesWhileIpLockoutIsInForce() {
        when(redis.getExpire("login:lock:acct:someone")).thenReturn(-2L);
        when(redis.getExpire("login:lock:ip:10.0.0.9")).thenReturn(300L);

        assertThrows(LoginThrottledException.class, () -> throttle.assertNotLocked("someone", "10.0.0.9"));
    }

    @Test
    void earlyFailuresDoNotLockOut() {
        when(values.increment("login:fail:acct:stud001")).thenReturn(1L);
        when(values.increment("login:fail:ip:10.0.0.1")).thenReturn(1L);

        assertFalse(throttle.recordFailure("stud001", "10.0.0.1"));
        verify(values, never()).set(eq("login:lock:acct:stud001"), anyString(), any(Duration.class));
    }

    @Test
    void reachingTheAccountThresholdSetsALockout() {
        when(values.increment("login:fail:acct:stud001")).thenReturn(3L);   // == max
        when(values.increment("login:fail:ip:10.0.0.1")).thenReturn(1L);

        assertTrue(throttle.recordFailure("stud001", "10.0.0.1"));
        verify(values).set(eq("login:lock:acct:stud001"), anyString(), any(Duration.class));
    }

    @Test
    void sprayingManyAccountsFromOneAddressTripsTheIpLimit() {
        when(values.increment(anyString())).thenReturn(1L);                  // each account looks innocent
        when(values.increment("login:fail:ip:10.0.0.7")).thenReturn(10L);    // but the source does not

        assertTrue(throttle.recordFailure("whoever", "10.0.0.7"));
        verify(values).set(eq("login:lock:ip:10.0.0.7"), anyString(), any(Duration.class));
    }

    @Test
    void loginIdIsNormalisedSoCaseCannotDodgeTheCounter() {
        when(values.increment("login:fail:acct:stud001")).thenReturn(3L);

        assertTrue(throttle.recordFailure("  StUd001 ", null));
        verify(values).set(eq("login:lock:acct:stud001"), anyString(), any(Duration.class));
    }

    @Test
    void successClearsTheCounters() {
        throttle.recordSuccess("stud001", "10.0.0.1");

        verify(redis).delete("login:fail:acct:stud001");
        verify(redis).delete("login:lock:acct:stud001");
        verify(redis).delete("login:fail:ip:10.0.0.1");
    }

    @Test
    void redisOutageFailsOpenRatherThanLockingEveryoneOut() {
        when(redis.getExpire(anyString())).thenThrow(new RuntimeException("redis down"));
        when(values.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        // 200-300 students logging in at once must not be blocked by an unavailable throttle.
        assertDoesNotThrow(() -> throttle.assertNotLocked("stud001", "10.0.0.1"));
        assertFalse(throttle.recordFailure("stud001", "10.0.0.1"));
        assertDoesNotThrow(() -> throttle.recordSuccess("stud001", "10.0.0.1"));
    }
}
