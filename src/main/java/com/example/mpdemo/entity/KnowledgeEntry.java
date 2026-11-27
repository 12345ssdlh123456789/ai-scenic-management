package com.example.mpdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_entry")
public class KnowledgeEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String category;
    private String question;
    private String answer;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
