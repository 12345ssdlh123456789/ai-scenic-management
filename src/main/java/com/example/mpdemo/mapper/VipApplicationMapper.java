package com.example.mpdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mpdemo.entity.VipApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VipApplicationMapper extends BaseMapper<VipApplication> {

    /**
     * 原子更新 VIP 申请状态（防止并发重复审核）
     *
     * @param applicationId 申请 ID
     * @param newStatus     新状态（APPROVED / REJECTED）
     * @param oldStatus     期望的旧状态（PENDING）
     * @return 影响行数（0 表示状态已被其他事务修改）
     */
    @Update("UPDATE t_vip_application SET status = #{newStatus} WHERE id = #{applicationId} AND status = #{oldStatus}")
    int updateApplicationStatus(Integer applicationId, String newStatus, String oldStatus);
}
