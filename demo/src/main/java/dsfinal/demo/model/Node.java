package dsfinal.demo.model;

import java.util.ArrayList;
import java.util.List;

public class Node {
    public WebPage page;
    public List<Node> children;
    public Node parent;
    public double nodeScore;

    public Node(WebPage page) {
        this.page = page;
        this.children = new ArrayList<>();
        this.nodeScore = 0;
    }

    public void addChild(Node child) {
        child.parent = this;
        this.children.add(child);
    }
    
    public double aggregateScore(double decay) {
        double childrenSum = 0;
        for (Node child : children) {
            childrenSum += child.aggregateScore(decay);
        }
        // 本節點最終分數 = 自己的分數 + (子節點總分 * 衰減係數)
        this.nodeScore = this.page.topicScore + (childrenSum * decay);
        return this.nodeScore;
    }
}