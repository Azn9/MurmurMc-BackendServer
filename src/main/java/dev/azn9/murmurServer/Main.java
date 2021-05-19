package dev.azn9.murmurServer;

import io.sentry.Sentry;
import io.vertx.core.Vertx;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Sentry.init(options -> options.setDsn("https://7f1ca4810e164a9fa4b34bc9323a50b2@o314452.ingest.sentry.io/5750772"));

        DockerService dockerService = new DockerService();
        dockerService.initialize();

        WebSocketService webSocketService = new WebSocketService();
        webSocketService.start();

        VerticleService verticleService = new VerticleService(dockerService, webSocketService);
        Vertx.vertx().deployVerticle(verticleService);

        boolean exit = false;

        while (!exit) {
            Scanner in = new Scanner(System.in);

            String command = in.nextLine();

            if ("stop".equalsIgnoreCase(command)) {
                exit = true;
            } else if (command.startsWith("cmd:")) {
                webSocketService.broadcastCommand(command.substring(4));
            }
        }

        System.exit(0);
    }

}
