package cn.starfall.blueprint;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class Esper {
    static final String DATA = "StarfallEsper";
    public static final int MAX_RANK = 7, MAX_LEVEL = 5;
    static final int MAX_SATELLITES = 512;
    public enum School {
        SPACE, ELECTRIC;
        public Component title() { return label("school." + name().toLowerCase(Locale.ROOT)); }
        public Skill root() { return this == SPACE ? Skill.BLINK : Skill.INDUCTION; }
        public java.util.List<Skill> skills() { return java.util.Arrays.stream(SKILLS).filter(s -> s.school == this).toList(); }
    }
    public enum Skill {
        BLINK(1, 20, 20, 0, 1), PORTAL(3, 45, 60, 2, 0), SLASH(2, 22, 24, 1, 1),
        STORAGE(2, 0, 60, 1, 2), COLLAPSE(5, 58, 80, 4, 2), VOLLEY(3, 34, 30, 2, 2),
        SLING(2, 20, 24, 1, 0), SHIFT(4, 22, 12, 3, 0), PULL(3, 28, 32, 2, 1),
        DOMAIN(5, 65, 50, 4, 0), BARRIER(5, 50, 100, 4, 1),
        ARC(3, 25, 32, 2, 0, 15), LOCK(4, 32, 75, 3, 1),
        INDUCTION(1, 12, 100, 0, 1, 0), SPARK(1, 17, 18, 1, 0, 7),
        BOOST(2, 18, 40, 1, 1, 8), FEED(2, 15, 50, 1, 2, 10),
        INSULATE(3, 27, 90, 2, 1, 13), SCAN(3, 16, 100, 2, 2, 8),
        PARALYZE(4, 32, 85, 3, 0, 20), PULSE(4, 41, 95, 3, 1, 24),
        OVERDRIVE(4, 32, 130, 3, 2, 18), STORM(5, 58, 160, 4, 0, 36),
        SURGE(5, 50, 145, 4, 2, 30), LANCE(4, 36, 55, 3, 3, 23),
        IONIZE(3, 21, 70, 2, 3, 12);

        public final int rank, cost, cooldown, column, row, charge;
        public final School school;
        public final String key;
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
                case SLING, SLASH, STORAGE -> java.util.List.of(BLINK);
                case PORTAL -> java.util.List.of(SLING);
                case PULL -> java.util.List.of(SLASH);
                case VOLLEY -> java.util.List.of(STORAGE);
                case SHIFT -> java.util.List.of(PORTAL);
                case LOCK -> java.util.List.of(PULL);
                case DOMAIN -> java.util.List.of(SHIFT, STORAGE);
                case BARRIER -> java.util.List.of(LOCK);
                case COLLAPSE -> java.util.List.of(VOLLEY, LOCK);
                case SPARK, BOOST, FEED -> java.util.List.of(INDUCTION);
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
            };
        }
        public Component title() { return label("skill." + key); }
    }
    public static final Skill[] SKILLS = Skill.values();
    public static final int SLOTS = 4;

    public static final net.neoforged.neoforge.registries.DeferredBlock<Facility> AWAKENER = facility("esper_awakener", 0);
    public static final net.neoforged.neoforge.registries.DeferredBlock<Facility> COMPUTE = facility("esper_compute_unit", 1);
    public static final net.neoforged.neoforge.registries.DeferredBlock<Facility> LAUNCHER = facility("esper_launcher", 2);
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SATELLITE =
            StarfallBlueprint.ITEMS.registerSimpleItem("esper_satellite");
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> TERMINAL =
            StarfallBlueprint.ITEMS.register("esper_terminal", () -> new Terminal(new Item.Properties().stacksTo(1)));
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> RETUNER =
            StarfallBlueprint.ITEMS.registerSimpleItem("esper_retuner");
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
                    out.accept(AWAKENER.get()); out.accept(COMPUTE.get()); out.accept(LAUNCHER.get());
                    out.accept(SATELLITE.get()); out.accept(TERMINAL.get()); out.accept(RETUNER.get());
                }).build());
    }

    private static net.neoforged.neoforge.registries.DeferredBlock<Facility> facility(String name, int kind) {
        var block = StarfallBlueprint.BLOCKS.register(name, () -> new Facility(kind,
                BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops().lightLevel(s -> kind == 0 ? 10 : 6)));
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
    static int practiceNeeded(int level) { return 5 * level; }
    static int rankPractice(int rank) { return rank * 10; }
    static int price(int level) { return level == 0 ? 2 : 2 + 2 * level; }
    static int rankPrice(int rank) { return 3 * rank; }

    static boolean train(ServerPlayer player, Skill skill) {
        CompoundTag d = data(player);
        int rank = rank(d), level = level(d, skill);
        boolean awakening = school(d) == null;
        if (awakening ? skill != skill.school.root() : (skill.school != school(d) || skill.rank > rank || level >= MAX_LEVEL
                || level >= rank || !prerequisites(d, skill) || mastery(d, skill) < practiceNeeded(level))) return false;
        int cost = awakening ? 0 : price(level);
        if (!pay(player, cost)) return false;
        d.putInt("Level_" + skill.key, level + 1);
        d.putInt("Mastery_" + skill.key, mastery(d, skill) - practiceNeeded(level));
        if (awakening) {
            activate(d, skill.school);
            refresh(player, d);
            d.putInt("Power", d.getInt("Max"));
        }
        return true;
    }
    private static void activate(CompoundTag data, School school) {
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
                || !stack.is(RETUNER.get()) || !p.level().getBlockState(pos).is(AWAKENER.get())) return false;
        stack.consume(1, p);
        EsperEffects.endSchoolEffects(p, d);
        activate(d, from == School.SPACE ? School.ELECTRIC : School.SPACE);
        refresh(p, d);
        p.displayClientMessage(label("retuned", school(d).title()), true);
        return true;
    }
    static boolean promote(ServerPlayer player) {
        CompoundTag d = data(player);
        int rank = rank(d);
        if (rank < 1 || rank >= MAX_RANK || rankProgress(d) < rankPractice(rank)
                || !pay(player, rankPrice(rank))) return false;
        d.putInt("Rank_" + school(d), rank + 1);
        d.putInt("RankPractice_" + school(d), rankProgress(d) - rankPractice(rank));
        refresh(player, d);
        return true;
    }
    static boolean equip(ServerPlayer player, int index, int slot) {
        if (slot < 0 || slot >= SLOTS || index < -1 || index >= SKILLS.length) return false;
        CompoundTag d = data(player);
        School school = school(d);
        if (school == null || (index >= 0 && (SKILLS[index].school != school || level(d, SKILLS[index]) == 0))) return false;
        for (int i = 0; i < SLOTS; i++)
            if (index >= 0 && slot(d, i) == index) d.remove("Slot" + i + "_" + school);
        d.putInt("Slot" + slot + "_" + school, index + 1);
        return true;
    }
    private static boolean pay(ServerPlayer p, int xp) {
        if (p.isCreative()) return true;
        if (p.experienceLevel < xp) return false;
        if (xp > 0) p.giveExperienceLevels(-xp);
        return true;
    }

    public record Cast(int slot) implements CustomPacketPayload {
        static final Type<Cast> TYPE = new Type<>(StarfallBlueprint.id("esper_cast"));
        static final StreamCodec<RegistryFriendlyByteBuf, Cast> CODEC =
                StreamCodec.of((buf, msg) -> buf.writeByte(msg.slot), buf -> new Cast(buf.readByte()));
        @Override public Type<Cast> type() { return TYPE; }
    }
    public static void register(IEventBus bus) {
        PROJECTILES.register(bus);
        bus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("1").playToServer(Cast.TYPE, Cast.CODEC, (msg, ctx) ->
                        ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer p) cast(p, msg.slot); })));
        NeoForge.EVENT_BUS.addListener(Esper::tick);
        NeoForge.EVENT_BUS.addListener(Esper::clonePlayer);
        NeoForge.EVENT_BUS.addListener(Esper::damage);
        NeoForge.EVENT_BUS.addListener(EsperEffects::tickPortals);
        NeoForge.EVENT_BUS.addListener(EsperEffects::tickBinding);
        NeoForge.EVENT_BUS.addListener(EsperElectric::tickFields);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, EsperEffects::blockDrops);
        NeoForge.EVENT_BUS.addListener(EsperDomain::guardTravel);
        NeoForge.EVENT_BUS.addListener(EsperDomain::commands);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.getPersistentData().contains(DATA))
                EsperEffects.stopShift(p, data(p));
        });
    }

    static boolean cast(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= SLOTS || p.isSpectator() || !p.isAlive() || p.isSleeping()
                || p.isPassenger() || p.containerMenu != p.inventoryMenu) return false;
        CompoundTag d = data(p);
        int index = slot(d, slot);
        if (index < 0 || index >= SKILLS.length) return false;
        Skill skill = SKILLS[index];
        int level = level(d, skill);
        long now = p.serverLevel().getServer().overworld().getGameTime();
        if (school(d) != skill.school || level <= 0 || rank(d) < skill.rank || !prerequisites(d, skill)
                || now < d.getLong("NextCast") || now < d.getLong("Ready_" + skill.key))
            return false;
        d.putLong("NextCast", now + 4);
        if (skill == Skill.SHIFT && d.contains("Shift")) {
            EsperEffects.stopShift(p, d);
            return true;
        }
        if (d.getInt("Power") < skill.cost) {
            p.displayClientMessage(label("low"), true);
            return false;
        }
        if (d.getInt("Charge") < skill.charge) {
            p.displayClientMessage(label("low_charge"), true);
            return false;
        }
        if (!EsperEffects.cast(p, d, skill, level)) {
            p.displayClientMessage(label("no_target"), true);
            return false;
        }
        d.putInt("Power", d.getInt("Power") - skill.cost);
        if (skill.charge > 0) d.putInt("Charge", d.getInt("Charge") - skill.charge);
        d.putLong("Ready_" + skill.key, now + skill.cooldown);
        d.putInt("Mastery_" + skill.key, Math.min(9999, mastery(d, skill) + 1));
        d.putInt("RankPractice_" + skill.school, Math.min(9999, rankProgress(d) + 1));
        p.displayClientMessage(label("cast", skill.title(), d.getInt("Power"), d.getInt("Max")), true);
        return true;
    }

    static void refresh(ServerPlayer p, CompoundTag d) {
        int local = EsperDomain.Ledger.get(p.serverLevel()).local(p);
        int remote = EsperDomain.Ledger.get(p.serverLevel()).remote(p);
        int satellites = d.getBoolean("Uplink") && hasTerminal(p) ? d.getInt("Satellites") : 0;
        d.putInt("Local", local); d.putInt("Remote", remote); d.putInt("Online", satellites);
        d.putInt("Max", 80 + rank(d) * 20 + (local + remote) * 8 + satellites * 18);
        d.putInt("Regen", 3 + rank(d) + (local + remote) + satellites * 2);
        d.putInt("Power", Math.min(d.getInt("Power"), d.getInt("Max")));
    }
    private static boolean hasTerminal(ServerPlayer p) {
        return p.getOffhandItem().is(TERMINAL.get()) || p.getInventory().items.stream().anyMatch(s -> s.is(TERMINAL.get()));
    }
    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isAlive()) return;
        EsperDomain.tick(p);
        CompoundTag d = p.getPersistentData().contains(DATA) ? data(p) : p.getPersistentData().getCompound(DATA);
        if (rank(d) == 0) return;
        long now = p.serverLevel().getServer().overworld().getGameTime();
        EsperEffects.tickPlayer(p, d, now);
        if (p.tickCount % 20 != 0) return;
        refresh(p, d);
        d.putInt("Power", Math.min(d.getInt("Max"), d.getInt("Power") + d.getInt("Regen")));
        if (school(d) == School.ELECTRIC && d.getInt("Charge") < 18) d.putInt("Charge", d.getInt("Charge") + 1);
    }
    private static void clonePlayer(PlayerEvent.Clone event) {
        if (!event.getOriginal().getPersistentData().contains(DATA)) return;
        CompoundTag d = data(event.getOriginal()).copy();
        d.remove("Barrier"); d.remove("Sling"); d.remove("NextCast"); d.remove("Shift");
        if (d.contains("ShiftGranted")) EsperEffects.stopShift(event.getEntity(), d);
        if (event.isWasDeath()) d.remove("DomainReturn");
        event.getEntity().getPersistentData().put(DATA, d);
    }
    private static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) EsperEffects.protect(p, event);
    }

    static boolean launch(ServerPlayer p, BlockPos pos, ItemStack stack) {
        CompoundTag d = data(p);
        if (p.isSpectator() || rank(d) == 0 || d.getInt("Satellites") >= MAX_SATELLITES
                || !stack.is(SATELLITE.get()) || !p.serverLevel().dimensionType().hasSkyLight()
                || !p.serverLevel().canSeeSky(pos.above())) return false;
        stack.consume(1, p);
        d.putInt("Satellites", d.getInt("Satellites") + 1);
        refresh(p, d);
        EsperEffects.launch(p.serverLevel(), pos);
        p.displayClientMessage(label("launch", d.getInt("Satellites")), true);
        return true;
    }
    public static final class Facility extends Block {
        private final int kind;
        Facility(int kind, Properties props) { super(props); this.kind = kind; }
        @Override protected void onPlace(BlockState s, Level l, BlockPos pos, BlockState old, boolean moving) {
            super.onPlace(s, l, pos, old, moving);
            if (kind == 1 && l instanceof net.minecraft.server.level.ServerLevel server && !old.is(this))
                EsperDomain.Ledger.get(server).unit(server, pos, true);
        }
        @Override protected void onRemove(BlockState s, Level l, BlockPos pos, BlockState next, boolean moving) {
            super.onRemove(s, l, pos, next, moving);
            if (kind == 1 && l instanceof net.minecraft.server.level.ServerLevel server && !next.is(this))
                EsperDomain.Ledger.get(server).unit(server, pos, false);
        }
        @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos pos, Player player, BlockHitResult hit) {
            if (player instanceof ServerPlayer p && !p.isSpectator()) {
                if (kind == 0) p.openMenu(new SimpleMenuProvider(
                        (id, inv, who) -> new EsperMenu(id, inv, ContainerLevelAccess.create(l, pos)), getName()));
                else if (kind == 1) p.displayClientMessage(label("local", EsperDomain.Ledger.get(p.serverLevel()).local(p)), true);
                else p.displayClientMessage(label("launcher_hint"), true);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState s, Level l, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit) {
            if (kind == 0 && stack.is(RETUNER.get())) {
                if (player instanceof ServerPlayer p && !retune(p, pos, stack)) p.displayClientMessage(label("retune_failed"), true);
                return ItemInteractionResult.sidedSuccess(l.isClientSide);
            }
            if (kind != 2 || !stack.is(SATELLITE.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (player instanceof ServerPlayer p && !launch(p, pos, stack)) p.displayClientMessage(label("launch_failed"), true);
            return ItemInteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    private static final class Terminal extends Item {
        Terminal(Properties props) { super(props); }
        @Override public InteractionResultHolder<ItemStack> use(Level l, Player user, InteractionHand hand) {
            if (user instanceof ServerPlayer p && !p.isSpectator()) {
                CompoundTag d = data(p);
                if (p.isShiftKeyDown()) d.putBoolean("Uplink", !d.getBoolean("Uplink"));
                if (rank(d) > 0) refresh(p, d);
                p.displayClientMessage(label(d.getBoolean("Uplink") ? "online" : "offline",
                        d.getInt("Satellites"), d.getInt("Power"), d.getInt("Max"), d.getInt("Regen")), true);
                l.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .6F, 1.3F);
            }
            return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), l.isClientSide);
        }
    }
    private Esper() {}
}
