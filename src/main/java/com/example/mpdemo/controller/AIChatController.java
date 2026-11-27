package com.example.mpdemo.controller;

import com.example.mpdemo.config.RateLimiter;
import com.example.mpdemo.dto.ChatRequestDTO;
import com.example.mpdemo.dto.KnowledgeDTO;
import com.example.mpdemo.entity.Result;
import com.example.mpdemo.filter.JwtDetails;
import com.example.mpdemo.service.AIChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 智能客服接口
 */
@RestController
@RequestMapping("/v1/ai")
@Tag(name = "AI智能客服", description = "景区智能问答接口")
public class AIChatController extends BaseController {

    @Autowired
    private AIChatService aiChatService;

    @Autowired
    private RateLimiter rateLimiter;

    @PostMapping("/chat")
    @Operation(summary = "AI 智能问答")
    public Result chat(@Valid @RequestBody ChatRequestDTO dto, HttpServletRequest request) {
        // SecurityConfig 全局要求 .anyRequest().authenticated()，未登录无法到达此处
        JwtDetails details = JwtDetails.from(SecurityContextHolder.getContext().getAuthentication());
        if (details == null) {
            return Result.error(401, "请先登录");
        }

        // AI 聊天接口限流：每用户每分钟最多 10 次
        String rateKey = "ai:chat:" + details.getUserId();
        if (!rateLimiter.tryAcquire(rateKey, 10, 60)) {
            return Result.error(429, "提问过于频繁，请稍后再试");
        }

        String answer = aiChatService.chat(dto.getQuestion());
        return Result.success("回答成功", Map.of("answer", answer));
    }

    @GetMapping("/knowledge")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "获取知识库（管理端）")
    public Result getKnowledge() {
        return Result.success("获取成功", aiChatService.getAllKnowledge());
    }

    @PostMapping("/knowledge")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "添加知识（管理端）")
    public Result addKnowledge(@Valid @RequestBody KnowledgeDTO dto) {
        aiChatService.addKnowledgeEntry(dto.getCategory(), dto.getQuestion(), dto.getAnswer());
        return Result.success("添加成功", null);
    }

    @DeleteMapping("/knowledge/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "删除知识（管理端）")
    public Result deleteKnowledge(@PathVariable Long id) {
        aiChatService.deleteKnowledge(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/stats")
    @Operation(summary = "知识库统计")
    public Result getStats() {
        return Result.success("统计成功", Map.of("count", aiChatService.getKnowledgeCount()));
    }
}