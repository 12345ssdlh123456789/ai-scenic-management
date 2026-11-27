package com.example.mpdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.entity.ScenicProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ScenicProjectMapper extends BaseMapper<ScenicProject> {

    /**
     * 原子扣减库存（含库存检查 + 上架状态检查）
     * 防止并发超卖，同时避免对已下架项目继续扣库存
     *
     * @param projectId 项目 ID
     * @param quantity  扣减数量
     * @return 影响行数（0 表示库存不足或项目已下架）
     */
    @Update("UPDATE t_scenic_project SET stock = stock - #{quantity}, version = version + 1 " +
            "WHERE id = #{projectId} AND stock >= #{quantity} AND status = 1")
    int deductStock(Integer projectId, Integer quantity);

    /**
     * 原子恢复库存（取消订单/退款时使用）
     *
     * @param projectId 项目 ID
     * @param quantity  恢复数量
     * @return 影响行数
     */
    @Update("UPDATE t_scenic_project SET stock = stock + #{quantity}, version = version + 1 WHERE id = #{projectId}")
    int restoreStock(Integer projectId, Integer quantity);

    /**
     * 查询可用项目列表（分页）
     */
    @Select("<script>" +
            "SELECT * FROM t_scenic_project WHERE status = 1" +
            "<if test='name != null and name != \"\"'>" +
            " AND name LIKE CONCAT('%', #{name}, '%')" +
            "</if>" +
            " ORDER BY create_time DESC" +
            "</script>")
    IPage<ScenicProject> selectAvailablePage(Page<ScenicProject> page, String name);

    /**
     * 查询所有项目列表（分页，含已下架）
     */
    @Select("<script>" +
            "SELECT * FROM t_scenic_project" +
            "<if test='name != null and name != \"\"'>" +
            " WHERE name LIKE CONCAT('%', #{name}, '%')" +
            "</if>" +
            " ORDER BY create_time DESC" +
            "</script>")
    IPage<ScenicProject> selectAllPage(Page<ScenicProject> page, String name);

    /**
     * 批量查询可用项目（用于购票后展示列表）
     */
    @Select("SELECT * FROM t_scenic_project WHERE status = 1 ORDER BY create_time DESC")
    List<ScenicProject> selectAvailableAll();
}
