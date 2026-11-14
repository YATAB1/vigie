package io.github.yatab1.vigie;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class VigieApiApplicationTests {

    @Test
    void contextLoads() {
        // Fails if the Spring context cannot start (bad config, missing bean...).
    }
}
