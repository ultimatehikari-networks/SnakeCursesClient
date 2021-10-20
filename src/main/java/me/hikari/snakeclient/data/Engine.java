package me.hikari.snakeclient.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.hikari.snakeclient.data.config.EngineConfig;
import me.hikari.snakeclient.data.config.UIConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Engine {
    private Integer stateOrder;

    private boolean isLatest = false;
    private EngineDTO dto = null;
    private final Object mapMonitor = new Object();

    private EngineConfig config;
    @Getter
    private final Map<Player, Snake> snakeMap = new HashMap<>();
    private final Map<Player, Direction> moves = new HashMap<>();
    private final List<Coord> foods = new ArrayList<>();
    private FieldRepresentation field;

    public EngineDTO getDTO() {
        synchronized (mapMonitor) {
            if (!isLatest) {
                var map = new HashMap<Player, Snake>();
                snakeMap.forEach((p, s) -> map.put(p, new Snake(s)));
                dto = new EngineDTO(map, config);
            }
            return dto;
        }
    }

    @AllArgsConstructor
    private class MoveResult {
        @Getter
        private final Coord coord;
        @Getter
        private final Snake snake;
    }

    public Engine(EngineConfig config) {
        this.config = config;
        field = new FieldRepresentation(new Coord(config.getWidth(), config.getHeight()));
    }

    public void addPlayer(Player player) {
        Snake snake = spawnSnake();
        snakeMap.put(player, snake);
    }

    private Snake spawnSnake() {
        //TODO upgrade algo
        return new Snake(Direction.RIGHT, new Coord(5, 5), new Coord(-1, 0));
    }

    public void notePlayerMove(Player player, Direction move) {
        moves.put(player, move);
    }

    public UIConfig getUIConfig() {
        return config;
    }

    public void applyMoves() {
        moves.forEach((Player p, Direction d) -> snakeMap.get(p).turnHead(d));
    }

    public void moveSnakes() {
        synchronized (mapMonitor) {
            ArrayList<MoveResult> list = new ArrayList<>();
            snakeMap.forEach((Player p, Snake s) -> {
                list.add(new MoveResult(s.moveHead(), s));
            });
            //TODO place snakes on field
            list.forEach((MoveResult m) -> {
                if (field.isCellSnakeCollided(m.getCoord())) {
                    // TODO kill all stuff
                    return;
                }
                if (!field.isCellFoodCollided(m.getCoord())) {
                    m.getSnake().dropTail();
                }
            });
        }
    }


}
