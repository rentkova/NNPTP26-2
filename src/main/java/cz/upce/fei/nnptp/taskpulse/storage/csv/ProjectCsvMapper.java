package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.domain.Project;

import java.util.List;
import java.util.UUID;

final class ProjectCsvMapper implements CsvEntityMapper<Project> {
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    private static final List<String> COLUMNS = List.of(ID, NAME, DESCRIPTION);

    @Override
    public String fileName() {
        return "projects.csv";
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public Project fromRecord(CsvRecord record) {
        return new Project(
                record.required(ID, UUID::fromString),
                record.text(NAME),
                record.text(DESCRIPTION));
    }

    @Override
    public List<String> toFields(Project project) {
        return List.of(project.getId().toString(), project.getName(), project.getDescription());
    }
}
