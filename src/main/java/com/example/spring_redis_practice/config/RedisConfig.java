package com.example.spring_redis_practice.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * RedisTemplate の最小設定。
 * - Key/HashKey は文字列
 * - Value/HashValue は JSON（GenericJackson2JsonRedisSerializer）
 *
 * こうしておくと Map や DTO をそのまま put/get できて扱いやすい。
 * なお、RedisConnectionFactory は Spring Boot のオートコンフィグに任せる。
 */
/**
 * Lettuce の運用向け設定を明示化：
 * - 自動再接続（autoReconnect）
 * - コマンドタイムアウト（デフォ3秒→必要に応じて変更）
 * - 接続タイムアウト（SocketOptions）
 * - リクエストキュー上限（詰め込みすぎ防止）
 * - ClientResources（I/Oスレッドなど）
 *
 * Standalone / Sentinel / Cluster のいずれも spring.data.redis.* の設定で切替。
 */
@Configuration
public class RedisConfig {

//todo 下記のlettuceのBeanたちはオプション設定の立ち位置なので、一旦無視。

//    /** Netty用スレッドプール・タイマの共有資源（アプリ全体で１つ）*/
//    @Bean(destroyMethod = "shutdown")
//    public ClientResources lettuceClientResources() {
//        return DefaultClientResources.create();
//    }
//
//
//    /** Lettuceのクライアントオプション（自動再接続・タイムアウト等） */
//    @Bean
//    public ClientOptions lettuceClientOptions() {
//        return ClientOptions.builder()
//                .autoReconnect(true) // ネットワーク断からの復帰
//                .requestQueueSize(10_000) // コマンドの待ち行列上限（過剰蓄積の抑止）
//                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS) // 断時は新規を拒否
//                .socketOptions(SocketOptions.builder()
//                        .connectTimeout(Duration.ofSeconds(3)) // 接続確立の上限
//                        .build())
//                .timeoutOptions(TimeoutOptions.enabled()) // コマンドタイムアウトを有効化
//                .build();
//    }
//
//    /** Lettuceクライアント設定（コマンドタイムアウト・SSL・クライアント名など） */
//    @Bean
//    public LettuceClientConfiguration lettuceClientConfiguration(
//            ClientResources clientResources,
//            ClientOptions clientOptions
//    ) {
//        return LettuceClientConfiguration.builder()
//                .clientResources(clientResources)
//                .clientOptions(clientOptions)
//                .commandTimeout(Duration.ofSeconds(3)) // 実行タイムアウト（Retryのトリガになりやすい）
//                // .useSsl() // 必要ならSSL
//                .clientName("spring-redis-session") // 監視/可観測性で識別しやすく
//                .shutdownTimeout(Duration.ofMillis(200))
//                .build();
//    }
//
//    /**
//     * 接続ファクトリ。
//     * spring.data.redis.* の内容を見て、Standalone/Sentinel/Cluster を自動選択。
//     * Sentinel/Clusterを使う場合は application.yml 側の該当ブロックを有効にする。
//     */
//    @Bean
//    public RedisConnectionFactory redisConnectionFactory(
//            RedisProperties props,
//            LettuceClientConfiguration lettuceClientConfiguration
//    ) {
//        // Sentinel 優先 → Cluster → Standalone の順で判定
//        if (props.getSentinel() != null && props.getSentinel().getMaster() != null) {
//            RedisSentinelConfiguration sentinel = new RedisSentinelConfiguration()
//                    .master(props.getSentinel().getMaster());
//            // "host:port" 形式を分解してノードに追加
//            if (props.getSentinel().getNodes() != null) {
//                for (String node : props.getSentinel().getNodes()) {
//                    String[] hp = node.split(":");
//                    sentinel.sentinel(hp[0], Integer.parseInt(hp[1]));
//                }
//            }
//            if (props.getPassword() != null && !props.getPassword().isEmpty()) {
//                sentinel.setPassword(RedisPassword.of(props.getPassword()));
//            }
//            return new LettuceConnectionFactory(sentinel, lettuceClientConfiguration);
//        }
//
//        if (props.getCluster() != null && props.getCluster().getNodes() != null && !props.getCluster().getNodes().isEmpty()) {
//            RedisClusterConfiguration cluster = new RedisClusterConfiguration(
//                    props.getCluster().getNodes().stream().collect(Collectors.toList())
//            );
//            if (props.getPassword() != null && !props.getPassword().isEmpty()) {
//                cluster.setPassword(RedisPassword.of(props.getPassword()));
//            }
//            return new LettuceConnectionFactory(cluster, lettuceClientConfiguration);
//        }
//
//        // 既定：Standalone
//        RedisStandaloneConfiguration standalone =
//                new RedisStandaloneConfiguration(props.getHost(), props.getPort());
//        if (props.getPassword() != null && !props.getPassword().isEmpty()) {
//            standalone.setPassword(RedisPassword.of(props.getPassword()));
//        }
//        if (props.getDatabase() != null) {
//            standalone.setDatabase(props.getDatabase());
//        }
//        return new LettuceConnectionFactory(standalone, lettuceClientConfiguration);
//    }


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


