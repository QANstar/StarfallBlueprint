"""Merge unified personal progression translations without regenerating other assets."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
MESSAGES = {
    'title': ('成长与科技', 'Growth & technology'),
    'advanced_engineering_title': ('进阶工程', 'Advanced Engineering'),
    'advanced_engineering_description': ('掌握水神计划进阶工程，在装配台将隔离、存活的涓流收纳机甲晋升为沧澜；继承能源、损伤、货物与配装。', 'Master advanced Water God engineering. Upgrade an isolated living packed Juanliu to Canglan at the workbench, retaining energy, damage, cargo and equipment.'),
    'lingchuan_description': ('岭川个人研究 · T%s', 'Lingchuan personal research · T%s'),
    'tab': ('成长', 'Growth'),
    'tab_hint': ('查看全部成长与科技，消耗材料与经验等级学习。', 'Browse all growth and technology. Learn using materials and experience levels.'),
    'open': ('打开成长面板', 'Open growth panel'),
    'back': ('返回背包', 'Inventory'),
    'category.all': ('全部', 'All'),
    'category.best': ('贝斯特', 'Best'),
    'category.lingchuan': ('岭川', 'Lingchuan'),
    'learn': ('学习', 'Learn'),
    'state.learned': ('已学会', 'Learned'),
    'state.available': ('可学习', 'Available'),
    'state.locked': ('未解锁', 'Locked'),
    'experience_summary': ('经验等级：%s / %s', 'Experience levels: %s / %s'),
    'prerequisites': ('前置科技', 'Prerequisites'),
    'none': ('无', 'None'),
    'prerequisite_met': ('已满足：%s', 'Met: %s'),
    'prerequisite_missing': ('尚缺：%s', 'Missing: %s'),
    'materials_heading': ('所需材料（仅限可用材料）', 'Materials (usable items only)'),
    'material_count': ('持有 %s / 需要 %s', 'Have %s / need %s'),
    'details_scroll': ('滚轮查看详情', 'Scroll for details'),
    'progress': ('已学 %s / %s', 'Learned %s / %s'),
    'station_hint': ('岭川研究已整合至背包的成长面板，材料与经验等级在该面板统一消耗。', 'Lingchuan research is now in the inventory growth panel, with unified material and experience-level costs.'),
    'invalid': ('无效的成长项目', 'Invalid progression node'),
    'already_learned': ('已掌握，无需再次支付', 'Already learned; no further payment required'),
    'prerequisite': ('请先完成前置学习', 'Complete the prerequisite research first'),
    'experience': ('经验等级不足：需要 %s 级', 'Not enough experience: requires %s levels'),
    'materials': ('材料不足：请提交足量清洁、可用的材料', 'Not enough materials: submit sufficient clean, usable materials'),
    'ready': ('可以学习 · 消耗 %s 级经验', 'Ready to learn · costs %s experience levels'),
    'unavailable': ('当前状态无法学习', 'Cannot learn in the current player state'),
    'learned': ('已掌握：%s', 'Learned: %s'),
    'rewards_heading': ('首次解锁奖励', 'First-unlock rewards'),
    'rewards_once': ('仅首次成功学习发放，已学节点不补发', 'Granted on first learning only; no retroactive claims'),
    'experience_reward': ('奖励 %s 经验点', 'Reward: %s experience points'),
    'item_reward': ('奖励 ×%s', 'Reward: x%s'),
    'reward_space': ('奖励所需背包空间不足，请整理主背包后再学习', 'Not enough room for rewards. Make space in your main inventory before learning.'),
}


def main():
    for index, language in enumerate(('zh_cn', 'en_us')):
        path = ROOT / 'src/main/resources/assets/starfall_blueprint/lang' / f'{language}.json'
        names = json.loads(path.read_text(encoding='utf-8'))
        names.update({f'growth.starfall_blueprint.{key}': pair[index] for key, pair in MESSAGES.items()})
        path.write_text(json.dumps(names, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'Merged {len(MESSAGES)} unified progression translations per language.')


if __name__ == '__main__':
    main()
