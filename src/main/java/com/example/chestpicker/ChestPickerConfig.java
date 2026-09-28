package com.example.chestpicker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Настройки мода. Хранятся в config/chestpicker.json.
 */
public final class ChestPickerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("chestpicker");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ChestPickerConfig instance = new ChestPickerConfig();

    /** ID предметов, которые нужно забирать, например "minecraft:diamond". */
    public Set<String> items = new LinkedHashSet<>();
    /** Автоматически забирать при открытии сундука. */
    public boolean autoOnOpen = true;
    /** Индекс уровня скорости, см. AutoLooter. */
    public int speed = 2;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("chestpicker.json");
    }

    public static ChestPickerConfig get() {
        return instance;
    }

    public static void load() {
        try (Reader reader = Files.newBufferedReader(path())) {
            ChestPickerConfig loaded = GSON.fromJson(reader, ChestPickerConfig.class);
            if (loaded != null) {
                if (loaded.items == null) {
                    loaded.items = new LinkedHashSet<>();
                }
                loaded.speed = Math.max(0, Math.min(AutoLooter.SPEED_LEVELS - 1, loaded.speed));
                instance = loaded;
            }
        } catch (NoSuchFileException e) {
            // Первого запуска конфига ещё нет, используем значения по умолчанию.
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Не удалось прочитать конфиг, используются значения по умолчанию", e);
        }
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(path())) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            LOGGER.warn("Не удалось сохранить конфиг", e);
        }
    }
}
