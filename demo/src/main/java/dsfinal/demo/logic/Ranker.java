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
        "荒野亂鬥", "Brawl Stars", "brawl star", "brawl", "Supercell",
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

        String fullText = (page.title + " " + page.content).toLowerCase();
        String query = userQuery.toLowerCase();
        String url = page.url.toLowerCase();
        
        String titleLower = page.title.toLowerCase();

        // 1. 主題檢查
        boolean isThemeRelated = false;
        boolean themeInTitle = false;
        boolean themeInContent = false;
        
        for (String themeWord : THEME_KEYWORDS) {
            if (titleLower.contains(themeWord.toLowerCase())) {
                themeInTitle = true;
                break;
            }
        }
        
        for (String themeWord : THEME_KEYWORDS) {
            if (fullText.contains(themeWord.toLowerCase())) {
                themeInContent = true;
                break;
            }
        }

        if (themeInTitle && themeInContent) {
            score += 60.0; // 標題和內文都有
            isThemeRelated = true;
        } else if (themeInTitle) {
            score += 40.0; // 只有標題有
            isThemeRelated = true;
        } else if (themeInContent) {
            score += 10.0; // 只有內文有
            isThemeRelated = true;
        } else {
            score -= 50.0; // 都沒有
            isThemeRelated = false;
        }

        // 關鍵字命中計算
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
        }

        // 權威網站加分
        boolean isAuthority = false;
        for (String domain : AUTHORITY_DOMAINS) {
            if (url.contains(domain)) {
                isAuthority = true;
                break;
            }
        }
        
        if (isAuthority && isThemeRelated && keywordScore > 0) {
            score += 15.0;
        }

        // 子網頁挖掘與評分
        if (doc != null) {
            double subPagesBonus = processSubPages(page, doc, query);
            double actualBonus = subPagesBonus * 0.2;
            if (actualBonus > 0) {
                score += actualBonus;
            }
        }

        page.scoreDetails = String.format("%.1f", score);
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