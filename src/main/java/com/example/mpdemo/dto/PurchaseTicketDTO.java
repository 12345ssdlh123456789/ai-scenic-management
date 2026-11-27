package com.example.mpdemo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PurchaseTicketDTO {
    @NotNull(message = "项目ID不能为空")
    private Integer projectId;

    @NotNull(message = "购票数量不能为空")
    @Min(value = 1, message = "购票数量至少为1")
    private Integer quantity;
}
