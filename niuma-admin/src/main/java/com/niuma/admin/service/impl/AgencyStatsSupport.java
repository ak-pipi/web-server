package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.niuma.admin.dto.AgencyGameStatDTO;
import com.niuma.admin.dto.AgencyStatsDTO;
import com.niuma.admin.dto.AgencyStatsPageResult;
import com.niuma.admin.dto.AgencyStatsQueryDTO;
import com.niuma.admin.entity.Agency;
import com.niuma.admin.entity.Player;
import com.niuma.admin.mapper.AgencyMapper;
import com.niuma.admin.mapper.GameRegionalRecordMapper;
import com.niuma.admin.mapper.PlayerMapper;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private GameRegionalRecordMapper gameRegionalRecordMapper;

    public PageResult<AgencyStatsDTO> statsPage(AgencyStatsQueryDTO dto, String parentPlayerId, String parentNickname) {
        String statType = normalizeStatType(dto.getStatType());
        String keyword = StringUtils.trim(dto.getKeyword()).toLowerCase(Locale.ROOT);
        DateRange statRange = parseDateRange(dto);
        AgencyStatsDTO selfStats = buildSelfStats(parentPlayerId, parentNickname, statRange);
        List<AgencyStatsDTO> rows;
        if (STAT_TYPE_GROUP.equals(statType)) {
            rows = queryDirectAgencyRows(parentPlayerId, parentNickname, keyword);
        } else if (isDownlineMemberView(dto, parentPlayerId)) {
            rows = queryAgencyTreeMemberRows(parentPlayerId, parentNickname, keyword);
        } else {
            rows = queryDirectMemberRows(parentPlayerId, parentNickname, keyword);
        }
        rows.sort(Comparator.comparing(AgencyStatsDTO::getPlayerId, Comparator.nullsLast(String::compareTo)));
        if (STAT_TYPE_GROUP.equals(statType)) {
            fillAgencyTeamGameStats(rows, statRange);
        } else {
            fillGameStats(rows, statRange);
        }

        int total = rows.size();
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 10 : dto.getPageSize();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<AgencyStatsDTO> records = from >= total ? Collections.emptyList() : new ArrayList<>(rows.subList(from, to));

        AgencyStatsPageResult result = new AgencyStatsPageResult();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(pageNum);
        result.setTotal(total);
        result.setRecords(records);
        result.setTotalScoreDelta(rows.stream().mapToLong(row -> nvl(row.getScoreDelta())).sum());
        result.setTotalRounds(rows.stream().mapToLong(row -> nvl(row.getRoundCount())).sum());
        result.setSelfStats(selfStats);
        return result;
    }

    public String normalizeStatType(String statType) {
        String value = StringUtils.isNotEmpty(statType) ? statType.trim().toLowerCase(Locale.ROOT) : STAT_TYPE_MEMBER;
        return STAT_TYPE_GROUP.equals(value) || "partner".equals(value) ? STAT_TYPE_GROUP : STAT_TYPE_MEMBER;
    }

    private boolean isDownlineMemberView(AgencyStatsQueryDTO dto, String parentPlayerId) {
        return StringUtils.isNotEmpty(dto.getParentPlayerId()) && !Agency.ROOT_PLAYER_ID.equals(parentPlayerId);
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

    private List<AgencyStatsDTO> queryAgencyTreeMemberRows(String parentPlayerId, String parentNickname, String keyword) {
        Agency rootAgency = agencyMapper.selectOne(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getPlayerId, parentPlayerId)
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus)));
        if (rootAgency == null) {
            return Collections.emptyList();
        }
        Map<String, String> ownerByAgencyId = queryAgencyTreeOwners(Collections.singletonList(rootAgency));
        if (ownerByAgencyId.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> playerIds = new LinkedHashSet<>(ownerByAgencyId.keySet());
        List<Player> downlinePlayers = playerMapper.selectList(
                Wrappers.lambdaQuery(Player.class)
                        .in(Player::getAgencyId, ownerByAgencyId.keySet())
                        .and(w -> w.eq(Player::getDelFlag, 0).or().isNull(Player::getDelFlag)));
        downlinePlayers.stream()
                .map(Player::getId)
                .filter(StringUtils::isNotEmpty)
                .forEach(playerIds::add);
        if (playerIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Player> players = playerMapper.selectBatchIds(playerIds);
        Map<String, Agency> agencyMap = agencyMapper.selectList(
                        Wrappers.lambdaQuery(Agency.class)
                                .in(Agency::getPlayerId, playerIds)
                                .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus)))
                .stream()
                .collect(Collectors.toMap(Agency::getPlayerId, a -> a, (a, b) -> a));

        List<AgencyStatsDTO> rows = new ArrayList<>();
        for (Player player : players) {
            if (player == null || (player.getDelFlag() != null && player.getDelFlag() != 0)) {
                continue;
            }
            if (parentPlayerId.equals(player.getId())) {
                continue;
            }
            AgencyStatsDTO row = toStatsDTO(player, agencyMap.get(player.getId()), parentPlayerId, parentNickname);
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
        dto.setScoreDelta(0L);
        dto.setRoundCount(0L);
        dto.setTotalConsume(0L);
        dto.setGiftReceived(0L);
        dto.setHasChildren(agency != null);
        dto.setSelf(false);
        return dto;
    }

    private AgencyStatsDTO buildSelfStats(String parentPlayerId, String parentNickname, DateRange statRange) {
        if (StringUtils.isEmpty(parentPlayerId) || Agency.ROOT_PLAYER_ID.equals(parentPlayerId)) {
            return null;
        }
        Player player = playerMapper.selectById(parentPlayerId);
        if (player == null || (player.getDelFlag() != null && player.getDelFlag() != 0)) {
            return null;
        }
        Agency agency = agencyMapper.selectOne(
                Wrappers.lambdaQuery(Agency.class)
                        .eq(Agency::getPlayerId, parentPlayerId)
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus)));
        AgencyStatsDTO row = toStatsDTO(player, agency, parentPlayerId, parentNickname);
        row.setSelf(true);
        applyGameStats(row, queryGameStatMap(Collections.singletonList(parentPlayerId), statRange).get(parentPlayerId));
        return row;
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

    private void fillGameStats(List<AgencyStatsDTO> rows, DateRange statRange) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> playerIds = rows.stream().map(AgencyStatsDTO::getPlayerId).collect(Collectors.toList());
        Map<String, AgencyGameStatDTO> statMap = queryGameStatMap(playerIds, statRange);

        for (AgencyStatsDTO row : rows) {
            AgencyGameStatDTO stat = statMap.get(row.getPlayerId());
            applyGameStats(row, stat);
        }
    }

    private void fillAgencyTeamGameStats(List<AgencyStatsDTO> rows, DateRange statRange) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> rootAgencyIds = rows.stream().map(AgencyStatsDTO::getPlayerId).collect(Collectors.toList());
        List<Agency> rootAgencies = agencyMapper.selectList(
                Wrappers.lambdaQuery(Agency.class)
                        .in(Agency::getPlayerId, rootAgencyIds)
                        .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus)));
        if (rootAgencies.isEmpty()) {
            return;
        }

        Map<String, String> ownerByAgencyId = queryAgencyTreeOwners(rootAgencies);
        if (ownerByAgencyId.isEmpty()) {
            return;
        }

        Map<String, String> ownerByPlayerId = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : ownerByAgencyId.entrySet()) {
            ownerByPlayerId.put(entry.getKey(), entry.getValue());
        }

        List<Player> players = playerMapper.selectList(
                Wrappers.lambdaQuery(Player.class)
                        .in(Player::getAgencyId, ownerByAgencyId.keySet())
                        .and(w -> w.eq(Player::getDelFlag, 0).or().isNull(Player::getDelFlag)));
        for (Player player : players) {
            String ownerId = ownerByAgencyId.get(player.getAgencyId());
            if (ownerId != null) {
                ownerByPlayerId.put(player.getId(), ownerId);
            }
        }

        Map<String, AgencyGameStatDTO> statMap = queryGameStatMap(new ArrayList<>(ownerByPlayerId.keySet()), statRange);
        Map<String, long[]> totalsByAgency = new HashMap<>();
        for (Map.Entry<String, String> entry : ownerByPlayerId.entrySet()) {
            AgencyGameStatDTO stat = statMap.get(entry.getKey());
            if (stat == null) {
                continue;
            }
            long[] totals = totalsByAgency.computeIfAbsent(entry.getValue(), id -> new long[2]);
            totals[0] += nvl(stat.getTotalScoreDelta());
            totals[1] += nvl(stat.getRoundCount());
        }

        for (AgencyStatsDTO row : rows) {
            long[] totals = totalsByAgency.get(row.getPlayerId());
            row.setScore(totals == null ? 0L : totals[0]);
            row.setScoreDelta(totals == null ? 0L : totals[0]);
            row.setRoundCount(totals == null ? 0L : totals[1]);
        }
    }

    private Map<String, String> queryAgencyTreeOwners(List<Agency> rootAgencies) {
        Map<String, String> ownerByAgencyId = new LinkedHashMap<>();
        List<Agency> frontier = new ArrayList<>();
        for (Agency agency : rootAgencies) {
            if (StringUtils.isEmpty(agency.getPlayerId()) || ownerByAgencyId.containsKey(agency.getPlayerId())) {
                continue;
            }
            ownerByAgencyId.put(agency.getPlayerId(), agency.getPlayerId());
            frontier.add(agency);
        }

        while (!frontier.isEmpty()) {
            List<String> parentIds = frontier.stream()
                    .map(Agency::getPlayerId)
                    .filter(StringUtils::isNotEmpty)
                    .collect(Collectors.toList());
            if (parentIds.isEmpty()) {
                break;
            }
            List<Agency> children = agencyMapper.selectList(
                    Wrappers.lambdaQuery(Agency.class)
                            .in(Agency::getSuperiorId, parentIds)
                            .and(w -> w.eq(Agency::getStatus, Agency.STATUS_NORMAL).or().isNull(Agency::getStatus)));
            frontier = new ArrayList<>();
            Set<String> seenThisLevel = new LinkedHashSet<>();
            for (Agency child : children) {
                String childPlayerId = child.getPlayerId();
                String ownerId = ownerByAgencyId.get(child.getSuperiorId());
                if (StringUtils.isEmpty(childPlayerId) || StringUtils.isEmpty(ownerId)
                        || ownerByAgencyId.containsKey(childPlayerId) || !seenThisLevel.add(childPlayerId)) {
                    continue;
                }
                ownerByAgencyId.put(childPlayerId, ownerId);
                frontier.add(child);
            }
        }
        return ownerByAgencyId;
    }

    private Map<String, AgencyGameStatDTO> queryGameStatMap(List<String> playerIds, DateRange statRange) {
        if (playerIds == null || playerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AgencyGameStatDTO> stats = gameRegionalRecordMapper.sumAgencyGameStatsByPlayerIds(
                playerIds, statRange.getStartTime(), statRange.getEndTime());
        return stats.stream()
                .collect(Collectors.toMap(AgencyGameStatDTO::getPlayerId, s -> s, (a, b) -> a));
    }

    private void applyGameStats(AgencyStatsDTO row, AgencyGameStatDTO stat) {
        long scoreDelta = stat == null ? 0L : nvl(stat.getTotalScoreDelta());
        long roundCount = stat == null ? 0L : nvl(stat.getRoundCount());
        row.setScore(scoreDelta);
        row.setScoreDelta(scoreDelta);
        row.setRoundCount(roundCount);
    }

    private DateRange parseDateRange(AgencyStatsQueryDTO dto) {
        LocalDateTime startTime = parseDateTime(dto.getStartTime(), true);
        LocalDateTime endTime = parseDateTime(dto.getEndTime(), false);
        if (startTime != null || endTime != null) {
            LocalDate today = LocalDate.now();
            if (startTime == null) {
                startTime = today.atStartOfDay();
            }
            if (endTime == null) {
                endTime = startTime.plusDays(1);
            }
            if (!endTime.isAfter(startTime)) {
                endTime = startTime.plusDays(1);
            }
            return new DateRange(startTime, endTime);
        }

        LocalDate statDate = parseDate(dto.getDate());
        LocalDateTime dayStart = statDate.atStartOfDay();
        return new DateRange(dayStart, dayStart.plusDays(1));
    }

    private LocalDateTime parseDateTime(String value, boolean startOfDay) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.length() == 10) {
                LocalDate date = LocalDate.parse(text);
                return startOfDay ? date.atStartOfDay() : date.plusDays(1).atStartOfDay();
            }
            return LocalDateTime.parse(text.replace(" ", "T"));
        } catch (Exception ex) {
            return null;
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

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private static class DateRange {
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;

        private DateRange(LocalDateTime startTime, LocalDateTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        private LocalDateTime getStartTime() {
            return startTime;
        }

        private LocalDateTime getEndTime() {
            return endTime;
        }
    }
}
