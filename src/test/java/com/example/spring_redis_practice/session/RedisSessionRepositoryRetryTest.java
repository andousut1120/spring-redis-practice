package com.example.spring_redis_practice.session;

import com.example.spring_redis_practice.config.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Redis を実際に立てず、Retry の挙動だけを検証。
 * - 1,2回目は put() 時に例外を投げる
 * - 3回目で成功させる
 * - expire() は成功試行のときだけ呼ばれること
 */
class RedisSessionRepositoryRetryTest {

    RedisTemplate<String, Object> redis;
    HashOperations<String, Object, Object> hashOps;
    RetryTemplate retryTemplate;
    RedisSessionRepository repo;

    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);

        // 本番と同設定でもOKだが、ここでは簡易に RetryConfig を使う
        retryTemplate = new RetryConfig().redisRetryTemplate();

        repo = new RedisSessionRepository(redis, retryTemplate);
    }

    @Test
    void setAttr_retries_then_succeeds() {
        String sid = "S1";
        String key = "session:" + sid;

        // 1回目: 接続失敗、2回目: タイムアウト、3回目: 成功
        doThrow(new RedisConnectionFailureException("down"))
                .doThrow(new QueryTimeoutException("slow"))
                .doNothing()
                .when(hashOps).put(key, "userId", "alice");

        // expire は成功試行でのみ呼ばれる想定。ここでは成功させる。
        when(redis.expire(eq(key), any(Duration.class))).thenReturn(true);

        // 実行
        repo.setAttr(sid, "userId", "alice");

        // 呼び出し検証
        InOrder inOrder = inOrder(hashOps, redis);
        // put は3回（2回失敗＋1回成功）
        inOrder.verify(hashOps, times(3)).put(key, "userId", "alice");
        // expire は成功時のみ1回
        inOrder.verify(redis, times(1)).expire(eq(key), any(Duration.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void load_returns_empty_map_without_retry_on_empty() {
        String sid = "none";
        String key = "session:" + sid;

        // entries が空（null/empty）なら TTL延長やリトライは不要
        when(hashOps.entries(key)).thenReturn(Map.of());

        Map<Object, Object> result = repo.load(sid);

        assertThat(result).isEmpty();
        verify(hashOps, times(1)).entries(key);
        verify(redis, never()).expire(anyString(), any());
    }
}
