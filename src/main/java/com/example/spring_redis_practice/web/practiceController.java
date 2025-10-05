package com.example.spring_redis_practice.web;

import com.example.spring_redis_practice.session.RedisSessionRepository;
import com.example.spring_redis_practice.session.SessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 動作確認用の簡易API。
 * - POST /me/login?userId=xxx : セッションに userId を格納
 * - GET  /me                  : セッションから userId を取得
 * - POST /me/logout           : セッション破棄
 *
 * 実サービスでは、userIdの代わりに認証済みユーザ情報やCSRFトークン等を保持する想定。
 */
@RestController
@RequestMapping("/me")
public class practiceController {

    private final RedisSessionRepository sessions;

    public practiceController(RedisSessionRepository sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public Map<String, Object> login(HttpServletRequest req, @RequestParam String userId) {
        // Filterが付与した SID を取得
        String sid = (String) req.getAttribute(SessionFilter.REQ_ATTR_SESSION_ID);

        // セッションに userId を保存。保存時/アクセス時にTTLが延長される。
        sessions.setAttr(sid, "userId", userId);

        return Map.of(
                "status", "ok",
                "sid", sid,
                "ttlSec", sessions.ttl(sid).toSeconds()  // 現在の残TTLを確認
        );
    }

    @GetMapping
    public Map<String, Object> me(HttpServletRequest req) {
        String sid = (String) req.getAttribute(SessionFilter.REQ_ATTR_SESSION_ID);

        // 取得成功時はスライディングTTLにより expire() で延長される
        Object userId = sessions.getAttr(sid, "userId");

        return Map.of("userId", userId);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        String sid = (String) req.getAttribute(SessionFilter.REQ_ATTR_SESSION_ID);
        sessions.invalidate(sid); // Redis上の "sess:{sid}" を削除
        return Map.of("status", "logged-out");
    }
}
