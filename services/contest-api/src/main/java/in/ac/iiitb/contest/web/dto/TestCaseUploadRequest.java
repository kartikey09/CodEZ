package in.ac.iiitb.contest.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record TestCaseUploadRequest(@NotEmpty List<@Valid TestCaseUpload> tests) {
}
