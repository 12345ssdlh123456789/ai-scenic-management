package com.example.mpdemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequestDTO {
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500字符")
    private String question;
}
