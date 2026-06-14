package io.github.mrspock182;

import io.github.mrspock182.endpoint.BattleEndpoint;
import org.glassfish.tyrus.server.Server;

import java.util.concurrent.CountDownLatch;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        Server server = new Server("0.0.0.0", port, "/ws", null, BattleEndpoint.class);
        server.start();

        System.out.println("Pokemon WebSocket server running on ws://0.0.0.0:" + port + "/ws/battle");

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            server.stop();
            latch.countDown();
        }));

        latch.await();
    }
}
