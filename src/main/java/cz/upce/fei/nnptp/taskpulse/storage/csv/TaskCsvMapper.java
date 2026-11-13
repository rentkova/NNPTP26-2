package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Label;
import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskBuilder;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class TaskCsvMapper implements CsvEntityMapper<Task> {
    private static final String ID = "id";
    private static final String PROJECT_ID = "projectId";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String PRIORITY = "priority";
    private static final String STATE = "state";
    private static final String ESTIMATED_MINUTES = "estimatedMinutes";
    private static final String DUE_DATE = "dueDate";
    private static final String LABELS = "labels";
    private static final List<String> COLUMNS = List.of(
            ID, PROJECT_ID, TITLE, DESCRIPTION, PRIORITY, STATE, ESTIMATED_MINUTES, DUE_DATE, LABELS);

    @Override
    public String fileName() {
        return "tasks.csv";
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public Task fromRecord(CsvRecord record) {
        return new TaskBuilder(record.required(PROJECT_ID, UUID::fromString), record.text(TITLE))
                .id(record.required(ID, UUID::fromString))
                .description(record.text(DESCRIPTION))
                .priority(record.required(PRIORITY, Priority::valueOf))
                .state(record.required(STATE, TaskState::valueOf))
                .estimatedMinutes(record.optional(ESTIMATED_MINUTES, Integer::valueOf))
                .dueDate(record.optional(DUE_DATE, LocalDate::parse))
                .labels(parseLabels(record.text(LABELS)))
                .build();
    }

    @Override
    public List<String> toFields(Task task) {
        return List.of(
                task.getId().toString(),
                task.getProjectId().toString(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority().name(),
                task.getState().name(),
                Objects.toString(task.getEstimatedMinutes(), ""),
                Objects.toString(task.getDueDate(), ""),
                String.join(Label.SEPARATOR, task.getLabels()));
    }

    private static List<String> parseLabels(String rawLabels) {
        return rawLabels.isEmpty() ? List.of() : Arrays.asList(rawLabels.split(Label.SEPARATOR, -1));
    }
}
