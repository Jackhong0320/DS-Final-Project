package dsfinal.demo.model;

public class WebPage {
    public String url;
    public String title;
    public String content;
    public int googleRank;
    public double topicScore;

    public WebPage(String url, String title) {
        this.url = url;
        this.title = title;
        this.topicScore = 0.0;
    }

    public void setContent(String content) {
        this.content = content;
    }
}