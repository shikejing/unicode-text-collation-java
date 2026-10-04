# 多语言文本规范化、折叠与排序键服务

本文档说明本仓库文本处理服务的规范化语义、排序配置与键的稳定性承诺。

## 组件总览

| 组件 | 职责 |
| --- | --- |
| `InputSanitizer` | 非法输入的确定性修复（未配对代理项 → U+FFFD） |
| `NormalizationService` | NFC / NFD / NFKC / NFKD 规范化 |
| `CaseFoldingService` | NFKC 兼容等价 + 大小写折叠，结果可回溯 |
| `CollationService` | 区域与强度可配置的多语言排序 |
| `GraphemeService` | 字素簇（grapheme cluster）切分、计数与截断 |
| `SortKeyService` | 可持久化排序键与检索键（带版本标签与长度上限） |
| `TextProcessingService` | 线程安全门面，内置有界 LRU 缓存 |
| `TextProcessingController` | HTTP 接口（`/api/text/**`） |

## 1. 规范化语义

- 同一文本的规范等价写法（如 `é` 与 `e + ◌́`）在同一规范化形式下**必然**归一到相同结果。
- 规范化是幂等的：对已规范化文本重复处理结果不变（`normalize(normalize(x)) == normalize(x)`）。
- 兼容等价（全角/半角、圈号数字、连字等）仅在 NFKC / NFKD 下统一；NFC / NFD 保持兼容字符原样。
- `null` 与空串原样传递，不抛异常。

## 2. 大小写折叠与兼容等价

折叠流水线（确定性、幂等）：

1. NFKC 规范化（全角→半角、兼容字符→标准形式）；
2. 特殊全折叠映射（`ß→ss`、`ẞ→ss`、`ς→σ` 等）；
3. 与区域无关的小写化（`Locale.ROOT`）。

每次折叠返回 `FoldingResult(original, folded)`，保留原始文本，折叠结果可回溯到来源。

## 3. 排序配置

- **区域**：通过 `java.util.Locale` 配置（如 `sv` 中 `ä` 排在 `z` 之后，ROOT 中排在 `a` 附近）。
- **比较强度**（`CollationStrength`）：
  - `PRIMARY`：忽略重音与大小写（`resume` = `résumé` = `RESUME`）；
  - `SECONDARY`：区分重音，忽略大小写；
  - `TERTIARY`：区分重音与大小写；
  - `IDENTICAL`：完全区分。
- **确定性承诺**：`comparator()` 在配置强度下相等返回 0；`sort()` 内部使用
  `deterministicComparator()`，对强度下相等的字符串按 IDENTICAL 比较再按码点次序打破平局，
  因此相同输入在任意次执行下产生**完全一致**的顺序，与输入列表的初始顺序无关。
- 比较前先做 NFC 规范化，规范等价字符串在任何强度下都判定相等。

## 4. 键的稳定性承诺

键格式：`v<rulesVersion>|<配置>|<负载>`。

- **跨进程稳定**：排序键负载为排序规则字节的小写十六进制串，普通字符串字典序比较即复现排序顺序，
  不依赖进程内状态。
- **版本标签**：`SortKeyService.RULES_VERSION` 绑定生成键所用的规范化/折叠规则。
  规则变化时必须递增该版本；`isCurrent(key)` 可识别过期键。**过期键不得静默复用，必须重新生成**。
- **配置隔离**：排序键内嵌区域标签与强度，不同配置生成的键不会互相误判。
- **长度上限**：所有键按 `maxLength` 截断（默认 512，绝对上限 4096），截断点绝不切裂代理项对。

## 5. 字素簇边界语义

- 字素簇 = 用户感知字符：基础字符 + 组合记号、emoji ZWJ 序列、区域指示符（国旗）对等。
- `truncate(input, maxClusters)`：
  - `maxClusters <= 0` → 返回 `""`；
  - `maxClusters >= 簇数` → 原样返回；
  - 否则返回前 `maxClusters` 个簇，**绝不在簇内或组合序列中间切断**。
- `null` 输入：`truncate` 返回 `null`，`graphemeCount` 返回 0。

## 6. 并发与缓存

- 所有服务方法线程安全；并发处理相同或不同文本互不干扰。
- `TextProcessingService` 内置有界 LRU 缓存（上限 10,000 条），重复输入不会导致缓存无界增长。
- 缓存只存储同一纯计算的结果，命中与未命中路径返回值完全一致。

## 7. 非法输入处理

- 未配对代理项：逐个替换为 U+FFFD，状态标记为 `REPAIRED`，与 `CLEAN` 可区分。
- null / 空输入：状态 `EMPTY`，文本按 `""` 处理，不抛异常。
- 所有处理结果对相同非法输入是确定性的。
