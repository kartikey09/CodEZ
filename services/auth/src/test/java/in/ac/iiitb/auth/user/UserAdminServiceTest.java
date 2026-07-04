package in.ac.iiitb.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import in.ac.iiitb.auth.error.InvalidRoleException;
import in.ac.iiitb.auth.error.LoginIdTakenException;
import in.ac.iiitb.auth.error.SelfModificationException;
import in.ac.iiitb.auth.error.UserNotFoundException;
import in.ac.iiitb.auth.web.dto.CreateUserRequest;
import in.ac.iiitb.auth.web.dto.CreatedUser;
import in.ac.iiitb.auth.web.dto.ImportResult;
import in.ac.iiitb.auth.web.dto.ResetResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository users;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost = fast tests
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(users, encoder);
        // Simulate the DB assigning an id on save so the returned DTOs carry a real id.
        AtomicLong seq = new AtomicLong(1);
        lenient().when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                ReflectionTestUtils.setField(u, "id", seq.getAndIncrement());
            }
            return u;
        });
    }

    @Test
    void createGeneratesPasswordFlagsMustChangeAndHashes() {
        when(users.findByLoginId("stud100")).thenReturn(Optional.empty());

        CreatedUser created = service.create(new CreateUserRequest("stud100", "New Student", null, null));

        assertThat(created.loginId()).isEqualTo("stud100");
        assertThat(created.role()).isEqualTo("student");           // default
        assertThat(created.initialPassword()).isNotBlank();         // generated, revealed once
        assertThat(created.initialPassword()).hasSize(10);

        // The persisted user is hashed (not plaintext), active, and must-change.
        User saved = savedUser();
        assertThat(saved.getPasswordHash()).isNotEqualTo(created.initialPassword());
        assertThat(encoder.matches(created.initialPassword(), saved.getPasswordHash())).isTrue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isMustChangePassword()).isTrue();
    }

    @Test
    void createWithSuppliedPasswordDoesNotEchoIt() {
        when(users.findByLoginId("stud101")).thenReturn(Optional.empty());

        CreatedUser created = service.create(
                new CreateUserRequest("stud101", "Chosen Pw", "admin", "hunter2xy"));

        assertThat(created.role()).isEqualTo("admin");
        assertThat(created.initialPassword()).isNull();             // nothing to reveal
        assertThat(encoder.matches("hunter2xy", savedUser().getPasswordHash())).isTrue();
    }

    @Test
    void createRejectsDuplicateLoginId() {
        when(users.findByLoginId("dupe")).thenReturn(Optional.of(new User("dupe", "h", "Dupe", "student")));

        assertThatThrownBy(() -> service.create(new CreateUserRequest("dupe", "Dupe", null, null)))
                .isInstanceOf(LoginIdTakenException.class);
    }

    @Test
    void createRejectsInvalidRole() {
        assertThatThrownBy(() -> service.create(new CreateUserRequest("x", "X", "superuser", null)))
                .isInstanceOf(InvalidRoleException.class);
    }

    @Test
    void importCreatesValidRowsSkipsHeaderDuplicatesAndBadRows() {
        // Nothing exists yet except "taken".
        when(users.findByLoginId(any())).thenReturn(Optional.empty());
        when(users.findByLoginId("taken")).thenReturn(Optional.of(new User("taken", "h", "T", "student")));

        String csv = String.join("\n",
                "loginId,displayName,role",   // header — skipped
                "alice,Alice Example,student", // ok
                "bob,Bob Example",             // ok, role defaults to student
                "carol,Carol,admin",           // ok, admin
                "taken,Already There,student", // skipped: exists
                "alice,Alice Twice,student",   // skipped: duplicate within batch
                "dave,Dave,wizard",            // skipped: bad role
                ",No Login,student",           // skipped: missing login id
                "");                            // blank — ignored

        ImportResult result = service.importCsv(csv);

        assertThat(result.created()).extracting(CreatedUser::loginId)
                .containsExactly("alice", "bob", "carol");
        assertThat(result.created()).allSatisfy(c -> assertThat(c.initialPassword()).hasSize(10));
        assertThat(result.created().get(1).role()).isEqualTo("student"); // bob defaulted
        assertThat(result.created().get(2).role()).isEqualTo("admin");   // carol

        assertThat(result.skipped()).extracting(ImportResult.Skipped::loginId)
                .containsExactlyInAnyOrder("taken", "alice", "dave", "");
    }

    @Test
    void resetPasswordRotatesHashAndForcesChange() {
        User u = new User("stud1", encoder.encode("oldpassword"), "Stud", "student");
        ReflectionTestUtils.setField(u, "id", 7L);
        u.setMustChangePassword(false);
        when(users.findById(7L)).thenReturn(Optional.of(u));

        ResetResult res = service.resetPassword(7L);

        assertThat(res.initialPassword()).hasSize(10);
        assertThat(encoder.matches(res.initialPassword(), u.getPasswordHash())).isTrue();
        assertThat(encoder.matches("oldpassword", u.getPasswordHash())).isFalse();
        assertThat(u.isMustChangePassword()).isTrue();
    }

    @Test
    void resetPasswordUnknownUserThrows() {
        when(users.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resetPassword(999L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deactivateOthersWorksButNotYourself() {
        User other = userWithId(2L, "other", "student");
        when(users.findById(2L)).thenReturn(Optional.of(other));
        assertThat(service.setActive(2L, false, 1L).active()).isFalse();

        User me = userWithId(1L, "admin", "admin");
        when(users.findById(1L)).thenReturn(Optional.of(me));
        assertThatThrownBy(() -> service.setActive(1L, false, 1L))
                .isInstanceOf(SelfModificationException.class);
    }

    @Test
    void setRoleChangesRoleButBlocksSelfDemotion() {
        User other = userWithId(2L, "other", "student");
        when(users.findById(2L)).thenReturn(Optional.of(other));
        assertThat(service.setRole(2L, "admin", 1L).role()).isEqualTo("admin");

        User me = userWithId(1L, "admin", "admin");
        when(users.findById(1L)).thenReturn(Optional.of(me));
        assertThatThrownBy(() -> service.setRole(1L, "student", 1L))
                .isInstanceOf(SelfModificationException.class);
    }

    @Test
    void setRoleRejectsInvalidRole() {
        assertThatThrownBy(() -> service.setRole(2L, "wizard", 1L))
                .isInstanceOf(InvalidRoleException.class);
    }

    // ---- helpers ----

    private User savedUser() {
        org.mockito.ArgumentCaptor<User> cap = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(users).save(cap.capture());
        return cap.getValue();
    }

    private static User userWithId(long id, String loginId, String role) {
        User u = new User(loginId, "hash", loginId, role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}
