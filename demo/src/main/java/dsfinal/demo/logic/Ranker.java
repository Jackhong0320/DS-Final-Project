package dsfinal.demo.logic;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dsfinal.demo.model.WebPage;

public class Ranker {
    
    private final String[] THEME_KEYWORDS = {
        "荒野亂鬥", "Brawl Stars", "brawl star", "brawl",
        "ブロスタ", "브롤스타즈", "브롤", "براول ستارز"
    };

    private final String[] AUTHORITY_DOMAINS = {
        "wikipedia.org", "fandom.com", "wiki", "game8", "gamewith", "inven", "dcard",
        "supercell.com", "gamer.com.tw", "facebook.com"
    };

    private final Map<String, Set<String>> translationCache = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * [修改] 新增 Document 參數，讓 Ranker 可以分析網頁內的連結
     */
    public double calculatePageScore(WebPage page, String userQuery, Document doc) {
        if (page.content == null) page.content = ""; 
        
        double score = 0.0;
        String fullText = (page.title + " " + page.content).toLowerCase();
        String query = userQuery.toLowerCase();
        String url = page.url.toLowerCase();

        // 1. 主題檢查
        boolean isThemeRelated = false;
        for (String themeWord : THEME_KEYWORDS) {
            if (fullText.contains(themeWord.toLowerCase())) {
                isThemeRelated = true;
                break;
            }
        }
        if (isThemeRelated) score += 30.0;
        else score -= 50.0;

        // 2. 關鍵字命中計算
        String[] keywords = query.split("\\s+");
        if (keywords.length > 0) {
            String firstKeyword = keywords[0];
            boolean hasFirstKeyword = containsWithTranslations(fullText, firstKeyword);
            boolean firstInTitle = containsWithTranslations(page.title.toLowerCase(), firstKeyword);
            
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
            
            if (hasFirstKeyword && hasAllOtherKeywords) {
                score += 100.0; 
                if (firstInTitle) score += 30.0; 
                score += otherKeywordsInTitle * 20.0; 
            } else if (hasFirstKeyword) {
                score += 40.0; 
                if (firstInTitle) score += 20.0;
                score += otherKeywordsMatched * 5.0;
            } else if (otherKeywordsMatched > 0) {
                score += 10.0; 
                score += otherKeywordsMatched * 3.0;
            } else {
                score -= 30.0; 
            }
        }

        // 3. 權威網站加分
        for (String domain : AUTHORITY_DOMAINS) {
            if (url.contains(domain)) {
                score += 10.0; 
                break;
            }
        }

        // 4. [新增功能] 子網頁挖掘與評分 (Sitelinks)
        if (doc != null) {
            double subPagesBonus = processSubPages(page, doc, query);
            // 讓子網頁的分數稍微影響主網頁，但不要太多 (權重 0.2)
            score += (subPagesBonus * 0.2);
        }

        page.topicScore = score;
        return score;
    }

    /**
     * [核心新功能] 挖掘子網頁並計算加分
     */
    private double processSubPages(WebPage parentPage, Document doc, String userQuery) {
        Elements links = doc.select("a[href]");
        Set<String> seenUrls = new HashSet<>();
        seenUrls.add(parentPage.url); // 排除自己
        
        List<WebPage> candidates = new ArrayList<>();
        String parentDomain = getDomain(parentPage.url);

        for (Element link : links) {
            String subUrl = link.attr("abs:href"); // 取得絕對路徑
            String subTitle = link.text().trim();

            if (subUrl.isEmpty() || subTitle.length() < 2) continue;
            if (seenUrls.contains(subUrl)) continue;
            // 必須是同網域 (簡單判斷)
            if (!subUrl.contains(parentDomain)) continue; 
            // 排除一些明顯的功能連結
            if (subTitle.contains("登入") || subTitle.contains("Login") || subTitle.contains("Privacy")) continue;

            seenUrls.add(subUrl);

            // --- 子網頁評分邏輯 ---
            double subScore = 0.0;
            String lowerTitle = subTitle.toLowerCase();
            
            // 規則 A: 包含主題關鍵字 (高分)
            for(String theme : THEME_KEYWORDS) {
                if(lowerTitle.contains(theme.toLowerCase())) {
                    subScore += 20.0; // 主題相關加最多
                    break; 
                }
            }

            // 規則 B: 包含使用者搜尋關鍵字 (次高分)
            if (lowerTitle.contains(userQuery.toLowerCase())) {
                subScore += 15.0;
            }
            
            // 規則 C: 連結文字長度適中 (避免雜訊)
            //if (subTitle.length() >= 4 && subTitle.length() <= 20) {
             //   subScore += 5.0;
            //}

            // 只有分數夠高的才列入候選
            if (subScore > 0) {
                WebPage subPage = new WebPage(subUrl, subTitle);
                subPage.topicScore = subScore;
                candidates.add(subPage);
            }
        }

        // 排序候選子網頁 (分數高 -> 低)
        Collections.sort(candidates, (a, b) -> Double.compare(b.topicScore, a.topicScore));

        // 取前 3 名
        double totalBonus = 0.0;
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            WebPage bestSub = candidates.get(i);
            parentPage.subPages.add(bestSub);
            totalBonus += bestSub.topicScore;
        }

        return totalBonus;
    }

    // 簡單取得網域 helper
    private String getDomain(String url) {
        try {
            // 簡單處理：取 // 後面直到下一個 / 之前的字串
            int start = url.indexOf("://");
            if(start == -1) return "";
            start += 3;
            int end = url.indexOf("/", start);
            if(end == -1) return url.substring(start);
            return url.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean containsWithTranslations(String textLower, String keywordRaw) {
        String keyword = keywordRaw.toLowerCase();
        if (textLower.contains(keyword)) return true;
        Set<String> variants = getTranslations(keyword);
        for (String variant : variants) {
            if (variant.isEmpty()) continue;
            if (textLower.contains(variant)) return true;
        }
        return false;
    }

    private Set<String> getTranslations(String keyword) {
        String key = keyword.toLowerCase();
        if (translationCache.containsKey(key)) return translationCache.get(key);
        Set<String> variants = new LinkedHashSet<>();
        variants.add(key);
        List<String> targets = detectLikelyLangCodes(key);
        for (String target : targets) {
            try {
                String translated = translateViaMyMemory(key, target);
                if (translated != null && !translated.isEmpty()) {
                    variants.add(translated.toLowerCase());
                }
            } catch (Exception ignored) {}
        }
        translationCache.put(key, variants);
        return variants;
    }

    private List<String> detectLikelyLangCodes(String text) {
        Set<String> codes = new LinkedHashSet<>();
        String[] common = {"en", "zh-TW", "zh-CN", "ja", "ko"};
        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) codes.add("zh-TW");
        }
        Collections.addAll(codes, common);
        return new ArrayList<>(codes);
    }

    private String translateViaMyMemory(String text, String targetLang) throws Exception {
        String urlStr = "https://api.mymemory.translated.net/get?q=" + 
                java.net.URLEncoder.encode(text, StandardCharsets.UTF_8) + "&langpair=auto|" + targetLang;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(1000); 
        conn.setReadTimeout(1000);
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
            String resp = scanner.useDelimiter("\\A").next();
            JsonNode root = mapper.readTree(resp);
            return root.path("responseData").path("translatedText").asText();
        }
    }
}