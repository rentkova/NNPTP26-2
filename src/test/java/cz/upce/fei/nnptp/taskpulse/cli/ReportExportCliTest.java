package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportCliTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    @TempDir
    Path directory;

    private ProjectService projects;
    private TaskService tasks;
    private Project project;
    private TaskPulseCli cli;

    @BeforeEach
    void setUp() {
        var projectRepository = new InMemoryProjectRepository();
        var taskRepository = new InMemoryTaskRepository();
        projects = new ProjectService(projectRepository, taskRepository);
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T23:30:00Z"), ZoneId.of("Europe/Prague"));
        tasks = new TaskService(taskRepository, projectRepository, clock);
        project = projects.createProject("Cesky projekt", "Prehled prace");
        cli = new TaskPulseCli(projects, tasks,
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                clock);
    }

    @Test
    void printsOnlySelectedProjectWithMetricsAndDoesNotMutateTasks() {
        Task late = tasks.createTask(project.getId(), "Late", "", Priority.HIGH, 30, TODAY.minusDays(1));
        tasks.transitionTask(late.getId(), TaskState.IN_PROGRESS);
        tasks.logWork(late.getId(), 15, "Developer", "Work");
        Task today = tasks.createTask(project.getId(), "Today", "", Priority.LOW, null, TODAY);
        Task done = tasks.createTask(project.getId(), "Done", "", Priority.MEDIUM, 0, TODAY.minusDays(2));
        tasks.transitionTask(done.getId(), TaskState.IN_PROGRESS);
        tasks.logWork(done.getId(), 5, "Developer", "Done");
        tasks.transitionTask(done.getId(), TaskState.DONE);
        Project other = projects.createProject("Other", "");
        Task excluded = tasks.createTask(other.getId(), "Excluded", "", Priority.HIGH, null, TODAY.minusDays(1));

        assertThat(cli.executeLine(commandMarkdown())).isTrue();

        assertThat(stdout()).contains(
                        "# Project Summary: Cesky projekt",
                        "| Overdue | 1 |",
                        "| Total recorded minutes | 20 |",
                        "| TODO | 1 |",
                        "| IN_PROGRESS | 1 |",
                        "| DONE | 1 |")
                .contains(late.getId().toString(), today.getId().toString(), done.getId().toString())
                .doesNotContain(excluded.getId().toString())
                .containsSubsequence("| Done |", "| Late |", "| Today |");
        assertThat(stderr()).isEmpty();
        assertThat(tasks.listTasks()).hasSize(4);
        assertThat(late.getState()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(late.getTotalRecordedMinutes()).isEqualTo(15);
        assertThat(today.getEstimatedMinutes()).isNull();
        assertThat(project.getName()).isEqualTo("Cesky projekt");
    }

    @Test
    void exportsEmptyProjectAsMarkdown() {
        cli.executeLine(commandMarkdown());
        assertThat(stdout()).contains("| TODO | 0 |", "| DONE | 0 |", "| Overdue | 0 |",
                "| Total recorded minutes | 0 |");
        assertThat(stderr()).isEmpty();
    }

    @Test
    void exportsJsonToConsoleUsingMergedJsonExporter() {
        Task task = tasks.createTask(project.getId(), "Json task", "", Priority.LOW, null, TODAY);

        cli.executeLine(commandJson());

        assertThat(stdout()).contains(
                "\"name\": \"Cesky projekt\"",
                "\"description\": \"Prehled prace\"",
                "\"overdueCount\": 0",
                "\"totalRecordedMinutes\": 0",
                "\"title\": \"Json task\"",
                "\"estimatedMinutes\": null",
                task.getId().toString());
        assertThat(stderr()).isEmpty();
    }

    @Test
    void writesUtf8MarkdownToQuotedPathAndAcceptsOptionsInEitherOrder() throws Exception {
        Path file = directory.resolve("prehled-projektu.md");
        cli.executeLine(commandMarkdown());
        String expected = stdout();
        output.reset();

        cli.executeLine("report EXPORT " + project.getId() + " --OUTPUT \"" + file + "\" --FORMAT MARKDOWN");

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(expected);
        assertThat(stdout()).contains("Report exported to: " + file).doesNotContain("# Project Summary");
        assertThat(stderr()).isEmpty();
    }

    @Test
    void writesJsonReportToFile() throws Exception {
        Path file = directory.resolve("report.json");
        cli.executeLine(commandJson() + " --output \"" + file + "\"");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(content).contains("\"name\": \"Cesky projekt\"", "\"tasks\": []");
        assertThat(stdout()).contains("Report exported to: " + file);
        assertThat(stderr()).isEmpty();
    }

    @Test
    void neverOverwritesExistingFile() throws Exception {
        Path file = directory.resolve("existing.md");
        Files.writeString(file, "Keep this content");

        cli.executeLine(commandMarkdown() + " --output \"" + file + "\"");

        assertThat(Files.readString(file)).isEqualTo("Keep this content");
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("Output already exists", "Choose a new file path");
    }

    @Test
    void reportsMissingOutputDirectoryWithoutSuccessMessage() {
        Path file = directory.resolve("missing/report.md");
        cli.executeLine(commandMarkdown() + " --output \"" + file + "\"");
        assertThat(file).doesNotExist();
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("Cannot write report").doesNotContain("Unexpected error");
    }

    @Test
    void rejectsDirectoryAsOutput() {
        cli.executeLine(commandMarkdown() + " --output \"" + directory + "\"");
        assertThat(directory).isDirectory();
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("Error:");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "--format",
            "--format --output report.md",
            "--format yaml",
            "--format markdown --output",
            "--format markdown --unknown value",
            "--format markdown --format markdown",
            "--format markdown extra",
            "--format markdown --output first.md --output second.md"
    })
    void rejectsInvalidOptionsBeforeWritingAnything(String options) throws Exception {
        Path file = directory.resolve("must-not-exist.md");
        cli.executeLine("report export " + project.getId() + " --output \"" + file + "\" " + options);
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("Invalid argument:");
        assertThat(file).doesNotExist();
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"report", "report export", "report unknown", "report unknown ignored"})
    void explainsCommandUsage(String command) {
        cli.executeLine(command);
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("Usage: report export <project-id> --format <markdown|json>");
    }

    @Test
    void rejectsInvalidProjectId() {
        cli.executeLine("report export not-a-uuid --format markdown");
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("Invalid argument:").doesNotContain("Unexpected error");
    }

    @Test
    void unknownProjectDoesNotCreateOutputFile() {
        Path file = directory.resolve("unknown.md");
        cli.executeLine("report export " + UUID.randomUUID() + " --format markdown --output \"" + file + "\"");
        assertThat(file).doesNotExist();
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("Project not found");
    }

    @Test
    void helpDocumentsFormatOutputAndOverwriteBehavior() {
        cli.executeLine("help");
        assertThat(stdout()).contains(
                "report export <project-id> --format <markdown|json> [--output <file-path>]",
                "Existing files are never overwritten");
    }

    private String commandMarkdown() {
        return "report export " + project.getId() + " --format markdown";
    }

    private String commandJson() {
        return "report export " + project.getId() + " --format json";
    }

    private String stdout() {
        return output.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errors.toString(StandardCharsets.UTF_8);
    }
}
