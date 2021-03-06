package me.hikari.snakeclient.tui;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import me.hikari.snakeclient.data.EngineDTO;
import me.hikari.snakeclient.data.MetaEngineDTO;
import me.hikari.snakeclient.data.config.KeyConfig;

import java.io.IOException;

public class Tui implements PluggableUI {
    private Screen screen;

    private Object currentScreen = null;
    private final GameScreen gameScreen;
    private final MainScreen mainScreen;

    /**
     * TODO
     * Pass configs for:
     *  both grids
     *  DTO2Image
     */

    public Tui(KeyConfig config) throws IOException {
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        this.screen = new TerminalScreen(terminal);
        var footer = new FooterFactory(config);
        this.gameScreen = new GameScreen(screen, new LeftCorneredView(), footer.getGameFooter());
        this.mainScreen = new MainScreen(screen, footer.getMainFooter());
        screen.startScreen();
    }

    @Override
    public void close() throws IOException {
        screen.stopScreen();
    }

    @Override
    public void showMainScreen(MetaEngineDTO engine) throws IOException {
        if(currentScreen != mainScreen){
            screen.clear();
            currentScreen = mainScreen;
        }
        mainScreen.show(engine);
    }

    @Override
    public void showGameScreen(EngineDTO engine) throws IOException {
        if(currentScreen != gameScreen){
            screen.clear();
            currentScreen = gameScreen;
        }
        gameScreen.show(engine);
    }

    @Override
    public KeyStroke getInput() throws IOException {
        return screen.readInput();
    }

}
