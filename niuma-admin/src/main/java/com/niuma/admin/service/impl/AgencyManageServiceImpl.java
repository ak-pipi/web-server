package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niuma.admin.dto.*;
import com.niuma.admin.entity.*;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.*;
import com.niuma.admin.service.IAgencyManageService;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.constant.ResultCodeEnum;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.exception.http.ForbiddenException;
import com.niuma.common.exception.http.InternalServerException;
import com.niuma.common.exception.http.NotFoundException;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.CommonUtils;
import com.niuma.common.utils.SecurityUtils;
import com.niuma.common.utils.StringUtils;
import com.niuma.common.utils.ip.IpUtils;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.utils.PlayerSecurityUtils;
import com.niuma.system.domain.SysUserRole;
import com.niuma.system.mapper.SysUserRoleMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台代理管理服务实现。
 */
@Service
@Slf4j
public class AgencyManageServiceImpl implements IAgencyManageService {
    private static final int RATE_FULL = 10000;
    private static final long AGENT_L1_ROLE_ID = 3L;
    private static final long AGENT_L2_ROLE_ID = 4L;

    @Autowired
    private AgencyMapper agencyMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private SysUserAgentMapper sysUserAgentMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private AgencyInviteCodeMapper agencyInviteCodeMapper;

    @Autowired
    private PlayerAgentBindMapper playerAgentBindMapper;

    @Autowired
    private AgencyCommissionLedgerMapper agencyCommissionLedgerMapper;

    @Autowired
    private AgencyUnbindRequestMapper agencyUnbindRequestMapper;

    @Autowired
    private AgencyWalletAdjustLogMapper agencyWalletAdjustLogMapper;

    @Autowired
    private WalletLedgerMapper walletLedgerMapper;

    @Autowired
    private RoomFeeLedgerMapper roomFeeLedgerMapper;

    @Autowired
    private AdminAuditLogMapper adminAuditLogMapper;

    @Autowired
    private GameScoreboardMapper gameScoreboardMapper;

    @Autowired
    private IWalletService walletService;

    @Data
    private static class AgencyScope {
        private boolean admin;
        private Long userId;
        private String username;
        private String agentPlayerId;
        private Integer agentType;
        private String pathPrefix;
        private String rootAgentPlayerId;
    }

    @Override
    public AjaxResult overview() {
        AgencyScope scope = resolveScope();
        List<String> agentIds = getScopeAgentIds(scope);
        Set<String> playerIds = getScopePlayerIds(scope, agentIds);

        LocalDateTime todayStart = LocalDateTime.now().with(LocalTime.MIN);
        List<AgencyCommissionLedger> ledgers = queryCommissionByScope(scope, agentIds, null);
        long totalCommission = 0L;
        long todayCommission = 0L;
        long totalRoomFee = 0L;
        long todayRoomFee = 0L;
        for (AgencyCommissionLedger ledger : ledgers) {
            long commission = nvl(ledger.getCommissionAmount());
            long fee = nvl(ledger.getFeeAmount());
            totalCommission += commission;
            totalRoomFee += fee;
            if (ledger.getCreateTime() != null && !ledger.getCreateTime().isBefore(todayStart)) {
                todayCommission += commission;
                todayRoomFee += fee;
            }
        }

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("agentCount", agentIds.size());
        ajax.put("playerCount", playerIds.size());
        ajax.put("totalRoomFee", totalRoomFee);
        ajax.put("todayRoomFee", todayRoomFee);
        ajax.put("totalCommission", totalCommission);
        ajax.put("todayCommission", todayCommission);
        ajax.put("scopeAgentPlayerId", scope.getAgentPlayerId());
        ajax.put("admin", scope.isAdmin());
        return ajax;
    }

    @Override
    public AjaxResult tree() {
        AgencyScope scope = resolveScope();
        List<Agency> agencies = queryScopeAgencies(scope);
        Map<String, Agency> agencyMap = agencies.stream()
                .collect(Collectors.toMap(Agency::getPlayerId, a -> a, (a, b) -> a));

        Map<String, AgencyTreeNodeDTO> nodeMap = new LinkedHashMap<>();
        String rootNodeId;
        if (scope.isAdmin()) {
            AgencyTreeNodeDTO root = new AgencyTreeNodeDTO();
            root.setId("A:" + Agency.ROOT_PLAYER_ID);
            root.setPlayerId(Agency.ROOT_PLAYER_ID);
            root.setNickname("平台/超级管理员");
            root.setNodeType("root");
            root.setAgentType(Agency.TYPE_ROOT);
            root.setDepth(0);
            root.setCommissionRateBp(RATE_FULL);
            root.setStatus(Agency.STATUS_NORMAL);
            nodeMap.put(root.getId(), root);
            rootNodeId = root.getId();
        } else {
            rootNodeId = "A:" + scope.getAgentPlayerId();
        }

        agencies.sort(Comparator.comparing(a -> safeInt(a.getDepth())));
        for (Agency agency : agencies) {
            AgencyTreeNodeDTO node = toAgentNode(agency);
            String parentId = Agency.ROOT_PLAYER_ID.equals(agency.getSuperiorId()) || StringUtils.isEmpty(agency.getSuperiorId())
                    ? "A:" + Agency.ROOT_PLAYER_ID
                    : "A:" + agency.getSuperiorId();
            node.setParentId(parentId);
            nodeMap.put(node.getId(), node);
        }

        List<String> agentIds = agencies.stream().map(Agency::getPlayerId).collect(Collectors.toList());
        List<PlayerAgentBind> binds = queryActiveBinds(agentIds);
        Set<String> boundPlayerIds = new HashSet<>();
        for (PlayerAgentBind bind : binds) {
            if (agencyMap.containsKey(bind.getPlayerId())) {
                continue;
            }
            AgencyTreeNodeDTO node = toPlayerNode(bind.getPlayerId(), bind.getAgentPlayerId());
            nodeMap.put(node.getId(), node);
            boundPlayerIds.add(bind.getPlayerId());
        }

        List<Player> legacyPlayers = queryLegacyBoundPlayers(agentIds);
        for (Player player : legacyPlayers) {
            if (boundPlayerIds.contains(player.getId()) || agencyMap.containsKey(player.getId())) {
                continue;
            }
            AgencyTreeNodeDTO node = toPlayerNode(player.getId(), player.getAgencyId());
            node.setNickname(player.getNickname());
            nodeMap.put(node.getId(), node);
        }

        List<AgencyTreeNodeDTO> roots = new ArrayList<>();
        for (AgencyTreeNodeDTO node : nodeMap.values()) {
            if (StringUtils.isNotEmpty(node.getParentId()) && nodeMap.containsKey(node.getParentId())) {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            } else if (node.getId().equals(rootNodeId) || scope.isAdmin()) {
                roots.add(node);
            }
        }
        if (!scope.isAdmin() && nodeMap.containsKey(rootNodeId) && !roots.contains(nodeMap.get(rootNodeId))) {
            roots.add(nodeMap.get(rootNodeId));
        }
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("records", roots);
        return ajax;
    }

    @Override
    public PageResult<AgencyListDTO> page(AgencyPageQueryDTO dto) {
        AgencyScope scope = resolveScope();
        List<Agency> agencies = queryScopeAgencies(scope);
        List<AgencyListDTO> records = new ArrayList<>();
        for (Agency agency : agencies) {
            if (dto.getAgentType() != null && !dto.getAgentType().equals(resolveAgentType(agency))) {
                continue;
            }
            if (dto.getStatus() != null && !dto.getStatus().equals(resolveStatus(agency))) {
                continue;
            }
            if (StringUtils.isNotEmpty(dto.getPlayerId()) && !agency.getPlayerId().contains(dto.getPlayerId())) {
                continue;
            }
            String nickname = nickname(agency.getPlayerId());
            if (StringUtils.isNotEmpty(dto.getNickname()) && (nickname == null || !nickname.contains(dto.getNickname()))) {
                continue;
            }
            records.add(toAgencyListDTO(agency, nickname));
        }
        records.sort(Comparator.comparing(AgencyListDTO::getDepth, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AgencyListDTO::getPlayerId));
        return pageList(records, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult createAgency(AgencyCreateDTO dto) {
        AgencyScope scope = resolveScope();
        Player player = requirePlayer(dto.getPlayerId());
        if (isPlayerAgency(dto.getPlayerId())) {
            throw new ForbiddenException("该玩家已经是代理");
        }
        if (CommonUtils.predicate(player.getBanned()) || CommonUtils.predicate(player.getDelFlag())) {
            throw new ForbiddenException("玩家已封禁或删除，不能设置为代理");
        }
        int requestedType = dto.getAgentType();
        if (requestedType != Agency.TYPE_LEVEL_ONE && requestedType != Agency.TYPE_LEVEL_TWO) {
            throw new BadRequestException("代理类型只能为一级代理或二级代理");
        }

        String superiorId;
        int depth;
        int parentRate;
        String parentPath;
        if (requestedType == Agency.TYPE_LEVEL_ONE) {
            if (!scope.isAdmin()) {
                throw new ForbiddenException("只有超级管理员可以设置一级代理");
            }
            PlayerAgentBind currentBind = findActiveBind(dto.getPlayerId());
            if (currentBind != null && !Agency.ROOT_PLAYER_ID.equals(currentBind.getAgentPlayerId())) {
                throw new ForbiddenException("该玩家已绑定代理，请先解除原绑定关系");
            }
            superiorId = Agency.ROOT_PLAYER_ID;
            depth = 1;
            parentRate = RATE_FULL;
            parentPath = "/" + Agency.ROOT_PLAYER_ID + "/";
        } else {
            superiorId = StringUtils.isNotEmpty(dto.getSuperiorPlayerId())
                    ? dto.getSuperiorPlayerId()
                    : scope.getAgentPlayerId();
            Agency superior = requireAgency(superiorId);
            ensureAgencyEnabled(superior);
            if (!scope.isAdmin() && !isAgencyInScope(superior, scope)) {
                throw new ForbiddenException("不能在当前线路外设置下级代理");
            }
            PlayerAgentBind bind = findActiveBind(dto.getPlayerId());
            String currentAgentId = bind != null ? bind.getAgentPlayerId() : player.getAgencyId();
            if (!superiorId.equals(currentAgentId)) {
                throw new ForbiddenException("目标玩家必须先绑定在指定上级代理名下");
            }
            if (!scope.isAdmin() && !isPlayerInScope(dto.getPlayerId(), scope)) {
                throw new ForbiddenException("不能设置当前线路外的玩家");
            }
            depth = safeInt(superior.getDepth()) + 1;
            if (depth <= 1) {
                depth = resolveDepth(superior) + 1;
            }
            parentRate = resolveRate(superior);
            parentPath = resolvePath(superior);
        }
        validateRate(dto.getCommissionRateBp(), parentRate);

        Agency agency = new Agency();
        agency.setPlayerId(dto.getPlayerId());
        agency.setSuperiorId(superiorId);
        agency.setLevel(depth);
        agency.setAgentType(depth == 1 ? Agency.TYPE_LEVEL_ONE : Agency.TYPE_LEVEL_TWO);
        agency.setDepth(depth);
        agency.setPath(parentPath + dto.getPlayerId() + "/");
        agency.setCommissionRateBp(dto.getCommissionRateBp());
        agency.setJuniorCount(0);
        agency.setTotalReward(0L);
        agency.setStatus(Agency.STATUS_NORMAL);
        agency.setCreatedByUserId(scope.getUserId());
        agency.setCreatedByPlayerId(scope.getAgentPlayerId());
        agencyMapper.insert(agency);

        if (dto.getSysUserId() != null) {
            bindSysUser(dto.getSysUserId(), agency.getPlayerId(), agency.getAgentType(), scope.getUsername());
        }
        AgencyInviteCode inviteCode = ensureInviteCode(agency.getPlayerId(), null, scope.getUsername());
        writeAudit(scope, "AGENCY_CREATE", "AGENCY", agency.getPlayerId(), null,
                "设置代理，比例bp=" + dto.getCommissionRateBp());

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", agency.getPlayerId());
        ajax.put("agentType", agency.getAgentType());
        ajax.put("inviteCode", inviteCode.getInviteCode());
        return ajax;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult updateRate(String agentPlayerId, AgencyRateUpdateDTO dto) {
        AgencyScope scope = resolveScope();
        Agency agency = requireAgency(agentPlayerId);
        ensureManagedAgency(scope, agency, false);
        if (!scope.isAdmin() && agentPlayerId.equals(scope.getAgentPlayerId())) {
            throw new ForbiddenException("代理不能修改自己的返佣比例");
        }
        int parentRate = Agency.ROOT_PLAYER_ID.equals(agency.getSuperiorId())
                ? RATE_FULL
                : resolveRate(requireAgency(agency.getSuperiorId()));
        validateRate(dto.getCommissionRateBp(), parentRate);
        int maxChildRate = maxChildRate(agentPlayerId);
        if (dto.getCommissionRateBp() < maxChildRate) {
            throw new ForbiddenException("当前代理已有下级比例高于新比例，请先调整下级比例");
        }
        String before = "rate=" + resolveRate(agency);
        agency.setCommissionRateBp(dto.getCommissionRateBp());
        agencyMapper.updateById(agency);
        writeAudit(scope, "AGENCY_RATE_UPDATE", "AGENCY", agentPlayerId, before,
                "rate=" + dto.getCommissionRateBp() + " | " + StringUtils.nvl(dto.getReason(), ""));
        return AjaxResult.successEx();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult updateStatus(String agentPlayerId, AgencyStatusUpdateDTO dto) {
        AgencyScope scope = resolveScope();
        Agency agency = requireAgency(agentPlayerId);
        ensureManagedAgency(scope, agency, true);
        if (dto.getStatus() != Agency.STATUS_NORMAL && dto.getStatus() != Agency.STATUS_DISABLED) {
            throw new BadRequestException("代理状态错误");
        }
        String before = "status=" + resolveStatus(agency);
        agency.setStatus(dto.getStatus());
        agencyMapper.updateById(agency);
        writeAudit(scope, "AGENCY_STATUS_UPDATE", "AGENCY", agentPlayerId, before,
                "status=" + dto.getStatus() + " | " + StringUtils.nvl(dto.getReason(), ""));
        return AjaxResult.successEx();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult resetInviteCode(String agentPlayerId) {
        AgencyScope scope = resolveScope();
        Agency agency = requireAgency(agentPlayerId);
        ensureManagedAgency(scope, agency, true);
        disableActiveInviteCodes(agentPlayerId);
        AgencyInviteCode inviteCode = ensureInviteCode(agentPlayerId, null, scope.getUsername());
        writeAudit(scope, "AGENCY_INVITE_RESET", "AGENCY", agentPlayerId, null, inviteCode.getInviteCode());
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("inviteCode", inviteCode.getInviteCode());
        return ajax;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult bindByInviteCode(String playerId, String inviteCode, String bindSource, Long operatorUserId) {
        if (StringUtils.isEmpty(playerId)) {
            throw new BadRequestException("玩家ID不能为空");
        }
        if (StringUtils.isEmpty(inviteCode)) {
            throw new BadRequestException("邀请码不能为空");
        }
        Player player = requirePlayer(playerId);
        if (CommonUtils.predicate(player.getBanned()) || CommonUtils.predicate(player.getDelFlag())) {
            throw new ForbiddenException("玩家已封禁或删除，不能绑定代理");
        }
        AgencyInviteCode code = findActiveInviteCode(inviteCode);
        if (code == null) {
            throw new NotFoundException("邀请码不存在或已失效");
        }
        Agency agent = requireAgency(code.getAgentPlayerId());
        ensureAgencyEnabled(agent);
        if (playerId.equals(agent.getPlayerId())) {
            throw new ForbiddenException("玩家不能绑定自己为上级代理");
        }
        Agency playerAgency = getAgency(playerId);
        if (playerAgency != null && isAncestorOrSelf(playerAgency, agent.getPlayerId())) {
            throw new ForbiddenException("不能绑定自己的下级代理，避免形成循环线路");
        }

        PlayerAgentBind current = findActiveBind(playerId);
        if (current != null) {
            if (agent.getPlayerId().equals(current.getAgentPlayerId())) {
                AjaxResult ajax = AjaxResult.successEx();
                ajax.put("agencyId", agent.getPlayerId());
                ajax.put("agencyName", nickname(agent.getPlayerId()));
                ajax.put("alreadyBound", true);
                return ajax;
            }
            throw new ForbiddenException("玩家已经绑定代理，请先走解绑流程");
        }

        PlayerAgentBind bind = new PlayerAgentBind();
        bind.setPlayerId(playerId);
        bind.setAgentPlayerId(agent.getPlayerId());
        bind.setRootAgentPlayerId(rootAgentId(agent));
        bind.setBindSource(StringUtils.isNotEmpty(bindSource) ? bindSource : "invite_code");
        bind.setInviteCode(code.getInviteCode());
        bind.setPathSnapshot(resolvePath(agent) + playerId + "/");
        bind.setStatus(PlayerAgentBind.STATUS_ACTIVE);
        bind.setBindAt(LocalDateTime.now());
        playerAgentBindMapper.insert(bind);
        playerMapper.updateAgencyId(playerId, agent.getPlayerId());

        code.setBindCount(safeInt(code.getBindCount()) + 1);
        agencyInviteCodeMapper.updateById(code);
        increaseJuniorCounts(agent.getPlayerId(), 1);

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("agencyId", agent.getPlayerId());
        ajax.put("agencyName", nickname(agent.getPlayerId()));
        return ajax;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult bindCurrentPlayerByInviteCode(String inviteCode) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null) {
            throw new InternalServerException("Current login player is null");
        }
        return bindByInviteCode(player.getId(), inviteCode, "invite_code", null);
    }

    @Override
    public AjaxResult getCurrentAgentInviteCode() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null) {
            throw new InternalServerException("Current login player is null");
        }
        Agency agency = requireAgency(player.getId());
        ensureAgencyEnabled(agency);
        AgencyInviteCode inviteCode = ensureInviteCode(player.getId(), null, player.getName());
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("inviteCode", inviteCode.getInviteCode());
        ajax.put("agentPlayerId", player.getId());
        return ajax;
    }

    @Override
    public PageResult<AgencyInviteCode> invitePage(PageBody dto) {
        AgencyScope scope = resolveScope();
        List<String> agentIds = getScopeAgentIds(scope);
        LambdaQueryWrapper<AgencyInviteCode> wrapper = Wrappers.lambdaQuery(AgencyInviteCode.class);
        if (!scope.isAdmin()) {
            if (agentIds.isEmpty()) {
                return emptyPage(dto);
            }
            wrapper.in(AgencyInviteCode::getAgentPlayerId, agentIds);
        }
        wrapper.orderByDesc(AgencyInviteCode::getId);
        Page<AgencyInviteCode> page = new Page<>(pageNum(dto), pageSize(dto));
        Page<AgencyInviteCode> ret = agencyInviteCodeMapper.selectPage(page, wrapper);
        return new PageResult<>(ret.getRecords(), (int) ret.getCurrent(), (int) ret.getTotal());
    }

    @Override
    public PageResult<AgencyBindingDTO> bindingPage(AgencyBindingQueryDTO dto) {
        AgencyScope scope = resolveScope();
        List<String> agentIds = getScopeAgentIds(scope);
        List<AgencyBindingDTO> records = new ArrayList<>();

        List<PlayerAgentBind> binds = queryBindsByScope(scope, agentIds, dto);
        Set<String> seen = new HashSet<>();
        for (PlayerAgentBind bind : binds) {
            records.add(toBindingDTO(bind));
            if (PlayerAgentBind.STATUS_ACTIVE.equals(bind.getStatus())) {
                seen.add(bind.getPlayerId());
            }
        }
        for (Player player : queryLegacyBoundPlayers(agentIds)) {
            if (seen.contains(player.getId())) {
                continue;
            }
            if (StringUtils.isNotEmpty(dto.getPlayerId()) && !player.getId().contains(dto.getPlayerId())) {
                continue;
            }
            if (StringUtils.isNotEmpty(dto.getAgentPlayerId()) && !dto.getAgentPlayerId().equals(player.getAgencyId())) {
                continue;
            }
            AgencyBindingDTO item = new AgencyBindingDTO();
            item.setPlayerId(player.getId());
            item.setNickname(player.getNickname());
            item.setAgentPlayerId(player.getAgencyId());
            item.setAgentNickname(nickname(player.getAgencyId()));
            Agency agent = getAgency(player.getAgencyId());
            item.setRootAgentPlayerId(agent != null ? rootAgentId(agent) : null);
            item.setRootAgentNickname(nickname(item.getRootAgentPlayerId()));
            item.setBindSource("legacy");
            item.setStatus(PlayerAgentBind.STATUS_ACTIVE);
            records.add(item);
        }
        records.sort(Comparator.comparing(AgencyBindingDTO::getBindAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return pageList(records, dto);
    }

    @Override
    public PageResult<AgencyCommissionDTO> commissionPage(AgencyCommissionQueryDTO dto) {
        AgencyScope scope = resolveScope();
        List<String> agentIds = getScopeAgentIds(scope);
        List<AgencyCommissionLedger> ledgers = queryCommissionByScope(scope, agentIds, dto);
        List<AgencyCommissionDTO> records = ledgers.stream().map(this::toCommissionDTO).collect(Collectors.toList());
        records.sort(Comparator.comparing(AgencyCommissionDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return pageList(records, dto);
    }

    @Override
    public AjaxResult commissionSummary(AgencyCommissionQueryDTO dto) {
        AgencyScope scope = resolveScope();
        List<String> agentIds = getScopeAgentIds(scope);
        List<AgencyCommissionLedger> ledgers = queryCommissionByScope(scope, agentIds, dto);
        long fee = 0L;
        long commission = 0L;
        Set<Long> feeLedgerIds = new HashSet<>();
        for (AgencyCommissionLedger ledger : ledgers) {
            feeLedgerIds.add(ledger.getRoomFeeLedgerId());
            commission += nvl(ledger.getCommissionAmount());
        }
        for (Long id : feeLedgerIds) {
            RoomFeeLedger roomFeeLedger = roomFeeLedgerMapper.selectById(id);
            if (roomFeeLedger != null) {
                fee += nvl(roomFeeLedger.getFeeAmount());
            }
        }
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("feeTotal", fee);
        ajax.put("commissionTotal", commission);
        ajax.put("commissionCount", ledgers.size());
        ajax.put("roomFeeCount", feeLedgerIds.size());
        return ajax;
    }

    @Override
    public PageResult<WalletLedger> walletLedgerPage(LedgerQueryDTO dto) {
        AgencyScope scope = resolveScope();
        LambdaQueryWrapper<WalletLedger> wrapper = Wrappers.lambdaQuery(WalletLedger.class);
        if (StringUtils.isNotEmpty(dto.getPlayerId())) {
            if (!scope.isAdmin() && !isPlayerInScope(dto.getPlayerId(), scope)) {
                throw new ForbiddenException("不能查看当前线路外的玩家流水");
            }
            wrapper.eq(WalletLedger::getUserId, dto.getPlayerId());
        } else if (!scope.isAdmin()) {
            List<String> agentIds = getScopeAgentIds(scope);
            Set<String> playerIds = getScopePlayerIds(scope, agentIds);
            if (playerIds.isEmpty()) {
                return emptyPage(dto);
            }
            wrapper.in(WalletLedger::getUserId, playerIds);
        }
        if (StringUtils.isNotEmpty(dto.getWalletType())) {
            wrapper.eq(WalletLedger::getWalletType, dto.getWalletType());
        }
        if (StringUtils.isNotEmpty(dto.getBizType())) {
            wrapper.eq(WalletLedger::getBizType, dto.getBizType());
        }
        if (StringUtils.isNotEmpty(dto.getStartTime())) {
            wrapper.ge(WalletLedger::getCreateTime, dto.getStartTime());
        }
        if (StringUtils.isNotEmpty(dto.getEndTime())) {
            wrapper.le(WalletLedger::getCreateTime, dto.getEndTime());
        }
        Integer total = walletLedgerMapper.selectCount(wrapper);
        int pageNum = pageNum(dto);
        int pageSize = pageSize(dto);
        wrapper.orderByDesc(WalletLedger::getId)
                .last(limitClause(pageNum, pageSize));
        List<WalletLedger> records = walletLedgerMapper.selectList(wrapper);
        return new PageResult<>(records, pageNum, total != null ? total : 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult adjustWallet(WalletAdjustDTO dto) {
        AgencyScope scope = resolveScope();
        if (!scope.isAdmin() && !isPlayerInScope(dto.getPlayerId(), scope)) {
            throw new ForbiddenException("不能调整当前线路外的玩家积分");
        }
        if (dto.getAmount() == null || dto.getAmount() == 0) {
            throw new BadRequestException("调整金额不能为0");
        }
        if (StringUtils.isEmpty(dto.getReason())) {
            throw new BadRequestException("调整原因不能为空");
        }
        String walletType = StringUtils.isNotEmpty(dto.getWalletType()) ? dto.getWalletType() : WalletType.GOLD.getCode();
        Long before = walletService.getBalance(dto.getPlayerId(), walletType);
        dto.setWalletType(walletType);
        String counterpartyPlayerId = scope.isAdmin() ? Agency.ROOT_PLAYER_ID : scope.getAgentPlayerId();
        AjaxResult result = walletService.transferAdjust(dto, scope.getUsername(), counterpartyPlayerId);
        Long after = walletService.getBalance(dto.getPlayerId(), walletType);

        AgencyWalletAdjustLog logEntity = new AgencyWalletAdjustLog();
        logEntity.setOperatorUserId(scope.getUserId());
        logEntity.setOperatorAgentPlayerId(scope.getAgentPlayerId());
        logEntity.setTargetPlayerId(dto.getPlayerId());
        logEntity.setWalletType(walletType);
        logEntity.setChangeAmount(dto.getAmount());
        logEntity.setBeforeAmount(before);
        logEntity.setAfterAmount(after);
        Object ledgerId = result.get("ledgerId");
        if (ledgerId instanceof Number) {
            logEntity.setWalletLedgerId(((Number) ledgerId).longValue());
        }
        logEntity.setReason(dto.getReason());
        logEntity.setStatus(AgencyWalletAdjustLog.STATUS_SUCCESS);
        logEntity.setCreateTime(LocalDateTime.now());
        agencyWalletAdjustLogMapper.insert(logEntity);

        writeAudit(scope, "AGENCY_WALLET_ADJUST", "PLAYER", dto.getPlayerId(),
                String.valueOf(before), String.valueOf(after) + " | " + dto.getReason());
        return result;
    }

    @Override
    public PageResult<AgencyUnbindRequest> unbindPage(PageBody dto) {
        AgencyScope scope = resolveScope();
        LambdaQueryWrapper<AgencyUnbindRequest> wrapper = Wrappers.lambdaQuery(AgencyUnbindRequest.class);
        if (!scope.isAdmin()) {
            wrapper.eq(AgencyUnbindRequest::getScopeRootPlayerId, scope.getRootAgentPlayerId());
        }
        wrapper.orderByDesc(AgencyUnbindRequest::getId);
        Page<AgencyUnbindRequest> page = new Page<>(pageNum(dto), pageSize(dto));
        Page<AgencyUnbindRequest> ret = agencyUnbindRequestMapper.selectPage(page, wrapper);
        return new PageResult<>(ret.getRecords(), (int) ret.getCurrent(), (int) ret.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult requestUnbind(AgencyUnbindDTO dto) {
        AgencyScope scope = resolveScope();
        ensureCanUnbind(scope, dto.getPlayerId());
        PlayerAgentBind bind = findActiveBind(dto.getPlayerId());
        if (bind == null) {
            throw new BadRequestException("目标玩家当前没有有效绑定关系");
        }
        AgencyUnbindRequest request = new AgencyUnbindRequest();
        request.setPlayerId(dto.getPlayerId());
        request.setCurrentAgentPlayerId(bind.getAgentPlayerId());
        request.setRequestByUserId(scope.getUserId());
        request.setRequestByPlayerId(scope.getAgentPlayerId());
        request.setScopeRootPlayerId(bind.getRootAgentPlayerId());
        request.setReason(dto.getReason());
        request.setStatus(AgencyUnbindRequest.STATUS_APPROVED);
        request.setReviewByUserId(scope.getUserId());
        request.setReviewRemark("MVP自动审核通过");
        request.setCreateTime(LocalDateTime.now());
        request.setReviewTime(LocalDateTime.now());
        agencyUnbindRequestMapper.insert(request);
        executeUnbindInternal(scope, request);
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("requestId", request.getId());
        ajax.put("status", request.getStatus());
        return ajax;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult approveUnbind(Long requestId, AgencyUnbindReviewDTO dto) {
        AgencyScope scope = resolveScope();
        AgencyUnbindRequest request = requireUnbindRequest(requestId);
        ensureCanReviewUnbind(scope, request);
        if (!AgencyUnbindRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new BadRequestException("该解绑申请不是待审核状态");
        }
        request.setStatus(AgencyUnbindRequest.STATUS_APPROVED);
        request.setReviewByUserId(scope.getUserId());
        request.setReviewRemark(dto != null ? dto.getRemark() : null);
        request.setReviewTime(LocalDateTime.now());
        agencyUnbindRequestMapper.updateById(request);
        return AjaxResult.successEx();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult executeUnbind(Long requestId) {
        AgencyScope scope = resolveScope();
        AgencyUnbindRequest request = requireUnbindRequest(requestId);
        ensureCanReviewUnbind(scope, request);
        executeUnbindInternal(scope, request);
        return AjaxResult.successEx();
    }

    @Override
    public AjaxResult playerStats(String playerId) {
        AgencyScope scope = resolveScope();
        if (!scope.isAdmin() && !isPlayerInScope(playerId, scope)) {
            throw new ForbiddenException("不能查看当前线路外的玩家统计");
        }
        List<GameScoreboard> list = gameScoreboardMapper.selectList(
                Wrappers.lambdaQuery(GameScoreboard.class).eq(GameScoreboard::getPlayerId, playerId));
        int win = 0;
        int lose = 0;
        int draw = 0;
        for (GameScoreboard item : list) {
            win += safeInt(item.getWinNum());
            lose += safeInt(item.getLoseNum());
            draw += safeInt(item.getDrawNum());
        }
        int total = win + lose + draw;
        BigDecimal winRate = total == 0 ? BigDecimal.ZERO :
                new BigDecimal(win).multiply(new BigDecimal("100"))
                        .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerId", playerId);
        ajax.put("winCount", win);
        ajax.put("loseCount", lose);
        ajax.put("drawCount", draw);
        ajax.put("totalRounds", total);
        ajax.put("winRate", winRate);
        return ajax;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processRoomFee(RoomFeeLedger roomFeeLedger) {
        if (roomFeeLedger == null || roomFeeLedger.getId() == null || nvl(roomFeeLedger.getFeeAmount()) <= 0) {
            return;
        }
        Integer exists = agencyCommissionLedgerMapper.selectCount(
                Wrappers.lambdaQuery(AgencyCommissionLedger.class)
                        .eq(AgencyCommissionLedger::getRoomFeeLedgerId, roomFeeLedger.getId()));
        if (CommonUtils.predicate(exists)) {
            log.info("[代理返佣] 房费已处理，跳过: roomFeeLedgerId={}", roomFeeLedger.getId());
            return;
        }

        PlayerAgentBind bind = findActiveBind(roomFeeLedger.getUserId());
        if (bind == null) {
            insertPlatformCommission(roomFeeLedger, RATE_FULL, roomFeeLedger.getFeeAmount(), "玩家未绑定代理，平台获得全部房费");
            return;
        }
        Agency directAgent = getAgency(bind.getAgentPlayerId());
        if (directAgent == null) {
            insertPlatformCommission(roomFeeLedger, RATE_FULL, roomFeeLedger.getFeeAmount(), "绑定代理不存在，平台获得全部房费");
            return;
        }

        List<Agency> chain = buildAgencyChain(directAgent);
        if (chain.isEmpty()) {
            insertPlatformCommission(roomFeeLedger, RATE_FULL, roomFeeLedger.getFeeAmount(), "代理线路为空，平台获得全部房费");
            return;
        }
        long remaining = roomFeeLedger.getFeeAmount();
        int nextRate = resolveRate(chain.get(0));
        long platformAmount = calcShare(roomFeeLedger.getFeeAmount(), RATE_FULL - nextRate);
        remaining -= platformAmount;
        insertPlatformCommission(roomFeeLedger, RATE_FULL - nextRate, platformAmount, "平台房费分成");

        for (int i = 0; i < chain.size(); i++) {
            Agency current = chain.get(i);
            int selfRate = resolveRate(current);
            int childRate = (i + 1) < chain.size() ? resolveRate(chain.get(i + 1)) : 0;
            if (childRate > selfRate) {
                throw new ForbiddenException("代理线路比例异常，下级比例超过上级");
            }
            int shareRate = selfRate - childRate;
            long amount = (i + 1) == chain.size() ? remaining : calcShare(roomFeeLedger.getFeeAmount(), shareRate);
            remaining -= amount;
            insertAgentCommission(roomFeeLedger, current, childRate, shareRate, amount, bind.getPathSnapshot());
        }
    }

    private AgencyScope resolveScope() {
        AgencyScope scope = new AgencyScope();
        Long userId = SecurityUtils.getUserId();
        String username = SecurityUtils.getUsername();
        scope.setUserId(userId);
        scope.setUsername(username);
        if (SecurityUtils.isAdmin(userId)) {
            scope.setAdmin(true);
            scope.setRootAgentPlayerId(Agency.ROOT_PLAYER_ID);
            scope.setPathPrefix("/" + Agency.ROOT_PLAYER_ID + "/");
            return scope;
        }
        SysUserAgent relation = sysUserAgentMapper.selectOne(
                Wrappers.lambdaQuery(SysUserAgent.class)
                        .eq(SysUserAgent::getUserId, userId)
                        .eq(SysUserAgent::getStatus, SysUserAgent.STATUS_NORMAL)
                        .last("LIMIT 1"));
        if (relation == null) {
            throw new ForbiddenException("当前后台账号未绑定代理身份");
        }
        Agency agency = requireAgency(relation.getPlayerId());
        ensureAgencyEnabled(agency);
        scope.setAdmin(false);
        scope.setAgentPlayerId(agency.getPlayerId());
        scope.setAgentType(resolveAgentType(agency));
        scope.setPathPrefix(resolvePath(agency));
        scope.setRootAgentPlayerId(rootAgentId(agency));
        return scope;
    }

    private List<Agency> queryScopeAgencies(AgencyScope scope) {
        LambdaQueryWrapper<Agency> wrapper = Wrappers.lambdaQuery(Agency.class);
        if (!scope.isAdmin()) {
            wrapper.likeRight(Agency::getPath, scope.getPathPrefix());
        }
        wrapper.orderByAsc(Agency::getDepth).orderByAsc(Agency::getId);
        List<Agency> agencies = agencyMapper.selectList(wrapper);
        if (!scope.isAdmin() && agencies.stream().noneMatch(a -> scope.getAgentPlayerId().equals(a.getPlayerId()))) {
            Agency self = requireAgency(scope.getAgentPlayerId());
            agencies.add(self);
        }
        return agencies;
    }

    private List<String> getScopeAgentIds(AgencyScope scope) {
        return queryScopeAgencies(scope).stream().map(Agency::getPlayerId).distinct().collect(Collectors.toList());
    }

    private Set<String> getScopePlayerIds(AgencyScope scope, List<String> agentIds) {
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(agentIds);
        for (PlayerAgentBind bind : queryActiveBinds(agentIds)) {
            ids.add(bind.getPlayerId());
        }
        for (Player player : queryLegacyBoundPlayers(agentIds)) {
            ids.add(player.getId());
        }
        if (scope.isAdmin()) {
            return ids;
        }
        return ids;
    }

    private boolean isAgencyInScope(Agency agency, AgencyScope scope) {
        if (scope.isAdmin()) {
            return true;
        }
        if (agency == null) {
            return false;
        }
        if (resolvePath(agency).startsWith(scope.getPathPrefix())) {
            return true;
        }
        String current = agency.getPlayerId();
        int guard = 0;
        while (StringUtils.isNotEmpty(current) && !Agency.ROOT_PLAYER_ID.equals(current) && guard++ < 64) {
            if (current.equals(scope.getAgentPlayerId())) {
                return true;
            }
            Agency cursor = getAgency(current);
            if (cursor == null || StringUtils.isEmpty(cursor.getSuperiorId())) {
                break;
            }
            current = cursor.getSuperiorId();
        }
        return false;
    }

    private boolean isPlayerInScope(String playerId, AgencyScope scope) {
        if (scope.isAdmin()) {
            return true;
        }
        Agency agency = getAgency(playerId);
        if (agency != null) {
            return isAgencyInScope(agency, scope);
        }
        PlayerAgentBind bind = findActiveBind(playerId);
        if (bind != null) {
            Agency agent = getAgency(bind.getAgentPlayerId());
            return agent != null && isAgencyInScope(agent, scope);
        }
        Player player = playerMapper.selectById(playerId);
        if (player != null && StringUtils.isNotEmpty(player.getAgencyId())) {
            Agency agent = getAgency(player.getAgencyId());
            return agent != null && isAgencyInScope(agent, scope);
        }
        return false;
    }

    private void ensureManagedAgency(AgencyScope scope, Agency agency, boolean allowSelf) {
        if (!isAgencyInScope(agency, scope)) {
            throw new ForbiddenException("不能操作当前线路外的代理");
        }
        if (!allowSelf && !scope.isAdmin() && agency.getPlayerId().equals(scope.getAgentPlayerId())) {
            throw new ForbiddenException("不能操作自己的代理配置");
        }
    }

    private void ensureCanUnbind(AgencyScope scope, String playerId) {
        if (!scope.isAdmin() && scope.getAgentType() != Agency.TYPE_LEVEL_ONE) {
            throw new ForbiddenException("只有一级代理和超级管理员可以解除绑定关系");
        }
        if (!scope.isAdmin() && !isPlayerInScope(playerId, scope)) {
            throw new ForbiddenException("不能解除当前线路外的绑定关系");
        }
        Agency agency = getAgency(playerId);
        if (agency != null && hasActiveChildren(playerId)) {
            throw new ForbiddenException("该代理仍有下级代理或玩家，请先转移或解除下级关系");
        }
    }

    private void ensureCanReviewUnbind(AgencyScope scope, AgencyUnbindRequest request) {
        if (!scope.isAdmin() && scope.getAgentType() != Agency.TYPE_LEVEL_ONE) {
            throw new ForbiddenException("只有一级代理和超级管理员可以处理解绑");
        }
        if (!scope.isAdmin() && !scope.getRootAgentPlayerId().equals(request.getScopeRootPlayerId())) {
            throw new ForbiddenException("不能处理当前线路外的解绑申请");
        }
    }

    private void executeUnbindInternal(AgencyScope scope, AgencyUnbindRequest request) {
        if (!AgencyUnbindRequest.STATUS_APPROVED.equals(request.getStatus())
                && !AgencyUnbindRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new BadRequestException("该解绑申请当前状态不能执行");
        }
        ensureCanUnbind(scope, request.getPlayerId());
        PlayerAgentBind bind = findActiveBind(request.getPlayerId());
        if (bind == null) {
            throw new BadRequestException("目标玩家当前没有有效绑定关系");
        }
        bind.setStatus(PlayerAgentBind.STATUS_UNBOUND);
        bind.setUnbindAt(LocalDateTime.now());
        bind.setUnbindByUserId(scope.getUserId());
        bind.setUnbindReason(request.getReason());
        if (bind.getId() != null) {
            playerAgentBindMapper.updateById(bind);
        }
        playerMapper.updateAgencyId(request.getPlayerId(), null);
        increaseJuniorCounts(bind.getAgentPlayerId(), -1);

        request.setStatus(AgencyUnbindRequest.STATUS_EXECUTED);
        request.setExecuteTime(LocalDateTime.now());
        agencyUnbindRequestMapper.updateById(request);
        writeAudit(scope, "AGENCY_UNBIND_EXECUTE", "PLAYER", request.getPlayerId(), bind.getAgentPlayerId(), request.getReason());
    }

    private boolean hasActiveChildren(String agentPlayerId) {
        Integer childAgents = agencyMapper.selectCount(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getSuperiorId, agentPlayerId)
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus)));
        if (CommonUtils.predicate(childAgents)) {
            return true;
        }
        Integer childPlayers = playerAgentBindMapper.selectCount(
                Wrappers.lambdaQuery(PlayerAgentBind.class)
                        .eq(PlayerAgentBind::getAgentPlayerId, agentPlayerId)
                        .eq(PlayerAgentBind::getStatus, PlayerAgentBind.STATUS_ACTIVE));
        if (CommonUtils.predicate(childPlayers)) {
            return true;
        }
        Integer legacy = playerMapper.countJuniorPlayer(agentPlayerId);
        return CommonUtils.predicate(legacy);
    }

    private Player requirePlayer(String playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new NotFoundException("玩家不存在: " + playerId);
        }
        return player;
    }

    private Agency requireAgency(String playerId) {
        Agency agency = getAgency(playerId);
        if (agency == null) {
            throw new NotFoundException("代理不存在: " + playerId);
        }
        return agency;
    }

    private Agency getAgency(String playerId) {
        if (StringUtils.isEmpty(playerId) || Agency.ROOT_PLAYER_ID.equals(playerId)) {
            return null;
        }
        return agencyMapper.selectOne(Wrappers.lambdaQuery(Agency.class)
                .eq(Agency::getPlayerId, playerId)
                .last("LIMIT 1"));
    }

    private boolean isPlayerAgency(String playerId) {
        return getAgency(playerId) != null;
    }

    private void ensureAgencyEnabled(Agency agency) {
        if (agency == null || resolveStatus(agency) != Agency.STATUS_NORMAL) {
            throw new ForbiddenException("代理已停用或不存在");
        }
    }

    private PlayerAgentBind findActiveBind(String playerId) {
        PlayerAgentBind bind = playerAgentBindMapper.selectOne(
                Wrappers.lambdaQuery(PlayerAgentBind.class)
                        .eq(PlayerAgentBind::getPlayerId, playerId)
                        .eq(PlayerAgentBind::getStatus, PlayerAgentBind.STATUS_ACTIVE)
                        .last("LIMIT 1"));
        if (bind != null) {
            return bind;
        }
        Player player = playerMapper.selectById(playerId);
        if (player == null || StringUtils.isEmpty(player.getAgencyId())) {
            return null;
        }
        Agency agent = getAgency(player.getAgencyId());
        if (agent == null) {
            return null;
        }
        PlayerAgentBind legacy = new PlayerAgentBind();
        legacy.setPlayerId(playerId);
        legacy.setAgentPlayerId(player.getAgencyId());
        legacy.setRootAgentPlayerId(rootAgentId(agent));
        legacy.setBindSource("legacy");
        legacy.setPathSnapshot(resolvePath(agent) + playerId + "/");
        legacy.setStatus(PlayerAgentBind.STATUS_ACTIVE);
        return legacy;
    }

    private List<PlayerAgentBind> queryActiveBinds(List<String> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return playerAgentBindMapper.selectList(
                Wrappers.lambdaQuery(PlayerAgentBind.class)
                        .in(PlayerAgentBind::getAgentPlayerId, agentIds)
                        .eq(PlayerAgentBind::getStatus, PlayerAgentBind.STATUS_ACTIVE));
    }

    private List<PlayerAgentBind> queryBindsByScope(AgencyScope scope, List<String> agentIds, AgencyBindingQueryDTO dto) {
        LambdaQueryWrapper<PlayerAgentBind> wrapper = Wrappers.lambdaQuery(PlayerAgentBind.class);
        if (!scope.isAdmin()) {
            if (agentIds.isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.in(PlayerAgentBind::getAgentPlayerId, agentIds);
        }
        if (StringUtils.isNotEmpty(dto.getPlayerId())) {
            wrapper.like(PlayerAgentBind::getPlayerId, dto.getPlayerId());
        }
        if (StringUtils.isNotEmpty(dto.getAgentPlayerId())) {
            wrapper.eq(PlayerAgentBind::getAgentPlayerId, dto.getAgentPlayerId());
        }
        if (StringUtils.isNotEmpty(dto.getRootAgentPlayerId())) {
            wrapper.eq(PlayerAgentBind::getRootAgentPlayerId, dto.getRootAgentPlayerId());
        }
        if (StringUtils.isNotEmpty(dto.getStatus())) {
            wrapper.eq(PlayerAgentBind::getStatus, dto.getStatus());
        }
        if (StringUtils.isNotEmpty(dto.getStartTime())) {
            wrapper.ge(PlayerAgentBind::getBindAt, dto.getStartTime());
        }
        if (StringUtils.isNotEmpty(dto.getEndTime())) {
            wrapper.le(PlayerAgentBind::getBindAt, dto.getEndTime());
        }
        wrapper.orderByDesc(PlayerAgentBind::getId);
        return playerAgentBindMapper.selectList(wrapper);
    }

    private List<Player> queryLegacyBoundPlayers(List<String> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return playerMapper.selectList(Wrappers.lambdaQuery(Player.class)
                .in(Player::getAgencyId, agentIds)
                .ne(Player::getDelFlag, 1));
    }

    private List<AgencyCommissionLedger> queryCommissionByScope(AgencyScope scope, List<String> agentIds, AgencyCommissionQueryDTO dto) {
        LambdaQueryWrapper<AgencyCommissionLedger> wrapper = Wrappers.lambdaQuery(AgencyCommissionLedger.class);
        if (!scope.isAdmin()) {
            if (agentIds.isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.in(AgencyCommissionLedger::getAgentPlayerId, agentIds);
        }
        if (dto != null) {
            if (StringUtils.isNotEmpty(dto.getRoomId())) {
                wrapper.eq(AgencyCommissionLedger::getRoomId, dto.getRoomId());
            }
            if (StringUtils.isNotEmpty(dto.getFeePlayerId())) {
                wrapper.eq(AgencyCommissionLedger::getFeePlayerId, dto.getFeePlayerId());
            }
            if (StringUtils.isNotEmpty(dto.getAgentPlayerId())) {
                wrapper.eq(AgencyCommissionLedger::getAgentPlayerId, dto.getAgentPlayerId());
            }
            if (dto.getAgentType() != null) {
                wrapper.eq(AgencyCommissionLedger::getAgentType, dto.getAgentType());
            }
            if (StringUtils.isNotEmpty(dto.getStatus())) {
                wrapper.eq(AgencyCommissionLedger::getStatus, dto.getStatus());
            }
            if (StringUtils.isNotEmpty(dto.getStartTime())) {
                wrapper.ge(AgencyCommissionLedger::getCreateTime, dto.getStartTime());
            }
            if (StringUtils.isNotEmpty(dto.getEndTime())) {
                wrapper.le(AgencyCommissionLedger::getCreateTime, dto.getEndTime());
            }
        }
        wrapper.orderByDesc(AgencyCommissionLedger::getId);
        return agencyCommissionLedgerMapper.selectList(wrapper);
    }

    private List<Agency> buildAgencyChain(Agency directAgent) {
        String path = resolvePath(directAgent);
        List<String> ids = Arrays.stream(path.split("/"))
                .filter(StringUtils::isNotEmpty)
                .filter(id -> !Agency.ROOT_PLAYER_ID.equals(id))
                .collect(Collectors.toList());
        List<Agency> chain = new ArrayList<>();
        for (String id : ids) {
            Agency agency = getAgency(id);
            if (agency != null) {
                chain.add(agency);
            }
        }
        chain.sort(Comparator.comparing(a -> safeInt(a.getDepth())));
        return chain;
    }

    private void insertPlatformCommission(RoomFeeLedger fee, int shareRate, long amount, String remark) {
        if (amount < 0) {
            throw new ForbiddenException("房费返佣金额不能为负数");
        }
        AgencyCommissionLedger ledger = new AgencyCommissionLedger();
        ledger.setRoomFeeLedgerId(fee.getId());
        ledger.setRoomId(fee.getRoomId());
        ledger.setFeePlayerId(fee.getUserId());
        ledger.setAgentPlayerId(Agency.ROOT_PLAYER_ID);
        ledger.setAgentType(Agency.TYPE_ROOT);
        ledger.setAgentDepth(0);
        ledger.setParentRateBp(RATE_FULL);
        ledger.setSelfRateBp(RATE_FULL);
        ledger.setChildRateBp(RATE_FULL - shareRate);
        ledger.setShareRateBp(shareRate);
        ledger.setFeeAmount(fee.getFeeAmount());
        ledger.setCommissionAmount(amount);
        ledger.setPathSnapshot("/" + Agency.ROOT_PLAYER_ID + "/");
        if (amount > 0) {
            String refNo = "PLATFORM_COMMISSION_" + fee.getId();
            Long walletLedgerId = walletService.increase(Agency.ROOT_PLAYER_ID, WalletType.DEPOSIT.getCode(), amount,
                    LedgerBizType.AGENCY_COMMISSION.getCode(), refNo, remark + " | 房间:" + fee.getRoomId());
            ledger.setWalletLedgerId(walletLedgerId);
        }
        ledger.setStatus(AgencyCommissionLedger.STATUS_SETTLED);
        ledger.setRemark(remark);
        ledger.setCreateTime(LocalDateTime.now());
        agencyCommissionLedgerMapper.insert(ledger);
    }

    private void insertAgentCommission(RoomFeeLedger fee, Agency agency, int childRate, int shareRate, long amount, String snapshot) {
        if (amount <= 0) {
            return;
        }
        String refNo = "AGENCY_COMMISSION_" + fee.getId() + "_" + agency.getPlayerId();
        Long walletLedgerId = walletService.increase(agency.getPlayerId(), WalletType.DEPOSIT.getCode(), amount,
                LedgerBizType.AGENCY_COMMISSION.getCode(), refNo, "房费返佣 | 房间:" + fee.getRoomId());
        AgencyCommissionLedger ledger = new AgencyCommissionLedger();
        ledger.setRoomFeeLedgerId(fee.getId());
        ledger.setRoomId(fee.getRoomId());
        ledger.setFeePlayerId(fee.getUserId());
        ledger.setAgentPlayerId(agency.getPlayerId());
        ledger.setAgentType(resolveAgentType(agency));
        ledger.setAgentDepth(resolveDepth(agency));
        ledger.setParentRateBp(Agency.ROOT_PLAYER_ID.equals(agency.getSuperiorId())
                ? RATE_FULL
                : resolveRate(requireAgency(agency.getSuperiorId())));
        ledger.setSelfRateBp(resolveRate(agency));
        ledger.setChildRateBp(childRate);
        ledger.setShareRateBp(shareRate);
        ledger.setFeeAmount(fee.getFeeAmount());
        ledger.setCommissionAmount(amount);
        ledger.setPathSnapshot(StringUtils.isNotEmpty(snapshot) ? snapshot : resolvePath(agency));
        ledger.setWalletLedgerId(walletLedgerId);
        ledger.setStatus(AgencyCommissionLedger.STATUS_SETTLED);
        ledger.setRemark("房费返佣");
        ledger.setCreateTime(LocalDateTime.now());
        agencyCommissionLedgerMapper.insert(ledger);
    }

    private long calcShare(long feeAmount, int shareRateBp) {
        if (shareRateBp <= 0 || feeAmount <= 0) {
            return 0L;
        }
        return (feeAmount * shareRateBp) / RATE_FULL;
    }

    private void validateRate(Integer rate, int parentRate) {
        if (rate == null || rate < 0 || rate > RATE_FULL) {
            throw new BadRequestException("返佣比例必须在0-10000之间");
        }
        if (rate > parentRate) {
            throw new ForbiddenException("下级返佣比例不能超过上级比例");
        }
    }

    private int maxChildRate(String agentPlayerId) {
        List<Agency> children = agencyMapper.selectList(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getSuperiorId, agentPlayerId)
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus)));
        int max = 0;
        for (Agency child : children) {
            max = Math.max(max, resolveRate(child));
        }
        return max;
    }

    private AgencyInviteCode ensureInviteCode(String agentPlayerId, String channelName, String operator) {
        AgencyInviteCode current = agencyInviteCodeMapper.selectOne(
                Wrappers.lambdaQuery(AgencyInviteCode.class)
                        .eq(AgencyInviteCode::getAgentPlayerId, agentPlayerId)
                        .eq(AgencyInviteCode::getStatus, AgencyInviteCode.STATUS_ACTIVE)
                        .last("LIMIT 1"));
        if (current != null) {
            return current;
        }
        String code = generateInviteCode();
        AgencyInviteCode entity = new AgencyInviteCode();
        entity.setAgentPlayerId(agentPlayerId);
        entity.setInviteCode(code);
        entity.setChannelName(channelName);
        entity.setStatus(AgencyInviteCode.STATUS_ACTIVE);
        entity.setBindCount(0);
        entity.setCreatedBy(operator);
        entity.setCreateTime(LocalDateTime.now());
        agencyInviteCodeMapper.insert(entity);

        Agency agency = getAgency(agentPlayerId);
        if (agency != null) {
            agency.setInviteCode(code);
            agencyMapper.updateById(agency);
        }
        return entity;
    }

    private AgencyInviteCode findActiveInviteCode(String inviteCode) {
        return agencyInviteCodeMapper.selectOne(
                Wrappers.lambdaQuery(AgencyInviteCode.class)
                        .eq(AgencyInviteCode::getInviteCode, inviteCode)
                        .eq(AgencyInviteCode::getStatus, AgencyInviteCode.STATUS_ACTIVE)
                        .last("LIMIT 1"));
    }

    private void disableActiveInviteCodes(String agentPlayerId) {
        List<AgencyInviteCode> codes = agencyInviteCodeMapper.selectList(
                Wrappers.lambdaQuery(AgencyInviteCode.class)
                        .eq(AgencyInviteCode::getAgentPlayerId, agentPlayerId)
                        .eq(AgencyInviteCode::getStatus, AgencyInviteCode.STATUS_ACTIVE));
        for (AgencyInviteCode code : codes) {
            code.setStatus(AgencyInviteCode.STATUS_DISABLED);
            code.setDisabledAt(LocalDateTime.now());
            agencyInviteCodeMapper.updateById(code);
        }
    }

    private String generateInviteCode() {
        String code = CommonUtils.generateRandomCode(8, CommonUtils.CODE_CAPITAL | CommonUtils.CODE_NUMBER,
                value -> agencyInviteCodeMapper.selectCount(
                        Wrappers.lambdaQuery(AgencyInviteCode.class)
                                .eq(AgencyInviteCode::getInviteCode, value)) > 0);
        if (StringUtils.isEmpty(code)) {
            throw new InternalServerException("邀请码生成失败");
        }
        return code;
    }

    private void bindSysUser(Long userId, String playerId, Integer agentType, String operator) {
        SysUserAgent exists = sysUserAgentMapper.selectOne(
                Wrappers.lambdaQuery(SysUserAgent.class)
                        .eq(SysUserAgent::getUserId, userId)
                        .last("LIMIT 1"));
        if (exists == null) {
            exists = new SysUserAgent();
            exists.setUserId(userId);
            exists.setPlayerId(playerId);
            exists.setAgentRole(agentType == Agency.TYPE_LEVEL_ONE ? "L1" : "L2");
            exists.setStatus(SysUserAgent.STATUS_NORMAL);
            exists.setCreateBy(operator);
            exists.setCreateTime(LocalDateTime.now());
            sysUserAgentMapper.insert(exists);
        } else {
            exists.setPlayerId(playerId);
            exists.setAgentRole(agentType == Agency.TYPE_LEVEL_ONE ? "L1" : "L2");
            exists.setStatus(SysUserAgent.STATUS_NORMAL);
            sysUserAgentMapper.updateById(exists);
        }
        grantAgentRole(userId, agentType);
    }

    private void grantAgentRole(Long userId, Integer agentType) {
        if (userId == null) {
            return;
        }
        SysUserRole l1Role = new SysUserRole();
        l1Role.setUserId(userId);
        l1Role.setRoleId(AGENT_L1_ROLE_ID);
        sysUserRoleMapper.deleteUserRoleInfo(l1Role);
        SysUserRole l2Role = new SysUserRole();
        l2Role.setUserId(userId);
        l2Role.setRoleId(AGENT_L2_ROLE_ID);
        sysUserRoleMapper.deleteUserRoleInfo(l2Role);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(Objects.equals(agentType, Agency.TYPE_LEVEL_ONE) ? AGENT_L1_ROLE_ID : AGENT_L2_ROLE_ID);
        sysUserRoleMapper.batchUserRole(Collections.singletonList(userRole));
    }

    private void increaseJuniorCounts(String agentPlayerId, int delta) {
        String current = agentPlayerId;
        int guard = 0;
        while (StringUtils.isNotEmpty(current) && !Agency.ROOT_PLAYER_ID.equals(current) && guard < 64) {
            Agency agency = getAgency(current);
            if (agency == null) {
                break;
            }
            int next = safeInt(agency.getJuniorCount()) + delta;
            agency.setJuniorCount(Math.max(0, next));
            agencyMapper.updateById(agency);
            current = agency.getSuperiorId();
            guard++;
        }
    }

    private boolean isAncestorOrSelf(Agency agency, String possibleAncestorPlayerId) {
        if (agency == null || StringUtils.isEmpty(possibleAncestorPlayerId)) {
            return false;
        }
        return resolvePath(agency).contains("/" + possibleAncestorPlayerId + "/");
    }

    private String resolvePath(Agency agency) {
        if (agency == null) {
            return "/" + Agency.ROOT_PLAYER_ID + "/";
        }
        if (StringUtils.isNotEmpty(agency.getPath())) {
            return agency.getPath();
        }
        LinkedList<String> ids = new LinkedList<>();
        Agency cursor = agency;
        int guard = 0;
        while (cursor != null && guard < 64) {
            ids.addFirst(cursor.getPlayerId());
            if (StringUtils.isEmpty(cursor.getSuperiorId()) || Agency.ROOT_PLAYER_ID.equals(cursor.getSuperiorId())) {
                break;
            }
            cursor = getAgency(cursor.getSuperiorId());
            guard++;
        }
        StringBuilder path = new StringBuilder("/").append(Agency.ROOT_PLAYER_ID).append("/");
        for (String id : ids) {
            path.append(id).append("/");
        }
        return path.toString();
    }

    private String rootAgentId(Agency agency) {
        String path = resolvePath(agency);
        String[] parts = path.split("/");
        for (String part : parts) {
            if (StringUtils.isNotEmpty(part) && !Agency.ROOT_PLAYER_ID.equals(part)) {
                return part;
            }
        }
        return agency != null ? agency.getPlayerId() : Agency.ROOT_PLAYER_ID;
    }

    private int resolveRate(Agency agency) {
        if (agency == null) {
            return RATE_FULL;
        }
        return agency.getCommissionRateBp() != null ? agency.getCommissionRateBp() : 0;
    }

    private int resolveDepth(Agency agency) {
        if (agency == null) {
            return 0;
        }
        if (agency.getDepth() != null && agency.getDepth() > 0) {
            return agency.getDepth();
        }
        if (agency.getLevel() != null && agency.getLevel() > 0) {
            return agency.getLevel();
        }
        return Math.max(1, resolvePath(agency).split("/").length - 2);
    }

    private int resolveAgentType(Agency agency) {
        if (agency == null) {
            return Agency.TYPE_ROOT;
        }
        if (agency.getAgentType() != null) {
            return agency.getAgentType();
        }
        return resolveDepth(agency) <= 1 ? Agency.TYPE_LEVEL_ONE : Agency.TYPE_LEVEL_TWO;
    }

    private int resolveStatus(Agency agency) {
        return agency != null && agency.getStatus() != null ? agency.getStatus() : Agency.STATUS_NORMAL;
    }

    private String nickname(String playerId) {
        if (StringUtils.isEmpty(playerId)) {
            return null;
        }
        if (Agency.ROOT_PLAYER_ID.equals(playerId)) {
            return "平台/超级管理员";
        }
        return playerMapper.getNickname(playerId);
    }

    private AgencyTreeNodeDTO toAgentNode(Agency agency) {
        AgencyTreeNodeDTO node = new AgencyTreeNodeDTO();
        node.setId("A:" + agency.getPlayerId());
        node.setPlayerId(agency.getPlayerId());
        node.setNickname(nickname(agency.getPlayerId()));
        node.setNodeType("agent");
        node.setAgentType(resolveAgentType(agency));
        node.setDepth(resolveDepth(agency));
        node.setCommissionRateBp(resolveRate(agency));
        node.setInviteCode(agency.getInviteCode());
        node.setStatus(resolveStatus(agency));
        node.setDirectPlayerCount(playerMapper.countJuniorPlayer(agency.getPlayerId()));
        node.setDirectAgentCount(agencyMapper.selectCount(Wrappers.lambdaQuery(Agency.class)
                .eq(Agency::getSuperiorId, agency.getPlayerId())));
        return node;
    }

    private AgencyTreeNodeDTO toPlayerNode(String playerId, String agentPlayerId) {
        AgencyTreeNodeDTO node = new AgencyTreeNodeDTO();
        node.setId("P:" + playerId);
        node.setParentId("A:" + agentPlayerId);
        node.setPlayerId(playerId);
        node.setNickname(nickname(playerId));
        node.setNodeType("player");
        node.setAgentType(null);
        node.setStatus(0);
        return node;
    }

    private AgencyListDTO toAgencyListDTO(Agency agency, String nickname) {
        AgencyListDTO dto = new AgencyListDTO();
        dto.setPlayerId(agency.getPlayerId());
        dto.setNickname(nickname);
        dto.setAgentType(resolveAgentType(agency));
        dto.setDepth(resolveDepth(agency));
        dto.setSuperiorId(agency.getSuperiorId());
        dto.setSuperiorNickname(nickname(agency.getSuperiorId()));
        dto.setCommissionRateBp(resolveRate(agency));
        dto.setInviteCode(agency.getInviteCode());
        dto.setDirectPlayerCount(playerMapper.countJuniorPlayer(agency.getPlayerId()));
        dto.setDirectAgentCount(agencyMapper.selectCount(Wrappers.lambdaQuery(Agency.class)
                .eq(Agency::getSuperiorId, agency.getPlayerId())));
        dto.setTotalCommission(sumCommission(agency.getPlayerId()));
        dto.setTotalRoomFee(sumRoomFee(agency.getPlayerId()));
        dto.setStatus(resolveStatus(agency));
        return dto;
    }

    private AgencyBindingDTO toBindingDTO(PlayerAgentBind bind) {
        AgencyBindingDTO dto = new AgencyBindingDTO();
        dto.setId(bind.getId());
        dto.setPlayerId(bind.getPlayerId());
        dto.setNickname(nickname(bind.getPlayerId()));
        dto.setAgentPlayerId(bind.getAgentPlayerId());
        dto.setAgentNickname(nickname(bind.getAgentPlayerId()));
        dto.setRootAgentPlayerId(bind.getRootAgentPlayerId());
        dto.setRootAgentNickname(nickname(bind.getRootAgentPlayerId()));
        dto.setBindSource(bind.getBindSource());
        dto.setInviteCode(bind.getInviteCode());
        dto.setStatus(bind.getStatus());
        dto.setBindAt(bind.getBindAt());
        dto.setUnbindAt(bind.getUnbindAt());
        return dto;
    }

    private AgencyCommissionDTO toCommissionDTO(AgencyCommissionLedger ledger) {
        AgencyCommissionDTO dto = new AgencyCommissionDTO();
        dto.setId(ledger.getId());
        dto.setRoomFeeLedgerId(ledger.getRoomFeeLedgerId());
        dto.setRoomId(ledger.getRoomId());
        dto.setFeePlayerId(ledger.getFeePlayerId());
        dto.setFeePlayerNickname(nickname(ledger.getFeePlayerId()));
        dto.setAgentPlayerId(ledger.getAgentPlayerId());
        dto.setAgentNickname(nickname(ledger.getAgentPlayerId()));
        dto.setAgentType(ledger.getAgentType());
        dto.setAgentDepth(ledger.getAgentDepth());
        dto.setParentRateBp(ledger.getParentRateBp());
        dto.setSelfRateBp(ledger.getSelfRateBp());
        dto.setChildRateBp(ledger.getChildRateBp());
        dto.setShareRateBp(ledger.getShareRateBp());
        dto.setFeeAmount(ledger.getFeeAmount());
        dto.setCommissionAmount(ledger.getCommissionAmount());
        dto.setPathSnapshot(ledger.getPathSnapshot());
        dto.setStatus(ledger.getStatus());
        dto.setRemark(ledger.getRemark());
        dto.setCreateTime(ledger.getCreateTime());
        return dto;
    }

    private long sumCommission(String agentPlayerId) {
        long sum = 0L;
        List<AgencyCommissionLedger> ledgers = agencyCommissionLedgerMapper.selectList(
                Wrappers.lambdaQuery(AgencyCommissionLedger.class)
                        .eq(AgencyCommissionLedger::getAgentPlayerId, agentPlayerId));
        for (AgencyCommissionLedger ledger : ledgers) {
            sum += nvl(ledger.getCommissionAmount());
        }
        return sum;
    }

    private long sumRoomFee(String agentPlayerId) {
        long sum = 0L;
        List<AgencyCommissionLedger> ledgers = agencyCommissionLedgerMapper.selectList(
                Wrappers.lambdaQuery(AgencyCommissionLedger.class)
                        .eq(AgencyCommissionLedger::getAgentPlayerId, agentPlayerId));
        Set<Long> feeIds = new HashSet<>();
        for (AgencyCommissionLedger ledger : ledgers) {
            if (ledger.getRoomFeeLedgerId() != null) {
                feeIds.add(ledger.getRoomFeeLedgerId());
            }
        }
        for (Long feeId : feeIds) {
            RoomFeeLedger fee = roomFeeLedgerMapper.selectById(feeId);
            if (fee != null) {
                sum += nvl(fee.getFeeAmount());
            }
        }
        return sum;
    }

    private AgencyUnbindRequest requireUnbindRequest(Long requestId) {
        AgencyUnbindRequest request = agencyUnbindRequestMapper.selectById(requestId);
        if (request == null) {
            throw new NotFoundException("解绑申请不存在");
        }
        return request;
    }

    private void writeAudit(AgencyScope scope, String action, String targetType, String targetId, String before, String after) {
        AdminAuditLog audit = new AdminAuditLog();
        audit.setAdminId(scope.getUserId());
        audit.setAdminName(scope.getUsername());
        audit.setModule("AGENCY");
        audit.setAction(action);
        audit.setTargetType(targetType);
        audit.setTargetId(targetId);
        audit.setBeforeJson(toAuditJson(before));
        audit.setAfterJson(toAuditJson(after));
        audit.setReason(after);
        audit.setStatus(1);
        audit.setIp(IpUtils.getIpAddr());
        audit.setCreateTime(LocalDateTime.now());
        adminAuditLogMapper.insert(audit);
    }

    private String toAuditJson(String value) {
        if (value == null) {
            return null;
        }
        return "{\"value\":\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private <T> PageResult<T> pageList(List<T> records, PageBody dto) {
        int pageNum = pageNum(dto);
        int pageSize = pageSize(dto);
        int total = records.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<T> pageRecords = from >= total ? Collections.emptyList() : records.subList(from, to);
        PageResult<T> result = new PageResult<>(pageRecords, pageNum, total);
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        return result;
    }

    private <T> PageResult<T> emptyPage(PageBody dto) {
        PageResult<T> result = new PageResult<>(Collections.emptyList(), pageNum(dto), 0);
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        return result;
    }

    private int pageNum(PageBody dto) {
        return dto != null && dto.getPageNum() != null && dto.getPageNum() > 0 ? dto.getPageNum() : 1;
    }

    private int pageSize(PageBody dto) {
        return dto != null && dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;
    }

    private String limitClause(int pageNum, int pageSize) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        return "LIMIT " + offset + ", " + pageSize;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private long nvl(Long value) {
        return value != null ? value : 0L;
    }
}
