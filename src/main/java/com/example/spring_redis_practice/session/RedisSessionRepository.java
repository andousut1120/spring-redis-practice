package com.example.spring_redis_practice.session;

import java.time.Duration;
import java.util.Map;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * 「セッションID -> Hash属性集」を Redis の Hash として保持する薄いリポジトリ。
 * ・キー： "session:{sid}"
 * ・Hashの中に userId, roles, csrfToken など好きな属性を格納
 * ・アクセス時に expire() をかけ直し、TTLを延長（スライディングTTL）
 * <p>
 * リトライ方針：
 * - Redis/Lettuce の一時的な接続断やタイムアウト時は、短時間の指数バックオフで再試行
 * - 入力エラーなど恒久的失敗は即時例外
 * - 各メソッドの「1回の論理操作」を 1 回の RetryTemplate 実行に収め、冪等性を担保
 * 注意：
 *  - setAttr/putAll は「put → expire」の2操作。どちらかが失敗した場合の再試行で二重実行されても
 *    結果は同一（冪等）になるため安全。
 * <p>
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
    private final RetryTemplate retry; // ★ 追加：リトライ器

    public RedisSessionRepository(RedisTemplate<String, Object> redis, RetryTemplate redisRetryTemplate) {
        this.redis = redis;
        this.retry = redisRetryTemplate;
    }

    private String getKey(String sid) { return KEY_PREFIX + sid; }

    /** セッション全属性を取得。取得に成功したらTTL延長（スライディング）。 */
    public Map<Object, Object> load(String sid) {
        final String k = getKey(sid);
        return retry.execute(ctx -> {
            // Hash全体をMapで受け取る。存在しない場合は空Mapを返す。
            Map<Object, Object> m = redis.opsForHash().entries(k);
            if (m == null || m.isEmpty()) return Map.of();
            // アクセスがあったので延長（expire失敗もまとめてリトライ対象）
            redis.expire(k, DEFAULT_TTL);
            return m;
        });
    }

    /** 単一属性を取得。値が存在する場合のみTTL延長。 */
    public Object getAttr(String sid, String name) {
        final String k = getKey(sid);
        return retry.execute(ctx -> {
            Object v = redis.opsForHash().get(k, name);
            if (v != null) redis.expire(k, DEFAULT_TTL);
            return v;
        });
    }

    /** 単一属性の保存。put後にキー全体のTTLを設定/延長。 */
    public void setAttr(String sid, String name, Object value) {
        final String k = getKey(sid);
        retry.execute((RetryCallback<Void, RuntimeException>) ctx -> {
            redis.opsForHash().put(k, name, value);
            redis.expire(k, DEFAULT_TTL);
            return null;
        });
    }

    /** 複数属性をまとめて保存。保存後にTTL設定/延長。 */
    public void putAll(String sid, Map<String, Object> values) {
        final String k = getKey(sid);
        retry.execute((RetryCallback<Void, RuntimeException>) ctx -> {
            redis.opsForHash().putAll(k, values);
            redis.expire(k, DEFAULT_TTL);
            return null;
        });
    }

    /** セッション破棄。ログアウト時などに使用。 */
    public void invalidate(String sid) {
        final String k = getKey(sid);
        retry.execute((RetryCallback<Void, RuntimeException>) ctx -> {
            redis.delete(k);
            return null;
        });
    }

    /** 残りTTLの確認（負値やnullなら Duration.ZERO を返す）。 */
    public Duration ttl(String sid) {
        final String k = getKey(sid);
        Long sec = retry.execute(ctx -> redis.getExpire(k));
        if (sec == null || sec < 0) return Duration.ZERO; // -2=キーなし, -1=TTLなし（=永続）
        return Duration.ofSeconds(sec);
    }
}
