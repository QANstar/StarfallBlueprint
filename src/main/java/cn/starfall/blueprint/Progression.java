package cn.starfall.blueprint;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Personal progression uses the original knowledge as its only source of truth. */
public final class Progression {
    public static final DeferredHolder<MenuType<?>, MenuType<ProgressionMenu>> MENU = StarfallBlueprint.MENUS.register(
            "progression", () -> new MenuType<>(ProgressionMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public record Cost(String key, int count) {
        public Cost { Objects.requireNonNull(key); if (count <= 0) throw new IllegalArgumentException("Positive progression cost required"); }
    }
    public record Reward(List<Cost> items, int experiencePoints) {
        public static final Reward NONE = new Reward(List.of(), 0);
        public Reward {
            items = List.copyOf(items);
            if (experiencePoints < 0) throw new IllegalArgumentException("Negative progression XP reward");
        }
    }
    /** Additional trees can register nodes during initialization using the same transaction engine. */
    public record Node(String id, String category, Component title, Component description, List<String> prerequisites,
                       List<Cost> costs, int experienceLevels, Supplier<ItemStack> iconFactory,
                       Predicate<Player> learned, Consumer<Player> grant, Reward reward) {
        public Node(String id, String category, Component title, Component description, List<String> prerequisites,
                    List<Cost> costs, int experienceLevels, Supplier<ItemStack> iconFactory,
                    Predicate<Player> learned, Consumer<Player> grant) {
            this(id, category, title, description, prerequisites, costs, experienceLevels, iconFactory, learned, grant, Reward.NONE);
        }
        public Node {
            Objects.requireNonNull(id); Objects.requireNonNull(category); Objects.requireNonNull(title);
            Objects.requireNonNull(description); Objects.requireNonNull(iconFactory);
            Objects.requireNonNull(learned); Objects.requireNonNull(grant); Objects.requireNonNull(reward);
            prerequisites = List.copyOf(prerequisites); costs = List.copyOf(costs);
            if (experienceLevels < 0) throw new IllegalArgumentException("Negative progression XP cost");
        }
        public ItemStack icon() { return iconFactory.get().copy(); }
    }
    public enum Status { READY, ALREADY_LEARNED, PREREQUISITE, EXPERIENCE, MATERIALS, REWARD_SPACE, INVALID }
    public record Preview(Status status, Component reason) {
        public boolean executable() { return status == Status.READY; }
    }
    private static final List<Node> REGISTERED = new ArrayList<>();
    public static final List<Node> NODES = Collections.unmodifiableList(REGISTERED);
    private static final Map<String, Node> BY_ID = new LinkedHashMap<>();
    private static final Map<Integer, String> BEST = Map.of(0,"best:basic",1,"best:drilling",5,"best:servicing",
            8,"best:mobility",13,"best:diagnostics",17,"best:mech_operation");
    static {
        best(0, "steel", 0, List.of(), List.of(new Cost("steel",2),new Cost("board",1)),
                new Reward(List.of(new Cost("iron",4)),5),
                p -> BestEngineering.researchLevel(p)>=1, p -> p.getPersistentData().putInt("BestEngineeringResearch",1));
        best(1, "best_cutter", 1, List.of(BEST.get(0)), List.of(new Cost("load",1),new Cost("conductor",1)),
                new Reward(List.of(new Cost("steel",2)),10),
                p -> BestEngineering.researchLevel(p)>=2, p -> p.getPersistentData().putInt("BestEngineeringResearch",2));
        best(5, "best_service_kit", 2, List.of(BEST.get(1)), List.of(new Cost("load",2),new Cost("conductor",1),new Cost("board",1)),
                new Reward(List.of(new Cost("best_service_kit",1)),20),
                p -> BestEngineering.researchLevel(p)>=3, p -> p.getPersistentData().putInt("BestEngineeringResearch",3));
        best(8, "best_stride_module", 2, List.of(BEST.get(5)), List.of(new Cost("load",2),new Cost("conductor",1),new Cost("board",1)),
                new Reward(List.of(new Cost("steel_fix",2)),15),
                BestEngineering::hasLightMobility, p -> p.getPersistentData().putBoolean("BestLightMobility",true));
        best(13, "best_diagnostic_tool", 2, List.of(BEST.get(5)), List.of(new Cost("load",1),new Cost("conductor",2),new Cost("board",1)),
                new Reward(List.of(new Cost("best_diagnostic_tool",1)),20),
                BestEngineering::hasDiagnostics, p -> p.getPersistentData().putBoolean("BestDiagnostics",true));
        best(17, "best_mech", 5, List.of(BEST.get(5),BEST.get(8),BEST.get(13)),
                List.of(new Cost("load",4),new Cost("conductor",4),new Cost("sensor",2),new Cost("board",2)),
                new Reward(List.of(new Cost("steel",8),new Cost("steel_fix",4)),50),
                BestEngineering::hasMechOperation, p -> p.getPersistentData().putBoolean("BestMechOperation",true));
        for (int i=0;i<Lingchuan.RESEARCH.size();i++) {
            final int index=i;
            var r=Lingchuan.RESEARCH.get(i);
            Set<String> parents=new LinkedHashSet<>();
            if(r.parent()>=0) parents.add(lingchuanId(r.parent()));
            if(r.tier()>0) parents.add(lingchuanId(r.tier()));
            List<Cost> costs=new ArrayList<>();
            if(r.alloy()>0)costs.add(new Cost("laminar_alloy",r.alloy()));
            if(r.crystal()>0)costs.add(new Cost("common",r.crystal()));
            register(new Node(lingchuanId(i),"lingchuan",r.name(),Component.translatable("lc.research_description."+r.key()),
                    List.copyOf(parents),costs,switch(r.tier()){case 0->1;case 1->3;case 2->5;default->8;},
                    ()->CatalogueContent.stack("laminar_alloy",1),p->Lingchuan.knows(p,index),
                    p->p.getPersistentData().putInt("LingchuanResearch",Lingchuan.knowledge(p)|(1<<index)),
                     switch(i) {
                         case 0 -> new Reward(List.of(new Cost("laminar_alloy",1)),10);
                         case 1 -> new Reward(List.of(new Cost("laminar_alloy",2)),20);
                         case 2,8 -> new Reward(List.of(new Cost("conductor",2)),35);
                         case 3 -> new Reward(List.of(new Cost("sheath",4)),60);
                         default -> new Reward(List.of(),switch(r.tier()){case 1->10;case 2->20;default->35;});
                     }));
        }
    }
    private static void best(int action,String icon,int xp,List<String> parents,List<Cost> costs,Reward reward,Predicate<Player> learned,Consumer<Player> grant) {
        register(new Node(BEST.get(action),"best",BestEngineering.actionLabelComponent(action),
                BestEngineering.actionCostComponent(action),parents,costs,xp,()->CatalogueContent.stack(icon,1),learned,grant,reward));
    }
    public static void initialize() {}
    public static synchronized void register(Node node) {
        if(BY_ID.containsKey(node.id()))throw new IllegalArgumentException("Duplicate progression node: "+node.id());
        BY_ID.put(node.id(),node); REGISTERED.add(node);
    }
    public static String bestId(int action) { return BEST.get(action); }
    public static String lingchuanId(int index) {
        return index>=0&&index<Lingchuan.RESEARCH.size()?"lingchuan:"+Lingchuan.RESEARCH.get(index).key():"";
    }
    public static boolean isLearned(Player player,String id) {
        Node node=BY_ID.get(id); return node!=null&&node.learned().test(player);
    }
    public static Set<String> learned(Player player) {
        Set<String> result=new HashSet<>();
        for(Node node:NODES)if(node.learned().test(player))result.add(node.id());
        return result;
    }
    private record Plan(Preview preview,List<ItemStack> inventory) {}
    private static Plan reject(Status status,String key,Object... args) {
        return new Plan(new Preview(status,Component.translatable("growth.starfall_blueprint."+key,args)),List.of());
    }
    public static boolean usableMaterial(ItemStack stack) {
        if(stack.isDamageableItem()&&stack.getDamageValue()>=stack.getMaxDamage())return false;
        MaterialBatch batch=stack.get(StarfallBlueprint.BATCH.get());
        return MaterialQuality.of(stack).contamination()==0&&(batch==null||batch.contamination()==0);
    }
    private static Plan plan(Inventory inventory,String id,Set<String> learned,int xp) {
        Node node=BY_ID.get(id);
        if(node==null)return reject(Status.INVALID,"invalid");
        if(learned.contains(id))return reject(Status.ALREADY_LEARNED,"already_learned");
        if(!learned.containsAll(node.prerequisites()))return reject(Status.PREREQUISITE,"prerequisite");
        if(xp<node.experienceLevels())return reject(Status.EXPERIENCE,"experience",node.experienceLevels());
        List<ItemStack> copies=new ArrayList<>();
        for(int slot=0;slot<36;slot++)copies.add(inventory.getItem(slot).copy());
        for(Cost cost:node.costs()) {
            int remaining=cost.count();
            for(ItemStack stack:copies)if(CatalogueContent.matches(cost.key(),stack)&&usableMaterial(stack)) {
                int taken=Math.min(remaining,stack.getCount());stack.shrink(taken);remaining-=taken;
                if(remaining==0)break;
            }
            if(remaining>0)return reject(Status.MATERIALS,"materials");
        }
        for(Cost reward:node.reward().items()) {
            ItemStack output=CatalogueContent.stack(reward.key(),reward.count());
            for(ItemStack existing:copies) {
                if(!existing.isEmpty()&&ItemStack.isSameItemSameComponents(existing,output)) {
                    int moved=Math.min(output.getCount(),Math.max(0,Math.min(inventory.getMaxStackSize(),existing.getMaxStackSize())-existing.getCount()));
                    existing.grow(moved);output.shrink(moved);
                    if(output.isEmpty())break;
                }
            }
            for(int slot=0;slot<36&&!output.isEmpty();slot++)if(copies.get(slot).isEmpty()) {
                int moved=Math.min(output.getCount(),Math.min(inventory.getMaxStackSize(),output.getMaxStackSize()));
                copies.set(slot,output.copyWithCount(moved));output.shrink(moved);
            }
            if(!output.isEmpty())return reject(Status.REWARD_SPACE,"reward_space");
        }
        return new Plan(new Preview(Status.READY,Component.translatable("growth.starfall_blueprint.ready",node.experienceLevels())),copies);
    }
    public static Preview preview(Inventory inventory,String id,Set<String> learned,int xp) {
        Player player=inventory.player;
        if(!player.isAlive()||player.isSpectator())return reject(Status.INVALID,"unavailable").preview();
        return plan(inventory,id,learned,xp).preview();
    }
    /** Shared by menus and legacy entry points. Never accepts client prices or knowledge. */
    public static boolean learn(Player player,String id) {
        if(player.level().isClientSide||!player.isAlive()||player.isSpectator())return false;
        Plan transaction=plan(player.getInventory(),id,learned(player),player.experienceLevel);
        if(!transaction.preview().executable()) {
            player.displayClientMessage(transaction.preview().reason(),true);return false;
        }
        Node node=BY_ID.get(id);
        for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,transaction.inventory().get(slot));
        player.giveExperienceLevels(-node.experienceLevels());
        node.grant().accept(player);
        player.giveExperiencePoints(node.reward().experiencePoints());
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("growth.starfall_blueprint.learned",node.title()),true);
        return true;
    }
    public static void open(Player player) {
        if(!(player instanceof ServerPlayer server)||!player.isAlive()||player.isSpectator())return;
        if(player.containerMenu instanceof ProgressionMenu)return;
        if(player.containerMenu!=player.inventoryMenu)return;
        server.openMenu(new SimpleMenuProvider((id,inventory,owner)->new ProgressionMenu(id,inventory),Component.translatable("growth.starfall_blueprint.title")));
    }
    private Progression() {}
}
