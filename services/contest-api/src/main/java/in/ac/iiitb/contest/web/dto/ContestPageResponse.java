package in.ac.iiitb.contest.web.dto;

import java.util.List;

/** GET /api/contests page (ContestPageResponse). Zero-indexed, newest contest first. */
public record ContestPageResponse(
        List<ContestResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
