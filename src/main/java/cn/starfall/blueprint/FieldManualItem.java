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
                page("§l继续找矿§r\n\n晶料＋铜锭＋红石→近距寻矿指示片。\n\n右键扫描8格球形范围，只反馈有无信号；不会显示坐标或品位，不加载远处区块。\n\n冷却2秒。原矿的品位和完整度在提示中显示。"),
                page("§l搭成基础产线§r\n\n上方漏斗投矿，侧面漏斗投煤，下方漏斗取晶料和尾砂。\n\n水可手动补入，也开放NeoForge普通水输入接口。\n\n尾砂槽最多缓存16批相同尾砂。不同品质不可强行混合。"),
                page("§l雨水补给§r\n\n4玻璃围住1铁锭→4000mB常压罐。\n\n集雨器：\n铁 玻璃 铁\n玻璃 罐 空\n木板 木板 木板\n\n露天雨天100mB/秒，放在整备台正上方可自动补水；晴天不产水。"),
                page("§l停机与搬迁§r\n\n欠燃料或堵塞时保留进度。开始一批时预留矿与水，每刻扣除相能。\n\n拆下整备台会把库存、水、余能、接头及在制批次打包；重新放置续作。\n\n罐与集雨器同样保留水量。未加载的区块不补算产量。"),
                page("§l当前版本范围§r\n\n公共基础整备与CB-01至04已实现。\n\n本版不含NPC研究交易、专业精制、阵营研究、跨星球或电网。\n\n自然矿分布为原型参数，尚未通过设定要求的20种子稀有度校准。")
        ), true)));
    }
    private static Filterable<Component> page(String text) { return Filterable.passThrough(Component.literal(text)); }
}
