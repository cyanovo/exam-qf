package com.cyan.exam.system.controller;

import java.util.List;

import com.cyan.exam.common.utils.StringUtils;
import com.cyan.exam.system.domain.dto.CyanStudentImportDTO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.core.controller.BaseController;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.common.enums.BusinessType;
import com.cyan.exam.system.domain.CyanStudent;
import com.cyan.exam.system.service.ICyanStudentService;
import com.cyan.exam.common.utils.poi.ExcelUtil;
import com.cyan.exam.common.core.page.TableDataInfo;

/**
 * 学生扩展信息Controller
 * 
 * @author cyan
 * @date 2026-06-01
 */
@RestController
@RequestMapping("/system/student")
public class CyanStudentController extends BaseController
{
    @Autowired
    private ICyanStudentService cyanStudentService;

    /**
     * 查询学生扩展信息列表
     */
    @PreAuthorize("@ss.hasPermi('system:student:list')")
    @GetMapping("/list")
    public TableDataInfo list(CyanStudent cyanStudent)
    {
        startPage();
        List<CyanStudent> list = cyanStudentService.selectCyanStudentList(cyanStudent);
        return getDataTable(list);
    }

    /**
     * 导出学生扩展信息列表
     */
    @PreAuthorize("@ss.hasPermi('system:student:export')")
    @Log(title = "学生扩展信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, CyanStudent cyanStudent)
    {
        List<CyanStudent> list = cyanStudentService.selectCyanStudentList(cyanStudent);
        ExcelUtil<CyanStudent> util = new ExcelUtil<CyanStudent>(CyanStudent.class);
        util.exportExcel(response, list, "学生扩展信息数据");
    }

    /**
     * 获取学生扩展信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:student:query')")
    @GetMapping(value = "/{studentId}")
    public AjaxResult getInfo(@PathVariable("studentId") Long studentId)
    {
        return success(cyanStudentService.selectCyanStudentByStudentId(studentId));
    }

    /**
     * 新增学生扩展信息
     */
    @PreAuthorize("@ss.hasPermi('system:student:add')")
    @Log(title = "学生扩展信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CyanStudent cyanStudent)
    {
        return toAjax(cyanStudentService.insertCyanStudent(cyanStudent));
    }

    /**
     * 修改学生扩展信息
     */
    @PreAuthorize("@ss.hasPermi('system:student:edit')")
    @Log(title = "学生扩展信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CyanStudent cyanStudent)
    {
        return toAjax(cyanStudentService.updateCyanStudent(cyanStudent));
    }

    /**
     * 删除学生扩展信息
     */
    @PreAuthorize("@ss.hasPermi('system:student:remove')")
    @Log(title = "学生扩展信息", businessType = BusinessType.DELETE)
	@DeleteMapping("/{studentIds}")
    public AjaxResult remove(@PathVariable Long[] studentIds)
    {
        return toAjax(cyanStudentService.deleteCyanStudentByStudentIds(studentIds));
    }

    /**
     * 一键批量创建学生账号
     */
    @PreAuthorize("@ss.hasPermi('system:student:import')")
    @Log(title = "学生扩展信息", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public AjaxResult importStudents(@RequestBody CyanStudentImportDTO dto) {
        String msg = cyanStudentService.importStudents(dto);
        return StringUtils.isEmpty(msg) ? success("创建成功") : error(msg);
    }
}
