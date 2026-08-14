package io.voltweave.simulator;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.voltweave.simulator.config.ScenarioLoader;
import io.voltweave.simulator.mqtt.MqttDeviceRuntime;
import tools.jackson.databind.json.JsonMapper;

public final class SimulatorApplication {
    private SimulatorApplication() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar simulation-service-exec.jar <scenario.json>");
            System.exit(2);
        }

        var configuration = ScenarioLoader.load(Path.of(args[0]));
        var objectMapper = JsonMapper.builder().build();
        List<MqttDeviceRuntime> runtimes = new ArrayList<>();
        try {
            for (var scenario : configuration.devices()) {
                var runtime = new MqttDeviceRuntime(
                        configuration.brokerUri(), scenario,
                        configuration.telemetryIntervalSeconds(), objectMapper,
                        Clock.systemUTC()
                );
                runtime.start();
                runtimes.add(runtime);
                System.out.printf("Started %s simulator %s%n",
                        scenario.type(), scenario.deviceId());
            }
        } catch (Exception exception) {
            runtimes.forEach(MqttDeviceRuntime::close);
            throw exception;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> runtimes.forEach(MqttDeviceRuntime::close), "simulator-shutdown"
        ));
        new CountDownLatch(1).await();
    }
}
