# kotlin-sdk

[FurCDN](https://www.furcdn.us) 開放 API 的 Kotlin SDK，使用 kotlinx-serialization。

完整 API 文檔：<https://docs.furcdn.us/api>

## 安裝（JitPack）

`build.gradle.kts`：

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.FurCDN:kotlin-sdk:0.1.0")
}
```

Groovy DSL：

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.FurCDN:kotlin-sdk:0.1.0'
}
```

Maven：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.FurCDN</groupId>
    <artifactId>kotlin-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 使用

```kotlin
import us.furcdn.api.FurCdnClient

fun main() {
    val client = FurCdnClient("fck_xxxxxxxx_xxxxxxxxxxxxxxxxxxxxxxxx")

    client.listDomains().forEach {
        println("${it.id}  ${it.name}  enabled=${it.enabled}")
    }

    val r = client.purgeCache(123)
    println("purged ${r.success}/${r.total} nodes")

    client.uploadSsl(
        domainId = 123,
        cert = "-----BEGIN CERTIFICATE-----\n...",
        key  = "-----BEGIN PRIVATE KEY-----\n...",
    )

    // 取得全部節點回源 IP（公開端點，不需 API key）。清單非固定，建議定期重取後同步源站防火牆白名單。
    println(client.originIps())
}
```

## 清除快取

### 整站清除（舊行為，維持不變）

```kotlin
val r = client.purgeCache(123)   // 不帶 body，清除整個域名快取
println("${r.success}/${r.total} 個節點已回應，mode=${r.mode}")
```

`PurgeResult` 欄位：

| 欄位 | 說明 |
| --- | --- |
| `ok` | 伺服器是否接受此次清除 |
| `total` | 正在服務此域名的節點數 |
| `success` | 已回應的節點數 |
| `mode` | `all`（整站）或 `selective`（選擇性） |
| `removed` | 選擇性清除時，各節點合計刪除的快取條目數（同一 URL 快取在 3 個節點就算 3） |

### 選擇性清除

只清命中條件的內容，其餘快取維持不變：

```kotlin
import us.furcdn.api.PurgeOptions

// 精確 URL：查詢字串算在比對範圍內（除非域名開啟「忽略查詢字串」）
client.purgeCache(123, PurgeOptions(
    urls = listOf("https://example.com/a.css?v=1", "/b.js"),
))

// 前綴：/static/ 會命中 /static/a.js、/static/x/y.css
client.purgeCache(123, PurgeOptions(prefixes = listOf("/static/")))

// glob：* 匹配任意字元（含 /），必須以 / 開頭
client.purgeCache(123, PurgeOptions(patterns = listOf("/assets/*.js")))

// 三種可併用（聯集）
client.purgeCache(123, PurgeOptions(
    urls = listOf("/index.html"),
    prefixes = listOf("/static/"),
    patterns = listOf("/img/*"),
))

// 等於整站清除
client.purgeCache(123, PurgeOptions(all = true))
```

規則：

- 不帶 body（`purgeCache(123)`）或 `all = true` 都代表清除整個域名快取，舊版呼叫方式不受影響。
- `all = true` 不可與 `urls` / `prefixes` / `patterns` 併用（伺服器回 400）。
- `urls` 每筆是絕對 URL（host 必須等於該域名）或以 `/` 開頭的路徑；會清掉該 URL 的所有快取變體
  （含 Vary 副本、GET 與 HEAD）。查詢字串屬於比對的一部分：清 `/a.css` 不會清掉 `/a.css?v=1`。
- `prefixes` 是 URL 路徑的字串前綴比對；`patterns` 是路徑 glob（`*` 匹配任意字元，含 `/`）。
  兩者都忽略查詢字串，也可傳絕對 URL（只取其中的路徑）。
- 限制：`urls + prefixes + patterns` 合計最多 100 筆，單筆最長 2048 字元且不可為空字串。
  SDK 會在發出請求前以 `IllegalArgumentException` 擋下不合法的條件。
- 速率限制：整站清除每使用者 5 分鐘 10 次、選擇性清除 5 分鐘 60 次，超過回 429
  （丟出 `FurCdnException`，`statusCode == 429`）。
- 目前不支援 tag / surrogate-key 清除。

## 快取規則與清除

- 快取規則（TTL、不快取等）只在節點回源抓取時套用；修改規則不會動到已快取的內容。
  要讓規則變更立刻生效，請清除對應快取（整站或選擇性）。
- 規則 `priority` 數字越小越優先，第一條命中的規則生效；同 priority 時較早建立的規則優先。
  `priority = 0` 是合法值。
- `matchType = path`：值含 `*` 時以 glob 比對路徑，否則為前綴比對（`/api/` 命中 `/api/x/y`）；
  單一值 `/` 只命中根路徑 `/`。`matchType = extension` 的值有無前導點皆可（`js` 等於 `.js`），
  只比對最後一個副檔名；`contentType` 忽略 `; charset=...` 參數。
- 回應只會套用命中規則的 `cacheControl`。

## 錯誤處理

非 2xx 回應會丟出 `FurCdnException`：

```kotlin
try {
    client.listDomains()
} catch (e: FurCdnException) {
    System.err.println("HTTP ${e.statusCode}: ${e.message}")
}
```

## 開發

需要 JDK 17+ 與 Gradle：

```bash
gradle build
gradle test
```

## License

MIT
