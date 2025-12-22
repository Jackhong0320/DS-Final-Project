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
        "supercell.com", "gamer.com.tw", "facebook.com", "zh.moegirl.org.cn"
    };

    private final Map<String, Set<String>> translationCache = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public double calculatePageScore(WebPage page, String userQuery, Document doc) {
        if (page.content == null) page.content = ""; 
        
        double score = 0.0;
        StringBuilder sb = new StringBuilder();

        String fullText = (page.title + " " + page.content).toLowerCase();
        String query = userQuery.toLowerCase();
        String url = page.url.toLowerCase();
        
        // 轉小寫的標題，方便比對
        String titleLower = page.title.toLowerCase();

        // 1. 主題檢查 (修改為：標題+內文雙重檢查)
        boolean isThemeRelated = false;
        boolean themeInTitle = false;
        boolean themeInContent = false;
        
        // 檢查標題
        for (String themeWord : THEME_KEYWORDS) {
            if (titleLower.contains(themeWord.toLowerCase())) {
                themeInTitle = true;
                break;
            }
        }
        
        // 檢查內文 (這裡我們檢查 fullText，因為 fullText 包含 title + content，若 title 沒中但 fullText 中，代表 content 中)
        // 為了精確區分，我們可以只檢查 content，但為了保險起見，我們沿用 fullText 邏輯
        for (String themeWord : THEME_KEYWORDS) {
            if (fullText.contains(themeWord.toLowerCase())) {
                themeInContent = true;
                break;
            }
        }

        if (themeInTitle && themeInContent) {
            // 情況 A: 標題和內文都有 -> 最佳 -> +60
            score += 60.0;
            sb.append("[主題(標題+內文):60] ");
            isThemeRelated = true;
        } else if (themeInTitle) {
            // 情況 B: 只有標題有 (內文可能抓取失敗或太短) -> 次佳 -> +40
            score += 40.0;
            sb.append("[主題(僅標題):40] ");
            isThemeRelated = true;
        } else if (themeInContent) {
            // 情況 C: 只有內文有 (標題沒提) -> 配角 -> +10
            score += 10.0;
            sb.append("[主題(僅內文):10] ");
            isThemeRelated = true;
        } else {
            // 情況 D: 都沒有 -> 無關 -> -50
            score -= 50.0;
            sb.append("[非主題:-50] ");
            isThemeRelated = false;
        }

        // 2. 關鍵字命中計算
        String[] keywords = query.split("\\s+");
        double keywordScore = 0;
        
        if (keywords.length > 0) {
            String firstKeyword = keywords[0];
            boolean hasFirstKeyword = containsWithTranslations(fullText, firstKeyword);
            boolean firstInTitle = containsWithTranslations(page.title.toLowerCase(), firstKeyword);
            
            int otherKeywordsMatched = 0;
            for (int i = 1; i < keywords.length; i++) {
                if (keywords[i].length() < 1) continue;
                if (containsWithTranslations(fullText, keywords[i])) {
                    otherKeywordsMatched++;
                }
            }
            
            if (hasFirstKeyword) {
                keywordScore += 40.0;
                if (firstInTitle) keywordScore += 20.0;
                keywordScore += otherKeywordsMatched * 10.0;
            } else if (otherKeywordsMatched > 0) {
                keywordScore += 10.0;
                keywordScore += otherKeywordsMatched * 5.0;
            } else {
                keywordScore -= 30.0;
            }
            
            score += keywordScore;
            if(keywordScore > 0) sb.append("+ [關鍵字:" + (int)keywordScore + "] ");
            else sb.append("- [關鍵字缺失] ");
        }

        // 3. 權威網站加分
        boolean isAuthority = false;
        for (String domain : AUTHORITY_DOMAINS) {
            if (url.contains(domain)) {
                isAuthority = true;
                break;
            }
        }
        // 只有當 (是權威站) AND (符合主題) AND (關鍵字分數 > 0) 時才加分
        if (isAuthority && isThemeRelated && keywordScore > 0) {
            score += 15.0;
            sb.append("+ [權威:15] ");
        }

        // 4. 子網頁挖掘與評分
        if (doc != null) {
            double subPagesBonus = processSubPages(page, doc, query);
            double actualBonus = subPagesBonus * 0.2;
            if (actualBonus > 0) {
                score += actualBonus;
                sb.append("+ [子網頁:" + String.format("%.1f", actualBonus) + "]");
            }
        }

        page.scoreDetails = sb.toString();
        page.topicScore = score;
        return score;
    }

    // 子網頁處理邏輯
    private double processSubPages(WebPage parentPage, Document doc, String userQuery) {
        Elements links = doc.select("a[href]");
        Set<String> seenUrls = new HashSet<>();
        seenUrls.add(parentPage.url); 
        
        List<WebPage> candidates = new ArrayList<>();
        String parentDomain = getDomain(parentPage.url);
        String lowerQuery = userQuery.toLowerCase();

        for (Element link : links) {
            String subUrl = link.attr("abs:href");
            String subTitle = link.text().trim();

            if (subUrl.isEmpty() || subTitle.length() < 2) continue;
            if (seenUrls.contains(subUrl)) continue;
            if (!subUrl.contains(parentDomain)) continue; 
            if (subTitle.contains("登入") || subTitle.contains("Login") || subTitle.contains("Privacy")) continue;

            seenUrls.add(subUrl);

            double subScore = 0.0;
            String lowerTitle = subTitle.toLowerCase();
            
            boolean hasTheme = false;
            for(String theme : THEME_KEYWORDS) {
                if(lowerTitle.contains(theme.toLowerCase())) {
                    hasTheme = true;
                    break; 
                }
            }
            boolean hasKeyword = lowerTitle.contains(lowerQuery);

            if (hasTheme && hasKeyword) {
                subScore = 20.0; 
            } else if (hasTheme) {
                subScore = 15.0; 
            } else if (hasKeyword) {
                subScore = 5.0; 
            }

            if (subScore > 0) {
                WebPage subPage = new WebPage(subUrl, subTitle);
                subPage.topicScore = subScore;
                candidates.add(subPage);
            }
        }

        Collections.sort(candidates, (a, b) -> Double.compare(b.topicScore, a.topicScore));

        double totalBonus = 0.0;
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            WebPage bestSub = candidates.get(i);
            parentPage.subPages.add(bestSub);
            totalBonus += bestSub.topicScore;
        }

        return totalBonus;
    }

    private String getDomain(String url) {
        try {
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