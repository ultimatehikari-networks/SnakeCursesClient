package me.hikari.snakeclient.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.hikari.snakeclient.data.config.EngineConfig;

import java.util.Set;

@Getter
@AllArgsConstructor
public class MetaEngineDTO {
    private final Set<EngineConfig> configs;
}