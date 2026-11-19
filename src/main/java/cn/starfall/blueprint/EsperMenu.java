package cn.starfall.blueprint;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class EsperMenu extends AbstractContainerMenu {
    private static final int COUNT = Esper.SKILLS.length;
    public static final int PROMOTE = 28, EQUIP = 32, CLEAR = 96;
    private final Inventory inventory;
    private final ContainerLevelAccess access;
    private final int[] info = new int[11 + 2 * COUNT + Esper.SLOTS];

    public EsperMenu(int id, Inventory inv) { this(id, inv, ContainerLevelAccess.NULL); }
    EsperMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(Esper.MENU.get(), id);
        this.inventory = inv;
        this.access = access;
        for (int i = 0; i < info.length; i++) {
            final int index = i;
            addDataSlot(new DataSlot() {
                @Override public int get() { return info[index]; }
                @Override public void set(int value) { info[index] = value; }
            });
        }
        snapshot();
    }
    private void snapshot() {
        if (!(inventory.player instanceof ServerPlayer p)) return;
        var d = Esper.data(p);
        info[0] = Esper.rank(d); info[1] = Esper.rankProgress(d); info[2] = Math.min(32767, p.experienceLevel);
        info[3] = d.getInt("Power"); info[4] = d.getInt("Max"); info[5] = p.isCreative() ? 1 : 0;
        info[6] = d.getInt("Online"); info[7] = d.getInt("Local") + d.getInt("Remote");
        for (int i = 0; i < COUNT; i++) {
            info[8 + i] = Esper.level(d, Esper.SKILLS[i]);
            info[8 + COUNT + i] = Esper.mastery(d, Esper.SKILLS[i]);
        }
        for (int i = 0; i < Esper.SLOTS; i++) info[8 + COUNT * 2 + i] = Esper.slot(d, i) + 1;
        info[info.length - 3] = Esper.school(d) == null ? 0 : Esper.school(d).ordinal() + 1;
        info[info.length - 2] = d.getInt("Charge");
        var machine = machine();
        info[info.length - 1] = machine == null ? 0 : machine.energy.getEnergyStored() / 1000;
    }
    public int rank() { return info[0]; }
    public int practice() { return info[1]; }
    public int xp() { return info[2]; }
    public int power() { return info[3]; }
    public int capacity() { return info[4]; }
    public int satellites() { return info[6]; }
    public int units() { return info[7]; }
    public Esper.School school() {
        int id = info[info.length - 3];
        return id <= 0 ? null : Esper.School.values()[id - 1];
    }
    public int charge() { return info[info.length - 2]; }
    public int energy() { return info[info.length - 1]; }
    public int level(int index) { return info[8 + index]; }
    public int mastery(int index) { return info[8 + COUNT + index]; }
    public int slot(int index) { return info[8 + COUNT * 2 + index] - 1; }
    public int practiceCost(int index) { return Esper.practiceNeeded(level(index)); }
    public int trainCost(int index) { return rank() == 0 || info[5] == 1 ? 0 : Esper.price(Esper.SKILLS[index], level(index)); }
    public int trainEnergy(int index) {
        return info[5] == 1 ? 0 : Esper.trainingEnergy(Esper.SKILLS[index], level(index), school() == null) / 1000;
    }
    public int rankEnergy() { return info[5] == 1 ? 0 : Esper.promotionEnergy(rank()) / 1000; }
    public int rankCost() { return info[5] == 1 ? 0 : Esper.rankPrice(rank()); }
    public int rankPractice() { return Esper.rankPractice(rank()); }
    public boolean canPromote() {
        return rank() > 0 && rank() < Esper.MAX_RANK && practice() >= rankPractice()
                && xp() >= rankCost() && energy() >= rankEnergy();
    }
    public boolean canTrain(int index) {
        int level = level(index), rank = rank(), required = Esper.SKILLS[index].rank;
        Esper.Skill skill = Esper.SKILLS[index];
        return energy() >= trainEnergy(index) && (school() == null ? skill.school.root() == skill : skill.school == school()
                && skill.prerequisites().stream().allMatch(node -> level(node.ordinal()) > 0)
                && required <= rank && level < Math.min(rank, Esper.MAX_LEVEL)
                && mastery(index) >= practiceCost(index) && xp() >= trainCost(index));
    }
    private EsperFacility.Controller machine() { return access.evaluate(EsperFacility::controller, null); }
    @Override public void broadcastChanges() { snapshot(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return player == inventory.player && player.isAlive() && !player.isSpectator()
                && stillValid(access, player, Esper.AWAKENER.get())
                && access.evaluate((level, pos) -> {
                    var machine = EsperFacility.controller(level, pos);
                    return machine != null && EsperFacility.complete(machine) && level.mayInteract(player, pos)
                            && level.mayInteract(player, machine.getBlockPos());
                }, true);
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer p) || p.containerMenu != this || !stillValid(p)) return false;
        boolean success;
        if (id >= 0 && id < COUNT) success = Esper.train(p, Esper.SKILLS[id], machine());
        else if (id == PROMOTE) success = Esper.promote(p, machine());
        else if (id >= EQUIP && id < CLEAR && Esper.school(Esper.data(p)) != null) {
            var skills = Esper.school(Esper.data(p)).skills();
            int index = (id - EQUIP) / Esper.SLOTS;
            success = index < skills.size() && Esper.equip(p, skills.get(index).ordinal(), (id - EQUIP) % Esper.SLOTS);
        }
        else if (id >= CLEAR && id < CLEAR + Esper.SLOTS) success = Esper.equip(p, -1, id - CLEAR);
        else return false;
        if (success) broadcastChanges();
        return success;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
