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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectSoftDeleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private Long adminId;
    private Long projectId;

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

        // Get admin id
        MvcResult meResult = mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        adminId = Long.valueOf(
                objectMapper.readValue(meResult.getResponse().getContentAsString(), Map.class)
                        .get("id").toString());

        // Create a project
        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Deletable Project",
                                  "description": "to be deleted",
                                  "ownerId": %d
                                }
                                """.formatted(adminId)))
                .andExpect(status().isOk())
                .andReturn();

        projectId = Long.valueOf(
                objectMapper.readValue(projectResult.getResponse().getContentAsString(), Map.class)
                        .get("id").toString());
    }

    @Test
    void softDelete_removesProjectFromActiveList_restoreAddsItBack() throws Exception {
        // Confirm project is in the active list before delete
        MvcResult beforeDelete = mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> projectsBefore = objectMapper.readValue(
                beforeDelete.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

        assertThat(projectsBefore).anyMatch(p -> projectId.equals(Long.valueOf(p.get("id").toString())));

        // Soft delete the project
        mockMvc.perform(delete("/projects/" + projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Project must no longer appear in GET /projects
        MvcResult afterDelete = mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> projectsAfterDelete = objectMapper.readValue(
                afterDelete.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

        assertThat(projectsAfterDelete).noneMatch(p -> projectId.equals(Long.valueOf(p.get("id").toString())));

        // GET /projects/{id} for deleted project must return 404
        mockMvc.perform(get("/projects/" + projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // Restore the project
        mockMvc.perform(post("/projects/" + projectId + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Project must be back in the active list
        MvcResult afterRestore = mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> projectsAfterRestore = objectMapper.readValue(
                afterRestore.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

        assertThat(projectsAfterRestore).anyMatch(p -> projectId.equals(Long.valueOf(p.get("id").toString())));

        // GET /projects/{id} must succeed again
        mockMvc.perform(get("/projects/" + projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId));
    }

    @Test
    void nonAdminCannotRestoreProject() throws Exception {
        // Create a developer user
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "dev",
                                  "email": "dev@example.com",
                                  "password": "dev123",
                                  "fullName": "Developer",
                                  "role": "DEVELOPER"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult devLogin = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dev\",\"password\":\"dev123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String devToken = objectMapper.readValue(
                devLogin.getResponse().getContentAsString(), Map.class)
                .get("accessToken").toString();

        // Admin deletes the project
        mockMvc.perform(delete("/projects/" + projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Developer tries to restore — must be forbidden
        mockMvc.perform(post("/projects/" + projectId + "/restore")
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isForbidden());
    }
}
