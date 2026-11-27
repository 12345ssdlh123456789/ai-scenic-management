package com.example.mpdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mpdemo.config.ScenicProperties;
import com.example.mpdemo.entity.BusinessException;
import com.example.mpdemo.entity.ScenicProject;
import com.example.mpdemo.mapper.ScenicProjectMapper;
import com.example.mpdemo.vo.ProjectVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ScenicProjectService {

    @Autowired
    private ScenicProjectMapper scenicProjectMapper;

    @Autowired
    private ScenicProperties scenicProperties;

    @Autowired(required = false)
    private CacheManager cacheManager;

    /**
     * 清除 projects 缓存。
     * <p>
     * 必须整体清空：读路径的 key 由当前页/每页条数/搜索词动态构成
     * （{@code list:<current>:<size>:<name>}）以及 {@code detail:<id>}，
     * 任何单 key 的硬编码都无法覆盖，会造成库存/价格长期陈旧。
     * 项目增删改不是高频操作，整体清空成本可接受。
     */
    private void evictProjectCache() {
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("projects");
            if (cache != null) {
                cache.clear();
            }
        }
    }

    public IPage<ScenicProject> getAvailableProjects(int current, int size, String name) {
        Page<ScenicProject> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<ScenicProject> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);

        if (name != null && !name.trim().isEmpty()) {
            queryWrapper.like("name", name.trim());
        }

        queryWrapper.orderByDesc("create_time");
        return scenicProjectMapper.selectPage(page, queryWrapper);
    }

    public IPage<ProjectVO> getAvailableProjectsVO(int current, int size, String name) {
        IPage<ScenicProject> page = getAvailableProjects(current, size, name);
        return page.convert(this::toProjectVO);
    }

    public IPage<ScenicProject> getAllProjects(int current, int size, String name) {
        Page<ScenicProject> page = new Page<>(current, Math.min(size, 100));
        QueryWrapper<ScenicProject> queryWrapper = new QueryWrapper<>();

        if (name != null && !name.trim().isEmpty()) {
            queryWrapper.like("name", name.trim());
        }

        queryWrapper.orderByDesc("create_time");
        return scenicProjectMapper.selectPage(page, queryWrapper);
    }

    public IPage<ProjectVO> getAllProjectsVO(int current, int size, String name) {
        IPage<ScenicProject> page = getAllProjects(current, size, name);
        return page.convert(this::toProjectVO);
    }

    public ScenicProject getProjectById(Integer id) {
        return scenicProjectMapper.selectById(id);
    }

    public ProjectVO getProjectByIdVO(Integer id) {
        ScenicProject project = scenicProjectMapper.selectById(id);
        return project != null ? toProjectVO(project) : null;
    }

    public String addProject(ScenicProject project) {
        // 确保关键字段不会被外部篡改
        project.setStatus(1);
        project.setId(null);
        project.setCreateTime(null);
        project.setUpdateTime(null);
        project.setVersion(null);

        int rows = scenicProjectMapper.insert(project);
        if (rows > 0) {
            evictProjectCache();
            return "添加成功";
        }
        throw new BusinessException("添加失败");
    }

    public String updateProject(ScenicProject project) {
        // 确保关键字段不会被外部篡改
        project.setCreateTime(null);
        project.setUpdateTime(null);
        project.setVersion(null);
        // 注意：保留 id，否则 updateById 会因 id 为 null 而失败

        int rows = scenicProjectMapper.updateById(project);
        if (rows > 0) {
            evictProjectCache();
            return "更新成功";
        }
        throw new BusinessException("更新失败");
    }

    /**
     * 软删除项目（下架）
     * 将 status 设为 0，不物理删除数据
     */
    public String softDeleteProject(Integer id) {
        ScenicProject project = scenicProjectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }

        project.setStatus(0);
        int rows = scenicProjectMapper.updateById(project);
        if (rows > 0) {
            evictProjectCache();
            return "删除成功";
        }
        throw new BusinessException("删除失败");
    }

    public BigDecimal calculatePrice(BigDecimal originalPrice, String userRole) {
        // VIP 及管理员（ADMIN/SUPER_ADMIN）均享 8 折优惠
        if ("VIP".equals(userRole) || "ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole)) {
            return originalPrice.multiply(scenicProperties.getVipDiscount());
        }
        return originalPrice;
    }

    private ProjectVO toProjectVO(ScenicProject project) {
        return ProjectVO.builder()
                .id(project.getId())
                .name(project.getName())
                .price(project.getPrice())
                .stock(project.getStock())
                .description(project.getDescription())
                .imageUrl(project.getImageUrl())
                .status(project.getStatus())
                .deleted(project.getStatus() != null && project.getStatus() == 0)
                .createTime(project.getCreateTime())
                .updateTime(project.getUpdateTime())
                .build();
    }
}
