package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.domain.Task;
import cz.upce.fei.nnptp.taskpulse.domain.TaskBuilder;
import cz.upce.fei.nnptp.taskpulse.domain.TaskState;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static cz.upce.fei.nnptp.taskpulse.storage.csv.CsvMapperTestSupport.fromValues;
import static cz.upce.fei.nnptp.taskpulse.storage.csv.CsvMapperTestSupport.roundTrip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskCsvMapperTest {

    private final TaskCsvMapper mapper = new TaskCsvMapper();

    @Test
    void describesTasksFile() {
        assertThat(mapper.fileName()).isEqualTo("tasks.csv");
        assertThat(mapper.columns()).containsExactly("id", "projectId", "title", "description",
                "priority", "state", "estimatedMinutes", "dueDate", "labels");
    }

    @Test
    void fromRecordCreatesTaskWithAllFields() {
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        Task task = fromValues(mapper, 2, id.toString(), projectId.toString(), "Opravit login", "Popis",
                "HIGH", "IN_PROGRESS", "90", "2026-12-31", "backend;security");

        assertThat(task.getId()).isEqualTo(id);
        assertThat(task.getProjectId()).isEqualTo(projectId);
        assertThat(task.getTitle()).isEqualTo("Opravit login");
        assertThat(task.getDescription()).isEqualTo("Popis");
        assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(task.getState()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(task.getEstimatedMinutes()).isEqualTo(90);
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(task.getLabels()).containsExactlyInAnyOrder("backend", "security");
    }

    @Test
    void fromRecordTreatsBlankOptionalFieldsAsAbsent() {
        Task task = fromValues(mapper, 2, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "Úkol", "", "LOW", "TODO", "", "", "");

        assertThat(task.getEstimatedMinutes()).isNull();
        assertThat(task.getDueDate()).isNull();
        assertThat(task.getLabels()).isEmpty();
    }

    @Test
    void toFieldsWritesAbsentOptionalFieldsAsBlank() {
        Task task = new TaskBuilder(UUID.randomUUID(), "Úkol").build();

        List<String> fields = mapper.toFields(task);

        assertThat(fields).hasSize(mapper.columns().size());
        assertThat(fields.subList(6, 9)).containsOnly("");
    }

    @Test
    void roundTripPreservesAllFields() {
        Task original = new TaskBuilder(UUID.randomUUID(), "Titulek, \"s uvozovkami\"")
                .description("Více\nřádků")
                .priority(Priority.HIGH)
                .state(TaskState.IN_PROGRESS)
                .estimatedMinutes(120)
                .dueDate(LocalDate.of(2026, 11, 30))
                .labels(List.of("api", "čeština"))
                .build();

        Task restored = roundTrip(mapper, original);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getProjectId()).isEqualTo(original.getProjectId());
        assertThat(restored.getTitle()).isEqualTo(original.getTitle());
        assertThat(restored.getDescription()).isEqualTo(original.getDescription());
        assertThat(restored.getPriority()).isEqualTo(original.getPriority());
        assertThat(restored.getState()).isEqualTo(original.getState());
        assertThat(restored.getEstimatedMinutes()).isEqualTo(original.getEstimatedMinutes());
        assertThat(restored.getDueDate()).isEqualTo(original.getDueDate());
        assertThat(restored.getLabels()).containsExactlyInAnyOrderElementsOf(original.getLabels());
    }

    @Test
    void fromRecordReportsInvalidState() {
        assertThatThrownBy(() -> fromValues(mapper, 7, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "Úkol", "", "LOW", "FINISHED", "", "", ""))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("tasks.csv")
                .hasMessageContaining("line 7")
                .hasMessageContaining("column 'state'")
                .hasMessageContaining("FINISHED");
    }
}
