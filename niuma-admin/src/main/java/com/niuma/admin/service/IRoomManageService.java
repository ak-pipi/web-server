package com.niuma.admin.service;

import com.niuma.admin.dto.RoomCreateDTO;
import com.niuma.admin.dto.RoomForceDissolveDTO;
import com.niuma.admin.dto.RoomQueryDTO;
import com.niuma.admin.entity.Room;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageResult;

/**
 * 房间管理服务接口（后台管理 + 状态机 + MQ对接预留）
 */
public interface IRoomManageService {

    /**
     * 创建房间（通过MQ下发到C++服务器）
     *
     * @param dto      房间配置
     * @param operator 操作人
     * @return 房间信息
     */
    AjaxResult createRoom(RoomCreateDTO dto, String operator);

    /**
     * 查询房间详情（含玩家列表 + 配置快照 + 结算信息）
     */
    AjaxResult getRoomDetail(String roomId);

    /**
     * 分页查询房间列表（支持多维度筛选）
     */
    PageResult<Room> pageList(RoomQueryDTO dto);

    /**
     * 强制解散异常房间
     */
    AjaxResult forceDissolve(String roomId, String reason, String operator);

    /**
     * 标记争议房间
     */
    AjaxResult markDisputed(String roomId, String reason, String operator);

    /**
     * 解决争议标记
     */
    AjaxResult resolveDispute(String roomId, String operator);

    /**
     * 接收MQ消息 - 房间状态同步
     *
     * @param roomId   房间ID
     * @param status   新状态
     * @param extraData 附加数据(JSON)
     */
    void onStatusSync(String roomId, Integer status, String extraData);

    /**
     * 统计各状态的房间数量（用于后台仪表盘）
     */
    AjaxResult roomStats();
}
