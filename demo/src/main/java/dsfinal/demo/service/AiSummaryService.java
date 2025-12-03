package dsfinal.demo.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import dsfinal.demo.model.WebPage;

@Service
public class AiSummaryService {

    public String generateSummary(String query, List<WebPage> topPages) {
        // 只取前 3 篇
        List<WebPage> references = topPages.size() > 3 ? topPages.subList(0, 3) : topPages;
        
        boolean isChinese = isChinese(query);
        
        // 改回：句子萃取模式 (Sentence Extraction)
        List<String> keySentences = extractKeySentences(query, references, isChinese);

        StringBuilder sb = new StringBuilder();
        
        String title = isChinese ? "🤖 AI 智能摘要" : "🤖 AI Summary";
        String noResult = isChinese ? "資訊量不足，無法生成摘要。" : "Not enough information to generate a summary.";
        String sourceTitle = isChinese ? "📚 資料來源：" : "📚 Sources:";

        sb.append("<h3>").append(title).append(": ").append(query).append("</h3>");

        if (keySentences.isEmpty()) {
            sb.append("<p>").append(noResult).append("</p>");
        } else {
            sb.append("<p>");
            Set<String> added = new HashSet<>();
            for (String sentence : keySentences) {
                if (!added.contains(sentence)) {
                    sb.append(sentence).append(" "); // 用空白拼接句子
                    added.add(sentence);
                }
            }
            sb.append("</p>");
        }

        // 引用來源
        sb.append("<div style='margin-top:15px; font-size:12px; color:#666; border-top:1px solid #eee; padding-top:10px;'>");
        sb.append("<strong>").append(sourceTitle).append("</strong><br>");
        for (int i = 0; i < references.size(); i++) {
            WebPage page = references.get(i);
            sb.append((i + 1)).append(". <a href='").append(page.url).append("' target='_blank' style='color:#1a0dab; text-decoration:none;'>")
              .append(page.title).append("</a><br>");
        }
        sb.append("</div>");

        return sb.toString();
    }

    private List<String> extractKeySentences(String query, List<WebPage> pages, boolean isChinese) {
        List<SentenceScore> scoredSentences = new ArrayList<>();
        Set<String> seenSentences = new HashSet<>(); 

        for (WebPage page : pages) {
            if (page.content == null) continue;

            String dirtyContent = page.content.replaceAll("[\\uE000-\\uF8FF]", ""); 
            
            // [恢復] 切分句子：遇到句號、問號、驚嘆號就切斷
            String[] sentences = dirtyContent.split("[。！？\\n\\r?!]");

            for (String s : sentences) {
                String cleanS = s.trim();
                
                // 長度限制：太短像標題，太長像內文整段，都不要
                int minLen = isChinese ? 10 : 20;  
                int maxLen = 150; 
                
                if (cleanS.length() < minLen || cleanS.length() > maxLen) continue; 
                if (seenSentences.contains(cleanS)) continue; 

                int score = calculateScore(cleanS, query, isChinese);
                
                if (score > 0) {
                    scoredSentences.add(new SentenceScore(cleanS, score));
                    seenSentences.add(cleanS);
                }
            }
        }

        // 分數排序
        scoredSentences.sort((s1, s2) -> Integer.compare(s2.score, s1.score));

        List<String> result = new ArrayList<>();
        // 只取前 3 句高分句子
        for (int i = 0; i < Math.min(3, scoredSentences.size()); i++) {
            String text = scoredSentences.get(i).text;
            // 補上標點符號
            if(!text.matches(".*[。！？?!.]$")) {
                text += (isChinese ? "。" : ".");
            }
            result.add(text);
        }
        return result;
    }

    private int calculateScore(String sentence, String query, boolean isChinese) {
        int score = 0;
        String lowerS = sentence.toLowerCase();
        String lowerQ = query.toLowerCase();

        // 1. 黑名單過濾
        if (lowerS.contains("cookies") || lowerS.contains("login") || lowerS.contains("登入") || lowerS.contains("版權")) return -999;

        // 2. 關鍵字命中
        if (lowerS.contains(lowerQ)) {
            score += 50;
            if (lowerS.startsWith(lowerQ)) score += 20;
        } else if (lowerS.contains("brawl") || lowerS.contains("荒野")) {
            // 如果沒關鍵字但有遊戲名，給個保底分，以免什麼都抓不到
            score += 10;
        } else {
            return 0; 
        }

        // 3. 解釋性詞彙加分
        String[] keywords = isChinese 
            ? new String[]{"是", "為", "意思", "攻略", "技巧", "排名", "最強", "玩法", "介紹"} 
            : new String[]{"is", "guide", "tips", "intro", "best", "tier", "how to"};
            
        for (String w : keywords) {
            if (lowerS.contains(w)) score += 10;
        }
        
        return score;
    }

    private boolean isChinese(String text) {
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    private static class SentenceScore {
        String text;
        int score;
        public SentenceScore(String text, int score) {
            this.text = text;
            this.score = score;
        }
    }
}