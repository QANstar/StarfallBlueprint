package cn.starfall.blueprint;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

public final class OrbitalTerminalItem extends Item {
    private final boolean creativeOnly;
    public OrbitalTerminalItem(Properties p){this(p,false);}
    public OrbitalTerminalItem(Properties p,boolean creativeOnly){super(p.stacksTo(1));this.creativeOnly=creativeOnly;}
    public static boolean usable(ItemStack s,Player p){return s.getItem() instanceof OrbitalTerminalItem t&&(!t.creativeOnly||p.isCreative());}
    public static boolean creativeAccess(Player p){
        if(!p.isCreative())return false;
        for(ItemStack s:p.getInventory().items)if(s.getItem() instanceof OrbitalTerminalItem t&&t.creativeOnly)return true;
        return p.getOffhandItem().getItem() instanceof OrbitalTerminalItem t&&t.creativeOnly;
    }
    public static ItemStack find(Player p){
        if(usable(p.getMainHandItem(),p))return p.getMainHandItem();if(usable(p.getOffhandItem(),p))return p.getOffhandItem();
        for(ItemStack s:p.getInventory().items)if(usable(s,p))return s;return ItemStack.EMPTY;
    }
    @Override public InteractionResult useOn(UseOnContext c){
        var b=OrbitalBlock.controller(c.getLevel(),c.getClickedPos());if(b==null||b.kind()!=0)return InteractionResult.PASS;
        var p=c.getPlayer();if(p==null||!usable(c.getItemInHand(),p))return InteractionResult.FAIL;
        if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;if(!Haijia.knows(p,"power")||!c.getLevel().mayInteract(p,c.getClickedPos()))return InteractionResult.FAIL;
        var t=CatalogueEquipment.getData(c.getItemInHand());
        if(p.isShiftKeyDown()){
            for(BlockPos pos:b.links)if(c.getLevel().hasChunkAt(pos)&&c.getLevel().getBlockEntity(pos) instanceof OrbitalBlockEntity target){target.links.remove(b.getBlockPos());target.setChanged();}
            b.links.clear();b.setChanged();t.remove("RelayPos");CatalogueEquipment.setData(c.getItemInHand(),t);p.displayClientMessage(Haijia.text("relay.cleared"),true);return InteractionResult.CONSUME;
        }
        String dim=c.getLevel().dimension().location().toString();
        if(t.contains("RelayPos")&&dim.equals(t.getString("RelayDim"))){BlockPos pos=BlockPos.of(t.getLong("RelayPos"));if(c.getLevel().hasChunkAt(pos)&&c.getLevel().getBlockEntity(pos) instanceof OrbitalBlockEntity first&&first.link(b)){t.remove("RelayPos");p.displayClientMessage(Haijia.text("relay.linked"),true);}else{p.displayClientMessage(Haijia.text("relay.invalid"),true);t.remove("RelayPos");}}
        else{t.putLong("RelayPos",b.getBlockPos().asLong());t.putString("RelayDim",dim);p.displayClientMessage(Haijia.text("relay.selected"),true);}
        CatalogueEquipment.setData(c.getItemInHand(),t);return InteractionResult.CONSUME;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack s=player.getItemInHand(hand);if(!usable(s,player))return InteractionResultHolder.fail(s);if(level.isClientSide)return InteractionResultHolder.success(s);
        if(!(player instanceof ServerPlayer p)||p.isSpectator())return InteractionResultHolder.fail(s);
        int mode=Math.clamp(CatalogueEquipment.getData(s).getInt("OrbitalMode"),0,4);
        if(p.isShiftKeyDown()||mode==0){p.openMenu(new SimpleMenuProvider((id,inv,who)->new OrbitalMenu(id,inv,ContainerLevelAccess.NULL,4),Haijia.text("terminal")));}
        else if(mode==3)weather(p,0);else if(mode==4)weather(p,1);else strike(p,mode==1?0:1);
        return InteractionResultHolder.consume(s);
    }
    public static boolean weather(ServerPlayer p,int mode){
        var l=p.serverLevel();var d=OrbitalFleet.get(l);var f=d.serviceFleet(p);long now=l.getGameTime();
        if(f.count[3]>0)f.active[3]=true;
        if(!Haijia.knows(p,"weather")||f.count[3]==0||!f.active[3]||f.energy<400000||now<f.weatherReady||l.dimension()!=Level.OVERWORLD){p.displayClientMessage(Haijia.text("weather.blocked"),true);return false;}
        f.energy-=400000;f.weatherReady=now+2400;l.setWeatherParameters(mode==0?12000:0,mode==0?0:12000,mode!=0,mode==2);d.setDirty();p.displayClientMessage(Haijia.text("weather.changed"),true);return true;
    }
    public static boolean strike(ServerPlayer p,int style){
        var l=p.serverLevel();var d=OrbitalFleet.get(l);var f=d.serviceFleet(p);int type=style==0?1:2;long now=l.getGameTime();int cost=style==0?250000:600000;
        var hit=l.clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(192)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));var pos=hit.getBlockPos();
        if(!Haijia.knows(p,OrbitalFleet.TYPES[type])||f.count[type]==0||!f.active[type]||f.energy<cost||now<(style==0?f.laserReady:f.rodReady)||style==1&&f.rods==0||hit.getType()==HitResult.Type.MISS||!l.hasChunkAt(pos)||!l.getWorldBorder().isWithinBounds(pos)||l.dimension()!=Level.OVERWORLD||!l.mayInteract(p,pos)){
            p.displayClientMessage(Haijia.text("strike.blocked"),true);return false;
        }
        var entity=Haijia.STRIKE.get().create(l);if(entity==null)return false;entity.setPos(hit.getLocation());entity.configure(style,p.getUUID(),style==0?60:100);entity.strength(f.strikeDamage(type),f.strikeRadius(type),f.strikeBlast(type));
        if(!l.addFreshEntity(entity))return false;f.energy-=cost;if(style==0)f.laserReady=now+200;else{f.rods--;f.rodReady=now+600;}d.setDirty();p.displayClientMessage(Haijia.text("strike.marked",style==0?3:5),true);return true;
    }
}
