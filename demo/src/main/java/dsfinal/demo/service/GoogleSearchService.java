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

    private final String GOOGLE_API_KEY = "AIzaSyDe9f1O6NEdXEcY-dwC88ECOpFhHU0D1So"; 
    private final String SEARCH_ENGINE_ID = "b25a4f1b2c45547ca"; 
    
    private final String BASE_URL = "https://www.googleapis.com/customsearch/v1?key=" + GOOGLE_API_KEY + "&cx=" + SEARCH_ENGINE_ID;
    
    private Ranker ranker = new Ranker();

    public List<WebPage> searchAndRank(String query) {
        System.out.println(">>> 系統收到搜尋請求: " + query);
        List<WebPage> pages = new ArrayList<>();
        
        // --- 以第一個關鍵字的語言為主 ---
        String searchTerm = query;
        String langParam = "";
        
        // 分割關鍵字，取第一個關鍵字作為語言判斷依據
        String[] keywords = query.trim().split("\\s+");
        String firstKeyword = keywords.length > 0 ? keywords[0] : query;
        
        System.out.println(">>> 第一個關鍵字: " + firstKeyword);

        if (containsChinese(firstKeyword)) {
            if (!query.contains("荒野")) searchTerm = query + " 荒野亂鬥";
            langParam = "&lr=lang_zh-TW";
            System.out.println(">>> 偵測到中文，鎖定繁體中文搜尋");
            
        } else if (containsJapanese(firstKeyword)) {
            if (!query.contains("ブロスタ") && !query.contains("Brawl")) searchTerm = query + " ブロスタ";
            langParam = "&lr=lang_ja";
            System.out.println(">>> 偵測到日文，鎖定日文搜尋");
            
        } else if (containsKorean(firstKeyword)) {
            if (!query.contains("브롤") && !query.contains("Brawl")) searchTerm = query + " 브롤스타즈";
            langParam = "&lr=lang_ko";
            System.out.println(">>> 偵測到韓文，鎖定韓文搜尋");
            
        } else if (containsArabic(firstKeyword)) {
            langParam = "&lr=lang_ar";
            System.out.println(">>> 偵測到阿拉伯文，鎖定阿拉伯文搜尋");
            
        } else {
            // 其他 (英文/西文等)
            if (!query.toLowerCase().contains("brawl")) {
                searchTerm = query + " Brawl Stars";
            }
            System.out.println(">>> 偵測到英文或其他語言，不限制語言");
        }

        try {
            // 組裝 URL，加入 langParam 強制鎖定語言
            String url = BASE_URL + "&q=" + searchTerm + langParam;
            System.out.println(">>> 呼叫 Google URL (Term: " + searchTerm + ", Lang: " + langParam + ")");

            RestTemplate restTemplate = new RestTemplate();
            String resultJson = restTemplate.getForObject(url, String.class);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(resultJson);
            JsonNode items = root.path("items");

            if (items.isMissingNode() || items.size() == 0) {
                throw new RuntimeException("API 查無資料");
            }

            for (JsonNode item : items) {
                String title = item.path("title").asText();
                String link = item.path("link").asText();
                String snippet = item.path("snippet").asText();

                WebPage page = new WebPage(link, title);
                try {
                    Document doc = Jsoup.connect(link).userAgent("Mozilla/5.0").timeout(2000).get();
                    page.setContent(doc.body().text());
                } catch (Exception e) {
                    page.setContent(snippet);
                }

                ranker.calculatePageScore(page, query);
                pages.add(page);
            }

        } catch (Exception e) {
            System.err.println(">>> 發生錯誤，切換至演示模式");
            return generateDummyData(query);
        }

        Collections.sort(pages, (o1, o2) -> Double.compare(o2.topicScore, o1.topicScore));
        return pages;
    }

    // --- 語言偵測工具區 ---
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
        // (保持原樣)
        return new ArrayList<>();
    }
}