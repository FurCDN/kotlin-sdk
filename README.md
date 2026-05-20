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
}
```

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
