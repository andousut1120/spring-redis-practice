package com.example.spring_redis_practice.config;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import org.springframework.classify.SubclassClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

/**
 * Redis向けのRetryTemplate。
 *
 * リトライ対象（代表例）：
 *  - RedisConnectionFailureException（接続断）
 *  - RedisSystemException（内部の一時的エラー）
 *  - QueryTimeoutException（Spring側タイムアウト）
 *  - RedisCommandTimeoutException / RedisConnectionException（Lettuce）
 *
 * 非リトライ：
 *  - IllegalArgumentException など「呼び出し側のバグ/不正入力」
 *  - 既に存在しないキーの参照など、ビジネス的に許容すべき状態
 */
@Configuration
public class RetryConfig {

    @Bean
    public RetryTemplate redisRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        // 1) バックオフ（指数）
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setInitialInterval(100);   // 1回目 100ms
        backoff.setMultiplier(2.0);        // 倍々
        backoff.setMaxInterval(1000);      // 上限 1s
        template.setBackOffPolicy(backoff);

        // 2) ポリシー：例外でルーティング
        ExceptionClassifierRetryPolicy classifier = new ExceptionClassifierRetryPolicy();
        classifier.setExceptionClassifier(new SubclassClassifier<>(Map.of(
                // リトライするポリシー（最大3回）
                RedisConnectionFailureException.class, simple(3),
                RedisSystemException.class,            simple(3),
                QueryTimeoutException.class,           simple(3),
                RedisCommandTimeoutException.class,    simple(3),
                RedisConnectionException.class,        simple(3)
        ), new NeverRetryPolicy())); // デフォルトはリトライしない

        template.setRetryPolicy(classifier);

        return template;
    }

    private RetryPolicy simple(int maxAttempts) {
        // 指定回数（試行回数＝例外発生後の再試行含めて maxAttempts）
        return new SimpleRetryPolicy(maxAttempts);
    }
}