package cn.starfall.blueprint;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class Esper {
    static final String DATA = "StarfallEsper";
    public static final int MAX_RANK = 7, MAX_LEVEL = 5;
    public static final double SLASH_HALF_ANGLE = Math.toRadians(56);
    public static final float BARRIER_RADIUS = 1.55F;
    public enum School {
        SPACE, ELECTRIC;
        public Component title() { return label("school." + name().toLowerCase(Locale.ROOT)); }
        public Skill root() { return this == SPACE ? Skill.BLINK : Skill.INDUCTION; }
        public java.util.List<Skill> skills() { return java.util.Arrays.stream(SKILLS).filter(s -> s.school == this).toList(); }
    }
    public enum Skill {
        BLINK(1, 20, 20, 0, 1), PORTAL(3, 45, 60, 2, 0), SLASH(2, 8, 10, 1, 1),
        STORAGE(2, 0, 60, 1, 2), COLLAPSE(5, 58, 80, 4, 2), VOLLEY(3, 34, 12, 2, 2),
        SLING(2, 28, 28, 1, 0), SHIFT(4, 10, 12, 3, 0), PULL(3, 28, 32, 2, 1),
        DOMAIN(5, 65, 50, 4, 0), BARRIER(5, 0, 10, 4, 1),
        ARC(3, 25, 32, 2, 0, 15), LOCK(4, 32, 75, 3, 1),
        INDUCTION(1, 8, 24, 0, 1, 0), SPARK(1, 17, 18, 1, 0, 7),
        BOOST(2, 18, 40, 1, 1, 8), FEED(2, 15, 50, 1, 2, 10),
        INSULATE(3, 27, 90, 2, 1, 13), SCAN(3, 16, 100, 2, 2, 8),
        PARALYZE(4, 32, 85, 3, 0, 20), PULSE(4, 41, 95, 3, 1, 24),
        OVERDRIVE(4, 32, 130, 3, 2, 18), STORM(5, 58, 160, 4, 0, 36),
        SURGE(5, 50, 145, 4, 2, 30), LANCE(4, 36, 55, 3, 3, 23),
        IONIZE(3, 12, 32, 2, 3, 4),
        CAPACITOR(1, 0, 0, 0, 2, 0), RECYCLING(3, 0, 0, 2, 4, 0),
        ANCHOR(2, 0, 0, 0, 3), RESONANCE(4, 0, 0, 3, 3);

        public final int rank, cost, cooldown, column, row, charge;
        public final School school;
        public final String key;
        public boolean passive() { return this == CAPACITOR || this == RECYCLING || this == ANCHOR || this == RESONANCE; }
        public boolean channelled() { return this == SLASH || this == COLLAPSE || this == VOLLEY; }
        public int cooldownAt(int level) { return Math.max(0, cooldown - cooldown * (Math.max(1, level) - 1) / 12); }
        Skill(int rank, int cost, int cooldown, int column, int row) {
            this(rank, cost, cooldown, column, row, -1);
        }
        Skill(int rank, int cost, int cooldown, int column, int row, int charge) {
            this.rank = rank; this.cost = cost; this.cooldown = cooldown;
            this.column = column; this.row = row; this.charge = Math.max(0, charge);
            school = charge < 0 ? School.SPACE : School.ELECTRIC;
            key = name().toLowerCase(Locale.ROOT);
        }
        public java.util.List<Skill> prerequisites() {
            return switch (this) {
                case BLINK, INDUCTION -> java.util.List.of();
                case SLING, SLASH, STORAGE, ANCHOR -> java.util.List.of(BLINK);
                case PORTAL -> java.util.List.of(SLING);
                case PULL -> java.util.List.of(SLASH);
                case VOLLEY -> java.util.List.of(STORAGE);
                case SHIFT -> java.util.List.of(PORTAL);
                case LOCK -> java.util.List.of(PULL);
                case DOMAIN -> java.util.List.of(SHIFT, STORAGE);
                case BARRIER -> java.util.List.of(LOCK);
                case COLLAPSE -> java.util.List.of(VOLLEY, LOCK);
                case SPARK, BOOST, FEED, CAPACITOR -> java.util.List.of(INDUCTION);
                case ARC -> java.util.List.of(SPARK);
                case INSULATE -> java.util.List.of(BOOST);
                case SCAN -> java.util.List.of(FEED);
                case PARALYZE -> java.util.List.of(ARC);
                case PULSE -> java.util.List.of(INSULATE, ARC);
                case OVERDRIVE -> java.util.List.of(SCAN);
                case STORM -> java.util.List.of(PARALYZE, PULSE);
                case SURGE -> java.util.List.of(OVERDRIVE, PULSE);
                case IONIZE -> java.util.List.of(SPARK);
                case LANCE -> java.util.List.of(IONIZE, ARC);
                case RECYCLING -> java.util.List.of(IONIZE, CAPACITOR);
                case RESONANCE -> java.util.List.of(ANCHOR, SLASH);
            };
        }
        public Component title() { return label("skill." + key); }
    }
    public static final Skill[] SKILLS = Skill.values();
    public static final int SLOTS = 4;

    public static final net.neoforged.neoforge.registries.DeferredBlock<EsperFacility> AWAKENER = facility("esper_awakener", 0);
    public static final net.neoforged.neoforge.registries.DeferredBlock<EsperFacility> COMPUTE = facility("esper_compute_unit", 1);
    public static final net.neoforged.neoforge.registries.DeferredBlock<EsperFacility> LAUNCHER = facility("esper_launcher", 2);
    static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>, BlockEntityType<EsperFacility.Controller>>
            CONTROLLER = StarfallBlueprint.ENTITIES.register("esper_facility",
                    () -> BlockEntityType.Builder.of(EsperFacility.Controller::new, AWAKENER.get(), COMPUTE.get()).build(null));
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SATELLITE =
            StarfallBlueprint.ITEMS.registerSimpleItem("esper_satellite");
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> TERMINAL =
            StarfallBlueprint.ITEMS.registerItem("esper_terminal", OrbitalTerminalItem::new);
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> RETUNER =
            StarfallBlueprint.ITEMS.registerSimpleItem("esper_retuner");
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> MAXIMIZER =
            StarfallBlueprint.ITEMS.registerItem("esper_maximizer", Maximizer::new);
    public static final net.neoforged.neoforge.registries.DeferredHolder<MenuType<?>, MenuType<EsperMenu>> MENU =
            StarfallBlueprint.MENUS.register("esper_awakener", () -> new MenuType<>(EsperMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final net.neoforged.neoforge.registries.DeferredRegister<net.minecraft.world.entity.EntityType<?>> PROJECTILES =
            net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, StarfallBlueprint.ID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,
            net.minecraft.world.entity.EntityType<EsperProjectile>> SHOT = PROJECTILES.register("esper_shot", () ->
            net.minecraft.world.entity.EntityType.Builder.<EsperProjectile>of(EsperProjectile::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.24F, .24F).clientTrackingRange(6).updateInterval(2).build("starfall_blueprint:esper_shot"));

    static {
        StarfallBlueprint.TABS.register("baiwan", () -> CreativeModeTab.builder()
                .title(label("name")).icon(() -> new ItemStack(AWAKENER.get()))
                .displayItems((p, out) -> {
                    out.accept(AWAKENER.get()); out.accept(COMPUTE.get()); out.accept(SATELLITE.get());
                    out.accept(Haijia.item("hj_terminal")); out.accept(RETUNER.get()); out.accept(MAXIMIZER.get());
                }).build());
    }

    private static net.neoforged.neoforge.registries.DeferredBlock<EsperFacility> facility(String name, int kind) {
        var block = StarfallBlueprint.BLOCKS.register(name, () -> new EsperFacility(kind,
                BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops().noOcclusion()
                        .lightLevel(s -> s.getValue(EsperFacility.PART) <= 1 ? 8 : 3)));
        StarfallBlueprint.ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    public static Component label(String id, Object... args) {
        return Component.translatable("esper." + id, args);
    }

    static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(DATA, 10)) root.put(DATA, new CompoundTag());
        CompoundTag data = root.getCompound(DATA);
        migrateLegacy(data);
        return data;
    }
    private static void migrateLegacy(CompoundTag data) {
        int previous = data.getInt("Rank");
        if (previous == 0 || school(data) != null) return;
        School main = level(data, Skill.ARC) > 0 && java.util.Arrays.stream(SKILLS)
                .noneMatch(skill -> skill.school == School.SPACE && level(data, skill) > 0) ? School.ELECTRIC : School.SPACE;
        activate(data, main);
        data.putInt("Rank_" + main, previous);
        data.putInt("RankPractice_" + main, data.getInt("RankPractice"));
        for (int i = 0; i < SLOTS; i++) {
            int old = data.getInt("Slot" + i);
            data.remove("Slot" + i);
            if (old > 0 && old <= 12 && SKILLS[old - 1].school == main && SKILLS[old - 1] != main.root())
                data.putInt("Slot" + i + "_" + main, old);
        }
        data.remove("Rank"); data.remove("RankPractice");
    }
    static School school(CompoundTag data) {
        return switch (data.getString("School")) {
            case "SPACE" -> School.SPACE;
            case "ELECTRIC" -> School.ELECTRIC;
            default -> null;
        };
    }
    static int rank(CompoundTag data) { return school(data) == null ? 0 : data.getInt("Rank_" + school(data)); }
    static int rankProgress(CompoundTag data) { return data.getInt("RankPractice_" + school(data)); }
    static int level(CompoundTag data, Skill skill) { return data.getInt("Level_" + skill.key); }
    static int mastery(CompoundTag data, Skill skill) { return data.getInt("Mastery_" + skill.key); }
    static int slot(CompoundTag data, int slot) { return data.getInt("Slot" + slot + "_" + school(data)) - 1; }
    static boolean prerequisites(CompoundTag data, Skill skill) {
        return skill.prerequisites().stream().allMatch(required -> level(data, required) > 0);
    }
    static int practiceNeeded(int level) { return level == 0 ? 0 : 8 * level * (level + 1); }
    static int rankPractice(int rank) { return 18 + 12 * rank * rank; }
    static int price(Skill skill, int level) { return 2 + skill.rank + 2 * level + level * level; }
    static int rankPrice(int rank) { return 3 + 3 * rank; }
    static int trainingEnergy(Skill skill, int level, boolean awakening) {
        return awakening ? 8000 : 12000 * skill.rank + 10000 * level * (level + 1);
    }
    static int promotionEnergy(int rank) { return 20000 * rank * (rank + 1); }

    static boolean train(ServerPlayer player, Skill skill, EsperFacility.Controller machine) {
        CompoundTag d = data(player);
        int rank = rank(d), level = level(d, skill);
        boolean awakening = school(d) == null;
        if (awakening ? skill != skill.school.root() : (skill.school != school(d) || skill.rank > rank || level >= MAX_LEVEL
                || level >= rank || !prerequisites(d, skill) || mastery(d, skill) < practiceNeeded(level))) return false;
        int cost = awakening ? 0 : price(skill, level);
        if (!pay(player, machine, cost, trainingEnergy(skill, level, awakening))) return false;
        d.putInt("Level_" + skill.key, level + 1);
        d.putInt("Mastery_" + skill.key, mastery(d, skill) - practiceNeeded(level));
        if (awakening) {
            activate(d, skill.school);
            refresh(player, d);
            d.putInt("Power", d.getInt("Max"));
        }
        return true;
    }
    static void activate(CompoundTag data, School school) {
        data.putString("School", school.name());
        if (rank(data) > 0) return;
        data.putInt("Rank_" + school, 1);
        Skill root = school.root();
        data.putInt("Level_" + root.key, 1);
        data.putInt("Slot0_" + school, root.ordinal() + 1);
        if (school == School.ELECTRIC) data.putInt("Charge", 40);
    }
    static boolean retune(ServerPlayer p, BlockPos pos, ItemStack stack) {
        CompoundTag d = data(p);
        School from = school(d);
        if (from == null || p.isSpectator() || !p.isAlive() || p.level().dimension().equals(EsperDomain.KEY)
                || !stack.is(RETUNER.get()) || !p.level().getBlockState(pos).is(AWAKENER.get())
                || !p.level().mayInteract(p, pos)
                || !pay(p, EsperFacility.controller(p.level(), pos), 0, 8000)) return false;
        stack.consume(1, p);
        EsperEffects.endSchoolEffects(p, d);
        activate(d, from == School.SPACE ? School.ELECTRIC : School.SPACE);
        refresh(p, d);
        p.displayClientMessage(label("retuned", school(d).title()), true);
        return true;
    }
    static boolean promote(ServerPlayer player, EsperFacility.Controller machine) {
        CompoundTag d = data(player);
        int rank = rank(d);
        if (rank < 1 || rank >= MAX_RANK || rankProgress(d) < rankPractice(rank)
                || !pay(player, machine, rankPrice(rank), promotionEnergy(rank))) return false;
        d.putInt("Rank_" + school(d), rank + 1);
        d.putInt("RankPractice_" + school(d), rankProgress(d) - rankPractice(rank));
        refresh(player, d);
        return true;
    }
    static boolean equip(ServerPlayer player, int index, int slot) {
        if (slot < 0 || slot >= SLOTS || index < -1 || index >= SKILLS.length) return false;
        CompoundTag d = data(player);
        School school = school(d);
        if (school == null || (index >= 0 && (SKILLS[index].school != school || SKILLS[index].passive() || level(d, SKILLS[index]) == 0))) return false;
        for (int i = 0; i < SLOTS; i++)
            if (index >= 0 && slot(d, i) == index) d.remove("Slot" + i + "_" + school);
        d.putInt("Slot" + slot + "_" + school, index + 1);
        return true;
    }
    private static boolean pay(ServerPlayer p, EsperFacility.Controller machine, int xp, int energy) {
        if (machine == null || !EsperFacility.complete(machine)) return false;
        if (p.isCreative()) return true;
        if (p.experienceLevel < xp || !machine.spend(energy)) return false;
        if (xp > 0) p.giveExperienceLevels(-xp);
        return true;
    }

    public record Cast(int slot) implements CustomPacketPayload {
        public static final int TOGGLE = -1;
        public static final int CANCEL_CHARGE = -2;
        public static final int RELEASE = 8;
        public static final int HOLD = 16;
        static final Type<Cast> TYPE = new Type<>(StarfallBlueprint.id("esper_cast"));
        static final StreamCodec<RegistryFriendlyByteBuf, Cast> CODEC =
                StreamCodec.of((buf, msg) -> buf.writeByte(msg.slot), buf -> new Cast(buf.readByte()));
        @Override public Type<Cast> type() { return TYPE; }
    }
    public static void register(IEventBus bus) {
        PROJECTILES.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                        CONTROLLER.get(), (part, side) -> {
                            var machine = EsperFacility.controller(part.getLevel(), part.getBlockPos());
                            return machine != null && machine.getBlockState().is(AWAKENER.get())
                                    && EsperFacility.complete(machine) ? machine.energy : null;
                        }));
        bus.addListener(EsperNetworking::register);
        NeoForge.EVENT_BUS.addListener(Esper::tick);
        NeoForge.EVENT_BUS.addListener(Esper::clonePlayer);
        NeoForge.EVENT_BUS.addListener(Esper::damage);
        NeoForge.EVENT_BUS.addListener(EsperEffects::tickPortals);
        NeoForge.EVENT_BUS.addListener(EsperEffects::tickBinding);
        NeoForge.EVENT_BUS.addListener(EsperElectric::tickFields);
        NeoForge.EVENT_BUS.addListener(EsperWarp::tick);
        NeoForge.EVENT_BUS.addListener(EsperCollapse::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, EsperEffects::blockDrops);
        NeoForge.EVENT_BUS.addListener(EsperDomain::guardTravel);
        NeoForge.EVENT_BUS.addListener(EsperDomain::commands);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.getPersistentData().contains(DATA)) {
                var d = data(p);
                d.remove("Mode");
                EsperEffects.endSchoolEffects(p, d);
            }
        });
    }

    static void toggleMode(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || p.containerMenu != p.inventoryMenu) return;
        var d = data(p);
        long now = p.serverLevel().getServer().overworld().getGameTime();
        if (now < d.getLong("NextMode")) return;
        d.putLong("NextMode", now + 4);
        boolean enabled = rank(d) > 0 && !d.getBoolean("Mode");
        d.putBoolean("Mode", enabled);
        if (!enabled) {
            EsperEffects.endModeEffects(p, d);
        }
        EsperNetworking.sync(p, d, now);
    }

    static boolean cast(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= SLOTS || !canCast(p)) return false;
        CompoundTag d = data(p);
        if (!d.getBoolean("Mode")) return false;
        int index = slot(d, slot);
        if (index < 0 || index >= SKILLS.length) return false;
        Skill skill = SKILLS[index];
        int level = level(d, skill);
        long now = p.serverLevel().getServer().overworld().getGameTime();
        if (school(d) != skill.school || level <= 0 || rank(d) < skill.rank || !prerequisites(d, skill)
                || now < d.getLong("NextCast"))
            return false;
        if (skill.passive()) return false;
        if (skill == Skill.BARRIER && d.contains("Barrier")) {
            EsperEffects.stopBarrier(p, d);
            return true;
        }
        if (skill == Skill.SHIFT && d.contains("Shift")) {
            EsperEffects.stopShift(p, d);
            return true;
        }
        if (now < d.getLong("Ready_" + skill.key)) return false;
        d.putLong("NextCast", now + 4);
        if (d.getInt("Power") < skill.cost) {
            p.displayClientMessage(label("low"), true);
            return false;
        }
        if (d.getInt("Charge") < skill.charge) {
            p.displayClientMessage(label("low_charge"), true);
            return false;
        }
        if (skill == Skill.VOLLEY) return EsperVolley.begin(p, slot, level, now);
        if (skill == Skill.SLASH || skill == Skill.COLLAPSE) {
            if (!EsperChannel.begin(p, slot, skill, level, now)) return false;
            completeCast(d, skill, level, now);
            return true;
        }
        if (!EsperEffects.cast(p, d, skill, level)) {
            p.displayClientMessage(label("no_target"), true);
            return false;
        }
        completeCast(d, skill, level, now);
        return true;
    }

    static boolean canCast(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.isPassenger() && p.containerMenu == p.inventoryMenu;
    }

    static void completeCast(CompoundTag d, Skill skill, int level, long now) {
        d.putInt("Power", d.getInt("Power") - skill.cost);
        if (skill.charge > 0) d.putInt("Charge", d.getInt("Charge") - skill.charge);
        d.putLong("Ready_" + skill.key, now + skill.cooldownAt(level));
        d.putInt("Mastery_" + skill.key, Math.min(9999, mastery(d, skill) + 1));
        d.putInt("RankPractice_" + skill.school, Math.min(9999, rankProgress(d) + 1));
    }

    static void refresh(ServerPlayer p, CompoundTag d) {
        int local = EsperDomain.Ledger.get(p.serverLevel()).local(p);
        int remote = EsperDomain.Ledger.get(p.serverLevel()).remote(p);
        OrbitalFleet orbit = OrbitalFleet.get(p.serverLevel());
        orbit.migrateEsper(p, d);
        var fleet = OrbitalFleet.hasTerminal(p) ? orbit.serviceFleet(p) : null;
        int satellites = fleet != null && fleet.computeEnabled ? fleet.compute : 0;
        d.putInt("Local", local); d.putInt("Remote", remote); d.putInt("Online", satellites);
        d.putInt("Max", 80 + rank(d) * 20 + (local + remote) * 8 + satellites * 18);
        d.putInt("Regen", 3 + rank(d) + (local + remote) + satellites * 2);
        d.putInt("Power", Math.min(d.getInt("Power"), d.getInt("Max")));
    }
    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isAlive()) return;
        EsperDomain.tick(p);
        CompoundTag d = p.getPersistentData().contains(DATA) ? data(p) : p.getPersistentData().getCompound(DATA);
        if (rank(d) == 0) return;
        long now = p.serverLevel().getServer().overworld().getGameTime();
        EsperVolley.tick(p, d, now);
        EsperChannel.tick(p, d, now);
        EsperEffects.tickPlayer(p, d, now);
        if (p.isSpectator() && d.getBoolean("Mode")) {
            d.remove("Mode");
            EsperNetworking.sync(p, d, now);
        }
        if (d.getBoolean("Mode") && p.tickCount % 5 == 0) EsperNetworking.sync(p, d, now);
        if (p.tickCount % 20 != 0) return;
        refresh(p, d);
        if (!d.contains("Shift") && !EsperChannel.active(p))
            d.putInt("Power", Math.min(d.getInt("Max"), d.getInt("Power") + d.getInt("Regen")));
        for (Skill skill : school(d).skills()) if (skill.passive() && level(d, skill) > 0)
            d.putInt("Mastery_" + skill.key, Math.min(9999, mastery(d, skill) + 1));
    }
    private static void clonePlayer(PlayerEvent.Clone event) {
        if (!event.getOriginal().getPersistentData().contains(DATA)) return;
        CompoundTag d = data(event.getOriginal()).copy();
        d.remove("Barrier"); d.remove("BarrierHits"); d.remove("Sling"); d.remove("NextCast");
        EsperEffects.stopShift(event.getEntity(), d);
        if (event.isWasDeath()) { d.remove("DomainReturn"); d.remove("Mode"); }
        event.getEntity().getPersistentData().put(DATA, d);
    }
    private static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) EsperEffects.protect(p, event);
    }

    private static final class Maximizer extends Item {
        Maximizer(Properties props) { super(props.stacksTo(1)); }
        @Override public InteractionResultHolder<ItemStack> use(Level l, Player user, InteractionHand hand) {
            if (!user.isCreative()) return InteractionResultHolder.fail(user.getItemInHand(hand));
            if (user instanceof ServerPlayer p) {
                CompoundTag d = data(p);
                if (school(d) == null) {
                    p.displayClientMessage(label("maximizer_awaken"), true);
                    return InteractionResultHolder.fail(user.getItemInHand(hand));
                }
                d.putInt("Rank_" + school(d), MAX_RANK);
                for (Skill skill : school(d).skills()) d.putInt("Level_" + skill.key, MAX_LEVEL);
                refresh(p, d);
                d.putInt("Power", d.getInt("Max"));
                p.displayClientMessage(label("maximizer_done", school(d).title()), true);
            }
            return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), l.isClientSide);
        }
    }
    private Esper() {}
}
