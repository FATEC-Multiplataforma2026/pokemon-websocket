package io.github.mrspock182;

import io.github.mrspock182.endpoint.BattleEndpoint;
import org.glassfish.tyrus.server.Server;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        Server server = new Server("0.0.0.0", port, "/ws", null, BattleEndpoint.class);
        server.start();

        System.out.println("Pokemon WebSocket server running on ws://0.0.0.0:" + port + "/ws/battle");
        System.out.println("Connect with: ws://<host>:" + port + "/ws/battle?username=<your-username>");
        System.out.println("Press ENTER to stop.");

        System.in.read();
        server.stop();
    }
}
