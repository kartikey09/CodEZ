package in.ac.iiitb.contest.web.dto;

import java.time.Instant;

public record ContestResponse(long id,
                              String title,
                              Instant startsAt,
                              Instant endsAt,
                              String state) {
}
