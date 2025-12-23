package dsfinal.demo.model;

import java.util.ArrayList;
import java.util.List;

public class WebPage {
    public String url;
    public String title;
    public String content;
    public int googleRank;
    public double topicScore;
    
    // 存放算分過程字串
    public String scoreDetails; 
    
    // 存放子網頁的列表
    public List<WebPage> subPages; 

    // 新增無參數建構子
    public WebPage() {
        this.subPages = new ArrayList<>();
        this.scoreDetails = "";
    }

    // 原本的有參數建構子
    public WebPage(String url, String title) {
        this.url = url;
        this.title = title;
        this.topicScore = 0.0;
        this.scoreDetails = "";
        this.subPages = new ArrayList<>(); 
    }

    public void setContent(String content) {
        this.content = content;
    }
}