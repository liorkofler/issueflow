package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.request.CreateProjectRequest;
import com.att.tdp.issueflow.dto.request.UpdateProjectRequest;
import com.att.tdp.issueflow.dto.response.ProjectResponse;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.UserRole;
import com.att.tdp.issueflow.exception.ForbiddenException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    private User adminUser() {
        return User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
    }

    private User developerUser() {
        return User.builder().id(2L).username("dev").role(UserRole.DEVELOPER).build();
    }

    private Project activeProject() {
        return Project.builder()
                .id(1L)
                .name("Test Project")
                .description("A description")
                .owner(adminUser())
                .build();
    }

    private Project deletedProject() {
        Project p = activeProject();
        p.setDeletedAt(LocalDateTime.now());
        return p;
    }

    // ---- getAllProjects ----

    @Test
    void getAllProjects_returnsOnlyActiveProjects() {
        when(projectRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(activeProject()));

        List<ProjectResponse> result = projectService.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("Test Project");
    }

    // ---- getDeletedProjects ----

    @Test
    void getDeletedProjects_returnsOnlySoftDeletedProjects() {
        when(projectRepository.findAllByDeletedAtIsNotNull()).thenReturn(List.of(deletedProject()));

        List<ProjectResponse> result = projectService.getDeletedProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    // ---- getProjectById ----

    @Test
    void getProjectById_returnsProject_whenActive() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(activeProject()));

        ProjectResponse response = projectService.getProjectById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Test Project");
        assertThat(response.ownerId()).isEqualTo(1L);
    }

    @Test
    void getProjectById_throws404_whenSoftDeleted() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(deletedProject()));

        assertThatThrownBy(() -> projectService.getProjectById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    void getProjectById_throws404_whenNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---- createProject ----

    @Test
    void createProject_savesAndReturnsResponse() {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("New Project");
        request.setDescription("Desc");
        request.setOwnerId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser()));
        Project saved = activeProject();
        when(projectRepository.save(any(Project.class))).thenReturn(saved);

        ProjectResponse response = projectService.createProject(request);

        assertThat(response.name()).isEqualTo("Test Project");
        assertThat(response.ownerId()).isEqualTo(1L);
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void createProject_throws404_whenOwnerNotFound() {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("X");
        request.setOwnerId(99L);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.createProject(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectRepository, never()).save(any());
    }

    // ---- updateProject ----

    @Test
    void updateProject_updatesNameAndDescription() {
        Project project = activeProject();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("Updated Name");
        request.setDescription("Updated Desc");

        projectService.updateProject(1L, request);

        verify(projectRepository).save(argThat(p ->
                "Updated Name".equals(p.getName()) && "Updated Desc".equals(p.getDescription())
        ));
    }

    @Test
    void updateProject_throws404_whenProjectIsDeleted() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(deletedProject()));

        assertThatThrownBy(() -> projectService.updateProject(1L, new UpdateProjectRequest()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectRepository, never()).save(any());
    }

    // ---- softDeleteProject ----

    @Test
    void softDeleteProject_setsDeletedAt() {
        Project project = activeProject();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        projectService.softDeleteProject(1L);

        verify(projectRepository).save(argThat(p -> p.getDeletedAt() != null));
    }

    @Test
    void softDeleteProject_throws404_whenAlreadyDeleted() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(deletedProject()));

        assertThatThrownBy(() -> projectService.softDeleteProject(1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void softDeleteProject_throws404_whenNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.softDeleteProject(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- restoreProject ----

    @Test
    void restoreProject_clearsDeletedAt_whenCallerIsAdmin() {
        Project deleted = deletedProject();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser()));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(projectRepository.save(any(Project.class))).thenReturn(deleted);

        projectService.restoreProject(1L, "admin");

        verify(projectRepository).save(argThat(p -> p.getDeletedAt() == null));
    }

    @Test
    void restoreProject_throwsForbidden_whenCallerIsNotAdmin() {
        when(userRepository.findByUsername("dev")).thenReturn(Optional.of(developerUser()));

        assertThatThrownBy(() -> projectService.restoreProject(1L, "dev"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ADMIN");

        verify(projectRepository, never()).findById(any());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void restoreProject_throws404_whenProjectNotFound() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser()));
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.restoreProject(99L, "admin"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void restoreProject_throws404_whenProjectIsNotDeleted() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser()));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(activeProject()));

        assertThatThrownBy(() -> projectService.restoreProject(1L, "admin"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectRepository, never()).save(any());
    }
}
