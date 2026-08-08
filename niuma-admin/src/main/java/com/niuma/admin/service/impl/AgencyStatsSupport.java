package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.niuma.admin.dto.AgencyStatAmountDTO;
import com.niuma.admin.dto.AgencyStatsDTO;
import com.niuma.admin.dto.AgencyStatsQueryDTO;
import com.niuma.admin.entity.Agency;
import com.niuma.admin.entity.Capital;
import com.niuma.admin.entity.Player;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.AgencyMapper;
import com.niuma.admin.mapper.CapitalMapper;
import com.niuma.admin.mapper.PlayerMapper;
import com.niuma.admin.mapper.WalletLedgerMapper;
import com.niuma.common.constant.ResultCodeEnum;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 代理统计统一聚合逻辑，供 Cocos 玩家端和 web_ui 后台共用。
 */
@Service
public class AgencyStatsSupport {
    public static final String STAT_TYPE_GROUP = "group";
    public static final String STAT_TYPE_MEMBER = "member";

    @Autowired
    private AgencyMapper agencyMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private CapitalMapper capitalMapper;

    @Autowired
    private WalletLedgerMapper walletLedgerMapper;

    public PageResult<AgencyStatsDTO> statsPage(AgencyStatsQueryDTO dto, String parentPlayerId, String parentNickname) {
        String statType = normalizeStatType(dto.getStatType());
        String keyword = StringUtils.trim(dto.getKeyword()).toLowerCase(Locale.ROOT);
        LocalDate statDate = parseDate(dto.getDate());
        List<AgencyStatsDTO> rows = STAT_TYPE_GROUP.equals(statType)
                ? queryDirectAgencyRows(parentPlayerId, parentNickname, keyword)
                : queryDirectMemberRows(parentPlayerId, parentNickname, keyword);
        rows.sort(Comparator.comparing(AgencyStatsDTO::getPlayerId, Comparator.nullsLast(String::compareTo)));

        int total = rows.size();
        int from = Math.max(0, (dto.getPageNum() - 1) * dto.getPageSize());
        int to = Math.min(total, from + dto.getPageSize());
        List<AgencyStatsDTO> records = from >= total ? Collections.emptyList() : new ArrayList<>(rows.subList(from, to));
        fillAmounts(records, statDate);

        PageResult<AgencyStatsDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }

    public String normalizeStatType(String statType) {
        String value = StringUtils.isNotEmpty(statType) ? statType.trim().toLowerCase(Locale.ROOT) : STAT_TYPE_GROUP;
        return STAT_TYPE_MEMBER.equals(value) ? STAT_TYPE_MEMBER : STAT_TYPE_GROUP;
    }

    private List<AgencyStatsDTO> queryDirectAgencyRows(String parentPlayerId, String parentNickname, String keyword) {
        List<Agency> agencies = agencyMapper.selectList(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getSuperiorId, parentPlayerId)
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus))
                        .orderByAsc(Agency::getDepth).orderByAsc(Agency::getId));
        if (agencies.isEmpty()) {
            return Collections.emptyList();
        }
        List<Player> players = playerMapper.selectBatchIds(
                agencies.stream().map(Agency::getPlayerId).collect(Collectors.toList()));
        Map<String, Player> playerMap = players.stream()
                .collect(Collectors.toMap(Player::getId, p -> p, (a, b) -> a, LinkedHashMap::new));

        List<AgencyStatsDTO> rows = new ArrayList<>();
        for (Agency agency : agencies) {
            Player player = playerMap.get(agency.getPlayerId());
            if (player == null) {
                continue;
            }
            AgencyStatsDTO row = toStatsDTO(player, agency, parentPlayerId, parentNickname);
            if (matchesKeyword(row, keyword)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<AgencyStatsDTO> queryDirectMemberRows(String parentPlayerId, String parentNickname, String keyword) {
        List<Player> players = playerMapper.selectList(
                Wrappers.lambdaQuery(Player.class)
                        .eq(Player::getAgencyId, parentPlayerId)
                        .and(w -> w.eq(Player::getDelFlag, 0).or().isNull(Player::getDelFlag)));
        if (players.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> agencyPlayerIds = agencyMapper.selectList(
                        Wrappers.lambdaQuery(Agency.class)
                                .in(Agency::getPlayerId, players.stream().map(Player::getId).collect(Collectors.toList())))
                .stream()
                .map(Agency::getPlayerId)
                .collect(Collectors.toSet());

        List<AgencyStatsDTO> rows = new ArrayList<>();
        for (Player player : players) {
            if (agencyPlayerIds.contains(player.getId())) {
                continue;
            }
            AgencyStatsDTO row = toStatsDTO(player, null, parentPlayerId, parentNickname);
            if (matchesKeyword(row, keyword)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private AgencyStatsDTO toStatsDTO(Player player, Agency agency, String parentPlayerId, String parentNickname) {
        AgencyStatsDTO dto = new AgencyStatsDTO();
        dto.setPlayerId(player.getId());
        dto.setNickname(player.getNickname());
        dto.setAccount(player.getName());
        dto.setAvatar(player.getAvatar());
        dto.setRole(agency == null ? STAT_TYPE_MEMBER : STAT_TYPE_GROUP);
        dto.setRoleText(agency == null ? "成员" : "合伙人");
        dto.setIdentity(agency == null ? "成员" : "合伙人");
        dto.setAgentType(agency != null ? agency.getAgentType() : null);
        dto.setLevel(agency != null ? agency.getLevel() : 0);
        dto.setJuniorCount(agency != null && agency.getJuniorCount() != null ? agency.getJuniorCount() : 0);
        dto.setParentPlayerId(parentPlayerId);
        dto.setParentNickname(parentNickname);
        dto.setScore(0L);
        dto.setTotalConsume(0L);
        dto.setGiftReceived(0L);
        dto.setHasChildren(agency != null);
        return dto;
    }

    private boolean matchesKeyword(AgencyStatsDTO row, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return true;
        }
        return contains(row.getPlayerId(), keyword)
                || contains(row.getNickname(), keyword)
                || contains(row.getAccount(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.isNotEmpty(value) && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void fillAmounts(List<AgencyStatsDTO> records, LocalDate statDate) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> playerIds = records.stream().map(AgencyStatsDTO::getPlayerId).collect(Collectors.toList());
        List<Capital> capitals = capitalMapper.selectBatchIds(playerIds);
        Map<String, Long> scoreMap = capitals.stream()
                .collect(Collectors.toMap(Capital::getPlayerId, c -> c.getGold() == null ? 0L : c.getGold(), (a, b) -> a));

        LocalDateTime start = statDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<AgencyStatAmountDTO> amounts = walletLedgerMapper.sumAgencyStatsByUserIds(
                playerIds,
                WalletType.GOLD.getCode(),
                LedgerBizType.ADMIN_ADJUST.getCode(),
                start,
                end);
        Map<String, AgencyStatAmountDTO> amountMap = amounts.stream()
                .collect(Collectors.toMap(AgencyStatAmountDTO::getPlayerId, a -> a, (a, b) -> a));

        for (AgencyStatsDTO record : records) {
            record.setScore(scoreMap.getOrDefault(record.getPlayerId(), 0L));
            AgencyStatAmountDTO amount = amountMap.get(record.getPlayerId());
            if (amount != null) {
                record.setTotalConsume(amount.getTotalConsume() == null ? 0L : amount.getTotalConsume());
                record.setGiftReceived(amount.getGiftReceived() == null ? 0L : amount.getGiftReceived());
            }
        }
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
}
