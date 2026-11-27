package com.example.mpdemo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 景点项目 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectVO {

    private Integer id;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String description;
    private String imageUrl;
    private Integer status;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
