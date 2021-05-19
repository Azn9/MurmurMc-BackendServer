package dev.azn9.murmurServer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebSocketService extends WebSocketServer {

    private final List<WebSocket> rawSockets = new ArrayList<>();
    private final Map<String, WebSocket> socketMap = new HashMap<>();
    private final Map<WebSocket, String> invertedSocketMap = new HashMap<>();
    private final Map<Integer, String> portMap = new HashMap<>();
    private final Map<String, List<String>> onlineUsersMumble = new HashMap<>();
    private final Map<String, List<String>> onlineUsersMc = new HashMap<>();

    public WebSocketService() {
        super(new InetSocketAddress("0.0.0.0", 8888));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        this.rawSockets.add(conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        this.rawSockets.remove(conn);

        if (!this.invertedSocketMap.containsKey(conn)) {
            return;
        }

        String serverId = this.invertedSocketMap.remove(conn);
        this.socketMap.remove(serverId);
        this.onlineUsersMumble.remove(serverId);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Received message: " + message);

        if (message.startsWith("reg|")) {
            int port = Integer.parseInt(message.split("\\|")[1]);

            if (this.portMap.containsKey(port)) {
                String serverId = this.portMap.remove(port);

                this.socketMap.put(serverId, conn);
                this.invertedSocketMap.put(conn, serverId);
            }
        } else if (message.startsWith("join|")) {
            if (!this.invertedSocketMap.containsKey(conn)) {
                return;
            }

            String username = message.split("\\|")[1];
            String serverId = this.invertedSocketMap.get(conn);

            if (serverId == null && username != null) {
                return;
            }

            this.onJoinMumble(serverId, username);
        } else if (message.startsWith("leave|")) {
            if (!this.invertedSocketMap.containsKey(conn)) {
                return;
            }

            String username = message.split("\\|")[1];
            String serverId = this.invertedSocketMap.get(conn);

            if (serverId == null && username != null) {
                return;
            }

            this.onLeaveMumble(serverId, username);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("server started successfully");
    }

    private void sendCommand(String serverId, String command) {
        if (this.socketMap.containsKey(serverId)) {
            this.socketMap.get(serverId).send(command);
        }
    }

    public void sendMuteCommand(String serverId, String username) {
        if (this.isOnline(serverId, username)) {
            this.sendCommand(serverId, "mute|" + username);
        }
    }

    public void sendUnmuteCommand(String serverId, String username) {
        if (this.isOnline(serverId, username)) {
            this.sendCommand(serverId, "unmute|" + username);
        }
    }

    public void sendMoveCommand(String serverId, String username, String channel) {
        if (this.isOnline(serverId, username)) {
            this.sendCommand(serverId, "move|" + username + "|" + channel);
        }
    }

    public void sendMassMoveCommand(String serverId, String channel) {
        this.sendCommand(serverId, "massmove|" + channel);
    }

    public void sendPositionUpdate(String serverId, String username, String pos) {
        if (this.isOnline(serverId, username)) {
            this.sendCommand(serverId, "updatepos|" + username + "|" + pos);
        }
    }

    public void sendStatusUpdate(String serverId, String username, String status) {
        if (this.isOnline(serverId, username)) {
            this.sendCommand(serverId, "status|" + username + "|" + status);
        }
    }

    public boolean isOnline(String serverId, String username) {
        return this.onlineUsersMumble.containsKey(serverId) && this.onlineUsersMumble.get(serverId).contains(username);
    }

    public List<String> getOnline(String serverId) {
        List<String> online = this.onlineUsersMumble.get(serverId);
        if (online == null) {
            online = new ArrayList<>();
        }

        return online;
    }

    public void broadcastCommand(String command) {
        for (WebSocket socket : this.rawSockets) {
            socket.send(command);
        }
    }

    public void onJoinMc(String serverId, String username, boolean alive) {
        if (!this.isOnline(serverId, username)) {
            return;
        }

        this.sendCommand(serverId, "join|" + username + "|" + alive);

        this.onlineUsersMc.computeIfAbsent(serverId, (s) -> new ArrayList<>()).add(username);
    }

    public void onLeaveMc(String serverId, String username) {
        if (!this.isOnline(serverId, username)) {
            return;
        }

        this.onlineUsersMc.computeIfPresent(serverId, (s, strings) -> {
            strings.remove(username);
            return strings;
        });

        this.sendCommand(serverId, "leave|" + username);
    }

    private void onJoinMumble(String serverId, String username) {
        this.onlineUsersMc.computeIfAbsent(serverId, (s) -> new ArrayList<>()).add(username);

        if (!this.onlineUsersMc.containsKey(serverId) || !this.onlineUsersMc.get(serverId).contains(username)) {
            this.sendMuteCommand(serverId, username);
        }
    }

    private void onLeaveMumble(String serverId, String username) {
        this.onlineUsersMumble.computeIfPresent(serverId, (s, strings) -> {
            strings.remove(username);
            return strings;
        });
    }

}
