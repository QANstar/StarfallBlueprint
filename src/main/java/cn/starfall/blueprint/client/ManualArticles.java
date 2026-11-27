package cn.starfall.blueprint.client;

import cn.starfall.blueprint.Progression;
import cn.starfall.blueprint.StarfallBlueprint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Reading content, independent of the former research costs and player knowledge. */
final class ManualArticles {
    record Article(String id, String category, Component title, Component description, ItemStack icon, List<String> related) {}

    static List<Article> entries() {
        var articles = new ArrayList<Article>();
        var book = StarfallBlueprint.GUIDE.get().getDefaultInstance().get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (book != null) {
            int page = 0;
            for (var text : book.pages()) {
                String[] parts = ChatFormatting.stripFormatting(text.raw().getString()).split("\\n", 2);
                articles.add(new Article("camp:" + page++, "camp", Component.literal(parts[0]),
                        Component.literal(parts.length > 1 ? parts[1].strip() : ""),
                        new ItemStack(StarfallBlueprint.GUIDE.get()), List.of()));
            }
        }
        for (var node : Progression.NODES) articles.add(new Article(node.id(), node.category(), node.title(),
                node.description(), node.icon(), node.prerequisites()));
        return List.copyOf(articles);
    }
    private ManualArticles() {}
}
