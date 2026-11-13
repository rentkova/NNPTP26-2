package cz.upce.fei.nnptp.taskpulse.domain;

import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTest {

    @Test
    void defaultValuesForNewTask() {
        UUID projectId = UUID.randomUUID();
        Task task = new Task(projectId, "First Task");

        assertThat(task.getId()).isNotNull();
        assertThat(task.getProjectId()).isEqualTo(projectId);
        assertThat(task.getTitle()).isEqualTo("First Task");
        assertThat(task.getDescription()).isEmpty();
        assertThat(task.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(task.getState()).isEqualTo(TaskState.TODO);
        assertThat(task.getEstimatedMinutes()).isNull();
        assertThat(task.getDueDate()).isNull();
        assertThat(task.getLabels()).isEmpty();
        assertThat(task.getWorklogs()).isEmpty();
        assertThat(task.getTotalRecordedMinutes()).isEqualTo(0L);
        assertThat(task.getVarianceMinutes()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void rejectBlankTitle(String invalidTitle) {
        UUID projectId = UUID.randomUUID();
        assertThatThrownBy(() -> new Task(projectId, invalidTitle))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectNullProjectId() {
        assertThatThrownBy(() -> new Task(null, "Valid Title"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectNegativeEstimate() {
        UUID projectId = UUID.randomUUID();
        assertThatThrownBy(() -> new Task(UUID.randomUUID(), projectId, "Title", "", Priority.LOW, TaskState.TODO, -10, null, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Estimated minutes cannot be negative");
    }

    @Test
    void zeroEstimateIsAllowedAndDistinguishedFromNull() {
        UUID projectId = UUID.randomUUID();
        Task task = new Task(projectId, "Title");
        task.setEstimatedMinutes(0);

        assertThat(task.getEstimatedMinutes()).isEqualTo(0);
        assertThat(task.getVarianceMinutes()).isEqualTo(0L);
    }

    @Test
    void labelNormalizationAndDeduplication() {
        UUID projectId = UUID.randomUUID();
        Task task = new Task(projectId, "Title");

        task.addLabel("  Backend  ");
        task.addLabel("backend");
        task.addLabel("BACKEND");

        assertThat(task.getLabels()).containsExactly("backend");
        assertThat(task.hasLabel(" Backend ")).isTrue();
        assertThat(task.hasLabel("backend")).isTrue();

        task.removeLabel(" Backend ");
        assertThat(task.getLabels()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectBlankLabel(String invalidLabel) {
        UUID projectId = UUID.randomUUID();
        Task task = new Task(projectId, "Title");

        assertThatThrownBy(() -> task.addLabel(invalidLabel))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"backend;frontend", "urgent;", ";urgent", ";", "a;;b"})
    void rejectLabelContainingSeparator(String invalidLabel) {
        UUID projectId = UUID.randomUUID();
        Task task = new Task(projectId, "Title");

        assertThatThrownBy(() -> task.addLabel(invalidLabel))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(Label.SEPARATOR);
    }

    @Test
    void overdueCalculation() {
        UUID projectId = UUID.randomUUID();
        Task task = new Task(projectId, "Title");
        LocalDate today = LocalDate.of(2026, 10, 21);

        assertThat(task.isOverdue(today)).isFalse();

        task.setDueDate(today);
        assertThat(task.isOverdue(today)).isFalse();

        task.setDueDate(today.minusDays(1));
        assertThat(task.isOverdue(today)).isTrue();

        task.setDueDate(today.plusDays(1));
        assertThat(task.isOverdue(today)).isFalse();
    }

    @Test
    void completedTaskIsNotOverdue() {
        UUID projectId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 10, 21);
        Task task = new Task(projectId, "Title");
        task.setDueDate(today.minusDays(5));
        task.transitionTo(TaskState.IN_PROGRESS);
        task.addWorklog(new Worklog(LocalDateTime.now(), 30, "Dev"));
        task.transitionTo(TaskState.DONE);

        assertThat(task.isOverdue(today)).isFalse();
    }

    @Test
    void calculateTotalRecordedMinutesAndVariance() {
        UUID projectId = UUID.randomUUID();
        Task task = new Task(projectId, "Title");
        task.setEstimatedMinutes(60);
        task.transitionTo(TaskState.IN_PROGRESS);

        task.addWorklog(new Worklog(LocalDateTime.now(), 40, "Alice"));
        task.addWorklog(new Worklog(LocalDateTime.now(), 35, "Bob"));

        assertThat(task.getTotalRecordedMinutes()).isEqualTo(75L);
        assertThat(task.getVarianceMinutes()).isEqualTo(15L);
    }
}
