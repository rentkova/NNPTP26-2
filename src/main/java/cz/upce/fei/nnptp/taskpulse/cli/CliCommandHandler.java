package cz.upce.fei.nnptp.taskpulse.cli;

import java.util.List;



interface CliCommandHandler {
    void handle(List<String> args);
}
