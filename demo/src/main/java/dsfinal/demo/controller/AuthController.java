package dsfinal.demo.controller;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dsfinal.demo.model.User;
import dsfinal.demo.model.WebPage;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // 使用 ConcurrentHashMap 確保多執行緒安全
    private Map<String, User> userDb = new ConcurrentHashMap<>();
    
    // 設定存檔的檔名
    private final String DATA_FILE = "users_data.json";
    private final ObjectMapper mapper = new ObjectMapper();

    // [核心] 程式啟動時，自動執行此方法載入資料
    @PostConstruct
    public void init() {
        loadData();
    }

    // 1. 註冊
    @PostMapping("/register")
    public Map<String, Object> register(@RequestParam String username, @RequestParam String password) {
        Map<String, Object> response = new HashMap<>();
        if (userDb.containsKey(username)) {
            response.put("success", false);
            response.put("message", "帳號已存在");
        } else {
            userDb.put(username, new User(username, password));
            saveData(); // [存檔]
            response.put("success", true);
            response.put("message", "註冊成功，請登入");
        }
        return response;
    }

    // 2. 登入
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username, @RequestParam String password, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = userDb.get(username);
        
        if (user != null && user.getPassword().equals(password)) {
            session.setAttribute("currentUser", user); 
            response.put("success", true);
            response.put("username", username);
        } else {
            response.put("success", false);
            response.put("message", "帳號或密碼錯誤");
        }
        return response;
    }

    // 3. 登出
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate(); 
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return response;
    }

    // 4. 檢查狀態
    @GetMapping("/check")
    public Map<String, Object> checkSession(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User sessionUser = (User) session.getAttribute("currentUser");
        
        if (sessionUser != null) {
            // 從記憶體 DB 拿最新的資料 (避免 Session 裡的資料是舊的)
            User latestUser = userDb.get(sessionUser.getUsername());
            if (latestUser != null) {
                response.put("isLoggedIn", true);
                response.put("username", latestUser.getUsername());
                response.put("favorites", latestUser.getFavorites());
            } else {
                response.put("isLoggedIn", false);
            }
        } else {
            response.put("isLoggedIn", false);
        }
        return response;
    }

    // 5. 加入最愛
    @PostMapping("/favorite/add")
    public Map<String, Object> addFavorite(@RequestBody WebPage page, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User sessionUser = (User) session.getAttribute("currentUser");
        
        if (sessionUser != null) {
            // 更新 DB 中的使用者資料
            User dbUser = userDb.get(sessionUser.getUsername());
            if (dbUser != null) {
                dbUser.addFavorite(page);
                saveData(); // [存檔]
                
                // 同步更新 Session 裡的資料，以免下次 check 拿到舊的
                session.setAttribute("currentUser", dbUser);
                response.put("success", true);
            }
        } else {
            response.put("success", false);
            response.put("message", "請先登入");
        }
        return response;
    }

    // 6. [新增] 移除最愛
    @PostMapping("/favorite/remove")
    public Map<String, Object> removeFavorite(@RequestBody WebPage page, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User sessionUser = (User) session.getAttribute("currentUser");
        
        if (sessionUser != null) {
            // 更新 DB 中的使用者資料
            User dbUser = userDb.get(sessionUser.getUsername());
            if (dbUser != null) {
                dbUser.removeFavorite(page.url); // 使用 User.java 裡原本就有的 remove 方法
                saveData(); // 存檔
                
                // 同步更新 Session
                session.setAttribute("currentUser", dbUser);
                response.put("success", true);
            }
        } else {
            response.put("success", false);
            response.put("message", "請先登入");
        }
        return response;
    }

    // --- 檔案存取 Helper Methods ---

    // 將記憶體資料寫入 JSON 檔案
    private void saveData() {
        try {
            mapper.writeValue(new File(DATA_FILE), userDb);
            System.out.println(">>> 使用者資料已儲存至: " + DATA_FILE);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(">>> 儲存資料失敗: " + e.getMessage());
        }
    }

    // 從 JSON 檔案讀取資料到記憶體
    private void loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try {
                // TypeReference 用來告訴 Jackson 我們要讀的是 Map<String, User> 這種複雜型態
                userDb = mapper.readValue(file, new TypeReference<Map<String, User>>(){});
                System.out.println(">>> 成功載入使用者資料，共 " + userDb.size() + " 筆");
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println(">>> 讀取資料失敗，將使用空資料庫");
                userDb = new ConcurrentHashMap<>();
            }
        } else {
            System.out.println(">>> 找不到資料檔，將建立新的: " + DATA_FILE);
        }
    }
}