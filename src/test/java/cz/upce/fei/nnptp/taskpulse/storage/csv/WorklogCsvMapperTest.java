package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Worklog;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static cz.upce.fei.nnptp.taskpulse.storage.csv.CsvMapperTestSupport.fromValues;
import static cz.upce.fei.nnptp.taskpulse.storage.csv.CsvMapperTestSupport.roundTrip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorklogCsvMapperTest {

    private final WorklogCsvMapper mapper = new WorklogCsvMapper();

    @Test
    void describesWorklogsFile() {
        assertThat(mapper.fileName()).isEqualTo("worklogs.csv");
        assertThat(mapper.columns()).containsExactly("taskId", "timestamp", "durationMinutes", "author", "description");
    }

    @Test
    void fromRecordCreatesWorklogWithTaskReference() {
        UUID taskId = UUID.randomUUID();

        WorklogCsvMapper.TaskWorklog result =
                fromValues(mapper, 2, taskId.toString(), "2026-10-21T14:30", "45", "Petr", "Code review");

        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.worklog()).isEqualTo(
                new Worklog(LocalDateTime.of(2026, 10, 21, 14, 30), 45, "Petr", "Code review"));
    }

    @Test
    void toFieldsFollowsColumnOrder() {
        UUID taskId = UUID.randomUUID();
        Worklog worklog = new Worklog(LocalDateTime.of(2026, 10, 21, 14, 30), 30, "Petr", "Hotovo");

        assertThat(mapper.toFields(new WorklogCsvMapper.TaskWorklog(taskId, worklog)))
                .containsExactly(taskId.toString(), "2026-10-21T14:30", "30", "Petr", "Hotovo");
    }

    @Test
    void roundTripPreservesSpecialCharacters() {
        WorklogCsvMapper.TaskWorklog original = new WorklogCsvMapper.TaskWorklog(UUID.randomUUID(),
                new Worklog(LocalDateTime.of(2026, 1, 2, 3, 4, 5), 15, "Novák, Jan", "Popis \"s uvozovkami\"\na řádkem"));

        assertThat(roundTrip(mapper, original)).isEqualTo(original);
    }

    @Test
    void fromRecordReportsInvalidDuration() {
        assertThatThrownBy(() -> fromValues(mapper, 6,
                UUID.randomUUID().toString(), "2026-10-21T14:30", "abc", "Petr", ""))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("worklogs.csv")
                .hasMessageContaining("line 6")
                .hasMessageContaining("column 'durationMinutes'")
                .hasMessageContaining("abc");
    }
}
