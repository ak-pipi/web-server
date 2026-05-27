package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niuma.admin.dto.RoomCreateDTO;
import com.niuma.admin.dto.RoomForceDissolveDTO;
import com.niuma.admin.dto.RoomQueryDTO;
import com.niuma.admin.entity.*;
import com.niuma.admin.mapper.*;
import com.niuma.admin.service.IRoomManageService;
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
import java.util.UUID;

/**
 * 房间管理服务实现
 */
@Service
@Slf4j
public class RoomManageServiceImpl implements IRoomManageService {

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private GameRuleVersionMapper ruleVersionMapper;

    @Autowired
    private AdminAuditLogMapper auditLogMapper;

    // ==================== 创建房间 ====================

    @Override
    public AjaxResult createRoom(RoomCreateDTO dto, String operator) {
        // 验证游戏存在且上架
        Game game = gameMapper.selectById(dto.getGameId());
        if (game == null) {
            throw new NotFoundException("游戏不存在");
        }
        if (game.getStatus() != 1) {
            throw new BadRequestException("游戏未上架，无法创建房间");
        }

        // 确定规则版本
        Long ruleVersionId = dto.getRuleVersionId();
        if (ruleVersionId == null) {
            // 使用当前生效版本
            LambdaQueryWrapper<GameRuleVersion> activeWrapper = Wrappers.lambdaQuery(GameRuleVersion.class);
            activeWrapper.eq(GameRuleVersion::getGameId, dto.getGameId())
                        .eq(GameRuleVersion::getStatus, GameRuleVersion.STATUS_ACTIVE);
            GameRuleVersion active = ruleVersionMapper.selectOne(activeWrapper);
            if (active == null) {
                throw new BadRequestException("该游戏没有生效的规则版本，请先发布一个版本");
            }
            ruleVersionId = active.getId();
        } else {
            GameRuleVersion version = ruleVersionMapper.selectById(ruleVersionId);
            if (version == null || !version.getGameId().equals(dto.getGameId())) {
                throw new BadRequestException("规则版本不存在或不属于该游戏");
            }
        }

        // 生成房间号
        String roomId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String roomNo = generateRoomNo(dto.getGameId());

        // 创建房间记录
        Room room = new Room();
        room.setId(roomId);
        room.setRoomNo(roomNo);
        room.setOwnerId(operator);  // 后台创建时操作人为房主(或后续指定)
        room.setDistrictId(dto.getDistrictId());
        room.setGameId(dto.getGameId());
        room.setRuleVersionId(ruleVersionId);
        room.setRoomType(dto.getRoomType() != null ? dto.getRoomType() : Room.TYPE_FRIEND);
        room.setStatus(Room.STATUS_WAITING);
        room.setTotalRound(dto.getTotalRound());
        room.setCurrentRound(0);
        room.setConfigSnapshot(buildConfigSnapshot(ruleVersionId, dto.getCustomConfig()));
        room.setDisputedFlag(0);
        room.setCreateTime(LocalDateTime.now());
        roomMapper.insert(room);

        log.info("[房间管理] 创建房间: roomId={}, roomNo={}, gameId={}, type={}",
                roomId, roomNo, dto.getGameId(), room.getRoomType());

        // TODO: 通过MQ下发创建房间命令到C++服务器

        writeAuditLog(operator, "ROOM_CREATE", "room", roomId,
                "", "create", "后台创建房间: " + roomNo);

        AjaxResult result = AjaxResult.successEx();
        result.put("roomId", roomId);
        result.put("roomNo", roomNo);
        result.put("status", room.getStatus());
        return result;
    }

    // ==================== 查询 ====================

    @Override
    public AjaxResult getRoomDetail(String roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new NotFoundException("房间不存在");
        }

        AjaxResult result = AjaxResult.successEx();
        result.put("room", room);

        // 关联游戏名称
        Game game = gameMapper.selectById(room.getGameId());
        if (game != null) {
            result.put("gameName", game.getName());
        }

        // 关联规则版本
        if (room.getRuleVersionId() != null) {
            GameRuleVersion version = ruleVersionMapper.selectById(room.getRuleVersionId());
            result.put("ruleVersion", version);
        }

        // TODO: 查询该房间的牌局记录(game_round)
        // TODO: 查询该房间的玩家列表

        return result;
    }

    @Override
    public PageResult<Room> pageList(RoomQueryDTO dto) {
        LambdaQueryWrapper<Room> wrapper = buildQueryWrapper(dto);
        wrapper.orderByDesc(Room::getCreateTime);
        Page<Room> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<Room> result = roomMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), (int) result.getCurrent(), (int) result.getTotal());
    }

    // ==================== 强制解散 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult forceDissolve(String roomId, String reason, String operator) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new NotFoundException("房间不存在");
        }
        Integer oldStatus = room.getStatus();

        // 只能解散非已结束/已解散的房间
        if (oldStatus == Room.STATUS_FINISHED || oldStatus == Room.STATUS_DISSOLVED) {
            throw new BadRequestException("房间已结束或已解散，无需再次操作");
        }

        room.setStatus(Room.STATUS_DISSOLVED);
        room.setFinishedAt(LocalDateTime.now());
        roomMapper.updateById(room);

        log.info("[房间管理] 强制解散: roomId={}, roomNo={}, fromStatus={}, operator={}",
                roomId, room.getRoomNo(), oldStatus, operator);

        // TODO: 通过MQ下发强制解散命令到C++服务器

        writeAuditLog(operator, "ROOM_FORCE_DISSOLVE", "room",
                roomId, String.valueOf(oldStatus), "dissolved",
                "强制解散 | 原因: " + reason);

        AjaxResult result = AjaxResult.successEx();
        result.put("msg", "房间已强制解散");
        result.put("previousStatus", oldStatus);
        return result;
    }

    // ==================== 争议处理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult markDisputed(String roomId, String reason, String operator) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new NotFoundException("房间不存在");
        }

        room.setDisputedFlag(1);
        room.setDisputeReason(reason);
        room.setDisputant(operator);
        room.setDisputedAt(LocalDateTime.now());
        roomMapper.updateById(room);

        log.info("[房间管理] 标记争议: roomId={}, roomNo={}, operator={}, reason={}",
                roomId, room.getRoomNo(), operator, reason);

        writeAuditLog(operator, "ROOM_DISPUTE_MARK", "room",
                roomId, "normal", "disputed", "标记争议 | 原因: " + reason);

        AjaxResult result = AjaxResult.successEx();
        result.put("msg", "房间已标记为争议");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult resolveDispute(String roomId, String operator) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new NotFoundException("房间不存在");
        }
        if (room.getDisputedFlag() == null || room.getDisputedFlag() != 1) {
            throw new BadRequestException("该房间未被标记为争议");
        }

        room.setDisputedFlag(0);
        room.setDisputeReason(null);
        roomMapper.updateById(room);

        log.info("[房间管理] 解决争议: roomId={}, roomNo={}, operator={}", roomId, room.getRoomNo(), operator);

        writeAuditLog(operator, "ROOM_DISPUTE_RESOLVE", "room",
                roomId, "disputed", "resolved", "解决争议标记");

        AjaxResult result = AjaxResult.successEx();
        result.put("msg", "争议标记已解除");
        return result;
    }

    // ==================== MQ 状态同步接收 ====================

    @Override
    public void onStatusSync(String roomId, Integer status, String extraData) {
        try {
            Room room = roomMapper.selectById(roomId);
            if (room == null) {
                log.warn("[房间MQ] 收到未知房间状态同步: roomId={}, status={}", roomId, status);
                return;
            }

            Integer oldStatus = room.getStatus();
            room.setStatus(status);

            // 特殊状态更新结束时间
            if (status == Room.STATUS_FINISHED || status == Room.STATUS_DISSOLVED) {
                room.setFinishedAt(LocalDateTime.now());
            }

            // 解析附加数据（如当前局数等）
            if (extraData != null && !extraData.isEmpty()) {
                try {
                    Map<String, Object> data = com.alibaba.fastjson2.JSON.parseObject(extraData, Map.class);
                    if (data.containsKey("currentRound")) {
                        Object roundVal = data.get("currentRound");
                        if (roundVal instanceof Number) {
                            room.setCurrentRound(((Number) roundVal).intValue());
                        }
                    }
                } catch (Exception e) {
                    log.debug("[房间MQ] 解析附加数据失败: {}", e.getMessage());
                }
            }

            roomMapper.updateById(room);

            log.info("[房间MQ] 状态同步: roomId={}, {} -> {}, extra={}",
                    roomId, oldStatus, status, extraData);
        } catch (Exception e) {
            log.error("[房间MQ] 状态同步处理异常: roomId={}, status={}", roomId, status, e);
        }
    }

    // ==================== 统计 ====================

    @Override
    public AjaxResult roomStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("waiting", countByStatus(Room.STATUS_WAITING));
        stats.put("ready", countByStatus(Room.STATUS_READY));
        stats.put("playing", countByStatus(Room.STATUS_PLAYING));
        stats.put("settling", countByStatus(Room.STATUS_SETTLING));
        stats.put("finished", countByStatus(Room.STATUS_FINISHED));
        stats.put("dissolved", countByStatus(Room.STATUS_DISSOLVED));
        stats.put("abnormal", countByStatus(Room.STATUS_ABNORMAL));

        LambdaQueryWrapper<Room> disputeWrapper = Wrappers.lambdaQuery(Room.class);
        disputeWrapper.eq(Room::getDisputedFlag, 1);
        long disputedCount = roomMapper.selectCount(disputeWrapper);
        stats.put("disputed", disputedCount);

        AjaxResult result = AjaxResult.successEx();
        result.putAll(stats);
        return result;
    }

    // ==================== 内部方法 ====================

    private LambdaQueryWrapper<Room> buildQueryWrapper(RoomQueryDTO dto) {
        LambdaQueryWrapper<Room> wrapper = Wrappers.lambdaQuery(Room.class);
        if (dto.getRoomNo() != null && !dto.getRoomNo().isEmpty()) {
            wrapper.like(Room::getRoomNo, dto.getRoomNo());
        }
        if (dto.getGameId() != null) {
            wrapper.eq(Room::getGameId, dto.getGameId());
        }
        if (dto.getOwnerId() != null && !dto.getOwnerId().isEmpty()) {
            wrapper.eq(Room::getOwnerId, dto.getOwnerId());
        }
        if (dto.getRoomType() != null && !dto.getRoomType().isEmpty()) {
            wrapper.eq(Room::getRoomType, dto.getRoomType());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Room::getStatus, dto.getStatus());
        }
        if (dto.getDisputedOnly() != null && dto.getDisputedOnly() == 1) {
            wrapper.eq(Room::getDisputedFlag, 1);
        }
        if (dto.getStartTime() != null && !dto.getStartTime().isEmpty()) {
            wrapper.ge(Room::getCreateTime, dto.getStartTime());
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            wrapper.le(Room::getCreateTime, dto.getEndTime());
        }
        return wrapper;
    }

    private String generateRoomNo(Long gameId) {
        // 格式: 游戏ID后2位 + 时间戳后6位 + 随机3位
        String gameSuffix = String.format("%02d", gameId % 100);
        String timeSuffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String randomSuffix = String.format("%03d", (int)(Math.random() * 1000));
        return gameSuffix + timeSuffix + randomSuffix;
    }

    private String buildConfigSnapshot(Long ruleVersionId, String customConfig) {
        if (customConfig != null && !customConfig.isEmpty()) {
            return customConfig;
        }
        if (ruleVersionId != null) {
            GameRuleVersion version = ruleVersionMapper.selectById(ruleVersionId);
            if (version != null) {
                return version.getConfigJson();
            }
        }
        return "{}";
    }

    private long countByStatus(Integer status) {
        LambdaQueryWrapper<Room> wrapper = Wrappers.lambdaQuery(Room.class);
        wrapper.eq(Room::getStatus, status);
        return roomMapper.selectCount(wrapper);
    }

    private void writeAuditLog(String adminId, String action, String targetType,
                               String targetId, String beforeValue,
                               String afterValue, String remark) {
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
