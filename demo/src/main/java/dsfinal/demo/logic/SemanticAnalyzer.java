package dsfinal.demo.logic;

import dsfinal.demo.model.WebPage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticAnalyzer {
    
    private static final Map<String, String[]> KEYWORD_MAP = new HashMap<>();

    static {
        // 定義關聯詞
        KEYWORD_MAP.put("戰鬥", new String[]{"寶石爭奪 技巧", "荒野決鬥 攻略", "亂鬥足球 戰術", "極限淘汰賽", "3v3 對戰"});
        KEYWORD_MAP.put("模式", new String[]{"單人競技", "雙人競技", "搶星大作戰", "金庫攻防", "機甲攻堅戰"});
        KEYWORD_MAP.put("攻擊", new String[]{"超級技能 使用時機", "極限威能", "自動瞄準", "手動瞄準", "傷害計算"});
        KEYWORD_MAP.put("角色", new String[]{"最強英雄 排名", "T0角色", "新英雄", "傳奇英雄", "免費英雄"});
        KEYWORD_MAP.put("寶石", new String[]{"寶石爭奪地圖", "寶石特價", "亂鬥通行證", "寶石獲取"});
        KEYWORD_MAP.put("升級", new String[]{"能力之星 推薦", "武裝配件", "能量點數", "金幣不足"});
    }

    public List<String> deriveRelatedKeywords(List<WebPage> topPages, String userQuery) {
        List<String> suggestions = new ArrayList<>();
        
        // 1. 查字典
        for (String key : KEYWORD_MAP.keySet()) {
            if (userQuery.contains(key)) {
                for (String suggestion : KEYWORD_MAP.get(key)) {
                    if (!suggestions.contains(suggestion)) {
                        suggestions.add(suggestion);
                    }
                    if (suggestions.size() >= 5) break;
                }
            }
        }

        // 2. 如果不夠 5 個，用通用詞補滿
        String[] fallbackWords = {
            "荒野亂鬥 角色排名", 
            "荒野亂鬥 更新資訊", 
            "荒野亂鬥 禮包碼", 
            "Brawl Stars meta", 
            "荒野亂鬥 上分技巧"
        };
        
        for (String word : fallbackWords) {
            if (suggestions.size() >= 5) break;
            if (!suggestions.contains(word)) {
                suggestions.add(word);
            }
        }
        
        if (suggestions.size() > 5) {
            return suggestions.subList(0, 5);
        }
        
        return suggestions;
    }
}