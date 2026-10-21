package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.WrittenBookContent;

public final class FieldManualItem extends WrittenBookItem {
    public FieldManualItem(Properties p) {
        super(p.stacksTo(1).component(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("星落蓝图 · 营地手册"), "游子独立团 · 工务组", 0, List.of(
                page("§l从一块矿开始§r\n\n主世界地下寻找蓝绿色晶脉，用铁镐以上工具采收。\n\n整备台：上排3铁；中排石/铜/石；下排石/空/石。\n\n不需要晶料或电控板起步。"),
                page("§l第一次整备§r\n\n水桶右键加入1000mB水，可加工4批。界面放入原矿和煤/木炭。\n\n每批10秒，耗水250mB、40kXH；一煤320kXH，可加工8批。\n\nG6%出5份；G12%出10份；G24%出20份。"),
                page("§l第一件有用的成品§r\n\n晶料＋铜锭→省能接头。右键装入整备台，每批36kXH。\n\n晶料上、铁锭下→工具加固扣。\n晶料左、铁锭右→盾面衬片。\n\n与镐/盾合成，耐久＋20%，不修复原有损耗，不可叠加。"),
                page("§l修理加固装备§r\n\n工作台或砂轮合并同类装备，保留其中的加固。\n\n工作台会清除名称与普通附魔；砂轮沿用上槽名称。\n\n铁砧以左侧为目标：把加固装备放左侧，右侧加固不转移。"),
                page("§l升级下界合金§r\n\n加固钻石镐可用原版模板和下界合金锭升级。\n\n新耐久上限2437，保留原损耗比例、名称与附魔。\n\n加固仍只生效一次，不能再次安装加固扣。"),
                page("§l继续找矿§r\n\n晶料＋铜锭＋红石→近距寻矿指示片。\n\n右键扫描8格球形范围，只反馈有无信号；不会显示坐标或品位，不加载远处区块。\n\n冷却2秒。原矿的品位和完整度在提示中显示。"),
                page("§l搭成基础产线§r\n\n上方漏斗投矿，侧面漏斗投煤，下方漏斗取晶料和尾砂。\n\n水可手动补入，也开放NeoForge普通水输入接口。\n\n尾砂槽最多缓存16批相同尾砂。不同品质不可强行混合。"),
                page("§l雨水补给§r\n\n4玻璃围住1铁锭→4000mB常压罐。\n\n集雨器：\n铁 玻璃 铁\n玻璃 罐 空\n木板 木板 木板\n\n露天雨天100mB/秒，放在整备台正上方可自动补水；晴天不产水。"),
                page("§l停机与搬迁§r\n\n欠燃料或堵塞时保留进度。开始一批时预留矿与水，每刻扣除相能。\n\n拆下整备台会把库存、水、余能、接头及在制批次打包；重新放置续作。\n\n罐与集雨器同样保留水量。未加载的区块不补算产量。"),
                page("§l安置点工务交付§r\n\n终端配方：纸放上中；圆石放中排三格与下中。\n\n持合格Q0晶料右键，每份换1铁；所有终端共享每日4笔。空手查看余量，不领取物资。"),
                page("§l为重建提供工具§r\n\n折桥救援之后，白灯安置点需要供水、照明与运输。整备台、升级件和雨水设备服务这些实际需求。\n\n本册只供查阅配方，冒险按主线剧情推进。"),
                page("§l普通结构材料§r\n\n2铁锭竖排→2份铁板。\n1铜锭→1份铜板。\n3铁锭横排→3组铁制固定件。\n\n黏土球经熔炉10秒→陶瓷。\n铜板＋石英→2份隔离基底，不再拆半份材料。"),
                page("§l合金混料炉§r\n\n工作台按图摆放：\n铁 铜 铁\n石 炉 石\n石 石 石\n\n石是圆石，炉是普通熔炉。无需钢、晶料或控制板。\n\n右键打开：上方两格放3铁锭和1煤/木炭，下方燃料格另外加煤/木炭。"),
                page("§l从铁到普通钢§r\n\n混料炉每10秒把3铁＋1煤/木炭加工成3钢坯。碳料和供热燃料分别消耗。\n\n再把钢坯送普通熔炉，每件10秒烧成1普通钢。\n\n顶部进两种原料，侧面进燃料，底部取钢坯。欠料、欠燃料或满仓暂停；拆移保留在制批次。"),
                page("§l绝缘与电控§r\n\n纸＋玻璃→2份绝缘基材；小麦＋黏液球→2份粘结剂。\n\n铜＋红石＋玻璃＋1份绝缘→控制板。\n铁＋铜＋红石＋1份绝缘→断路保护器。\n\n1份陶瓷→护套；1份普通钢→钢制固定件。"),
                page("§l九宫格只放整件§r\n\n基础机架：4铁锭、2铜锭、2圆石、1组铁制固定件无序合成。每件各占一格，正好9格。\n\n2铁＋玻璃＋1份绝缘→封存盒，当前只作装配材料。\n\n配方中的3铁要摆3格；不会在一格中自动扣3件，不用称量或拆分余料。"),
                page("§l当前版本范围§r\n\n公共基础整备、CB-01至05、普通结构与电控材料、基础机架、合金混料炉及营地雨水补给已实现。\n\n剧情任务与人物仍待接入；本版不含专业精制、跨星球或电网。\n\n自然矿分布尚未通过20种子稀有度校准。")
        ), true)));
    }
    private static Filterable<Component> page(String text) { return Filterable.passThrough(Component.literal(text)); }
}
