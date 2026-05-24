package com.att.tdp.issueflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
class TicketLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private Long projectId;
    private Long ticketId;

    @BeforeEach
    void setUp() throws Exception {
        // Create admin user
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "email": "admin@example.com",
                                  "password": "admin123",
                                  "fullName": "Admin",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk());

        // Login
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        adminToken = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), Map.class)
                .get("accessToken").toString();

        // Get admin user id
        MvcResult meResult = mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        Long adminId = Long.valueOf(
                objectMapper.readValue(meResult.getResponse().getContentAsString(), Map.class)
                        .get("id").toString());

        // Create a project
        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Test Project",
                                  "description": "desc",
                                  "ownerId": %d
                                }
                                """.formatted(adminId)))
                .andExpect(status().isOk())
                .andReturn();

        projectId = Long.valueOf(
                objectMapper.readValue(projectResult.getResponse().getContentAsString(), Map.class)
                        .get("id").toString());

        // Create a ticket in TODO status
        MvcResult ticketResult = mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Lifecycle Ticket",
                                  "status": "TODO",
                                  "priority": "MEDIUM",
                                  "type": "BUG",
                                  "projectId": %d
                                }
                                """.formatted(projectId)))
                .andExpect(status().isOk())
                .andReturn();

        ticketId = Long.valueOf(
                objectMapper.readValue(ticketResult.getResponse().getContentAsString(), Map.class)
                        .get("id").toString());
    }

    @Test
    void ticketMovesForwardThroughAllStatuses() throws Exception {
        // TODO → IN_PROGRESS
        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // IN_PROGRESS → IN_REVIEW
        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        // IN_REVIEW → DONE
        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void backwardTransition_fails() throws Exception {
        // Move to IN_PROGRESS first
        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        // Try to go back to TODO — must fail with 400
        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TODO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDoneTicket_fails() throws Exception {
        // Move ticket all the way to DONE
        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());

        // Any update on a DONE ticket must fail
        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new title\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void skipStatusTransition_fails() throws Exception {
        // Trying to skip IN_PROGRESS and jump straight to IN_REVIEW must fail
        mockMvc.perform(patch("/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isBadRequest());
    }
}
