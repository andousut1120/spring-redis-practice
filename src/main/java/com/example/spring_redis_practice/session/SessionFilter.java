package com.example.spring_redis_practice.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * 役割：リクエストごとに「SIDクッキー」を確認し、なければ新規発行して付与する。
 * - このFilterは「IDの配布のみ」を担当。セッション属性の操作は Repository 側が担当。
 * - Cookieは HttpOnly/Secure/SameSite=Lax を強く推奨（HTTPS前提）。
 *
 * Controller などからは、req.getAttribute(REQ_ATTR_SESSION_ID) でSIDが取れる。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "SID";
    public static final String REQ_ATTR_SESSION_ID = "session.id";

    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // 1) 既存のSIDをCookieから探す。無ければ新規発行してSet-Cookie。
        String sid = readCookie(req, COOKIE_NAME).orElseGet(() -> {
            String s = newSessionId();                // URL-safe ランダム文字列
            addCookie(res, COOKIE_NAME, s, true, true); // HttpOnly/Secure推奨
            return s;
        });

        // 2) 後続処理（Controller等）で使えるよう、リクエスト属性に積む
        req.setAttribute(REQ_ATTR_SESSION_ID, sid);

        // 3) 次のFilter/Controllerへ
        chain.doFilter(req, res);
    }

    private Optional<String> readCookie(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return Optional.empty();
        return Arrays.stream(req.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    /**
     * Cookieをレスポンスに付与。
     * - SameSite を明示したい場合、環境によりヘッダ追記で指定（ServletのCookieAPIで未対応なことがあるため）
     */
    private void addCookie(HttpServletResponse res, String name, String value, boolean httpOnly, boolean secure) {
        Cookie c = new Cookie(name, value);
        c.setPath("/");
        c.setHttpOnly(httpOnly);
        c.setSecure(secure);
        // Max-Ageは設定しない（ブラウザ終了で消える＝サーバ側TTLと分離しておく）
        res.addCookie(c);

        // SameSite=Lax をヘッダで追加（ダブりの「; ;」を避けるため整形）
        res.addHeader("Set-Cookie",
                String.format("%s=%s; Path=/; %s; %s; SameSite=Lax",
                                name, value, httpOnly ? "HttpOnly" : "", secure ? "Secure" : "")
                        .replace("; ;", ";"));
    }

    /**
     * 144bitのランダム値をURL-safe Base64で表現（固定長/パディングなし）
     */
    private String newSessionId() {
        byte[] buf = new byte[18]; // 18 bytes = 144 bits
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
