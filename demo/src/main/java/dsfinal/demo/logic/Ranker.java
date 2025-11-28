package dsfinal.demo.logic;

import dsfinal.demo.model.WebPage;

public class Ranker {
    
    // 主題關鍵字：只要出現這些字，代表這個網頁跟荒野亂鬥高度相關
    private final String[] THEME_KEYWORDS = {"荒野亂鬥", "Brawl Stars", "Supercell", "brawl stars"};

    public Ranker() {
    }

    /**
     * 計算分數邏輯：
     * 1. 是否包含「荒野亂鬥」主題？ (權重最重)
     * 2. 是否包含使用者搜尋的關鍵字？ (權重次之)
     */
    public double calculatePageScore(WebPage page, String userQuery) {
        if (page.content == null) page.content = ""; 
        
        double score = 0.0;
        String fullText = (page.title + " " + page.content).toLowerCase();
        String query = userQuery.toLowerCase();

        // 規則 1: 荒野亂鬥主題檢查
        boolean isThemeRelated = false;
        for (String themeWord : THEME_KEYWORDS) {
            if (fullText.contains(themeWord.toLowerCase())) {
                isThemeRelated = true;
                break;
            }
        }

        if (isThemeRelated) {
            // 只要跟荒野亂鬥有關，直接先給 20 分 (基礎分)
            score += 20.0; 
            
            // 如果是在標題就提到荒野亂鬥，再加 10 分
            //if (page.title.toLowerCase().contains("荒野亂鬥") || page.title.toLowerCase().contains("brawl stars")) {
                //score += 10.0;
            //}
        } else {
            // 如果完全沒提到荒野亂鬥
            // 進行扣分
            score -= 20.0; 
        }

        // 規則 2: 使用者查詢關聯度
        // 計算使用者搜尋的字 (例如 "戰鬥") 出現次數
        int count = countOccurrences(fullText, query);
        
        if (count > 0) {
            // 有出現搜尋字，給予加分
            // 例如：出現一次 +2 分，上限 +10 分
            double queryScore = Math.min(count * 2.0, 10.0);
            score += queryScore;
        }

        page.topicScore = score;
        return score;
    }

    private int countOccurrences(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(keyword, idx)) != -1) {
            count++;
            idx += keyword.length();
        }
        return count;
    }
}