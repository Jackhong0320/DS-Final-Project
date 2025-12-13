package dsfinal.demo.logic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dsfinal.demo.model.WebPage;

public class SemanticAnalyzer {

    // 定義停用詞：如果抓到的詞是以這些開頭，通常不是好的關鍵字
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "的", "是", "在", "有", "與", "和", "了", "the", "is", "of", "in", "to", "for", "a", "an"
    ));

    public List<String> deriveRelatedKeywords(List<WebPage> topPages, String userQuery) {
        // 用來統計候選詞頻率的 Map (候選詞 -> 出現次數)
        Map<String, Integer> candidateFreq = new HashMap<>();
        String lowerQuery = userQuery.toLowerCase().trim();
        
        // 擴大樣本數：看前 30 名標題，樣本越多，"配置"、"WIKI" 這種熱詞的統計就會越準確
        List<WebPage> targetPages = topPages.size() > 5 ? topPages.subList(0, 5) : topPages;

        for (WebPage page : targetPages) {
            String rawTitle = page.title;
            if (rawTitle == null) continue;

            // --- 步驟 1: 強力清洗 (Surgical Cleaning) ---
            // 這是產生乾淨建議的關鍵，修正之前 "雪莉 |" 或 "雪莉 #荒" 的問題
            
            // 1. 移除括號與內容 (去除雜訊)
            String cleanTitle = rawTitle.replaceAll("【.*?】", " ")
                                        .replaceAll("\\[.*?\\]", " ")
                                        .replaceAll("\\(.*?\\)", " ")
                                        .replaceAll("《.*?》", " "); // 移除書名號
            
            // 2. 移除網站後綴 (只要遇到 | - _ : 就切斷)
            // 這裡改進了 regex，只要有分隔符號就視為結尾
            cleanTitle = cleanTitle.replaceAll("(?i)(\\s*[-|–:_]\\s*).*$", "");
            
            // 3. 移除特殊標點符號 (包含井號 #)
            cleanTitle = cleanTitle.replaceAll("[!！?？,，.。~～#]", " ");
            
            // 4. 合併空白並去頭尾
            cleanTitle = cleanTitle.trim().replaceAll("\\s+", " ");
            
            String lowerTitle = cleanTitle.toLowerCase();

            // --- 步驟 2: 後綴挖掘 (Suffix Mining) ---
            // 找出關鍵字在標題中的位置，然後分析它「後面」接了什麼
            
            int idx = lowerTitle.indexOf(lowerQuery);
            if (idx != -1) {
                // 截取關鍵字後面的字串 (Suffix)
                String suffix = cleanTitle.substring(idx + userQuery.length()).trim();
                
                if (!suffix.isEmpty()) {
                    // 對這個後綴進行 N-gram 採樣
                    extractNGrams(suffix, candidateFreq);
                }
            }
        }

        // --- 步驟 3: 排序與過濾 ---
        List<String> suggestions = candidateFreq.entrySet().stream()
            .sorted((a, b) -> {
                // 頻率優先：出現越多次的詞越重要
                int freqCompare = b.getValue().compareTo(a.getValue());
                // 如果頻率相同，優先選短的 (例如 "配置" 優於 "配置介紹")，這符合 Google 風格
                if (freqCompare == 0) return Integer.compare(a.getKey().length(), b.getKey().length());
                return freqCompare;
            })
            .map(Map.Entry::getKey) // 取出詞彙
            .filter(s -> isValidCandidate(s)) // 過濾掉不合法的詞
            .limit(5) // 只取前 5 個
            .map(s -> userQuery + " " + s) // 組合：雪莉 + 配置
            .collect(Collectors.toList());

        // 如果挖出來的不足 5 個 (例如新角色資料太少)，用通用詞補滿
        if (suggestions.size() < 5) {
            fillFallbackSuggestions(suggestions, userQuery);
        }

        return suggestions;
    }

    /**
     * 從後綴字串中提取 N-gram 候選詞
     * 例如 suffix = "最強配置攻略"
     * 提取 -> "最強" (2字), "最強配" (3字)
     */
    private void extractNGrams(String suffix, Map<String, Integer> freqMap) {
        // 策略 A: 空白分隔 (適用於英文或混雜英文，如 "Wiki", "Skin")
        String[] parts = suffix.split("\\s+");
        if (parts.length > 0) {
            String firstWord = parts[0].trim();
            // 如果是純英文單字，直接加分
            if (firstWord.matches("^[a-zA-Z0-9]+$") && firstWord.length() > 1) {
                freqMap.put(firstWord, freqMap.getOrDefault(firstWord, 0) + 3); // 英文單字權重高
            }
        }

        // 策略 B: 連續字元切分 (適用於中文，如 "配置", "星能力")
        // 我們假設關鍵字通常是 2 個字或 3 個字
        
        // 2-gram (例如 "配置", "攻略", "造型")
        if (suffix.length() >= 2) {
            String twoChars = suffix.substring(0, 2);
            // 只有當它是中文字或英數混合時才算
            if (!STOP_WORDS.contains(twoChars)) {
                freqMap.put(twoChars, freqMap.getOrDefault(twoChars, 0) + 1);
            }
        }
        
        // 3-gram (例如 "星能力", "妙具", "懶人包")
        if (suffix.length() >= 3) {
            String threeChars = suffix.substring(0, 3);
            if (!STOP_WORDS.contains(threeChars)) {
                freqMap.put(threeChars, freqMap.getOrDefault(threeChars, 0) + 1);
            }
        }
    }

    /**
     * 檢查候選詞是否合法
     */
    private boolean isValidCandidate(String s) {
        // 過濾太短的 (1個字通常無意義)
        if (s.length() < 2) return false;
        
        // 過濾太長的 (超過 6 個字就是句子了，不是關鍵字)
        if (s.length() > 6) return false;
        
        // 過濾停用詞開頭 (例如 "的是", "的攻")
        for (String stop : STOP_WORDS) {
            if (s.startsWith(stop)) return false;
        }
        
        // 過濾純符號或純數字 (除非是年份如 2025)
        if (s.matches("[0-9]+") && s.length() < 4) return false;
        
        return true;
    }
    
    /**
     * 備用方案：如果真的什麼都挖不到，才用通用詞補
     */
    private void fillFallbackSuggestions(List<String> suggestions, String userQuery) {
        boolean isChinese = userQuery.chars().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS);
        String[] suffixes = isChinese 
            ? new String[]{" 攻略", " 技巧", " 排名", " 介紹"} 
            : new String[]{" Guide", " Wiki", " Tips", " Build"};

        for (String suffix : suffixes) {
            if (suggestions.size() >= 5) break;
            String key = userQuery + suffix;
            // 避免重複
            boolean exists = false;
            for(String s : suggestions) if(s.equalsIgnoreCase(key)) exists = true;
            
            if (!exists) suggestions.add(key);
        }
    }
}