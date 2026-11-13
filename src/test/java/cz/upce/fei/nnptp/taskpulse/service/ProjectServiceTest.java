
package cz.upce.fei.nnptp.taskpulse.service;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.exception.ResourceNotFoundException;
import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectServiceTest {

    private InMemoryProjectRepository projectRepository;
    private InMemoryTaskRepository taskRepository;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectRepository = new InMemoryProjectRepository();
        taskRepository = new InMemoryTaskRepository();
        projectService = new ProjectService(projectRepository, taskRepository);
    }

    @Test
    void createProjectWithValidData() {
        Project project = projectService.createProject("TaskPulse", "Project description");

        assertThat(project.getId()).isNotNull();
        assertThat(project.getName()).isEqualTo("TaskPulse");
        assertThat(project.getDescription()).isEqualTo("Project description");
        assertThat(projectRepository.existsById(project.getId())).isTrue();
    }

    @Test
    void getExistingProject() {
        Project savedProject = projectRepository.save(
                new Project("TaskPulse", "Description")
        );

        Project result = projectService.getProject(savedProject.getId());

        assertThat(result).isEqualTo(savedProject);
    }

    @Test
    void rejectGettingNonExistentProject() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> projectService.getProject(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void rejectGettingProjectWithNullId() {
        assertThatThrownBy(() -> projectService.getProject(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Project ID must not be null");
    }

    @Test
    void listAllProjects() {
        Project firstProject = projectRepository.save(
                new Project("First", "First description")
        );
        Project secondProject = projectRepository.save(
                new Project("Second", "Second description")
        );

        List<Project> result = projectService.listProjects();

        assertThat(result).containsExactly(firstProject, secondProject);
    }

    @Test
    void updateExistingProject() {
        Project project = projectRepository.save(
                new Project("Original", "Original description")
        );

        Project result = projectService.updateProject(
                project.getId(),
                "Updated",
                "Updated description"
        );

        assertThat(result.getId()).isEqualTo(project.getId());
        assertThat(result.getName()).isEqualTo("Updated");
        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(projectRepository.findById(project.getId()))
                .contains(result);
    }

    @Test
    void rejectUpdatingNonExistentProject() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> projectService.updateProject(
                unknownId, "Updated", "Description"
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void deleteProjectWithoutTasks() {
        Project project = projectRepository.save(
                new Project("Empty project", "")
        );

        projectService.deleteProject(project.getId());

        assertThat(projectRepository.existsById(project.getId())).isFalse();
    }

    @Test
    void rejectDeletingProjectWithTasks() {
        Project project = projectRepository.save(
                new Project("Project with tasks", "")
        );

        // Simulate an existing task count for the project.
        taskRepository.save(
                new cz.upce.fei.nnptp.taskpulse.domain.Task(
                        project.getId(), "Existing task", ""
                )
        );

        assertThatThrownBy(() -> projectService.deleteProject(project.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("contains 1 task");

        assertThat(projectRepository.existsById(project.getId())).isTrue();
    }
}
