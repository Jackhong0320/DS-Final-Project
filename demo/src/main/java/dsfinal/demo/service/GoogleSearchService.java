package dsfinal.demo.service;

import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dsfinal.demo.logic.Ranker;
import dsfinal.demo.model.WebPage;

@Service
public class GoogleSearchService {

    // 額度不夠可自行修改API Key 和 Search Engine ID
    private final String GOOGLE_API_KEY = "AIzaSyDe9f1O6NEdXEcY-dwC88ECOpFhHU0D1So"; 
    private final String SEARCH_ENGINE_ID = "b25a4f1b2c45547ca"; 
    
    private final String BASE_URL = "https://www.googleapis.com/customsearch/v1?key=" + GOOGLE_API_KEY + "&cx=" + SEARCH_ENGINE_ID;
    
    private Ranker ranker = new Ranker();

    public List<WebPage> searchAndRank(String query) {
        System.out.println(">>> 系統收到搜尋請求: " + query);
        List<WebPage> pages = new ArrayList<>();
        
        String searchTerm = query;
        String langParam = "";
        
        String[] keywords = query.trim().split("\\s+");
        String firstKeyword = keywords.length > 0 ? keywords[0] : query;
        
        if (containsChinese(firstKeyword)) {
            if (!query.contains("荒野")) searchTerm = query + " 荒野亂鬥";
            langParam = "&lr=lang_zh-TW";
        } else if (containsJapanese(firstKeyword)) {
            if (!query.contains("ブロスタ") && !query.contains("Brawl")) searchTerm = query + " ブロスタ";
            langParam = "&lr=lang_ja";
        } else if (containsKorean(firstKeyword)) {
            if (!query.contains("브롤") && !query.contains("Brawl")) searchTerm = query + " 브롤스타즈";
            langParam = "&lr=lang_ko";
        } else if (containsArabic(firstKeyword)) {
            if (!query.contains("براول") && !query.contains("Brawl")) {
                searchTerm = query + " براول ستارز";
            }
            langParam = "&lr=lang_ar";
        } else {
            if (!query.toLowerCase().contains("brawl")) {
                searchTerm = query + " Brawl Stars";
            }
        }

        try {
            String url = BASE_URL + "&q=" + searchTerm + langParam;
            RestTemplate restTemplate = new RestTemplate();
            String resultJson = restTemplate.getForObject(url, String.class);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(resultJson);
            JsonNode items = root.path("items");

            if (items.isMissingNode() || items.size() == 0) {
                return new ArrayList<>(); 
            }

            // 第一次遍歷：正常評分
            for (JsonNode item : items) {
                String title = item.path("title").asText();
                String link = item.path("link").asText();
                String snippet = item.path("snippet").asText();

                WebPage page = new WebPage(link, title);
                Document doc = null; 

                try {
                    // 嘗試爬取網頁內容
                    doc = Jsoup.connect(link)
                           .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36") // 偽裝成更像真實的瀏覽器
                           .timeout(3000)
                           .get();
                    
                    String crawledText = doc.body().text();
                    page.setContent(crawledText + " " + snippet);

                } catch (Exception e) {
                    page.setContent(snippet);
                }

                ranker.calculatePageScore(page, query, doc);
                pages.add(page);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }

        Collections.sort(pages, (o1, o2) -> Double.compare(o2.topicScore, o1.topicScore));
        return pages;
    }

    // 語言偵測
    private boolean containsChinese(String s) {
        for (char c : s.toCharArray()) if (UnicodeBlock.of(c) == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) return true;
        return false;
    }
    
    private boolean containsJapanese(String s) {
        for (char c : s.toCharArray()) if (UnicodeBlock.of(c) == UnicodeBlock.HIRAGANA || UnicodeBlock.of(c) == UnicodeBlock.KATAKANA) return true;
        return false;
    }

    private boolean containsKorean(String s) {
        for (char c : s.toCharArray()) if (UnicodeBlock.of(c) == UnicodeBlock.HANGUL_SYLLABLES || UnicodeBlock.of(c) == UnicodeBlock.HANGUL_JAMO) return true;
        return false;
    }
    
    private boolean containsArabic(String s) {
        for (char c : s.toCharArray()) if (UnicodeBlock.of(c) == UnicodeBlock.ARABIC) return true;
        return false;
    }

    private List<WebPage> generateDummyData(String query) {
        return new ArrayList<>();
    }
}