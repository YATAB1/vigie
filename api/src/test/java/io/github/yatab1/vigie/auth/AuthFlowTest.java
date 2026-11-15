package io.github.yatab1.vigie.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.yatab1.vigie.user.Role;
import io.github.yatab1.vigie.user.User;
import io.github.yatab1.vigie.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** End-to-end tests of the auth endpoints, through the real security filter chain. */
@SpringBootTest
@ActiveProfiles("test")
class AuthFlowTest {

    private static final String EMAIL = "yassine@example.com";
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginThrottle throttle;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
        throttle.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---------- registration ----------

    @Test
    void registerCreatesAUserAndOpensASession() throws Exception {
        MvcResult result = register(EMAIL, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString())
                .contains(EMAIL)
                .contains("\"role\":\"USER\"")
                .doesNotContain("password")
                .doesNotContain(cookie(result, AuthCookies.ACCESS).getValue());
        assertThat(users.findByEmail(EMAIL)).isPresent();
    }

    @Test
    void passwordIsStoredHashedNeverInClear() throws Exception {
        register(EMAIL, PASSWORD);

        String stored = users.findByEmail(EMAIL).orElseThrow().getPasswordHash();

        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
    }

    @Test
    void authCookiesAreHttpOnlyAndSameSiteStrict() throws Exception {
        MvcResult result = register(EMAIL, PASSWORD);

        for (String name : new String[] {AuthCookies.ACCESS, AuthCookies.REFRESH}) {
            MockCookie cookie = (MockCookie) cookie(result, name);
            assertThat(cookie.isHttpOnly()).as(name + " HttpOnly").isTrue();
            assertThat(cookie.getSameSite()).as(name + " SameSite").isEqualTo("Strict");
        }
        assertThat(cookie(result, AuthCookies.REFRESH).getPath()).isEqualTo("/api/v1/auth");
    }

    @Test
    void emailIsNormalizedSoCaseDoesNotCreateDuplicates() throws Exception {
        register("Yassine@Example.COM", PASSWORD);

        assertThat(register(EMAIL, PASSWORD).getResponse().getStatus()).isEqualTo(409);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void registerRejectsAShortPassword() throws Exception {
        mockMvc.perform(json("/api/v1/auth/register", EMAIL, "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        assertThat(users.count()).isZero();
    }

    @Test
    void registerRejectsAnInvalidEmail() throws Exception {
        mockMvc.perform(json("/api/v1/auth/register", "not-an-email", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void registerCannotGrantTheAdminRole() throws Exception {
        String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD
                + "\",\"role\":\"ADMIN\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        assertThat(users.findAll()).noneMatch(user -> user.getRole() == Role.ADMIN);
    }

    // ---------- login ----------

    @Test
    void loginWithCorrectCredentialsOpensASession() throws Exception {
        register(EMAIL, PASSWORD);

        MvcResult result = mockMvc.perform(json("/api/v1/auth/login", EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/v1/me").cookie(cookie(result, AuthCookies.ACCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        register(EMAIL, PASSWORD);

        MvcResult wrongPassword = mockMvc.perform(json("/api/v1/auth/login", EMAIL, "wrong password!"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult unknownEmail = mockMvc.perform(json("/api/v1/auth/login", "nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
        assertThat(wrongPassword.getResponse().getCookie(AuthCookies.ACCESS)).isNull();
    }

    @Test
    void repeatedFailuresBlockTheAccountForAWhile() throws Exception {
        register(EMAIL, PASSWORD);
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            mockMvc.perform(json("/api/v1/auth/login", EMAIL, "wrong password " + i))
                    .andExpect(status().isUnauthorized());
        }

        // Even the right password is refused while the account is blocked.
        mockMvc.perform(json("/api/v1/auth/login", EMAIL, PASSWORD))
                .andExpect(status().isTooManyRequests());
        // Other accounts are not affected.
        mockMvc.perform(json("/api/v1/auth/login", "someone-else@example.com", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailsAreThrottledLikeRealOnes() throws Exception {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            mockMvc.perform(json("/api/v1/auth/login", "nobody@example.com", PASSWORD));
        }

        // Same answer as for an existing account: the limit reveals nothing.
        mockMvc.perform(json("/api/v1/auth/login", "nobody@example.com", PASSWORD))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aSuccessfulSignInClearsPastFailures() throws Exception {
        register(EMAIL, PASSWORD);
        for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
            mockMvc.perform(json("/api/v1/auth/login", EMAIL, "wrong password " + i));
        }
        mockMvc.perform(json("/api/v1/auth/login", EMAIL, PASSWORD)).andExpect(status().isOk());

        mockMvc.perform(json("/api/v1/auth/login", EMAIL, "wrong again!!"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- access control ----------

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void forgedAccessTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me").cookie(new Cookie(AuthCookies.ACCESS, "forged.token.value")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayReachableWithoutASession() throws Exception {
        mockMvc.perform(get("/api/v1/status")).andExpect(status().isOk());
    }

    @Test
    void regularUserCannotReachAdminEndpoints() throws Exception {
        MvcResult result = register(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/admin/users").cookie(cookie(result, AuthCookies.ACCESS)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanListUsers() throws Exception {
        users.save(new User("admin@example.com", passwordEncoder.encode(PASSWORD), Role.ADMIN, Instant.now()));
        register(EMAIL, PASSWORD);
        MvcResult admin = mockMvc.perform(json("/api/v1/auth/login", "admin@example.com", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/v1/admin/users").cookie(cookie(admin, AuthCookies.ACCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    // ---------- refresh and logout ----------

    @Test
    void refreshIssuesANewTokenPair() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);

        MvcResult refreshed = refresh(cookie(session, AuthCookies.REFRESH));

        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(cookie(refreshed, AuthCookies.REFRESH).getValue())
                .isNotEqualTo(cookie(session, AuthCookies.REFRESH).getValue());
        mockMvc.perform(get("/api/v1/me").cookie(cookie(refreshed, AuthCookies.ACCESS)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithoutACookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void reusingARefreshTokenRevokesEverySessionOfTheUser() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);
        Cookie stolen = cookie(session, AuthCookies.REFRESH);
        MvcResult legitimate = refresh(stolen);
        assertThat(legitimate.getResponse().getStatus()).isEqualTo(200);

        // The first token was already used: presenting it again is a replay.
        assertThat(refresh(stolen).getResponse().getStatus()).isEqualTo(401);

        // The token obtained legitimately is revoked too: the user must sign in again.
        assertThat(refresh(cookie(legitimate, AuthCookies.REFRESH)).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsCookies() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);
        Cookie refreshCookie = cookie(session, AuthCookies.REFRESH);

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout").cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(cookie(logout, AuthCookies.ACCESS).getMaxAge()).isZero();
        assertThat(cookie(logout, AuthCookies.REFRESH).getMaxAge()).isZero();
        assertThat(refresh(refreshCookie).getResponse().getStatus()).isEqualTo(401);
    }

    // ---------- helpers ----------

    private MvcResult register(String email, String password) throws Exception {
        return mockMvc.perform(json("/api/v1/auth/register", email, password)).andReturn();
    }

    private MvcResult refresh(Cookie refreshCookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie)).andReturn();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            String path, String email, String password) {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static Cookie cookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("cookie " + name).isNotNull();
        return cookie;
    }
}
