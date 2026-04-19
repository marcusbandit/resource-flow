package bandit.resourceflow;

import arc.*;
import arc.graphics.*;
import arc.scene.*;
import arc.scene.event.*;
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

    private static final String SETTING_INTERVAL = "resflow-interval";
    private static final int DEFAULT_INTERVAL = 60;
    private static final float HIDE_THRESHOLD = 0.5f;
    private static final float TOP_PADDING = 6f;
    private static final float ICON_SIZE = 20f;
    private static final float COUNT_W = 44f;
    private static final float RATE_W = 80f;
    private static final int PER_ROW = 4;

    private final ObjectIntMap<Item> prev = new ObjectIntMap<>();
    private final ObjectFloatMap<Item> perSec = new ObjectFloatMap<>();
    private final ObjectSet<Item> sticky = new ObjectSet<>();
    private int tickCounter = 0;
    private Team prevTeam;

    private Table root;
    private Table inner;
    private CoreItemsDisplay cachedCoreDisplay;

    @Override
    public void init() {
        Events.on(EventType.ClientLoadEvent.class, e -> buildUi());
    }

    private int interval() {
        int v = Core.settings.getInt(SETTING_INTERVAL, DEFAULT_INTERVAL);
        return Math.max(6, v);
    }

    private void buildUi() {
        if (Vars.ui == null || Vars.ui.hudGroup == null) return;

        root = new Table();
        root.setFillParent(true);
        root.touchable = Touchable.childrenOnly;
        root.top();
        root.update(this::tick);

        inner = new Table(Tex.buttonTrans);
        inner.touchable = Touchable.disabled;
        inner.margin(6f);

        root.add(inner).padTop(TOP_PADDING);
        Vars.ui.hudGroup.addChild(root);
        root.toBack();

        Log.info("[resource-flow] HUD overlay attached.");
    }

    private void tick() {
        if (inner == null) return;

        hideCoreDisplay();

        Team team = currentTeam();
        boolean show = Vars.state != null && Vars.state.isGame() && team != null;
        inner.visible = show;
        if (!show) return;

        if (team != prevTeam) {
            prev.clear();
            perSec.clear();
            sticky.clear();
            prevTeam = team;
            tickCounter = 0;
            seedPrev(team);
            updateSticky(team);
            rebuild();
            return;
        }

        updateSticky(team);

        tickCounter++;
        if (tickCounter >= interval()) {
            int ticks = tickCounter;
            tickCounter = 0;
            sample(team, ticks);
            rebuild();
        }
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
        // Rebuild immediately on newly-seen items so they appear without waiting for the sample tick.
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

        if (sticky.size == 0) {
            inner.add("— no items yet —").color(Color.lightGray).pad(4f);
            return;
        }

        int col = 0;
        for (Item item : Vars.content.items()) {
            if (!sticky.contains(item)) continue;
            final Item it = item;
            float rate = perSec.get(it, 0f);
            boolean hasRate = Math.abs(rate) >= HIDE_THRESHOLD;
            Color rateColor = hasRate ? (rate > 0 ? Color.green : Color.scarlet) : Color.clear;
            String rateText = hasRate ? "(" + formatSigned(rate) + "/s)" : "";

            Table cell = new Table();
            cell.image(it.uiIcon).size(ICON_SIZE);
            cell.label(() -> UI.formatAmount(safeCount(it)))
                .width(COUNT_W).right().padLeft(4f);
            cell.add(rateText).width(RATE_W).left().padLeft(6f).color(rateColor);

            inner.add(cell).padLeft(10f).padRight(12f).padTop(2f).padBottom(2f);

            col++;
            if (col % PER_ROW == 0) inner.row();
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
