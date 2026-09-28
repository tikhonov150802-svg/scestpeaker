package com.example.chestpicker;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Меню выбора предметов: поиск, сетка всех предметов, клик добавляет/убирает предмет из списка.
 */
public class ItemPickerScreen extends Screen {
    private static final int COLS = 12;
    private static final int CELL = 20;

    private record Entry(ItemStack stack, String id, String nameLower) {
    }

    private final Screen parent;
    private final List<Entry> all = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();

    private EditBox search;
    private Button onlySelectedButton;
    private Button autoButton;
    private Button speedButton;

    private boolean onlySelected;
    private int scroll; // индекс верхней видимой строки
    private int gridLeft;
    private int gridTop;
    private int visibleRows;

    public ItemPickerScreen(Screen parent) {
        super(Component.translatable("chestpicker.screen.title"));
        this.parent = parent;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = item.getDefaultInstance();
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            all.add(new Entry(stack, id, name));
        }
    }

    @Override
    protected void init() {
        ChestPickerConfig cfg = ChestPickerConfig.get();

        gridLeft = (this.width - COLS * CELL) / 2;
        gridTop = 56;
        visibleRows = Math.max(1, (this.height - 34 - gridTop) / CELL);

        String previous = search == null ? "" : search.getValue();
        search = new EditBox(this.font, this.width / 2 - 120, 24, 180, 18, Component.translatable("chestpicker.search"));
        search.setHint(Component.translatable("chestpicker.search"));
        search.setValue(previous);
        search.setResponder(text -> {
            scroll = 0;
            refilter();
        });
        this.addRenderableWidget(search);
        this.setInitialFocus(search);

        this.addRenderableWidget(Button.builder(Component.translatable("chestpicker.clear"), b -> {
            cfg.items.clear();
            refilter();
        }).bounds(this.width / 2 + 64, 23, 56, 20).build());

        int buttonWidth = 112;
        int gap = 4;
        int total = 4 * buttonWidth + 3 * gap;
        int x = (this.width - total) / 2;
        int y = this.height - 26;

        onlySelectedButton = Button.builder(onlySelectedLabel(), b -> {
            onlySelected = !onlySelected;
            scroll = 0;
            b.setMessage(onlySelectedLabel());
            refilter();
        }).bounds(x, y, buttonWidth, 20).build();
        this.addRenderableWidget(onlySelectedButton);

        autoButton = Button.builder(autoLabel(), b -> {
            cfg.autoOnOpen = !cfg.autoOnOpen;
            b.setMessage(autoLabel());
        }).bounds(x + (buttonWidth + gap), y, buttonWidth, 20).build();
        this.addRenderableWidget(autoButton);

        speedButton = Button.builder(speedLabel(), b -> {
            cfg.speed = (cfg.speed + 1) % AutoLooter.SPEED_LEVELS;
            b.setMessage(speedLabel());
        }).bounds(x + 2 * (buttonWidth + gap), y, buttonWidth, 20).build();
        this.addRenderableWidget(speedButton);

        this.addRenderableWidget(Button.builder(Component.translatable("chestpicker.done"), b -> this.onClose())
                .bounds(x + 3 * (buttonWidth + gap), y, buttonWidth, 20).build());

        refilter();
    }

    private Component onlySelectedLabel() {
        return Component.translatable("chestpicker.only_selected",
                Component.translatable(onlySelected ? "chestpicker.on" : "chestpicker.off"));
    }

    private Component autoLabel() {
        return Component.translatable("chestpicker.auto",
                Component.translatable(ChestPickerConfig.get().autoOnOpen ? "chestpicker.on" : "chestpicker.off"));
    }

    private Component speedLabel() {
        return Component.translatable("chestpicker.speed",
                Component.translatable("chestpicker.speed." + ChestPickerConfig.get().speed));
    }

    private void refilter() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        Set<String> selected = ChestPickerConfig.get().items;
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all) {
            if (onlySelected && !selected.contains(entry.id())) {
                continue;
            }
            if (!query.isEmpty() && !entry.nameLower().contains(query) && !entry.id().contains(query)) {
                continue;
            }
            result.add(entry);
        }
        filtered = result;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int maxScroll() {
        int rows = (int) Math.ceil(filtered.size() / (double) COLS);
        return Math.max(0, rows - visibleRows);
    }

    private Entry entryAt(double mouseX, double mouseY) {
        if (mouseX < gridLeft || mouseY < gridTop) {
            return null;
        }
        int col = (int) ((mouseX - gridLeft) / CELL);
        int row = (int) ((mouseY - gridTop) / CELL);
        if (col >= COLS || row >= visibleRows) {
            return null;
        }
        int index = (scroll + row) * COLS + col;
        return index < filtered.size() ? filtered.get(index) : null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Set<String> selected = ChestPickerConfig.get().items;

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("chestpicker.count", selected.size(), filtered.size()),
                gridLeft, 46, 0xFFFFFFFF);

        int gridRight = gridLeft + COLS * CELL;
        int gridBottom = gridTop + visibleRows * CELL;
        graphics.fill(gridLeft - 2, gridTop - 2, gridRight + 2, gridBottom + 2, 0x88000000);

        Entry hovered = null;
        int start = scroll * COLS;
        for (int i = 0; i < visibleRows * COLS; i++) {
            int index = start + i;
            if (index >= filtered.size()) {
                break;
            }
            Entry entry = filtered.get(index);
            int x = gridLeft + (i % COLS) * CELL;
            int y = gridTop + (i / COLS) * CELL;
            boolean isSelected = selected.contains(entry.id());
            boolean isHovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;

            if (isSelected) {
                graphics.fill(x, y, x + CELL - 1, y + CELL - 1, 0x8855FF55);
            } else if (isHovered) {
                graphics.fill(x, y, x + CELL - 1, y + CELL - 1, 0x66FFFFFF);
            }
            graphics.renderItem(entry.stack(), x + 1, y + 1);
            if (isHovered) {
                hovered = entry;
            }
        }

        // Полоса прокрутки
        int max = maxScroll();
        if (max > 0) {
            int trackX = gridRight + 5;
            int trackHeight = gridBottom - gridTop;
            graphics.fill(trackX, gridTop, trackX + 5, gridBottom, 0x66000000);
            int thumbHeight = Math.max(12, trackHeight * visibleRows / (visibleRows + max));
            int thumbY = gridTop + (trackHeight - thumbHeight) * scroll / max;
            graphics.fill(trackX, thumbY, trackX + 5, thumbY + thumbHeight, 0xCCFFFFFF);
        }

        if (hovered != null) {
            graphics.setTooltipForNextFrame(this.font, hovered.stack().getHoverName(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == 0) {
            Entry entry = entryAt(event.x(), event.y());
            if (entry != null) {
                Set<String> selected = ChestPickerConfig.get().items;
                if (!selected.remove(entry.id())) {
                    selected.add(entry.id());
                }
                if (onlySelected) {
                    refilter();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        ChestPickerConfig.save();
        super.removed();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
