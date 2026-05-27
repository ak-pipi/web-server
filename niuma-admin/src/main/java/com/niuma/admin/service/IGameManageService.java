package com.niuma.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuma.admin.dto.GameManageDTO;
import com.niuma.admin.entity.Game;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageResult;

/**
 * 游戏管理服务接口（后台CRUD + 状态管理）
 */
public interface IGameManageService extends IService<Game> {

    /**
     * 创建游戏
     */
    AjaxResult create(GameManageDTO dto);

    /**
     * 更新游戏信息
     */
    AjaxResult update(GameManageDTO dto);

    /**
     * 删除游戏（逻辑删除）
     */
    AjaxResult delete(Long id);

    /**
     * 查询游戏详情
     */
    AjaxResult getDetail(Long id);

    /**
     * 分页查询游戏列表
     */
    PageResult<Game> pageList(String keyword, String type, Integer status, int pageNum, int pageSize);

    /**
     * 上架游戏
     */
    AjaxResult online(Long id);

    /**
     * 下架游戏
     */
    AjaxResult offline(Long id);

    /**
     * 设置维护状态
     */
    AjaxResult setMaintenance(Long id, String reason);

    /**
     * 查询所有上架中的游戏列表
     */
    AjaxResult listOnlineGames();
}
