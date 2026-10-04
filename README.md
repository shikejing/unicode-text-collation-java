# unicode-text-collation-java

面向检索与去重场景的多语言文本规范化、折叠与排序键生成服务（Spring Boot / Java 17）。

## 能力

- Unicode 规范化（NFC/NFD/NFKC/NFKD），幂等且规范等价写法归一
- 大小写折叠与兼容等价（全角/半角、ß、连字等），结果可回溯
- 多语言排序：区域与比较强度可配置，结果完全确定
- 可持久化排序键/检索键：版本标签、跨进程字典序可比较、长度有上限
- 字素簇正确的切分、计数与截断（ICU4J）
- 并发安全 + 有界 LRU 缓存；非法输入确定性修复

## 构建与测试

```bash
mvn compile
mvn test
```

## HTTP 接口

| 接口 | 说明 |
| --- | --- |
| `POST /api/text/normalize` | 消毒 + NFC 规范化 |
| `POST /api/text/search-key?maxLength=512` | 检索/去重键 |
| `POST /api/text/sort-key?locale=sv&strength=TERTIARY&maxLength=512` | 排序键 |
| `POST /api/text/sort?locale=&strength=TERTIARY` | 排序字符串列表 |
| `POST /api/text/truncate?maxClusters=10` | 按字素簇截断 |

详细语义、排序配置与键的稳定性承诺见 [docs/text-processing.md](docs/text-processing.md)。
