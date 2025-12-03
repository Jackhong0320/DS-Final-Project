package dsfinal.demo.logic;

import dsfinal.demo.model.WebPage;

public class Ranker {
    
    // 支援多國語言的主題關鍵字
    private final String[] THEME_KEYWORDS = {
        "荒野亂鬥", "Brawl Stars", "Supercell", "brawl", 
        "ブロスタ", "브롤스타즈", "Clash", "브롤", "براول ستارز"
    };

    private final String[] AUTHORITY_DOMAINS = {
        "wikipedia.org", "fandom.com", "wiki", "game8", "gamewith", "inven", "dcard"
    };

    public double calculatePageScore(WebPage page, String userQuery) {
        if (page.content == null) page.content = ""; 
        
        double score = 0.0;
        String fullText = (page.title + " " + page.content).toLowerCase();
        String query = userQuery.toLowerCase();
        String url = page.url.toLowerCase();

        // 1. 主題檢查 (沒變)
        boolean isThemeRelated = false;
        for (String themeWord : THEME_KEYWORDS) {
            if (fullText.contains(themeWord.toLowerCase())) {
                isThemeRelated = true;
                break;
            }
        }
        if (isThemeRelated) score += 20.0;
        else score -= 20.0;

        // 2. [重要修改] 嚴格關鍵字檢查 (Strict Matching)
        // 把使用者輸入的字串切開 (例如 "ブロスタ 戦略" -> "ブロスタ", "戦略")
        String[] keywords = query.split("\\s+"); // 用空白切割
        
        for (String key : keywords) {
            if (key.length() < 1) continue;
            
            // 檢查這個詞有沒有在內文出現
            if (fullText.contains(key)) {
                // 有出現 -> 加分
                score += 10.0; 
                
                // 如果是在標題出現 -> 大加分
                if (page.title.toLowerCase().contains(key)) {
                    score += 20.0;
                }
            } else {
                // [關鍵] 只要缺了一個關鍵字，就重重扣分！
                // 這樣就能過濾掉那些「只沾到邊」的網站
                score -= 50.0;
            }
        }

        // 3. 權威網站微調 (沒變)
        for (String domain : AUTHORITY_DOMAINS) {
            if (url.contains(domain)) {
                score += 5.0; 
                break;
            }
        }

        page.topicScore = score;
        return score;
    }
}