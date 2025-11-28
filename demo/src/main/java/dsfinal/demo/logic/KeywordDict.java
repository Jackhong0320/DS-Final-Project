package dsfinal.demo.logic;

import java.util.HashMap;
import java.util.Map;

public class KeywordDict {
    private Map<String, Double> level1Map = new HashMap<>();
    private Map<String, Double> level2Map = new HashMap<>();
    private Map<String, Double> level3Map = new HashMap<>();

    public KeywordDict() {
        // Level 1: 核心詞 (權重最高 5.0)
        level1Map.put("荒野亂鬥", 5.0);
        level1Map.put("Brawl Stars", 5.0);
        level1Map.put("Supercell", 5.0);
        level1Map.put("英雄", 4.0);

        // Level 2: 遊戲機制 (權重中等 3.0)
        String[] l2Words = {"寶石", "排位賽", "金庫攻防", "據點", "極限威能", "能力之星", "足球", "淘汰賽", "3v3", "5v5"};
        for (String w : l2Words) level2Map.put(w, 3.0);

        // Level 3: 社群與延伸 (權重較低 2.0)
        String[] l3Words = {"排行榜", "攻略", "亂鬥TV", "新聞", "造型", "全球錦標賽", "勝率"};
        for (String w : l3Words) level3Map.put(w, 2.0);
    }

    public double getWeight(String term) {
        if (level1Map.containsKey(term)) return level1Map.get(term);
        if (level2Map.containsKey(term)) return level2Map.get(term);
        if (level3Map.containsKey(term)) return level3Map.get(term);
        return 0.0;
    }
}