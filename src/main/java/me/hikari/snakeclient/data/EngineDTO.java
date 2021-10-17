package me.hikari.snakeclient.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.hikari.snakeclient.data.config.UIConfig;

import java.util.Map;

@Getter
@AllArgsConstructor
public class EngineDTO {
    private final Map<Player, Snake> snakeMap;
    private final UIConfig uiConfig;
}