package dsfinal.demo.logic;

import java.util.ArrayList;
import java.util.List;

import dsfinal.demo.model.WebPage;

public class SemanticAnalyzer {

    public List<String> deriveRelatedKeywords(List<WebPage> topPages, String userQuery) {
        List<String> suggestions = new ArrayList<>();
        String lowerQuery = userQuery.toLowerCase();
        
        // 1. 鎖定前 3 名高分網頁 (只看這些網頁的內文)
        List<WebPage> targetPages = topPages.size() > 3 ? topPages.subList(0, 3) : topPages;

        for (WebPage page : targetPages) {
            if (page.content == null || page.content.isEmpty()) continue;

            // 清洗內文：把特殊符號轉成空白，保留文字與數字
            String cleanText = page.content.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9 ]", " ");
            
            // 2. 策略：找出包含關鍵字的「短語」
            // 我們把文章用空白切開，然後用滑動視窗(Sliding Window)或簡單的包含檢查
            // 這裡使用更直覺的方法：用標點或空白切分後的片段檢查
            
            // 為了抓到 "戰鬥 技巧" 這種詞，我們先用標點符號切分原本的 content
            String[] segments = page.content.split("[，。！？,.!?：:\\n\\[\\]\\(\\)]");

            for (String segment : segments) {
                String cleanSeg = segment.trim();
                String lowerSeg = cleanSeg.toLowerCase();

                // 過濾邏輯 (這是產生優質建議的核心)：
                // A. 必須包含使用者的關鍵字
                // B. 不能跟關鍵字一模一樣 (我們要延伸詞)
                // C. 長度限制：關鍵字長度 < 建議長度 < 關鍵字長度 + 10
                //    (例如搜 "雪莉"(2字)，我們接受 "雪莉攻略"(4字)，但不接受 "雪莉這個角色真的很強"(10+字))
                
                if (lowerSeg.contains(lowerQuery)) {
                    int qLen = lowerQuery.length();
                    int sLen = lowerSeg.length();

                    // 條件：是延伸詞 (比原詞長)，但不要太長 (太長就變成句子了)
                    if (sLen > qLen && sLen <= qLen + 8) {
                        
                        // 去重檢查 (Case insensitive)
                        boolean exists = false;
                        for (String existing : suggestions) {
                            if (existing.equalsIgnoreCase(cleanSeg)) {
                                exists = true;
                                break;
                            }
                        }
                        
                        if (!exists) {
                            suggestions.add(cleanSeg);
                        }
                    }
                }
                if (suggestions.size() >= 5) break; // 每個網頁抓滿了就換下一個，保持多樣性
            }
            if (suggestions.size() >= 5) break; // 總共抓滿 5 個就停
        }

        // 3. 保底機制 (Fallback)
        // 如果內文挖出來的不夠 5 個 (例如網頁內容太短)，用通用後綴補滿
        if (suggestions.size() < 5) {
            boolean isChinese = isChinese(userQuery);
            String[] suffixes;
            if (isChinese) {
                suffixes = new String[]{" 攻略", " 技巧", " 排名", " 介紹", " 玩法"};
            } else {
                suffixes = new String[]{" Guide", " Tips", " Wiki", " Build", " Stats"};
            }

            for (String suffix : suffixes) {
                if (suggestions.size() >= 5) break;
                
                // 檢查是否已經包含這個後綴
                String candidate = userQuery + suffix;
                boolean exists = false;
                for (String s : suggestions) {
                    if (s.equalsIgnoreCase(candidate)) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    suggestions.add(candidate);
                }
            }
        }
        
        // 最終截斷
        if (suggestions.size() > 5) {
            return suggestions.subList(0, 5);
        }

        return suggestions;
    }

    private boolean isChinese(String text) {
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }
}