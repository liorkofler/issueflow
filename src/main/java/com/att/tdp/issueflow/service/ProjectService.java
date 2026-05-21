package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.request.CreateProjectRequest;
import com.att.tdp.issueflow.dto.request.UpdateProjectRequest;
import com.att.tdp.issueflow.dto.response.ProjectResponse;
import com.att.tdp.issueflow.dto.response.WorkloadResponse;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.TicketStatus;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.UserRole;
import com.att.tdp.issueflow.exception.ForbiddenException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getOwnerId()));
        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(owner)
                .build();
        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (project.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Project not found: " + id);
        }
        return toResponse(project);
    }

    @Transactional
    public void updateProject(Long id, UpdateProjectRequest request) {
        Project project = findActiveOrThrow(id);
        if (request.getName() != null) {
            project.setName(request.getName());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        projectRepository.save(project);
    }

    @Transactional
    public void softDeleteProject(Long id) {
        Project project = findActiveOrThrow(id);
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAllByDeletedAtIsNull().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getDeletedProjects() {
        return projectRepository.findAllByDeletedAtIsNotNull().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void restoreProject(Long id, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Only ADMIN users can restore projects");
        }
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (project.getDeletedAt() == null) {
            throw new ResourceNotFoundException("Project not found: " + id);
        }
        project.setDeletedAt(null);
        projectRepository.save(project);
    }

    private Project findActiveOrThrow(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (project.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Project not found: " + id);
        }
        return project;
    }

    @Transactional(readOnly = true)
    public List<WorkloadResponse> getWorkload(Long projectId, String currentUsername) {
        projectRepository.findById(projectId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        List<User> developers = userRepository.findByRole(UserRole.DEVELOPER);

        return developers.stream()
                .map(dev -> {
                    Long count = ticketRepository.countByAssigneeIdAndProjectIdAndStatusNot(
                            dev.getId(), projectId, TicketStatus.DONE);
                    return new WorkloadResponse(dev.getId(), dev.getUsername(), count);
                })
                .sorted(Comparator.comparingLong(WorkloadResponse::openTicketCount)
                        .thenComparingLong(WorkloadResponse::userId))
                .toList();
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId()
        );
    }
}
