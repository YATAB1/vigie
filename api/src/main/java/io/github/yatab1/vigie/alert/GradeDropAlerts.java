package io.github.yatab1.vigie.alert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.yatab1.vigie.domain.Domain;
import io.github.yatab1.vigie.domain.DomainRepository;
import io.github.yatab1.vigie.scan.Scan;
import io.github.yatab1.vigie.scan.ScanCompletedEvent;
import io.github.yatab1.vigie.scan.ScanRepository;
import io.github.yatab1.vigie.scan.ScanStatus;
import io.github.yatab1.vigie.user.User;
import io.github.yatab1.vigie.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Emails the owner of a domain when a new scan gives it a worse grade than the previous one. */
@Component
public class GradeDropAlerts {

    private static final String GRADES = "ABCDF";
    private static final int MAX_LISTED_PROBLEMS = 5;

    private final ScanRepository scans;
    private final DomainRepository domains;
    private final UserRepository users;
    private final AlertSender sender;
    private final AlertProperties properties;
    private final ObjectMapper mapper;

    public GradeDropAlerts(
            ScanRepository scans,
            DomainRepository domains,
            UserRepository users,
            AlertSender sender,
            AlertProperties properties,
            ObjectMapper mapper) {
        this.scans = scans;
        this.domains = domains;
        this.users = users;
        this.sender = sender;
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Runs after the scan is committed, so a failed email never rolls back a report. */
    @TransactionalEventListener
    public void onScanCompleted(ScanCompletedEvent event) {
        List<Scan> lastTwo = scans.findTop2ByDomainIdAndStatusOrderByCreatedAtDesc(
                event.domainId(), ScanStatus.SUCCEEDED);
        if (lastTwo.size() < 2 || !lastTwo.get(0).getId().equals(event.scanId())) {
            return; // first scan of the domain, or a newer scan already exists
        }
        Scan current = lastTwo.get(0);
        Scan previous = lastTwo.get(1);
        if (!isWorse(current.getGrade(), previous.getGrade())) {
            return;
        }
        Domain domain = domains.findById(event.domainId()).orElse(null);
        User owner = domain == null ? null : users.findById(domain.getUserId()).orElse(null);
        if (owner == null) {
            return;
        }
        sender.send(owner.getEmail(), subject(domain, previous, current), body(domain, previous, current));
    }

    /** The fields of a stored check that the email needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CheckSummary(String status, String severity, String title) {
    }

    static boolean isWorse(String current, String previous) {
        int now = rank(current);
        int before = rank(previous);
        return now >= 0 && before >= 0 && now > before;
    }

    /** Position of a grade from best (0) to worst, or -1 for anything that is not a grade. */
    private static int rank(String grade) {
        // indexOf("") is 0, so an empty or null grade must be rejected before the lookup.
        if (grade == null || grade.length() != 1) {
            return -1;
        }
        return GRADES.indexOf(grade);
    }

    private static String subject(Domain domain, Scan previous, Scan current) {
        return "Vigie : la note de " + domain.getHostname() + " est passée de "
                + previous.getGrade() + " à " + current.getGrade();
    }

    private String body(Domain domain, Scan previous, Scan current) {
        StringBuilder text = new StringBuilder();
        text.append("Bonjour,\n\n")
                .append("Le dernier scan de ").append(domain.getHostname())
                .append(" donne la note ").append(current.getGrade())
                .append(" (").append(current.getScore()).append("/100), contre ")
                .append(previous.getGrade())
                .append(" (").append(previous.getScore()).append("/100) au scan précédent.\n");
        List<String> problems = failedChecks(current);
        if (!problems.isEmpty()) {
            text.append("\nPoints à corriger :\n");
            for (String problem : problems) {
                text.append("- ").append(problem).append('\n');
            }
        }
        text.append("\nRapport complet : ")
                .append(properties.publicUrl()).append("/domaines/").append(domain.getId())
                .append("\n\nVigie");
        return text.toString();
    }

    private List<String> failedChecks(Scan scan) {
        List<String> titles = new ArrayList<>();
        if (scan.getChecks() == null) {
            return titles;
        }
        try {
            for (CheckSummary check : mapper.readValue(scan.getChecks(), CheckSummary[].class)) {
                if ("fail".equals(check.status())
                        && !"info".equals(check.severity())
                        && titles.size() < MAX_LISTED_PROBLEMS) {
                    titles.add(check.title());
                }
            }
        } catch (JacksonException exception) {
            // The report was validated when stored; if it cannot be read, send the summary only.
        }
        return titles;
    }
}
