package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Project;
import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static cz.upce.fei.nnptp.taskpulse.storage.csv.CsvMapperTestSupport.fromValues;
import static cz.upce.fei.nnptp.taskpulse.storage.csv.CsvMapperTestSupport.roundTrip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectCsvMapperTest {

    private final ProjectCsvMapper mapper = new ProjectCsvMapper();

    @Test
    void describesProjectsFile() {
        assertThat(mapper.fileName()).isEqualTo("projects.csv");
        assertThat(mapper.columns()).containsExactly("id", "name", "description");
    }

    @Test
    void fromRecordCreatesProject() {
        UUID id = UUID.randomUUID();

        Project project = fromValues(mapper, 2, id.toString(), "Web", "Firemní web");

        assertThat(project.getId()).isEqualTo(id);
        assertThat(project.getName()).isEqualTo("Web");
        assertThat(project.getDescription()).isEqualTo("Firemní web");
    }

    @Test
    void toFieldsFollowsColumnOrder() {
        UUID id = UUID.randomUUID();

        assertThat(mapper.toFields(new Project(id, "Web", "Popis")))
                .containsExactly(id.toString(), "Web", "Popis");
    }

    @Test
    void roundTripPreservesSpecialCharacters() {
        Project original = new Project(UUID.randomUUID(), "Název, \"s uvozovkami\"", "Řádek 1\nŘádek 2");

        Project restored = roundTrip(mapper, original);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getDescription()).isEqualTo(original.getDescription());
    }

    @Test
    void fromRecordReportsInvalidId() {
        assertThatThrownBy(() -> fromValues(mapper, 4, "not-a-uuid", "Web", ""))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("projects.csv")
                .hasMessageContaining("line 4")
                .hasMessageContaining("column 'id'")
                .hasMessageContaining("not-a-uuid");
    }
}
