package com.example.chestpicker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Забирает выбранные предметы из открытого контейнера (шифт-клик по слоту).
 * Работает по тикам, чтобы скорость можно было настраивать.
 */
public final class AutoLooter {
    // Уровни скорости: сколько перемещений за один "пакет" и через сколько тиков следующий.
    private static final int[] MOVES = {1, 1, 1, 3, Integer.MAX_VALUE};
    private static final int[] DELAY = {4, 2, 1, 1, 1};
    public static final int SPEED_LEVELS = MOVES.length;

    private static AbstractContainerScreen<?> screen;
    private static Screen lastAutoScreen;
    private static boolean active;
    private static int warmup;
    private static int cooldown;
    private static int idle;
    private static int moved;
    private static final Set<Integer> tried = new HashSet<>();

    private AutoLooter() {
    }

    /** Поддерживаемые контейнеры: сундуки, бочки, эндер-сундуки (ChestMenu) и шалкеры. */
    public static boolean isSupported(Screen s) {
        return s instanceof AbstractContainerScreen<?> c
                && (c.getMenu() instanceof ChestMenu || c.getMenu() instanceof ShulkerBoxMenu);
    }

    /** Вызывается при открытии/инициализации экрана: запускает авто-сбор, если он включён. */
    public static void onScreenOpened(Screen s) {
        if (!ChestPickerConfig.get().autoOnOpen || !isSupported(s)) {
            return;
        }
        // Экран может инициализироваться повторно (например, при изменении размера окна).
        if (s == lastAutoScreen) {
            return;
        }
        lastAutoScreen = s;
        start((AbstractContainerScreen<?>) s, false);
    }

    public static void start(AbstractContainerScreen<?> s, boolean manual) {
        if (ChestPickerConfig.get().items.isEmpty()) {
            if (manual) {
                message(Component.translatable("chestpicker.nothing_selected"));
            }
            return;
        }
        screen = s;
        active = true;
        // Небольшая пауза: содержимое сундука приходит с сервера чуть позже открытия окна.
        warmup = 2;
        cooldown = 0;
        idle = 0;
        moved = 0;
        tried.clear();
    }

    public static void tick(Minecraft mc) {
        if (!active) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.screen != screen
                || player.containerMenu != screen.getMenu()) {
            finish();
            return;
        }
        if (warmup > 0) {
            warmup--;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        ChestPickerConfig cfg = ChestPickerConfig.get();
        int speed = Math.max(0, Math.min(SPEED_LEVELS - 1, cfg.speed));
        int budget = MOVES[speed];
        boolean didMove = false;
        AbstractContainerMenu menu = screen.getMenu();

        for (Slot slot : menu.slots) {
            if (budget <= 0) {
                break;
            }
            // Слоты инвентаря игрока пропускаем, нас интересует только сам контейнер.
            if (slot.container instanceof Inventory) {
                continue;
            }
            if (tried.contains(slot.index)) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!cfg.items.contains(id)) {
                continue;
            }
            // Каждый слот пробуем один раз, чтобы не зациклиться, если инвентарь полон.
            tried.add(slot.index);
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, player);
            moved++;
            budget--;
            didMove = true;
        }

        if (didMove) {
            idle = 0;
            cooldown = DELAY[speed] - 1;
        } else if (++idle >= 5) {
            finish();
        }
    }

    private static void finish() {
        if (active && moved > 0) {
            message(Component.translatable("chestpicker.taken", moved));
        }
        active = false;
        screen = null;
        moved = 0;
        tried.clear();
    }

    private static void message(Component text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(text, true);
        }
    }
}
