package io.voltweave.simulator.state;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import tools.jackson.databind.ObjectMapper;

public final class SimulatorStateStore {
    private final Path stateFile;
    private final ObjectMapper objectMapper;

    public SimulatorStateStore(Path stateFile, ObjectMapper objectMapper) {
        this.stateFile = stateFile;
        this.objectMapper = objectMapper;
    }

    public Optional<SimulatorState> load() throws IOException {
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(
                Files.readAllBytes(stateFile), SimulatorState.class
        ));
    }

    public void save(SimulatorState state) throws IOException {
        Files.createDirectories(stateFile.getParent());
        Path temporary = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
        Files.write(temporary, objectMapper.writeValueAsBytes(state));
        try {
            Files.move(
                    temporary, stateFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
