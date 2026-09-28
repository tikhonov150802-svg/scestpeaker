package com.example.chestpicker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ChestMenu;
import org.lwjgl.glfw.GLFW;

public class ChestPickerClient implements ClientModInitializer {
    public static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        ChestPickerConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("chestpicker", "main"));
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestpicker.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openMenuKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new ItemPickerScreen(null));
                }
            }
            AutoLooter.tick(mc);
        });

        ScreenEvents.AFTER_INIT.register(ChestPickerClient::onScreenInit);
    }

    private static void onScreenInit(Minecraft mc, Screen screen, int scaledWidth, int scaledHeight) {
        if (!AutoLooter.isSupported(screen)) {
            return;
        }
        AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>) screen;

        // Кнопки над окном контейнера. Ширина GUI сундука/шалкера всегда 176,
        // высота: 114 + 18 * число рядов.
        int rows = containerScreen.getMenu() instanceof ChestMenu chest ? chest.getRowCount() : 3;
        int imageHeight = 114 + rows * 18;
        int left = (scaledWidth - 176) / 2;
        int top = (scaledHeight - imageHeight) / 2;
        int y = Math.max(2, top - 22);

        Button take = Button.builder(Component.translatable("chestpicker.take"),
                        b -> AutoLooter.start(containerScreen, true))
                .bounds(left, y, 88, 20).build();

        Button auto = Button.builder(autoLabel(), b -> {
                    ChestPickerConfig cfg = ChestPickerConfig.get();
                    cfg.autoOnOpen = !cfg.autoOnOpen;
                    ChestPickerConfig.save();
                    b.setMessage(autoLabel());
                })
                .bounds(left + 92, y, 84, 20).build();

        Screens.getButtons(screen).add(take);
        Screens.getButtons(screen).add(auto);

        AutoLooter.onScreenOpened(screen);
    }

    private static Component autoLabel() {
        return Component.translatable("chestpicker.auto",
                Component.translatable(ChestPickerConfig.get().autoOnOpen ? "chestpicker.on" : "chestpicker.off"));
    }
}
