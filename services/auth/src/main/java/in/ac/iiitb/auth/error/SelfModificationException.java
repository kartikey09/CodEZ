package in.ac.iiitb.auth.error;

/**
 * An admin tried to deactivate or demote their own account → 409. Prevents an admin
 * from locking every admin (themselves included) out of the panel by accident.
 */
public class SelfModificationException extends RuntimeException {
}
