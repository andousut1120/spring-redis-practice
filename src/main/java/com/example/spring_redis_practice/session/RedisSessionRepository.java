package com.example.spring_redis_practice.session;

import java.time.Duration;
import java.util.Map;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 「セッションID -> Hash属性集」を Redis の Hash として保持する薄いリポジトリ。
 * ・キー： "sess:{sid}"
 * ・Hashの中に userId, roles, csrfToken など好きな属性を格納
 * ・アクセス時に expire() をかけ直し、TTLを延長（スライディングTTL）
 *
 * getExpire() の戻り特性：
 *   -2 : キーが存在しない
 *   -1 : TTL設定なし（永続）
 */
@Component
public class RedisSessionRepository {

    // 15分をデフォルトTTLとする（必要に応じて外出し設定化可）
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "session:";

    private final RedisTemplate<String, Object> redis;

    public RedisSessionRepository(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    private String getKey(String sid) { return KEY_PREFIX + sid; }

    /** セッション全属性を取得。取得に成功したらTTL延長（スライディング）。 */
    public Map<Object, Object> load(String sid) {
        String k = getKey(sid);
        // Hash全体をMapで受け取る。存在しない場合は空Mapを返す。
        Map<Object, Object> m = redis.opsForHash().entries(k);
        if (m == null || m.isEmpty()) return Map.of();
        // アクセスがあったのでTTLを延長
        redis.expire(k, DEFAULT_TTL);
        return m;
    }

    /** 単一属性を取得。値が存在する場合のみTTL延長。 */
    public Object getAttr(String sid, String name) {
        String k = getKey(sid);
        Object v = redis.opsForHash().get(k, name);
        if (v != null) redis.expire(k, DEFAULT_TTL);
        return v;
    }

    /** 単一属性の保存。put後にキー全体のTTLを設定/延長。 */
    public void setAttr(String sid, String name, Object value) {
        String k = getKey(sid);
        redis.opsForHash().put(k, name, value);
        redis.expire(k, DEFAULT_TTL);
    }

    /** 複数属性をまとめて保存。保存後にTTL設定/延長。 */
    public void putAll(String sid, Map<String, Object> values) {
        String k = getKey(sid);
        redis.opsForHash().putAll(k, values);
        redis.expire(k, DEFAULT_TTL);
    }

    /** セッション破棄。ログアウト時などに使用。 */
    public void invalidate(String sid) {
        redis.delete(getKey(sid));
    }

    /** 残りTTLの確認（負値やnullなら Duration.ZERO を返す）。 */
    public Duration ttl(String sid) {
        Long sec = redis.getExpire(getKey(sid));
        if (sec == null || sec < 0) return Duration.ZERO; // -2=キーなし, -1=TTLなし（=永続）
        return Duration.ofSeconds(sec);
    }
}
