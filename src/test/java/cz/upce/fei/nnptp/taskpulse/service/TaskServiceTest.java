package cz.upce.fei.nnptp.taskpulse.service;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.exception.ResourceNotFoundException;
import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskServiceTest {

    private InMemoryProjectRepository projectRepository;
    private InMemoryTaskRepository taskRepository;
    private ProjectService projectService;
    private TaskService taskService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        projectRepository = new InMemoryProjectRepository();
        taskRepository = new InMemoryTaskRepository();
        Instant fixedInstant = Instant.parse("2026-10-21T10:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        projectService = new ProjectService(projectRepository, taskRepository);
        taskService = new TaskService(taskRepository, projectRepository, fixedClock);
    }

    @Test
    void rejectNegativeEstimateWithoutChangingExistingTask() {
        Project project = projectService.createProject("Dev", "");
        LocalDate originalDate = LocalDate.of(2026, 11, 20);
        Task task = taskService.createTask(project.getId(), "Original", "Description", Priority.HIGH, 30, originalDate);

        assertThatThrownBy(() -> taskService.updateTask(task.getId(), "Changed", "Changed description",
                Priority.LOW, -1, originalDate.plusDays(1)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Estimated minutes cannot be negative");

        assertThat(taskService.getTask(task.getId())).satisfies(saved -> {
            assertThat(saved.getTitle()).isEqualTo("Original");
            assertThat(saved.getDescription()).isEqualTo("Description");
            assertThat(saved.getPriority()).isEqualTo(Priority.HIGH);
            assertThat(saved.getEstimatedMinutes()).isEqualTo(30);
            assertThat(saved.getDueDate()).isEqualTo(originalDate);
        });
    }

    @Test
    void rejectBlankTitleWithoutChangingExistingTask() {
        Project project = projectService.createProject("Dev", "");
        Task task = taskService.createTask(project.getId(), "Original", "Description", Priority.HIGH, 30, null);

        for (String invalidTitle : new String[]{null, "", "  \t  "}) {
            assertThatThrownBy(() -> taskService.updateTask(task.getId(), invalidTitle,
                    "Changed", Priority.LOW, 60, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Task title must not be blank");
            assertThat(taskService.getTask(task.getId()).getTitle()).isEqualTo("Original");
            assertThat(taskService.getTask(task.getId()).getDescription()).isEqualTo("Description");
            assertThat(taskService.getTask(task.getId()).getPriority()).isEqualTo(Priority.HIGH);
            assertThat(taskService.getTask(task.getId()).getEstimatedMinutes()).isEqualTo(30);
        }
    }

    @Test
    void updateTaskWithValidValues() {
        Project project = projectService.createProject("Dev", "");
        Task task = taskService.createTask(project.getId(), "Original", "Description", Priority.HIGH, 30, null);
        LocalDate newDate = LocalDate.of(2026, 12, 1);

        Task updated = taskService.updateTask(task.getId(), " Updated ", "New description", Priority.LOW, 0, newDate);

        assertThat(updated.getTitle()).isEqualTo("Updated");
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getPriority()).isEqualTo(Priority.LOW);
        assertThat(updated.getEstimatedMinutes()).isZero();
        assertThat(updated.getDueDate()).isEqualTo(newDate);
    }
    
    @Test
    void createTaskWithValidProject() {
        Project project = projectService.createProject("Dev", "Dev project");
        Task task = taskService.createTask(project.getId(), "Task 1", "Desc 1", Priority.HIGH, 60, LocalDate.of(2026, 10, 25));

        assertThat(task.getId()).isNotNull();
        assertThat(task.getProjectId()).isEqualTo(project.getId());
        assertThat(task.getTitle()).isEqualTo("Task 1");
        assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void rejectTaskCreationForNonExistentProject() {
        UUID fakeProjectId = UUID.randomUUID();
        assertThatThrownBy(() -> taskService.createTask(fakeProjectId, "Task 1", "", Priority.LOW, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void cannotDeleteProjectWithTasks() {
        Project project = projectService.createProject("Dev", "");
        taskService.createTask(project.getId(), "T1", "", Priority.LOW, null, null);

        assertThatThrownBy(() -> projectService.deleteProject(project.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("contains 1 task");

        assertThat(projectRepository.existsById(project.getId())).isTrue();
    }

    @Test
    void canDeleteEmptyProject() {
        Project project = projectService.createProject("Empty", "");
        projectService.deleteProject(project.getId());

        assertThat(projectRepository.existsById(project.getId())).isFalse();
    }

    @Test
    void labelManagementViaService() {
        Project project = projectService.createProject("P", "");
        Task task = taskService.createTask(project.getId(), "T", "", Priority.MEDIUM, null, null);

        taskService.addLabel(task.getId(), "Frontend");
        assertThat(taskService.getTask(task.getId()).getLabels()).containsExactly("frontend");

        taskService.removeLabel(task.getId(), "frontend");
        assertThat(taskService.getTask(task.getId()).getLabels()).isEmpty();
    }

    @Test
    void filterTasksByCombinedCriteria() {
        Project project = projectService.createProject("P", "");
        Task t1 = taskService.createTask(project.getId(), "Alpha Bug", "Critical bug in backend", Priority.CRITICAL, 30, LocalDate.of(2026, 10, 20));
        taskService.addLabel(t1.getId(), "bug");

        Task t2 = taskService.createTask(project.getId(), "Beta Feature", "Frontend view", Priority.LOW, 60, LocalDate.of(2026, 10, 22));
        taskService.addLabel(t2.getId(), "feature");

        TaskQuery query = TaskQuery.builder()
                .priority(Priority.CRITICAL)
                .label("bug")
                .textSearch("backend")
                .build();

        List<Task> results = taskService.findTasks(query);
        assertThat(results).containsExactly(t1);
    }

    @Test
    void filterTasksByOverdue() {
        Project project = projectService.createProject("P", "");
        LocalDate today = LocalDate.now(fixedClock);

        Task overdueTask = taskService.createTask(project.getId(), "Overdue", "", Priority.HIGH, null, today.minusDays(1));
        Task dueTodayTask = taskService.createTask(project.getId(), "Due Today", "", Priority.HIGH, null, today);
        Task futureTask = taskService.createTask(project.getId(), "Future", "", Priority.HIGH, null, today.plusDays(1));

        TaskQuery overdueQuery = TaskQuery.builder().overdue(true).build();
        List<Task> overdueResults = taskService.findTasks(overdueQuery);
        assertThat(overdueResults).containsExactly(overdueTask);

        TaskQuery notOverdueQuery = TaskQuery.builder().overdue(false).build();
        List<Task> notOverdueResults = taskService.findTasks(notOverdueQuery);
        assertThat(notOverdueResults).contains(dueTodayTask, futureTask);
    }

    @Test
    void rejectInvertedDueDateRange() {
        LocalDate fromDate = LocalDate.of(2026, 10, 25);
        LocalDate toDate = LocalDate.of(2026, 10, 20);

        assertThatThrownBy(() -> TaskQuery.builder().dueDateRange(fromDate, toDate).build())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid date range");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "none, 45, 0, false",
            "0, 0, 0, false",
            "0, 1, 0, true",
            "60, 45, 0, false",
            "60, 60, 0, false",
            "60, 75, 15, false",
            "60, 76, 15, true",
            "1, 2147483650, 2147483648, true"
    }, nullValues = "none")
    void filtersByVarianceAboveThreshold(Integer estimate, long recorded, long threshold, boolean expectedMatch) {
        UUID projectId = projectService.createProject("Planning", "").getId();
        Task task = taskService.createTask(projectId, "Review", "", Priority.MEDIUM, estimate, null);
        if (recorded > 0) {
            taskService.transitionTask(task.getId(), TaskState.IN_PROGRESS);
            taskService.logWork(task.getId(), recorded, "Dev", "");
        }

        TaskQuery query = TaskQuery.builder().varianceThresholdMinutes(threshold).build();
        assertThat(taskService.findTasks(query)).isEqualTo(expectedMatch ? List.of(task) : List.of());
    }
}
