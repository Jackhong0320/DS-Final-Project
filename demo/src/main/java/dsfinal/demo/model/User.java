package dsfinal.demo.model;

import java.util.ArrayList;
import java.util.List;

public class User {
    private String username;
    private String password;
    private List<WebPage> favorites;

    public User() {
        this.favorites = new ArrayList<>();
    }

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.favorites = new ArrayList<>();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public List<WebPage> getFavorites() { return favorites; }
    public void setFavorites(List<WebPage> favorites) { this.favorites = favorites; }
    
    public void addFavorite(WebPage page) {
        if (favorites == null) favorites = new ArrayList<>();
        boolean exists = favorites.stream().anyMatch(f -> f.url.equals(page.url));
        if (!exists) {
            favorites.add(page);
        }
    }
    
    public void removeFavorite(String url) {
        if (favorites != null) {
            favorites.removeIf(page -> page.url.equals(url));
        }
    }
}