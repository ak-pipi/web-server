package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niuma.admin.dto.GrayPublishDTO;
import com.niuma.admin.dto.RuleVersionApproveDTO;
import com.niuma.admin.dto.RuleVersionCreateDTO;
import com.niuma.admin.entity.*;
import com.niuma.admin.mapper.AdminAuditLogMapper;
import com.niuma.admin.mapper.GameRuleVersionMapper;
import com.niuma.admin.mapper.GameMapper;
import com.niuma.admin.service.IGameRuleVersionService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.exception.http.NotFoundException;
import com.niuma.common.page.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则版本管理服务实现
 */
@Service
@Slf4j
public class GameRuleVersionServiceImpl implements IGameRuleVersionService {

    @Autowired
    private GameRuleVersionMapper ruleVersionMapper;

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private AdminAuditLogMapper auditLogMapper;

    // ==================== 草稿 & 提交审批 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult createDraft(RuleVersionCreateDTO dto, String operator) {
        // 验证游戏存在
        Game game = gameMapper.selectById(dto.getGameId());
        if (game == null) {
            throw new NotFoundException("游戏不存在");
        }

        GameRuleVersion version = new GameRuleVersion();
        version.setGameId(dto.getGameId());
        version.setVersionNo(dto.getVersion());
        version.setTitle(dto.getTitle());
        version.setConfigJson(dto.getRuleConfig());
        version.setChangeNote(dto.getChangeNote());

        if (Boolean.TRUE.equals(dto.getAutoSubmit())) {
            version.setStatus(GameRuleVersion.STATUS_PENDING_REVIEW);  // 待审核
        } else {
            version.setStatus(GameRuleVersion.STATUS_DRAFT);           // 草稿
        }
        version.setCreator(operator);
        version.setCreateTime(LocalDateTime.now());
        ruleVersionMapper.insert(version);

        log.info("[规则版本] 创建版本草稿: gameId={}, versionNo={}, title={}, autoSubmit={}",
                dto.getGameId(), dto.getVersion(), dto.getTitle(), dto.getAutoSubmit());

        AjaxResult result = AjaxResult.successEx();
        result.put("versionId", version.getId());
        result.put("status", version.getStatus());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult submitForApproval(Long id, String operator) {
        GameRuleVersion version = ruleVersionMapper.selectById(id);
        if (version == null) {
            throw new NotFoundException("规则版本不存在");
        }
        if (version.getStatus() != GameRuleVersion.STATUS_DRAFT) {
            throw new BadRequestException("只有草稿状态的版本可以提交审批");
        }

        version.setStatus(GameRuleVersion.STATUS_PENDING_REVIEW);
        version.setUpdater(operator);
        version.setUpdateTime(LocalDateTime.now());
        ruleVersionMapper.updateById(version);

        writeAuditLog(operator, "RULE_VERSION_SUBMIT", "game_rule_version",
                String.valueOf(id), "", "提交审批", "规则版本提交审批");

        log.info("[规则版本] 提交审批: id={}, operator={}", id, operator);
        return AjaxResult.successEx();
    }

    // ==================== 审批 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult approve(RuleVersionApproveDTO dto, String operator) {
        GameRuleVersion version = ruleVersionMapper.selectById(dto.getId());
        if (version == null) {
            throw new NotFoundException("规则版本不存在");
        }
        if (version.getStatus() != GameRuleVersion.STATUS_PENDING_REVIEW) {
            throw new BadRequestException("当前版本不在待审核状态");
        }

        String action = dto.getAction().toLowerCase();
        if ("approve".equals(action)) {
            version.setStatus(GameRuleVersion.STATUS_APPROVED);
            log.info("[规则版本] 审批通过: id={}, operator={}", dto.getId(), operator);
        } else if ("reject".equals(action)) {
            version.setStatus(GameRuleVersion.STATUS_REJECTED);
            version.setRejectReason(dto.getComment());
            log.info("[规则版本] 审批驳回: id={}, operator={}, reason={}", dto.getId(), operator, dto.getComment());
        } else {
            throw new BadRequestException("不支持的审批操作: " + action);
        }

        version.setReviewer(operator);
        version.setReviewTime(LocalDateTime.now());
        version.setUpdateTime(LocalDateTime.now());
        ruleVersionMapper.updateById(version);

        writeAuditLog(operator, "RULE_VERSION_APPROVE", "game_rule_version",
                String.valueOf(dto.getId()), action,
                "审批" + ("approve".equals(action) ? "通过" : "驳回"), dto.getComment());

        AjaxResult result = AjaxResult.successEx();
        result.put("versionId", version.getId());
        result.put("newStatus", version.getStatus());
        return result;
    }

    // ==================== 发布（全量） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult publishAll(Long id, String operator) {
        GameRuleVersion version = ruleVersionMapper.selectById(id);
        if (version == null) {
            throw new NotFoundException("规则版本不存在");
        }
        if (version.getStatus() != GameRuleVersion.STATUS_APPROVED) {
            throw new BadRequestException("只有审批通过的版本可以发布");
        }

        // 将同游戏之前生效的版本置为历史
        LambdaQueryWrapper<GameRuleVersion> activeWrapper = Wrappers.lambdaQuery(GameRuleVersion.class);
        activeWrapper.eq(GameRuleVersion::getGameId, version.getGameId())
                     .eq(GameRuleVersion::getStatus, GameRuleVersion.STATUS_ACTIVE);
        List<GameRuleVersion> oldActiveVersions = ruleVersionMapper.selectList(activeWrapper);
        for (GameRuleVersion old : oldActiveVersions) {
            old.setStatus(GameRuleVersion.STATUS_HISTORY);
            old.setUpdateTime(LocalDateTime.now());
            ruleVersionMapper.updateById(old);
        }

        // 当前版本设为生效
        version.setStatus(GameRuleVersion.STATUS_ACTIVE);
        version.setPublishTime(LocalDateTime.now());
        version.setPublisher(operator);
        version.setUpdateTime(LocalDateTime.now());
        ruleVersionMapper.updateById(version);

        // TODO: 通过MQ通知C++服务器加载新规则

        writeAuditLog(operator, "RULE_VERSION_PUBLISH", "game_rule_version",
                String.valueOf(id), version.getVersionNo(), "全量发布", "发布版本: " + version.getTitle());

        log.info("[规则版本] 全量发布: gameId={}, versionNo={}, title={}",
                version.getGameId(), version.getVersionNo(), version.getTitle());

        AjaxResult result = AjaxResult.successEx();
        result.put("msg", "版本已全量发布生效");
        return result;
    }

    // ==================== 灰度发布 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult grayPublish(GrayPublishDTO dto, String operator) {
        GameRuleVersion version = ruleVersionMapper.selectById(dto.getId());
        if (version == null) {
            throw new NotFoundException("规则版本不存在");
        }
        if (version.getStatus() != GameRuleVersion.STATUS_APPROVED) {
            throw new BadRequestException("只有审批通过的版本可以进行灰度发布");
        }

        String grayType = dto.getGrayType();
        switch (grayType.toLowerCase()) {
            case "percent":
                if (dto.getPercent() == null || dto.getPercent() <= 0 || dto.getPercent() > 100) {
                    throw new BadRequestException("灰度百分比需在 1~100 之间");
                }
                version.setGrayType("percent");
                version.setGrayValue(String.valueOf(dto.getPercent()));
                break;
            case "channel":
                if (dto.getChannels() == null || dto.getChannels().isEmpty()) {
                    throw new BadRequestException("灰度渠道不能为空");
                }
                version.setGrayType("channel");
                version.setGrayValue(String.join(",", dto.getChannels()));
                break;
            case "region":
                if (dto.getRegions() == null || dto.getRegions().isEmpty()) {
                    throw new BadRequestException("灰度地区不能为空");
                }
                version.setGrayType("region");
                version.setGrayValue(String.join(",", dto.getRegions()));
                break;
            default:
                throw new BadRequestException("不支持的灰度类型: " + grayType);
        }

        version.setStatus(GameRuleVersion.STATUS_GRAY);
        version.setGrayStartTime(LocalDateTime.now());
        version.setPublisher(operator);
        version.setUpdateTime(LocalDateTime.now());
        ruleVersionMapper.updateById(version);

        writeAuditLog(operator, "RULE_VERSION_GRAY", "game_rule_version",
                String.valueOf(dto.getId()), grayType, "灰度发布", "类型=" + grayType);

        log.info("[规则版本] 灰度发布: id={}, grayType={}, value={}", dto.getId(), grayType, version.getGrayValue());

        AjaxResult result = AjaxResult.successEx();
        result.put("msg", "灰度发布已启动");
        result.put("grayType", grayType);
        return result;
    }

    // ==================== 回滚 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult rollback(Long id, String operator) {
        GameRuleVersion version = ruleVersionMapper.selectById(id);
        if (version == null) {
            throw new NotFoundException("规则版本不存在");
        }
        if (version.getStatus() != GameRuleVersion.STATUS_ACTIVE
                && version.getStatus() != GameRuleVersion.STATUS_GRAY) {
            throw new BadRequestException("只能回滚生效中或灰度中的版本");
        }

        Integer oldStatus = version.getStatus();

        // 查找上一个已发布的版本
        LambdaQueryWrapper<GameRuleVersion> historyWrapper = Wrappers.lambdaQuery(GameRuleVersion.class);
        historyWrapper.eq(GameRuleVersion::getGameId, version.getGameId())
                      .eq(GameRuleVersion::getStatus, GameRuleVersion.STATUS_HISTORY)
                      .orderByDesc(GameRuleVersion::getPublishTime)
                      .last("LIMIT 1");
        GameRuleVersion lastHistory = ruleVersionMapper.selectOne(historyWrapper);

        // 当前版本置为历史
        version.setStatus(GameRuleVersion.STATUS_HISTORY);
        version.setUpdateTime(LocalDateTime.now());
        ruleVersionMapper.updateById(version);

        // 如果有上一个历史版本，恢复为生效
        if (lastHistory != null) {
            lastHistory.setStatus(GameRuleVersion.STATUS_ACTIVE);
            lastHistory.setUpdateTime(LocalDateTime.now());
            ruleVersionMapper.updateById(lastHistory);
        }

        // TODO: 通过MQ通知C++服务器回滚到指定规则版本

        String targetVersion = lastHistory != null ? lastHistory.getVersionNo() : "(无)";
        writeAuditLog(operator, "RULE_VERSION_ROLLBACK", "game_rule_version",
                String.valueOf(id), version.getVersionNo(),
                "版本回滚", "回滚到: " + targetVersion);

        log.info("[规则版本] 回滚: id={}, fromStatus={}, rollbackTo={}",
                id, oldStatus, targetVersion);

        AjaxResult result = AjaxResult.successEx();
        result.put("msg", "版本已回滚");
        result.put("rollbackTo", targetVersion);
        return result;
    }

    // ==================== 查询 ====================

    @Override
    public AjaxResult getDetail(Long id) {
        GameRuleVersion version = ruleVersionMapper.selectById(id);
        if (version == null) {
            throw new NotFoundException("规则版本不存在");
        }
        return AjaxResult.successEx(version);
    }

    @Override
    public PageResult<GameRuleVersion> pageList(Long gameId, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<GameRuleVersion> wrapper = Wrappers.lambdaQuery(GameRuleVersion.class);
        if (gameId != null) {
            wrapper.eq(GameRuleVersion::getGameId, gameId);
        }
        if (status != null) {
            wrapper.eq(GameRuleVersion::getStatus, status);
        }
        wrapper.orderByDesc(GameRuleVersion::getId);
        Page<GameRuleVersion> page = new Page<>(pageNum, pageSize);
        Page<GameRuleVersion> result = ruleVersionMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), (int) result.getCurrent(), (int) result.getTotal());
    }

    @Override
    public AjaxResult getCurrentActiveVersion(Long gameId) {
        LambdaQueryWrapper<GameRuleVersion> wrapper = Wrappers.lambdaQuery(GameRuleVersion.class);
        wrapper.eq(GameRuleVersion::getGameId, gameId)
               .eq(GameRuleVersion::getStatus, GameRuleVersion.STATUS_ACTIVE);
        GameRuleVersion version = ruleVersionMapper.selectOne(wrapper);
        AjaxResult result = AjaxResult.successEx();
        result.put("data", version);
        return result;
    }

    @Override
    public AjaxResult listGrayVersions(Long gameId) {
        LambdaQueryWrapper<GameRuleVersion> wrapper = Wrappers.lambdaQuery(GameRuleVersion.class);
        wrapper.eq(GameRuleVersion::getGameId, gameId)
               .eq(GameRuleVersion::getStatus, GameRuleVersion.STATUS_GRAY);
        List<GameRuleVersion> versions = ruleVersionMapper.selectList(wrapper);
        AjaxResult result = AjaxResult.successEx();
        result.put("list", versions);
        return result;
    }

    // ==================== 内部方法 ====================

    private void writeAuditLog(String adminId, String action, String targetType,
                               String targetId, String beforeValue, String afterValue, String remark) {
        AdminAuditLog auditLog = new AdminAuditLog();
        auditLog.setAdminId(adminId);
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setBeforeValue(beforeValue);
        auditLog.setAfterValue(afterValue);
        auditLog.setRemark(remark);
        auditLog.setStatus(1);
        auditLog.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(auditLog);
    }
}
