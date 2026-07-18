package in.ac.iiitb.contest.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import in.ac.iiitb.contest.contest.Announcement;
import in.ac.iiitb.contest.contest.AnnouncementRepository;
import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.web.admin.AdminAnnouncementController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test spanning both announcement controllers (admin write side + student read
 * side). Repositories are mocked; the session filter is out of the slice.
 */
@WebMvcTest({AdminAnnouncementController.class, AnnouncementController.class})
class AnnouncementControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AnnouncementRepository announcements;

    @MockBean
    private ContestRepository contests;

    private static Announcement ann(long id, long contestId, String msg, boolean active) {
        Announcement a = new Announcement(contestId, msg);
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(a, "active", active);
        ReflectionTestUtils.setField(a, "createdAt", Instant.parse("2026-07-01T11:00:00Z"));
        return a;
    }

    private static Contest runningContest(long id) {
        Contest c = new Contest("Weekly", Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T13:00:00Z"), "running");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    @Test
    void adminCreateReturns201() throws Exception {
        when(contests.findById(1L)).thenReturn(Optional.of(runningContest(1)));
        when(announcements.save(any(Announcement.class))).thenAnswer(inv -> {
            Announcement a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 10L);
            ReflectionTestUtils.setField(a, "createdAt", Instant.parse("2026-07-01T11:00:00Z"));
            return a;
        });

        mvc.perform(post("/api/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contestId\":1,\"message\":\"Clarification: assume 1-indexed input.\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.contestId").value(1))
           .andExpect(jsonPath("$.active").value(true))
           .andExpect(jsonPath("$.message").value("Clarification: assume 1-indexed input."));
    }

    @Test
    void adminCreateForUnknownContestIs404() throws Exception {
        when(contests.findById(77L)).thenReturn(Optional.empty());

        mvc.perform(post("/api/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contestId\":77,\"message\":\"hi\"}"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("NO_CONTEST_FOUND"));
    }

    @Test
    void adminCreateWithBlankMessageIs400() throws Exception {
        mvc.perform(post("/api/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contestId\":1,\"message\":\"\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adminDeactivateFlipsActive() throws Exception {
        when(announcements.findById(10L)).thenReturn(Optional.of(ann(10, 1, "msg", true)));
        when(announcements.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/announcements/10/deactivate"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void studentGetReturnsActiveForRunningContest() throws Exception {
        when(contests.findFirstByStateOrderByStartsAtDesc("running")).thenReturn(Optional.of(runningContest(1)));
        when(announcements.findByContestIdAndActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(ann(10, 1, "Notice one", true), ann(9, 1, "Notice two", true)));

        mvc.perform(get("/api/announcements"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].message").value("Notice one"))
           .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void studentGetReturnsEmptyWhenNoRunningContest() throws Exception {
        when(contests.findFirstByStateOrderByStartsAtDesc("running")).thenReturn(Optional.empty());

        mvc.perform(get("/api/announcements"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(0));
    }
}
