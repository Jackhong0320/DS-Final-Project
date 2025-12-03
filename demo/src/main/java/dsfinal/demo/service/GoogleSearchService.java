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

    private final String GOOGLE_API_KEY = "AIzaSyA-NrUymsiuPx8zUnQ6PaZl5Gpo3I36eT4"; 
    private final String SEARCH_ENGINE_ID = "b25a4f1b2c45547ca"; 
    
    private final String BASE_URL = "https://www.googleapis.com/customsearch/v1?key=" + GOOGLE_API_KEY + "&cx=" + SEARCH_ENGINE_ID;
    
    private Ranker ranker = new Ranker();

    public List<WebPage> searchAndRank(String query) {
        System.out.println(">>> 系統收到搜尋請求: " + query);
        List<WebPage> pages = new ArrayList<>();
        
        // --- [修改 1] 智慧語言鎖定策略 ---
        String searchTerm = query;
        String langParam = ""; // Google 的 lr 參數

        if (containsChinese(query)) {
            // 中文：加上中文名，限制繁體中文 (避免搜到簡體或日文漢字)
            if (!query.contains("荒野")) searchTerm = query + " 荒野亂鬥";
            langParam = "&lr=lang_zh-TW";
            
        } else if (containsJapanese(query)) {
            // 日文：不加英文名，限制日文
            // 日本人習慣搜 "ブロスタ"，不用加 Brawl Stars
            langParam = "&lr=lang_ja";
            
        } else if (containsKorean(query)) {
            // 韓文：限制韓文
            langParam = "&lr=lang_ko";
            
        } else if (containsArabic(query)) {
            // 阿拉伯文：限制阿拉伯文
            langParam = "&lr=lang_ar";
            
        } else {
            // 其他 (英文/西文等)：加上 Brawl Stars 確保是遊戲相關，不限語言
            if (!query.toLowerCase().contains("brawl")) {
                searchTerm = query + " Brawl Stars";
            }
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
    
    // 新增阿拉伯文偵測
    private boolean containsArabic(String s) {
        for (char c : s.toCharArray()) if (UnicodeBlock.of(c) == UnicodeBlock.ARABIC) return true;
        return false;
    }

    private List<WebPage> generateDummyData(String query) {
        // (保持原樣)
        return new ArrayList<>();
    }
}