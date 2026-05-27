package com.niuma.admin.service;

import com.niuma.admin.dto.GrayPublishDTO;
import com.niuma.admin.dto.RuleVersionApproveDTO;
import com.niuma.admin.dto.RuleVersionCreateDTO;
import com.niuma.admin.entity.GameRuleVersion;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageResult;

/**
 * 规则版本管理服务接口（草稿/审批/发布/灰度/回滚）
 */
public interface IGameRuleVersionService {

    /**
     * 创建规则版本草稿
     */
    AjaxResult createDraft(RuleVersionCreateDTO dto, String operator);

    /**
     * 提交审批
     */
    AjaxResult submitForApproval(Long id, String operator);

    /**
     * 审批操作（通过/驳回）
     */
    AjaxResult approve(RuleVersionApproveDTO dto, String operator);

    /**
     * 生效发布（全量）
     */
    AjaxResult publishAll(Long id, String operator);

    /**
     * 灰度发布
     */
    AjaxResult grayPublish(GrayPublishDTO dto, String operator);

    /**
     * 版本回滚
     */
    AjaxResult rollback(Long id, String operator);

    /**
     * 查询版本详情
     */
    AjaxResult getDetail(Long id);

    /**
     * 分页查询版本列表
     */
    PageResult<GameRuleVersion> pageList(Long gameId, Integer status, int pageNum, int pageSize);

    /**
     * 查询某游戏的当前生效版本
     */
    AjaxResult getCurrentActiveVersion(Long gameId);

    /**
     * 查询某游戏的灰度版本列表
     */
    AjaxResult listGrayVersions(Long gameId);
}
