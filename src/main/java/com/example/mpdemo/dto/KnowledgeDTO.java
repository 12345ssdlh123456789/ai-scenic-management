package com.example.mpdemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeDTO {
    @NotBlank(message = "分类不能为空")
    @Size(max = 50)
    private String category;

    @NotBlank(message = "问题不能为空")
    @Size(max = 500)
    private String question;

    @NotBlank(message = "答案不能为空")
    @Size(max = 2000)
    private String answer;
}
