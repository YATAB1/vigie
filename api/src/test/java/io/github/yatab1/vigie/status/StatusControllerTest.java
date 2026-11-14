package io.github.yatab1.vigie.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatusControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StatusController("9.9.9-test")).build();
    }

    @Test
    void statusReturnsServiceNameAndVersion() throws Exception {
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("vigie-api"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.version").value("9.9.9-test"));
    }

    @Test
    void statusRejectsPost() throws Exception {
        mockMvc.perform(post("/api/v1/status"))
                .andExpect(status().isMethodNotAllowed());
    }
}
