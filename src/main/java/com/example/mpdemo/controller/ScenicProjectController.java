package com.example.mpdemo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.mpdemo.dto.CreateProjectDTO;
import com.example.mpdemo.dto.UpdateProjectDTO;
import com.example.mpdemo.entity.Result;
import com.example.mpdemo.entity.ScenicProject;
import com.example.mpdemo.filter.JwtDetails;
import com.example.mpdemo.service.OperationLogService;
import com.example.mpdemo.service.ScenicProjectService;
import com.example.mpdemo.vo.ProjectVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/project")
@Tag(name = "景点项目管理", description = "景点增删改查、上下架接口")
public class ScenicProjectController extends BaseController {

    @Autowired
    private ScenicProjectService scenicProjectService;

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping("/list")
    @Cacheable(value = "projects", key = "'list:' + #current + ':' + #size + ':' + (#name ?: '')")
    @Operation(summary = "获取可用项目列表（公开）")
    public Result listProjects(@RequestParam(defaultValue = "1") Integer current,
                               @RequestParam(defaultValue = "10") Integer size,
                               @RequestParam(required = false) String name) {
        IPage<ProjectVO> result = scenicProjectService.getAvailableProjectsVO(current, size, name);
        return Result.success("获取成功", result);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "获取所有项目（管理员）")
    public Result getAllProjects(@RequestParam(defaultValue = "1") Integer current,
                                 @RequestParam(defaultValue = "10") Integer size,
                                 @RequestParam(required = false) String name) {
        IPage<ProjectVO> result = scenicProjectService.getAllProjectsVO(current, size, name);
        return Result.success("获取成功", result);
    }

    @GetMapping("/{id}")
    @Cacheable(value = "projects", key = "'detail:' + #id")
    @Operation(summary = "获取项目详情")
    public Result getProject(@PathVariable Integer id) {
        ProjectVO project = scenicProjectService.getProjectByIdVO(id);
        if (project == null) {
            return Result.error("项目不存在");
        }
        return Result.success("获取成功", project);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @CacheEvict(value = "projects", key = "'list:' + 1 + ':' + 10 + ':' + ''")
    @Operation(summary = "添加项目（管理员）")
    public Result addProject(@Valid @RequestBody CreateProjectDTO dto, HttpServletRequest request) {
        ScenicProject project = new ScenicProject();
        project.setName(dto.getName());
        project.setPrice(dto.getPrice());
        project.setStock(dto.getStock());
        project.setDescription(dto.getDescription());
        project.setImageUrl(dto.getImageUrl());
        String message = scenicProjectService.addProject(project);
        JwtDetails details = getJwtDetails();
        logOperation(details, operationLogService, request, "项目管理", "创建",
                "创建项目: " + dto.getName(), "POST /v1/project");
        return Result.success(message, null);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @CacheEvict(value = "projects", allEntries = true)
    @Operation(summary = "更新项目（管理员）")
    public Result updateProject(@Valid @RequestBody UpdateProjectDTO dto, HttpServletRequest request) {
        ScenicProject project = new ScenicProject();
        project.setId(dto.getId());
        project.setName(dto.getName());
        project.setPrice(dto.getPrice());
        project.setStock(dto.getStock());
        project.setDescription(dto.getDescription());
        project.setImageUrl(dto.getImageUrl());
        String message = scenicProjectService.updateProject(project);
        JwtDetails details = getJwtDetails();
        logOperation(details, operationLogService, request, "项目管理", "更新",
                "更新项目ID: " + dto.getId(), "PUT /v1/project");
        return Result.success(message, null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @CacheEvict(value = "projects", allEntries = true)
    @Operation(summary = "下架项目（管理员）")
    public Result softDeleteProject(@PathVariable Integer id, HttpServletRequest request) {
        String message = scenicProjectService.softDeleteProject(id);
        JwtDetails details = getJwtDetails();
        logOperation(details, operationLogService, request, "项目管理", "下架",
                "下架项目ID: " + id, "DELETE /v1/project/" + id);
        return Result.success(message, null);
    }
}
