package in.ac.iiitb.contest.web;

import java.time.Instant;

import in.ac.iiitb.contest.web.dto.TimeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TimeController {

    // Requires a session (the filter enforces it for any non-public path).
    @GetMapping("/time")
    public TimeResponse time() {
        Instant now = Instant.now();
        return new TimeResponse(now.toString(), now.toEpochMilli());
    }
}
