package io.github.mrspock182.endpoint;

import io.github.mrspock182.entity.BattleSession;
import io.github.mrspock182.entity.PendingBattle;
import io.github.mrspock182.service.StatsService;
import io.github.mrspock182.service.TeamService;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/battle")
public class BattleEndpoint {

    private static final ConcurrentHashMap<String, Session>       connectedUsers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PendingBattle> pendingBattles = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, BattleSession> activeBattles  = new ConcurrentHashMap<>();

    // Delay before revealing the chosen attributes (suspense effect)
    private static final long REVEAL_DELAY_MS = 3_000;
    // Gap between one round ending and the next ROUND_SELECTING being sent
    private static final long BETWEEN_ROUNDS_MS = 2_000;

    @OnOpen
    public void onOpen(Session session) {
        String username = extractUsername(session);
        if (username == null || username.isBlank()) {
            closeWithError(session, "Missing query param: username");
            return;
        }
        session.getUserProperties().put("username", username);
        connectedUsers.put(username, session);
        send(session, "CONNECTED:" + username);
        System.out.println("[CONNECTED] " + username);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String sender = (String) session.getUserProperties().get("username");
        if (sender == null) return;

        if (message.startsWith("CHALLENGE:")) {
            handleChallenge(sender, message.substring("CHALLENGE:".length()).trim(), session);
        } else if (message.startsWith("ACCEPT:")) {
            handleAccept(sender, message.substring("ACCEPT:".length()).trim());
        } else if (message.startsWith("DECLINE:")) {
            handleDecline(sender, message.substring("DECLINE:".length()).trim());
        } else if (message.startsWith("USER_ID:")) {
            handleUserId(sender, message.substring("USER_ID:".length()).trim());
        }
    }

    @OnClose
    public void onClose(Session session) {
        String username = (String) session.getUserProperties().get("username");
        if (username != null) {
            connectedUsers.remove(username);
            pendingBattles.remove(username);

            BattleSession battle = activeBattles.remove(username);
            if (battle != null) {
                String opponent = username.equals(battle.player1) ? battle.player2 : battle.player1;
                activeBattles.remove(opponent);
                send(connectedUsers.get(opponent), "OPPONENT_DISCONNECTED:" + username);
            }
            System.out.println("[DISCONNECTED] " + username);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        String username = (String) session.getUserProperties().get("username");
        System.err.println("[ERROR] " + username + ": " + throwable.getMessage());
    }

    // --- handlers ---

    private void handleChallenge(String challenger, String targetUsername, Session challengerSession) {
        Session target = connectedUsers.get(targetUsername);
        if (target == null || !target.isOpen()) {
            send(challengerSession, "ERROR:User not found or offline: " + targetUsername);
            return;
        }
        send(target, "BATTLE_REQUEST:" + challenger);
        send(challengerSession, "CHALLENGE_SENT:" + targetUsername);
        System.out.println("[CHALLENGE] " + challenger + " -> " + targetUsername);
    }

    private void handleAccept(String accepter, String challengerUsername) {
        Session challenger = connectedUsers.get(challengerUsername);
        if (challenger != null && challenger.isOpen()) {
            PendingBattle battle = new PendingBattle(challengerUsername, accepter);
            pendingBattles.put(challengerUsername, battle);
            pendingBattles.put(accepter, battle);

            send(challenger, "BATTLE_ACCEPTED:" + accepter);
            send(connectedUsers.get(accepter), "BATTLE_ACCEPTED:" + challengerUsername);
            System.out.println("[ACCEPTED] " + accepter + " accepted " + challengerUsername);
        }
    }

    private void handleDecline(String decliner, String challengerUsername) {
        Session challenger = connectedUsers.get(challengerUsername);
        if (challenger != null && challenger.isOpen()) {
            send(challenger, "BATTLE_DECLINED:" + decliner);
        }
        System.out.println("[DECLINED] " + decliner + " declined " + challengerUsername);
    }

    private void handleUserId(String username, String userId) {
        PendingBattle battle = pendingBattles.get(username);
        if (battle == null) {
            send(connectedUsers.get(username), "ERROR:No pending battle found");
            return;
        }

        boolean bothReady = battle.setUserId(username, userId);
        if (!bothReady) {
            System.out.println("[USER_ID] " + username + " registered, waiting for opponent...");
            return;
        }

        pendingBattles.remove(battle.challenger);
        pendingBattles.remove(battle.opponent);
        fetchTeamsAndStart(battle);
    }

    private void fetchTeamsAndStart(PendingBattle battle) {
        System.out.println("[FETCHING] Teams for " + battle.challenger + " and " + battle.opponent);

        CompletableFuture<org.json.JSONArray> challengerTeam = TeamService.fetchTeam(battle.getChallengerUserId());
        CompletableFuture<org.json.JSONArray> opponentTeam   = TeamService.fetchTeam(battle.getOpponentUserId());

        CompletableFuture.allOf(challengerTeam, opponentTeam).whenComplete((ignored, error) -> {
            if (error != null) {
                notifyBothError(battle.challenger, battle.opponent, "Failed to load teams: " + error.getMessage());
                return;
            }
            try {
                org.json.JSONArray team1 = challengerTeam.get();
                org.json.JSONArray team2 = opponentTeam.get();

                JSONObject player1 = new JSONObject()
                        .put("username", battle.challenger)
                        .put("team", team1);
                JSONObject player2 = new JSONObject()
                        .put("username", battle.opponent)
                        .put("team", team2);

                String payload = "BATTLE_START:" + new JSONObject()
                        .put("player1", player1)
                        .put("player2", player2);

                send(connectedUsers.get(battle.challenger), payload);
                send(connectedUsers.get(battle.opponent),   payload);
                System.out.println("[BATTLE_START] " + battle.challenger + " vs " + battle.opponent);

                BattleSession session = new BattleSession(
                        battle.challenger, battle.opponent,
                        battle.getChallengerUserId(), battle.getOpponentUserId(),
                        team1, team2
                );
                activeBattles.put(battle.challenger, session);
                activeBattles.put(battle.opponent,   session);

                runBattleRounds(session);

            } catch (Exception e) {
                notifyBothError(battle.challenger, battle.opponent, "Failed to build battle payload: " + e.getMessage());
            }
        });
    }

    private void runBattleRounds(BattleSession session) {
        CompletableFuture.runAsync(() -> {
            try {
                while (!session.isOver()) {
                    // 1) Avisa que a seleção do atributo está acontecendo
                    int nextRound = session.currentRound() + 1;
                    String selecting = "ROUND_SELECTING:" + new JSONObject().put("round", nextRound);
                    send(connectedUsers.get(session.player1), selecting);
                    send(connectedUsers.get(session.player2), selecting);

                    // 2) Aguarda 3s para revelar — dá a sensação de suspense
                    Thread.sleep(REVEAL_DELAY_MS);

                    // 3) Revela o atributo sorteado e o resultado
                    JSONObject roundResult = session.playRound();
                    String payload = "ROUND_RESULT:" + roundResult;
                    send(connectedUsers.get(session.player1), payload);
                    send(connectedUsers.get(session.player2), payload);

                    System.out.println("[ROUND " + roundResult.getInt("round") + "] "
                            + session.player1 + "(" + session.getScore1() + ") x "
                            + "(" + session.getScore2() + ")" + session.player2);

                    // 4) Pausa antes do próximo round (para o frontend exibir o resultado)
                    if (!session.isOver()) Thread.sleep(BETWEEN_ROUNDS_MS);
                }

                finalizeBattle(session);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[BATTLE] Thread interrupted for " + session.player1 + " vs " + session.player2);
            }
        });
    }

    private static final java.util.Random BATTLE_RANDOM = new java.util.Random();

    private void finalizeBattle(BattleSession session) {
        activeBattles.remove(session.player1);
        activeBattles.remove(session.player2);

        String winner    = session.getWinner();
        String loser     = session.getLoser();
        int    pokemonId = BATTLE_RANDOM.nextInt(151) + 1; // 1–151

        JSONObject endPayload = new JSONObject()
                .put("winner",     winner)
                .put("loser",      loser)
                .put("pokemon_id", pokemonId)
                .put("scores", new JSONObject()
                        .put(session.player1, session.getScore1())
                        .put(session.player2, session.getScore2()));

        String message = "BATTLE_END:" + endPayload;
        send(connectedUsers.get(session.player1), message);
        send(connectedUsers.get(session.player2), message);

        System.out.println("[BATTLE_END] Winner: " + winner + " | Loser: " + loser + " | Pokemon: " + pokemonId);

        CompletableFuture.allOf(
                StatsService.addVictory(session.getWinnerUserId()),
                StatsService.addDefeat(session.getLoserUserId()),
                TeamService.captureRandomPokemon(session.getWinnerUserId(), pokemonId)
        ).whenComplete((ignored, err) -> {
            if (err != null) System.err.println("[STATS] Failed to finalize battle: " + err.getMessage());
            else              System.out.println("[STATS] Stats + capture updated for " + winner);
        });
    }

    private void notifyBothError(String player1, String player2, String message) {
        send(connectedUsers.get(player1), "ERROR:" + message);
        send(connectedUsers.get(player2), "ERROR:" + message);
        System.err.println("[ERROR] " + message);
    }

    // --- helpers ---

    private String extractUsername(Session session) {
        Map<String, List<String>> params = session.getRequestParameterMap();
        List<String> values = params.get("username");
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private void send(Session session, String message) {
        if (session != null && session.isOpen()) {
            session.getAsyncRemote().sendText(message);
        }
    }

    private void closeWithError(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (Exception ignored) {}
    }

    static {
        printFrontendIntegrationGuide();
    }

    private static void printFrontendIntegrationGuide() {
        System.out.println("""

                ╔══════════════════════════════════════════════════════════════════╗
                ║           GUIA DE INTEGRAÇÃO WEBSOCKET — FRONTEND               ║
                ╚══════════════════════════════════════════════════════════════════╝

                URL de conexão:
                  ws://<host>:8080/ws/battle?username=<nome-do-usuario>

                ── FLUXO COMPLETO ──────────────────────────────────────────────────

                1. CONECTAR
                   Cliente abre WebSocket com ?username=<nome>
                   Recebe → "CONNECTED:<nome>"

                2. DESAFIAR
                   Envia  → "CHALLENGE:<username-do-oponente>"
                   Recebe → "CHALLENGE_SENT:<oponente>"          (confirmação para o desafiante)
                   Oponente recebe → "BATTLE_REQUEST:<desafiante>"

                3. ACEITAR / RECUSAR (lado do oponente)
                   Aceitar:  envia → "ACCEPT:<username-do-desafiante>"
                   Recusar:  envia → "DECLINE:<username-do-desafiante>"
                   Se recusar, desafiante recebe → "BATTLE_DECLINED:<oponente>"

                4. ENVIAR USER_ID (AMBOS os jogadores, após ACCEPT)
                   Envia  → "USER_ID:<id-do-usuario-na-api>"
                   (aguarda o oponente também enviar — servidor busca os times)

                5. BATTLE_START (servidor envia para ambos)
                   Recebe → "BATTLE_START:<JSON>"
                   JSON:
                   {
                     "player1": { "username": "ash",   "team": [ ...pokemons... ] },
                     "player2": { "username": "misty", "team": [ ...pokemons... ] }
                   }
                   → Exibir os times e aguardar os rounds (automáticos, ~3s de intervalo)

                6. ROUND_SELECTING (servidor envia antes de revelar o atributo)
                   Recebe → "ROUND_SELECTING:<JSON>"
                   JSON: { "round": 2 }
                   → Exibir animação/spinner de "selecionando atributo..." por 3 segundos

                6b. ROUND_RESULT (servidor envia 3s depois do ROUND_SELECTING)
                   Recebe → "ROUND_RESULT:<JSON>"
                   JSON:
                   {
                     "round": 1,
                     "draw": false,
                     "player1": { "username": "ash",   "pokemon": "pikachu", "attribute": "speed",   "value": 90, "won": true  },
                     "player2": { "username": "misty", "pokemon": "starmie", "attribute": "defense", "value": 85, "won": false },
                     "scores":  { "ash": 1, "misty": 0 }
                   }
                   → Revelar o atributo sorteado e o resultado do round
                   → Jogo vai até 3 vitórias (melhor de 5 rounds no mínimo, ilimitado se houver empates)

                7. BATTLE_END (servidor envia ao fim, para ambos)
                   Recebe → "BATTLE_END:<JSON>"
                   JSON:
                   {
                     "winner": "ash",
                     "loser":  "misty",
                     "scores": { "ash": 3, "misty": 1 }
                   }
                   → Exibir tela de vitória/derrota
                   → Stats atualizados automaticamente pelo servidor (vitórias, derrotas, level)

                ── MENSAGENS DE ERRO ───────────────────────────────────────────────
                   "ERROR:<mensagem>"

                ── DESCONEXÃO DURANTE BATALHA ──────────────────────────────────────
                   Oponente recebe → "OPPONENT_DISCONNECTED:<username>"

                ── REGRAS DE NEGÓCIO (servidor) ────────────────────────────────────
                   • Atributos disponíveis: hp, attack, defense, special_attack,
                     special_defense, speed
                   • Cada jogador recebe um atributo aleatório e independente por round
                   • Maior valor vence o round; empate não conta ponto
                   • Primeiro a 3 vitórias ganha a batalha
                   • A cada 10 vitórias o campeão sobe de nível (10→lv2, 20→lv3 …)

                ════════════════════════════════════════════════════════════════════
                """);
    }
}
