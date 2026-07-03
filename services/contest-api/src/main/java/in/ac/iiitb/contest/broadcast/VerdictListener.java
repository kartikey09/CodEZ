package in.ac.iiitb.contest.broadcast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import in.ac.iiitb.contest.submission.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the worker's per-user verdict pings (ch:user:*) purely as a "something in this contest was
 * judged" signal — no worker change needed. The payload only carries {submissionId, verdict}, so we look up
 * the submission to learn its contest, then ask the broadcaster (debounced) to recompute + publish that
 * contest's board.
 */
@Component
public class VerdictListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(VerdictListener.class);

    private final SubmissionRepository submissions;
    private final StandingsBroadcaster broadcaster;
    private final ObjectMapper json;

    public VerdictListener(SubmissionRepository submissions, StandingsBroadcaster broadcaster, ObjectMapper json) {
        this.submissions = submissions;
        this.broadcaster = broadcaster;
        this.json = json;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode node = json.readTree(message.getBody());
            JsonNode idNode = node.get("submissionId");
            if (idNode == null) {
                return;
            }
            long submissionId = idNode.asLong();
            submissions.findById(submissionId)
                    .ifPresent(s -> broadcaster.schedule(s.getContestId()));
        } catch (Exception e) {
            log.warn("ignoring malformed verdict event", e);
        }
    }
}
