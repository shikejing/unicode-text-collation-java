# Unicode Text Collation & Normalization Service

面向多语言文本的规范化、折叠与排序键生成服务，供检索与去重场景统一使用。基于
[ICU4J](https://unicode-org.github.io/icu/) 实现，保证结果可复现、可并发、可持久化。

## 功能概览

| 能力 | 说明 |
| --- | --- |
| 规范化 | NFC / NFD / NFKC / NFKD，幂等且等价写法归一 |
| 大小写折叠 | NFKC 兼容折叠 + 完整 Unicode 大小写折叠，结果可回溯到原文 |
| 多语言排序 | 重音 / 大小写分层（PRIMARY→IDENTICAL），区域可配置，顺序确定 |
| 持久化键 | 带版本号的排序键与检索键，跨进程稳定比较，长度有上限 |
| 字素簇 | 按 UAX #29 扩展字素簇切分与截断，不切裂组合序列 |
| 并发与缓存 | 有界 LRU 缓存，线程安全，命中与未命中路径结果一致 |
| 非法输入 | 未配对代理项、越界码点、空输入不崩溃，返回可区分结果 |

## 规范化语义

- 支持四种标准规范化形式：`NFC`、`NFD`、`NFKC`、`NFKD`。
- **幂等**：对已规范化的文本重复处理结果不变。
- **等价归一**：同一段文本的不同规范化写法（如 `é` 的组合与分解形式）在同一形式下
  归一到相同结果。
- 非法输入（未配对代理项、越界码点）在规范化前会被替换为 `U+FFFD`，规范化本身不抛出
  异常；可通过 `TextNormalizer.validity(...)` 或 REST 接口 `POST /api/v1/validity`
  获取输入有效性：`VALID` / `EMPTY` / `UNPAIRED_SURROGATE` / `OUT_OF_RANGE`。

```java
TextProcessingService service = new TextProcessingService(1024);
service.normalize("é", NormalizationForm.NFC); // "é" (U+00E9)
service.normalize("é", NormalizationForm.NFD); // "é"
```

## 折叠规则

折叠流水线为 **NFKC 兼容规范化 → 完整 Unicode 大小写折叠**，与搜索引擎常用的
compatibility case fold 一致：

- 全角 / 半角 → ASCII：`Ａ` → `a`；
- 兼容字符 → 展开：`ﬁ` → `fi`，`ｶﾞ` → `ガ`；
- 特殊大小写：`ß` → `ss`。

折叠结果可回溯：`FoldResult.sourceIndex()` 给出每个折叠后码点对应的**原文码点索引**。
在跨码点兼容组合（如半角浊点与前面假名合并）的情况下，合并后的输出码点归因到首个相关
源索引，仍可回溯到原文区域。

```java
FoldResult result = service.fold("ＡＢＣ ﬁ ß");
result.folded();      // "abc fi ss"
result.sourceIndex(); // 每个折叠后码点对应的原文码点索引
```

折叠是幂等的：对折叠结果再次折叠不变。

## 排序配置

排序按强度分层（`CollationStrength`）：

| 强度 | 忽略大小写 | 忽略重音 | 说明 |
| --- | --- | --- | --- |
| `PRIMARY` | 是 | 是 | 仅区分基本字符 |
| `SECONDARY` | 是 | 否 | 区分重音，不区分大小写 |
| `TERTIARY` | 否 | 否 | 默认，区分重音与大小写 |
| `IDENTICAL` | 否 | 否 | 区分一切差异 |

区域通过 BCP-47 语言标签配置（如 `de-DE`、`zh-CN`、`sv-SE`），空字符串表示根区域
（语言无关顺序）。例如德语 `de-DE` 在 PRIMARY 强度下 `ß` 与 `ss` 等价，而瑞典语
`sv-SE` 中 `ä` 排在 `z` 之后。

```java
CollationConfig config = new CollationConfig("de-DE", CollationStrength.PRIMARY, "1");
service.compare("ß", "ss", config); // 0
```

**确定性承诺**：相同输入与配置在任意线程、任意次执行下产生完全一致的排序键字节与
相对顺序。

## 键的稳定性承诺

### 排序键（SortKey）

二进制帧格式（大端）：

```
偏移 0  : 魔数 "UCOL" (4 字节)
偏移 4  : 键格式版本 (1 字节)
偏移 5  : 配置身份哈希 (4 字节)
偏移 9  : 区域标签长度(2) + 区域标签(UTF-8)
随后    : 强度序号 (1 字节)
随后    : 版本标签长度(2) + 版本标签(UTF-8)
随后    : 原始排序键长度(2) + 键字节
```

- **自描述**：`SortKey.fromBytes(byte[])` / `fromBase64(String)` 可重建完整
  `CollationConfig`，无需外部元数据。
- **不静默失效**：格式版本不受支持或帧损坏时抛出 `IllegalArgumentException`，
  不会静默产生错误顺序。
- **跨配置不可比**：不同 `CollationConfig` 生成的键相互比较时抛出
  `IllegalArgumentException`，避免错误排序。
- **长度有上限**：生成键前会在字素簇边界截断输入（默认最多 512 个字素簇），原始键
  字节硬上限 8192。截断后仍保证保留前缀的顺序；共享长前缀的不同文本可能判为相等。

### 检索键（RetrievalKey）

固定 37 字节：魔数 `"URET"`(4) + 版本(1) + 折叠文本的 SHA-256 摘要(32)。
规范化、大小写、兼容折叠等价的文本共享同一检索键，用于检索与去重。版本变化时旧键被
识别为不兼容。

## 字素簇边界行为

按 UAX #29 扩展字素簇切分（`BreakIterator.getCharacterInstance()`）：

- 截断**绝不切裂**字素簇、组合序列或代理对；
- `null` 视为空；
- 未配对代理项计为一个字素簇（无法再分）；
- 负数限制抛出 `IllegalArgumentException`；
- `truncateToBytes` 保证结果 UTF-8 字节数不超过预算，且不切裂字素簇。

```java
service.graphemeLength("é");        // 1 (e + 组合重音)
service.graphemeLength("👨‍👩‍👧");   // 1 (ZWJ 序列)
service.truncateGraphemes("ab👨‍👩‍👧cd", 3).text(); // "ab👨‍👩‍👧"
```

## 并发与缓存

- 所有服务方法线程安全；ICU `Collator` 在配置后调用 `freeze()`，键生成通过内部锁
  串行化，多线程并发生成排序键不会互相干扰。
- 缓存为有界 LRU（默认 1024 条，可通过 `text.cache.max-size` 配置），不同与相同
  输入都不会导致内存无界增长。
- 命中与未命中路径返回一致结果（缓存值为不可变对象，计算确定）。

## 非法输入处理

| 输入 | 处理 | 结果区分 |
| --- | --- | --- |
| 空字符串 / `null` | 返回空 | `InputValidity.EMPTY` |
| 未配对代理项 | 替换为 `U+FFFD` | `InputValidity.UNPAIRED_SURROGATE` |
| 越界码点（`> U+10FFFF`、负数、代理码点） | 替换为 `U+FFFD` | `InputValidity.OUT_OF_RANGE` |

非法输入不抛出异常，处理结果与正常输入可区分。

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/normalize?form=NFC` | 规范化 |
| POST | `/api/v1/fold` | 折叠（含回溯映射） |
| POST | `/api/v1/grapheme/length` | 字素簇计数 |
| POST | `/api/v1/grapheme/truncate?maxClusters=` | 字素截断 |
| POST | `/api/v1/validity` | 输入有效性 |
| GET | `/api/v1/sortKey?text=&locale=&strength=` | 排序键 |
| GET | `/api/v1/compare?a=&b=&locale=&strength=` | 比较 |
| GET | `/api/v1/retrievalKey?text=` | 检索键 |

## 配置

```properties
# 缓存最大条目数（默认 1024）
text.cache.max-size=1024
```

## 构建与测试

```bash
mvn test
```

测试覆盖：规范化等价与幂等、折叠与回溯、排序强度与区域、排序键确定性与往返、
字素簇切分与截断、并发与缓存、非法输入、REST 接口。
