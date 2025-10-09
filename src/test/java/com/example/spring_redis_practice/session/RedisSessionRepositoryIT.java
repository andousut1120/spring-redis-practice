package com.example.spring_redis_practice.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers で Redis を起動し、実際の格納/取得/TTL を確認。
 * SpringBootTest でアプリの Bean 構成（RedisTemplate, RetryTemplate等）をそのまま使う。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Testcontainers
public class RedisSessionRepositoryIT {

    @Autowired
    RedisSessionRepository repo;

    @Test
    void set_get_invalidate_and_ttl() {
        String sid = "IT1";

        // 保存
        repo.setAttr(sid, "userId", "bob");

        // 取得（存在）
        Object userId = repo.getAttr(sid, "userId");
        assertThat(userId).isEqualTo("bob");

        // TTLは0より大きい（-1/-2ではない）
        Duration ttl = repo.ttl(sid);
        assertThat(ttl).isGreaterThan(Duration.ZERO);

        // 全属性取得でスライディングTTLも動く（ここでは値が取れることだけ確認）
        Map<Object, Object> all = repo.load(sid);
        assertThat(all).isNotEmpty();
        assertThat(all.get("userId")).isEqualTo("bob");

        // 破棄
        repo.invalidate(sid);
        assertThat(repo.load(sid)).isEmpty();
    }
}
