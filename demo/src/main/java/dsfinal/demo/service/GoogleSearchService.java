package dsfinal.demo.service;

import dsfinal.demo.logic.Ranker;
import dsfinal.demo.model.WebPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class GoogleSearchService {

    // ==========================================
    // [重要] 請填入你的 Google API 資訊
    // ==========================================
    private final String GOOGLE_API_KEY = "AIzaSyA-NrUymsiuPx8zUnQ6PaZl5Gpo3I36eT4"; 
    private final String SEARCH_ENGINE_ID = "b25a4f1b2c45547ca"; 
    // ==========================================

    // 修改：不要把 q= 寫死在這邊，我們在後面動態組裝
    private final String BASE_URL = "https://www.googleapis.com/customsearch/v1?key=" + GOOGLE_API_KEY + "&cx=" + SEARCH_ENGINE_ID;
    
    private Ranker ranker = new Ranker();

    public List<WebPage> searchAndRank(String query) {
        List<WebPage> pages = new ArrayList<>();
        
        try {
            // --- [關鍵修改 1] Query Expansion (查詢擴展) ---
            // 如果使用者的搜尋詞裡面沒有「荒野亂鬥」，我們就幫他加上去！
            // 這樣搜 "戰鬥" 會變成搜 "戰鬥 荒野亂鬥"，結果才會是遊戲相關的
            String searchTerm = query;
            if (!query.contains("荒野") && !query.contains("Brawl")) {
                searchTerm = query + " 荒野亂鬥"; 
            }

            // 組裝最終 URL (用擴展後的關鍵字去搜 Google)
            String url = BASE_URL + "&q=" + searchTerm + "&gl=tw&cr=countryTW";

            // 1. 呼叫 Google API
            RestTemplate restTemplate = new RestTemplate();
            String resultJson = restTemplate.getForObject(url, String.class);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(resultJson);
            JsonNode items = root.path("items");

            int rank = 1;
            for (JsonNode item : items) {
                String title = item.path("title").asText();
                String link = item.path("link").asText();
                String snippet = item.path("snippet").asText();

                WebPage page = new WebPage(link, title);
                page.googleRank = rank++;
                
                // 2. 爬取內文
                try {
                    Document doc = Jsoup.connect(link)
                            .userAgent("Mozilla/5.0")
                            .timeout(3000)
                            .get();
                    page.setContent(doc.body().text());
                } catch (Exception e) {
                    page.setContent(snippet);
                }

                // 3. 計算分數
                // --- [關鍵修改 2] ---
                // 注意：算分時要用「使用者原本輸入的 query」(例如 "戰鬥")
                // 這樣才能算出這個網頁跟使用者想找的東西有多相關
                ranker.calculatePageScore(page, query); 
                
                pages.add(page);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4. 重新排序 (分數高的排前面)
        Collections.sort(pages, (o1, o2) -> Double.compare(o2.topicScore, o1.topicScore));

        return pages;
    }
}