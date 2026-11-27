package com.example.mpdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 分页查询所有订单（含用户名）—— 手动分页，避免 MyBatis-Plus 自动生成的 COUNT 不准
     */
    @Select("SELECT o.*, u.username FROM t_order o LEFT JOIN t_user u ON o.user_id = u.id " +
            "ORDER BY o.order_time DESC")
    List<Order> selectPageWithUser(Page<Order> page);

    /**
     * 获取所有订单总数（用于 selectPageWithUser 的手动分页）
     */
    @Select("SELECT COUNT(*) FROM t_order")
    long countAllOrders();

    /**
     * 分页查询并过滤订单（含用户名）—— 手动分页
     */
    @Select("<script>" +
            "SELECT o.*, u.username FROM t_order o LEFT JOIN t_user u ON o.user_id = u.id " +
            "<where>" +
            "<if test='projectName != null and projectName != \"\"'>" +
            "AND o.project_name LIKE CONCAT('%', #{projectName}, '%')" +
            "</if>" +
            "<if test='username != null and username != \"\"'>" +
            "AND u.username LIKE CONCAT('%', #{username}, '%')" +
            "</if>" +
            "</where>" +
            "ORDER BY o.order_time DESC" +
            "</script>")
    List<Order> selectPageWithUserAndFilter(Page<Order> page, String projectName, String username);

    /**
     * 获取过滤后的订单总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM t_order o LEFT JOIN t_user u ON o.user_id = u.id " +
            "<where>" +
            "<if test='projectName != null and projectName != \"\"'>" +
            "AND o.project_name LIKE CONCAT('%', #{projectName}, '%')" +
            "</if>" +
            "<if test='username != null and username != \"\"'>" +
            "AND u.username LIKE CONCAT('%', #{username}, '%')" +
            "</if>" +
            "</where>" +
            "</script>")
    long countOrdersWithFilter(String projectName, String username);

    /**
     * 原子更新订单状态（防止并发重复操作）
     *
     * @param orderId   订单 ID
     * @param newStatus 新状态
     * @param oldStatus 期望的旧状态
     * @return 影响行数（0 表示状态已被其他事务修改）
     */
    @Update("UPDATE t_order SET status = #{newStatus} WHERE id = #{orderId} AND status = #{oldStatus}")
    int updateOrderStatus(Integer orderId, String newStatus, String oldStatus);
}
