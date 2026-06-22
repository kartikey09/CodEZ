package in.ac.iiitb.orchestrator.judge0;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Batch GET wraps the rows: {"submissions": [ ... ]}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Judge0Batch(List<Judge0Result> submissions) {
}
