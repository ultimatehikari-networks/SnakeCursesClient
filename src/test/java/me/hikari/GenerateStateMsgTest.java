package me.hikari;

import com.google.protobuf.InvalidProtocolBufferException;
import me.hikari.snakes.SnakesProto.GameMessage.StateMsg;
import me.hikari.snakes.SnakesProto.GameState.Snake;
import me.hikari.snakes.SnakesProto.GameState.Snake.SnakeState;
import org.junit.Assert;
import org.junit.Test;

import static me.hikari.snakes.SnakesProto.*;

public class GenerateStateMsgTest {
    /** Генерирует пример сообщения с состоянием, соответствующим картинке example1.png */
    @Test
    public void testGenerateStateMsg() throws InvalidProtocolBufferException {
        GameConfig config = GameConfig.newBuilder()
                .setWidth(10)
                .setHeight(10)
                // Все остальные параметры имеют значения по умолчанию
                .build();
        Snake snake = Snake.newBuilder()
                .setPlayerId(1)
                .setHeadDirection(Direction.LEFT)
                .setState(SnakeState.ALIVE)
                .addPoints(coord(5, 1)) // голова
                .addPoints(coord(3, 0))
                .addPoints(coord(0, 2))
                .addPoints(coord(-4, 0))
                .build();
        // Единственный игрок в игре, он же MASTER
        GamePlayer playerBob = GamePlayer.newBuilder()
                .setId(1)
                .setRole(NodeRole.MASTER)
                .setIpAddress("") // MASTER не отправляет собственный IP
                .setPort(20101)
                .setName("Bob")
                .setScore(8)
                .build();
        GamePlayers players = GamePlayers.newBuilder()
                .addPlayers(playerBob)
                .build();
        GameState state = GameState.newBuilder()
                .setStateOrder(193)
                .addSnakes(snake)
                .setPlayers(players)
                .setConfig(config)
                .addFoods(coord(7, 6))
                .addFoods(coord(8, 7))
                .build();
        StateMsg stateMsg = StateMsg.newBuilder()
                .setState(state)
                .build();
        GameMessage gameMessage = GameMessage.newBuilder()
                .setMsgSeq(15643)
                .setState(stateMsg)
                .build();

        byte[] bytesToSendViaDatagramPacket = gameMessage.toByteArray();
        Assert.assertEquals(81, bytesToSendViaDatagramPacket.length);
        var msg = GameMessage.parseFrom(bytesToSendViaDatagramPacket);
    }

    private GameState.Coord coord(int x, int y) {
        return GameState.Coord.newBuilder().setX(x).setY(y).build();
    }
}
