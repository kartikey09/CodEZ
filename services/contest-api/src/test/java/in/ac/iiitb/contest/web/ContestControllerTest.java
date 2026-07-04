package in.ac.iiitb.contest.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContestController.class)
class ContestControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ContestRepository contests;

    @Test
    void returnsTheRunningContest() throws Exception {
        Contest c = new Contest("Demo Round",
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2030-01-01T00:00:00Z"),
                "running");
        ReflectionTestUtils.setField(c, "id", 1L); // normally assigned by JPA on persist
        when(contests.findFirstByStateOrderByStartsAtDesc("running")).thenReturn(Optional.of(c));

        mvc.perform(get("/api/contest"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("Demo Round"))
           .andExpect(jsonPath("$.state").value("running"))
           .andExpect(jsonPath("$.serverEpochMillis").isNumber());
    }

    @Test
    void isNotFoundWhenNothingIsRunning() throws Exception {
        when(contests.findFirstByStateOrderByStartsAtDesc("running")).thenReturn(Optional.empty());

        mvc.perform(get("/api/contest"))
           .andExpect(status().isNotFound());
    }
}
