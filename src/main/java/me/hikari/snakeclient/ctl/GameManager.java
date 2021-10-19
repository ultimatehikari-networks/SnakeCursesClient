package me.hikari.snakeclient.ctl;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import me.hikari.snakeclient.data.*;
import me.hikari.snakeclient.data.config.EngineConfig;
import me.hikari.snakeclient.tui.PluggableUI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameManager /*implements ManagerDTO*/{
    private static final int UI_REFRESH_RATE_MS = 10;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private ScheduledFuture<?> currentGame = null;
    private List<ScheduledFuture<?>> handlers = new ArrayList<>();

    private Engine currentEngine = null;
    private MetaEngine gameList = new MetaEngine();
    @Getter
    private final PluggableUI ui;
    @Getter
    private StateSynchronizer synchronizer = new StateSynchronizer();

    private void startWorkers(){
        handlers.add(scheduler.scheduleAtFixedRate(
                new UIWorker(this),
                0,
                UI_REFRESH_RATE_MS,
                TimeUnit.MILLISECONDS));
        handlers.add(scheduler.scheduleWithFixedDelay(
                new InputWorker(this),
                0,
                UI_REFRESH_RATE_MS,
                TimeUnit.MILLISECONDS));
    }

    public GameManager(PluggableUI ui){
        this.ui = ui;
        startWorkers();
        gameList.addGame(new Player("dummy", 1, "255.255.255.255"), new EngineConfig());
    }

    public void startGame(EngineConfig config) {
        currentEngine = new Engine(config);

        handlers.add(scheduler.scheduleAtFixedRate(
                new EngineWorker(currentEngine),
                0,
                config.getStateDelayMs(),
                TimeUnit.MILLISECONDS));
    }

    public void stopGame() {
        currentGame.cancel(true);
        currentEngine = null;
    }

    public void close() throws IOException {
        handlers.forEach(h -> h.cancel(true));
        scheduler.shutdown();
        ui.close();
    }

    public MetaEngineDTO getMetaDTO(){
        return gameList.getDTO();
    }

    public EngineDTO getEngineDTO(){
        if(currentEngine != null){
            return currentEngine.getDTO();
        }else{
            // TODO mb throw exception on access error?
            return null;
        }
    }
}
