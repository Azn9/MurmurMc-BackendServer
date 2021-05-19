package dev.azn9.murmurServer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.net.Socket;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class VerticleService extends AbstractVerticle {

    private final Map<String, String> serverIps = new HashMap<>();
    private final Map<String, Tuple<String, String>> codes = new HashMap<>();
    private final Map<String, String> serverContainer = new HashMap<>();
    private final Random random = new SecureRandom();
    private final DockerService dockerService;
    private final WebSocketService webSocketService;

    public VerticleService(DockerService dockerService, WebSocketService webSocketService) {
        this.dockerService = dockerService;
        this.webSocketService = webSocketService;
    }

    @Override
    public void start(Future<Void> fut) {
        Router router = Router.router(this.vertx);

        router.route("/connect/*").handler(StaticHandler.create("assets"));

        router.route("/api/*").handler(BodyHandler.create()).handler(this::apiKeyCheck);

        router.get("/api/ping").handler(this::ping);
        router.post("/api/createurl").handler(this::createUrl);
        router.get("/api/validatecode").handler(this::validateCode);
        router.post("/api/createserver").handler(this::createServer);
        router.delete("/api/deleteserver").handler(this::deleteServer);

        router.route("/commands/*").handler(BodyHandler.create());
        router.post("/commands/exec").handler(this::onCommand);

        this.vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(this.config().getInteger("http.port", 63000), result -> {
                    if (result.succeeded()) {
                        fut.complete();
                    } else {
                        fut.fail(result.cause());
                    }
                });
    }

    private void apiKeyCheck(RoutingContext routingContext) {
        JsonObject jsonObject = routingContext.getBodyAsJson();

        if (jsonObject == null || !jsonObject.getString("api_key", "").matches("[a-zA-Z0-9]{32}")) {
            routingContext.response().setStatusCode(401).end();
            return;
        }

        //TODO verify key

        routingContext.next();
    }

    private void ping(RoutingContext routingContext) {
        routingContext.response().setStatusCode(204).end();
    }

    private void onCommand(RoutingContext routingContext) {
        JsonObject object = routingContext.getBodyAsJson();
        String serverUuid = object.getString("server_uuid", "");
        String command = object.getString("command", "");

        if (serverUuid.isEmpty() || command.isEmpty()) {
            return;
        }

        String username = object.getString("username", "");
        String channel = object.getString("channel", "");
        String pos = object.getString("pos", "");
        boolean alive = object.getBoolean("alive", false);

        switch (command) {
            case "mute":
                if (username.isEmpty()) {
                    break;
                }

                this.webSocketService.sendMuteCommand(serverUuid, username);
                break;

            case "unmute":
                if (username.isEmpty()) {
                    break;
                }

                this.webSocketService.sendUnmuteCommand(serverUuid, username);
                break;

            case "move":
                if (username.isEmpty() || channel.isEmpty()) {
                    break;
                }

                this.webSocketService.sendMoveCommand(serverUuid, username, channel);
                break;

            case "massmove":
                if (channel.isEmpty()) {
                    break;
                }

                this.webSocketService.sendMassMoveCommand(serverUuid, channel);
                break;

            case "updatepos":
                if (username.isEmpty() || pos.isEmpty()) {
                    break;
                }

                this.webSocketService.sendPositionUpdate(serverUuid, username, pos);
                break;

            case "join":
                if (username.isEmpty()) {
                    break;
                }

                this.webSocketService.onJoinMc(serverUuid, username, alive);
                break;

            case "leave":
                if (username.isEmpty()) {
                    break;
                }

                this.webSocketService.onLeaveMc(serverUuid, username);
                break;

            case "isonline":
                if (username.isEmpty()) {
                    break;
                }

                boolean online = this.webSocketService.isOnline(serverUuid, username);

                routingContext.response().setStatusCode(201).end("{\"response\":" + online + "}");
                return;

            case "getonline":
                routingContext.response().setStatusCode(201).end(Json.encodePrettily(this.webSocketService.getOnline(serverUuid)));
                return;

            default:
                break;
        }

        routingContext.response().setStatusCode(204).end();
    }

    private void deleteServer(RoutingContext routingContext) {
        JsonObject data = routingContext.getBodyAsJson();
        String serverUuid = data.getString("server_uuid", "");

        if (serverUuid.isEmpty()) {
            routingContext.response().setStatusCode(404).end();
            return;
        }

        String ip = this.serverIps.remove(serverUuid);

        if (ip != null && !ip.isEmpty()) {
            new HashMap<>(this.codes).forEach((s, tuple) -> {
                if (tuple.getValue().equalsIgnoreCase(ip)) {
                    this.codes.remove(s);
                }
            });
        }

        String containerId = this.serverContainer.remove(serverUuid);

        if (containerId == null || containerId.isEmpty()) {
            return;
        }

        this.dockerService.stopServer(containerId);

        routingContext.response().setStatusCode(204).end();
    }

    private void createServer(RoutingContext routingContext) {
        JsonObject data = routingContext.getBodyAsJson();
        String serverUuid = data.getString("server_uuid", "");

        if (serverUuid.isEmpty()) {
            routingContext.response().setStatusCode(404).end();
            return;
        }

        new Thread(() -> {
            int port;

            do {
                port = 63001 + this.random.nextInt(1000);
            } while (!this.isPortAvailable(port));

            this.serverIps.put(serverUuid, "azn9.dev:" + port);

            this.serverContainer.put(serverUuid, this.dockerService.createServer(port));
        }).start();

        routingContext.response().setStatusCode(204).end();
    }

    private void validateCode(RoutingContext routingContext) {
        JsonObject data = routingContext.getBodyAsJson();
        String code = data.getString("code", "");

        if (code.isEmpty() || !this.codes.containsKey(code)) {
            routingContext.response().setStatusCode(201).end("{\"result\":\"unknown_code\"}");
            return;
        }

        Tuple<String, String> codeData = this.codes.remove(code);

        routingContext.response().setStatusCode(201).end("{\"result\":\"ok\",\"url\":\"mumble://" + codeData.getKey() + "@" + codeData.getValue() + "\"}");
    }

    private void createUrl(RoutingContext routingContext) {
        JsonObject data = routingContext.getBodyAsJson();
        String serverUuid = data.getString("server_uuid", "");
        String username = data.getString("username", "");

        if (serverUuid.isEmpty() || username.isEmpty()) {
            routingContext.response().setStatusCode(404).end();
            return;
        }

        if (!this.serverIps.containsKey(serverUuid)) {
            routingContext.response().setStatusCode(404).end();
            return;
        }

        String code = this.generateCode();
        this.codes.put(code, new Tuple<>(this.serverIps.get(serverUuid), username));

        routingContext.response().setStatusCode(201).end("{\"code\":\"" + code + "\"}");
    }

    private String generateCode() {
        int leftLimit = 48;
        int rightLimit = 122;
        int targetStringLength = 25;

        return this.random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private boolean isPortAvailable(int port) {
        try {
            (new Socket("localhost", port)).close();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

}
