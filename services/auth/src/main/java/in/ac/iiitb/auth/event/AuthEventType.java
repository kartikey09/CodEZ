package in.ac.iiitb.auth.event;

/** The security-relevant things worth keeping a record of. */
public enum AuthEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGIN_LOCKED,          // this failure tripped the throttle
    LOGIN_BLOCKED,         // attempt arrived while a lockout was already in force
    LOGIN_DISABLED,        // right password, deactivated account
    LOGOUT,
    PASSWORD_CHANGED,
    USER_CREATED,
    USERS_IMPORTED,
    PASSWORD_RESET,
    USER_ACTIVATED,
    USER_DEACTIVATED,
    USER_ROLE_CHANGED,
    SESSIONS_REVOKED
}
