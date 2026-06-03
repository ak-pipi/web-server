package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niuma.admin.dto.PlayerActionDTO;
import com.niuma.admin.dto.PlayerDetailVO;
import com.niuma.admin.dto.PlayerQueryDTO;
import com.niuma.admin.entity.*;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.*;
import com.niuma.admin.service.IPlayerManageService;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.ip.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台玩家管理增强服务实现
 */
@Service
@Slf4j
public class PlayerManageServiceImpl implements IPlayerManageService {

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private CapitalMapper capitalMapper;

    @Autowired
    private GameRoundMapper gameRoundMapper;

    @Autowired
    private PlayerLoginLogMapper playerLoginLogMapper;

    @Autowired
    private AdminAuditLogMapper adminAuditLogMapper;

    @Autowired
    private IWalletService walletService;

    // ==================== 列表查询 ====================

    @Override
    public PageResult<Player> queryPlayers(PlayerQueryDTO dto) {
        LambdaQueryWrapper<Player> wrapper = Wrappers.lambdaQuery(Player.class);

        if (dto.getPlayerId() != null && !dto.getPlayerId().isEmpty()) {
            wrapper.eq(Player::getId, dto.getPlayerId());
        }
        if (dto.getKeyword() != null && !dto.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(Player::getName, dto.getKeyword())
                    .or()
                    .like(Player::getNickname, dto.getKeyword()));
        }
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            wrapper.eq(Player::getPhone, dto.getPhone());
        }
        if (dto.getOpenid() != null && !dto.getOpenid().isEmpty()) {
            wrapper.eq(Player::getOpenid, dto.getOpenid());
        }
        if (dto.getDeviceId() != null && !dto.getDeviceId().isEmpty()) {
            wrapper.eq(Player::getDeviceId, dto.getDeviceId());
        }
        if (dto.getRiskLevel() != null) {
            wrapper.eq(Player::getRiskLevel, dto.getRiskLevel());
        }
        if (dto.getBanned() != null) {
            wrapper.eq(Player::getBanned, dto.getBanned());
        }
        if (dto.getRealNameStatus() != null) {
            wrapper.eq(Player::getRealNameStatus, dto.getRealNameStatus());
        }
        if (dto.getAgencyId() != null && !dto.getAgencyId().isEmpty()) {
            wrapper.eq(Player::getAgencyId, dto.getAgencyId());
        }

        // 排除已删除的玩家
        wrapper.ne(Player::getDelFlag, 1);
        wrapper.orderByDesc(Player::getId);

        // 在线状态筛选（通过心跳判断，需要额外过滤）
        // 此处简化处理: 如果要求在线玩家，先查全部再内存过滤
        // 生产环境建议用Redis缓存在线状态

        Page<Player> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<Player> result = playerMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), (int) result.getCurrent(), (int) result.getTotal());
    }

    // ==================== 详情聚合查询 ====================

    @Override
    public AjaxResult getPlayerDetail(String playerId) {
        // 1. 基本信息
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BadRequestException("玩家不存在: " + playerId);
        }

        // 2. 构建聚合VO
        PlayerDetailVO vo = new PlayerDetailVO();
        vo.setPlayerId(player.getId());
        vo.setAccount(player.getName());
        vo.setNickname(player.getNickname());
        vo.setPhone(maskPhone(player.getPhone()));
        vo.setSex(player.getSex());
        vo.setAvatar(player.getAvatar());
        vo.setOpenid(player.getOpenid());
        vo.setRealNameStatus(player.getRealNameStatus());
        vo.setAgencyId(player.getAgencyId());

        // 代理昵称
        if (player.getAgencyId() != null && !player.getAgencyId().isEmpty()) {
            String agencyName = playerMapper.getNickname(player.getAgencyId());
            vo.setAgencyNickname(agencyName);
        }

        // 3. 账户状态
        vo.setBanned(player.getBanned());
        vo.setStatus(resolvePlayerStatus(player));
        vo.setOnline(isPlayerOnline(player));
        vo.setLastLoginIp(player.getLoginIp());
        vo.setLastLoginAt(player.getLoginDate());

        // 4. 资产概览
        loadAssetInfo(vo, playerId);

        // 5. 游戏战绩统计
        loadGameStats(vo, playerId);

        // 6. 风控信息
        vo.setRiskLevel(player.getRiskLevel());

        // 7. IP登录历史(最近20条)
        loadIpHistory(vo, playerId);

        AjaxResult result = AjaxResult.successEx();
        result.put("detail", vo);
        return result;
    }

    @Override
    public AjaxResult getPlayerDevices(String playerId) {
        Player player = checkPlayerExists(playerId);

        AjaxResult result = AjaxResult.successEx();
        result.put("playerId", playerId);

        List<Map<String, Object>> deviceList = new ArrayList<>();
        Map<String, Object> current = new HashMap<>();
        current.put("deviceId", player.getDeviceId());
        current.put("isCurrent", true);
        current.put("lastUsed", player.getLoginDate());
        deviceList.add(current);

        // TODO: 查询历史设备变更记录（需要扩展表或日志）
        // 当前仅返回当前设备

        result.put("devices", deviceList);
        result.put("total", deviceList.size());
        return result;
    }

    @Override
    public AjaxResult getPlayerIpHistory(String playerId) {
        checkPlayerExists(playerId);

        LambdaQueryWrapper<PlayerLoginLog> wrapper = Wrappers.lambdaQuery(PlayerLoginLog.class)
                .eq(PlayerLoginLog::getPlayerId, playerId)
                .orderByDesc(PlayerLoginLog::getLoginTime)
                .last("LIMIT 50");
        List<PlayerLoginLog> logs = playerLoginLogMapper.selectList(wrapper);

        AjaxResult result = AjaxResult.successEx();
        result.put("playerId", playerId);
        result.put("records", logs);
        result.put("total", logs.size());
        return result;
    }

    @Override
    public AjaxResult getPlayerRecords(String playerId) {
        checkPlayerExists(playerId);

        PlayerDetailVO stats = new PlayerDetailVO();
        loadGameStats(stats, playerId);

        // 按游戏类型分组统计 TODO: 需要关联room.gameCode

        AjaxResult result = AjaxResult.successEx();
        result.put("playerId", playerId);
        result.put("totalRounds", stats.getTotalRounds());
        result.put("winCount", stats.getWinCount());
        result.put("loseCount", stats.getLoseCount());
        result.put("winRate", stats.getWinRate());
        result.put("todayRounds", stats.getTodayRounds());
        return result;
    }

    // ==================== 账户操作 ====================

    @Override
    public AjaxResult banPlayer(PlayerActionDTO dto, String operator) {
        String playerId = dto.getPlayerId();
        Player player = checkPlayerExists(playerId);

        if (player.getBanned() != null && player.getBanned() == 1) {
            throw new BadRequestException("玩家已被封禁, 无需重复操作");
        }

        // 执行封禁
        player.setBanned(1);
        playerMapper.updateById(player);

        // 写入审计日志
        writeAuditLog(operator, "PLAYER_BAN", playerId,
                "封禁账号 | 原因: " + dto.getReason(),
                dto.getRemark(), 1);

        log.info("[玩家管理] 封禁玩家: operator={}, playerId={}, reason={}",
                operator, playerId, dto.getReason());
        return AjaxResult.success("封禁成功");
    }

    @Override
    public AjaxResult unbanPlayer(PlayerActionDTO dto, String operator) {
        String playerId = dto.getPlayerId();
        Player player = checkPlayerExists(playerId);

        if (player.getBanned() == null || player.getBanned() != 1) {
            throw new BadRequestException("玩家未被封禁, 无需解封");
        }

        player.setBanned(0);
        playerMapper.updateById(player);

        writeAuditLog(operator, "PLAYER_UNBAN", playerId,
                "解封账号 | 原因: " + dto.getReason(),
                dto.getRemark(), 1);

        log.info("[玩家管理] 解封玩家: operator={}, playerId={}", operator, playerId);
        return AjaxResult.success("解封成功");
    }

    @Override
    public AjaxResult freezePlayer(PlayerActionDTO dto, String operator) {
        String playerId = dto.getPlayerId();
        Player player = checkPlayerExists(playerId);

        // 冻结标记使用 riskLevel 特殊值 或新增 frozen 字段
        // 此处采用 riskLevel=99 标记冻结（与风控体系联动）
        Integer prevRiskLevel = player.getRiskLevel();

        player.setRiskLevel(99); // 99=冻结状态特殊标识
        playerMapper.updateById(player);

        String remark = "冻结账号";
        if (dto.getFrozenUntil() != null && !dto.getFrozenUntil().isEmpty()) {
            remark += " | 截止时间: " + dto.getFrozenUntil();
        }
        remark += " | 原因: " + dto.getReason();

        writeAuditLog(operator, "PLAYER_FREEZE", playerId, remark, dto.getRemark(), 1);

        log.info("[玩家管理] 冻结玩家: operator={}, playerId={}, until={}",
                operator, playerId, dto.getFrozenUntil());
        return AjaxResult.success("冻结成功");
    }

    @Override
    public AjaxResult unfreezePlayer(PlayerActionDTO dto, String operator) {
        String playerId = dto.getPlayerId();
        Player player = checkPlayerExists(playerId);

        if (player.getRiskLevel() == null || player.getRiskLevel() != 99) {
            throw new BadRequestException("玩家未被冻结, 无需解冻");
        }

        // 恢复为正常风险等级
        player.setRiskLevel(0); // 恢复为"正常"
        playerMapper.updateById(player);

        writeAuditLog(operator, "PLAYER_UNFREEZE", playerId,
                "解冻账号 | 原因: " + dto.getReason(),
                dto.getRemark(), 1);

        log.info("[玩家管理] 解冻玩家: operator={}, playerId={}", operator, playerId);
        return AjaxResult.success("解冻成功");
    }

    @Override
    public AjaxResult addRiskNote(PlayerActionDTO dto, String operator) {
        String playerId = dto.getPlayerId();
        Player player = checkPlayerExists(playerId);

        // 写入风控备注到审计日志（type=RISK_NOTE）
        writeAuditLog(operator, "PLAYER_RISK_NOTE", playerId,
                "风控备注 | 原因: " + dto.getReason(),
                dto.getRemark(), 0);

        log.info("[玩家管理] 添加风控备注: operator={}, playerId={}, reason={}",
                operator, playerId, dto.getReason());
        return AjaxResult.success("备注添加成功");
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 校验玩家是否存在
     */
    private Player checkPlayerExists(String playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BadRequestException("玩家不存在: " + playerId);
        }
        return player;
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 解析玩家当前状态字符串
     */
    private String resolvePlayerStatus(Player player) {
        if (player.getBanned() != null && player.getBanned() == 1) {
            return "banned";
        }
        if (player.getRiskLevel() != null && player.getRiskLevel() >= 99) {
            return "frozen";
        }
        if (isPlayerOnline(player)) {
            return "online";
        }
        return "offline";
    }

    /**
     * 判断玩家是否在线（基于心跳时间）
     */
    private boolean isPlayerOnline(Player player) {
        // TODO: 接入Redis/Netty在线状态判断
        // 简化实现：30分钟内有登录记录视为可能在线
        if (player.getLoginDate() == null) {
            return false;
        }
        return player.getLoginDate().isAfter(LocalDateTime.now().minusMinutes(30));
    }

    /**
     * 加载资产信息
     */
    private void loadAssetInfo(PlayerDetailVO vo, String playerId) {
        try {
            AjaxResult balances = walletService.getBalances(playerId);
            if (balances != null) {
                Object goldObj = balances.get("gold");
                Object depositObj = balances.get("deposit");
                Object diamondObj = balances.get("diamond");
                Object roomCardObj = balances.get("room_card");
                Object safeboxObj = balances.get("safebox");

                vo.setGoldBalance(goldObj instanceof Number ? ((Number) goldObj).longValue() : 0L);
                vo.setDepositBalance(depositObj instanceof Number ? ((Number) depositObj).longValue() : 0L);
                vo.setDiamondBalance(diamondObj instanceof Number ? ((Number) diamondObj).longValue() : 0L);
                vo.setRoomCardBalance(roomCardObj instanceof Number ? ((Number) roomCardObj).longValue() : 0L);
                vo.setSafeboxBalance(safeboxObj instanceof Number ? ((Number) safeboxObj).longValue() : 0L);
            }
        } catch (Exception e) {
            log.warn("[玩家管理] 加载资产信息失败: playerId={}, error={}", playerId, e.getMessage());
            vo.setGoldBalance(0L);
            vo.setDepositBalance(0L);
            vo.setDiamondBalance(0L);
            vo.setRoomCardBalance(0L);
            vo.setSafeboxBalance(0L);
        }
    }

    /**
     * 加载游戏战绩统计
     */
    private void loadGameStats(PlayerDetailVO vo, String playerId) {
        try {
            // 总局数统计（从 game_round 的 result_json 中提取该玩家的对局）
            // 简化实现: 通过结算流水中的 GAME_SETTLE 类型统计
            Long totalRounds = 0L;
            Long winCount = 0L;
            Long loseCount = 0L;
            Long totalScoreDelta = 0L;

            // TODO: 从 wallet_ledger 统计 game_settle 类型的变动次数作为总局数近似值
            // 完整实现应从 game_round + result_json 中解析每个玩家的胜负

            vo.setTotalRounds(totalRounds.intValue());
            vo.setWinCount(winCount.intValue());
            vo.setLoseCount(loseCount.intValue());
            vo.setTotalScoreDelta(totalScoreDelta);

            // 计算胜率
            if (totalRounds > 0) {
                BigDecimal winRate = new BigDecimal(winCount)
                        .multiply(new BigDecimal("100"))
                        .divide(new BigDecimal(totalRounds), 1, BigDecimal.ROUND_HALF_UP);
                vo.setWinRate(winRate);
            } else {
                vo.setWinRate(BigDecimal.ZERO);
            }

            // 今日局数（当天结算的局数近似）
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            // TODO: 按 settledAt 时间范围统计 game_round

        } catch (Exception e) {
            log.warn("[玩家管理] 加载战绩统计失败: playerId={}, error={}", vo.getPlayerId(), e.getMessage());
        }
    }

    /**
     * 加载IP登录历史
     */
    private void loadIpHistory(PlayerDetailVO vo, String playerId) {
        try {
            LambdaQueryWrapper<PlayerLoginLog> wrapper = Wrappers.lambdaQuery(PlayerLoginLog.class)
                    .eq(PlayerLoginLog::getPlayerId, playerId)
                    .orderByDesc(PlayerLoginLog::getLoginTime)
                    .last("LIMIT 20");
            List<PlayerLoginLog> logs = playerLoginLogMapper.selectList(wrapper);

            List<PlayerDetailVO.IpHistoryRecord> records = new ArrayList<>();
            for (PlayerLoginLog log : logs) {
                PlayerDetailVO.IpHistoryRecord record = new PlayerDetailVO.IpHistoryRecord();
                record.setIp(log.getLoginIp());
                record.setLocation(log.getLoginLocation());
                record.setLoginTime(log.getLoginTime());
                records.add(record);
            }
            vo.setIpHistory(records);
        } catch (Exception e) {
            log.warn("[玩家管理] 加载IP历史失败: playerId={}, error={}", playerId, e.getMessage());
            vo.setIpHistory(new ArrayList<>());
        }
    }

    /**
     * 写入审计日志
     */
    private void writeAuditLog(String operator, String action, String targetId,
                               String reason, String remark, int status) {
        AdminAuditLog auditLog = new AdminAuditLog();
        auditLog.setAdminName(operator);
        auditLog.setAction(action);
        auditLog.setTargetType("PLAYER");
        auditLog.setTargetId(targetId);
        auditLog.setReason(reason + (remark != null && !remark.isEmpty() ? " | " + remark : ""));
        auditLog.setStatus(status);
        auditLog.setIp(IpUtils.getIpAddr());
        auditLog.setCreateTime(LocalDateTime.now());
        adminAuditLogMapper.insert(auditLog);
    }
}
