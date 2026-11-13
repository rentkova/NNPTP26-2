package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPulseDateTimeTest {
    private static final Instant NEAR_MIDNIGHT = Instant.parse("2024-02-29T23:30:00Z");
    private static final LocalDate LEAP_DAY = LocalDate.of(2024, 2, 29);

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;
    private TaskService tasks;
    private TaskPulseCli cli;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        configureCli(ZoneOffset.UTC);
    }

    private void configureCli(ZoneId zone) {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        var projectRepository = new InMemoryProjectRepository();
        var taskRepository = new InMemoryTaskRepository();
        var projects = new ProjectService(projectRepository, taskRepository);
        tasks = new TaskService(taskRepository, projectRepository, Clock.fixed(NEAR_MIDNIGHT, zone));
        cli = new TaskPulseCli(projects, tasks, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        projectId = projects.createProject("Calendar", "").getId();
    }

    @Test
    void includesBothBoundsAcrossLeapDayAndExcludesDatesOutsideRange() {
        Task before = addTask("February 27", LEAP_DAY.minusDays(2));
        Task lower = addTask("February 28", LEAP_DAY.minusDays(1));
        Task inside = addTask("Leap day", LEAP_DAY);
        Task upper = addTask("March 1", LEAP_DAY.plusDays(1));
        Task after = addTask("March 2", LEAP_DAY.plusDays(2));
        Task undated = addTask("No due date", null);

        cli.executeLine("task list --from 2024-02-28 --to 2024-03-01 --sort duedate");

        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(out.toString(StandardCharsets.UTF_8))
                .containsSubsequence(lower.getId().toString(), inside.getId().toString(), upper.getId().toString())
                .doesNotContain(before.getId().toString(), after.getId().toString(), undated.getId().toString());
    }

    @ParameterizedTest
    @CsvSource({"UTC, false", "Europe/Prague, true"})
    void combinesSingleDayRangeWithOverdueUsingLocalDate(String zone, boolean overdue) {
        configureCli(ZoneId.of(zone));
        Task leapDay = addTask("Leap day", LEAP_DAY);
        Task earlier = addTask("Earlier", LEAP_DAY.minusDays(1));

        cli.executeLine("task list --from 2024-02-29 --to 2024-02-29 --overdue");

        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        String output = out.toString(StandardCharsets.UTF_8);
        if (overdue) {
            assertThat(output).contains(leapDay.getId().toString()).doesNotContain(earlier.getId().toString());
        } else {
            assertThat(output.strip()).isEqualTo("No tasks found.");
        }
    }

    @ParameterizedTest
    @CsvSource({"--from, 2023-02-29", "--to, 2024-04-31"})
    void rejectsImpossibleCalendarDates(String option, String date) {
        cli.executeLine("task list " + option + " " + date);

        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Invalid date: '" + date + "'");
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2_147_483_648L, Long.MAX_VALUE})
    void acceptsPositiveWholeMinutesWithoutNarrowingToAnInt(long minutes) {
        Task task = startTask();

        cli.executeLine("worklog log " + task.getId() + " " + minutes + " --author Student");

        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(tasks.getTask(task.getId()).getWorklogs())
                .containsExactly(new Worklog(LocalDateTime.of(2024, 2, 29, 23, 30), minutes, "Student"));
    }

    @ParameterizedTest
    @CsvSource({
            "0, Duration must be a positive number of minutes",
            "-1, Duration must be a positive number of minutes",
            "1.5, Duration must be a valid integer number of minutes",
            "01:30, Duration must be a valid integer number of minutes",
            "9223372036854775808, Duration must be a valid integer number of minutes"
    })
    void rejectsInvalidDurationsWithoutAddingAWorklog(String input, String message) {
        Task task = startTask();

        cli.executeLine("worklog log " + task.getId() + " " + input + " --author Student");

        assertThat(err.toString(StandardCharsets.UTF_8)).contains(message);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(tasks.getTask(task.getId()).getWorklogs()).isEmpty();
    }

    @Test
    void missingDurationShowsUsageWithoutAddingAWorklog() {
        Task task = startTask();

        cli.executeLine("worklog log " + task.getId());

        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("Usage: worklog log <task-id> <durationMinutes>");
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(tasks.getTask(task.getId()).getWorklogs()).isEmpty();
    }

    private Task startTask() {
        Task task = addTask("Timed task", null);
        return tasks.transitionTask(task.getId(), TaskState.IN_PROGRESS);
    }

    private Task addTask(String title, LocalDate dueDate) {
        return tasks.createTask(projectId, title, "", Priority.MEDIUM, null, dueDate);
    }
}
