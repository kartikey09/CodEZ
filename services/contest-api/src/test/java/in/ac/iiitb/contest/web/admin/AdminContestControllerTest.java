package in.ac.iiitb.contest.web.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for the Day-13 contest list + timing/state edits. The session filter is
 * registered via FilterRegistrationBean (not a @Component), so it's absent from the
 * slice and no auth stubbing is needed — same as HealthControllerTest.
 */
@WebMvcTest(AdminContestController.class)
class AdminContestControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ContestRepository contests;

    private static Contest contest(long id, String state) {
        Contest c = new Contest("Weekly Contest", Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T13:00:00Z"), state);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    @Test
    void listReturnsAllContests() throws Exception {
        when(contests.findAll()).thenReturn(List.of(contest(1, "finished"), contest(2, "running")));

        mvc.perform(get("/api/admin/contests"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].state").value("finished"))
           .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void patchUpdatesStateAndWindow() throws Exception {
        when(contests.findById(2L)).thenReturn(Optional.of(contest(2, "draft")));
        when(contests.save(any(Contest.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(patch("/api/admin/contests/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"running\",\"endsAt\":\"2026-07-01T14:00:00Z\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.state").value("running"))
           .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void patchWithUnknownStateIs400() throws Exception {
        when(contests.findById(2L)).thenReturn(Optional.of(contest(2, "draft")));

        mvc.perform(patch("/api/admin/contests/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"paused\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("INVALID_CONTEST_UPDATE"));
    }

    @Test
    void patchWithEndBeforeStartIs400() throws Exception {
        when(contests.findById(2L)).thenReturn(Optional.of(contest(2, "draft")));

        mvc.perform(patch("/api/admin/contests/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endsAt\":\"2026-07-01T09:00:00Z\"}")) // before the 10:00 start
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("INVALID_CONTEST_UPDATE"));
    }

    @Test
    void patchUnknownContestIs404() throws Exception {
        when(contests.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(patch("/api/admin/contests/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"running\"}"))
           .andExpect(status().isNotFound());
    }
}
