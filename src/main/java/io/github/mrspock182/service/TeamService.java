package io.github.mrspock182.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class TeamService {

    private static final String API_URL =
            "https://lnh1dhp1mj.execute-api.us-east-1.amazonaws.com/api-pokemon/pokemon/v1/team?user-id=";

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String CAPTURED_URL =
            "https://lnh1dhp1mj.execute-api.us-east-1.amazonaws.com/api-pokemon/pokemon/v1/captured";

    public static CompletableFuture<Void> captureRandomPokemon(String userId, int pokemonId) {
        String url = CAPTURED_URL + "?user-id=" + userId + "&pokemon-id=" + pokemonId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> System.out.println("[CAPTURE] user=" + userId
                        + " pokemon=" + pokemonId + " HTTP " + r.statusCode() + " body=" + r.body()));
    }

    public static CompletableFuture<JSONArray> fetchTeam(String userId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + userId))
                .GET()
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JSONObject body = new JSONObject(response.body());
                    return body.getJSONArray("team");
                });
    }
}
