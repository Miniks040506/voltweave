package io.voltweave.simulator.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.databind.json.JsonMapper;

public final class ScenarioLoader {
    private ScenarioLoader() {
    }

    public static SimulatorConfiguration load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Scenario file does not exist: " + path);
        }
        return JsonMapper.builder().build().readValue(
                Files.readString(path), SimulatorConfiguration.class
        );
    }
}
