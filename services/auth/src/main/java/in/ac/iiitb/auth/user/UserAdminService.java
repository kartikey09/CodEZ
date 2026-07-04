package in.ac.iiitb.auth.user;

import in.ac.iiitb.auth.error.InvalidRoleException;
import in.ac.iiitb.auth.error.LoginIdTakenException;
import in.ac.iiitb.auth.error.SelfModificationException;
import in.ac.iiitb.auth.error.UserNotFoundException;
import in.ac.iiitb.auth.web.dto.AdminUserView;
import in.ac.iiitb.auth.web.dto.CreateUserRequest;
import in.ac.iiitb.auth.web.dto.CreatedUser;
import in.ac.iiitb.auth.web.dto.ImportResult;
import in.ac.iiitb.auth.web.dto.ResetResult;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * All admin user-management logic. Kept as a plain service (no web types leaking in)
 * so it is unit-testable with a mocked repository and a real encoder.
 *
 * Password policy mirrors the dev seeder exactly: random, per-user, from an alphabet
 * with no ambiguous glyphs (no O/0, I/l/1), bcrypt-hashed at the shared cost, and every
 * account flagged must-change-on-first-login. The plaintext is returned to the admin
 * ONCE and never persisted.
 */
@Service
public class UserAdminService {

    /** No ambiguous characters (no O/0, I/l/1) — same set the seeder uses. */
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LEN = 10;
    private static final Set<String> ROLES = Set.of("student", "admin");

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final SecureRandom rng = new SecureRandom();

    public UserAdminService(UserRepository users, BCryptPasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public List<AdminUserView> list() {
        return users.findAll().stream().map(AdminUserView::of).toList();
    }

    @Transactional
    public CreatedUser create(CreateUserRequest req) {
        String loginId = req.loginId().trim();
        String displayName = req.displayName().trim();
        String role = normaliseRole(req.role());
        if (users.findByLoginId(loginId).isPresent()) {
            throw new LoginIdTakenException(loginId);
        }
        boolean generated = req.password() == null || req.password().isBlank();
        String plaintext = generated ? randomPassword() : req.password();
        User u = new User(loginId, encoder.encode(plaintext), displayName, role);
        // A brand-new user already defaults to active + must-change in the entity; be explicit.
        u.setActive(true);
        u.setMustChangePassword(true);
        User saved = users.save(u);
        // Only reveal a password we generated; if the admin chose one, there's nothing to echo.
        return new CreatedUser(saved.getId(), saved.getLoginId(), saved.getDisplayName(),
                saved.getRole(), generated ? plaintext : null);
    }

    /**
     * Best-effort roster import. Each valid row becomes an account with a generated
     * password; invalid or duplicate rows are collected in {@code skipped} rather than
     * failing the batch. Duplicate detection covers both existing users and repeats
     * within the same file.
     */
    @Transactional
    public ImportResult importCsv(String csv) {
        List<CreatedUser> created = new ArrayList<>();
        List<ImportResult.Skipped> skipped = new ArrayList<>();
        Set<String> seenInBatch = new HashSet<>();

        String[] lines = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            int lineNo = i + 1;
            if (raw.isBlank()) {
                continue;
            }
            List<String> fields = parseCsvLine(raw);
            String loginId = fields.isEmpty() ? "" : fields.get(0).trim();
            // Skip a header row if the first cell looks like a column name.
            if (i == 0 && (loginId.equalsIgnoreCase("loginId") || loginId.equalsIgnoreCase("login_id"))) {
                continue;
            }
            String displayName = fields.size() > 1 ? fields.get(1).trim() : "";
            String roleRaw = fields.size() > 2 ? fields.get(2).trim() : "";

            if (loginId.isEmpty() || displayName.isEmpty()) {
                skipped.add(new ImportResult.Skipped(lineNo, loginId, "missing login id or display name"));
                continue;
            }
            String role;
            try {
                role = normaliseRole(roleRaw);
            } catch (InvalidRoleException e) {
                skipped.add(new ImportResult.Skipped(lineNo, loginId, "invalid role '" + roleRaw + "'"));
                continue;
            }
            if (!seenInBatch.add(loginId.toLowerCase()) || users.findByLoginId(loginId).isPresent()) {
                skipped.add(new ImportResult.Skipped(lineNo, loginId, "duplicate login id"));
                continue;
            }
            String plaintext = randomPassword();
            User u = new User(loginId, encoder.encode(plaintext), displayName, role);
            u.setActive(true);
            u.setMustChangePassword(true);
            User saved = users.save(u);
            created.add(new CreatedUser(saved.getId(), saved.getLoginId(), saved.getDisplayName(),
                    saved.getRole(), plaintext));
        }
        return new ImportResult(created, skipped);
    }

    @Transactional
    public ResetResult resetPassword(long id) {
        User u = users.findById(id).orElseThrow(UserNotFoundException::new);
        String plaintext = randomPassword();
        u.setPasswordHash(encoder.encode(plaintext));
        u.setMustChangePassword(true);
        users.save(u);
        return new ResetResult(u.getId(), u.getLoginId(), plaintext);
    }

    @Transactional
    public AdminUserView setActive(long id, boolean active, long actingUserId) {
        User u = users.findById(id).orElseThrow(UserNotFoundException::new);
        if (!active && u.getId() == actingUserId) {
            throw new SelfModificationException(); // don't let an admin deactivate themselves
        }
        u.setActive(active);
        users.save(u);
        return AdminUserView.of(u);
    }

    @Transactional
    public AdminUserView setRole(long id, String roleRaw, long actingUserId) {
        String role = normaliseRole(roleRaw);
        User u = users.findById(id).orElseThrow(UserNotFoundException::new);
        if (u.getId() == actingUserId && !"admin".equals(role)) {
            throw new SelfModificationException(); // don't let an admin demote themselves
        }
        u.setRole(role);
        users.save(u);
        return AdminUserView.of(u);
    }

    private String normaliseRole(String role) {
        if (role == null || role.isBlank()) {
            return "student";
        }
        String r = role.trim().toLowerCase();
        if (!ROLES.contains(r)) {
            throw new InvalidRoleException();
        }
        return r;
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LEN);
        for (int i = 0; i < PASSWORD_LEN; i++) {
            sb.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Minimal CSV field splitter for one line: comma-separated, with support for
     * double-quoted fields (and "" as an escaped quote inside them). Multi-line quoted
     * fields are not supported — rosters don't need them.
     */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
