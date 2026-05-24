package com.att.tdp.issueflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullAuthFlow_createLoginProtectedEndpointLogoutRejectOldToken() throws Exception {
        // 1. Create a user (public endpoint)
        String createUserJson = """
                {
                  "username": "authuser",
                  "email": "authuser@example.com",
                  "password": "password123",
                  "fullName": "Auth User",
                  "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("authuser"));

        // 2. Login and get JWT
        String loginJson = """
                {
                  "username": "authuser",
                  "password": "password123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readValue(responseBody, Map.class).get("accessToken").toString();

        // 3. Call protected endpoint with token — should succeed
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("authuser"));

        // 4. Logout — blacklists the token
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 5. Old token must now be rejected
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_requestToProtectedEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String createUserJson = """
                {
                  "username": "testlogin",
                  "email": "testlogin@example.com",
                  "password": "correctpass",
                  "fullName": "Test",
                  "role": "DEVELOPER"
                }
                """;
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson))
                .andExpect(status().isOk());

        String badLoginJson = """
                {
                  "username": "testlogin",
                  "password": "wrongpass"
                }
                """;
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLoginJson))
                .andExpect(status().isUnauthorized());
    }
}
