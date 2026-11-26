package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.BestMechArms;
import cn.starfall.blueprint.BestMechCombatSystems;
import cn.starfall.blueprint.BestMechNetworking;
import cn.starfall.blueprint.BestMechProgression;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Presentation only: all operation permission and resource costs are server authoritative. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class BestMechClient {
    private static final IKeyConflictContext MECH_PILOT = new IKeyConflictContext() {
        @Override public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.screen == null && mc.player != null && mc.player.getVehicle() instanceof BestMechEntity;
        }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this; }
    };
    private static final KeyMapping PANEL = new KeyMapping("key.starfall_blueprint.mech_controls",MECH_PILOT,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,"key.categories.starfall_blueprint");
    private static final KeyMapping LOADOUT = new KeyMapping("key.starfall_blueprint.mech_loadout",MECH_PILOT,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_I,"key.categories.starfall_blueprint");
    private static final KeyMapping LIFT = new KeyMapping("key.starfall_blueprint.mech_lift",MECH_PILOT,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_H,"key.categories.starfall_blueprint");
    private static final KeyMapping DRILL = new KeyMapping("key.starfall_blueprint.mech_drill",MECH_PILOT,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G,"key.categories.starfall_blueprint");
    private static final KeyMapping FIRE = new KeyMapping("key.starfall_blueprint.mech_fire",MECH_PILOT,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.starfall_blueprint");
    private static final KeyMapping MINING_MODE = new KeyMapping("key.starfall_blueprint.mech_mining_mode",MECH_PILOT,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_J,"key.categories.starfall_blueprint");
    private static final KeyMapping FRIENDLY_FIRE = new KeyMapping("key.starfall_blueprint.mech_friendly_fire",MECH_PILOT,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_U,"key.categories.starfall_blueprint");
    private static final KeyMapping TERRAIN_DAMAGE = new KeyMapping("key.starfall_blueprint.mech_terrain_damage",MECH_PILOT,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_L,"key.categories.starfall_blueprint");
    private static final KeyMapping LINGCHUAN_SUPPORT = new KeyMapping("key.starfall_blueprint.mech_lingchuan_support",MECH_PILOT,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"key.categories.starfall_blueprint");
    private static BestMechEntity lastVehicle;
    private static boolean leftHeld, rightHeld;
    private static int lastSentBits = -1, inputHeartbeat;

    private BestMechClient() {}
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(PANEL); event.register(LOADOUT); event.register(DRILL); event.register(FIRE); event.register(LIFT);
        event.register(MINING_MODE); event.register(FRIENDLY_FIRE); event.register(TERRAIN_DAMAGE); event.register(LINGCHUAN_SUPPORT);
    }
    public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID,"mech_status"),(graphics,delta)->render(graphics));
    }
    private static boolean piloting() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && !mc.isPaused() && mc.player.getVehicle() instanceof BestMechEntity;
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event) {
        if (!piloting()) {
            if (event.getAction() == GLFW.GLFW_RELEASE) {
                if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) leftHeld = false;
                else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) rightHeld = false;
            }
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            leftHeld = event.getAction() != GLFW.GLFW_RELEASE;
            event.setCanceled(true);
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rightHeld = event.getAction() != GLFW.GLFW_RELEASE;
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (piloting()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BestMechEntity mech = mc.player != null && mc.player.getVehicle() instanceof BestMechEntity vehicle ? vehicle : null;
        if (mech != lastVehicle) {
            lastVehicle = mech; leftHeld = false; rightHeld = false; lastSentBits = -1; inputHeartbeat = 0;
        }
        if (mech == null || mc.screen != null || mc.isPaused()) {
            leftHeld = false; rightHeld = false;
            if (mech != null && lastSentBits > 0) {
                PacketDistributor.sendToServer(new BestMechNetworking.PilotInput(0));
                lastSentBits = 0;
            }
            if (mech == null) return;
        }
        while (PANEL.consumeClick()) {
            if (mc.screen == null && !mc.isPaused()) mc.setScreen(new ControlsScreen());
        }
        if (mc.screen == null && !mc.isPaused()) {
            // Drain single-press clicks so vanilla/other handlers never leak them; holding drives continuous input.
            while (DRILL.consumeClick()) {}
            while (FIRE.consumeClick()) {}
            int bits = 0;
            if (mc.options.keyJump.isDown()) bits |= BestMechEntity.INPUT_JUMP;
            if (mc.options.keySprint.isDown()) bits |= BestMechEntity.INPUT_SPRINT;
            boolean primary = leftHeld || (mech.combatCapable() ? FIRE.isDown() : DRILL.isDown());
            boolean secondary = rightHeld || (mech.combatCapable() && mech.engineeringCapable() && DRILL.isDown());
            if (primary) bits |= BestMechEntity.INPUT_PRIMARY;
            if (secondary) bits |= BestMechEntity.INPUT_SECONDARY;
            if (bits != lastSentBits || (bits != 0 && ++inputHeartbeat >= 4)) {
                PacketDistributor.sendToServer(new BestMechNetworking.PilotInput(bits));
                lastSentBits = bits;
                inputHeartbeat = 0;
            }
        }
        toggleClicks(LOADOUT, 8, mech, mc);
        toggleClicks(LIFT, 7, mech, mc);
        toggleClicks(MINING_MODE, 3, mech, mc);
        toggleClicks(FRIENDLY_FIRE, 4, mech, mc);
        toggleClicks(TERRAIN_DAMAGE, 5, mech, mc);
        toggleClicks(LINGCHUAN_SUPPORT, 6, mech, mc);
    }

    private static void toggleClicks(KeyMapping key, int action, BestMechEntity mech, Minecraft mc) {
        while (key.consumeClick()) {
            if (mech != null && mc.screen == null && !mc.isPaused())
                PacketDistributor.sendToServer(new BestMechNetworking.Action(action));
        }
    }

    static final int PANEL_WIDTH = 248, PANEL_HEIGHT = 140;
    private static final int TEXT = 0xffe6efeb, MUTED = 0xff9aafb5;
    private static final int NORMAL = 0xff75d9c0, WARNING = 0xffefb45f;
    private static final int DANGER = 0xfff16f70, ISOLATED = 0xffb0a0ed, WORN = 0xffd88b61;
    private static final String PANEL_KEY = "hud.starfall_blueprint.mech.panel.";

    /** Immutable synchronized inputs. requestTicks is only the local request throttle, never drill cooldown. */
    static record Snapshot(float health, float maxHealth, int energy, int heat, int wear,
                           boolean alive, boolean isolated, boolean thermalLocked, boolean qualified,
                           int requestTicks, int movementCost, String armKey, int workCost, int capacity, boolean areaTools, boolean supportDeployed) {
        Snapshot(float health, float maxHealth, int energy, int heat, int wear,
                 boolean alive, boolean isolated, boolean thermalLocked, boolean qualified,
                 int requestTicks, int movementCost, String armKey, int workCost, int capacity, boolean areaTools) {
            this(health, maxHealth, energy, heat, wear, alive, isolated, thermalLocked, qualified,
                    requestTicks, movementCost, armKey, workCost, capacity, areaTools, false);
        }
        Snapshot(float health, float maxHealth, int energy, int heat, int wear,
                 boolean alive, boolean isolated, boolean thermalLocked, boolean qualified,
                 int requestTicks, int movementCost, String armKey, int workCost, int capacity) {
            this(health, maxHealth, energy, heat, wear, alive, isolated, thermalLocked, qualified,
                    requestTicks, movementCost, armKey, workCost, capacity, false);
        }
        Snapshot(float health, float maxHealth, int energy, int heat, int wear,
                 boolean alive, boolean isolated, boolean thermalLocked, boolean qualified,
                 int requestTicks, int movementCost, String armKey, int workCost) {
            this(health, maxHealth, energy, heat, wear, alive, isolated, thermalLocked, qualified,
                    requestTicks, movementCost, armKey, workCost, BestMechEntity.CAPACITY);
        }
        Snapshot(float health, float maxHealth, int energy, int heat, int wear,
                 boolean alive, boolean isolated, boolean thermalLocked, boolean qualified,
                 int requestTicks, int movementCost) {
            this(health, maxHealth, energy, heat, wear, alive, isolated, thermalLocked, qualified,
                    requestTicks, movementCost, BestMechArms.DRILL, 32000);
        }
        Snapshot(float health, float maxHealth, int energy, int heat, int wear,
                 boolean alive, boolean isolated, boolean thermalLocked, boolean qualified, int requestTicks) {
            this(health, maxHealth, energy, heat, wear, alive, isolated, thermalLocked, qualified, requestTicks, 400);
        }
    }

    @SubscribeEvent
    public static void hideVehicleHearts(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof BestMechEntity
                && event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.VEHICLE_HEALTH)) {
            event.setCanceled(true);
        }
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.options.hideGui || mc.player==null || !(mc.player.getVehicle() instanceof BestMechEntity mech))return;
        Snapshot sample = new Snapshot(mech.getHealth(), mech.getMaxHealth(), mech.energy(), mech.heat(), mech.wear(),
                mech.isAlive(), mech.isolated(), mech.thermalLocked(), mech.operatorQualified(), mech.drillCooldown(), mech.movementCost(), mech.engineeringCapable() ? mech.workArmKey() : "combat", mech.engineeringCapable() ? mech.workCost() : 12000, mech.capacity(), mech.supportsParkingSupport(), mech.supportDeployed());
        if (mc.screen != null) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        int center = width / 2, horizon = height / 2;
        var state = status(sample);
        int lamp = state.color();
        // Shallow canopy braces appear only inside the first-person cockpit; third-person stays unobstructed.
        if (mc.options.getCameraType().isFirstPerson()) {
            for (int strip = 0; strip < 15; strip++) {
                int span = (15-strip)*3;
                graphics.fill(0,strip,span,strip+1,0xff23333d);
                graphics.fill(width-span,strip,width,strip+1,0xff23333d);
                graphics.fill(0,height-strip-1,span,height-strip,0xff192b37);
                graphics.fill(width-span,height-strip-1,width,height-strip,0xff192b37);
            }
        }
        graphics.fillGradient(center-70,0,center+70,24,0xe113212b,0x92203341);
        int heading = Math.floorMod(Math.round(mc.player.getYRot()) + 180, 360);
        graphics.drawCenteredString(mc.font, String.format(Locale.ROOT,"%03d°", heading), center,4,NORMAL);
        for (int i=-8; i<=8; i++) {
            int tickX=center+i*8-Math.floorMod(heading,5);
            graphics.fill(tickX,17,tickX+1,i%2==0?23:20,0xffa4d2d1);
        }
        // Crosshair brackets tint bright cyan when an engineering block or hostile target is in active range.
        int bracketColor = crosshairColor(mc, mech);
        for (int side : new int[]{-1,1}) {
            int x=center+side*12;
            graphics.fill(x,horizon-4,x+1,horizon+5,bracketColor);
            graphics.fill(Math.min(x,x+side*5),horizon,Math.max(x,x+side*5),horizon+1,bracketColor);
        }
        float scale = Math.min(1f, Math.min(width / 390f,height / 240f));
        // Left SYS panel: Chassis, Hull, Energy, Speed / Boost
        graphics.pose().pushPose();
        graphics.pose().translate(6,height-8-82*scale,0);
        graphics.pose().scale(scale,scale,1);
        BestHangarTerminal.frame(graphics,mc.font,0,0,150,82,"SYS");
        drawFit(graphics,mech.getName(),8,6,108,TEXT);
        meter(graphics,24,"health",decimal(sample.health())+" / "+decimal(sample.maxHealth()),sample.health(),sample.maxHealth(),sample.health()<=sample.maxHealth()*0.25f?DANGER:NORMAL);
        meter(graphics,45,"energy",compactEnergy(sample.energy())+" XH",sample.energy(),sample.capacity(),sample.energy()<sample.movementCost()?DANGER:NORMAL);
        double speedBps = mech.getDeltaMovement().horizontalDistance() * 20.0;
        String speedLabel = String.format(Locale.ROOT, "%.1f m/s%s", speedBps, mech.boosting() ? "  [BOOST]" : "");
        drawFit(graphics, Component.literal(speedLabel), 8, 66, 134, mech.boosting() ? WARNING : MUTED);
        graphics.pose().popPose();

        // Right OPS & Loadout panel: Readiness, Heat, Wear, Active Arm/Weapon, Cooldown & Ammo
        graphics.pose().pushPose();
        graphics.pose().translate(width-6-156*scale,height-8-88*scale,0);
        graphics.pose().scale(scale,scale,1);
        BestHangarTerminal.frame(graphics,mc.font,0,0,156,88,"OPS");
        drawFit(graphics,label(state.key()),8,6,112,lamp);
        int heatTint = sample.thermalLocked() || sample.heat() >= 850 ? DANGER : WARNING;
        meter(graphics,24,"heat",Integer.toString(sample.heat()),sample.heat(),BestMechEntity.MAX_HEAT,heatTint);
        meter(graphics,45,"wear",Integer.toString(sample.wear()),sample.wear(),BestMechEntity.MAX_WEAR,WORN);
        drawEquipmentReadout(graphics, mech, 65);
        graphics.pose().popPose();

        // Top-center compact mode badges below compass
        renderStatusBadges(graphics, mc, mech, center, 27);

        String quickHint = mech.combatCapable() && mech.engineeringCapable()
                ? "左键 开火 · 右键 钻掘 · 空格 跃升 · F5 视角 · " + LOADOUT.getTranslatedKeyMessage().getString() + " 终端 · " + PANEL.getTranslatedKeyMessage().getString() + " 键位"
                : mech.combatCapable()
                ? "左键 开火 · 空格 跃升 · F5 视角 · " + LOADOUT.getTranslatedKeyMessage().getString() + " 终端 · " + PANEL.getTranslatedKeyMessage().getString() + " 键位"
                : "左键 钻掘 · 空格 跃升 · F5 视角 · " + LOADOUT.getTranslatedKeyMessage().getString() + " 终端 · " + PANEL.getTranslatedKeyMessage().getString() + " 键位";
        drawFit(graphics, Component.literal(quickHint), 8, 20, Math.min(220, center - 78), MUTED);
    }

    private static int crosshairColor(Minecraft mc, BestMechEntity mech) {
        if (mc.level == null || mc.player == null) return 0xffb0e4d9;
        if (mech.engineeringCapable() && !mech.workArmKey().isEmpty()) {
            Vec3 from = mc.player.getEyePosition();
            double reach = Math.max(mech.workReach(), mc.player.blockInteractionRange());
            var hit = mc.level.clip(new ClipContext(from, from.add(mc.player.getLookAngle().scale(reach)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) return 0xff6df5d8;
        }
        if (mech.combatCapable() && mech.weaponCooldownSynced() == 0) return 0xffffca6b;
        return 0xffb0e4d9;
    }

    private static void drawEquipmentReadout(GuiGraphics graphics, BestMechEntity mech, int y) {
        Component toolName;
        int cd, maxCd;
        String suffix = "";
        int suffixColor = NORMAL;
        if (mech.combatCapable()) {
            String w = mech.visualWeaponKey();
            toolName = Component.translatable("item.starfall_blueprint." + w);
            cd = mech.weaponCooldownSynced();
            maxCd = Math.max(1, mech.maxWeaponCooldown());
            int ammo = mech.syncedAmmo();
            suffix = ammo < 0 ? "∞" : Integer.toString(ammo);
            if (ammo == 0) suffixColor = DANGER;
        } else {
            toolName = BestMechArms.name(mech.workArmKey());
            cd = mech.drillCooldown();
            maxCd = Math.max(1, mech.workCooldown());
            suffix = mech.areaEngineeringCapable() ? "3×3" : "1×1";
        }
        drawFit(graphics, toolName, 8, y, 102, TEXT);
        drawFit(graphics, Component.literal(suffix), 114, y, 34, suffixColor);
        double readyFraction = 1.0 - Math.clamp((double) cd / maxCd, 0.0, 1.0);
        int fill = (int) Math.round(140 * readyFraction);
        graphics.fill(8, y + 11, 148, y + 14, 0xff263742);
        if (fill > 0) graphics.fill(8, y + 11, 8 + fill, y + 14, cd > 0 ? WARNING : NORMAL);
    }

    private static void renderStatusBadges(GuiGraphics graphics, Minecraft mc, BestMechEntity mech, int center, int y) {
        List<Badge> badges = new ArrayList<>();
        if (mech.hasUpgrade(BestMechProgression.ZHENYUAN)) {
            badges.add(new Badge(mech.silkCollection() ? "精准采集" : "时运 III", mech.silkCollection() ? 0xff9ec7ff : NORMAL));
        }
        if (mech.hasUpgrade(BestMechProgression.TIANPU)) {
            badges.add(new Badge(mech.friendlyFireSynced() ? "友伤:开" : "友伤:关", mech.friendlyFireSynced() ? DANGER : MUTED));
            badges.add(new Badge(mech.terrainDamageSynced() ? "爆破:开" : "爆破:关", mech.terrainDamageSynced() ? WARNING : MUTED));
        }
        if (mech.hasUpgrade(BestMechProgression.CHANGCHUAN)) {
            badges.add(new Badge(mech.changchuanSynced() ? "长川支援:开" : "长川支援:关", mech.changchuanSynced() ? NORMAL : MUTED));
        }
        int liftId = mech.liftTargetId();
        if (liftId >= 0 && mc.level != null) {
            var carried = mc.level.getEntity(liftId);
            if (carried != null) badges.add(new Badge("吊运: " + carried.getDisplayName().getString(), WARNING));
        }
        if (badges.isEmpty()) return;
        int totalWidth = 0;
        for (Badge b : badges) totalWidth += mc.font.width(b.text) + 10;
        totalWidth += (badges.size() - 1) * 4;
        int x = center - totalWidth / 2;
        for (Badge b : badges) {
            int w = mc.font.width(b.text) + 10;
            graphics.fill(x, y, x + w, y + 12, 0xc0101e27);
            graphics.fill(x, y + 11, x + w, y + 12, b.color);
            graphics.drawString(mc.font, b.text, x + 5, y + 2, b.color, false);
            x += w + 4;
        }
    }

    private record Badge(String text, int color) {}

    private static void meter(GuiGraphics graphics,int y,String key,String value,double current,double maximum,int tint) {
        drawFit(graphics,label(key),8,y,48,MUTED);
        drawFit(graphics,Component.literal(value),62,y,74,TEXT);
        double fraction=maximum>0?Math.clamp(current/maximum,0,1):0;
        for(int i=0;i<16;i++) graphics.fill(8+i*8,y+11,14+i*8,y+14,i+1<=Math.ceil(fraction*16)?tint:0xff324551);
    }
    /** Worldless fixture contract: caller supplies position and actual sample; bounds are 248 by 140. */
    static void renderPanel(GuiGraphics graphics, int x, int y, Snapshot sample) {
        Minecraft mc = Minecraft.getInstance();
        PanelStatus status = status(sample);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xed111e26);
        graphics.fill(x, y, x + 3, y + PANEL_HEIGHT, status.color());
        graphics.fill(x + 3, y, x + PANEL_WIDTH, y + 20, 0xff22333d);
        graphics.fill(x + 8, y + 20, x + 240, y + 21, 0xff42555b);
        drawFit(graphics, label("title").copy().append(" · ").append(label("arm", BestMechArms.name(sample.armKey()))), x + 9, y + 6, 230, TEXT);
        int lineY = y + 25;
        for(var line : mc.font.split(label(status.key()), 230)) {
            if(lineY >= y + 43) break;
            graphics.drawString(mc.font, line, x + 9, lineY, status.color(), false);
            lineY += 9;
        }
        resource(graphics, x, y + 46, "health", decimal(sample.health()) + " / " + decimal(sample.maxHealth()),
                sample.health(), sample.maxHealth(), sample.health() <= sample.maxHealth() * 0.25f ? DANGER : NORMAL);
        resource(graphics, x, y + 63, "energy", compactEnergy(sample.energy()) + " / " + compactEnergy(sample.capacity()) + " XH",
                sample.energy(), sample.capacity(), sample.energy() < sample.movementCost() ? DANGER : sample.energy() < sample.capacity() / 10 ? WARNING : NORMAL);
        resource(graphics, x, y + 80, "heat", sample.heat() + " / " + BestMechEntity.MAX_HEAT,
                sample.heat(), BestMechEntity.MAX_HEAT, sample.thermalLocked() || sample.heat() >= BestMechEntity.MAX_HEAT ? DANGER : sample.heat() >= 800 ? WARNING : NORMAL);
        resource(graphics, x, y + 97, "wear", sample.wear() + " / " + BestMechEntity.MAX_WEAR,
                sample.wear(), BestMechEntity.MAX_WEAR, sample.wear() >= BestMechEntity.MAX_WEAR ? WORN : sample.wear() >= 2160 ? WARNING : NORMAL);
        Component request = sample.requestTicks() > 0
                ? label("request", DRILL.getTranslatedKeyMessage(), sample.requestTicks())
                : sample.armKey().isEmpty() ? label("no_arm")
                : BestMechArms.areaWork(sample.armKey()) && !sample.areaTools() ? label("incompatible_arm")
                : label(BestMechArms.areaWork(sample.armKey()) ? "heavy_standby" : "standby", DRILL.getTranslatedKeyMessage(), compactEnergy(sample.workCost()));
        drawFit(graphics, request, x + 9, y + 116, 230, sample.requestTicks() > 0 ? WARNING : TEXT);
        drawFit(graphics, label("controls", mc.options.keyUp.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage()),
                x + 9, y + 129, 230, MUTED);
    }

    /** Shows the current bindings, including keys changed in the controls settings. */
    private static final class ControlsScreen extends net.minecraft.client.gui.screens.Screen {
        private ControlsScreen() {
            super(Component.translatableWithFallback("ui.starfall_blueprint.mech_controls_title", "机甲操作 / Mech controls"));
        }

        @Override public boolean isPauseScreen() { return false; }

        @Override public void tick() {
            if (minecraft == null || minecraft.player == null || !(minecraft.player.getVehicle() instanceof BestMechEntity)) onClose();
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = Math.max(4, (width - 260) / 2), y = Math.max(4, (height - 165) / 2);
            int panelWidth = Math.min(260, width - 8);
            BestHangarTerminal.frame(graphics,font,x,y,panelWidth,165,"CTRL");
            drawFit(graphics, title, x + 8, y + 7, panelWidth - 16, TEXT);
            KeyMapping[] keys = {LOADOUT, DRILL, FIRE, LIFT, MINING_MODE, FRIENDLY_FIRE, TERRAIN_DAMAGE, LINGCHUAN_SUPPORT};
            for (int i = 0; i < keys.length; i++) {
                KeyMapping key = keys[i];
                if ((i & 1) == 0) graphics.fill(x+4,y+20+i*15,x+panelWidth-4,y+34+i*15,BestHangarTerminal.PANEL);
                Component line = Component.empty().append(key.getTranslatedKeyMessage()).append("  ·  ")
                        .append(Component.translatable(key.getName()));
                drawFit(graphics, line, x + 8, y + 23 + i * 15, panelWidth - 16, TEXT);
            }
            drawFit(graphics, Component.literal("鼠标左键/右键 作业与开火 · 空格 跳跃/推进 · Ctrl 冲刺 · F5 切换视角"),
                    x + 8, y + 145, panelWidth - 16, MUTED);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private record PanelStatus(String key, int color) {}

    /** System readiness only; no claim of movement, a valid work target, or a successful request. */
    private static PanelStatus status(Snapshot sample) {
        if(!sample.alive() || sample.health() <= 0) return new PanelStatus("destroyed", DANGER);
        if(sample.isolated()) return new PanelStatus("isolated", ISOLATED);
        if(sample.thermalLocked() || sample.heat() >= BestMechEntity.MAX_HEAT) return new PanelStatus("thermal_locked", DANGER);
        if(sample.wear() >= BestMechEntity.MAX_WEAR) return new PanelStatus("worn_out", WORN);
        if(!sample.qualified()) return new PanelStatus("unqualified", ISOLATED);
        if(sample.energy() < sample.movementCost()) return new PanelStatus("no_power", DANGER);
        if(sample.armKey().isEmpty()) return new PanelStatus("no_arm", WARNING);
        if(BestMechArms.areaWork(sample.armKey()) && !sample.areaTools()) return new PanelStatus("incompatible_arm", WARNING);
        if(sample.energy() < sample.workCost()) return new PanelStatus("work_power", WARNING);
        if(sample.heat() >= 800) return new PanelStatus("hot", WARNING);
        if(sample.energy() < sample.capacity() / 10) return new PanelStatus("low_power", WARNING);
        if(sample.wear() >= 2160) return new PanelStatus("worn", WARNING);
        return new PanelStatus("ready", NORMAL);
    }

    private static void resource(GuiGraphics graphics, int x, int y, String key, String value,
                                 double current, double maximum, int color) {
        Minecraft mc = Minecraft.getInstance();
        drawFit(graphics, label(key), x + 9, y, 55, MUTED);
        Component number = Component.literal(value);
        int numberWidth = mc.font.width(number);
        drawFit(graphics, number, x + 240 - Math.min(numberWidth, 170), y, 170, TEXT);
        graphics.fill(x + 9, y + 11, x + 240, y + 14, 0xff34464e);
        double fraction = maximum > 0 ? Math.clamp(current / maximum, 0.0, 1.0) : 0;
        int fill = (int) Math.floor(231 * fraction);
        // Incomplete resources always retain at least one empty pixel.
        if(current < maximum) fill = Math.min(fill, 230);
        if(fill > 0) graphics.fill(x + 9, y + 11, x + 9 + fill, y + 14, color);
    }

    private static void drawFit(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        Minecraft mc = Minecraft.getInstance();
        float scale = Math.min(1.0f, (float) width / Math.max(1, mc.font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(mc.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static Component label(String key, Object... args) {
        return Component.translatable(PANEL_KEY + key, args);
    }

    private static String decimal(float value) {
        double floored = Math.floor(Math.max(0, value) * 10.0) / 10.0;
        return floored == Math.floor(floored) ? Long.toString((long) floored) : String.format(Locale.ROOT, "%.1f", floored);
    }

    /** Truncate instead of rounding: 31,999 stays 31.999k; 19,999,999 stays below 20M. */
    private static String compactEnergy(int value) {
        int safe = Math.max(0, value);
        if(safe < 1000) return Integer.toString(safe);
        int unit = safe < 1_000_000 ? 1000 : 1_000_000;
        int thousandths = (safe % unit) / (unit / 1000);
        String fraction = thousandths == 0 ? "" : "." + String.format(Locale.ROOT, "%03d", thousandths).replaceFirst("0+$", "");
        return safe / unit + fraction + (unit == 1000 ? "k" : "M");
    }
}
