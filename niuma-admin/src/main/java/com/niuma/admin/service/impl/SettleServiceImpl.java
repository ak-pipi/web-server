package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.niuma.admin.dto.SettleMessageDTO;
import com.niuma.admin.dto.SettlePlayerResultDTO;
import com.niuma.admin.dto.SettleQueryDTO;
import com.niuma.admin.entity.*;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.*;
import com.niuma.admin.service.ISettleService;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.page.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏结算服务实现
 * <p>
 * 核心流程：MQ消息接收 → 幂等去重 → 数据校验 → 事务写入
 * （牌局记录 + 回放数据 + 钱包积分变动 + 房费记录 + 房间状态推进）
 */
@Service
@Slf4j
public class SettleServiceImpl extends ServiceImpl<GameRoundMapper, GameRound> implements ISettleService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_REPLAY_SIZE = 5 * 1024 * 1024; // 5MB

    @Autowired
    private GameRoundMapper gameRoundMapper;

    @Autowired
    private GameReplayMapper gameReplayMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private IWalletService walletService;

    @Autowired
    private RoomFeeLedgerMapper roomFeeLedgerMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== MQ 结算处理入口 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult processSettlement(SettleMessageDTO dto) {
        String messageId = dto.getMessageId();
        String roomId = dto.getRoomId();
        Integer roundNo = dto.getRoundNo();

        log.info("[结算] 开始处理结算消息: messageId={}, roomId={}, roundNo={}",
                messageId, roomId, roundNo);

        // 1. 消息去重（幂等校验）
        if (isDuplicate(messageId)) {
            log.warn("[结算] 重复消息, 直接ACK: messageId={}", messageId);
            return AjaxResult.success("重复消息, 已忽略", null);
        }

        // 2. 校验房间是否存在
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            log.error("[结算] 房间不存在: roomId={}", roomId);
            throw new BadRequestException("房间不存在: " + roomId);
        }

        // 3. 解析结算时间
        LocalDateTime settledAt = parseSettledAt(dto.getSettledAt());

        // 4. 序列化结果JSON
        String resultJson;
        try {
            resultJson = objectMapper.writeValueAsString(dto.getResults());
        } catch (JsonProcessingException e) {
            log.error("[结算] 结果JSON序列化失败: messageId={}", messageId, e);
            throw new BadRequestException("结算结果序列化失败");
        }

        // 5. 写入牌局记录
        Long roundId = saveGameRound(dto, roomId, settledAt, resultJson);
        log.info("[结算] 牌局记录已入库: roundId={}, roomId={}, roundNo={}", roundId, roomId, roundNo);

        // 6. 写入回放数据（如有且大小合理）
        if (dto.getReplayDataBase64() != null && !dto.getReplayDataBase64().isEmpty()) {
            saveReplayData(roundId, dto);
        }

        // 7. 循环处理每个玩家的钱包变动
        Map<Long, String> playerErrors = processPlayerSettlements(dto, roundId);

        // 8. 更新房间状态（推进局数 / 标记结束）
        updateRoomStatus(room, roundNo, dto);

        // 9. 构建返回结果
        AjaxResult result = buildSuccessResult(roundId, dto, playerErrors);

        log.info("[结算] 处理完成: messageId={}, roundId={}, 玩家数={}",
                messageId, roundId, dto.getResults().size());
        return result;
    }

    // ==================== 手动补单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult reprocessSettlement(SettleMessageDTO dto, String operator) {
        log.info("[结算-手动补单] 操作人={}, messageId={}, roomId={}, roundNo={}",
                operator, dto.getMessageId(), dto.getRoomId(), dto.getRoundNo());

        // 手动补单允许强制覆盖（先删除旧记录再重新入库）
        String messageId = dto.getMessageId();
        LambdaQueryWrapper<GameRound> wrapper = Wrappers.lambdaQuery(GameRound.class)
                .eq(GameRound::getMessageId, messageId);
        gameRoundMapper.delete(wrapper);

        log.info("[结算-手动补单] 已清除旧记录, 重新处理: messageId={}", messageId);

        AjaxResult result = processSettlement(dto);

        // 记录审计日志 TODO
        log.info("[结算-手动补单] 补单完成: operator={}, messageId={}", operator, messageId);

        return result;
    }

    // ==================== 查询接口 ====================

    @Override
    public PageResult<GameRound> querySettlements(SettleQueryDTO dto) {
        LambdaQueryWrapper<GameRound> wrapper = Wrappers.lambdaQuery(GameRound.class);

        if (dto.getRoomId() != null && !dto.getRoomId().isEmpty()) {
            wrapper.eq(GameRound::getRoomId, dto.getRoomId());
        }
        if (dto.getGameCode() != null && !dto.getGameCode().isEmpty()) {
            // 通过roomId关联room表查gameCode, 或在game_round加冗余字段
            // 此处简化: 先按房间ID筛选后内存过滤, 生产环境建议用子查询或视图
        }
        if (dto.getRoundNoMin() != null) {
            wrapper.ge(GameRound::getRoundNo, dto.getRoundNoMin());
        }
        if (dto.getRoundNoMax() != null) {
            wrapper.le(GameRound::getRoundNo, dto.getRoundNoMax());
        }
        if (dto.getStartTime() != null && !dto.getStartTime().isEmpty()) {
            wrapper.ge(GameRound::getSettledAt, dto.getStartTime());
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            wrapper.le(GameRound::getSettledAt, dto.getEndTime());
        }
        wrapper.orderByDesc(GameRound::getId);

        Page<GameRound> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<GameRound> result = gameRoundMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), (int) result.getCurrent(), (int) result.getTotal());
    }

    @Override
    public AjaxResult getSettlementDetail(Long roundId) {
        GameRound round = gameRoundMapper.selectById(roundId);
        if (round == null) {
            throw new BadRequestException("结算记录不存在: " + roundId);
        }

        AjaxResult result = AjaxResult.successEx();
        result.put("round", round);

        // 解析结算结果详情
        if (round.getResultJson() != null) {
            try {
                List<SettlePlayerResultDTO> results = objectMapper.readValue(
                        round.getResultJson(),
                        new TypeReference<List<SettlePlayerResultDTO>>() {}
                );
                result.put("playerResults", results);
            } catch (JsonProcessingException e) {
                log.warn("[结算] 解析结果JSON失败: roundId={}", roundId, e);
            }
        }

        // 关联回放数据
        LambdaQueryWrapper<GameReplay> replayWrapper = Wrappers.lambdaQuery(GameReplay.class)
                .eq(GameReplay::getRoundId, roundId);
        GameReplay replay = gameReplayMapper.selectOne(replayWrapper);
        if (replay != null) {
            result.put("hasReplay", true);
            result.put("replayHash", replay.getReplayHash());
        } else {
            result.put("hasReplay", false);
        }

        // 关联房间信息
        if (round.getRoomId() != null) {
            Room room = roomMapper.selectById(round.getRoomId());
            if (room != null) {
                result.put("roomInfo", room);
            }
        }

        return result;
    }

    @Override
    public AjaxResult getRoomSettlements(String roomId) {
        LambdaQueryWrapper<GameRound> wrapper = Wrappers.lambdaQuery(GameRound.class)
                .eq(GameRound::getRoomId, roomId)
                .orderByAsc(GameRound::getRoundNo);
        List<GameRound> rounds = gameRoundMapper.selectList(wrapper);

        AjaxResult result = AjaxResult.successEx();
        result.put("roomId", roomId);
        result.put("totalRounds", rounds.size());
        result.put("rounds", rounds);

        // 统计汇总
        return result;
    }

    @Override
    public AjaxResult getSettlementStats(Long gameId, String startDate, String endDate) {
        // TODO: 聚合统计查询, 可考虑使用原生SQL或新增统计方法
        AjaxResult result = AjaxResult.successEx();

        // 基础查询条件
        LambdaQueryWrapper<GameRound> wrapper = Wrappers.lambdaQuery(GameRound.class);
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(GameRound::getSettledAt, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(GameRound::getSettledAt, endDate);
        }

        long totalRounds = gameRoundMapper.selectCount(wrapper);

        result.put("totalRounds", totalRounds);
        result.put("gameId", gameId);
        result.put("startDate", startDate);
        result.put("endDate", endDate);

        return result;
    }

    // ==================== 内部核心方法 ====================

    /**
     * 幂等去重检查
     */
    private boolean isDuplicate(String messageId) {
        LambdaQueryWrapper<GameRound> wrapper = Wrappers.lambdaQuery(GameRound.class)
                .eq(GameRound::getMessageId, messageId);
        Long count = gameRoundMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 解析结算时间
     */
    private LocalDateTime parseSettledAt(String settledAtStr) {
        try {
            return LocalDateTime.parse(settledAtStr, DT_FMT);
        } catch (Exception e) {
            log.warn("[结算] 时间格式解析失败, 使用当前时间: {}", settledAtStr);
            return LocalDateTime.now();
        }
    }

    /**
     * 保存牌局记录
     */
    private Long saveGameRound(SettleMessageDTO dto, String roomId,
                                LocalDateTime settledAt, String resultJson) {
        GameRound round = new GameRound();
        round.setRoomId(roomId);
        round.setRoundNo(dto.getRoundNo());
        round.setResultJson(resultJson);
        round.setMessageId(dto.getMessageId());
        round.setSettledAt(settledAt);
        round.setCreateTime(LocalDateTime.now());
        gameRoundMapper.insert(round);
        return round.getId();
    }

    /**
     * 保存回放数据（大小超限时仅存哈希标记，数据异步传输）
     */
    private void saveReplayData(Long roundId, SettleMessageDTO dto) {
        byte[] replayBytes = java.util.Base64.getMimeDecoder().decode(dto.getReplayDataBase64());

        GameReplay replay = new GameReplay();
        replay.setRoundId(roundId);

        if (replayBytes.length > MAX_REPLAY_SIZE) {
            // 数据过大: 仅记录哈希, 实际数据需异步拉取
            log.warn("[结算] 回放数据过大({}字节), 仅存储哈希: roundId={}, hash={}",
                    replayBytes.length, roundId, dto.getReplayDataHash());
            replay.setReplayData(null); // LONGBLOB 存NULL
            replay.setReplayHash(dto.getReplayDataHash() + "[ASYNC]");
        } else {
            replay.setReplayData(replayBytes);
            replay.setReplayHash(dto.getReplayDataHash());
        }
        replay.setCreateTime(LocalDateTime.now());
        gameReplayMapper.insert(replay);
    }

    /**
     * 循环处理每个玩家的积分/房费变动
     *
     * @return 处理失败的玩家及原因Map (userId → errorMsg)
     */
    private Map<Long, String> processPlayerSettlements(SettleMessageDTO dto, Long roundId) {
        Map<Long, String> errors = new HashMap<>();
        String bizId = "SETTLE_" + dto.getMessageId();

        for (SettlePlayerResultDTO playerResult : dto.getResults()) {
            Long userId = playerResult.getUserId();
            Long score = playerResult.getScore();
            String walletType = playerResult.getWalletType() != null
                    ? playerResult.getWalletType()
                    : WalletType.GOLD.getCode(); // 默认金币钱包

            try {
                // 积分变动
                if (score != null && score != 0) {
                    String playerId = String.valueOf(userId);
                    if (score > 0) {
                        // 赢了: 增加积分
                        walletService.increase(playerId, walletType, score,
                                LedgerBizType.GAME_SETTLE.getCode(), bizId,
                                buildSettleRemark(dto, playerResult, "赢得"));
                    } else {
                        // 输了: 扣减积分（余额不足时捕获异常并记录）
                        try {
                            walletService.decrease(playerId, walletType, Math.abs(score),
                                    LedgerBizType.GAME_SETTLE.getCode(), bizId,
                                    buildSettleRemark(dto, playerResult, "输掉"));
                        } catch (BadRequestException e) {
                            log.error("[结算] 玩家余额不足扣减: userId={}, score={}, error={}",
                                    userId, score, e.getMessage());
                            errors.put(userId, "余额不足: " + e.getMessage());
                            // TODO: 触发人工补偿流程
                        }
                    }
                }

                // 房费扣除（如有）
                if (playerResult.getRoomFeeAmount() != null && playerResult.getRoomFeeAmount() > 0) {
                    recordRoomFee(dto, playerResult, roundId);
                }

            } catch (Exception e) {
                log.error("[结算] 玩家结算处理异常: userId={}, roundId={}, error={}",
                        userId, roundId, e.getMessage(), e);
                errors.put(userId, "处理异常: " + e.getMessage());
            }
        }
        return errors;
    }

    /**
     * 记录房费流水
     */
    private void recordRoomFee(SettleMessageDTO dto, SettlePlayerResultDTO playerResult, Long roundId) {
        RoomFeeLedger feeLedger = new RoomFeeLedger();
        feeLedger.setUserId(String.valueOf(playerResult.getUserId()));
        feeLedger.setRoomId(dto.getRoomId());
        feeLedger.setFeeType("SETTLE");
        feeLedger.setFeeAmount(playerResult.getRoomFeeAmount());
        feeLedger.setPayWalletType(WalletType.ROOM_CARD.getCode());
        feeLedger.setRemark(String.format("第%d局房费 | %s | 玩家:%d",
                dto.getRoundNo(), dto.getGameCode(), playerResult.getUserId()));
        feeLedger.setCreateTime(LocalDateTime.now());
        roomFeeLedgerMapper.insert(feeLedger);
    }

    /**
     * 更新房间状态
     */
    private void updateRoomStatus(Room room, Integer roundNo, SettleMessageDTO dto) {
        Integer currentRound = room.getCurrentRound() != null ? room.getCurrentRound() : 0;
        Integer totalRound = room.getTotalRound() != null ? room.getTotalRound() : 0;

        // 更新当前局数
        room.setCurrentRound(Math.max(currentRound, roundNo));

        // 判断是否全部结算完毕
        if (totalRound > 0 && roundNo >= totalRound) {
            // 全部局数已结算 → 房间状态改为"已结束"
            room.setStatus(Room.STATUS_FINISHED);
            room.setFinishedAt(LocalDateTime.now());
            log.info("[结算] 房间全部局数已结算, 状态→已结束: roomId={}, totalRound={}",
                    room.getId(), totalRound);
        } else {
            // 还有剩余局数 → 回到"就绪"等待下一局
            if (room.getStatus() == Room.STATUS_PLAYING || room.getStatus() == Room.STATUS_SETTLING) {
                room.setStatus(Room.STATUS_READY);
            }
        }

        roomMapper.updateById(room);
    }

    /**
     * 构建结算备注信息
     */
    private String buildSettleRemark(SettleMessageDTO dto, SettlePlayerResultDTO playerResult, String action) {
        return String.format("%s%d积分 | 房间:%s 第%d局 | 游戏:%s",
                action, Math.abs(playerResult.getScore()),
                dto.getRoomId(), dto.getRoundNo(), dto.getGameCode());
    }

    /**
     * 构建成功响应
     */
    private AjaxResult buildSuccessResult(Long roundId, SettleMessageDTO dto,
                                          Map<Long, String> playerErrors) {
        AjaxResult result = AjaxResult.successEx();
        result.put("roundId", roundId);
        result.put("messageId", dto.getMessageId());
        result.put("roomId", dto.getRoomId());
        result.put("roundNo", dto.getRoundNo());
        result.put("playerCount", dto.getResults().size());

        if (!playerErrors.isEmpty()) {
            result.put("partialFailure", true);
            result.put("errors", playerErrors);
            result.put("msg", "结算已完成, 但部分玩家处理存在异常");
        } else {
            result.put("partialFailure", false);
        }
        return result;
    }
}
