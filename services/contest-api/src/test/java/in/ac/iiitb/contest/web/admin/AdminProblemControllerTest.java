package in.ac.iiitb.contest.web.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.Problem;
import in.ac.iiitb.contest.contest.ProblemRepository;
import in.ac.iiitb.contest.contest.TestCase;
import in.ac.iiitb.contest.contest.TestCaseRepository;
import in.ac.iiitb.contest.submission.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for problem/test-case authoring. contest-api registers its session filter via
 * FilterRegistrationBean, so it's out of the slice and no auth stubbing is needed.
 */
@WebMvcTest(AdminProblemController.class)
class AdminProblemControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ContestRepository contests;

    @MockBean
    private ProblemRepository problems;

    @MockBean
    private TestCaseRepository tests;

    @MockBean
    private SubmissionRepository submissions;

    private static Problem problem(long id, long contestId) {
        Problem p = new Problem(contestId, "A", "Sum of Two", "Add them.", 1000, 256);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static TestCase test(long id, long problemId, int ordinal, boolean sample) {
        TestCase t = new TestCase(problemId, ordinal, "2 3\n", "5\n", sample);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    @Test
    void listByContestReturnsProblems() throws Exception {
        when(problems.findByContestIdOrderByLabel(1L)).thenReturn(List.of(problem(10, 1), problem(11, 1)));

        mvc.perform(get("/api/admin/problems?contestId=1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(2))
           .andExpect(jsonPath("$[0].label").value("A"));
    }

    @Test
    void detailWithholdsHiddenTestDataButShowsSamples() throws Exception {
        when(problems.findById(10L)).thenReturn(Optional.of(problem(10, 1)));
        when(tests.findByProblemIdOrderByOrdinal(10L))
                .thenReturn(List.of(test(1, 10, 1, true), test(2, 10, 2, false)));
        when(submissions.countPendingByProblem(10L)).thenReturn(0L);

        mvc.perform(get("/api/admin/problems/10"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.testCount").value(2))
           .andExpect(jsonPath("$.pendingSubmissions").value(0))
           // sample: full data is fine, students already see it
           .andExpect(jsonPath("$.tests[0].sample").value(true))
           .andExpect(jsonPath("$.tests[0].input").value("2 3\n"))
           // hidden: data withheld, sizes only
           .andExpect(jsonPath("$.tests[1].sample").value(false))
           .andExpect(jsonPath("$.tests[1].input").doesNotExist())
           .andExpect(jsonPath("$.tests[1].expectedOutput").doesNotExist())
           .andExpect(jsonPath("$.tests[1].inputBytes").value(4));
    }

    @Test
    void detailUnknownProblemIs404() throws Exception {
        when(problems.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/admin/problems/99"))
           .andExpect(status().isNotFound());
    }

    @Test
    void patchAppliesOnlyProvidedFields() throws Exception {
        Problem p = problem(10, 1);
        when(problems.findById(10L)).thenReturn(Optional.of(p));
        when(problems.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(patch("/api/admin/problems/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sum of Three\",\"timeLimitMs\":2000}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("Sum of Three"))
           .andExpect(jsonPath("$.timeLimitMs").value(2000))
           .andExpect(jsonPath("$.label").value("A"));   // untouched
    }

    @Test
    void patchWithNonPositiveLimitIs400() throws Exception {
        when(problems.findById(10L)).thenReturn(Optional.of(problem(10, 1)));

        mvc.perform(patch("/api/admin/problems/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memoryLimitMb\":0}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("INVALID_PROBLEM_UPDATE"));
    }

    @Test
    void patchWithBlankTitleIs400() throws Exception {
        when(problems.findById(10L)).thenReturn(Optional.of(problem(10, 1)));

        mvc.perform(patch("/api/admin/problems/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void uploadRejectsAnOrdinalThatAlreadyExists() throws Exception {
        when(problems.findById(10L)).thenReturn(Optional.of(problem(10, 1)));
        when(tests.findByProblemIdOrderByOrdinal(10L)).thenReturn(List.of(test(1, 10, 1, true)));

        mvc.perform(post("/api/admin/problems/10/test-cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tests\":[{\"ordinal\":1,\"input\":\"x\",\"expectedOutput\":\"y\",\"sample\":false}]}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("INVALID_PROBLEM_UPDATE"));
    }

    @Test
    void uploadBumpsTestDataVersionSoWorkersDropStaleTests() throws Exception {
        Problem p = problem(10, 1);
        int before = p.getTestDataVersion();
        when(problems.findById(10L)).thenReturn(Optional.of(p));
        when(tests.findByProblemIdOrderByOrdinal(10L)).thenReturn(List.of());
        when(problems.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/problems/10/test-cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tests\":[{\"ordinal\":1,\"input\":\"x\",\"expectedOutput\":\"y\",\"sample\":false}]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.added").value(1));

        org.junit.jupiter.api.Assertions.assertEquals(before + 1, p.getTestDataVersion());
    }

    @Test
    void deleteRemovesTheTestAndBumpsVersion() throws Exception {
        Problem p = problem(10, 1);
        int before = p.getTestDataVersion();
        TestCase t = test(2, 10, 2, false);
        when(problems.findById(10L)).thenReturn(Optional.of(p));
        when(tests.findByProblemIdAndOrdinal(10L, 2)).thenReturn(Optional.of(t));
        when(problems.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(delete("/api/admin/problems/10/test-cases/2"))
           .andExpect(status().isNoContent());

        verify(tests).delete(t);
        org.junit.jupiter.api.Assertions.assertEquals(before + 1, p.getTestDataVersion());
    }

    @Test
    void deleteUnknownOrdinalIs404() throws Exception {
        when(problems.findById(10L)).thenReturn(Optional.of(problem(10, 1)));
        when(tests.findByProblemIdAndOrdinal(10L, 9)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/admin/problems/10/test-cases/9"))
           .andExpect(status().isNotFound());
    }
}
