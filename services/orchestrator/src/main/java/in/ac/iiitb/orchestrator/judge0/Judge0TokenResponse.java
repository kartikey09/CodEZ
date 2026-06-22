package in.ac.iiitb.orchestrator.judge0;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Judge0 returns {"token": "..."} on submit (and an array of these for batch submit). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Judge0TokenResponse(String token) {
}
