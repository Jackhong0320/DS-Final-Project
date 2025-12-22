package dsfinal.demo.model;

import java.util.ArrayList;
import java.util.List;

public class WebPage {
    public String url;
    public String title;
    public String content;
    public int googleRank;
    public double topicScore;
    
    // 存放算分過程字串 (Ex: "主題30+關鍵字40=70")
    public String scoreDetails; 
    
    // 存放子網頁的列表
    public List<WebPage> subPages; 

    // =======================================================
    // [關鍵修改] 必須加入這個「無參數建構子」
    // 這是為了讓程式重啟讀取 users_data.json 時不會報錯
    // =======================================================
    public WebPage() {
        this.subPages = new ArrayList<>();
        this.scoreDetails = "";
    }

    // 原本的有參數建構子 (程式爬蟲運作時用的)
    public WebPage(String url, String title) {
        this.url = url;
        this.title = title;
        this.topicScore = 0.0;
        this.scoreDetails = ""; // 初始化
        this.subPages = new ArrayList<>(); 
    }

    public void setContent(String content) {
        this.content = content;
    }
}