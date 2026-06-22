package in.ac.iiitb.orchestrator.judge0;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Judge0 result row. stdout/stderr/compileOutput are base64 on the wire and are
 * decoded by the client before this is handed back. time is seconds as a string
 * (e.g. "0.012"); memory is in kilobytes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Judge0Result(
        String token,
        Judge0Status status,
        String stdout,
        String stderr,
        @JsonProperty("compile_output") String compileOutput,
        String time,
        Integer memory) {
}
