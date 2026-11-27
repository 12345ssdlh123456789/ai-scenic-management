package com.example.mpdemo.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mpdemo.config.ScenicProperties;
import com.example.mpdemo.entity.User;
import com.example.mpdemo.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * VIP 过期自动降级定时任务
 * 每天凌晨 2:00 执行一次，扫描所有过期的 VIP 用户，自动降级为普通用户
 */
@Slf4j
@Component
@EnableScheduling
public class VipExpiryTask {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ScenicProperties scenicProperties;

    /**
     * 每天凌晨 2:00 执行 VIP 过期检查
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void checkVipExpiry() {
        log.info("[VIP过期检查] 开始执行...");
        long startTime = System.currentTimeMillis();

        try {
            // 查询所有过期 VIP 用户：角色=VIP 且 vip_expire_time <= 当前时间
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("role", "VIP")
                    .isNotNull("vip_expire_time")
                    .le("vip_expire_time", LocalDateTime.now());

            List<User> expiredVips = userMapper.selectList(queryWrapper);

            if (expiredVips.isEmpty()) {
                log.info("[VIP过期检查] 本次无过期 VIP 用户");
                return;
            }

            int downgradeCount = 0;
            for (User user : expiredVips) {
                user.setRole("USER");
                user.setVipExpireTime(null);
                int rows = userMapper.updateById(user);
                if (rows > 0) {
                    downgradeCount++;
                    log.info("[VIP过期检查] 用户 {} (ID={}) VIP 已过期，降级为普通用户",
                            user.getUsername(), user.getId());
                } else {
                    log.warn("[VIP过期检查] 用户 {} (ID={}) 降级失败（乐观锁冲突或用户不存在）",
                            user.getUsername(), user.getId());
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[VIP过期检查] 执行完毕，共处理 {}/{} 个过期 VIP 用户，耗时 {}ms",
                    downgradeCount, expiredVips.size(), elapsed);

        } catch (Exception e) {
            log.error("[VIP过期检查] 执行异常", e);
        }
    }
}