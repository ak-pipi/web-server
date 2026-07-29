package com.niuma.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.niuma.admin.entity.Agency;
import com.niuma.admin.entity.Player;
import com.niuma.admin.entity.PlayerAgentBind;
import com.niuma.admin.entity.SysUserAgent;
import com.niuma.admin.mapper.AgencyMapper;
import com.niuma.admin.mapper.PlayerAgentBindMapper;
import com.niuma.admin.mapper.PlayerMapper;
import com.niuma.admin.mapper.SysUserAgentMapper;
import com.niuma.common.exception.http.ForbiddenException;
import com.niuma.common.utils.SecurityUtils;
import com.niuma.common.utils.StringUtils;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台代理账号的数据范围辅助。
 * 超级管理员和未绑定代理身份的普通后台账号不在这里额外收窄；
 * 已绑定代理身份的后台账号只能访问自身代理线路内的数据。
 */
@Service
public class AgencyScopeSupport {
    @Autowired
    private SysUserAgentMapper sysUserAgentMapper;

    @Autowired
    private AgencyMapper agencyMapper;

    @Autowired
    private PlayerAgentBindMapper playerAgentBindMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Data
    public static class Scope {
        private Long userId;
        private String agentPlayerId;
        private String pathPrefix;
    }

    public Optional<Scope> resolveCurrentAgentScope() {
        Long userId = SecurityUtils.getUserId();
        if (SecurityUtils.isAdmin(userId)) {
            return Optional.empty();
        }

        SysUserAgent relation = sysUserAgentMapper.selectOne(
                Wrappers.lambdaQuery(SysUserAgent.class)
                        .eq(SysUserAgent::getUserId, userId)
                        .eq(SysUserAgent::getStatus, SysUserAgent.STATUS_NORMAL)
                        .orderByDesc(SysUserAgent::getId)
                        .last("LIMIT 1"));
        if (relation == null) {
            SysUserAgent disabled = sysUserAgentMapper.selectOne(
                    Wrappers.lambdaQuery(SysUserAgent.class)
                            .eq(SysUserAgent::getUserId, userId)
                            .orderByDesc(SysUserAgent::getId)
                            .last("LIMIT 1"));
            if (disabled != null) {
                throw new ForbiddenException("当前后台账号的代理身份已停用");
            }
            return Optional.empty();
        }

        Agency agency = getAgency(relation.getPlayerId());
        if (agency == null || isAgencyDisabled(agency)) {
            throw new ForbiddenException("当前后台账号绑定的代理不存在或已停用");
        }

        Scope scope = new Scope();
        scope.setUserId(userId);
        scope.setAgentPlayerId(agency.getPlayerId());
        scope.setPathPrefix(resolvePath(agency));
        return Optional.of(scope);
    }

    public boolean canAccessPlayer(String playerId) {
        Optional<Scope> scope = resolveCurrentAgentScope();
        return !scope.isPresent() || isPlayerInScope(playerId, scope.get());
    }

    public void ensureCanAccessPlayer(String playerId) {
        if (StringUtils.isEmpty(playerId)) {
            return;
        }
        if (!canAccessPlayer(playerId)) {
            throw new ForbiddenException("不能访问当前代理线路外的玩家数据");
        }
    }

    public Optional<Set<String>> currentScopePlayerIds() {
        Optional<Scope> scope = resolveCurrentAgentScope();
        if (!scope.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(scopePlayerIds(scope.get()));
    }

    public Set<String> scopePlayerIds(Scope scope) {
        Set<String> ids = new LinkedHashSet<>();
        List<String> agentIds = scopeAgentIds(scope);
        ids.addAll(agentIds);

        if (!agentIds.isEmpty()) {
            List<PlayerAgentBind> binds = playerAgentBindMapper.selectList(
                    Wrappers.lambdaQuery(PlayerAgentBind.class)
                            .in(PlayerAgentBind::getAgentPlayerId, agentIds)
                            .eq(PlayerAgentBind::getStatus, PlayerAgentBind.STATUS_ACTIVE));
            for (PlayerAgentBind bind : binds) {
                ids.add(bind.getPlayerId());
            }

            List<Player> legacyPlayers = playerMapper.selectList(
                    Wrappers.lambdaQuery(Player.class)
                            .in(Player::getAgencyId, agentIds));
            for (Player player : legacyPlayers) {
                ids.add(player.getId());
            }
        }
        return ids;
    }

    public List<String> scopeAgentIds(Scope scope) {
        Set<String> allIds = new LinkedHashSet<>();
        allIds.add(scope.getAgentPlayerId());

        List<Agency> agencies = agencyMapper.selectList(
                Wrappers.lambdaQuery(Agency.class)
                        .likeRight(Agency::getPath, scope.getPathPrefix()));
        allIds.addAll(agencies.stream().map(Agency::getPlayerId).collect(Collectors.toList()));

        Queue<String> queue = new ArrayDeque<>();
        queue.add(scope.getAgentPlayerId());
        while (!queue.isEmpty()) {
            String parentId = queue.poll();
            List<Agency> children = agencyMapper.selectList(
                    Wrappers.lambdaQuery(Agency.class)
                            .eq(Agency::getSuperiorId, parentId));
            for (Agency child : children) {
                if (child == null || StringUtils.isEmpty(child.getPlayerId())) {
                    continue;
                }
                if (allIds.add(child.getPlayerId())) {
                    queue.add(child.getPlayerId());
                }
            }
        }
        return new ArrayList<>(allIds);
    }

    public boolean isPlayerInScope(String playerId, Scope scope) {
        if (StringUtils.isEmpty(playerId)) {
            return false;
        }
        Agency agency = getAgency(playerId);
        if (agency != null) {
            return isAgencyInScope(agency, scope);
        }

        PlayerAgentBind bind = playerAgentBindMapper.selectOne(
                Wrappers.lambdaQuery(PlayerAgentBind.class)
                        .eq(PlayerAgentBind::getPlayerId, playerId)
                        .eq(PlayerAgentBind::getStatus, PlayerAgentBind.STATUS_ACTIVE)
                        .last("LIMIT 1"));
        if (bind != null) {
            Agency agent = getAgency(bind.getAgentPlayerId());
            return isAgencyInScope(agent, scope);
        }

        PlayerAgentBind latestBind = playerAgentBindMapper.selectOne(
                Wrappers.lambdaQuery(PlayerAgentBind.class)
                        .eq(PlayerAgentBind::getPlayerId, playerId)
                        .orderByDesc(PlayerAgentBind::getId)
                        .last("LIMIT 1"));
        if (latestBind != null) {
            Agency agent = getAgency(latestBind.getAgentPlayerId());
            return isAgencyInScope(agent, scope);
        }

        Player player = playerMapper.selectById(playerId);
        if (player != null && StringUtils.isNotEmpty(player.getAgencyId())) {
            Agency agent = getAgency(player.getAgencyId());
            return isAgencyInScope(agent, scope);
        }
        return false;
    }

    private boolean isAgencyInScope(Agency agency, Scope scope) {
        if (agency == null || scope == null) {
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

    private boolean isAgencyDisabled(Agency agency) {
        return agency.getStatus() != null && Agency.STATUS_DISABLED == agency.getStatus();
    }

    private String resolvePath(Agency agency) {
        if (agency == null) {
            return "/" + Agency.ROOT_PLAYER_ID + "/";
        }
        if (StringUtils.isNotEmpty(agency.getPath())) {
            return agency.getPath();
        }
        if (StringUtils.isEmpty(agency.getSuperiorId()) || Agency.ROOT_PLAYER_ID.equals(agency.getSuperiorId())) {
            return "/" + Agency.ROOT_PLAYER_ID + "/" + agency.getPlayerId() + "/";
        }
        Agency superior = getAgency(agency.getSuperiorId());
        return resolvePath(superior) + agency.getPlayerId() + "/";
    }

    private Agency getAgency(String playerId) {
        if (StringUtils.isEmpty(playerId) || Agency.ROOT_PLAYER_ID.equals(playerId)) {
            return null;
        }
        return agencyMapper.selectOne(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getPlayerId, playerId)
                        .last("LIMIT 1"));
    }
}
