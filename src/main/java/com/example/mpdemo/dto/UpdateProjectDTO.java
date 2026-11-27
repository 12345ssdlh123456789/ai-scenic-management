package com.example.mpdemo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 景点项目管理更新 DTO
 * 与 ScenicProject 实体解耦，防止前端传入 id/version/createTime 等敏感字段
 */
@Data
public class UpdateProjectDTO {
    @NotNull(message = "项目ID不能为空")
    private Integer id;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称长度不能超过200")
    private String name;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;

    @Min(value = 0, message = "库存不能小于0")
    private Integer stock;

    @Size(max = 2000, message = "描述长度不能超过2000")
    private String description;

    @Size(max = 500, message = "图片链接长度不能超过500")
    private String imageUrl;
}
