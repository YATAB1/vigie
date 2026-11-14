package io.github.yatab1.vigie.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.yatab1.vigie.auth.AuthCookies;
import io.github.yatab1.vigie.auth.RefreshTokenRepository;
import io.github.yatab1.vigie.queue.ResultHandler;
import io.github.yatab1.vigie.scan.Scan;
import io.github.yatab1.vigie.scan.ScanRepository;
import io.github.yatab1.vigie.scan.ScanService;
import io.github.yatab1.vigie.support.RecordingJobQueue;
import io.github.yatab1.vigie.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Domains, ownership verification and scans, through the real security filter chain. */
@SpringBootTest
@ActiveProfiles("test")
class DomainScanFlowTest {

    @TestConfiguration
    static class QueueConfig {

        @Bean
        @Primary
        RecordingJobQueue recordingJobQueue() {
            return new RecordingJobQueue();
        }
    }

    private static final String PASSWORD = "correct horse battery";
    private static final String CHECKS = "[{\"id\":\"headers.hsts\",\"status\":\"fail\","
            + "\"severity\":\"high\",\"title\":\"HSTS\"}]";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private DomainRepository domains;

    @Autowired
    private ScanRepository scans;

    @Autowired
    private ScanService scanService;

    @Autowired
    private RecordingJobQueue queue;

    @Autowired
    private ResultHandler resultHandler;

    private MockMvc mockMvc;
    private Cookie alice;
    private Cookie bob;

    @BeforeEach
    void setUp() throws Exception {
        scans.deleteAll();
        domains.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        queue.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = signUp("alice@example.com");
        bob = signUp("bob@example.com");
    }

    // ---------- adding domains ----------

    @Test
    void addingADomainReturnsTheDnsChallengeToPublish() throws Exception {
        mockMvc.perform(addDomain(alice, "Example.COM"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hostname").value("example.com"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verification.recordType").value("TXT"))
                .andExpect(jsonPath("$.verification.recordName").value("_vigie-challenge.example.com"))
                .andExpect(jsonPath("$.verification.recordValue")
                        .value(org.hamcrest.Matchers.startsWith("vigie-verification=")));
    }

    @Test
    void eachDomainGetsItsOwnUnpredictableToken() throws Exception {
        String first = challengeOf(createDomain(alice, "example.com"));
        String second = challengeOf(createDomain(alice, "example.org"));

        assertThat(first).isNotEqualTo(second);
        assertThat(first).hasSize("vigie-verification=".length() + 48);
    }

    @Test
    void internalAndMalformedTargetsAreRefused() throws Exception {
        for (String hostname : new String[] {
            "127.0.0.1", "localhost", "169.254.169.254", "db.internal",
            "http://example.com", "example.com/admin", "example.com:6379"
        }) {
            mockMvc.perform(addDomain(alice, hostname)).andExpect(status().isBadRequest());
        }
        assertThat(domains.count()).isZero();
    }

    @Test
    void theSameDomainCannotBeAddedTwiceByOneUser() throws Exception {
        createDomain(alice, "example.com");

        mockMvc.perform(addDomain(alice, "EXAMPLE.com.")).andExpect(status().isConflict());
    }

    @Test
    void domainsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/domains")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/domains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostname\":\"example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- isolation between users ----------

    @Test
    void aUserOnlySeesTheirOwnDomains() throws Exception {
        createDomain(alice, "alice-site.com");
        createDomain(bob, "bob-site.com");

        mockMvc.perform(get("/api/v1/domains").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hostname").value("alice-site.com"));
    }

    @Test
    void anotherUsersDomainLooksLikeItDoesNotExist() throws Exception {
        String id = idOf(createDomain(alice, "example.com"));

        mockMvc.perform(get("/api/v1/domains/" + id).cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/domains/" + id + "/verify").cookie(bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/domains/" + id + "/scans").cookie(bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/domains/" + id).cookie(bob)).andExpect(status().isNotFound());

        assertThat(domains.count()).isEqualTo(1);
        assertThat(queue.jobs()).isEmpty();
    }

    @Test
    void twoUsersClaimingTheSameDomainGetDifferentChallenges() throws Exception {
        String aliceChallenge = challengeOf(createDomain(alice, "example.com"));
        String bobChallenge = challengeOf(createDomain(bob, "example.com"));

        assertThat(aliceChallenge).isNotEqualTo(bobChallenge);
    }

    // ---------- ownership verification ----------

    @Test
    void verificationAsksTheWorkerToReadTheChallengeRecord() throws Exception {
        String id = idOf(createDomain(alice, "example.com"));

        mockMvc.perform(post("/api/v1/domains/" + id + "/verify").cookie(alice))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.verification.checking").value(true));

        assertThat(queue.last().type()).isEqualTo("dns_txt");
        assertThat(queue.last().payload()).containsEntry("name", "_vigie-challenge.example.com");
    }

    @Test
    void domainIsVerifiedWhenDnsContainsTheExactChallenge() throws Exception {
        MvcResult created = createDomain(alice, "example.com");
        String id = idOf(created);
        mockMvc.perform(post("/api/v1/domains/" + id + "/verify").cookie(alice));

        resultHandler.handle(dnsResult(queue.last().id(), "unrelated", challengeOf(created)));

        mockMvc.perform(get("/api/v1/domains/" + id).cookie(alice))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").exists())
                .andExpect(jsonPath("$.verification.checking").value(false));
    }

    @Test
    void domainStaysPendingWhenTheChallengeIsMissingOrWrong() throws Exception {
        MvcResult created = createDomain(alice, "example.com");
        String id = idOf(created);
        mockMvc.perform(post("/api/v1/domains/" + id + "/verify").cookie(alice));

        resultHandler.handle(dnsResult(
                queue.last().id(), "vigie-verification=wrong", challengeOf(created) + "x"));

        mockMvc.perform(get("/api/v1/domains/" + id).cookie(alice))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verification.message").exists());
    }

    @Test
    void anotherUsersChallengeDoesNotVerifyMyClaim() throws Exception {
        MvcResult aliceDomain = createDomain(alice, "example.com");
        MvcResult bobDomain = createDomain(bob, "example.com");
        mockMvc.perform(post("/api/v1/domains/" + idOf(bobDomain) + "/verify").cookie(bob));

        // DNS holds Alice's token: Bob's verification must fail.
        resultHandler.handle(dnsResult(queue.last().id(), challengeOf(aliceDomain)));

        mockMvc.perform(get("/api/v1/domains/" + idOf(bobDomain)).cookie(bob))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void aResultForAnUnknownJobIsIgnored() throws Exception {
        MvcResult created = createDomain(alice, "example.com");

        // No verification was requested: a forged result must not verify the domain.
        resultHandler.handle(dnsResult(UUID.randomUUID(), challengeOf(created)));
        resultHandler.handle("not json");
        resultHandler.handle("{\"job_id\":\"not-a-uuid\",\"type\":\"dns_txt\"}");

        mockMvc.perform(get("/api/v1/domains/" + idOf(created)).cookie(alice))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void aFailedDnsLookupLeavesTheDomainPending() throws Exception {
        String id = idOf(createDomain(alice, "example.com"));
        mockMvc.perform(post("/api/v1/domains/" + id + "/verify").cookie(alice));

        resultHandler.handle("{\"job_id\":\"" + queue.last().id()
                + "\",\"type\":\"dns_txt\",\"status\":\"failed\",\"data\":{},\"error\":\"timeout\"}");

        mockMvc.perform(get("/api/v1/domains/" + id).cookie(alice))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verification.checking").value(false));
    }

    // ---------- scans ----------

    @Test
    void anUnverifiedDomainCannotBeScanned() throws Exception {
        String id = idOf(createDomain(alice, "example.com"));

        mockMvc.perform(post("/api/v1/domains/" + id + "/scans").cookie(alice))
                .andExpect(status().isConflict());

        assertThat(queue.jobs()).isEmpty();
        assertThat(scans.count()).isZero();
    }

    @Test
    void scanningAVerifiedDomainQueuesAJobAndStoresTheReport() throws Exception {
        String domainId = verifiedDomain(alice, "example.com");

        MvcResult started = mockMvc.perform(post("/api/v1/domains/" + domainId + "/scans").cookie(alice))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();
        String scanId = idOf(started);
        assertThat(queue.last().type()).isEqualTo("scan");
        assertThat(queue.last().id()).hasToString(scanId);
        assertThat(queue.last().payload()).containsEntry("hostname", "example.com");

        resultHandler.handle(scanResult(scanId, 80, "B"));

        mockMvc.perform(get("/api/v1/scans/" + scanId).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.score").value(80))
                .andExpect(jsonPath("$.grade").value("B"))
                .andExpect(jsonPath("$.checks[0].id").value("headers.hsts"))
                .andExpect(jsonPath("$.finishedAt").exists());
        mockMvc.perform(get("/api/v1/domains/" + domainId + "/scans").cookie(alice))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].score").value(80));
    }

    @Test
    void onlyOneScanRunsAtATimePerDomain() throws Exception {
        String domainId = verifiedDomain(alice, "example.com");
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/scans").cookie(alice))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/domains/" + domainId + "/scans").cookie(alice))
                .andExpect(status().isConflict());
    }

    @Test
    void aScanReportIsPrivateToTheDomainOwner() throws Exception {
        String domainId = verifiedDomain(alice, "example.com");
        String scanId = idOf(mockMvc.perform(
                post("/api/v1/domains/" + domainId + "/scans").cookie(alice)).andReturn());
        resultHandler.handle(scanResult(scanId, 80, "B"));

        mockMvc.perform(get("/api/v1/scans/" + scanId).cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/domains/" + domainId + "/scans").cookie(bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFailedScanKeepsTheWorkerError() throws Exception {
        String domainId = verifiedDomain(alice, "example.com");
        String scanId = idOf(mockMvc.perform(
                post("/api/v1/domains/" + domainId + "/scans").cookie(alice)).andReturn());

        resultHandler.handle("{\"job_id\":\"" + scanId + "\",\"type\":\"scan\",\"status\":\"failed\","
                + "\"data\":{},\"error\":\"TargetNotAllowedError: adresse non publique\"}");

        mockMvc.perform(get("/api/v1/scans/" + scanId).cookie(alice))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("TargetNotAllowedError: adresse non publique"))
                .andExpect(jsonPath("$.score").doesNotExist());
    }

    @Test
    void anInvalidOrReplayedReportCannotChangeAScan() throws Exception {
        String domainId = verifiedDomain(alice, "example.com");
        String scanId = idOf(mockMvc.perform(
                post("/api/v1/domains/" + domainId + "/scans").cookie(alice)).andReturn());

        resultHandler.handle(scanResult(scanId, 250, "A")); // score out of range
        mockMvc.perform(get("/api/v1/scans/" + scanId).cookie(alice))
                .andExpect(jsonPath("$.status").value("FAILED"));

        resultHandler.handle(scanResult(scanId, 100, "A")); // the scan is already finished
        mockMvc.perform(get("/api/v1/scans/" + scanId).cookie(alice))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void deletingADomainDeletesItsScans() throws Exception {
        String domainId = verifiedDomain(alice, "example.com");
        mockMvc.perform(post("/api/v1/domains/" + domainId + "/scans").cookie(alice));

        mockMvc.perform(delete("/api/v1/domains/" + domainId).cookie(alice))
                .andExpect(status().isNoContent());

        assertThat(domains.count()).isZero();
        assertThat(scans.count()).isZero();
    }

    @Test
    void aScanWithoutResultEventuallyTimesOut() throws Exception {
        String domainId = verifiedDomain(alice, "example.com");
        String scanId = idOf(mockMvc.perform(
                post("/api/v1/domains/" + domainId + "/scans").cookie(alice)).andReturn());

        assertThat(scanService.failStaleScans()).isZero(); // too recent to be stale

        // Rewind the scan's creation instead of waiting five minutes.
        Scan scan = scans.findById(UUID.fromString(scanId)).orElseThrow();
        ReflectionTestUtils.setField(scan, "createdAt", Instant.now().minus(Duration.ofMinutes(10)));
        scans.save(scan);

        assertThat(scanService.failStaleScans()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/scans/" + scanId).cookie(alice))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    // ---------- helpers ----------

    private Cookie signUp(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie(AuthCookies.ACCESS);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder addDomain(
            Cookie session, String hostname) {
        return post("/api/v1/domains")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostname\":\"" + hostname + "\"}");
    }

    private MvcResult createDomain(Cookie session, String hostname) throws Exception {
        return mockMvc.perform(addDomain(session, hostname)).andExpect(status().isCreated()).andReturn();
    }

    /** Creates a domain and walks it through a successful ownership check. */
    private String verifiedDomain(Cookie session, String hostname) throws Exception {
        MvcResult created = createDomain(session, hostname);
        String id = idOf(created);
        mockMvc.perform(post("/api/v1/domains/" + id + "/verify").cookie(session));
        resultHandler.handle(dnsResult(queue.last().id(), challengeOf(created)));
        assertThat(domains.findById(UUID.fromString(id)).orElseThrow().isVerified()).isTrue();
        return id;
    }

    private static String idOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String challengeOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.verification.recordValue");
    }

    private static String dnsResult(UUID jobId, String... records) {
        StringBuilder list = new StringBuilder();
        for (String record : records) {
            if (list.length() > 0) {
                list.append(',');
            }
            list.append('"').append(record).append('"');
        }
        return "{\"job_id\":\"" + jobId + "\",\"type\":\"dns_txt\",\"status\":\"succeeded\","
                + "\"data\":{\"name\":\"ignored\",\"records\":[" + list + "]},"
                + "\"error\":null,\"finished_at\":\"2026-11-14T12:00:00Z\"}";
    }

    private static String scanResult(String scanId, int score, String grade) {
        return "{\"job_id\":\"" + scanId + "\",\"type\":\"scan\",\"status\":\"succeeded\","
                + "\"data\":{\"hostname\":\"example.com\",\"scanned_ip\":\"93.184.216.34\","
                + "\"score\":" + score + ",\"grade\":\"" + grade + "\",\"checks\":" + CHECKS
                + ",\"duration_ms\":1200},\"error\":null,\"finished_at\":\"2026-11-14T12:00:00Z\"}";
    }
}
