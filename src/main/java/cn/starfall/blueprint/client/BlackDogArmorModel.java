package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;

/** Articulated hard-surface armour: geometry follows the humanoid bones, not a flat skin overlay. */
public final class BlackDogArmorModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(StarfallBlueprint.id("blackdog_exosuit"),"main");
    private final ModelPart torso,belt,flight,cloak,shield,heavy,laser,cannon,leftMissile,rightMissile,leftTurret,rightTurret,crown;
    public BlackDogArmorModel(ModelPart root){
        super(root);torso=body.getChild("torso");belt=body.getChild("belt");flight=body.getChild("flight");cloak=body.getChild("cloak");shield=body.getChild("shield");heavy=body.getChild("heavy");
        laser=rightArm.getChild("laser");cannon=rightArm.getChild("cannon");leftMissile=leftArm.getChild("missile");rightMissile=rightArm.getChild("missile");leftTurret=leftArm.getChild("turret");rightTurret=rightArm.getChild("turret");crown=head.getChild("crown");
    }
    private static CubeListBuilder box(int u,float x,float y,float z,float w,float h,float d){return CubeListBuilder.create().texOffs(u,0).addBox(x,y,z,w,h,d);}
    private static CubeListBuilder face(float x,float y,float z,float w,float h,float d){return CubeListBuilder.create().texOffs(0,64).addBox(x,y,z,w,h,d);}
    private static void part(PartDefinition parent,String name,int u,float x,float y,float z,float w,float h,float d){parent.addOrReplaceChild(name,box(u,x,y,z,w,h,d),PartPose.ZERO);}
    public static LayerDefinition layer(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        var head=root.addOrReplaceChild("head",face(-4.25f,-8.5f,-4.25f,8.5f,8.6f,8.5f),PartPose.ZERO);
        root.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        head.addOrReplaceChild("forehead",face(-3.6f,-1.1f,-.3f,7.2f,2.2f,.65f),PartPose.offsetAndRotation(0,-7.1f,-4.45f,-.15f,0,0));
        for(int sign:new int[]{-1,1}){
            head.addOrReplaceChild("faceplate"+sign,face(-1.55f,-2,-.28f,3.1f,4.5f,.6f),PartPose.offsetAndRotation(sign*1.7f,-3.55f,-4.5f,0,sign*.12f,-sign*.08f));
            head.addOrReplaceChild("eye"+sign,box(64,-1.4f,-.16f,-.16f,2.8f,.32f,.4f),PartPose.offsetAndRotation(sign*1.85f,-5.5f,-4.9f,0,0,sign*.12f));
            part(head,"temple"+sign,0,sign<0?-4.55f:4.15f,-6.1f,-2.5f,.4f,4.5f,4.8f);
            part(head,"temple_light"+sign,64,sign<0?-4.65f:4.55f,-5,-1.4f,.12f,1.7f,.3f);
        }
        part(head,"chin",0,-2.3f,-.9f,-4.45f,4.6f,1,.6f);
        part(head,"crown",64,-.3f,-8.65f,-1.4f,.6f,.2f,2.8f);
        var body=root.addOrReplaceChild("body",CubeListBuilder.create(),PartPose.ZERO);
        var torso=body.addOrReplaceChild("torso",box(0,-4.5f,.3f,-2.7f,9,10,5.4f),PartPose.ZERO);
        for(int sign:new int[]{-1,1}){
            torso.addOrReplaceChild("pectoral"+sign,box(0,-1.9f,-1.65f,-.5f,3.8f,3.8f,1.1f),PartPose.offsetAndRotation(sign*2.15f,2.8f,-3.15f,0,sign*.06f,-sign*.12f));
            torso.addOrReplaceChild("collar_light"+sign,box(64,-1.7f,-.15f,-.13f,3.4f,.3f,.3f),PartPose.offsetAndRotation(sign*2.2f,1.3f,-3.8f,0,0,sign*.23f));
            part(torso,"rib"+sign,0,sign<0?-4.4f:3.15f,5.1f,-2.95f,1.25f,4.5f,.65f);
            part(torso,"rib_light"+sign,64,sign<0?-3.8f:3.5f,6.5f,-3.65f,.3f,2.4f,.25f);
        }
        part(torso,"core_housing",96,-1,2.5f,-3.8f,2,2.8f,.4f);
        part(torso,"reactor",64,-.45f,2.8f,-4.08f,.9f,2.15f,.35f);
        for(int n=0;n<3;n++)part(torso,"abdomen"+n,0,-2.5f,5.7f+n*1.4f,-3.05f,5,1,.6f);
        part(torso,"backpack",0,-1.9f,1.8f,2.7f,3.8f,7.8f,1.7f);part(torso,"back_reactor",64,-.3f,3,4.45f,.6f,4.8f,.2f);
        part(body,"belt",0,-4.25f,10,-2.8f,8.5f,2,5.6f);
        var flight=body.addOrReplaceChild("flight",CubeListBuilder.create(),PartPose.ZERO);
        for(int sign:new int[]{-1,1}){
            var pod=flight.addOrReplaceChild("pod"+sign,box(0,-1.1f,-3.8f,-1.1f,2.2f,7.8f,2.2f),PartPose.offsetAndRotation(sign*3.3f,5,4.05f,-.1f,0,sign*.22f));
            part(pod,"nozzle",96,-1.2f,3.2f,-1.2f,2.4f,.9f,2.4f);part(pod,"exhaust",64,-.8f,4,-.8f,1.6f,.25f,1.6f);
            pod.addOrReplaceChild("fairing",box(0,-1.5f,-2.5f,-.4f,3,5,.8f),PartPose.offsetAndRotation(sign*.5f,-1.5f,1.2f,.15f,0,-sign*.12f));
        }
        part(body,"cloak",64,-2.8f,1.8f,4.65f,5.6f,.3f,.2f);
        var shield=body.addOrReplaceChild("shield",CubeListBuilder.create(),PartPose.ZERO);
        part(shield,"left",64,4.45f,3.2f,-1.8f,.25f,3.5f,.5f);part(shield,"right",64,-4.7f,3.2f,-1.8f,.25f,3.5f,.5f);
        var heavy=body.addOrReplaceChild("heavy",CubeListBuilder.create(),PartPose.ZERO);
        for(int sign:new int[]{-1,1})heavy.addOrReplaceChild("flank"+sign,box(0,-1.05f,-2,-.5f,2.1f,4,1),PartPose.offsetAndRotation(sign*3.9f,7.5f,-2.8f,0,0,sign*.15f));
        for(int sign:new int[]{-1,1}){
            float center=sign<0?-1:1;
            var arm=root.addOrReplaceChild(sign<0?"right_arm":"left_arm",box(0,center-2.1f,-2,-2.15f,4.2f,12,4.3f),PartPose.offset(sign*5,2,0));
            arm.addOrReplaceChild("pauldron",box(0,-2.5f,-1.6f,-2.7f,5,3.2f,5.4f),PartPose.offsetAndRotation(center,-1,0,0,0,-sign*.15f));
            part(arm,"shoulder_stripe",64,center-1.7f,-2.2f,-2.9f,3.4f,.3f,.25f);
            part(arm,"gauntlet",0,center-2.25f,6.1f,-2.4f,4.5f,4.3f,4.8f);
            part(arm,"forearm_seam",96,center-1.5f,9.8f,-2.55f,3,.35f,.2f);
            part(arm,"forearm_light",64,center-.25f,7,-2.7f,.5f,2,.2f);
            var missile=arm.addOrReplaceChild("missile",box(0,center-2.3f,-4,-1.6f,4.6f,1.6f,4.2f),PartPose.ZERO);
            for(int n=0;n<3;n++)part(missile,"port"+n,64,center-1.6f+n*1.25f,-3.6f,-1.9f,.7f,.5f,.3f);
            part(arm,"turret",0,center-.9f,-5.2f,-.7f,1.8f,1.4f,1.8f);
            if(sign<0){
                part(arm,"laser",64,center-1.2f,9,-2.95f,2.4f,.4f,.45f);
                var cannon=arm.addOrReplaceChild("cannon",CubeListBuilder.create(),PartPose.ZERO);
                for(int rail:new int[]{-1,1}){part(cannon,"rail"+rail,0,center+rail*1.1f-.6f,6.4f,-5,.9f,2.5f,3);part(cannon,"emitter"+rail,64,center+rail*1.1f-.5f,7.2f,-5.2f,.7f,.7f,.2f);}
            }
        }
        for(int sign:new int[]{-1,1}){
            var leg=root.addOrReplaceChild(sign<0?"right_leg":"left_leg",box(0,-1.85f,0,-2.1f,3.7f,12,4.2f),PartPose.offset(sign*1.9f,12,0));
            part(leg,"thigh",0,-2.05f,.2f,-2.35f,4.1f,4.3f,4.7f);
            part(leg,"knee",0,-1.9f,4.6f,-2.8f,3.8f,2.1f,.9f);
            part(leg,"shin",0,-1.85f,7,-2.5f,3.7f,4.5f,.6f);
            part(leg,"shin_stripe",64,sign<0?-1.6f:1.3f,7.4f,-2.8f,.3f,3.8f,.2f);
            part(leg,"boot",0,-2,10.4f,-3.6f,4,2,6);
            part(leg,"sole",96,-2.1f,12,-3.7f,4.2f,.45f,6.2f);
        }
        return LayerDefinition.create(mesh,128,128);
    }
    public void prepare(EquipmentSlot slot,CompoundTag data){
        setAllVisible(false);head.visible=slot==EquipmentSlot.HEAD;body.visible=slot==EquipmentSlot.CHEST||slot==EquipmentSlot.LEGS;
        leftArm.visible=rightArm.visible=slot==EquipmentSlot.CHEST;leftLeg.visible=rightLeg.visible=slot==EquipmentSlot.LEGS||slot==EquipmentSlot.FEET;
        torso.visible=slot==EquipmentSlot.CHEST;belt.visible=slot==EquipmentSlot.LEGS;
        int mask=data.getInt("Abilities");
        flight.visible=slot==EquipmentSlot.CHEST&&ability(mask,"flight");cloak.visible=slot==EquipmentSlot.CHEST&&ability(mask,"cloak");shield.visible=slot==EquipmentSlot.CHEST&&ability(mask,"shield");heavy.visible=slot==EquipmentSlot.CHEST&&ability(mask,"heavy");
        laser.visible=ability(mask,"laser");cannon.visible=ability(mask,"cannon");leftMissile.visible=rightMissile.visible=ability(mask,"missile");leftTurret.visible=rightTurret.visible=ability(mask,"turret");
        var tiers=data.getIntArray("ChassisTiers");crown.visible=tiers.length==8&&tiers[0]>=3;
        for(var leg:new ModelPart[]{leftLeg,rightLeg}){
            // Base cubes are drawn once with leggings; boots retain their own rigid geometry.
            leg.skipDraw=slot!=EquipmentSlot.LEGS;
            for(String name:new String[]{"thigh","knee","shin","shin_stripe"})leg.getChild(name).visible=slot==EquipmentSlot.LEGS;
            leg.getChild("boot").visible=leg.getChild("sole").visible=slot==EquipmentSlot.FEET;
        }
    }
    public void preview(CompoundTag data){young=false;riding=false;crouching=false;attackTime=0;prepare(EquipmentSlot.CHEST,data);head.visible=true;body.visible=true;belt.visible=true;leftLeg.visible=rightLeg.visible=true;for(var leg:new ModelPart[]{leftLeg,rightLeg}){leg.skipDraw=false;leg.getAllParts().forEach(p->p.visible=true);}head.yRot=-.12f;rightArm.zRot=.08f;leftArm.zRot=-.08f;}
    private static boolean ability(int mask,String key){return (mask&(1<<Lingchuan.ABILITIES.indexOf(key)))!=0;}
}
