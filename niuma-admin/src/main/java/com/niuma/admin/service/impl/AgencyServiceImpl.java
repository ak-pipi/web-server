package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.constant.NiuMaCodeEnum;
import com.niuma.admin.constant.NiuMaConstants;
import com.niuma.admin.dto.AgencyMemberDTO;
import com.niuma.admin.dto.AgencyMemberQueryDTO;
import com.niuma.admin.dto.AgencyMatchGiftStatsDTO;
import com.niuma.admin.dto.AgencyMatchLedgerDTO;
import com.niuma.admin.dto.AgencyMatchOperationLogDTO;
import com.niuma.admin.dto.AgencyMatchPlayerDTO;
import com.niuma.admin.dto.AgencyMatchQueryDTO;
import com.niuma.admin.dto.AgencyStatsDTO;
import com.niuma.admin.dto.AgencyStatsQueryDTO;
import com.niuma.admin.dto.AgencyPlayLimitDTO;
import com.niuma.admin.dto.CollectRecordDTO;
import com.niuma.admin.dto.JuniorPlayerDTO;
import com.niuma.admin.dto.RewardDTO;
import com.niuma.admin.dto.WalletAdjustDTO;
import com.niuma.admin.entity.Agency;
import com.niuma.admin.entity.AgencyCommissionLedger;
import com.niuma.admin.entity.AgencyCollect;
import com.niuma.admin.entity.AgencyWalletAdjustLog;
import com.niuma.admin.entity.Capital;
import com.niuma.admin.entity.Player;
import com.niuma.admin.entity.PlayerAgentBind;
import com.niuma.admin.entity.PlayerGameRestriction;
import com.niuma.admin.entity.RoomFeeLedger;
import com.niuma.admin.entity.WalletLedger;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.AgencyCollectMapper;
import com.niuma.admin.mapper.AgencyCommissionLedgerMapper;
import com.niuma.admin.mapper.AgencyMapper;
import com.niuma.admin.mapper.AgencyWalletAdjustLogMapper;
import com.niuma.admin.mapper.CapitalMapper;
import com.niuma.admin.mapper.PlayerAgentBindMapper;
import com.niuma.admin.mapper.PlayerGameRestrictionMapper;
import com.niuma.admin.mapper.PlayerMapper;
import com.niuma.admin.mapper.VenueMapper;
import com.niuma.admin.mapper.WalletLedgerMapper;
import com.niuma.admin.service.IAgencyService;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.constant.ResultCodeEnum;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.dto.IntPairDTO;
import com.niuma.common.dto.LongPairDTO;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.exception.http.ForbiddenException;
import com.niuma.common.exception.http.InternalServerException;
import com.niuma.common.exception.http.NotFoundException;
import com.niuma.common.utils.CommonUtils;
import com.niuma.common.utils.PlayerSecurityUtils;
import com.niuma.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class AgencyServiceImpl extends ServiceImpl<AgencyMapper, Agency> implements IAgencyService {
    private static final String MEMBER_TYPE_ALL = "all";
    private static final String MEMBER_TYPE_AGENT = "agent";
    private static final String MEMBER_TYPE_PLAYER = "player";
    private static final String MATCH_VIEW_SHARE = "share";
    private static final String MATCH_VIEW_SHUFFLE_SHARE = "shuffleShare";
    private static final DateTimeFormatter INCOME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private PlayerAgentBindMapper playerAgentBindMapper;

    @Autowired
    private PlayerGameRestrictionMapper playerGameRestrictionMapper;

    @Autowired
    private AgencyCollectMapper agencyCollectMapper;

    @Autowired
    private CapitalMapper capitalMapper;

    @Autowired
    private WalletLedgerMapper walletLedgerMapper;

    @Autowired
    private VenueMapper venueMapper;

    @Autowired
    private AgencyWalletAdjustLogMapper agencyWalletAdjustLogMapper;

    @Autowired
    private AgencyCommissionLedgerMapper agencyCommissionLedgerMapper;

    @Autowired
    private IWalletService walletService;

    @Autowired
    private AgencyStatsSupport agencyStatsSupport;

    @Override
    public AjaxResult getAgency() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        LambdaQueryWrapper<Agency> query = Wrappers.lambdaQuery();
        query.eq(Agency::getPlayerId, player.getId());
        Agency entity = this.baseMapper.selectOne(query);
        if (entity == null)
            throw new ForbiddenException(NiuMaCodeEnum.AGENCY_ERROR.getCode(), "Current player is not agency");
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("level", entity.getLevel());
        ajax.put("superiorId", entity.getSuperiorId());
        ajax.put("juniorCount", entity.getJuniorCount());
        ajax.put("totalReward", entity.getTotalReward());
        Long reward = this.baseMapper.getIndirectReward(player.getId());
        if (reward == null)
            reward = 0L;
        ajax.put("indirectReward", reward);
        reward = this.baseMapper.getCurrentReward(player.getId());
        if (reward == null)
            reward = 0L;
        ajax.put("currentReward", reward);
        return ajax;
    }

    @Override
    public PageResult<JuniorPlayerDTO> getJuniorPlayers(PageBody dto) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        PageResult<JuniorPlayerDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        Integer totalNum = this.playerMapper.countJuniorPlayer(player.getId());
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<JuniorPlayerDTO> records = this.playerMapper.getJuniorPlayers(player.getId(), offset, dto.getPageSize());
        if (records != null) {
            Integer level = 0;
            Integer juniorCount = 0;
            Long totalReward = 0L;
            for (JuniorPlayerDTO record : records) {
                IntPairDTO pair = this.baseMapper.getLevelAndJuniorCount(record.getPlayerId());
                if (pair != null) {
                    level = pair.getValue1();
                    juniorCount = pair.getValue2();
                } else {
                    level = 0;
                    juniorCount = 0;
                }
                record.setLevel(level);
                record.setJuniorCount(juniorCount);
                totalReward = this.baseMapper.getTotalReward(player.getId(), record.getPlayerId());
                if (totalReward == null)
                    totalReward = 0L;
                record.setTotalReward(totalReward);
            }
        }
        result.setRecords(records);
        return result;
    }

    @Override
    public PageResult<AgencyMemberDTO> getMemberPage(AgencyMemberQueryDTO dto) {
        validatePage(dto);
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String parentPlayerId = StringUtils.isNotEmpty(dto.getParentPlayerId())
                ? dto.getParentPlayerId().trim()
                : loginPlayer.getId();
        Agency parentAgency = loginPlayer.getId().equals(parentPlayerId)
                ? currentAgency
                : requireEnabledAgency(parentPlayerId);
        ensureInCurrentAgencyScope(currentAgency, parentAgency);

        String memberType = normalizeMemberType(dto.getMemberType());
        String keyword = StringUtils.trim(dto.getKeyword()).toLowerCase(Locale.ROOT);

        List<Agency> directAgencies = this.baseMapper.selectList(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getSuperiorId, parentPlayerId)
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus))
                        .orderByAsc(Agency::getDepth).orderByAsc(Agency::getId));
        Map<String, Agency> directAgencyMap = new LinkedHashMap<>();
        for (Agency agency : directAgencies) {
            directAgencyMap.put(agency.getPlayerId(), agency);
        }
        Map<String, PlayerAgentBind> directBindMap = findActiveDirectBindMap(parentPlayerId);

        Map<String, Player> playerMap = new LinkedHashMap<>();
        List<Player> directPlayers = this.playerMapper.selectList(
                Wrappers.lambdaQuery(Player.class)
                        .eq(Player::getAgencyId, parentPlayerId)
                        .and(w -> w.eq(Player::getDelFlag, 0).or().isNull(Player::getDelFlag)));
        for (Player player : directPlayers) {
            playerMap.put(player.getId(), player);
        }
        if (!directAgencyMap.isEmpty()) {
            List<Player> agentPlayers = this.playerMapper.selectBatchIds(directAgencyMap.keySet());
            for (Player player : agentPlayers) {
                playerMap.put(player.getId(), player);
            }
        }

        List<AgencyMemberDTO> members = new ArrayList<>();
        if (MEMBER_TYPE_ALL.equals(memberType) || MEMBER_TYPE_AGENT.equals(memberType)) {
            for (Agency agency : directAgencies) {
                Player player = playerMap.get(agency.getPlayerId());
                if (player != null) {
                    AgencyMemberDTO member = toAgencyMemberDTO(player, agency, parentPlayerId, directBindMap.get(player.getId()));
                    if (matchesKeyword(member, player, keyword)) {
                        members.add(member);
                    }
                }
            }
        }
        if (MEMBER_TYPE_ALL.equals(memberType) || MEMBER_TYPE_PLAYER.equals(memberType)) {
            for (Player player : directPlayers) {
                if (directAgencyMap.containsKey(player.getId())) {
                    continue;
                }
                AgencyMemberDTO member = toAgencyMemberDTO(player, null, parentPlayerId, directBindMap.get(player.getId()));
                if (matchesKeyword(member, player, keyword)) {
                    members.add(member);
                }
            }
        }

        members.sort((a, b) -> {
            LocalDateTime left = a.getLoginTime();
            LocalDateTime right = b.getLoginTime();
            if (left == null && right == null) {
                return StringUtils.nvl(a.getPlayerId(), "").compareTo(StringUtils.nvl(b.getPlayerId(), ""));
            }
            if (left == null) return 1;
            if (right == null) return -1;
            return right.compareTo(left);
        });

        return pageMembers(members, dto);
    }

    @Override
    @Transactional
    public AjaxResult setMemberAgent(String playerId, Integer commissionRateBp) {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String targetPlayerId = StringUtils.trim(playerId);
        if (StringUtils.isEmpty(targetPlayerId)) {
            throw new BadRequestException("玩家ID不能为空");
        }
        if (loginPlayer.getId().equals(targetPlayerId)) {
            throw new ForbiddenException("不能将自己设置为下级合伙人");
        }
        int rate = normalizeRate(commissionRateBp, resolveRate(currentAgency));

        Player targetPlayer = this.playerMapper.selectById(targetPlayerId);
        if (targetPlayer == null) {
            throw new NotFoundException("玩家不存在: " + targetPlayerId);
        }
        if (CommonUtils.predicate(targetPlayer.getBanned()) || CommonUtils.predicate(targetPlayer.getDelFlag())) {
            throw new ForbiddenException("玩家已封禁或删除，不能设置为合伙人");
        }

        Agency existingAgency = this.baseMapper.selectOne(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getPlayerId, targetPlayerId)
                        .last("LIMIT 1"));
        if (existingAgency != null) {
            ensureInCurrentAgencyScope(currentAgency, existingAgency);
            AjaxResult ajax = AjaxResult.successEx();
            ajax.put("playerId", existingAgency.getPlayerId());
            ajax.put("agentType", existingAgency.getAgentType());
            ajax.put("commissionRateBp", existingAgency.getCommissionRateBp());
            ajax.put("alreadyAgent", true);
            return ajax;
        }

        PlayerAgentBind activeBind = findActiveBind(targetPlayerId);
        boolean activeBoundToCurrent = activeBind != null && loginPlayer.getId().equals(activeBind.getAgentPlayerId());
        boolean legacyBoundToCurrent = loginPlayer.getId().equals(targetPlayer.getAgencyId());
        if (!activeBoundToCurrent && !legacyBoundToCurrent) {
            throw new ForbiddenException("只能设置当前合伙人直属成员");
        }
        if (activeBind != null && !activeBoundToCurrent) {
            throw new ForbiddenException("目标玩家已绑定其他代理");
        }

        int parentDepth = safeInt(currentAgency.getDepth());
        if (parentDepth <= 0) {
            parentDepth = safeInt(currentAgency.getLevel());
        }
        int depth = Math.max(2, parentDepth + 1);
        Agency agency = new Agency();
        agency.setPlayerId(targetPlayerId);
        agency.setSuperiorId(loginPlayer.getId());
        agency.setLevel(depth);
        agency.setAgentType(Agency.TYPE_LEVEL_TWO);
        agency.setDepth(depth);
        agency.setPath(resolveAgencyPath(currentAgency) + targetPlayerId + "/");
        agency.setCommissionRateBp(rate);
        agency.setJuniorCount(0);
        agency.setTotalReward(0L);
        agency.setStatus(Agency.STATUS_NORMAL);
        agency.setCreatedByPlayerId(loginPlayer.getId());
        this.baseMapper.insert(agency);

        if (activeBind == null) {
            PlayerAgentBind bind = new PlayerAgentBind();
            bind.setPlayerId(targetPlayerId);
            bind.setAgentPlayerId(loginPlayer.getId());
            bind.setRootAgentPlayerId(rootAgentId(currentAgency));
            bind.setBindSource("member_set_agent");
            bind.setPathSnapshot(resolveAgencyPath(currentAgency) + targetPlayerId + "/");
            bind.setStatus(PlayerAgentBind.STATUS_ACTIVE);
            bind.setBindAt(LocalDateTime.now());
            this.playerAgentBindMapper.insert(bind);
        }
        this.playerMapper.updateAgencyId(targetPlayerId, loginPlayer.getId());

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", agency.getPlayerId());
        ajax.put("agentType", agency.getAgentType());
        ajax.put("commissionRateBp", agency.getCommissionRateBp());
        ajax.put("alreadyAgent", false);
        return ajax;
    }

    @Override
    @Transactional
    public AjaxResult removeMember(String playerId, String reason) {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String targetPlayerId = normalizeTargetPlayerId(playerId);
        if (loginPlayer.getId().equals(targetPlayerId)) {
            throw new ForbiddenException("不能踢出自己");
        }
        Player targetPlayer = requireScopedPlayer(currentAgency, targetPlayerId);
        Agency targetAgency = findAgency(targetPlayerId);
        if (targetAgency != null) {
            ensureInCurrentAgencyScope(currentAgency, targetAgency);
            if (hasActiveChildren(targetPlayerId)) {
                throw new ForbiddenException("该合伙人仍有下级成员，请先处理下级关系");
            }
            targetAgency.setStatus(Agency.STATUS_DISABLED);
            this.baseMapper.updateById(targetAgency);
        }

        PlayerAgentBind activeBind = findActiveBind(targetPlayerId);
        if (activeBind != null) {
            Agency bindAgent = findAgency(activeBind.getAgentPlayerId());
            if (bindAgent != null) {
                ensureInCurrentAgencyScope(currentAgency, bindAgent);
            }
            activeBind.setStatus(PlayerAgentBind.STATUS_UNBOUND);
            activeBind.setUnbindAt(LocalDateTime.now());
            activeBind.setUnbindReason(StringUtils.nvl(reason, "Cocos成员管理踢出成员"));
            this.playerAgentBindMapper.updateById(activeBind);
            adjustJuniorCounts(activeBind.getAgentPlayerId(), -1);
        }
        this.playerMapper.updateAgencyId(targetPlayer.getId(), null);

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", targetPlayerId);
        return ajax;
    }

    @Override
    @Transactional
    public AjaxResult demoteMember(String playerId, String reason) {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String targetPlayerId = normalizeTargetPlayerId(playerId);
        if (loginPlayer.getId().equals(targetPlayerId)) {
            throw new ForbiddenException("不能将自己降为成员");
        }
        Player targetPlayer = requireScopedPlayer(currentAgency, targetPlayerId);
        Agency targetAgency = findAgency(targetPlayerId);
        if (targetAgency == null || (targetAgency.getStatus() != null && targetAgency.getStatus() != Agency.STATUS_NORMAL)) {
            throw new BadRequestException("目标玩家不是有效合伙人");
        }
        ensureInCurrentAgencyScope(currentAgency, targetAgency);
        if (hasActiveChildren(targetPlayerId)) {
            throw new ForbiddenException("该合伙人仍有下级成员，请先处理下级关系");
        }
        String superiorId = StringUtils.nvl(targetAgency.getSuperiorId(), loginPlayer.getId());
        targetAgency.setStatus(Agency.STATUS_DISABLED);
        this.baseMapper.updateById(targetAgency);
        this.playerMapper.updateAgencyId(targetPlayer.getId(), superiorId);

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", targetPlayerId);
        ajax.put("superiorId", superiorId);
        ajax.put("reason", reason);
        return ajax;
    }

    @Override
    @Transactional
    public AjaxResult updateMemberGameStatus(String playerId, Integer banned, String reason) {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String targetPlayerId = normalizeTargetPlayerId(playerId);
        if (loginPlayer.getId().equals(targetPlayerId)) {
            throw new ForbiddenException("不能操作自己的游戏状态");
        }
        Player targetPlayer = requireScopedPlayer(currentAgency, targetPlayerId);
        int status = banned != null && banned == 1 ? 1 : 0;
        targetPlayer.setBanned(status);
        this.playerMapper.updateById(targetPlayer);

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", targetPlayerId);
        ajax.put("banned", status);
        ajax.put("reason", reason);
        return ajax;
    }

    @Override
    @Transactional
    public AjaxResult updateMemberRemark(String playerId, String remark) {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String targetPlayerId = normalizeTargetPlayerId(playerId);
        if (loginPlayer.getId().equals(targetPlayerId)) {
            throw new ForbiddenException("不能给自己设置成员备注");
        }
        Player targetPlayer = requireScopedPlayer(currentAgency, targetPlayerId);
        String normalizedRemark = normalizeMemberRemark(remark);

        Agency targetAgency = findAgency(targetPlayerId);
        if (targetAgency != null && (targetAgency.getStatus() == null || targetAgency.getStatus() == Agency.STATUS_NORMAL)) {
            ensureInCurrentAgencyScope(currentAgency, targetAgency);
            targetAgency.setMemberRemark(normalizedRemark);
            this.baseMapper.updateById(targetAgency);
        } else {
            PlayerAgentBind activeBind = ensureRemarkBind(currentAgency, targetPlayer);
            activeBind.setMemberRemark(normalizedRemark);
            this.playerAgentBindMapper.updateById(activeBind);
        }

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", targetPlayerId);
        ajax.put("remark", StringUtils.nvl(normalizedRemark, ""));
        return ajax;
    }

    @Override
    public AjaxResult getPlayLimit(String playerId) {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String targetPlayerId = normalizeTargetPlayerId(playerId);
        requireScopedPlayer(currentAgency, targetPlayerId);
        List<PlayerGameRestriction> rows = this.playerGameRestrictionMapper.selectList(
                Wrappers.lambdaQuery(PlayerGameRestriction.class)
                        .eq(PlayerGameRestriction::getPlayerId, targetPlayerId)
                        .eq(PlayerGameRestriction::getRestricted, PlayerGameRestriction.STATUS_RESTRICTED));
        List<Integer> gameTypes = new ArrayList<>();
        for (PlayerGameRestriction row : rows) {
            gameTypes.add(row.getGameType());
        }
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", targetPlayerId);
        ajax.put("gameTypes", gameTypes);
        return ajax;
    }

    @Override
    @Transactional
    public AjaxResult updatePlayLimit(AgencyPlayLimitDTO dto) {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String targetPlayerId = normalizeTargetPlayerId(dto.getPlayerId());
        if (loginPlayer.getId().equals(targetPlayerId)) {
            throw new ForbiddenException("不能限制自己的玩法");
        }
        requireScopedPlayer(currentAgency, targetPlayerId);
        this.playerGameRestrictionMapper.delete(
                Wrappers.lambdaQuery(PlayerGameRestriction.class)
                        .eq(PlayerGameRestriction::getPlayerId, targetPlayerId));
        Set<Integer> gameTypes = new LinkedHashSet<>();
        if (dto.getGameTypes() != null) {
            for (Integer gameType : dto.getGameTypes()) {
                if (gameType != null && gameType > 0) {
                    gameTypes.add(gameType);
                }
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (Integer gameType : gameTypes) {
            PlayerGameRestriction restriction = new PlayerGameRestriction();
            restriction.setPlayerId(targetPlayerId);
            restriction.setGameType(gameType);
            restriction.setRestricted(PlayerGameRestriction.STATUS_RESTRICTED);
            restriction.setOperatorAgentPlayerId(loginPlayer.getId());
            restriction.setReason(StringUtils.nvl(dto.getReason(), "Cocos玩法限制"));
            restriction.setCreateTime(now);
            restriction.setUpdateTime(now);
            this.playerGameRestrictionMapper.insert(restriction);
        }
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", targetPlayerId);
        ajax.put("gameTypes", new ArrayList<>(gameTypes));
        return ajax;
    }

    @Override
    public PageResult<AgencyStatsDTO> getStatsPage(AgencyStatsQueryDTO dto) {
        validatePage(dto);
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String parentPlayerId = StringUtils.isNotEmpty(dto.getParentPlayerId())
                ? dto.getParentPlayerId().trim()
                : loginPlayer.getId();
        Agency parentAgency = loginPlayer.getId().equals(parentPlayerId)
                ? currentAgency
                : requireEnabledAgency(parentPlayerId);
        ensureInCurrentAgencyScope(currentAgency, parentAgency);
        return agencyStatsSupport.statsPage(dto, parentAgency.getPlayerId(), nickname(parentAgency.getPlayerId()));
    }

    @Override
    public PageResult<AgencyMatchPlayerDTO> getMatchPlayerPage(AgencyMatchQueryDTO dto) {
        validatePage(dto);
        Agency currentAgency = currentLoginAgency();
        String viewType = StringUtils.trim(dto.getViewType());
        List<AgencyMatchPlayerDTO> rows = MATCH_VIEW_SHARE.equals(viewType) || MATCH_VIEW_SHUFFLE_SHARE.equals(viewType)
                ? queryMatchSharePlayers(currentAgency, dto, MATCH_VIEW_SHUFFLE_SHARE.equals(viewType)
                        ? RoomFeeLedger.FEE_TYPE_SHUFFLE
                        : null)
                : queryMatchPlayers(currentAgency, dto);
        return pageList(rows, dto);
    }

    @Override
    public PageResult<AgencyMatchLedgerDTO> getMatchLedgerPage(AgencyMatchQueryDTO dto) {
        validatePage(dto);
        Agency currentAgency = currentLoginAgency();
        String targetPlayerId = StringUtils.trim(dto.getPlayerId());
        List<String> playerIds = StringUtils.isNotEmpty(targetPlayerId)
                ? Collections.singletonList(requireScopedPlayer(currentAgency, targetPlayerId).getId())
                : matchScopePlayerIds(currentAgency, dto);
        if (playerIds.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), dto.getPageNum(), 0);
        }

        LambdaQueryWrapper<WalletLedger> wrapper = Wrappers.lambdaQuery(WalletLedger.class)
                .eq(WalletLedger::getWalletType, WalletType.GOLD.getCode())
                .in(WalletLedger::getUserId, playerIds);
        applyLedgerDate(wrapper, parseDate(dto.getDate()));
        applyLedgerChangeFilter(wrapper, dto.getChangeType());
        Integer total = this.walletLedgerMapper.selectCount(wrapper);
        wrapper.orderByDesc(WalletLedger::getId)
                .last(limitClause(dto.getPageNum(), dto.getPageSize()));
        List<WalletLedger> ledgers = this.walletLedgerMapper.selectList(wrapper);
        List<AgencyMatchLedgerDTO> records = new ArrayList<>();
        for (WalletLedger ledger : ledgers) {
            records.add(toMatchLedgerDTO(ledger));
        }
        return new PageResult<>(records, dto.getPageNum(), total == null ? 0 : total);
    }

    @Override
    public PageResult<AgencyMatchOperationLogDTO> getMatchOperationLogPage(AgencyMatchQueryDTO dto) {
        validatePage(dto);
        Agency currentAgency = currentLoginAgency();
        LocalDate date = parseDate(dto.getDate());
        LambdaQueryWrapper<AgencyWalletAdjustLog> wrapper = Wrappers.lambdaQuery(AgencyWalletAdjustLog.class)
                .eq(AgencyWalletAdjustLog::getWalletType, WalletType.GOLD.getCode())
                .ge(AgencyWalletAdjustLog::getCreateTime, date.atStartOfDay())
                .lt(AgencyWalletAdjustLog::getCreateTime, date.plusDays(1).atStartOfDay())
                .orderByDesc(AgencyWalletAdjustLog::getId);
        String changeType = StringUtils.trim(dto.getChangeType());
        if ("admin_up".equals(changeType)) {
            wrapper.gt(AgencyWalletAdjustLog::getChangeAmount, 0);
        } else if ("admin_down".equals(changeType)) {
            wrapper.lt(AgencyWalletAdjustLog::getChangeAmount, 0);
        }
        List<AgencyWalletAdjustLog> logs = this.agencyWalletAdjustLogMapper.selectList(wrapper);
        List<AgencyMatchOperationLogDTO> rows = new ArrayList<>();
        String keyword = StringUtils.trim(dto.getKeyword()).toLowerCase(Locale.ROOT);
        for (AgencyWalletAdjustLog log : logs) {
            if (!isPlayerIdInCurrentScope(currentAgency, log.getTargetPlayerId())) {
                continue;
            }
            AgencyMatchOperationLogDTO row = toOperationLogDTO(log);
            if (matchesOperationKeyword(row, keyword)) {
                rows.add(row);
            }
        }
        return pageList(rows, dto);
    }

    @Override
    public PageResult<AgencyMatchGiftStatsDTO> getMatchGiftStatsPage(AgencyMatchQueryDTO dto) {
        validatePage(dto);
        Agency currentAgency = currentLoginAgency();
        List<AgencyMatchPlayerDTO> players = queryMatchPlayers(currentAgency, dto);
        List<String> playerIds = new ArrayList<>();
        for (AgencyMatchPlayerDTO player : players) {
            playerIds.add(player.getPlayerId());
        }
        Map<String, Long> giftTimes = new HashMap<>();
        Map<String, Long> giftScores = new HashMap<>();
        if (!playerIds.isEmpty()) {
            LambdaQueryWrapper<WalletLedger> wrapper = Wrappers.lambdaQuery(WalletLedger.class)
                    .eq(WalletLedger::getWalletType, WalletType.GOLD.getCode())
                    .eq(WalletLedger::getBizType, LedgerBizType.ADMIN_ADJUST.getCode())
                    .gt(WalletLedger::getChangeAmount, 0)
                    .in(WalletLedger::getUserId, playerIds);
            applyLedgerDate(wrapper, parseDate(dto.getDate()));
            List<WalletLedger> ledgers = this.walletLedgerMapper.selectList(wrapper);
            for (WalletLedger ledger : ledgers) {
                giftTimes.put(ledger.getUserId(), giftTimes.getOrDefault(ledger.getUserId(), 0L) + 1);
                giftScores.put(ledger.getUserId(), giftScores.getOrDefault(ledger.getUserId(), 0L)
                        + Math.max(0L, ledger.getChangeAmount() == null ? 0L : ledger.getChangeAmount()));
            }
        }

        List<AgencyMatchGiftStatsDTO> rows = new ArrayList<>();
        for (AgencyMatchPlayerDTO player : players) {
            AgencyMatchGiftStatsDTO row = new AgencyMatchGiftStatsDTO();
            row.setPlayerId(player.getPlayerId());
            row.setNickname(player.getNickname());
            row.setAccount(player.getAccount());
            row.setAvatar(player.getAvatar());
            row.setIdentity(player.getIdentity());
            row.setRole(player.getRole());
            row.setJuniorCount(player.getJuniorCount());
            row.setScore(player.getScore());
            row.setGiftTimes(giftTimes.getOrDefault(player.getPlayerId(), 0L));
            row.setGiftScore(giftScores.getOrDefault(player.getPlayerId(), 0L));
            rows.add(row);
        }
        return pageList(rows, dto);
    }

    @Override
    @Transactional
    public AjaxResult adjustMatchScore(WalletAdjustDTO dto) {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        Agency currentAgency = requireEnabledAgency(loginPlayer.getId());
        String targetPlayerId = normalizeTargetPlayerId(dto.getPlayerId());
        if (loginPlayer.getId().equals(targetPlayerId)) {
            throw new ForbiddenException("不能给自己上下分");
        }
        requireScopedPlayer(currentAgency, targetPlayerId);
        if (dto.getAmount() == null || dto.getAmount() == 0L || dto.getAmount() == Long.MIN_VALUE) {
            throw new BadRequestException("调整金额不能为0");
        }
        String reason = StringUtils.isNotEmpty(dto.getReason()) ? dto.getReason() : "Cocos比赛分上下分";
        long absAmount = Math.abs(dto.getAmount());
        boolean targetIncrease = dto.getAmount() > 0;
        String refNo = "COCOS_MATCH_" + UUID.randomUUID().toString().replace("-", "");
        Long before = walletService.getBalance(targetPlayerId, WalletType.GOLD.getCode());

        Long targetLedgerId;
        Long operatorLedgerId;
        if (targetIncrease) {
            operatorLedgerId = walletService.decrease(loginPlayer.getId(), WalletType.GOLD.getCode(), absAmount,
                    LedgerBizType.ADMIN_ADJUST.getCode(), refNo + ":OUT",
                    "比赛分上分 | 对方:" + targetPlayerId + " | 原因:" + reason);
            targetLedgerId = walletService.increase(targetPlayerId, WalletType.GOLD.getCode(), absAmount,
                    LedgerBizType.ADMIN_ADJUST.getCode(), refNo + ":IN",
                    "比赛分上分 | 操作人:" + loginPlayer.getId() + " | 原因:" + reason);
        } else {
            targetLedgerId = walletService.decrease(targetPlayerId, WalletType.GOLD.getCode(), absAmount,
                    LedgerBizType.ADMIN_ADJUST.getCode(), refNo + ":OUT",
                    "比赛分下分 | 操作人:" + loginPlayer.getId() + " | 原因:" + reason);
            operatorLedgerId = walletService.increase(loginPlayer.getId(), WalletType.GOLD.getCode(), absAmount,
                    LedgerBizType.ADMIN_ADJUST.getCode(), refNo + ":IN",
                    "比赛分下分 | 对方:" + targetPlayerId + " | 原因:" + reason);
        }
        Long after = walletService.getBalance(targetPlayerId, WalletType.GOLD.getCode());

        AgencyWalletAdjustLog logEntity = new AgencyWalletAdjustLog();
        logEntity.setOperatorAgentPlayerId(loginPlayer.getId());
        logEntity.setTargetPlayerId(targetPlayerId);
        logEntity.setWalletType(WalletType.GOLD.getCode());
        logEntity.setChangeAmount(dto.getAmount());
        logEntity.setBeforeAmount(before);
        logEntity.setAfterAmount(after);
        logEntity.setWalletLedgerId(targetLedgerId);
        logEntity.setReason(reason);
        logEntity.setStatus(AgencyWalletAdjustLog.STATUS_SUCCESS);
        logEntity.setCreateTime(LocalDateTime.now());
        this.agencyWalletAdjustLogMapper.insert(logEntity);

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("ledgerId", targetLedgerId);
        ajax.put("counterpartyLedgerId", operatorLedgerId);
        ajax.put("beforeAmount", before);
        ajax.put("afterAmount", after);
        ajax.put("operatorBalance", walletService.getBalance(loginPlayer.getId(), WalletType.GOLD.getCode()));
        return ajax;
    }

    @Override
    public AjaxResult incomeBox() {
        LoginPlayer loginPlayer = requireLoginPlayer();
        Agency agency = requireEnabledAgency(loginPlayer.getId());
        return buildIncomeBoxResult(loginPlayer.getId(), agency, 0L, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult withdrawIncomeBox(Long requestedAmount) {
        LoginPlayer loginPlayer = requireLoginPlayer();
        Agency agency = requireEnabledAgency(loginPlayer.getId());
        String playerId = loginPlayer.getId();

        List<AgencyCommissionLedger> pendingLedgers = queryPendingIncomeLedgers(playerId);
        List<AgencyCommissionLedger> depositSettledLedgers =
                queryDepositSettledUncollectedIncomeLedgers(playerId, null, null, true);
        List<LongPairDTO> legacyRewards = this.baseMapper.getCurrentRewards(playerId);
        long pendingTotal = sumIncomeLedgers(pendingLedgers);
        long depositSettledTotal = sumIncomeLedgers(depositSettledLedgers);
        long legacyTotal = sumLegacyRewards(legacyRewards);
        long availableTotal = pendingTotal + depositSettledTotal + legacyTotal;
        long total = resolveWithdrawAmount(requestedAmount, availableTotal);
        long pendingConsume = Math.min(total, pendingTotal);
        long depositConsume = Math.min(total - pendingConsume, depositSettledTotal);
        long legacyConsume = total - pendingConsume - depositConsume;

        Long walletLedgerId = null;
        Long beforeGold = null;
        Long afterGold = null;
        if (total > 0L) {
            String refNo = "INCOME_BOX_WITHDRAW_" + UUID.randomUUID().toString().replace("-", "");
            beforeGold = walletService.getBalance(playerId, WalletType.GOLD.getCode());
            if (depositConsume > 0L) {
                walletService.decrease(playerId, WalletType.DEPOSIT.getCode(), depositConsume,
                        LedgerBizType.AGENCY_COMMISSION.getCode(), refNo + ":DEPOSIT_OUT",
                        "收益箱提取 | 从保险箱转出");
            }
            walletLedgerId = walletService.increase(playerId, WalletType.GOLD.getCode(), total,
                    LedgerBizType.AGENCY_COMMISSION.getCode(), refNo + ":GOLD_IN", "收益箱提取到账户");
            afterGold = walletService.getBalance(playerId, WalletType.GOLD.getCode());

            AgencyCollect collect = new AgencyCollect();
            collect.setPlayerId(playerId);
            collect.setAmount(total);
            collect.setTime(LocalDateTime.now());
            this.agencyCollectMapper.insert(collect);

            consumeIncomeLedgers(pendingLedgers, pendingConsume, collect.getId(), walletLedgerId, true, false);
            consumeIncomeLedgers(depositSettledLedgers, depositConsume, collect.getId(), null, false, true);
            consumeLegacyRewards(legacyRewards, legacyConsume, collect.getId());
            this.baseMapper.addTotalReward(playerId, total);
            agency.setTotalReward(safeLong(agency.getTotalReward()) + total);
        }

        return buildIncomeBoxResult(playerId, agency, total, walletLedgerId, beforeGold, afterGold);
    }

    private Agency currentLoginAgency() {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        return requireEnabledAgency(loginPlayer.getId());
    }

    private List<AgencyMatchPlayerDTO> queryMatchPlayers(Agency currentAgency, AgencyMatchQueryDTO dto) {
        String keyword = StringUtils.trim(dto.getKeyword()).toLowerCase(Locale.ROOT);
        String viewType = StringUtils.trim(dto.getViewType());
        boolean includeSelf = "detail".equals(viewType) || "gift".equals(viewType);
        String parentPlayerId = currentAgency.getPlayerId();

        List<Agency> directAgencies = this.baseMapper.selectList(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getSuperiorId, parentPlayerId)
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus))
                        .orderByAsc(Agency::getDepth).orderByAsc(Agency::getId));
        Map<String, Agency> agencyMap = new LinkedHashMap<>();
        for (Agency agency : directAgencies) {
            agencyMap.put(agency.getPlayerId(), agency);
        }

        Map<String, Player> playerMap = new LinkedHashMap<>();
        if (!agencyMap.isEmpty()) {
            List<Player> agentPlayers = this.playerMapper.selectBatchIds(agencyMap.keySet());
            for (Player player : agentPlayers) {
                playerMap.put(player.getId(), player);
            }
        }
        List<Player> directPlayers = this.playerMapper.selectList(
                Wrappers.lambdaQuery(Player.class)
                        .eq(Player::getAgencyId, parentPlayerId)
                        .and(w -> w.eq(Player::getDelFlag, 0).or().isNull(Player::getDelFlag)));
        for (Player player : directPlayers) {
            playerMap.put(player.getId(), player);
        }

        List<AgencyMatchPlayerDTO> rows = new ArrayList<>();
        if (includeSelf) {
            Player selfPlayer = this.playerMapper.selectById(currentAgency.getPlayerId());
            if (selfPlayer != null) {
                AgencyMatchPlayerDTO row = toMatchPlayerDTO(selfPlayer, currentAgency,
                        StringUtils.nvl(currentAgency.getSuperiorId(), Agency.ROOT_PLAYER_ID));
                if (matchesMatchKeyword(row, keyword)) {
                    rows.add(row);
                }
            }
        }
        for (Agency agency : directAgencies) {
            Player player = playerMap.get(agency.getPlayerId());
            if (player == null) {
                continue;
            }
            AgencyMatchPlayerDTO row = toMatchPlayerDTO(player, agency, parentPlayerId);
            if (matchesMatchKeyword(row, keyword)) {
                rows.add(row);
            }
        }
        for (Player player : playerMap.values()) {
            if (agencyMap.containsKey(player.getId()) || currentAgency.getPlayerId().equals(player.getId())) {
                continue;
            }
            AgencyMatchPlayerDTO row = toMatchPlayerDTO(player, null, parentPlayerId);
            if (matchesMatchKeyword(row, keyword)) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(AgencyMatchPlayerDTO::getRole, Comparator.nullsLast(String::compareTo))
                .thenComparing(AgencyMatchPlayerDTO::getPlayerId, Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    private List<AgencyMatchPlayerDTO> queryMatchSharePlayers(Agency currentAgency, AgencyMatchQueryDTO dto, String feeType) {
        String keyword = StringUtils.trim(dto.getKeyword()).toLowerCase(Locale.ROOT);
        String currentPath = resolveAgencyPath(currentAgency);
        List<Agency> agencies = this.baseMapper.selectList(
                Wrappers.lambdaQuery(Agency.class)
                        .and(w -> w.eq(Agency::getPlayerId, currentAgency.getPlayerId())
                                .or().likeRight(Agency::getPath, currentPath))
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus))
                        .orderByAsc(Agency::getDepth).orderByAsc(Agency::getId));
        if (agencies.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> agencyPlayerIds = agencies.stream().map(Agency::getPlayerId).collect(java.util.stream.Collectors.toList());
        Map<String, Player> playerMap = new LinkedHashMap<>();
        List<Player> players = this.playerMapper.selectBatchIds(agencyPlayerIds);
        for (Player player : players) {
            playerMap.put(player.getId(), player);
        }

        List<AgencyMatchPlayerDTO> rows = new ArrayList<>();
        for (Agency agency : agencies) {
            Player player = playerMap.get(agency.getPlayerId());
            if (player == null) {
                continue;
            }
            String parentId = StringUtils.nvl(agency.getSuperiorId(), Agency.ROOT_PLAYER_ID);
            AgencyMatchPlayerDTO row = toMatchPlayerDTO(player, agency, parentId);
            if (matchesMatchKeyword(row, keyword)) {
                rows.add(row);
            }
        }
        fillShareCommission(rows, parseDate(dto.getDate()), feeType);
        rows.sort(Comparator.comparing((AgencyMatchPlayerDTO row) -> currentAgency.getPlayerId().equals(row.getPlayerId()) ? 0 : 1)
                .thenComparing(AgencyMatchPlayerDTO::getLevel, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AgencyMatchPlayerDTO::getPlayerId, Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    private void fillShareCommission(List<AgencyMatchPlayerDTO> rows, LocalDate date, String feeType) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> agentPlayerIds = rows.stream()
                .map(AgencyMatchPlayerDTO::getPlayerId)
                .filter(StringUtils::isNotEmpty)
                .collect(java.util.stream.Collectors.toList());
        if (agentPlayerIds.isEmpty()) {
            return;
        }
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        LambdaQueryWrapper<AgencyCommissionLedger> wrapper = Wrappers.lambdaQuery(AgencyCommissionLedger.class)
                .in(AgencyCommissionLedger::getAgentPlayerId, agentPlayerIds)
                .ne(AgencyCommissionLedger::getStatus, AgencyCommissionLedger.STATUS_REVERSED)
                .ge(AgencyCommissionLedger::getCreateTime, start)
                .lt(AgencyCommissionLedger::getCreateTime, end);
        if (StringUtils.isNotEmpty(feeType)) {
            wrapper.eq(AgencyCommissionLedger::getFeeType, feeType);
        } else {
            wrapper.and(w -> w.ne(AgencyCommissionLedger::getFeeType, RoomFeeLedger.FEE_TYPE_SHUFFLE)
                    .or().isNull(AgencyCommissionLedger::getFeeType));
        }
        List<AgencyCommissionLedger> ledgers = this.agencyCommissionLedgerMapper.selectList(wrapper);
        Map<String, Long> feeMap = new HashMap<>();
        Map<String, Long> commissionMap = new HashMap<>();
        for (AgencyCommissionLedger ledger : ledgers) {
            String agentPlayerId = ledger.getAgentPlayerId();
            feeMap.put(agentPlayerId, feeMap.getOrDefault(agentPlayerId, 0L) + safeLong(ledger.getFeeAmount()));
            commissionMap.put(agentPlayerId, commissionMap.getOrDefault(agentPlayerId, 0L) + safeLong(ledger.getCommissionAmount()));
        }
        for (AgencyMatchPlayerDTO row : rows) {
            row.setFeeAmount(feeMap.getOrDefault(row.getPlayerId(), 0L));
            row.setCommissionAmount(commissionMap.getOrDefault(row.getPlayerId(), 0L));
        }
    }

    private List<String> matchScopePlayerIds(Agency currentAgency, AgencyMatchQueryDTO dto) {
        List<String> playerIds = new ArrayList<>();
        for (AgencyMatchPlayerDTO row : queryMatchPlayers(currentAgency, dto)) {
            playerIds.add(row.getPlayerId());
        }
        return playerIds;
    }

    private AgencyMatchPlayerDTO toMatchPlayerDTO(Player player, Agency agency, String parentPlayerId) {
        AgencyMatchPlayerDTO dto = new AgencyMatchPlayerDTO();
        dto.setPlayerId(player.getId());
        dto.setNickname(player.getNickname());
        dto.setAccount(player.getName());
        dto.setAvatar(player.getAvatar());
        dto.setRole(agency == null ? MEMBER_TYPE_PLAYER : MEMBER_TYPE_AGENT);
        dto.setRoleText(agency == null ? "成员" : "合伙人");
        dto.setIdentity(agency == null ? "成员" : "合伙人" + Math.max(1, safeInt(agency.getDepth())));
        dto.setAgentType(agency != null ? agency.getAgentType() : null);
        dto.setLevel(agency != null ? agency.getLevel() : 0);
        dto.setJuniorCount(agency != null ? safeInt(agency.getJuniorCount()) : 0);
        dto.setParentPlayerId(parentPlayerId);
        dto.setParentNickname(nickname(parentPlayerId));
        dto.setScore(walletService.getBalance(player.getId(), WalletType.GOLD.getCode()));
        dto.setCommissionRateBp(agency != null ? agency.getCommissionRateBp() : 0);
        return dto;
    }

    private boolean matchesMatchKeyword(AgencyMatchPlayerDTO row, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return true;
        }
        return containsIgnoreCase(row.getPlayerId(), keyword)
                || containsIgnoreCase(row.getNickname(), keyword)
                || containsIgnoreCase(row.getAccount(), keyword);
    }

    private void applyLedgerDate(LambdaQueryWrapper<WalletLedger> wrapper, LocalDate date) {
        wrapper.ge(WalletLedger::getCreateTime, date.atStartOfDay())
                .lt(WalletLedger::getCreateTime, date.plusDays(1).atStartOfDay());
    }

    private void applyLedgerChangeFilter(LambdaQueryWrapper<WalletLedger> wrapper, String rawType) {
        String type = StringUtils.trim(rawType);
        if ("wash".equals(type)) {
            wrapper.eq(WalletLedger::getBizType, LedgerBizType.SHUFFLE_FEE.getCode());
        } else if ("transfer".equals(type)) {
            wrapper.in(WalletLedger::getBizType, Arrays.asList(
                    LedgerBizType.TRANSFER_IN.getCode(),
                    LedgerBizType.TRANSFER_OUT.getCode()));
        } else if ("gift".equals(type) || "admin_up".equals(type)) {
            wrapper.eq(WalletLedger::getBizType, LedgerBizType.ADMIN_ADJUST.getCode())
                    .gt(WalletLedger::getChangeAmount, 0);
        } else if ("admin_down".equals(type)) {
            wrapper.eq(WalletLedger::getBizType, LedgerBizType.ADMIN_ADJUST.getCode())
                    .lt(WalletLedger::getChangeAmount, 0);
        } else if ("winlose".equals(type)) {
            wrapper.eq(WalletLedger::getBizType, LedgerBizType.GAME_SETTLE.getCode());
        }
    }

    private AgencyMatchLedgerDTO toMatchLedgerDTO(WalletLedger ledger) {
        Player player = this.playerMapper.selectById(ledger.getUserId());
        Agency agency = findAgency(ledger.getUserId());
        String parentId = agency != null ? agency.getSuperiorId() : (player != null ? player.getAgencyId() : "");
        AgencyMatchLedgerDTO dto = new AgencyMatchLedgerDTO();
        dto.setPlayerId(ledger.getUserId());
        dto.setNickname(player != null ? player.getNickname() : nickname(ledger.getUserId()));
        dto.setAccount(player != null ? player.getName() : null);
        dto.setParentPlayerId(parentId);
        dto.setParentNickname(nickname(parentId));
        dto.setScore(walletService.getBalance(ledger.getUserId(), WalletType.GOLD.getCode()));
        dto.setMatchScore(ledger.getChangeAmount());
        dto.setBalanceAfter(ledger.getBalanceAfter());
        dto.setChangeType(safeLong(ledger.getChangeAmount()) >= 0 ? "增加" : "减少");
        dto.setBizType(ledger.getBizType());
        dto.setBizTypeText(bizTypeText(ledger.getBizType()));
        dto.setGameName(gameNameText(ledger));
        dto.setRemark(ledger.getRemark());
        dto.setTime(ledger.getCreateTime());
        return dto;
    }

    private AgencyMatchOperationLogDTO toOperationLogDTO(AgencyWalletAdjustLog logEntity) {
        AgencyMatchOperationLogDTO dto = new AgencyMatchOperationLogDTO();
        dto.setOperatorPlayerId(logEntity.getOperatorAgentPlayerId());
        dto.setOperatorNickname(nickname(logEntity.getOperatorAgentPlayerId()));
        dto.setTargetPlayerId(logEntity.getTargetPlayerId());
        dto.setTargetNickname(nickname(logEntity.getTargetPlayerId()));
        dto.setChangeAmount(logEntity.getChangeAmount());
        dto.setChangeType(safeLong(logEntity.getChangeAmount()) >= 0 ? "积分上分" : "积分下分");
        dto.setReason(logEntity.getReason());
        dto.setStatus(logEntity.getStatus());
        dto.setTime(logEntity.getCreateTime());
        return dto;
    }

    private boolean matchesOperationKeyword(AgencyMatchOperationLogDTO row, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return true;
        }
        return containsIgnoreCase(row.getOperatorPlayerId(), keyword)
                || containsIgnoreCase(row.getOperatorNickname(), keyword)
                || containsIgnoreCase(row.getTargetPlayerId(), keyword)
                || containsIgnoreCase(row.getTargetNickname(), keyword);
    }

    private boolean isPlayerIdInCurrentScope(Agency currentAgency, String playerId) {
        if (StringUtils.isEmpty(playerId)) {
            return false;
        }
        Player player = this.playerMapper.selectById(playerId);
        return player != null && isPlayerInCurrentScope(currentAgency, player);
    }

    private LocalDate parseDate(String value) {
        if (StringUtils.isEmpty(value)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return LocalDate.now();
        }
    }

    private String limitClause(int pageNum, int pageSize) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        return "LIMIT " + offset + ", " + pageSize;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private LoginPlayer requireLoginPlayer() {
        LoginPlayer loginPlayer = PlayerSecurityUtils.getLoginPlayer();
        if (loginPlayer == null) {
            throw new InternalServerException("Current login player is null, this is unexpected");
        }
        return loginPlayer;
    }

    private AjaxResult buildIncomeBoxResult(String playerId, Agency agency, Long amount, Long walletLedgerId,
                                            Long beforeGold, Long afterGold) {
        long pendingCommission = sumAvailableCommission(playerId, AgencyCommissionLedger.STATUS_PENDING, null);
        long depositSettledUncollected = sumDepositSettledUncollectedCommission(playerId, null);
        Long legacyReward = this.baseMapper.getCurrentReward(playerId);
        long legacyPending = safeLong(legacyReward);
        long withdrawableCommission = pendingCommission + depositSettledUncollected;
        long availableBalance = withdrawableCommission + legacyPending;
        long pendingTodayCommission = sumAvailableCommission(playerId, AgencyCommissionLedger.STATUS_PENDING, LocalDate.now());
        long depositSettledTodayUncollected = sumDepositSettledUncollectedCommission(playerId, LocalDate.now());
        long todayCommission = sumCommission(playerId, null, LocalDate.now());
        long totalCommission = sumCommission(playerId, null, null);
        Long totalReward = agency != null ? agency.getTotalReward() : this.baseMapper.getTotalReward1(playerId);
        if (totalReward == null) {
            totalReward = this.baseMapper.getTotalReward1(playerId);
        }

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", playerId);
        ajax.put("balance", availableBalance);
        ajax.put("pendingAmount", withdrawableCommission);
        ajax.put("pendingLedgerAmount", pendingCommission);
        ajax.put("depositSettledAmount", depositSettledUncollected);
        ajax.put("prepaidAmount", depositSettledUncollected);
        ajax.put("legacyReward", legacyPending);
        ajax.put("todayCommission", todayCommission);
        ajax.put("todayPendingCommission", pendingTodayCommission);
        ajax.put("todayDepositSettledAmount", depositSettledTodayUncollected);
        ajax.put("availableTodayCommission", pendingTodayCommission + depositSettledTodayUncollected);
        ajax.put("totalCommission", totalCommission + legacyPending);
        ajax.put("claimedAmount", safeLong(totalReward));
        ajax.put("amount", safeLong(amount));
        if (walletLedgerId != null) {
            ajax.put("ledgerId", walletLedgerId);
            ajax.put("walletLedgerId", walletLedgerId);
            ajax.put("withdrawnAmount", safeLong(amount));
        }
        if (beforeGold != null) {
            ajax.put("beforeGold", beforeGold);
        }
        if (afterGold != null) {
            ajax.put("afterGold", afterGold);
        }
        List<CollectRecordDTO> withdrawRecords = this.baseMapper.getCollectRecord(playerId, 0, 20);
        ajax.put("records", withdrawRecords);
        ajax.put("withdrawRecords", withdrawRecords);
        ajax.put("incomeDetails", buildIncomeDetails(queryRecentIncomeLedgers(playerId)));
        ajax.put("gold", walletService.getBalance(playerId, WalletType.GOLD.getCode()));
        ajax.put("deposit", walletService.getBalance(playerId, WalletType.DEPOSIT.getCode()));
        return ajax;
    }

    private List<AgencyCommissionLedger> queryPendingIncomeLedgers(String playerId) {
        return this.agencyCommissionLedgerMapper.selectList(
                Wrappers.lambdaQuery(AgencyCommissionLedger.class)
                        .eq(AgencyCommissionLedger::getAgentPlayerId, playerId)
                        .eq(AgencyCommissionLedger::getStatus, AgencyCommissionLedger.STATUS_PENDING)
                        .apply("commission_amount - ifnull(collected_amount, 0) > 0")
                        .orderByAsc(AgencyCommissionLedger::getId)
                        .last("FOR UPDATE"));
    }

    private List<AgencyCommissionLedger> queryDepositSettledUncollectedIncomeLedgers(String playerId, LocalDate startDate,
                                                                                     LocalDate endDate, boolean forUpdate) {
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.atStartOfDay();
        return this.agencyCommissionLedgerMapper.selectDepositSettledUncollected(
                playerId, startTime, endTime, forUpdate);
    }

    private List<AgencyCommissionLedger> queryRecentIncomeLedgers(String playerId) {
        return this.agencyCommissionLedgerMapper.selectList(
                Wrappers.lambdaQuery(AgencyCommissionLedger.class)
                        .eq(AgencyCommissionLedger::getAgentPlayerId, playerId)
                        .ne(AgencyCommissionLedger::getStatus, AgencyCommissionLedger.STATUS_REVERSED)
                        .gt(AgencyCommissionLedger::getCommissionAmount, 0L)
                        .orderByDesc(AgencyCommissionLedger::getId)
                        .last("LIMIT 50"));
    }

    private List<Map<String, Object>> buildIncomeDetails(List<AgencyCommissionLedger> ledgers) {
        List<Map<String, Object>> details = new ArrayList<>();
        if (ledgers == null || ledgers.isEmpty()) {
            return details;
        }
        for (AgencyCommissionLedger ledger : ledgers) {
            Map<String, Object> item = new LinkedHashMap<>();
            String feeType = StringUtils.isNotEmpty(ledger.getFeeType())
                    ? ledger.getFeeType()
                    : RoomFeeLedger.FEE_TYPE_GAME_ROOM;
            item.put("id", ledger.getId());
            Integer gameType = resolveIncomeGameType(ledger);
            String gameName = gameNameText(gameType);
            String sourcePlayerName = nickname(ledger.getFeePlayerId());
            String sourceTypeText = RoomFeeLedger.FEE_TYPE_SHUFFLE.equals(feeType) ? "洗牌分分成" : "房费分成";
            item.put("sourceType", feeType);
            item.put("sourceTypeText", sourceTypeText);
            item.put("gameType", gameType);
            item.put("gameName", gameName);
            item.put("roomId", ledger.getRoomId());
            item.put("feePlayerId", ledger.getFeePlayerId());
            item.put("feePlayerNickname", sourcePlayerName);
            item.put("sourcePlayerId", ledger.getFeePlayerId());
            item.put("sourcePlayerName", sourcePlayerName);
            item.put("feeAmount", safeLong(ledger.getFeeAmount()));
            item.put("commissionAmount", safeLong(ledger.getCommissionAmount()));
            item.put("collectedAmount", safeLong(ledger.getCollectedAmount()));
            item.put("availableAmount", availableIncomeAmount(ledger));
            item.put("status", ledger.getStatus());
            item.put("statusText", incomeLedgerStatusText(ledger));
            item.put("time", formatIncomeTime(ledger.getCreateTime()));
            item.put("remark", ledger.getRemark());
            item.put("sourceInfo", buildIncomeSourceInfo(ledger, feeType, gameName, sourcePlayerName));
            details.add(item);
        }
        return details;
    }

    private String incomeLedgerStatusText(AgencyCommissionLedger ledger) {
        if (ledger == null) {
            return "";
        }
        long availableAmount = availableIncomeAmount(ledger);
        long collectedAmount = safeLong(ledger.getCollectedAmount());
        if (availableAmount <= 0L) {
            return "已提取";
        }
        if (collectedAmount > 0L) {
            return "部分提取";
        }
        if (AgencyCommissionLedger.STATUS_PENDING.equals(ledger.getStatus())
                || isDepositSettledUncollectedIncome(ledger)) {
            return "待提取";
        }
        return "已提取";
    }

    private boolean isDepositSettledUncollectedIncome(AgencyCommissionLedger ledger) {
        if (ledger == null
                || !AgencyCommissionLedger.STATUS_SETTLED.equals(ledger.getStatus())
                || ledger.getWalletLedgerId() == null
                || availableIncomeAmount(ledger) <= 0L) {
            return false;
        }
        WalletLedger walletLedger = this.walletLedgerMapper.selectById(ledger.getWalletLedgerId());
        return walletLedger != null
                && WalletType.DEPOSIT.getCode().equals(walletLedger.getWalletType())
                && safeLong(walletLedger.getChangeAmount()) > 0L;
    }

    private String buildIncomeSourceInfo(AgencyCommissionLedger ledger, String feeType, String gameName, String sourcePlayerName) {
        StringBuilder builder = new StringBuilder();
        builder.append(RoomFeeLedger.FEE_TYPE_SHUFFLE.equals(feeType) ? "洗牌分" : "房费");
        if (StringUtils.isNotEmpty(gameName)) {
            builder.append(" | 游戏:").append(gameName);
        }
        if (StringUtils.isNotEmpty(ledger.getRoomId())) {
            builder.append(" | 房间:").append(ledger.getRoomId());
        }
        if (StringUtils.isNotEmpty(sourcePlayerName)) {
            builder.append(" | 来源玩家:").append(sourcePlayerName);
        }
        builder.append(" | 原始积分:").append(safeLong(ledger.getFeeAmount()));
        return builder.toString();
    }

    private Integer resolveIncomeGameType(AgencyCommissionLedger ledger) {
        if (ledger == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(ledger.getRoomId())) {
            try {
                Integer gameType = venueMapper.getGameType(ledger.getRoomId());
                if (gameType != null) {
                    return gameType;
                }
            } catch (Exception ex) {
                log.warn("[收益箱] 查询房间游戏类型失败: roomId={}", ledger.getRoomId(), ex);
            }
        }
        return parseGameTypeFromRemark(ledger.getRemark());
    }

    private Integer parseGameTypeFromRemark(String remark) {
        if (StringUtils.isEmpty(remark)) {
            return null;
        }
        if (containsAny(remark, "TaoJiangMahjong", "桃江麻将")) return NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG;
        if (containsAny(remark, "HongZhongMahjong", "红中麻将")) return NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG;
        if (containsAny(remark, "PaoDeKuai", "跑得快")) return NiuMaConstants.GAME_TYPE_PAO_DE_KUAI;
        if (containsAny(remark, "ChangShaMahjong", "长沙麻将")) return NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG;
        if (containsAny(remark, "YiYangWaiHuZi", "益阳歪胡子")) return NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI;
        if (containsAny(remark, "YuanJiangQianFen", "沅江千分")) return NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN;
        if (containsAny(remark, "GuanDan", "掼蛋")) return NiuMaConstants.GAME_TYPE_GUAN_DAN;
        if (containsAny(remark, "DouDiZhu", "斗地主")) return NiuMaConstants.GAME_TYPE_DOU_DI_ZHU;
        return null;
    }

    private boolean containsAny(String source, String... keys) {
        if (StringUtils.isEmpty(source) || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (StringUtils.isNotEmpty(key) && source.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String gameNameText(Integer gameType) {
        if (gameType == null) return "";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_MAHJONG)) return "麻将";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU)) return "斗地主";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_NIU_NIU_100)) return "百人牛牛";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_BI_JI)) return "六安比鸡";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_LACKEY)) return "逮狗腿";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_GUAN_DAN)) return "掼蛋";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG)) return "桃江麻将";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG)) return "红中麻将";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI)) return "跑得快";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG)) return "长沙麻将";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI)) return "益阳歪胡子";
        if (gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN)) return "沅江千分";
        return "游戏" + gameType;
    }

    private String formatIncomeTime(LocalDateTime time) {
        return time == null ? "" : time.format(INCOME_TIME_FORMATTER);
    }

    private long sumCommission(String playerId, String status, LocalDate date) {
        LambdaQueryWrapper<AgencyCommissionLedger> wrapper = Wrappers.lambdaQuery(AgencyCommissionLedger.class)
                .eq(AgencyCommissionLedger::getAgentPlayerId, playerId)
                .ne(AgencyCommissionLedger::getStatus, AgencyCommissionLedger.STATUS_REVERSED);
        if (StringUtils.isNotEmpty(status)) {
            wrapper.eq(AgencyCommissionLedger::getStatus, status);
        }
        if (date != null) {
            wrapper.ge(AgencyCommissionLedger::getCreateTime, date.atStartOfDay())
                    .lt(AgencyCommissionLedger::getCreateTime, date.plusDays(1).atStartOfDay());
        }
        List<AgencyCommissionLedger> ledgers = this.agencyCommissionLedgerMapper.selectList(wrapper);
        long total = 0L;
        for (AgencyCommissionLedger ledger : ledgers) {
            total += safeLong(ledger.getCommissionAmount());
        }
        return total;
    }

    private long sumAvailableCommission(String playerId, String status, LocalDate date) {
        LambdaQueryWrapper<AgencyCommissionLedger> wrapper = Wrappers.lambdaQuery(AgencyCommissionLedger.class)
                .eq(AgencyCommissionLedger::getAgentPlayerId, playerId)
                .ne(AgencyCommissionLedger::getStatus, AgencyCommissionLedger.STATUS_REVERSED)
                .apply("commission_amount - ifnull(collected_amount, 0) > 0");
        if (StringUtils.isNotEmpty(status)) {
            wrapper.eq(AgencyCommissionLedger::getStatus, status);
        }
        if (date != null) {
            wrapper.ge(AgencyCommissionLedger::getCreateTime, date.atStartOfDay())
                    .lt(AgencyCommissionLedger::getCreateTime, date.plusDays(1).atStartOfDay());
        }
        return sumIncomeLedgers(this.agencyCommissionLedgerMapper.selectList(wrapper));
    }

    private long sumDepositSettledUncollectedCommission(String playerId, LocalDate date) {
        LocalDate endDate = date == null ? null : date.plusDays(1);
        return sumIncomeLedgers(queryDepositSettledUncollectedIncomeLedgers(playerId, date, endDate, false));
    }

    private long sumIncomeLedgers(List<AgencyCommissionLedger> ledgers) {
        long total = 0L;
        if (ledgers == null || ledgers.isEmpty()) {
            return total;
        }
        for (AgencyCommissionLedger ledger : ledgers) {
            total += availableIncomeAmount(ledger);
        }
        return total;
    }

    private long availableIncomeAmount(AgencyCommissionLedger ledger) {
        if (ledger == null) {
            return 0L;
        }
        return Math.max(0L, safeLong(ledger.getCommissionAmount()) - safeLong(ledger.getCollectedAmount()));
    }

    private long sumLegacyRewards(List<LongPairDTO> rewards) {
        long total = 0L;
        if (rewards == null || rewards.isEmpty()) {
            return total;
        }
        for (LongPairDTO pair : rewards) {
            if (pair != null) {
                total += safeLong(pair.getValue2());
            }
        }
        return total;
    }

    private long resolveWithdrawAmount(Long requestedAmount, long availableTotal) {
        if (availableTotal <= 0L) {
            return 0L;
        }
        if (requestedAmount == null) {
            return availableTotal;
        }
        long amount = safeLong(requestedAmount);
        if (amount <= 0L) {
            throw new BadRequestException("提取数量必须大于0");
        }
        if (amount > availableTotal) {
            throw new BadRequestException("提取数量不能超过可提取收益");
        }
        return amount;
    }

    private void consumeIncomeLedgers(List<AgencyCommissionLedger> ledgers, long amount, Long collectId,
                                      Long walletLedgerId, boolean setWalletLedgerOnFull,
                                      boolean appendCollectedRemark) {
        long remaining = amount;
        if (remaining <= 0L) {
            return;
        }
        for (AgencyCommissionLedger ledger : ledgers) {
            if (remaining <= 0L) {
                break;
            }
            long available = availableIncomeAmount(ledger);
            if (available <= 0L) {
                continue;
            }
            long consumed = Math.min(available, remaining);
            long collected = safeLong(ledger.getCollectedAmount()) + consumed;
            ledger.setCollectedAmount(collected);
            if (collected >= safeLong(ledger.getCommissionAmount())) {
                ledger.setCollectId(collectId);
                ledger.setStatus(AgencyCommissionLedger.STATUS_SETTLED);
                if (setWalletLedgerOnFull && walletLedgerId != null) {
                    ledger.setWalletLedgerId(walletLedgerId);
                }
                if (appendCollectedRemark) {
                    ledger.setRemark(appendIncomeBoxCollectedRemark(ledger.getRemark()));
                }
            } else {
                ledger.setCollectId(null);
            }
            this.agencyCommissionLedgerMapper.updateById(ledger);
            remaining -= consumed;
        }
        if (remaining > 0L) {
            throw new BadRequestException("可提取收益发生变化，请刷新后重试");
        }
    }

    private void consumeLegacyRewards(List<LongPairDTO> rewards, long amount, Long collectId) {
        long remaining = amount;
        if (remaining <= 0L) {
            return;
        }
        List<Long> collectedIds = new ArrayList<>();
        if (rewards != null) {
            for (LongPairDTO pair : rewards) {
                if (remaining <= 0L) {
                    break;
                }
                if (pair == null || pair.getValue1() == null) {
                    continue;
                }
                long rewardAmount = safeLong(pair.getValue2());
                if (rewardAmount <= 0L) {
                    continue;
                }
                if (rewardAmount <= remaining) {
                    collectedIds.add(pair.getValue1());
                    remaining -= rewardAmount;
                } else {
                    int rows = this.baseMapper.reduceRewardAmount(pair.getValue1(), remaining);
                    if (rows == 0) {
                        throw new BadRequestException("可提取收益发生变化，请刷新后重试");
                    }
                    remaining = 0L;
                }
            }
        }
        if (!collectedIds.isEmpty()) {
            this.baseMapper.collectRewards(collectedIds, collectId);
        }
        if (remaining > 0L) {
            throw new BadRequestException("可提取收益发生变化，请刷新后重试");
        }
    }

    private String appendIncomeBoxCollectedRemark(String remark) {
        String collected = "收益箱已提取";
        if (StringUtils.isNotEmpty(remark) && remark.contains(collected)) {
            return remark;
        }
        String prefix = StringUtils.isNotEmpty(remark) ? remark + " | " : "";
        String result = prefix + collected;
        return result.length() > 255 ? result.substring(0, 255) : result;
    }

    private String bizTypeText(String value) {
        if (LedgerBizType.ADMIN_ADJUST.getCode().equals(value)) {
            return "操作上下分";
        }
        if (LedgerBizType.GAME_SETTLE.getCode().equals(value)) {
            return "输赢分";
        }
        if (LedgerBizType.TRANSFER_IN.getCode().equals(value) || LedgerBizType.TRANSFER_OUT.getCode().equals(value)) {
            return "转移分";
        }
        if (LedgerBizType.AGENCY_COMMISSION.getCode().equals(value)) {
            return "分成";
        }
        if (LedgerBizType.SHUFFLE_FEE.getCode().equals(value)) {
            return "洗牌分";
        }
        return StringUtils.nvl(value, "-");
    }

    private String gameNameText(WalletLedger ledger) {
        if (LedgerBizType.GAME_SETTLE.getCode().equals(ledger.getBizType())) {
            return "比赛房";
        }
        return "-";
    }

    private <T> PageResult<T> pageList(List<T> rows, PageBody dto) {
        int total = rows == null ? 0 : rows.size();
        int from = Math.max(0, (dto.getPageNum() - 1) * dto.getPageSize());
        int to = Math.min(total, from + dto.getPageSize());
        List<T> records = from >= total || rows == null ? Collections.emptyList() : new ArrayList<>(rows.subList(from, to));
        return new PageResult<>(records, dto.getPageNum(), total);
    }

    private void validatePage(PageBody dto) {
        if (dto.getPageNum() < 1) {
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        }
        if (dto.getPageSize() < 1) {
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        }
    }

    private Agency requireEnabledAgency(String playerId) {
        Agency agency = this.baseMapper.selectOne(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getPlayerId, playerId)
                        .last("LIMIT 1"));
        if (agency == null || (agency.getStatus() != null && agency.getStatus() != Agency.STATUS_NORMAL)) {
            throw new ForbiddenException(NiuMaCodeEnum.AGENCY_ERROR.getCode(), "当前玩家不是有效代理");
        }
        return agency;
    }

    private String normalizeTargetPlayerId(String playerId) {
        String targetPlayerId = StringUtils.trim(playerId);
        if (StringUtils.isEmpty(targetPlayerId)) {
            throw new BadRequestException("玩家ID不能为空");
        }
        return targetPlayerId;
    }

    private int normalizeRate(Integer rate, int parentRate) {
        int value = rate == null ? 0 : rate;
        if (value < 0 || value > 10000) {
            throw new BadRequestException("分佣比例必须在0-10000之间");
        }
        if (value > parentRate) {
            throw new ForbiddenException("不能设置超过当前被设置的佣金比例");
        }
        return value;
    }

    private int resolveRate(Agency agency) {
        if (agency == null) {
            return 10000;
        }
        return agency.getCommissionRateBp() == null ? 0 : agency.getCommissionRateBp();
    }

    private Agency findAgency(String playerId) {
        if (StringUtils.isEmpty(playerId)) {
            return null;
        }
        return this.baseMapper.selectOne(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getPlayerId, playerId)
                        .last("LIMIT 1"));
    }

    private PlayerAgentBind findActiveBind(String playerId) {
        return this.playerAgentBindMapper.selectOne(
                Wrappers.lambdaQuery(PlayerAgentBind.class)
                        .eq(PlayerAgentBind::getPlayerId, playerId)
                        .eq(PlayerAgentBind::getStatus, PlayerAgentBind.STATUS_ACTIVE)
                        .last("LIMIT 1"));
    }

    private Map<String, PlayerAgentBind> findActiveDirectBindMap(String agentPlayerId) {
        if (StringUtils.isEmpty(agentPlayerId)) {
            return Collections.emptyMap();
        }
        List<PlayerAgentBind> binds = this.playerAgentBindMapper.selectList(
                Wrappers.lambdaQuery(PlayerAgentBind.class)
                        .eq(PlayerAgentBind::getAgentPlayerId, agentPlayerId)
                        .eq(PlayerAgentBind::getStatus, PlayerAgentBind.STATUS_ACTIVE));
        Map<String, PlayerAgentBind> result = new HashMap<>();
        for (PlayerAgentBind bind : binds) {
            result.put(bind.getPlayerId(), bind);
        }
        return result;
    }

    private PlayerAgentBind ensureRemarkBind(Agency currentAgency, Player targetPlayer) {
        PlayerAgentBind activeBind = findActiveBind(targetPlayer.getId());
        if (activeBind != null) {
            Agency bindAgent = findAgency(activeBind.getAgentPlayerId());
            if (bindAgent == null || (bindAgent.getStatus() != null && bindAgent.getStatus() != Agency.STATUS_NORMAL)) {
                throw new ForbiddenException("成员绑定的上级代理无效");
            }
            ensureInCurrentAgencyScope(currentAgency, bindAgent);
            return activeBind;
        }

        String agentPlayerId = StringUtils.trim(targetPlayer.getAgencyId());
        if (StringUtils.isEmpty(agentPlayerId) || Agency.ROOT_PLAYER_ID.equals(agentPlayerId)) {
            throw new BadRequestException("该成员没有有效直属上级，不能设置备注");
        }
        Agency directAgency = requireEnabledAgency(agentPlayerId);
        ensureInCurrentAgencyScope(currentAgency, directAgency);

        PlayerAgentBind bind = new PlayerAgentBind();
        bind.setPlayerId(targetPlayer.getId());
        bind.setAgentPlayerId(agentPlayerId);
        bind.setRootAgentPlayerId(rootAgentId(directAgency));
        bind.setBindSource("member_remark");
        bind.setPathSnapshot(resolveAgencyPath(directAgency) + targetPlayer.getId() + "/");
        bind.setStatus(PlayerAgentBind.STATUS_ACTIVE);
        bind.setBindAt(LocalDateTime.now());
        this.playerAgentBindMapper.insert(bind);
        return bind;
    }

    private String normalizeMemberRemark(String remark) {
        String value = StringUtils.trim(remark);
        if (StringUtils.isEmpty(value)) {
            return "";
        }
        if (value.codePointCount(0, value.length()) > 10) {
            throw new BadRequestException("备注不能超过10个字");
        }
        return value;
    }

    private String resolveMemberRemark(Agency agency, PlayerAgentBind bind) {
        String remark = agency != null ? agency.getMemberRemark() : null;
        if (StringUtils.isEmpty(remark) && bind != null) {
            remark = bind.getMemberRemark();
        }
        return StringUtils.nvl(remark, "");
    }

    private void ensureInCurrentAgencyScope(Agency currentAgency, Agency targetAgency) {
        if (currentAgency.getPlayerId().equals(targetAgency.getPlayerId())) {
            return;
        }
        String currentPath = resolveAgencyPath(currentAgency);
        String targetPath = resolveAgencyPath(targetAgency);
        if (!targetPath.startsWith(currentPath)) {
            throw new ForbiddenException("不能查看当前代理线路外的成员");
        }
    }

    private Player requireScopedPlayer(Agency currentAgency, String targetPlayerId) {
        Player player = this.playerMapper.selectById(targetPlayerId);
        if (player == null) {
            throw new NotFoundException("玩家不存在: " + targetPlayerId);
        }
        if (CommonUtils.predicate(player.getDelFlag())) {
            throw new ForbiddenException("玩家已删除");
        }
        if (!isPlayerInCurrentScope(currentAgency, player)) {
            throw new ForbiddenException("不能操作当前代理线路外的成员");
        }
        return player;
    }

    private boolean isPlayerInCurrentScope(Agency currentAgency, Player player) {
        if (currentAgency == null || player == null) {
            return false;
        }
        if (currentAgency.getPlayerId().equals(player.getId())) {
            return true;
        }
        Agency targetAgency = findAgency(player.getId());
        if (targetAgency != null && isAgencyInCurrentScope(currentAgency, targetAgency)) {
            return true;
        }
        PlayerAgentBind activeBind = findActiveBind(player.getId());
        if (activeBind != null && StringUtils.isNotEmpty(activeBind.getAgentPlayerId())) {
            Agency bindAgent = findAgency(activeBind.getAgentPlayerId());
            if (bindAgent != null && isAgencyInCurrentScope(currentAgency, bindAgent)) {
                return true;
            }
        }
        if (StringUtils.isNotEmpty(player.getAgencyId())) {
            Agency parentAgency = findAgency(player.getAgencyId());
            return parentAgency != null && isAgencyInCurrentScope(currentAgency, parentAgency);
        }
        return false;
    }

    private boolean isAgencyInCurrentScope(Agency currentAgency, Agency targetAgency) {
        if (currentAgency.getPlayerId().equals(targetAgency.getPlayerId())) {
            return true;
        }
        String currentPath = resolveAgencyPath(currentAgency);
        String targetPath = resolveAgencyPath(targetAgency);
        return targetPath.startsWith(currentPath);
    }

    private boolean hasActiveChildren(String agentPlayerId) {
        Integer childAgents = this.baseMapper.selectCount(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getSuperiorId, agentPlayerId)
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus)));
        if (CommonUtils.predicate(childAgents)) {
            return true;
        }
        Integer activeBinds = this.playerAgentBindMapper.selectCount(
                Wrappers.lambdaQuery(PlayerAgentBind.class)
                        .eq(PlayerAgentBind::getAgentPlayerId, agentPlayerId)
                        .eq(PlayerAgentBind::getStatus, PlayerAgentBind.STATUS_ACTIVE));
        if (CommonUtils.predicate(activeBinds)) {
            return true;
        }
        Integer legacyPlayers = this.playerMapper.countJuniorPlayer(agentPlayerId);
        return CommonUtils.predicate(legacyPlayers);
    }

    private void adjustJuniorCounts(String agentPlayerId, int delta) {
        String current = agentPlayerId;
        int guard = 0;
        while (StringUtils.isNotEmpty(current) && !Agency.ROOT_PLAYER_ID.equals(current) && guard++ < 64) {
            Agency agency = findAgency(current);
            if (agency == null) {
                break;
            }
            agency.setJuniorCount(Math.max(0, safeInt(agency.getJuniorCount()) + delta));
            this.baseMapper.updateById(agency);
            current = agency.getSuperiorId();
        }
    }

    private String resolveAgencyPath(Agency agency) {
        if (StringUtils.isNotEmpty(agency.getPath())) {
            return agency.getPath();
        }
        return "/" + Agency.ROOT_PLAYER_ID + "/" + agency.getPlayerId() + "/";
    }

    private String rootAgentId(Agency agency) {
        if (agency == null) {
            return Agency.ROOT_PLAYER_ID;
        }
        String path = resolveAgencyPath(agency);
        String prefix = "/" + Agency.ROOT_PLAYER_ID + "/";
        if (path.startsWith(prefix)) {
            String rest = path.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                return rest.substring(0, slash);
            }
        }
        if (Agency.ROOT_PLAYER_ID.equals(agency.getSuperiorId()) || StringUtils.isEmpty(agency.getSuperiorId())) {
            return agency.getPlayerId();
        }
        Agency superior = this.baseMapper.selectOne(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getPlayerId, agency.getSuperiorId())
                        .last("LIMIT 1"));
        return superior == null ? agency.getPlayerId() : rootAgentId(superior);
    }

    private String normalizeMemberType(String type) {
        String value = StringUtils.isNotEmpty(type) ? type.trim().toLowerCase(Locale.ROOT) : MEMBER_TYPE_ALL;
        if (MEMBER_TYPE_AGENT.equals(value) || MEMBER_TYPE_PLAYER.equals(value)) {
            return value;
        }
        return MEMBER_TYPE_ALL;
    }

    private AgencyMemberDTO toAgencyMemberDTO(Player player, Agency agency, String superiorId, PlayerAgentBind bind) {
        AgencyMemberDTO dto = new AgencyMemberDTO();
        dto.setPlayerId(player.getId());
        dto.setNickname(player.getNickname());
        dto.setAccount(player.getName());
        dto.setAvatar(player.getAvatar());
        dto.setRemark(resolveMemberRemark(agency, bind));
        dto.setRole(agency == null ? MEMBER_TYPE_PLAYER : MEMBER_TYPE_AGENT);
        dto.setRoleText(agency == null ? "成员" : "代理");
        dto.setAgentType(agency != null ? agency.getAgentType() : null);
        dto.setLevel(agency != null ? agency.getLevel() : 0);
        dto.setJuniorCount(agency != null ? safeInt(agency.getJuniorCount()) : 0);
        dto.setCommissionRateBp(agency != null ? agency.getCommissionRateBp() : 0);
        dto.setBanned(player.getBanned());
        dto.setSuperiorId(superiorId);
        dto.setSuperiorNickname(nickname(superiorId));
        dto.setLoginTime(player.getLoginDate());
        return dto;
    }

    private boolean matchesKeyword(AgencyMemberDTO member, Player player, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return true;
        }
        return containsIgnoreCase(member.getPlayerId(), keyword)
                || containsIgnoreCase(member.getNickname(), keyword)
                || containsIgnoreCase(player.getName(), keyword)
                || containsIgnoreCase(member.getRemark(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.isNotEmpty(value) && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String nickname(String playerId) {
        if (StringUtils.isEmpty(playerId)) {
            return "";
        }
        if (Agency.ROOT_PLAYER_ID.equals(playerId)) {
            return "平台";
        }
        return StringUtils.nvl(this.playerMapper.getNickname(playerId), playerId);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private PageResult<AgencyMemberDTO> pageMembers(List<AgencyMemberDTO> members, PageBody dto) {
        int total = members.size();
        int from = Math.max(0, (dto.getPageNum() - 1) * dto.getPageSize());
        int to = Math.min(total, from + dto.getPageSize());
        List<AgencyMemberDTO> records = from >= total ? Collections.emptyList() : members.subList(from, to);

        PageResult<AgencyMemberDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }

    @Override
    public PageResult<RewardDTO> getRewards(PageBody dto) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        PageResult<RewardDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        Integer totalNum = this.baseMapper.countReward(player.getId());
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<RewardDTO> records = this.baseMapper.getRewards(player.getId(), offset, dto.getPageSize());
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional
    public AjaxResult collect() {
        AjaxResult ajax = withdrawIncomeBox(null);
        Object amount = ajax.get("amount");
        long collected = amount instanceof Number ? ((Number) amount).longValue() : 0L;
        if (collected <= 0L) {
            throw new ForbiddenException(NiuMaCodeEnum.REWARD_ERROR.getCode(), "You have no reward now");
        }
        return ajax;
    }

    @Override
    public PageResult<CollectRecordDTO> collectRecord(PageBody dto) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        PageResult<CollectRecordDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        Integer totalNum = this.baseMapper.countCollect(player.getId());
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<CollectRecordDTO> records = this.baseMapper.getCollectRecord(player.getId(), offset, dto.getPageSize());
        result.setRecords(records);
        return result;
    }

}
