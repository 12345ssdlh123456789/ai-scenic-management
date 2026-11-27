package com.example.mpdemo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserDTO {
    @NotNull(message = "用户ID不能为空")
    private Integer id;

    private String username;
    private String role;
    private String phone;
    private String email;
    private Integer status;
}
