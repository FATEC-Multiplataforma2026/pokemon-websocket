package io.github.mrspock182.service;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class StatsService {

    private static final String BASE_URL =
            "https://lnh1dhp1mj.execute-api.us-east-1.amazonaws.com/api-pokemon/auth/v1/stats/";

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static CompletableFuture<JSONObject> getStats(String userId) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + userId))
                .GET()
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> new JSONObject(r.body()));
    }

    public static CompletableFuture<Void> putStats(String userId, int level, int vitorias, int derrotas) {
        String body = new JSONObject()
                .put("level",    String.valueOf(level))
                .put("vitorias", String.valueOf(vitorias))
                .put("derrotas", String.valueOf(derrotas))
                .toString();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + userId))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> System.out.println("[STATS] PUT " + userId + " -> " + body + " | HTTP " + r.statusCode()));
    }

    public static CompletableFuture<Void> addVictory(String userId) {
        return getStats(userId).thenCompose(stats -> {
            int vitorias = safeInt(stats, "vitorias") + 1;
            int derrotas = safeInt(stats, "derrotas");
            int level    = (vitorias / 10) + 1;
            System.out.println("[STATS] " + userId + " victoria #" + vitorias + " -> level " + level);
            return putStats(userId, level, vitorias, derrotas);
        });
    }

    public static CompletableFuture<Void> addDefeat(String userId) {
        return getStats(userId).thenCompose(stats -> {
            int vitorias = safeInt(stats, "vitorias");
            int derrotas = safeInt(stats, "derrotas") + 1;
            int level    = (vitorias / 10) + 1;
            System.out.println("[STATS] " + userId + " derrota #" + derrotas);
            return putStats(userId, level, vitorias, derrotas);
        });
    }

    private static int safeInt(JSONObject obj, String key) {
        try { return Integer.parseInt(obj.optString(key, "0")); }
        catch (NumberFormatException e) { return 0; }
    }
}
