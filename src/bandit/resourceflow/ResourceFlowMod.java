package bandit.resourceflow;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.modules.*;

public class ResourceFlowMod extends Mod {

    private static final String S_INTERVAL = "resflow-interval";
    private static final String S_BRACKETS = "resflow-brackets";
    private static final String S_OPACITY = "resflow-opacity";
    private static final String S_HIDE_IDLE = "resflow-hide-idle";
    private static final String S_SHOW_RATES = "resflow-show-rates";

    private static final int DEFAULT_INTERVAL = 60;
    private static final boolean DEFAULT_BRACKETS = false;
    private static final int DEFAULT_OPACITY = 40;
    private static final boolean DEFAULT_HIDE_IDLE = false;
    private static final boolean DEFAULT_SHOW_RATES = true;

    private static final String BIND_TOGGLE_RATES = "resflow-toggle-rates";
    private static final String BIND_CATEGORY = "Resource Flow";

    private static final float HIDE_THRESHOLD = 0.5f;
    private static final float TOP_PADDING = 6f;
    private static final float ICON_SIZE = 20f;
    private static final float COUNT_W = 44f;
    private static final float RATE_W_BRACKETS = 80f;
    private static final float RATE_W_PLAIN = 64f;
    private static final int PER_ROW = 4;

    private final ObjectIntMap<Item> prev = new ObjectIntMap<>();
    private final ObjectFloatMap<Item> perSec = new ObjectFloatMap<>();
    private final ObjectSet<Item> sticky = new ObjectSet<>();
    private int tickCounter = 0;
    private Team prevTeam;

    private Table root;
    private Table inner;
    private Image bg;
    private Stack stack;
    private CoreItemsDisplay cachedCoreDisplay;
    private KeyBind toggleBind;

    private boolean lastBrackets;
    private boolean lastHideIdle;
    private boolean lastShowRates;

    @Override
    public void init() {
        toggleBind = KeyBind.add(BIND_TOGGLE_RATES, KeyCode.f9, BIND_CATEGORY);

        Events.on(EventType.ClientLoadEvent.class, e -> {
            registerSettings();
            buildUi();
        });
    }

    private void registerSettings() {
        if (Vars.ui == null || Vars.ui.settings == null) return;

        ObjectMap<String, String> bundle = Core.bundle.getProperties();
        bundle.put("setting." + S_SHOW_RATES + ".name", "Show +/- rate");
        bundle.put("setting." + S_BRACKETS + ".name", "Wrap rate in brackets");
        bundle.put("setting." + S_HIDE_IDLE + ".name", "Hide items with no change");
        bundle.put("setting." + S_OPACITY + ".name", "Background opacity");
        bundle.put("setting." + S_INTERVAL + ".name", "Sample interval");

        Vars.ui.settings.addCategory(BIND_CATEGORY, t -> {
            t.checkPref(S_SHOW_RATES, DEFAULT_SHOW_RATES);
            t.checkPref(S_BRACKETS, DEFAULT_BRACKETS);
            t.checkPref(S_HIDE_IDLE, DEFAULT_HIDE_IDLE);
            t.sliderPref(S_OPACITY, DEFAULT_OPACITY, 0, 100, 5, v -> v + "%");
            t.sliderPref(S_INTERVAL, DEFAULT_INTERVAL, 6, 300, 6, v -> String.format("%.1fs", v / 60f));
        });
    }

    private int interval() {
        int v = Core.settings.getInt(S_INTERVAL, DEFAULT_INTERVAL);
        return Math.max(6, v);
    }

    private void buildUi() {
        if (Vars.ui == null || Vars.ui.hudGroup == null) return;

        root = new Table();
        root.setFillParent(true);
        root.touchable = Touchable.childrenOnly;
        root.top();
        root.update(this::tick);

        bg = new Image(Tex.button);
        bg.touchable = Touchable.disabled;

        inner = new Table();
        inner.touchable = Touchable.disabled;
        inner.margin(6f);

        stack = new Stack(bg, inner);
        root.add(stack).padTop(TOP_PADDING);
        Vars.ui.hudGroup.addChild(root);
        root.toBack();

        syncSettingsSnapshot();
        Log.info("[resource-flow] HUD overlay attached.");
    }

    private void tick() {
        if (stack == null) return;

        hideCoreDisplay();

        if (toggleBind != null && Core.input.keyTap(toggleBind)) {
            boolean cur = Core.settings.getBool(S_SHOW_RATES, DEFAULT_SHOW_RATES);
            Core.settings.put(S_SHOW_RATES, !cur);
        }

        int opacity = Core.settings.getInt(S_OPACITY, DEFAULT_OPACITY);
        bg.color.a = opacity / 100f;

        Team team = currentTeam();
        boolean show = Vars.state != null && Vars.state.isGame() && team != null;
        stack.visible = show;
        if (!show) return;

        if (team != prevTeam) {
            prev.clear();
            perSec.clear();
            sticky.clear();
            prevTeam = team;
            tickCounter = 0;
            seedPrev(team);
            updateSticky(team);
            syncSettingsSnapshot();
            rebuild();
            return;
        }

        updateSticky(team);

        if (settingsChanged()) {
            syncSettingsSnapshot();
            rebuild();
        }

        tickCounter++;
        if (tickCounter >= interval()) {
            int ticks = tickCounter;
            tickCounter = 0;
            sample(team, ticks);
            rebuild();
        }
    }

    private boolean settingsChanged() {
        return lastBrackets != Core.settings.getBool(S_BRACKETS, DEFAULT_BRACKETS)
            || lastHideIdle != Core.settings.getBool(S_HIDE_IDLE, DEFAULT_HIDE_IDLE)
            || lastShowRates != Core.settings.getBool(S_SHOW_RATES, DEFAULT_SHOW_RATES);
    }

    private void syncSettingsSnapshot() {
        lastBrackets = Core.settings.getBool(S_BRACKETS, DEFAULT_BRACKETS);
        lastHideIdle = Core.settings.getBool(S_HIDE_IDLE, DEFAULT_HIDE_IDLE);
        lastShowRates = Core.settings.getBool(S_SHOW_RATES, DEFAULT_SHOW_RATES);
    }

    private Team currentTeam() {
        var p = Vars.player;
        return p == null ? null : p.team();
    }

    private void hideCoreDisplay() {
        if (cachedCoreDisplay != null && cachedCoreDisplay.parent != null) {
            cachedCoreDisplay.visible = false;
            return;
        }
        cachedCoreDisplay = findCoreDisplay(Vars.ui == null ? null : Vars.ui.hudGroup);
        if (cachedCoreDisplay != null) cachedCoreDisplay.visible = false;
    }

    private CoreItemsDisplay findCoreDisplay(Group g) {
        if (g == null) return null;
        for (int i = 0; i < g.getChildren().size; i++) {
            Element el = g.getChildren().get(i);
            if (el instanceof CoreItemsDisplay cid) return cid;
            if (el instanceof Group sub) {
                CoreItemsDisplay r = findCoreDisplay(sub);
                if (r != null) return r;
            }
        }
        return null;
    }

    private void seedPrev(Team team) {
        ItemModule items = team.items();
        if (items == null) return;
        for (Item item : Vars.content.items()) {
            prev.put(item, items.get(item));
        }
    }

    private void updateSticky(Team team) {
        ItemModule items = team.items();
        if (items == null) return;
        boolean added = false;
        for (Item item : Vars.content.items()) {
            if (sticky.contains(item)) continue;
            if (items.get(item) > 0 || Math.abs(perSec.get(item, 0f)) >= HIDE_THRESHOLD) {
                sticky.add(item);
                added = true;
            }
        }
        if (added) rebuild();
    }

    private void sample(Team team, int tickDelta) {
        ItemModule items = team.items();
        if (items == null) return;

        float seconds = tickDelta / 60f;

        for (Item item : Vars.content.items()) {
            int cur = items.get(item);
            int p = prev.get(item, cur);
            float rate = (cur - p) / seconds;
            perSec.put(item, rate);
            prev.put(item, cur);
        }
    }

    private void rebuild() {
        inner.clearChildren();

        boolean brackets = Core.settings.getBool(S_BRACKETS, DEFAULT_BRACKETS);
        boolean showRates = Core.settings.getBool(S_SHOW_RATES, DEFAULT_SHOW_RATES);
        boolean hideIdle = Core.settings.getBool(S_HIDE_IDLE, DEFAULT_HIDE_IDLE);
        float rateW = brackets ? RATE_W_BRACKETS : RATE_W_PLAIN;

        if (sticky.size == 0) {
            inner.add("— no items yet —").color(Color.lightGray).pad(4f);
            return;
        }

        int col = 0;
        int shown = 0;
        for (Item item : Vars.content.items()) {
            if (!sticky.contains(item)) continue;
            final Item it = item;
            float rate = perSec.get(it, 0f);
            boolean hasRate = Math.abs(rate) >= HIDE_THRESHOLD;

            if (showRates && hideIdle && !hasRate) continue;

            Table cell = new Table();
            cell.image(it.uiIcon).size(ICON_SIZE);
            cell.label(() -> UI.formatAmount(safeCount(it)))
                .width(COUNT_W).right().padLeft(4f);
            if (showRates) {
                Color rateColor = hasRate ? (rate > 0 ? Color.green : Color.scarlet) : Color.clear;
                String rateText = hasRate
                    ? (brackets ? "(" + formatSigned(rate) + "/s)" : formatSigned(rate) + "/s")
                    : "";
                cell.add(rateText).width(rateW).left().padLeft(6f).color(rateColor);
            }

            inner.add(cell).padLeft(10f).padRight(12f).padTop(2f).padBottom(2f);

            col++;
            if (col % PER_ROW == 0) inner.row();
            shown++;
        }

        if (shown == 0) {
            inner.add("— nothing changing —").color(Color.lightGray).pad(4f);
        }
    }

    private int safeCount(Item item) {
        var p = Vars.player;
        if (p == null) return 0;
        var t = p.team();
        if (t == null) return 0;
        var im = t.items();
        if (im == null) return 0;
        return im.get(item);
    }

    private String formatSigned(float v) {
        String sign = v > 0 ? "+" : "-";
        long abs = Math.round(Math.abs(v));
        return sign + UI.formatAmount(abs);
    }
}
