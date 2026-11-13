package cz.upce.fei.nnptp.taskpulse.domain;

import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Task {
    private final UUID id;
    private final UUID projectId;
    private String title;
    private String description;
    private Priority priority;
    private TaskState state;
    private Integer estimatedMinutes;
    private LocalDate dueDate;
    private final Set<String> labels = new LinkedHashSet<>();
    private final List<Worklog> worklogs = new ArrayList<>();

    public Task(UUID id, UUID projectId, String title, String description,
                Priority priority, TaskState state, Integer estimatedMinutes,
                LocalDate dueDate, Collection<String> initialLabels, Collection<Worklog> initialWorklogs) {
        this.id = Objects.requireNonNull(id, "Task ID must not be null");
        this.projectId = Objects.requireNonNull(projectId, "Project ID must not be null");
        setTitle(title);
        setDescription(description);
        this.priority = priority == null ? Priority.MEDIUM : priority;
        this.state = state == null ? TaskState.TODO : state;
        setEstimatedMinutes(estimatedMinutes);
        setDueDate(dueDate);
        if (initialLabels != null) {
            for (String label : initialLabels) {
                addLabel(label);
            }
        }
        if (initialWorklogs != null) {
            for (Worklog worklog : initialWorklogs) {
                Objects.requireNonNull(worklog, "Worklog must not be null");
                this.worklogs.add(worklog);
            }
        }
    }

    public Task(UUID id, UUID projectId, String title) {
        this(id, projectId, title, "", Priority.MEDIUM, TaskState.TODO, null, null, null, null);
    }

    public Task(UUID projectId, String title) {
        this(UUID.randomUUID(), projectId, title);
    }

    public Task(UUID projectId, String title, String description) {
        this(UUID.randomUUID(), projectId, title, description, Priority.MEDIUM, TaskState.TODO, null, null, null, null);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        validateTitle(title);
        this.title = title.strip();
    }

    public static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("Task title must not be blank");
        }
    }

    public static void validateEstimatedMinutes(Integer estimatedMinutes) {
        if (estimatedMinutes != null && estimatedMinutes < 0) {
            throw new ValidationException("Estimated minutes cannot be negative, got: " + estimatedMinutes);
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.strip();
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = Objects.requireNonNull(priority, "Priority must not be null");
    }

    public TaskState getState() {
        return state;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        validateEstimatedMinutes(estimatedMinutes);
        this.estimatedMinutes = estimatedMinutes;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Set<String> getLabels() {
        return Collections.unmodifiableSet(labels);
    }

    public void addLabel(String rawLabel) {
        String normalized = Label.normalize(rawLabel);
        this.labels.add(normalized);
    }

    public void removeLabel(String rawLabel) {
        String normalized = Label.normalize(rawLabel);
        this.labels.remove(normalized);
    }

    public boolean hasLabel(String rawLabel) {
        String normalized = Label.normalize(rawLabel);
        return this.labels.contains(normalized);
    }

    public List<Worklog> getWorklogs() {
        return Collections.unmodifiableList(worklogs);
    }

    public void addWorklog(Worklog worklog) {
        Objects.requireNonNull(worklog, "Worklog must not be null");
        if (this.state != TaskState.IN_PROGRESS) {
            throw new ValidationException("Worklog can only be added to a task in IN_PROGRESS state, but task is in " + this.state);
        }
        this.worklogs.add(worklog);
    }

    public void restoreWorklog(Worklog worklog) {
        Objects.requireNonNull(worklog, "Worklog must not be null");
        this.worklogs.add(worklog);
    }

    public void transitionTo(TaskState nextState) {
        state.validateTransitionTo(nextState, !worklogs.isEmpty());
        this.state = nextState;
    }

    public long getTotalRecordedMinutes() {
        return worklogs.stream().mapToLong(Worklog::durationMinutes).sum();
    }

    public Long getVarianceMinutes() {
        if (estimatedMinutes == null) {
            return null;
        }
        return getTotalRecordedMinutes() - estimatedMinutes;
    }

    public boolean isOverdue(LocalDate today) {
        Objects.requireNonNull(today, "Date must not be null");
        if (dueDate == null || state == TaskState.DONE) {
            return false;
        }
        return dueDate.isBefore(today);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(id, task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", projectId=" + projectId +
                ", title='" + title + '\'' +
                ", priority=" + priority +
                ", state=" + state +
                ", estimatedMinutes=" + estimatedMinutes +
                ", dueDate=" + dueDate +
                '}';
    }
}
