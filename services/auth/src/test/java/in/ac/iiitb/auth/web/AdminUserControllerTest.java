package in.ac.iiitb.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import in.ac.iiitb.auth.event.AuthEventService;
import in.ac.iiitb.auth.session.SessionService;
import in.ac.iiitb.auth.user.UserAdminService;
import in.ac.iiitb.auth.web.admin.AdminUserController;
import in.ac.iiitb.auth.web.dto.AdminUserView;
import in.ac.iiitb.auth.web.dto.CreatedUser;
import in.ac.iiitb.auth.web.dto.ImportResult;
import in.ac.iiitb.auth.web.dto.ResetResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice test. Filters are disabled (addFilters = false) so the endpoints are
 * exercised in isolation — the admin role gate lives in SessionAuthFilter and is proven
 * end-to-end by the Day-12 .http suite (a student gets 403 on these routes). The
 * @RestControllerAdvice is loaded by the slice, so validation surfaces as a real 400.
 */
@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserAdminService service;

    // The filter isn't run here, but @WebMvcTest may still wire it; give it a mock dep.
    @MockBean
    private SessionService sessions;

    // Day 15: AdminUserController now writes an audit event for every action.
    @MockBean
    private AuthEventService events;

    @Test
    void listReturnsUsers() throws Exception {
        when(service.list()).thenReturn(List.of(
                new AdminUserView(1, "admin", "Admin", "admin", true, false, null),
                new AdminUserView(2, "stud001", "Student One", "student", true, true, null)));

        mvc.perform(get("/auth/admin/users"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].loginId").value("admin"))
           .andExpect(jsonPath("$[1].role").value("student"))
           .andExpect(jsonPath("$[1].mustChangePassword").value(true));
    }

    @Test
    void createReturns201WithGeneratedPassword() throws Exception {
        when(service.create(any())).thenReturn(
                new CreatedUser(5, "stud005", "Student Five", "student", "Kx7mNp2rat"));

        mvc.perform(post("/auth/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"stud005\",\"displayName\":\"Student Five\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.loginId").value("stud005"))
           .andExpect(jsonPath("$.initialPassword").value("Kx7mNp2rat"));
    }

    @Test
    void createWithBlankLoginIdIs400() throws Exception {
        mvc.perform(post("/auth/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"\",\"displayName\":\"No Login\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void importReturnsCreatedAndSkipped() throws Exception {
        when(service.importCsv(any())).thenReturn(new ImportResult(
                List.of(new CreatedUser(9, "alice", "Alice", "student", "abcdef2345")),
                List.of(new ImportResult.Skipped(3, "bob", "duplicate login id"))));

        mvc.perform(post("/auth/admin/users/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"csv\":\"alice,Alice\\nbob,Bob\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.created[0].loginId").value("alice"))
           .andExpect(jsonPath("$.skipped[0].reason").value("duplicate login id"));
    }

    @Test
    void resetPasswordReturnsNewSecret() throws Exception {
        when(service.resetPassword(7L)).thenReturn(new ResetResult(7, "stud007", "New2Pass34"));

        mvc.perform(post("/auth/admin/users/7/reset-password"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.initialPassword").value("New2Pass34"));
    }

    @Test
    void deactivateReturnsUpdatedView() throws Exception {
        when(service.setActive(eq(3L), eq(false), anyLong()))
                .thenReturn(new AdminUserView(3, "stud003", "Student Three", "student", false, false, null));

        mvc.perform(post("/auth/admin/users/3/deactivate"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void setRoleReturnsUpdatedView() throws Exception {
        when(service.setRole(eq(4L), eq("admin"), anyLong()))
                .thenReturn(new AdminUserView(4, "stud004", "Student Four", "admin", true, false, null));

        mvc.perform(patch("/auth/admin/users/4/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"admin\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.role").value("admin"));
    }
}
