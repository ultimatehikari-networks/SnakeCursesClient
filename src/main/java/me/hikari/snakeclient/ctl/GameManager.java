package me.hikari.snakeclient.ctl;

import com.googlecode.lanterna.input.KeyStroke;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.hikari.snakeclient.data.*;
import me.hikari.snakeclient.data.config.GameConfig;
import me.hikari.snakeclient.tui.PluggableUI;
import me.hikari.snakes.SnakesProto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * TODO: mechanism of graceful game exit and reenter (line 30)
 */

@Log4j2
class GameManager implements InputDelegate, MessageDelegate {
    private static final int UI_REFRESH_RATE_MS = 10;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final List<ScheduledFuture<?>> generalHandlers = new ArrayList<>();
    private final List<ScheduledFuture<?>> gameHandlers = new LinkedList<>();

    private final GameConfig config;
    private Engine currentEngine = null;
    private final MetaEngine gameList;
    @Getter
    private final PluggableUI ui;
    @Getter
    private final StateSynchronizer synchronizer = new StateSynchronizer();
    private final CommWorker communicator;
    private final ListenWorker listener;


    private void startWorkers() {
        generalHandlers.add(
                scheduler.scheduleAtFixedRate(
                        new UIWorker(this),
                        0,
                        UI_REFRESH_RATE_MS,
                        TimeUnit.MILLISECONDS
                )
        );
        generalHandlers.add(
                scheduler.scheduleWithFixedDelay(
                        new InputWorker(this, config.getKeyConfig()),
                        0,
                        UI_REFRESH_RATE_MS,
                        TimeUnit.MILLISECONDS
                )
        );
        generalHandlers.add(
                scheduler.scheduleWithFixedDelay(
                        new ActualizeWorker(this),
                        0,
                        MetaEngine.GAME_KEEP_ALIVE_MS,
                        TimeUnit.MILLISECONDS
                )
        );
        generalHandlers.add(
                scheduler.scheduleWithFixedDelay(
                        new ResendWorker(communicator),
                        0,
                        ResendWorker.RESEND_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS
                )
        );
        generalHandlers.add(
                scheduler.schedule(
                        listener,
                        0,
                        TimeUnit.MILLISECONDS
                )
        );
    }

    public GameManager(PluggableUI ui, GameConfig config) throws IOException {
        this.ui = ui;
        this.config = config;
        this.gameList = new MetaEngine(
                new GameEntry(
                        new Player(config.getPlayerConfig()),
                        config.getEngineConfig()
                )
        );
        this.listener = new ListenWorker(this, config.getNetConfig());
        this.communicator = new CommWorker(
                this,
                config.getNetConfig(),
                config.getEngineConfig(),
                config.getPlayerConfig().getPort()
        );
        startWorkers();
    }

    /**
     * endless loop on listening inside communicator;
     * our boy also can send stuff, but via handles for other workers
     */
    public void start() {
        communicator.run();
    }

    @Override
    public void close() throws IOException {
        stopGame();
        generalHandlers.forEach(h -> h.cancel(true));
        listener.close();
        communicator.close();
        scheduler.shutdown();
        ui.close();
    }

    @Override
    public void startGame() throws IOException {
        var entry = gameList.getSelectedEntry();
        currentEngine = new Engine(
                entry,
                new Player(config.getPlayerConfig()),
                communicator
        );
        if (entry.getJoinAddress() != null) {
            // async wait for join
            // (communicator will call join() on ack)
            currentEngine.setSelfRole(SnakesProto.NodeRole.NORMAL);
            var joinMsg = SnakesProto.GameMessage.JoinMsg.newBuilder()
                    .setName(config.getPlayerConfig().getName())
                    .build();
            var msg = SnakesProto.GameMessage.newBuilder()
                    .setJoin(joinMsg)
                    .buildPartial();
            communicator.updateMaster(entry.getJoinAddress());
            communicator.sendMessageToMaster(msg);
        } else {
            // local game, local as only player is already master
            spinEngine(entry.getConfig().getStateDelayMs());
            spinAnnouncer();
            synchronizer.setRole(SnakesProto.NodeRole.MASTER);
        }

    }

    @Override
    public void stopGame() {
        // become viewer
        currentEngine.setSelfRole(SnakesProto.NodeRole.VIEWER);
        synchronizer.setRole(SnakesProto.NodeRole.VIEWER);
        gameHandlers.forEach(h -> log.info(h.cancel(true)));
        gameHandlers.clear();
        //log.info("cancelled");
    }

    private void spinAnnouncer() {
        generalHandlers.add(
                scheduler.scheduleAtFixedRate(
                        new AnnounceWorker(this, communicator),
                        0,
                        1,
                        TimeUnit.SECONDS)
        );
    }

    private void spinEngine(Integer stateDelay) {
        generalHandlers.add(
                scheduler.scheduleAtFixedRate(
                        new EngineWorker(currentEngine),
                        0,
                        stateDelay,
                        TimeUnit.MILLISECONDS)
        );
    }

    MetaEngineDTO getMetaDTO() {
        return gameList.getDTO();
    }

    EngineDTO getEngineDTO() {
        if (currentEngine != null) {
            return currentEngine.getDTO();
        }
        throw new IllegalStateException("No game is active");
    }

    @Override
    public void joinAsNormal(Integer receiverID) {
        currentEngine.selfBecome(receiverID);
        synchronizer.setRole(SnakesProto.NodeRole.NORMAL);
    }

    @Override
    public void noteNavDown() {
        gameList.navDown();
    }

    @Override
    public void noteNavUp() {
        gameList.navUp();
    }

    @Override
    public void noteSnakeMove(SnakesProto.Direction dir) {
        currentEngine.noteHostMove(dir);
    }

    void noteAnnouncement(SnakesProto.GameMessage.AnnouncementMsg msg, InetSocketAddress address) {
        gameList.addGame(new GameEntry(msg, address));
    }

    public void actualizeGameList() {
        gameList.actualizeGames();
    }

    @Override
    public Integer getLocalID() {
        return currentEngine.getSelfId();
    }

    @Override
    public Integer getPeerID(Peer peer) {
        return currentEngine.getPeerID(peer);
    }

    @Override
    public void sendSteer(SnakesProto.Direction direction) throws IOException {
        currentEngine.sendSteer(direction);
    }

    @Override
    public void handleStateMsg(SnakesProto.GameState state) {
        currentEngine.applyState(state);
    }

    @Override
    public void handleSteerMsg(SnakesProto.Direction direction, Peer peer) {
        currentEngine.notePeerMove(peer, direction);
    }

    @Override
    public Integer handleJoinMsg(Peer peer, String name) throws IllegalStateException {
        return currentEngine.joinPlayer(peer, name);
    }

    @Override
    public void handleErrorMsg(String errorMessage) {
        currentEngine.noteError(errorMessage);
    }

    @Override
    public void handleExitChange(Peer peer) {
        currentEngine.notePeerLeft(peer);
    }

    @Override
    public void handleReceiverRoleChange(SnakesProto.NodeRole role) {
        synchronizer.setRole(role);
        if (role == SnakesProto.NodeRole.MASTER || role == SnakesProto.NodeRole.DEPUTY) {
            // idling when deputy for faster start at master death
            spinEngine(config.getEngineConfig().getStateDelayMs());
            spinAnnouncer();
        }
        // for sync with rest of engine
        currentEngine.setSelfRole(role);
    }

    @Override
    public void masterFailed() throws IOException {
        switch (currentEngine.getSelfRole()) {
            case DEPUTY -> {
                currentEngine.setSelfRole(SnakesProto.NodeRole.MASTER);
                synchronizer.setRole(SnakesProto.NodeRole.MASTER);
                currentEngine.propagateNewMaster();
                // engine is already spinned
            }
            case NORMAL -> communicator.updateMasterToDeputy();
        }
    }

    @Override
    public void deputyFailed() throws IOException {
        var player = currentEngine.tryElectDeputy();
        if (player.isPresent()) {
            communicator.updateDeputy(player.get().formAddress());
        }
    }

    @Override
    public boolean isMaster() {
        return synchronizer.getRole().equals(SnakesProto.NodeRole.MASTER);
    }

    @Override
    public KeyStroke getInput() throws IOException {
        return ui.getInput();
    }

    public void actualizeNodeInfo() throws IOException {
        communicator.actualizeNodes();
    }
}
