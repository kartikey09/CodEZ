package in.ac.iiitb.auth.web.dto;

import in.ac.iiitb.auth.event.AuthEvent;

import java.time.Instant;

/** One audit-trail row as returned to an admin. */
public record AuthEventView(
        long id,
        Instant at,
        String event,
        String loginId,
        Long userId,
        Long actorId,
        String ip,
        String userAgent,
        String detail) {

    public static AuthEventView of(AuthEvent e) {
        return new AuthEventView(e.getId(), e.getAt(), e.getEvent(), e.getLoginId(), e.getUserId(),
            e.getActorId(), e.getIp(), e.getUserAgent(), e.getDetail());
    }
}
