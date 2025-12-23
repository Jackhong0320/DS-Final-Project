package dsfinal.demo.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import dsfinal.demo.model.WebPage;

@Service
public class AiSummaryService {

    private final String[] GAME_NAMES = {
        "brawl", "荒野", "ブロスタ", "브롤", "бравл", "براول"
    };

    public String generateSummary(String query, List<WebPage> topPages) {
        // 取前3篇
        List<WebPage> references = topPages.size() > 3 ? topPages.subList(0, 3) : topPages;
        
        boolean isChinese = isChinese(query);
        
        List<String> keySentences = extractKeySentences(query, references, isChinese);

        StringBuilder sb = new StringBuilder();
        
        String title = isChinese ? "🤖 AI摘要" : "🤖 AI Summary";
        // 如果真的沒抓到句子，顯示提示
        String noResult = isChinese ? "資訊量不足，無法生成摘要。" : "Not enough information to generate a summary.";
        String sourceTitle = isChinese ? "📚 資料來源：" : "📚 Sources:";

        // AI標題
        sb.append("<div style='margin-bottom:10px;'>")
          .append("<span style='font-weight:bold; color:#1a73e8; font-size:16px;'>").append(title).append("</span>")
          .append("<span style='color:#666; font-size:14px; margin-left:10px;'>").append(query).append("</span>")
          .append("</div>");

        if (keySentences.isEmpty()) {
            sb.append("<p>").append(noResult).append("</p>");
        } else {
            sb.append("<p style='line-height:1.6; color:#333;'>");
            Set<String> added = new HashSet<>();
            for (String sentence : keySentences) {
                // 簡單去重
                if (!added.contains(sentence)) {
                    sb.append(sentence).append(" "); 
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

            // 移除特殊字元
            String dirtyContent = page.content.replaceAll("[\\uE000-\\uF8FF]", ""); 
            String[] sentences = dirtyContent.split("[。！？\\n\\r?!.]");

            for (String s : sentences) {
                String cleanS = s.trim();
                
                // 長度限制
                int minLen = isChinese ? 10 : 15;  
                int maxLen = 200;
                
                if (cleanS.length() < minLen || cleanS.length() > maxLen) continue; 
                if (seenSentences.contains(cleanS)) continue; 

                int score = calculateScore(cleanS, query, isChinese);
                
                if (score > 0) {
                    scoredSentences.add(new SentenceScore(cleanS, score));
                    seenSentences.add(cleanS);
                }
            }
        }

        // 分數高到低排序
        scoredSentences.sort((s1, s2) -> Integer.compare(s2.score, s1.score));

        List<String> result = new ArrayList<>();
        // 取前3句
        for (int i = 0; i < Math.min(3, scoredSentences.size()); i++) {
            String text = scoredSentences.get(i).text;
            // 補標點
            if(!text.matches(".*[。！？?!.]$")) {
                text += (isChinese ? "。" : ". ");
            }
            result.add(text);
        }
        return result;
    }

    private int calculateScore(String sentence, String query, boolean isChinese) {
        int score = 0;
        String lowerS = sentence.toLowerCase();
        String lowerQ = query.toLowerCase();

        if (lowerS.contains("cookies") || lowerS.contains("login") || lowerS.contains("rights reserved") || lowerS.contains("登入")) return -999;

        String[] keywords = lowerQ.split("\\s+");
        int matchCount = 0;
        
        for (String kw : keywords) {
            if (kw.length() < 1) continue;
            if (lowerS.contains(kw)) {
                score += 30;
                matchCount++;
            }
        }

        // 如果全部關鍵字都命中，給予額外加分
        if (matchCount == keywords.length && keywords.length > 0) {
            score += 40;
        }

        // 保底
        for (String gameName : GAME_NAMES) {
            if (lowerS.contains(gameName)) {
                score += 10;
                break;
            }
        }

        // 解釋性詞彙加分
        String[] explainWords = isChinese 
            ? new String[]{"是", "為", "意思", "攻略", "技巧", "排名", "最強", "玩法", "介紹"} 
            : new String[]{"is", "guide", "tips", "intro", "best", "tier", "how to", "build"};
            
        for (String w : explainWords) {
            if (lowerS.contains(w)) score += 5;
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