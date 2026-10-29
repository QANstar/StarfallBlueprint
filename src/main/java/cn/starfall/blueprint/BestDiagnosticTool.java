package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/** Reusable read-only inspection. No target state or inventory is changed by inspection. */
public final class BestDiagnosticTool extends Item {
    public BestDiagnosticTool(Properties properties){super(properties.stacksTo(1).attributes(ItemAttributeModifiers.EMPTY));}
    private static Component state(boolean value){return Component.translatable("message.starfall_blueprint.best_state_"+(value?"yes":"no"));}
    /** All faults are reported independently, including thermal latch and battery isolation. */
    public static Component report(Player player,ItemStack target){
        if(!BestEquipment.isEquipment(target))return Component.translatable("message.starfall_blueprint.best_diagnostic_target");
        boolean frame=target.getItem() instanceof BestEquipment.Frame;
        var result=Component.translatable("message.starfall_blueprint.best_diagnostic_report",
                target.getHoverName(),BestEquipment.charge(target),BestEquipment.CAPACITY,
                BestEquipment.heat(target),BestEquipment.MAX_HEAT,BestEquipment.RECOVER_HEAT,
                state(CatalogueEquipment.getData(target).getBoolean("BestOverheated")),
                BestEquipment.MAX_WEAR-BestEquipment.wear(target),BestEquipment.MAX_WEAR,
                state(BestEquipment.isIsolated(target)),state(BestEngineering.researchLevel(player)>=(frame?1:2)));
        if(frame)result.append("\n").append(Component.translatable("message.starfall_blueprint.best_diagnostic_frame",
                state(BestEquipment.strideInstalled(target)),state(BestEquipment.strideEnabled(target)),
                state(BestEngineering.researchLevel(player)>=3&&BestEngineering.hasLightMobility(player))));
        return result;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack tool=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.fail(tool);
        if(level.isClientSide)return InteractionResultHolder.sidedSuccess(tool,true);
        if(BestEngineering.researchLevel(player)<3||!BestEngineering.hasDiagnostics(player)){
            player.displayClientMessage(Component.translatable("message.starfall_blueprint.best_diagnostics_required"),true);
            return InteractionResultHolder.fail(tool);
        }
        ItemStack target=player.getOffhandItem();
        if(!BestEquipment.isEquipment(target)){
            player.displayClientMessage(Component.translatable("message.starfall_blueprint.best_diagnostic_target"),true);
            return InteractionResultHolder.fail(tool);
        }
        player.displayClientMessage(report(player,target),false);
        return InteractionResultHolder.sidedSuccess(tool,false);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){
        lines.add(Component.translatable("message.starfall_blueprint.best_diagnostic_hint"));
    }
}
