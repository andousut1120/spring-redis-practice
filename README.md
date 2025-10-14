・Spring Session は RedisIndexedSessionRepository が内部で RedisOperations（= ほぼ StringRedisTemplate） を使って Redis に保存/読取。<br>
・LettuceConnectionFactory が接続の起点。Standalone/Sentinel/Cluster は *Configuration の渡し方で決まる。<br>
・Retry は AOP かラップで RedisIndexedSessionRepository や独自 Repository の Redis I/O を再試行可能。<br>

```mermaid
flowchart LR
    subgraph Web["Web層（Spring MVC / Spring Security）"]
        SSF[Spring Session<br/>HttpSessionRepositoryFilter] --> C[Controller]
    end

subgraph App["アプリ層（あなたのコード）"]
C --> SVC[Service / UseCase]
SVC -->|セッション操作| HSES[HttpSession / Session]
SVC -->|任意に| REPO[独自Repository 任意]
end

subgraph SpringSession["Spring Session"]
HSES --> RSR[RedisIndexedSessionRepository<br/> Spring Session]
RSR --> ROS[RedisOperations StringRedisTemplate]
end

subgraph RetryConfig["RetryConfig（あなたの設定）"]
RTMP[RetryTemplate / @Retryable]:::retry
RTMP -.wrap/AOP.-> RSR
RTMP -.wrap/AOP.-> REPO
end

subgraph RedisConfig["RedisConfig（あなたの設定）"]
LCF[LettuceConnectionFactory]
TPL[RedisTemplate / StringRedisTemplate]
LCF --> |コネクションを生成して渡す。| TPL
TPL --> ROS
REPO --> TPL
end

subgraph Lettuce["Lettuce クライアント"]
LCF --> LCLI[io.lettuce.core<br/>StatefulConnection]
end

subgraph RedisSide["Redis サーバ"]
ST[(Standalone)]
SEN[(Sentinel)]
CLU[(Cluster)]
end

LCLI --> ST
LCLI --> SEN
LCLI --> CLU

classDef retry fill:#fff3,stroke-dasharray: 3 3;
```

* Spring Session は Redis に以下のようなキーで保存するのが一般的：<br>
     * spring:session:sessions:<sessionId>（Hash：attrs、creationTime、lastAccessedTime…）<br>
     * spring:session:expirations:<epochSec>（Set：期限管理）<br>
* TTL/有効期限は server.servlet.session.timeout や Spring Session の設定で制御。<br>
* Retry を入れるなら、save/createSession/findById を横断的に AOP で再試行対象にすると現実的。<br>
```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant F as Spring Session Filter<br/>(HttpSessionRepositoryFilter)
    participant C as Controller
    participant S as Service/UseCase
    participant H as HttpSession
    participant R as RedisIndexedSessionRepository<br/>(Spring Session)
    participant O as RedisOperations<br/>(StringRedisTemplate)
    participant L as LettuceConnectionFactory
    participant X as Lettuce Client
    participant D as Redis

    B->>F: HTTP Request (Cookie:JSESSIONID?)
    F->>R: セッション読取(load by id) ※初回は未検出で新規作成
    R->>O: HGET/HMGET など
    O->>L: getConnection()
    L-->>O: RedisConnection (Lettuce)
    O->>X: READ commands
    X-->>O: OK / (初回なら空)
    O-->>R: セッションデータ
    R-->>F: HttpSession 準備
    F->>C: フィルタ通過して Controller 呼出

    C->>S: ビジネス処理
    S->>H: setAttribute("userName","Taro")

    alt レスポンス返却時のフラッシュ(保存)
        F->>R: save(session)
        note right of R: ここに Retry を適用可能<br/>(AOPでR.saveを再試行)
        R->>O: HMSET/HSET (spring:session:sessions:<id>)<br/>SADD, EXPIRE など
        O->>L: getConnection()
        L-->>O: RedisConnection
        O->>X: WRITE commands
        alt 一時的失敗（例：接続失敗）
            X-->>O: 例外（RedisConnectionFailureException等）
            O-->>R: 例外
            R-->>R: Retryポリシー(Backoff/Jitter)
            R->>O: 再試行(WRITE)
            O->>X: WRITE commands (retry)
            X-->>O: OK
            O-->>R: OK
        else 成功
            X-->>O: OK
            O-->>R: OK
        end
    end

    R-->>F: 保存完了
    F-->>B: HTTP Response(Set-Cookie/TTL更新の可能性)
```

```mermaid
flowchart TB
    %% --- 上層：Web ---
    subgraph WEB["🌐 Web層（Spring MVC / Filter）"]
        F["HttpSessionRepositoryFilter<br/>(Spring Session Filter)"]
        C["Controller / Service"]
    end

    %% --- 中層：Spring Session + Retry + RedisTemplate ---
    subgraph APP["🧠 アプリ層（Spring Session + Retry統合）"]
        RSR["RedisIndexedSessionRepository<br/>（Spring Session）"]
        SRO["sessionRedisOperations<br/>(= RetryingRedisTemplate)"]
        RT["RetryTemplate"]
        LCF["LettuceConnectionFactory"]
        LC["Lettuce Client（io.lettuce.core）"]
    end

    %% --- 下層：Redis ---
    subgraph REDIS["🗄️ Redis サーバ層"]
        ST[(Standalone)]
        SEN[(Sentinel)]
        CLU[(Cluster)]
    end

    %% Connections
    F -->|セッション読取/保存| RSR
    C --> F
    RSR --> SRO
    SRO -->|内部で retryTemplate.execute..| RT
    RT -->|3回など再試行| SRO
    SRO -->|execute 呼出| LCF
    LCF --> LC
    LC -->|通信| ST
    LC -->|構成により| SEN
    LC -->|構成により| CLU

    classDef layer fill:#fff3,stroke-dasharray:3 3;
    class WEB,APP,REDIS layer;
```

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant F as HttpSessionRepositoryFilter
    participant R as RedisIndexedSessionRepository
    participant O as RetryingRedisTemplate(sessionRedisOperations)
    participant T as RetryTemplate
    participant L as LettuceConnectionFactory
    participant X as Lettuce Client
    participant D as Redis

    B->>F: HTTP Request (Cookie:JSESSIONID)
    F->>R: findById()（Redis読取）
    R->>O: opsForHash().entries(...) → execute(...)
    O->>T: retryTemplate.execute(action)
    T->>O: super.execute(action)
    O->>L: getConnection()
    L->>X: HGETALL spring:session:sessions:<id>
    X-->>O: OK or Exception
    alt 一時的失敗（例: 接続タイムアウト）
        X-->>O: RedisConnectionFailureException
        O-->>T: Exception
        T-->>T: Backoff(100ms→200ms→400ms...)
        T->>O: 再試行
        O->>L: getConnection()
        L->>X: 再送
        X-->>O: OK
    else 成功
        X-->>O: OK
    end
    O-->>R: セッションデータ取得成功
    R-->>F: HttpSession オブジェクト返却
    F->>B: HTTP Response（書込時も同様の流れで retryTemplate 経由）
```