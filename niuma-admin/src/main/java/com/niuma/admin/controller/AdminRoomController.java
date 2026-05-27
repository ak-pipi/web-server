package com.niuma.admin.controller;

import com.niuma.admin.dto.RoomCreateDTO;
import com.niuma.admin.dto.RoomForceDissolveDTO;
import com.niuma.admin.dto.RoomQueryDTO;
import com.niuma.admin.entity.Room;
import com.niuma.admin.service.IRoomManageService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 后台房间管理控制器
 */
@RestController
@RequestMapping("/admin/room")
public class AdminRoomController {

    @Autowired
    private IRoomManageService roomManageService;

    /**
     * 创建房间（通过MQ下发）
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('niuma:room:manage')")
    public AjaxResult createRoom(@RequestBody RoomCreateDTO dto) {
        return roomManageService.createRoom(dto, SecurityUtils.getUsername());
    }

    /**
     * 查询房间详情（含玩家列表 + 配置快照 + 结算信息）
     */
    @GetMapping("/{roomId}")
    @PreAuthorize("@ss.hasPermi('niuma:room:query')")
    public AjaxResult getDetail(@PathVariable String roomId) {
        return roomManageService.getRoomDetail(roomId);
    }

    /**
     * 分页查询房间列表（支持多维度筛选）
     */
    @PostMapping("/page")
    @PreAuthorize("@ss.hasPermi('niuma:room:query')")
    public PageResult<Room> pageList(@RequestBody RoomQueryDTO dto) {
        return roomManageService.pageList(dto);
    }

    /**
     * 强制解散异常房间
     */
    @PostMapping("/force-dissolve")
    @PreAuthorize("@ss.hasPermi('niuma:room:dissolve')")
    public AjaxResult forceDissolve(@RequestBody RoomForceDissolveDTO dto) {
        if (!"dissolve".equals(dto.getAction())) {
            throw new IllegalArgumentException("操作类型不正确, 期望 dissolve");
        }
        return roomManageService.forceDissolve(dto.getRoomId(), dto.getReason(), SecurityUtils.getUsername());
    }

    /**
     * 标记争议房间
     */
    @PostMapping("/dispute-mark")
    @PreAuthorize("@ss.hasPermi('niuma:room:dispute')")
    public AjaxResult markDisputed(@RequestBody RoomForceDissolveDTO dto) {
        if (!"dispute".equals(dto.getAction())) {
            throw new IllegalArgumentException("操作类型不正确, 期望 dispute");
        }
        return roomManageService.markDisputed(dto.getRoomId(), dto.getReason(), SecurityUtils.getUsername());
    }

    /**
     * 解决争议标记
     */
    @PostMapping("/{roomId}/dispute-resolve")
    @PreAuthorize("@ss.hasPermi('niuma:room:dispute')")
    public AjaxResult resolveDispute(@PathVariable String roomId) {
        return roomManageService.resolveDispute(roomId, SecurityUtils.getUsername());
    }

    /**
     * 房间统计仪表盘数据
     */
    @GetMapping("/stats")
    @PreAuthorize("@ss.hasPermi('niuma:room:query')")
    public AjaxResult roomStats() {
        return roomManageService.roomStats();
    }
}
