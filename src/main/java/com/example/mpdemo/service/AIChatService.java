package com.example.mpdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mpdemo.config.AIConfig;
import com.example.mpdemo.entity.BusinessException;
import com.example.mpdemo.entity.KnowledgeEntry;
import com.example.mpdemo.mapper.KnowledgeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI 智能客服 Service
 * - 知识库缓存带 TTL，过期自动刷新
 * - 使用 Redis 做分布式缓存，多节点共享
 * - 中文分词使用字符级 n-gram 匹配（无需第三方库）
 */
@Slf4j
@Service
public class AIChatService {

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KNOWLEDGE_CACHE_KEY = "ai:knowledge:full";
    private static final long CACHE_TTL_MINUTES = 30;

    /**
     * 关键词映射表：将口语化表达映射到知识库中的标准术语
     * 解决"门票多少钱"→"票价"、"开门"→"开放时间" 这类 bigram 匹配不到的问题
     */
    private static final java.util.Map<String, String> KEYWORD_ALIASES = new java.util.HashMap<>();
    static {
        KEYWORD_ALIASES.put("门票", "票价");
        KEYWORD_ALIASES.put("多少钱", "票价");
        KEYWORD_ALIASES.put("价格", "票价");
        KEYWORD_ALIASES.put("收费", "票价");
        KEYWORD_ALIASES.put("几点开门", "开放时间");
        KEYWORD_ALIASES.put("几点关门", "开放时间");
        KEYWORD_ALIASES.put("营业时间", "开放时间");
        KEYWORD_ALIASES.put("好玩", "景点介绍");
        KEYWORD_ALIASES.put("项目", "景点介绍");
        KEYWORD_ALIASES.put("有什么", "景点介绍");
        KEYWORD_ALIASES.put("怎么去", "交通");
        KEYWORD_ALIASES.put("怎么到", "交通");
        KEYWORD_ALIASES.put("停车场", "交通");
        KEYWORD_ALIASES.put("开车", "交通");
        KEYWORD_ALIASES.put("地铁", "交通");
        KEYWORD_ALIASES.put("公交", "交通");
        KEYWORD_ALIASES.put("宠物", "注意事项");
        KEYWORD_ALIASES.put("带狗", "注意事项");
        KEYWORD_ALIASES.put("食物", "注意事项");
        KEYWORD_ALIASES.put("下雨", "注意事项");
        KEYWORD_ALIASES.put("储物柜", "注意事项");
    }

    // 复用 HttpClient 实例
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();

    /**
     * 从缓存加载知识库
     * 优先使用 Redis 分布式缓存，降级到本地缓存
     */
    private List<KnowledgeEntry> loadKnowledge() {
        // 1. 尝试从 Redis 读取
        if (redisTemplate != null) {
            try {
                Object cached = redisTemplate.opsForValue().get(KNOWLEDGE_CACHE_KEY);
                if (cached != null) {
                    @SuppressWarnings("unchecked")
                    List<KnowledgeEntry> list = (List<KnowledgeEntry>) cached;
                    // 防御性校验：泛型强转在运行时会擦除类型，不会报错。
                    // 若 Redis 里的旧值是用「关闭 DefaultTyping」的序列化器写入的
                    // （不含 @class），这里的元素其实是 LinkedHashMap，
                    // 直到被当作 KnowledgeEntry 使用才抛 ClassCastException，
                    // 表现为 AI 客服一直"系统繁忙"，且堆栈指向检索逻辑、极难定位。
                    // 在这里显式校验并降级到数据库，同时清掉这批坏缓存。
                    if (!list.isEmpty() && !(list.get(0) instanceof KnowledgeEntry)) {
                        log.warn("知识库缓存类型不符（实际 {}），已清除并回退数据库直查",
                                list.get(0).getClass().getName());
                        evictKnowledgeCache();
                    } else {
                        log.debug("知识库命中 Redis 缓存，共 {} 条", list.size());
                        return list;
                    }
                }
            } catch (Exception e) {
                log.warn("Redis 缓存读取失败，降级到数据库直查", e);
            }
        }

        // 2. 从数据库加载
        QueryWrapper<KnowledgeEntry> qw = new QueryWrapper<>();
        List<KnowledgeEntry> entries = knowledgeMapper.selectList(qw);
        if (entries == null) entries = Collections.emptyList();

        // 3. 写入 Redis 缓存
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(KNOWLEDGE_CACHE_KEY, entries,
                        CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                log.info("知识库缓存加载完成（Redis），共 {} 条", entries.size());
            } catch (Exception e) {
                log.warn("Redis 缓存写入失败", e);
            }
        } else {
            log.info("知识库缓存加载完成（本地），共 {} 条", entries.size());
        }

        return entries;
    }

    /**
     * 清除知识库缓存（在增删知识时调用）
     */
    private void evictKnowledgeCache() {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(KNOWLEDGE_CACHE_KEY);
                log.info("知识库缓存已清除");
            } catch (Exception e) {
                log.warn("Redis 缓存清除失败", e);
            }
        }
    }

    public String chat(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return "请输入您的问题";
        }

        // 前置检查：API Key 未配置直接报错（503 表示服务不可用）
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            throw new BusinessException(503, "AI 客服暂未配置，请联系管理员设置 DASHSCOPE_API_KEY");
        }

        List<KnowledgeEntry> relevantKnowledge = retrieve(userInput);
        String prompt = buildPrompt(userInput, relevantKnowledge);
        String answer = callQwenAPI(prompt);
        log.info("用户问题: {}, 检索知识数: {}", userInput, relevantKnowledge.size());
        return answer;
    }

    private List<KnowledgeEntry> retrieve(String question) {
        List<KnowledgeEntry> entries = loadKnowledge();
        String qLower = normalize(question);

        // 关键词别名扩展：将用户口语化表达映射到知识库类别，提高匹配度
        String expanded = qLower;
        for (Map.Entry<String, String> alias : KEYWORD_ALIASES.entrySet()) {
            if (qLower.contains(alias.getKey())) {
                expanded = expanded + " " + alias.getValue();
            }
        }

        final String queryText = expanded;

        return entries.stream()
                .map(entry -> {
                    double score = calculateSimilarity(queryText, entry);
                    return new ScoredEntry(entry, score);
                })
                .filter(se -> se.score > 0.05)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(5)
                .map(se -> se.entry)
                .collect(Collectors.toList());
    }

    /**
     * 标准化文本：转小写 + 去除标点符号
     */
    private String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[\\p{P}\\p{S}]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static class ScoredEntry {
        KnowledgeEntry entry;
        double score;
        ScoredEntry(KnowledgeEntry entry, double score) {
            this.entry = entry;
            this.score = score;
        }
    }

    /**
     * 中文相似度计算：字符级 bigram 重叠度
     * 比单字匹配更鲁棒，不需要第三方分词库
     */
    private double calculateSimilarity(String qLower, KnowledgeEntry entry) {
        String qNorm = normalize(entry.getQuestion());
        String aNorm = normalize(entry.getAnswer());

        List<String> qBigrams = getBigrams(qNorm);
        List<String> aBigrams = getBigrams(aNorm);
        List<String> qWords = splitWords(qLower);

        int qInQuestion = 0;
        for (String w : qWords) {
            if (w.length() > 1 && qNorm.contains(w)) qInQuestion++;
        }
        double keywordScore = qWords.stream().filter(w -> w.length() > 1).count() > 0
                ? (double) qInQuestion / qWords.stream().filter(w -> w.length() > 1).count()
                : 0;

        int qInAnswer = 0;
        for (String w : qWords) {
            if (w.length() > 1 && aNorm.contains(w)) qInAnswer++;
        }
        double answerScore = qWords.stream().filter(w -> w.length() > 1).count() > 0
                ? (double) qInAnswer / qWords.stream().filter(w -> w.length() > 1).count()
                : 0;

        // bigram 重叠度
        double bigramScore = 0;
        if (!qBigrams.isEmpty() && !aBigrams.isEmpty()) {
            long overlap = qBigrams.stream().filter(aBigrams::contains).count();
            bigramScore = (double) overlap / Math.max(qBigrams.size(), aBigrams.size());
        }

        // 综合评分：bigram 权重最高（中文特性），其次是关键词和答案匹配
        return keywordScore * 0.2 + answerScore * 0.2 + bigramScore * 0.6;
    }

    /**
     * 提取字符级 bigram
     */
    private List<String> getBigrams(String text) {
        List<String> bigrams = new ArrayList<>();
        for (int i = 0; i < text.length() - 1; i++) {
            bigrams.add(text.substring(i, i + 2));
        }
        return bigrams;
    }

    /**
     * 按字符 bigram 切分（中文无空格，逐字切分无法表达词义）
     * 同时保留英文/数字单词
     */
    private List<String> splitWords(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        // 1) 提取连续英文/数字单词
        java.util.regex.Matcher enMatcher = java.util.regex.Pattern.compile("[a-z0-9]+").matcher(text);
        while (enMatcher.find()) {
            tokens.add(enMatcher.group());
        }
        // 2) 提取连续中文（每个 bigram 作为 token）
        java.util.regex.Matcher cnMatcher = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]+").matcher(text);
        while (cnMatcher.find()) {
            String segment = cnMatcher.group();
            for (int i = 0; i < segment.length() - 1; i++) {
                tokens.add(segment.substring(i, i + 2));
            }
            if (segment.length() == 1) {
                tokens.add(segment);
            }
        }
        return tokens;
    }

    private String buildPrompt(String question, List<KnowledgeEntry> knowledge) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的景区智能客服，请用友好、专业的语气回答游客的问题。\n\n");
        sb.append("【参考知识】\n");
        if (knowledge.isEmpty()) {
            sb.append("未找到相关参考信息。\n\n");
        } else {
            for (int i = 0; i < knowledge.size(); i++) {
                KnowledgeEntry entry = knowledge.get(i);
                sb.append(String.format("%d. [%s] %s\n", i + 1, entry.getCategory(), entry.getAnswer()));
            }
        }
        sb.append("\n【问题】").append(question);
        sb.append("\n\n请根据以上参考知识回答问题。如果知识中有相关信息，请基于知识回答；");
        sb.append("如果知识中没有相关信息，请如实告知用户，不要编造信息。回答控制在200字以内。");
        return sb.toString();
    }

    private String callQwenAPI(String prompt) {
        try {
            var messagesArray = objectMapper.createArrayNode();
            // 添加系统提示，强制中文回答
            messagesArray.add(objectMapper.createObjectNode()
                    .put("role", "system")
                    .put("content", "You are a professional scenic park customer service assistant. Always respond in Chinese."));
            messagesArray.add(objectMapper.createObjectNode()
                    .put("role", "user")
                    .put("content", prompt));

            var requestBody = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.createObjectNode()
                    .put("model", aiConfig.getModel())
                    .set("messages", messagesArray);
            ((com.fasterxml.jackson.databind.node.ObjectNode) requestBody)
                    .put("temperature", 0.7)
                    .put("top_p", 0.8);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiConfig.getBaseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + aiConfig.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode content = choices.get(0).path("message").path("content");
                    return content.asText();
                }
                log.error("NVIDIA API 返回格式异常: {}", response.body());
                throw new BusinessException(502, "抱歉，AI 服务返回格式异常，请稍后再试");
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.error("NVIDIA API 认证失败，状态码: {}，请检查 NVIDIA_API_KEY", response.statusCode());
                throw new BusinessException(502, "AI 服务认证失败，请联系管理员检查 API Key 配置");
            } else {
                log.error("NVIDIA API 调用失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                throw new BusinessException(502, "抱歉，AI 服务暂时不可用，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 NVIDIA API 失败", e);
            throw new BusinessException(502, "抱歉，AI 服务暂时不可用，请稍后再试或联系人工客服");
        }
    }

    public List<KnowledgeEntry> getAllKnowledge() {
        return Collections.unmodifiableList(loadKnowledge());
    }

    public void addKnowledgeEntry(String category, String question, String answer) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setCategory(category);
        entry.setQuestion(question);
        entry.setAnswer(answer);
        knowledgeMapper.insert(entry);

        // 清除缓存，下次查询时重新加载
        evictKnowledgeCache();
        log.info("新增知识库条目: category={}, question={}", category, question);
    }

    public void deleteKnowledge(Long id) {
        knowledgeMapper.deleteById(id);

        // 清除缓存
        evictKnowledgeCache();
        log.info("删除知识库条目: id={}", id);
    }

    public int getKnowledgeCount() {
        return loadKnowledge().size();
    }
}
