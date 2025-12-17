package dsfinal.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dsfinal.demo.logic.SemanticAnalyzer;
import dsfinal.demo.model.WebPage;
import dsfinal.demo.service.AiSummaryService;
import dsfinal.demo.service.GoogleSearchService;

@RestController
public class SearchController {

    @Autowired
    private GoogleSearchService searchService;
    
    @Autowired
    private AiSummaryService aiSummaryService;
    
    private SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();

    @GetMapping("/api/search")
    public Map<String, Object> search(@RequestParam String q) {
        // 1. 搜尋
        List<WebPage> results = searchService.searchAndRank(q);
        
        // 2. 語意分析
        List<String> related = semanticAnalyzer.deriveRelatedKeywords(results, q);

        // 3. 生成AI摘要
        String aiSummary = aiSummaryService.generateSummary(q, results);

        // 4. 包裝結果
        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("related_keywords", related);
        response.put("ai_summary", aiSummary);
        
        return response;
    }
}