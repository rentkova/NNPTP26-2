package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.report.JsonReportExporter;
import cz.upce.fei.nnptp.taskpulse.report.MarkdownReportExporter;
import cz.upce.fei.nnptp.taskpulse.report.ReportExporter;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskQuery;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

final class ReportCliHandler implements CliCommandHandler {
    static final String USAGE = "report export <project-id> --format <markdown|json> [--output <file-path>]";

    private final ProjectService projectService;
    private final TaskService taskService;
    private final PrintStream out;
    private final PrintStream err;
    private final Clock clock;

    ReportCliHandler(ProjectService projectService, TaskService taskService, PrintStream out, PrintStream err,
                     Clock clock) {
        this.projectService = Objects.requireNonNull(projectService, "ProjectService must not be null");
        this.taskService = Objects.requireNonNull(taskService, "TaskService must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.err = Objects.requireNonNull(err, "err must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    public void handle(List<String> args) {
        if (args.size() < 2 || !"export".equalsIgnoreCase(args.get(0))) {
            throw new IllegalArgumentException("Usage: " + USAGE);
        }

        UUID projectId = TaskPulseCli.parseUuid(args.get(1));
        String format = null;
        Path output = null;

        for (int i = 2; i < args.size(); i++) {
            String option = args.get(i);
            if (!"--format".equalsIgnoreCase(option) && !"--output".equalsIgnoreCase(option)) {
                throw new IllegalArgumentException("Unknown report option: " + option + ". Usage: " + USAGE);
            }
            if (i + 1 >= args.size() || args.get(i + 1).startsWith("--") || args.get(i + 1).isBlank()) {
                throw new IllegalArgumentException("Missing value for " + option + ". Usage: " + USAGE);
            }
            String value = args.get(++i);
            if ("--format".equalsIgnoreCase(option)) {
                if (format != null) {
                    throw new IllegalArgumentException("Duplicate option: --format");
                }
                format = value;
            } else {
                if (output != null) {
                    throw new IllegalArgumentException("Duplicate option: --output");
                }
                output = Path.of(value);
            }
        }

        if (format == null) {
            throw new IllegalArgumentException("Missing --format. Usage: " + USAGE);
        }

        ReportExporter exporter = resolveExporter(format);
        Project project = projectService.getProject(projectId);
        List<Task> tasks = taskService.findTasks(TaskQuery.builder().projectId(projectId).build());
        String report = exporter.export(project, tasks, LocalDate.now(clock));

        if (output == null) {
            out.print(report);
            return;
        }

        try {
            Files.writeString(output, report, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            out.println("Report exported to: " + output);
        } catch (FileAlreadyExistsException e) {
            err.println("Error: Output already exists: " + output + ". Choose a new file path.");
        } catch (IOException e) {
            err.println("Error: Cannot write report to " + output + ": " + e.getMessage());
        }
    }

    private static ReportExporter resolveExporter(String format) {
        return switch (format.strip().toLowerCase(Locale.ROOT)) {
            case "markdown" -> new MarkdownReportExporter();
            case "json" -> new JsonReportExporter();
            default -> throw new IllegalArgumentException(
                    "Unsupported report format: " + format + ". Supported: markdown, json");
        };
    }
}
