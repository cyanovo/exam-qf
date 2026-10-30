package com.cyan.exam.system.controller;

import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.enums.BusinessType;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.system.service.EquipmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/equipment")
public class EquipmentController {
    private final EquipmentService service;
    public EquipmentController(EquipmentService service) { this.service=service; }
    @GetMapping
    @PreAuthorize("@ss.hasPermi('system:equipment:list')")
    public AjaxResult list() { return AjaxResult.success(service.list()); }
    @PostMapping("/{kind}")
    @PreAuthorize("@ss.hasPermi('system:equipment:edit')")
    @Log(title="设备工位台账",businessType=BusinessType.UPDATE)
    public AjaxResult save(@PathVariable String kind,@RequestBody EquipmentService.Item item) {
        service.save(item,station(kind)); return AjaxResult.success();
    }
    @DeleteMapping("/{kind}/{id}")
    @PreAuthorize("@ss.hasPermi('system:equipment:edit')")
    @Log(title="设备工位台账",businessType=BusinessType.DELETE)
    public AjaxResult delete(@PathVariable String kind,@PathVariable Long id,@RequestParam int revision) {
        service.delete(id,revision,station(kind)); return AjaxResult.success();
    }
    private boolean station(String kind) {
        if(!"devices".equals(kind) && !"stations".equals(kind)) throw new com.cyan.exam.common.exception.ServiceException("台账类型无效");
        return "stations".equals(kind);
    }
}
