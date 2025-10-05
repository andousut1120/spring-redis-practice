package com.example.spring_redis_practice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate の最小設定。
 * - Key/HashKey は文字列
 * - Value/HashValue は JSON（GenericJackson2JsonRedisSerializer）
 *
 * こうしておくと Map や DTO をそのまま put/get できて扱いやすい。
 * なお、RedisConnectionFactory は Spring Boot のオートコンフィグに任せる。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Object> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(cf);

        var json = new GenericJackson2JsonRedisSerializer();

        tpl.setKeySerializer(new StringRedisSerializer());
        tpl.setValueSerializer(json);
        tpl.setHashKeySerializer(new StringRedisSerializer());
        tpl.setHashValueSerializer(json);

        tpl.afterPropertiesSet();
        return tpl;
    }
}


