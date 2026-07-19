package in.ac.iiitb.auth.security;

import in.ac.iiitb.auth.error.LoginThrottledException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Rate-limits failed logins (Day 15 — the hardening item deferred from Day 3).
 *
 * Two independent counters, because they stop different attacks:
 *   - per ACCOUNT, which stops someone grinding one student's password;
 *   - per SOURCE IP, which stops spraying one password across the whole roster.
 * Either tripping locks that dimension for {@code lockoutSeconds}.
 *
 * Counting is keyed on the SUBMITTED login id whether or not that account exists, so the throttle
 * itself can never be used to test which login ids are real.
 *
 * Deliberately fail-open on a Redis outage: contest day is 200-300 students logging in at once, and
 * a Redis blip locking everyone out of an exam is a worse outcome than a brief loss of throttling.
 */
@Component
public class LoginThrottle {

    private final StringRedisTemplate redis;
    private final int maxAccountFailures;
    private final int maxIpFailures;
    private final Duration window;
    private final Duration lockout;

    public LoginThrottle(StringRedisTemplate redis,
                         @Value("${app.login.max-account-failures:8}") int maxAccountFailures,
                         @Value("${app.login.max-ip-failures:30}") int maxIpFailures,
                         @Value("${app.login.window-seconds:300}") long windowSeconds,
                         @Value("${app.login.lockout-seconds:900}") long lockoutSeconds) {
        this.redis = redis;
        this.maxAccountFailures = maxAccountFailures;
        this.maxIpFailures = maxIpFailures;
        this.window = Duration.ofSeconds(windowSeconds);
        this.lockout = Duration.ofSeconds(lockoutSeconds);
    }

    private static String norm(String loginId) {
        return loginId == null ? "" : loginId.trim().toLowerCase(Locale.ROOT);
    }

    private static String failKey(String loginId) { return "login:fail:acct:" + norm(loginId); }
    private static String lockKey(String loginId) { return "login:lock:acct:" + norm(loginId); }
    private static String ipFailKey(String ip)    { return "login:fail:ip:" + ip; }
    private static String ipLockKey(String ip)    { return "login:lock:ip:" + ip; }

    /** Called BEFORE any password check. Throws 429 while a lockout is in force. */
    public void assertNotLocked(String loginId, String ip) {
        long remaining = Math.max(secondsLeft(lockKey(loginId)), ip == null ? -1 : secondsLeft(ipLockKey(ip)));
        if (remaining > 0) {
            throw new LoginThrottledException(remaining);
        }
    }

    /** Record a failed attempt. Returns true if this attempt tripped a lockout. */
    public boolean recordFailure(String loginId, String ip) {
        boolean locked = bump(failKey(loginId), lockKey(loginId), maxAccountFailures);
        if (ip != null) {
            locked |= bump(ipFailKey(ip), ipLockKey(ip), maxIpFailures);
        }
        return locked;
    }

    /** A good password clears that account's counter (and its share of the IP counter). */
    public void recordSuccess(String loginId, String ip) {
        try {
            redis.delete(failKey(loginId));
            redis.delete(lockKey(loginId));
            if (ip != null) {
                redis.delete(ipFailKey(ip));
            }
        } catch (Exception e) {
            // fail open — see class javadoc
        }
    }

    private boolean bump(String failKey, String lockKey, int max) {
        try {
            Long count = redis.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redis.expire(failKey, window);       // first failure starts the sliding window
            }
            if (count != null && count >= max) {
                redis.opsForValue().set(lockKey, "1", lockout);
                redis.delete(failKey);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;                            // fail open
        }
    }

    private long secondsLeft(String key) {
        try {
            Long ttl = redis.getExpire(key);
            return ttl == null ? -1 : ttl;
        } catch (Exception e) {
            return -1;
        }
    }
}
