package com.example.mpdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.config.ScenicProperties;
import com.example.mpdemo.entity.BusinessException;
import com.example.mpdemo.entity.RechargeApplication;
import com.example.mpdemo.entity.User;
import com.example.mpdemo.mapper.RechargeApplicationMapper;
import com.example.mpdemo.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class RechargeServiceTest {

    @Mock
    private RechargeApplicationMapper rechargeApplicationMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ScenicProperties scenicProperties;

    @InjectMocks
    private RechargeService rechargeService;

    private User testUser;

    @BeforeEach
    void setUp() {
        when(scenicProperties.getRechargeMinAmount()).thenReturn(new BigDecimal("1.00"));
        when(scenicProperties.getRechargeMaxAmount()).thenReturn(new BigDecimal("10000.00"));

        testUser = new User();
        testUser.setId(1);
        testUser.setUsername("testuser");
        testUser.setBalance(new BigDecimal("100.00"));
    }

    @Test
    void applyRecharge_success() {
        when(rechargeApplicationMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(rechargeApplicationMapper.insert(any(RechargeApplication.class))).thenReturn(1);

        String result = rechargeService.applyRecharge(1, "testuser", new BigDecimal("500"));

        assertEquals("申请提交成功", result);
        verify(rechargeApplicationMapper).insert(any(RechargeApplication.class));
    }

    @Test
    void applyRecharge_pendingExists() {
        when(rechargeApplicationMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> rechargeService.applyRecharge(1, "testuser", new BigDecimal("500")));
        assertEquals("您已有待审核的充值申请", ex.getMessage());
    }

    @Test
    void applyRecharge_invalidAmount() {
        assertThrows(BusinessException.class,
                () -> rechargeService.applyRecharge(1, "testuser", BigDecimal.ZERO));
        assertThrows(BusinessException.class,
                () -> rechargeService.applyRecharge(1, "testuser", new BigDecimal("-10")));
    }

    @Test
    void applyRecharge_belowMinAmount() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> rechargeService.applyRecharge(1, "testuser", new BigDecimal("0.50")));
        assertTrue(ex.getMessage().contains("不能低于"));
    }

    @Test
    void applyRecharge_aboveMaxAmount() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> rechargeService.applyRecharge(1, "testuser", new BigDecimal("99999.00")));
        assertTrue(ex.getMessage().contains("不能高于"));
    }

    @Test
    void approveRecharge_success() {
        RechargeApplication app = new RechargeApplication();
        app.setId(1);
        app.setUserId(1);
        app.setAmount(new BigDecimal("500.00"));
        app.setStatus("PENDING");

        when(rechargeApplicationMapper.selectById(1)).thenReturn(app);
        when(rechargeApplicationMapper.updateApplicationStatus(1, "APPROVED", "PENDING")).thenReturn(1);
        when(userMapper.addBalance(1, new BigDecimal("500.00"))).thenReturn(1);

        String result = rechargeService.approveRecharge(1, 10, "审核通过");

        assertEquals("审批通过，充值成功", result);
        // 原子状态流转 + 原子到账，杜绝并发重复审批
        verify(rechargeApplicationMapper).updateApplicationStatus(1, "APPROVED", "PENDING");
        verify(userMapper).addBalance(1, new BigDecimal("500.00"));
    }

    @Test
    void approveRecharge_alreadyProcessed() {
        RechargeApplication app = new RechargeApplication();
        app.setId(1);
        app.setStatus("APPROVED");

        when(rechargeApplicationMapper.selectById(1)).thenReturn(app);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> rechargeService.approveRecharge(1, 10, ""));
        assertEquals("申请状态不正确", ex.getMessage());
    }

    @Test
    void approveRecharge_concurrentConflict() {
        RechargeApplication app = new RechargeApplication();
        app.setId(1);
        app.setUserId(1);
        app.setAmount(new BigDecimal("500.00"));
        app.setStatus("PENDING");

        when(rechargeApplicationMapper.selectById(1)).thenReturn(app);
        // 并发下原子状态更新返回 0（其他线程已处理）
        when(rechargeApplicationMapper.updateApplicationStatus(1, "APPROVED", "PENDING")).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> rechargeService.approveRecharge(1, 10, ""));
        assertEquals("该申请已被处理，请刷新后再试", ex.getMessage());
        // 余额不得重复累加
        verify(userMapper, never()).addBalance(anyInt(), any());
    }

    @Test
    void rejectRecharge_success() {
        RechargeApplication app = new RechargeApplication();
        app.setId(1);
        app.setUserId(1);
        app.setStatus("PENDING");

        when(rechargeApplicationMapper.selectById(1)).thenReturn(app);
        when(rechargeApplicationMapper.updateApplicationStatus(1, "REJECTED", "PENDING")).thenReturn(1);

        String result = rechargeService.rejectRecharge(1, 10, "不符合条件");

        assertEquals("已拒绝该申请", result);
        verify(rechargeApplicationMapper).updateApplicationStatus(1, "REJECTED", "PENDING");
    }

    @Test
    void getUserApplications_returnsPage() {
        when(rechargeApplicationMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(new Page<>());

        IPage<RechargeApplication> result = rechargeService.getUserApplications(1, 1, 10);

        assertNotNull(result);
        verify(rechargeApplicationMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }
}