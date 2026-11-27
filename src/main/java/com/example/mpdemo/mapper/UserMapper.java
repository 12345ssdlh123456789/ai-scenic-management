package com.example.mpdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mpdemo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子扣减余额（含余额检查）
     * 解决并发情况下"读余额→扣减"两步操作的超扣问题
     *
     * @return 影响行数（0 表示用户不存在或余额不足）
     */
    @Update("UPDATE t_user SET balance = balance - #{amount} WHERE id = #{userId} AND balance >= #{amount}")
    int deductBalance(Integer userId, BigDecimal amount);

    /**
     * 原子增加余额（退款/充值到账）
     *
     * @return 影响行数
     */
    @Update("UPDATE t_user SET balance = balance + #{amount} WHERE id = #{userId}")
    int addBalance(Integer userId, BigDecimal amount);
}
