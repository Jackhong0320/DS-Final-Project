package dsfinal.demo.service;

import dsfinal.demo.model.WebPage;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiSummaryService {

    //動態生成摘要 (Extractive Summarization)

    public String generateSummary(String query, List<WebPage> topPages) {
        // 1. 準備資料
        List<WebPage> references = topPages.size() > 5 ? topPages.subList(0, 3) : topPages;
        
        // 2. 萃取關鍵句
        List<String> keySentences = extractKeySentences(query, references);

        // 3. 組裝 HTML 輸出
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>🤖 AI 智能摘要：").append(query).append("</h3>");

        if (keySentences.isEmpty()) {
            sb.append("<p>根據目前的搜尋結果，無法萃取到關於 <strong>").append(query).append("</strong> 的具體描述。");
            sb.append("這可能是因為搜尋到的網頁內容較少或為純圖片網站。</p>");
        } else {
            sb.append("<p>");
            // 把萃取到的句子串起來，變成一段話
            for (String sentence : keySentences) {
                sb.append(sentence).append(" ");
            }
            sb.append("</p>");
            
            // 加入一些動態生成的建議
            sb.append("<h4>💡 重點分析：</h4><ul>");
            sb.append("<li>建議優先閱讀下方的資料來源，以獲取最完整的資訊。</li>");
            sb.append("</ul>");
        }

        // 4. 生成引用來源
        sb.append("<div style='margin-top:15px; font-size:12px; color:#666; border-top:1px solid #eee; padding-top:10px;'>");
        sb.append("<strong>📚 資料來源 (基於以下網頁內容即時生成)：</strong><br>");
        for (int i = 0; i < references.size(); i++) {
            WebPage page = references.get(i);
            sb.append((i + 1)).append(". <a href='").append(page.url).append("' target='_blank' style='color:#1a0dab; text-decoration:none;'>")
              .append(page.title).append("</a> <span style='color:#d93025'>(Score: ").append(String.format("%.1f", page.topicScore)).append(")</span><br>");
        }
        sb.append("</div>");

        return sb.toString();
    }

    // 核心演算法：從一堆網頁內文中，找出最能解釋「關鍵字」的句子
    private List<String> extractKeySentences(String query, List<WebPage> pages) {
        List<SentenceScore> scoredSentences = new ArrayList<>();
        Set<String> seenSentences = new HashSet<>();

        for (WebPage page : pages) {
            if (page.content == null || page.content.length() < 10) continue;

            // 1. 斷句 (用句號、問號、驚嘆號、換行來切分)
            String[] sentences = page.content.split("[。！？\\n\\r?!]");

            for (String s : sentences) {
                String cleanS = s.trim();
                if (cleanS.length() < 10 || cleanS.length() > 100) continue; // 過濾太短或太長的廢話
                if (seenSentences.contains(cleanS)) continue; // 去除重複的句子

                // 2. 評分
                int score = calculateScore(cleanS, query, page.title);
                
                if (score > 0) {
                    scoredSentences.add(new SentenceScore(cleanS, score));
                    seenSentences.add(cleanS);
                }
            }
        }

        // 3. 排序 (分數高的排前面)
        scoredSentences.sort((s1, s2) -> Integer.compare(s2.score, s1.score));

        // 4. 取前 3 名句子
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(3, scoredSentences.size()); i++) {
            result.add(scoredSentences.get(i).text + "。");
        }
        return result;
    }

    // 句子評分邏輯
    
    private int calculateScore(String sentence, String query, String pageTitle) {
        int score = 0;
        String lowerS = sentence.toLowerCase();
        String lowerQ = query.toLowerCase();

        // 規則 A: 包含使用者搜尋的關鍵字
        if (lowerS.contains(lowerQ)) {
            score += 50;
            // 如果句子開頭就是關鍵字，分數加倍
            if (lowerS.startsWith(lowerQ)) {
                score += 20;
            }
        } else {
            // 如果完全沒包含關鍵字，基本上這句話沒用，除非它包含「荒野亂鬥」
            if (lowerS.contains("荒野亂鬥") || lowerS.contains("brawl stars")) {
                score += 10;
            } else {
                return 0;
            }
        }

        // 規則 B: 包含解釋性或攻略性詞彙 (加分)
        String[] bonusWords = {"是", "為", "意思", "攻略", "技巧", "排名", "最強", "玩法", "介紹", "特點"};
        for (String w : bonusWords) {
            if (lowerS.contains(w)) {
                score += 5;
            }
        }

        // 規則 C: 完整性檢查
        if (pageTitle != null && lowerS.contains(pageTitle.substring(0, Math.min(5, pageTitle.length())))) {
            score += 10;
        }

        // 規則 D: 懲罰垃圾訊息
        if (lowerS.contains("登入") || lowerS.contains("註冊") || lowerS.contains("cookies") || lowerS.contains("版權所有")) {
            score -= 100;
        }

        return score;
    }

    // 存句子和分數
    private static class SentenceScore {
        String text;
        int score;

        public SentenceScore(String text, int score) {
            this.text = text;
            this.score = score;
        }
    }
}