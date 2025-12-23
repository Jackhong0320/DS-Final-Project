package dsfinal.demo.logic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dsfinal.demo.model.WebPage;

/**
 * SemanticAnalyzer
 */
public class SemanticAnalyzer {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "的", "是", "在", "有", "與", "和", "了", "對", "也", "及", "等", "或", "之",
        "他", "你", "我", "它", "這", "那", "個", "位", "名", "其", "實", "讓",
        "認", "為", "覺", "得", "顯", "示", "看", "到", "做", "作", "用",
        "the", "is", "of", "in", "to", "for", "a", "an", "on", "at", "by", "and", "or", "it", "with", "from", "about"
    ));

    public List<String> deriveRelatedKeywords(List<WebPage> topPages, String userQuery) {
        Map<String, Integer> candidateFreq = new HashMap<>();
        String lowerQuery = userQuery.toLowerCase().trim();
        
        boolean hasSpace = userQuery.contains(" ");
        String firstWord = hasSpace ? userQuery.split("\\s+")[0] : userQuery;
        
        List<WebPage> targetPages = topPages.size() > 10 ? topPages.subList(0, 10) : topPages;

        for (WebPage page : targetPages) {
            String rawTitle = page.title;
            if (rawTitle == null) continue;

            // 清洗
            
            String cleanTitle = rawTitle.replaceAll("(?i)(\\s*[-|–:_]\\s*).*$", "")
                                   .replaceAll("【.*?】", " ")
                                   .replaceAll("\\[.*?\\]", " ")
                                   .replaceAll("\\(.*?\\)", " ")
                                   .replaceAll("《.*?》", " ");
            
            // 加入 \\p{M} 支援聲調符號
            cleanTitle = cleanTitle.replaceAll("[^\\p{L}\\p{N}\\p{M}\\s]", " ");
            
            cleanTitle = cleanTitle.trim().replaceAll("\\s+", " ");
            String lowerTitle = cleanTitle.toLowerCase();

            // 挖掘邏輯
            
            if (hasSpace) {
                // 空格查詢策略
                extractSecondWords(cleanTitle, firstWord, candidateFreq);
            } else {
                // 無空格查詢策略 (包含錯字處理)
                int idx = lowerTitle.indexOf(lowerQuery);
                
                // 如果精確比對找不到，嘗試「模糊比對」
                if (idx == -1) {
                    idx = findFuzzyMatchIndex(lowerTitle, lowerQuery);
                }

                while (idx != -1) {
                    // 從找到的位置往後抓
                    String suffix = cleanTitle.substring(idx + userQuery.length());
                    suffix = trimLeadingStopWords(suffix);
                    if (!suffix.isEmpty()) {
                        extractCandidates(suffix, candidateFreq);
                    }
                    // 繼續找下一個
                    if (lowerTitle.indexOf(lowerQuery, idx + 1) != -1) {
                        idx = lowerTitle.indexOf(lowerQuery, idx + 1);
                    } else {
                        break; 
                    }
                }
            }
        }

        // 排序與輸出
        List<String> suggestions = new java.util.ArrayList<>();
        Set<Character> usedChars = new HashSet<>();
        
        candidateFreq.entrySet().stream()
            .sorted((a, b) -> {
                int freqCompare = b.getValue().compareTo(a.getValue());
                if (freqCompare != 0) return freqCompare;
                return Integer.compare(a.getKey().length(), b.getKey().length());
            })
            .map(Map.Entry::getKey)
            .filter(s -> isValidCandidate(s, lowerQuery))
            .forEach(candidate -> {
                if (suggestions.size() >= 10) return;
                if (containsUsedChars(candidate, usedChars)) return;
                
                // 如果使用者打錯字，建議時還是用原本打的字 + 建議詞
                String suggestion = hasSpace ? firstWord + " " + candidate : userQuery + " " + candidate;
                suggestions.add(suggestion);
                
                for (char c : candidate.toCharArray()) {
                    if (Character.isLetterOrDigit(c)) usedChars.add(Character.toLowerCase(c));
                }
            });
        
        return suggestions;
    }
    
    /**
     * 模糊比對：在標題中尋找與 Query 最像的片段，允許一定比例的錯字
     */
    private int findFuzzyMatchIndex(String title, String query) {
        if (title.length() < query.length()) return -1;
        if (query.length() < 2) return -1; // 太短不模糊比對

        int bestIdx = -1;
        // 允許 25% 的錯誤
        int maxErrors = Math.max(1, query.length() / 4); 
        int minDiff = Integer.MAX_VALUE;

        // 滑動視窗掃描整串標題
        for (int i = 0; i <= title.length() - query.length(); i++) {
            String sub = title.substring(i, i + query.length());
            int diff = 0;
            
            // 計算差異字元數
            for (int j = 0; j < query.length(); j++) {
                if (sub.charAt(j) != query.charAt(j)) {
                    diff++;
                }
            }
            
            // 如果差異在容許範圍內，且是目前最像的
            if (diff <= maxErrors && diff < minDiff) {
                minDiff = diff;
                bestIdx = i;
            }
        }
        
        if (minDiff > maxErrors) return -1;
        
        return bestIdx;
    }

    private void extractSecondWords(String title, String firstWord, Map<String, Integer> freqMap) {
        String lowerTitle = title.toLowerCase();
        String lowerFirst = firstWord.toLowerCase();
        int idx = lowerTitle.indexOf(lowerFirst);
        
        // 如果空格搜尋的第一個詞也打錯 (例如 "荒也 攻略")，也嘗試模糊比對
        if (idx == -1) {
            idx = findFuzzyMatchIndex(lowerTitle, lowerFirst);
        }

        while (idx != -1) {
            int start = idx + firstWord.length();
            if (start < title.length()) {
                String suffix = title.substring(start).trim();
                if (!suffix.isEmpty()) {
                    String[] parts = suffix.split("\\s+");
                    if (parts.length > 0 && parts[0].length() >= 2) {
                        String secondWord = parts[0];
                        if (!STOP_WORDS.contains(secondWord.toLowerCase())) {
                            freqMap.put(secondWord, freqMap.getOrDefault(secondWord, 0) + 3);
                        }
                    }
                    String noSpace = suffix.replaceAll("\\s+", "");
                    for (int len = 2; len <= Math.min(4, noSpace.length()); len++) {
                        String gram = noSpace.substring(0, len);
                        if (!containsStopWord(gram.toLowerCase())) {
                            freqMap.put(gram, freqMap.getOrDefault(gram, 0) + 1);
                        }
                    }
                }
            }
            // 模糊比對後只取第一個最像的
            break;
        }
    }
    
    
    private boolean containsUsedChars(String candidate, Set<Character> usedChars) {
        for (char c : candidate.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (usedChars.contains(Character.toLowerCase(c))) return true;
            }
        }
        return false;
    }

    private String trimLeadingStopWords(String text) {
        String processed = text;
        for (int i = 0; i < 3; i++) {
            processed = processed.trim();
            if (processed.isEmpty()) return "";
            String firstChar = processed.substring(0, 1);
            if (STOP_WORDS.contains(firstChar)) {
                processed = processed.substring(1);
                continue;
            }
            int spaceIdx = processed.indexOf(" ");
            if (spaceIdx != -1) {
                String firstWord = processed.substring(0, spaceIdx).toLowerCase();
                if (STOP_WORDS.contains(firstWord)) {
                    processed = processed.substring(spaceIdx + 1);
                    continue;
                }
            }
            break; 
        }
        return processed;
    }

    private void extractCandidates(String suffix, Map<String, Integer> freqMap) {
        String[] parts = suffix.split("\\s+");
        if (parts.length > 0) {
            String word = parts[0].trim();
            if (word.length() > 1 && !STOP_WORDS.contains(word.toLowerCase())) {
                freqMap.put(word, freqMap.getOrDefault(word, 0) + 3);
            }
        }
        if (suffix.length() >= 2) {
            String two = suffix.substring(0, 2);
            if (!containsStopWord(two) && !two.contains(" ")) {
                freqMap.put(two, freqMap.getOrDefault(two, 0) + 1);
            }
        }
        if (suffix.length() >= 3) {
            String three = suffix.substring(0, 3);
            if (!containsStopWord(three) && !three.contains(" ")) {
                freqMap.put(three, freqMap.getOrDefault(three, 0) + 1);
            }
        }
    }

    private boolean containsStopWord(String s) {
        for (String stop : STOP_WORDS) if (s.contains(stop)) return true;
        return false;
    }

    private boolean isValidCandidate(String s, String query) {
        if (s.length() < 2) return false;
        if (s.length() > 13) return false;
        if (s.toLowerCase().contains(query)) return false;
        if (s.matches("[0-9]+")) return false;
        return true;
    }
}