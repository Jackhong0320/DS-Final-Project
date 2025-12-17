package dsfinal.demo.logic;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dsfinal.demo.model.WebPage;

public class Ranker {
    
    // 支援主題關鍵字
    private final String[] THEME_KEYWORDS = {
        "荒野亂鬥", "Brawl Stars", "Supercell", "brawl", 
        "ブロスタ", "브롤스타즈", "Clash", "브롤", "براول ستارز"
    };

    private final String[] AUTHORITY_DOMAINS = {
        "wikipedia.org", "fandom.com", "wiki", "game8", "gamewith", "inven", "dcard",
        "supercell.com", "gamer.com.tw"
    };

    // 簡單翻譯快取，避免重複呼叫外部翻譯服務
    private final Map<String, Set<String>> translationCache = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public double calculatePageScore(WebPage page, String userQuery) {
        if (page.content == null) page.content = ""; 
        
        double score = 0.0;
        String fullText = (page.title + " " + page.content).toLowerCase();
        String query = userQuery.toLowerCase();
        String url = page.url.toLowerCase();

        // 1. 主題檢查 - 必須與荒野亂鬥相關
        boolean isThemeRelated = false;
        for (String themeWord : THEME_KEYWORDS) {
            if (fullText.contains(themeWord.toLowerCase())) {
                isThemeRelated = true;
                break;
            }
        }
        if (isThemeRelated) score += 30.0; // 提高主題相關性權重
        else score -= 50.0; // 不相關

        // 2. 拆字搜尋 - 第一個關鍵字最重要
        String[] keywords = query.split("\\s+");
        
        if (keywords.length == 0) {
            // 沒有關鍵字，不處理
            page.topicScore = score;
            return score;
        }
        
        // 第一個關鍵字
        String firstKeyword = keywords[0];
        boolean hasFirstKeyword = containsWithTranslations(fullText, firstKeyword);
        boolean firstInTitle = containsWithTranslations(page.title.toLowerCase(), firstKeyword);
        
        // 檢查其他關鍵字
        int otherKeywordsMatched = 0;
        int otherKeywordsInTitle = 0;
        for (int i = 1; i < keywords.length; i++) {
            if (keywords[i].length() < 1) continue;
            if (containsWithTranslations(fullText, keywords[i])) {
                otherKeywordsMatched++;
                if (containsWithTranslations(page.title.toLowerCase(), keywords[i])) {
                    otherKeywordsInTitle++;
                }
            }
        }
        
        int totalOtherKeywords = keywords.length - 1;
        boolean hasAllOtherKeywords = (totalOtherKeywords == 0) || (otherKeywordsMatched == totalOtherKeywords);
        
        // 權重分級：
        // 第一級：第一個關鍵字 + 所有其他關鍵字都符合
        if (hasFirstKeyword && hasAllOtherKeywords) {
            score += 100.0; // 最高權重
            if (firstInTitle) score += 30.0; // 第一個關鍵字在標題
            score += otherKeywordsInTitle * 20.0; // 其他關鍵字在標題也加分
        }
        // 第二級：只有第一個關鍵字符合
        else if (hasFirstKeyword && !hasAllOtherKeywords) {
            score += 40.0; // 第二權重
            if (firstInTitle) score += 20.0;
            // 部分其他關鍵字符合也給點分
            score += otherKeywordsMatched * 5.0;
        }
        // 第三級：沒有第一個關鍵字，但有其他關鍵字
        else if (!hasFirstKeyword && otherKeywordsMatched > 0) {
            score += 10.0; // 第三權重
            score += otherKeywordsMatched * 3.0;
        }
        // 第四級：完全沒有任何關鍵字
        else {
            score -= 30.0; // 重重扣分
        }

        // 3. 微調
        for (String domain : AUTHORITY_DOMAINS) {
            if (url.contains(domain)) {
                score += 10.0; 
                break;
            }
        }

        page.topicScore = score;
        return score;
    }

    private boolean containsWithTranslations(String textLower, String keywordRaw) {
        String keyword = keywordRaw.toLowerCase();
        if (textLower.contains(keyword)) return true;

        // 嘗試取得翻譯後的同義詞，支援多語混搭場景
        Set<String> variants = getTranslations(keyword);
        for (String variant : variants) {
            if (variant.isEmpty()) continue;
            if (textLower.contains(variant)) return true;
        }
        return false;
    }

    /**
     * 取得關鍵字的跨語翻譯變體（使用免費 MyMemory API），並快取結果。
     * 會嘗試多個目標語言：動態偵測 + 常見語言集合，覆蓋更多語系。
     */
    private Set<String> getTranslations(String keyword) {
        String key = keyword.toLowerCase();
        if (translationCache.containsKey(key)) return translationCache.get(key);

        Set<String> variants = new LinkedHashSet<>();
        variants.add(key);

        // 動態判斷可能語系，並加入常用語系以涵蓋更多嘗試
        List<String> targets = detectLikelyLangCodes(key);
        for (String target : targets) {
            try {
                String translated = translateViaMyMemory(key, target);
                if (translated != null && !translated.isEmpty()) {
                    variants.add(translated.toLowerCase());
                }
            } catch (Exception ignored) {
                // 如果翻譯失敗，忽略該語言
            }
        }

        translationCache.put(key, variants);
        return variants;
    }

    /**
     * 簡單偵測文字可能語系並組合常見語系清單。
     * 目標：覆蓋更多語言
     */
    private List<String> detectLikelyLangCodes(String text) {
        Set<String> codes = new LinkedHashSet<>();

        // 常見語言全集（廣覆蓋）：
        String[] common = {"en", "zh-TW", "zh-CN", "ja", "ko", "es", "fr", "de", "ru", "ar", "pt", "hi", "id", "vi", "th", "tr", "it", "nl", "pl"};

        // 基於 Unicode 區段粗略判斷主要語系
        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) codes.add("zh-TW");
            else if (block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA) codes.add("ja");
            else if (block == Character.UnicodeBlock.HANGUL_SYLLABLES || block == Character.UnicodeBlock.HANGUL_JAMO) codes.add("ko");
            else if (block == Character.UnicodeBlock.CYRILLIC) codes.add("ru");
            else if (block == Character.UnicodeBlock.ARABIC) codes.add("ar");
            else if (block == Character.UnicodeBlock.GREEK) codes.add("el");
            else if (block == Character.UnicodeBlock.HEBREW) codes.add("he");
            else if (block == Character.UnicodeBlock.THAI) codes.add("th");
            else if (block == Character.UnicodeBlock.DEVANAGARI) codes.add("hi");
        }

        // 加入常見語言全集，確保涵蓋更多語系嘗試
        Collections.addAll(codes, common);

        return new ArrayList<>(codes);
    }

    /**
     * 呼叫 MyMemory 免費翻譯 API
     */
    private String translateViaMyMemory(String text, String targetLang) throws Exception {
        String urlStr = "https://api.mymemory.translated.net/get?q=" + 
                java.net.URLEncoder.encode(text, StandardCharsets.UTF_8) + "&langpair=auto|" + targetLang;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
            String resp = scanner.useDelimiter("\\A").next();
            JsonNode root = mapper.readTree(resp);
            JsonNode translatedNode = root.path("responseData").path("translatedText");
            if (translatedNode.isMissingNode()) return null;
            return translatedNode.asText();
        }
    }
}