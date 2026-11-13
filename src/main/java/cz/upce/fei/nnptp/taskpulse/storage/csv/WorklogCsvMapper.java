package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Worklog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class WorklogCsvMapper implements CsvEntityMapper<WorklogCsvMapper.TaskWorklog> {
    private static final String TASK_ID = "taskId";
    private static final String TIMESTAMP = "timestamp";
    private static final String DURATION_MINUTES = "durationMinutes";
    private static final String AUTHOR = "author";
    private static final String DESCRIPTION = "description";
    private static final List<String> COLUMNS = List.of(TASK_ID, TIMESTAMP, DURATION_MINUTES, AUTHOR, DESCRIPTION);

    /** A worklog together with the identifier of the task it belongs to. */
    record TaskWorklog(UUID taskId, Worklog worklog) {
        TaskWorklog {
            Objects.requireNonNull(taskId, "Task ID must not be null");
            Objects.requireNonNull(worklog, "Worklog must not be null");
        }
    }

    @Override
    public String fileName() {
        return "worklogs.csv";
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public TaskWorklog fromRecord(CsvRecord record) {
        UUID taskId = record.required(TASK_ID, UUID::fromString);
        Worklog worklog = new Worklog(
                record.required(TIMESTAMP, LocalDateTime::parse),
                record.required(DURATION_MINUTES, Long::parseLong),
                record.text(AUTHOR),
                record.text(DESCRIPTION));
        return new TaskWorklog(taskId, worklog);
    }

    @Override
    public List<String> toFields(TaskWorklog taskWorklog) {
        Worklog worklog = taskWorklog.worklog();
        return List.of(
                taskWorklog.taskId().toString(),
                worklog.timestamp().toString(),
                Long.toString(worklog.durationMinutes()),
                worklog.author(),
                worklog.description());
    }
}
