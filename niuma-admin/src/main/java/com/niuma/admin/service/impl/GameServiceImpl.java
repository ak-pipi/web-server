package com.niuma.admin.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.niuma.admin.constant.NiuMaCodeEnum;
import com.niuma.admin.constant.NiuMaConstants;
import com.niuma.admin.constant.NiuMaRedisKeys;
import com.niuma.admin.data.*;
import com.niuma.admin.dto.*;
import com.niuma.admin.entity.*;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.*;
import com.niuma.admin.rabbit.RabbitSender;
import com.niuma.admin.service.IGameRecordRetentionService;
import com.niuma.admin.service.IGameService;
import com.niuma.admin.service.IWalletService;
import com.niuma.admin.utils.JsonUtils;
import com.niuma.common.constant.ResultCodeEnum;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.core.redis.RedisCache;
import com.niuma.common.core.redis.RedisPrimitive;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;
import com.niuma.common.exception.http.*;
import com.niuma.common.utils.CommonUtils;
import com.niuma.common.utils.PlayerSecurityUtils;
import com.niuma.common.utils.StringUtils;
import com.niuma.common.utils.sign.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class GameServiceImpl implements IGameService {
    private static final int DOUDIZHU_HAND_CARD_COUNT = 17;
    private static final int DOUDIZHU_BOTTOM_CARD_COUNT = 3;
    private static final int DOUDIZHU_AUTO_PLAY_TIMEOUT = 180000;
    private static final long MIN_CARRY_SCORE_MULTIPLIER = 8L;

    /**
     * 用于Java内部数据类型的缓存
     */
    @Autowired
    private RedisCache redisCache;

    /**
     * 用于支持跨平台的数据缓存
     */
    @Autowired
    private RedisPrimitive redisPrimitive;

    @Autowired
    private RabbitSender rabbitSender;

    @Value("${rabbitmq.game.exchange}")
    private String gameExchange;

    @Value("${rabbitmq.game.routingKey}")
    private String gameRoutingKey;

    @Autowired
    private JsonUtils jsonUtils;

    @Resource
    private VenueMapper venueMapper;

    @Autowired
    private CapitalMapper capitalMapper;

    @Resource
    private GameDumbMapper gameDumbMapper;

    @Resource
    private GameMahjongMapper mahjongMapper;

    @Resource
    private GameBiJiMapper biJiMapper;

    @Resource
    private GameLackeyMapper lackeyMapper;

    @Resource
    private GameNiu100Mapper niu100Mapper;

    @Resource
    private GameDoudizhuMapper doudizhuMapper;

    @Resource
    private GameGuanDanMapper guanDanMapper;

    @Resource
    private GameTaojiangMahjongMapper taojiangMahjongMapper;

    @Resource
    private GameHongzhongMahjongMapper hongzhongMahjongMapper;

    @Resource
    private GamePaodekuaiMapper paodekuaiMapper;

    @Resource
    private GameChangshaMahjongMapper changshaMahjongMapper;

    @Resource
    private GameYiyangWaihuziMapper yiyangWaihuziMapper;

    @Resource
    private GameYuanjiangQianfenMapper yuanjiangQianfenMapper;

    @Resource
    private GameTaojiangMahjongRecordMapper taojiangMahjongRecordMapper;

    @Resource
    private GameRegionalRecordMapper gameRegionalRecordMapper;

    @Resource
    private GameFaultMapper gameFaultMapper;

    @Resource
    private PlayerMapper playerMapper;

    @Resource
    private DistrictMapper districtMapper;

    @Resource
    private IGameRecordRetentionService gameRecordRetentionService;

    @Autowired
    private WalletChangeEventProcessor walletChangeEventProcessor;

    @Autowired
    private IWalletService walletService;

    // 异步命令映射表
    private Map<String, MqCommandDeferred> commandDeferredMap = new HashMap<>();

    // 异步命令按创建时间先后的排序序列
    private LinkedList<String> commandDeferredSequence = new LinkedList<>();

    // 线程锁
    private Lock lock = new ReentrantLock();

    @FunctionalInterface
    private interface BeforeEnterCallback {
        void invoke(String playerId, String venueId);
    }

    @FunctionalInterface
    private interface RegionalRecordCounter {
        Integer count(String playerId, LocalDateTime cutoff);
    }

    @FunctionalInterface
    private interface RegionalRecordPager {
        List<GameRegionalRecord> get(String playerId, LocalDateTime cutoff, Integer offset, Integer pageSize);
    }

    @FunctionalInterface
    private interface RegionalRecordGetter {
        GameRegionalRecord get(Long id);
    }

    @FunctionalInterface
    private interface RegionalPlaybackGetter {
        String get(Long id);
    }

    @FunctionalInterface
    private interface RegionalNumberGetter {
        String get(String venueId);
    }

    /**
     * 进入(创建)场地前检查
     * @param playerId 玩家id
     * @param venueId 目标场地id
     * @return 异步动作
     */
    private MqCommandDeferred checkBeforeEnter(String playerId, String venueId, BeforeEnterCallback callback) {
        if (StringUtils.isEmpty(playerId))
            throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), "Current login player is null, this is unexpected");
        String lockKey = NiuMaRedisKeys.PLAYER_ENTER_LOCK + playerId;
        Long ret = this.redisPrimitive.incr(lockKey, 1L);
        if (ret == null)
            throw new InternalServerException(ResultCodeEnum.REDIS_ACCESS_ERROR);
        if (!ret.equals(1L))
            throw new ForbiddenException(NiuMaCodeEnum.PLAYER_ENTER_CONFLICT);
        this.redisPrimitive.expire(lockKey, 5L, TimeUnit.SECONDS);
        boolean test = false;
        String enterKey = NiuMaRedisKeys.PLAYER_ENTER_DATA + playerId;
        PlayerEnter enterData = this.redisCache.getCacheObject(enterKey);
        if (enterData != null) {
            if ((venueId != null) && venueId.equals(enterData.getAuthorizedVenue()))
                test = true;
            if (!test) {
                Long nowTime = System.currentTimeMillis();
                Long delta = nowTime - enterData.getAuthorizedTime();
                if (delta < 1000L) {
                    // 太过频繁请求进入不同的场地
                    this.redisPrimitive.delete(lockKey);
                    throw new ForbiddenException(NiuMaCodeEnum.PLAYER_ENTER_FREQUENTLY);
                }
                if (delta > 300000L) {
                    // 授权数据超过5分钟，属于历史残留，清理掉
                    this.redisCache.deleteObject(enterKey);
                }
            }
        }
        if (!test) {
            String venueKey = NiuMaRedisKeys.PLAYER_CURRENT_VENUE + playerId;
            String currentVenue = this.redisPrimitive.get(venueKey);
            if (currentVenue != null) {
                if ((venueId == null) || !(venueId.equals(currentVenue))) {
                    // 异步离开玩家当前所在场地
                    MqCommandDeferred actionDeferred = this.leaveCurrentVenue(playerId, currentVenue);
                    if (actionDeferred != null) {
                        return actionDeferred;
                    }
                }
            }
        }
        try {
            // 可以直接创建或者进入指定场地
            if (callback != null)
                callback.invoke(playerId, venueId);
        } catch (HttpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), ex.getMessage());
        } finally {
            this.redisPrimitive.delete(lockKey);
        }
        return null;
    }

    /**
     * 离开玩家当前所在场地
     * @param playerId 玩家id
     * @param currentVenue 当前所在场地id
     * @return 异步动作
     */
    private MqCommandDeferred leaveCurrentVenue(String playerId, String currentVenue) {
        // 查询当前场地所在的服务器ID
        String redisKey = NiuMaRedisKeys.VENUE_SERVER_MAP + currentVenue;
        String routingKey = this.redisPrimitive.get(redisKey);
        if (StringUtils.isEmpty(routingKey)) {
            // 场地映射已丢失，清理脏数据
            clearPlayerVenueCache(playerId, currentVenue);
            return null;
        }
        // 查询服务器是否在线
        redisKey = NiuMaRedisKeys.SERVER_KEEP_ALIVE + routingKey;
        boolean test = false;
        Long timestamp = this.redisPrimitive.getLong(redisKey);
        if (timestamp != null) {
            Long nowTime = System.currentTimeMillis();
            nowTime /= 1000L;
            Long delta = nowTime - timestamp;
            if (delta > 30L)
                test = true;   // 最近30秒都没有更新，说明服务器可能离线了
        } else
            test = true;
        if (test) {
            // 记录游戏故障，以便后续在后台手动解决故障
            Integer count = this.gameFaultMapper.hasGameFault(currentVenue, routingKey);
            if (!CommonUtils.predicate(count)) {
                GameFault gameFault = new GameFault();
                gameFault.setVenueId(currentVenue);
                gameFault.setServerId(routingKey);
                gameFault.setProcessed(0);
                gameFault.setTime(LocalDateTime.now());
                this.gameFaultMapper.insert(gameFault);
            }
            log.error("Player(id: {}) try leave current venue(id: {}) which on server(id: {}), but the server if offline.", playerId, currentVenue, routingKey);
            // 清理残留的Redis缓存，避免玩家下次请求被卡住
            clearPlayerVenueCache(playerId, currentVenue);
            throw new InternalServerException(NiuMaCodeEnum.SERVER_INACCESSIBLE);
        }
        MqCommandDeferred actionDeferred = createCommandDeferred(playerId);
        actionDeferred.setCurrentVenue(currentVenue);
        // 发送MQ消息通知玩家离开当前场地
        MqLeaveVenue cmd = new MqLeaveVenue();
        cmd.setPlayerId(playerId);
        cmd.setVenueId(currentVenue);
        cmd.setCommandId(actionDeferred.getCommandId());
        cmd.setRoutingKey(this.gameRoutingKey);
        String json = this.jsonUtils.convertToStr(cmd);
        String base64 = Base64.encode(json.getBytes());
        MqMessage msg = new MqMessage();
        msg.setMsgType("MsgLeaveVenue");
        msg.setMsgPack(base64);
        this.rabbitSender.sendObject(this.gameExchange, routingKey, msg);
        return actionDeferred;
    }

    /**
     * 清理玩家场地相关的Redis缓存残留
     * 当C++游戏服务器离线或场地映射丢失时，需要清理这些key以避免玩家被卡住。
     * 注意：不清理player_authorized_venue，因为该key由C++服务器在玩家离开场地时清理，
     * 提前清理会导致已进入场地的玩家断线重连时auth检查失败。
     * @param playerId 玩家id
     * @param venueId 场地id（可为null）
     */
    private void clearPlayerVenueCache(String playerId, String venueId) {
        this.redisPrimitive.delete(NiuMaRedisKeys.PLAYER_CURRENT_VENUE + playerId);
        this.redisCache.deleteObject(NiuMaRedisKeys.PLAYER_ENTER_DATA + playerId);
        if (StringUtils.isNotEmpty(venueId)) {
            this.redisPrimitive.delete(NiuMaRedisKeys.VENUE_SERVER_MAP + venueId);
        }
    }

    private boolean hasCommand(String commandId) {
        boolean ret = false;
        try {
            this.lock.lock();
            ret = this.commandDeferredMap.containsKey(commandId);
        } finally {
            this.lock.unlock();
        }
        return ret;
    }

    private static class CommandTester implements CommonUtils.DuplicateTester {
        private GameServiceImpl service;

        public CommandTester(GameServiceImpl service) {
            this.service = service;
        }

        @Override
        public boolean testDuplicate(String code) {
            return this.service.hasCommand(code);
        }
    }

    private MqCommandDeferred createCommandDeferred(String playerId) {
        String commandId = CommonUtils.generateRandomCode(10, CommonUtils.CODE_ALL, new CommandTester(this));
        if (StringUtils.isEmpty(commandId))
            throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), "Generate command id failed");
        MqCommandDeferred cmd = new MqCommandDeferred(playerId, commandId);
        try {
            this.lock.lock();
            this.commandDeferredMap.put(commandId, cmd);
            this.commandDeferredSequence.add(commandId);
        } finally {
            this.lock.unlock();
        }
        return cmd;
    }

    private static class VenueTester implements CommonUtils.DuplicateTester {
        private VenueMapper mapper;

        public VenueTester(VenueMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public boolean testDuplicate(String code) {
            LambdaQueryWrapper<Venue> query = Wrappers.lambdaQuery();
            query.eq(Venue::getId, code);
            Integer count = this.mapper.selectCount(query);
            return CommonUtils.predicate(count);
        }
    }

    private static class MahjongNumberTester implements CommonUtils.DuplicateTester {
        private GameMahjongMapper mapper;

        public MahjongNumberTester(GameMahjongMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class BiJiNumberTester implements CommonUtils.DuplicateTester {
        private GameBiJiMapper mapper;

        public BiJiNumberTester(GameBiJiMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class LackeyNumberTester implements CommonUtils.DuplicateTester {
        private GameLackeyMapper mapper;

        public LackeyNumberTester(GameLackeyMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class Niu100NumberTester implements CommonUtils.DuplicateTester {
        private GameNiu100Mapper mapper;

        public Niu100NumberTester(GameNiu100Mapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class GuanDanNumberTester implements CommonUtils.DuplicateTester {
        private GameGuanDanMapper mapper;

        public GuanDanNumberTester(GameGuanDanMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class DoudizhuNumberTester implements CommonUtils.DuplicateTester {
        private GameDoudizhuMapper mapper;

        public DoudizhuNumberTester(GameDoudizhuMapper mapper) { this.mapper = mapper; }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class TaojiangMahjongNumberTester implements CommonUtils.DuplicateTester {
        private GameTaojiangMahjongMapper mapper;

        public TaojiangMahjongNumberTester(GameTaojiangMahjongMapper mapper) { this.mapper = mapper; }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class HongzhongMahjongNumberTester implements CommonUtils.DuplicateTester {
        private GameHongzhongMahjongMapper mapper;

        public HongzhongMahjongNumberTester(GameHongzhongMahjongMapper mapper) { this.mapper = mapper; }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class PaodekuaiNumberTester implements CommonUtils.DuplicateTester {
        private GamePaodekuaiMapper mapper;

        public PaodekuaiNumberTester(GamePaodekuaiMapper mapper) { this.mapper = mapper; }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class ChangshaMahjongNumberTester implements CommonUtils.DuplicateTester {
        private GameChangshaMahjongMapper mapper;

        public ChangshaMahjongNumberTester(GameChangshaMahjongMapper mapper) { this.mapper = mapper; }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class YiyangWaihuziNumberTester implements CommonUtils.DuplicateTester {
        private GameYiyangWaihuziMapper mapper;

        public YiyangWaihuziNumberTester(GameYiyangWaihuziMapper mapper) { this.mapper = mapper; }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    private static class YuanjiangQianfenNumberTester implements CommonUtils.DuplicateTester {
        private GameYuanjiangQianfenMapper mapper;

        public YuanjiangQianfenNumberTester(GameYuanjiangQianfenMapper mapper) { this.mapper = mapper; }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.hasNumber(code);
            return CommonUtils.predicate(count);
        }
    }

    /**
     * 生成场地id
     * @return 场地id
     */
    private String generateVenueId() {
        String venueId = CommonUtils.generateRandomCode(10, CommonUtils.CODE_ALL, new VenueTester(this.venueMapper));
        if (StringUtils.isEmpty(venueId))
            throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), "Generate venue id failed");
        return venueId;
    }

    /**
     * 生成6位数编号
     * @param tester 重复测试器
     * @return 6位数编号
     */
    private String generateNumber(CommonUtils.DuplicateTester tester) {
        String number = CommonUtils.generateRandomCode(6, CommonUtils.CODE_NUMBER, tester);
        if (StringUtils.isEmpty(number))
            throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), "Generate number failed");
        return number;
    }

    /**
     * 保存创建房间时的玩法配置，供游戏服加载场地使用
     */
    private String resolveRuleConfig(String json) {
        if (StringUtils.isEmpty(json))
            return "{}";
        try {
            JSONObject raw = JSONObject.parseObject(json);
            if (raw == null)
                return "{}";
            if (raw.getInteger("room_fee") == null && raw.getInteger("room_fee_type") == null)
                putRoomFee(raw, resolveDefaultRoomFee(resolvePositiveBaseScore(raw)));
            return raw.toJSONString();
        } catch (Exception ex) {
            return json;
        }
    }

    /**
     * 桃江麻将规则统一由服务端白名单归一化，避免客户端旧参数进入游戏服。
     */
    private String resolveTaojiangRuleConfig(String json) {
        JSONObject raw = StringUtils.isEmpty(json) ? null : JSONObject.parseObject(json);
        if (raw == null)
            raw = new JSONObject();
        JSONObject rule = new JSONObject();
        Integer level = raw.getInteger("level");
        if (level != null)
            rule.put("level", level);

        int roundCount = normalizeTaojiangRoundCount(raw.getInteger("round_count"));
        int baseScore = normalizeTaojiangBaseScore(raw.getInteger("base_score"), roundCount);
        fillTaojiangRuleDefaults(rule, baseScore, roundCount);

        Integer maxScore = raw.getInteger("max_score");
        if (maxScore != null && maxScore > 0)
            rule.put("max_score", maxScore);
        applyRoomFeeOverride(raw, rule);
        return rule.toJSONString();
    }

    private int normalizeTaojiangRoundCount(Integer roundCount) {
        return (roundCount != null && roundCount == 1) ? 1 : 8;
    }

    private int normalizeTaojiangBaseScore(Integer baseScore, int roundCount) {
        int score = baseScore == null ? 0 : baseScore;
        int[] validScores = roundCount == 1
                ? new int[] {5, 10, 25}
                : new int[] {1, 2, 5, 10, 20};
        for (int validScore : validScores) {
            if (score == validScore)
                return score;
        }
        return validScores[0];
    }

    private void fillTaojiangRuleDefaults(JSONObject rule, int baseScore, int roundCount) {
        rule.put("base_score", baseScore);
        rule.put("round_count", roundCount);
        rule.put("max_score", 18);
        rule.put("allow_chi", true);
        rule.put("allow_peng", true);
        rule.put("allow_gang", true);
        rule.put("allow_zimo", true);
        rule.put("allow_dianpao", true);
        rule.put("laizi_enabled", true);
        rule.put("hongzhong_enabled", false);
        rule.put("bao_ting_enabled", true);
        rule.put("dissolve_vote", true);
        rule.put("banker_rule", 0);
        putRoomFee(rule, resolveDefaultRoomFee(baseScore));
    }

    /**
     * 长沙麻将规则统一归一化，服务端与客户端按同一组配置字段工作。
     */
    private String resolveChangshaRuleConfig(String json) {
        JSONObject raw = StringUtils.isEmpty(json) ? null : JSONObject.parseObject(json);
        if (raw == null)
            raw = new JSONObject();
        JSONObject rule = new JSONObject();
        Integer level = raw.getInteger("level");
        if (level != null)
            rule.put("level", level);

        int roundCount = normalizeChangshaRoundCount(raw.getInteger("round_count"));
        int baseScore = normalizeChangshaBaseScore(raw.getInteger("base_score"), roundCount);
        fillChangshaRuleDefaults(rule, baseScore, roundCount);

        Integer maxScore = raw.getInteger("max_score");
        if (maxScore != null && maxScore >= 0)
            rule.put("max_score", maxScore);
        applyRoomFeeOverride(raw, rule);
        Integer maxFan = raw.getInteger("max_fan");
        if (maxFan != null)
            rule.put("max_fan", normalizeChangshaMaxFan(maxFan));
        Integer birdCount = raw.getInteger("bird_count");
        if (birdCount != null)
            rule.put("bird_count", normalizeChangshaBirdCount(birdCount));

        copyBooleanRuleOption(raw, rule, "allow_chi");
        copyBooleanRuleOption(raw, rule, "allow_peng");
        copyBooleanRuleOption(raw, rule, "allow_gang");
        copyBooleanRuleOption(raw, rule, "allow_zimo");
        copyBooleanRuleOption(raw, rule, "allow_dianpao");
        copyBooleanRuleOption(raw, rule, "dissolve_vote");
        copyBooleanRuleOption(raw, rule, "require_258_jiang");
        copyBooleanRuleOption(raw, rule, "queyise_enabled");
        copyBooleanRuleOption(raw, rule, "banbanhu_enabled");
        copyBooleanRuleOption(raw, rule, "dasixi_enabled");
        copyBooleanRuleOption(raw, rule, "liuliushun_enabled");
        copyBooleanRuleOption(raw, rule, "jiejiegao_enabled");
        copyBooleanRuleOption(raw, rule, "santong_enabled");
        copyBooleanRuleOption(raw, rule, "yizhihua_enabled");
        copyBooleanRuleOption(raw, rule, "zhongniao_enabled");
        copyBooleanRuleOption(raw, rule, "bird_double");
        copyBooleanRuleOption(raw, rule, "bird_cap_max");
        return rule.toJSONString();
    }

    private int normalizeChangshaRoundCount(Integer roundCount) {
        return (roundCount != null && roundCount == 1) ? 1 : 8;
    }

    private int normalizeChangshaBaseScore(Integer baseScore, int roundCount) {
        int score = baseScore == null ? 0 : baseScore;
        int[] validScores = roundCount == 1
                ? new int[] {5, 10, 25}
                : new int[] {1, 2, 5, 10, 20};
        for (int validScore : validScores) {
            if (score == validScore)
                return score;
        }
        return validScores[0];
    }

    private int normalizeChangshaMaxFan(Integer maxFan) {
        int fan = maxFan == null ? 0 : maxFan;
        return fan > 0 ? Math.min(fan, 16) : 8;
    }

    private int normalizeChangshaBirdCount(Integer birdCount) {
        int count = birdCount == null ? 0 : birdCount;
        if (count == 0 || count == 1 || count == 2 || count == 4 || count == 6)
            return count;
        return 2;
    }

    private int resolveChangshaRoomFee(int baseScore) {
        return resolveDefaultRoomFee(baseScore);
    }

    private void fillChangshaRuleDefaults(JSONObject rule, int baseScore, int roundCount) {
        int roomFee = resolveChangshaRoomFee(baseScore);
        rule.put("base_score", baseScore);
        rule.put("di_zhu", baseScore);
        rule.put("round_count", roundCount);
        rule.put("player_count", 4);
        rule.put("max_score", 300);
        putRoomFee(rule, roomFee);
        rule.put("allow_chi", true);
        rule.put("allow_peng", true);
        rule.put("allow_gang", true);
        rule.put("allow_zimo", true);
        rule.put("allow_dianpao", true);
        rule.put("dissolve_vote", true);
        rule.put("banker_rule", 0);
        rule.put("max_fan", 8);
        rule.put("require_258_jiang", true);
        rule.put("queyise_enabled", true);
        rule.put("banbanhu_enabled", true);
        rule.put("dasixi_enabled", true);
        rule.put("liuliushun_enabled", true);
        rule.put("jiejiegao_enabled", true);
        rule.put("santong_enabled", true);
        rule.put("yizhihua_enabled", true);
        rule.put("zhongniao_enabled", true);
        rule.put("bird_count", 2);
        rule.put("bird_double", true);
        rule.put("bird_cap_max", true);
        rule.put("tile_count", 108);
    }

    private void copyBooleanRuleOption(JSONObject raw, JSONObject rule, String key) {
        Boolean value = raw.getBoolean(key);
        if (value != null)
            rule.put(key, value);
    }

    /**
     * 红中麻将规则统一归一化，避免客户端旧参数绕过游戏服固定玩法。
     */
    private String resolveHongzhongRuleConfig(String json) {
        JSONObject raw = StringUtils.isEmpty(json) ? null : JSONObject.parseObject(json);
        if (raw == null)
            raw = new JSONObject();
        JSONObject rule = new JSONObject();
        Integer level = raw.getInteger("level");
        if (level != null)
            rule.put("level", level);

        int roundCount = normalizeHongzhongRoundCount(raw.getInteger("round_count"));
        int baseScore = normalizeHongzhongBaseScore(raw.getInteger("base_score"), roundCount);
        int playerCount = normalizeHongzhongPlayerCount(raw.getInteger("player_count"));
        fillHongzhongRuleDefaults(rule, baseScore, roundCount, playerCount);
        applyRoomFeeOverride(raw, rule);
        return rule.toJSONString();
    }

    private int normalizeHongzhongRoundCount(Integer roundCount) {
        return (roundCount != null && roundCount == 1) ? 1 : 8;
    }

    private int normalizeHongzhongBaseScore(Integer baseScore, int roundCount) {
        int score = baseScore == null ? 0 : baseScore;
        int[] validScores = roundCount == 1
                ? new int[] {5, 10, 25}
                : new int[] {1, 2, 5, 10, 20};
        for (int validScore : validScores) {
            if (score == validScore)
                return score;
        }
        return validScores[0];
    }

    private int normalizeHongzhongPlayerCount(Integer playerCount) {
        return 2;
    }

    private int resolveHongzhongRoomFee(int baseScore) {
        return resolveDefaultRoomFee(baseScore);
    }

    private void fillHongzhongRuleDefaults(JSONObject rule, int baseScore, int roundCount, int playerCount) {
        int roomFee = resolveHongzhongRoomFee(baseScore);
        rule.put("base_score", baseScore);
        rule.put("round_count", roundCount);
        rule.put("player_count", playerCount);
        rule.put("max_score", 0);
        putRoomFee(rule, roomFee);
        rule.put("allow_chi", false);
        rule.put("allow_peng", true);
        rule.put("allow_gang", true);
        rule.put("allow_zimo", true);
        rule.put("allow_dianpao", true);
        rule.put("laizi_enabled", true);
        rule.put("hongzhong_enabled", true);
        rule.put("bao_ting_enabled", false);
        rule.put("dao_di_hu_enabled", false);
        rule.put("qidui_enabled", true);
        rule.put("pengpenghu_enabled", true);
        rule.put("qingyise_enabled", true);
        rule.put("zimo_double", false);
        rule.put("bird_count", 1);
        rule.put("bird_rule", "one_hit_number_multiplier");
        rule.put("allow_qianggang_hu", true);
        rule.put("qianggang_only_jiagang", true);
        rule.put("dianpao_without_hongzhong_only", true);
        rule.put("dissolve_vote", true);
        rule.put("banker_rule", 0);
        rule.put("tile_count", 112);
    }

    /**
     * 跑得快规则统一归一化：当前系统只开放两人15张玩法。
     */
    private String resolvePaodekuaiRuleConfig(String json) {
        JSONObject raw = StringUtils.isEmpty(json) ? null : JSONObject.parseObject(json);
        if (raw == null)
            raw = new JSONObject();
        JSONObject rule = new JSONObject();
        Integer level = raw.getInteger("level");
        if (level != null)
            rule.put("level", level);

        int roundCount = normalizePaodekuaiRoundCount(raw.getInteger("round_count"));
        int baseScore = normalizePaodekuaiBaseScore(raw.getInteger("base_score"), roundCount);
        fillPaodekuaiRuleDefaults(rule, baseScore, roundCount);

        Integer maxScore = raw.getInteger("max_score");
        if (maxScore != null && maxScore >= 0)
            rule.put("max_score", maxScore);
        applyRoomFeeOverride(raw, rule);
        return rule.toJSONString();
    }

    private int normalizePaodekuaiRoundCount(Integer roundCount) {
        return (roundCount != null && roundCount == 1) ? 1 : 8;
    }

    private int normalizePaodekuaiBaseScore(Integer baseScore, int roundCount) {
        int score = baseScore == null ? 0 : baseScore;
        int[] validScores = roundCount == 1
                ? new int[] {5, 10, 25}
                : new int[] {1, 2, 5, 10, 20};
        for (int validScore : validScores) {
            if (score == validScore)
                return score;
        }
        return validScores[0];
    }

    private void fillPaodekuaiRuleDefaults(JSONObject rule, int baseScore, int roundCount) {
        rule.put("base_score", baseScore);
        rule.put("round_count", roundCount);
        rule.put("player_count", 2);
        rule.put("card_count", 15);
        rule.put("max_score", 0);
        rule.put("allow_pass", true);
        rule.put("force_play_if_can_beat", true);
        rule.put("must_include_spade3", false);
        rule.put("first_lead_rule", "first_round_random_then_winner");
        rule.put("triple_carry_any_two", true);
        rule.put("bomb_double", true);
        rule.put("spring_double", true);
        rule.put("auto_play_timeout", 180000);
        rule.put("deck_rule", "remove_jokers_3x2_3xA_1xK");
        putRoomFee(rule, resolveDefaultRoomFee(baseScore));
    }

    private int resolveDefaultRoomFee(int baseScore) {
        if (baseScore <= 2) return 2;
        if (baseScore == 3) return 3;
        if (baseScore == 5) return 4;
        if (baseScore == 10) return 6;
        if (baseScore >= 20) return 7;
        return 2;
    }

    private void putRoomFee(JSONObject rule, int roomFee) {
        int value = Math.max(0, roomFee);
        rule.put("room_fee_type", value);
        rule.put("room_fee", value);
    }

    private void applyRoomFeeOverride(JSONObject raw, JSONObject rule) {
        Integer roomFee = raw.getInteger("room_fee");
        if (roomFee == null)
            roomFee = raw.getInteger("room_fee_type");
        if (roomFee != null && roomFee >= 0)
            putRoomFee(rule, roomFee);
    }

    private String decodeRuleConfigBase64(String base64) {
        if (StringUtils.isEmpty(base64))
            return null;
        try {
            byte[] buf = java.util.Base64.getDecoder().decode(base64);
            return buf == null ? null : new String(buf, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "创建房间规则参数格式错误");
        }
    }

    private JSONObject parseRuleJson(String json) {
        if (StringUtils.isEmpty(json))
            return new JSONObject();
        try {
            JSONObject obj = JSONObject.parseObject(json);
            return obj == null ? new JSONObject() : obj;
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    private int resolvePositiveBaseScore(JSONObject rule) {
        Integer baseScore = rule.getInteger("base_score");
        if (baseScore == null)
            baseScore = rule.getInteger("di_zhu");
        return baseScore != null && baseScore > 0 ? baseScore : 1;
    }

    private long resolveMinCarryScoreByBaseScore(int baseScore) {
        return baseScore > 0 ? baseScore * MIN_CARRY_SCORE_MULTIPLIER : 0L;
    }

    private long resolveMinCarryScoreForCreate(Integer gameType, String json) {
        if (gameType == null)
            return 0L;
        JSONObject rule = parseRuleJson(json);
        int baseScore = 0;
        if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG)) {
            int roundCount = normalizeTaojiangRoundCount(rule.getInteger("round_count"));
            baseScore = normalizeTaojiangBaseScore(rule.getInteger("base_score"), roundCount);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG)) {
            int roundCount = normalizeHongzhongRoundCount(rule.getInteger("round_count"));
            baseScore = normalizeHongzhongBaseScore(rule.getInteger("base_score"), roundCount);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG)) {
            int roundCount = normalizeChangshaRoundCount(rule.getInteger("round_count"));
            baseScore = normalizeChangshaBaseScore(rule.getInteger("base_score"), roundCount);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI)) {
            int roundCount = normalizePaodekuaiRoundCount(rule.getInteger("round_count"));
            baseScore = normalizePaodekuaiBaseScore(rule.getInteger("base_score"), roundCount);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU) ||
                gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI) ||
                gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN)) {
            baseScore = resolvePositiveBaseScore(rule);
        }
        return resolveMinCarryScoreByBaseScore(baseScore);
    }

    private long resolveMinCarryScoreForRuleConfig(Integer gameType, String ruleConfig) {
        if (gameType == null)
            return 0L;
        JSONObject rule = parseRuleJson(ruleConfig);
        int baseScore = 0;
        if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG)) {
            int roundCount = normalizeTaojiangRoundCount(rule.getInteger("round_count"));
            baseScore = normalizeTaojiangBaseScore(rule.getInteger("base_score"), roundCount);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG)) {
            int roundCount = normalizeHongzhongRoundCount(rule.getInteger("round_count"));
            baseScore = normalizeHongzhongBaseScore(rule.getInteger("base_score"), roundCount);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG)) {
            int roundCount = normalizeChangshaRoundCount(rule.getInteger("round_count"));
            baseScore = normalizeChangshaBaseScore(rule.getInteger("base_score"), roundCount);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI)) {
            int roundCount = normalizePaodekuaiRoundCount(rule.getInteger("round_count"));
            baseScore = normalizePaodekuaiBaseScore(rule.getInteger("base_score"), roundCount);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU) ||
                gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI) ||
                gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN)) {
            baseScore = resolvePositiveBaseScore(rule);
        }
        return resolveMinCarryScoreByBaseScore(baseScore);
    }

    private String getRuleConfigForVenue(Integer gameType, String venueId) {
        if (gameType == null || StringUtils.isEmpty(venueId))
            return null;
        if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG)) {
            LambdaQueryWrapper<GameTaojiangMahjong> query = Wrappers.lambdaQuery();
            query.eq(GameTaojiangMahjong::getVenueId, venueId);
            GameTaojiangMahjong entity = this.taojiangMahjongMapper.selectOne(query);
            return entity == null ? null : entity.getRuleConfig();
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG)) {
            LambdaQueryWrapper<GameHongzhongMahjong> query = Wrappers.lambdaQuery();
            query.eq(GameHongzhongMahjong::getVenueId, venueId);
            GameHongzhongMahjong entity = this.hongzhongMahjongMapper.selectOne(query);
            return entity == null ? null : entity.getRuleConfig();
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG)) {
            LambdaQueryWrapper<GameChangshaMahjong> query = Wrappers.lambdaQuery();
            query.eq(GameChangshaMahjong::getVenueId, venueId);
            GameChangshaMahjong entity = this.changshaMahjongMapper.selectOne(query);
            return entity == null ? null : entity.getRuleConfig();
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI)) {
            LambdaQueryWrapper<GamePaodekuai> query = Wrappers.lambdaQuery();
            query.eq(GamePaodekuai::getVenueId, venueId);
            GamePaodekuai entity = this.paodekuaiMapper.selectOne(query);
            return entity == null ? null : entity.getRuleConfig();
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU)) {
            LambdaQueryWrapper<GameDoudizhu> query = Wrappers.lambdaQuery();
            query.eq(GameDoudizhu::getVenueId, venueId);
            GameDoudizhu entity = this.doudizhuMapper.selectOne(query);
            return entity == null ? null : entity.getRuleConfig();
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI)) {
            LambdaQueryWrapper<GameYiyangWaihuzi> query = Wrappers.lambdaQuery();
            query.eq(GameYiyangWaihuzi::getVenueId, venueId);
            GameYiyangWaihuzi entity = this.yiyangWaihuziMapper.selectOne(query);
            return entity == null ? null : entity.getRuleConfig();
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN)) {
            LambdaQueryWrapper<GameYuanjiangQianfen> query = Wrappers.lambdaQuery();
            query.eq(GameYuanjiangQianfen::getVenueId, venueId);
            GameYuanjiangQianfen entity = this.yuanjiangQianfenMapper.selectOne(query);
            return entity == null ? null : entity.getRuleConfig();
        }
        return null;
    }

    private long resolveMinCarryScoreForVenue(Venue venue) {
        if (venue == null)
            return 0L;
        Integer districtId = venue.getDistrictId();
        if (districtId != null && districtId > 0)
            return resolveMinCarryScoreForDistrict(districtId, null);
        return resolveMinCarryScoreForRuleConfig(venue.getGameType(), getRuleConfigForVenue(venue.getGameType(), venue.getId()));
    }

    private long resolveMinCarryScoreForDistrict(Integer districtId, District district) {
        int baseScore = resolveDistrictBaseScore(districtId);
        if (baseScore > 0)
            return resolveMinCarryScoreByBaseScore(baseScore);
        Long goldNeed = district == null ? null : district.getGoldNeed();
        if (goldNeed == null && districtId != null) {
            District entity = this.districtMapper.selectById(districtId);
            if (entity != null)
                goldNeed = entity.getGoldNeed();
        }
        return goldNeed != null && goldNeed > 0L ? goldNeed : 0L;
    }

    private boolean isPlayerInVenue(String playerId, String venueId) {
        if (StringUtils.isEmpty(playerId) || StringUtils.isEmpty(venueId))
            return false;
        String venueKey = NiuMaRedisKeys.PLAYER_CURRENT_VENUE + playerId;
        String currentVenue = this.redisPrimitive.get(venueKey);
        return venueId.equals(currentVenue);
    }

    private boolean isPlayerInDistrict(String playerId, Integer districtId) {
        if (StringUtils.isEmpty(playerId) || districtId == null)
            return false;
        String venueKey = NiuMaRedisKeys.PLAYER_CURRENT_VENUE + playerId;
        String currentVenue = this.redisPrimitive.get(venueKey);
        if (StringUtils.isEmpty(currentVenue))
            return false;
        Integer currentDistrictId = this.venueMapper.getDistrictId(currentVenue);
        return districtId.equals(currentDistrictId);
    }

    private void assertEnoughCarryScore(String playerId, long minCarryScore) {
        if (minCarryScore <= 0L)
            return;
        Long gold = this.capitalMapper.getGold(playerId);
        if (gold == null)
            gold = 0L;
        if (gold < minCarryScore) {
            String msg = "携带积分不足，最低需要" + minCarryScore + "积分，保险柜积分不参与游戏结算，请先从保险柜取出积分";
            throw new ForbiddenException(NiuMaCodeEnum.GOLD_INSUFFICIENT_ERROR.getCode(), msg);
        }
    }

    private void assertEnoughCarryScoreForCreate(String playerId, Integer gameType, String json) {
        assertEnoughCarryScore(playerId, resolveMinCarryScoreForCreate(gameType, json));
    }

    private void assertEnoughCarryScoreForVenue(String playerId, Venue venue) {
        if (venue == null || isPlayerInVenue(playerId, venue.getId()))
            return;
        assertEnoughCarryScore(playerId, resolveMinCarryScoreForVenue(venue));
    }

    private void assertEnoughCarryScoreForDistrict(String playerId, Integer districtId, District district) {
        if (isPlayerInDistrict(playerId, districtId))
            return;
        assertEnoughCarryScore(playerId, resolveMinCarryScoreForDistrict(districtId, district));
    }

    private void responseHttpException(DeferredResult<ResponseEntity<AjaxResult> > result, HttpException ex) {
        AjaxResult ajax = new AjaxResult();
        if (StringUtils.isNotEmpty(ex.getCode()))
            ajax.put(AjaxResult.CODE_TAG, ex.getCode());
        if (StringUtils.isNotEmpty(ex.getMessage()))
            ajax.put(AjaxResult.MSG_TAG, ex.getMessage());
        result.setResult(new ResponseEntity<>(ajax, ex.getStatus()));
    }

    public String createGame(Integer gameType, String playerId, String base64) {
        String json = decodeRuleConfigBase64(base64);
        assertEnoughCarryScoreForCreate(playerId, gameType, json);
        GameMahjong mahjong = null;
        GameBiJi biJi = null;
        GameLackey lackey = null;
        GameNiu100 niu100 = null;
        GameDoudizhu doudizhu = null;
        GameGuanDan guanDan = null;
        GameTaojiangMahjong taojiangMahjong = null;
        GameHongzhongMahjong hongzhongMahjong = null;
        GamePaodekuai paodekuai = null;
        GameChangshaMahjong changshaMahjong = null;
        GameYiyangWaihuzi yiyangWaihuzi = null;
        GameYuanjiangQianfen yuanjiangQianfen = null;
        if (gameType.equals(NiuMaConstants.GAME_TYPE_MAHJONG))
            mahjong = checkCreateMahjong(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_BI_JI))
            biJi = checkCreateBiJi(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_LACKEY))
            lackey = checkCreateLackey(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_NIU_NIU_100))
            niu100 = checkCreateNiu100(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU))
            doudizhu = checkCreateDoudizhu(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_GUAN_DAN))
            guanDan = checkCreateGuanDan(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG))
            taojiangMahjong = checkCreateTaojiangMahjong(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG))
            hongzhongMahjong = checkCreateHongzhongMahjong(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI))
            paodekuai = checkCreatePaodekuai(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG))
            changshaMahjong = checkCreateChangshaMahjong(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI))
            yiyangWaihuzi = checkCreateYiyangWaihuzi(playerId, json);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN))
            yuanjiangQianfen = checkCreateYuanjiangQianfen(playerId, json);
        String venueId = null;
        try {
            venueId = generateVenueId();
            Venue entity = new Venue();
            entity.setId(venueId);
            entity.setOwnerId(playerId);
            entity.setGameType(gameType);
            entity.setStatus(0);
            entity.setCreateTime(LocalDateTime.now());
            this.venueMapper.insert(entity);
            if (gameType.equals(NiuMaConstants.GAME_TYPE_DUMB))
                createDumbGame(venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_MAHJONG))
                createMahjong(mahjong, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_BI_JI))
                createBiJi(biJi, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_LACKEY))
                createLackey(lackey, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_NIU_NIU_100))
                createNiu100(niu100, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU))
                createDoudizhu(doudizhu, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_GUAN_DAN))
                createGuanDan(guanDan, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG))
                createTaojiangMahjong(taojiangMahjong, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG))
                createHongzhongMahjong(hongzhongMahjong, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI))
                createPaodekuai(paodekuai, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG))
                createChangshaMahjong(changshaMahjong, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI))
                createYiyangWaihuzi(yiyangWaihuzi, venueId);
            else if (gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN))
                createYuanjiangQianfen(yuanjiangQianfen, venueId);
        } catch (Exception ex) {
            if (StringUtils.isNotEmpty(venueId)) {
                LambdaQueryWrapper<Venue> query = Wrappers.lambdaQuery();
                query.eq(Venue::getId, venueId);
                this.venueMapper.delete(query);
            }
            if (ex instanceof HttpException)
                throw ex;
            else
                throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), ex.getMessage());
        }
        return venueId;
    }

    private void createDumbGame(String venueId) {
        GameDumb game = new GameDumb();
        game.setVenueId(venueId);
        game.setName("DumbGame");
        game.setMaxPlayers(3);
        this.gameDumbMapper.insert(game);
    }

    private GameMahjong checkCreateMahjong(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer mode = jsonObject.getInteger("mode");
        Integer diZhu = jsonObject.getInteger("diZhu");
        Integer rule = jsonObject.getInteger("rule");
        if (mode == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameter \"mode\" missing");
        if (diZhu == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameter \"diZhu\" missing");
        if (rule == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameter \"rule\" missing");
        if (mode < 0 || mode > 1)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Value of parameter \"mode\" error");
        if (diZhu < 0 || diZhu > 4)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Value of parameter \"diZhu\" error");
        if (mode.equals(1) && diZhu.equals(4))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "When mode is 1, diZhu can't be 4");
        Integer[] diZhuList0 = new Integer[] { 500, 600, 800, 1000, 0 };
        Integer[] diZhuList1 = new Integer[] { 2000, 2500, 3000, 4000, 5000 };
        if (mode.equals(0))
            diZhu = diZhuList0[diZhu];
        else
            diZhu = diZhuList1[diZhu];
        // 押金为底注的50倍，检查玩家是否有足够金币
        Integer cashPledge = diZhu * 50;
        Long gold = this.capitalMapper.getGold(playerId);
        if (gold == null)
            gold = 0L;
        if (gold < cashPledge)
            throw new ForbiddenException(NiuMaCodeEnum.GOLD_INSUFFICIENT_ERROR.getCode(), "金币不足，最低需要50倍底注数量金币");
        if (mode.equals(0)) {
            // 扣钻模式
            Long diamond = this.capitalMapper.getDiamond(playerId);
            if ((diamond == null) || (diamond < 4L))
                throw new ForbiddenException(NiuMaCodeEnum.DIAMOND_INSUFFICIENT_ERROR.getCode(), "钻石不足，最低需要4枚钻石");
        }
        String number = this.generateNumber(new MahjongNumberTester(this.mahjongMapper));
        GameMahjong entity = new GameMahjong();
        entity.setNumber(number);
        entity.setMode(mode);
        entity.setDiZhu(diZhu);
        entity.setRule(rule);
        return entity;
    }

    private void createMahjong(GameMahjong entity, String venueId) {
        entity.setVenueId(venueId);
        this.mahjongMapper.insert(entity);
    }

    private GameBiJi checkCreateBiJi(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer mode = jsonObject.getInteger("mode");
        Integer diZhu = jsonObject.getInteger("diZhu");
        Integer isPublic = jsonObject.getInteger("isPublic");
        if (mode == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameter \"mode\" missing");
        if (diZhu == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameter \"diZhu\" missing");
        if (isPublic == null)
            isPublic = 0;
        if (mode < 0 || mode > 1)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Value of parameter \"mode\" error");
        if (diZhu < 0 || diZhu > 7)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Value of parameter \"diZhu\" error");
        Integer[][] diZhuList = { { 100, 200, 300, 400, 500, 600, 800, 0 }, { 1000, 1200, 1500, 2000, 2500, 3000, 3500, 4000 } };
        diZhu = diZhuList[mode][diZhu];
        // 押金为底注的10倍，检查玩家是否有足够金币
        Integer cashPledge = diZhu * 10;
        Long gold = this.capitalMapper.getGold(playerId);
        if (gold == null)
            gold = 0L;
        if (gold < cashPledge)
            throw new ForbiddenException(NiuMaCodeEnum.GOLD_INSUFFICIENT_ERROR.getCode(), "金币不足，最低需底注10倍数量金币");
        if (mode.equals(0)) {
            // 扣钻模式
            Long diamond = this.capitalMapper.getDiamond(playerId);
            if ((diamond == null) || (diamond < 1L))
                throw new ForbiddenException(NiuMaCodeEnum.DIAMOND_INSUFFICIENT_ERROR.getCode(), "钻石不足，最低需要1枚钻石");
        }
        String number = this.generateNumber(new BiJiNumberTester(this.biJiMapper));
        GameBiJi entity = new GameBiJi();
        entity.setNumber(number);
        entity.setMode(mode);
        entity.setDiZhu(diZhu);
        entity.setIsPublic(isPublic);
        return entity;
    }

    private void createBiJi(GameBiJi entity, String venueId) {
        entity.setVenueId(venueId);
        this.biJiMapper.insert(entity);
    }

    private enum LackeyRoomLevel {
        Invalid,	// 无效
        Friend,		// 好友房
        Beginner,	// 新手房
        Moderate,	// 初级房
        Advanced,	// 高级房
        Master		// 大师房
    };

    private GameLackey checkCreateLackey(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer mode = jsonObject.getInteger("mode");
        Integer diZhu = jsonObject.getInteger("diZhu");
        if (mode == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameter \"mode\" missing");
        if (diZhu == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameter \"diZhu\" missing");
        if (mode < 0 || mode > 1)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Value of parameter \"mode\" error");
        if (diZhu < 0 || diZhu > 8)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Value of parameter \"diZhu\" error");
        Integer[][] diZhuList = { { 100, 200, 300, 400, 500, 600, 700, 800, 0 }, { 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000 } };
        diZhu = diZhuList[mode][diZhu];
        // 押金为底注的15倍，检查玩家是否有足够金币
        Integer cashPledge = diZhu * 15;
        Long gold = this.capitalMapper.getGold(playerId);
        if (gold == null)
            gold = 0L;
        if (gold < cashPledge)
            throw new ForbiddenException(NiuMaCodeEnum.GOLD_INSUFFICIENT_ERROR.getCode(), "金币不足，需底注15倍底注数量金币");
        if (mode.equals(0)) {
            // 扣钻模式
            Long diamond = this.capitalMapper.getDiamond(playerId);
            if ((diamond == null) || (diamond < 2L))
                throw new ForbiddenException(NiuMaCodeEnum.DIAMOND_INSUFFICIENT_ERROR.getCode(), "钻石不足，最低需要2枚钻石");
        }
        String number = this.generateNumber(new LackeyNumberTester(this.lackeyMapper));
        GameLackey entity = new GameLackey();
        entity.setNumber(number);
        entity.setLevel(LackeyRoomLevel.Friend.ordinal());
        entity.setMode(mode);
        entity.setDiZhu(diZhu);
        return entity;
    }

    private void createLackey(GameLackey entity, String venueId) {
        entity.setVenueId(venueId);
        this.lackeyMapper.insert(entity);
    }

    private GameNiu100 checkCreateNiu100(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Long deposit = jsonObject.getLong("deposit");
        Integer isPublic = jsonObject.getInteger("isPublic");
        if (deposit == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameter \"deposit\" missing");
        if (isPublic == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameter \"isPublic\" missing");
        if (deposit < 200000L)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "最低奖池押金数20万金币");
        walletService.decrease(playerId, WalletType.GOLD.getCode(), deposit,
                LedgerBizType.ROOM_DEPOSIT.getCode(), null, "创建百人牛牛奖池押金");
        String number = this.generateNumber(new Niu100NumberTester(this.niu100Mapper));
        log.info("玩家(ID：{})创建百人牛牛游戏(房号：{})，奖池押金：{}", playerId, number, deposit);
        GameNiu100 entity = new GameNiu100();
        entity.setNumber(number);
        entity.setDeposit(deposit);
        entity.setIsPublic(isPublic);
        entity.setBankerId(playerId);
        return entity;
    }

    private void createNiu100(GameNiu100 entity, String venueId) {
        entity.setVenueId(venueId);
        this.niu100Mapper.insert(entity);
    }

    private enum GuanDanLevel {
        Invalid,	// 无效
        Friend,		// 好友房
        Practice,   // 练习房(单机模式)
        Beginner,	// 初级房
        Moderate,	// 中级房
        Advanced,	// 高级房
        Master		// 大师房
    };

    private String resolveDoudizhuRuleConfig(String json) {
        JSONObject raw = StringUtils.isEmpty(json) ? null : JSONObject.parseObject(json);
        if (raw == null)
            raw = new JSONObject();
        JSONObject rule = new JSONObject();
        Integer level = raw.getInteger("level");
        if (level != null)
            rule.put("level", level);
        Integer baseScore = raw.getInteger("base_score");
        int normalizedBaseScore = baseScore != null && baseScore > 0 ? baseScore : 1;
        rule.put("base_score", normalizedBaseScore);
        Integer roundCount = raw.getInteger("round_count");
        rule.put("round_count", roundCount != null && roundCount > 0 ? roundCount : 8);
        Integer maxScore = raw.getInteger("max_score");
        rule.put("max_score", maxScore != null && maxScore > 0 ? maxScore : 0);
        rule.put("player_count", 2);
        rule.put("remove_three_and_four", false);
        rule.put("hand_card_count", DOUDIZHU_HAND_CARD_COUNT);
        rule.put("bottom_card_count", DOUDIZHU_BOTTOM_CARD_COUNT);
        rule.put("auto_play_timeout", DOUDIZHU_AUTO_PLAY_TIMEOUT);
        putRoomFee(rule, resolveDefaultRoomFee(normalizedBaseScore));
        applyRoomFeeOverride(raw, rule);
        return rule.toJSONString();
    }

    private String resolveGuandanRuleConfig(String json) {
        JSONObject raw = StringUtils.isEmpty(json) ? null : JSONObject.parseObject(json);
        if (raw == null)
            raw = new JSONObject();
        JSONObject rule = new JSONObject();
        Integer level = raw.getInteger("level");
        if (level != null)
            rule.put("level", level);
        Integer roundCount = raw.getInteger("round_count");
        rule.put("round_count", roundCount != null && roundCount > 0 ? roundCount : 8);
        Integer baseScore = raw.getInteger("base_score");
        int normalizedBaseScore = baseScore != null && baseScore > 0 ? baseScore : 1;
        rule.put("base_score", normalizedBaseScore);
        rule.put("player_count", 4);
        putRoomFee(rule, resolveDefaultRoomFee(normalizedBaseScore));
        applyRoomFeeOverride(raw, rule);
        return rule.toJSONString();
    }

    private GameDoudizhu checkCreateDoudizhu(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer level = jsonObject.getInteger("level");
        if (level == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "未指定房间类型");
        if (!(level.equals(GuanDanLevel.Practice.ordinal()) || level.equals(GuanDanLevel.Friend.ordinal())))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "房间类型错误");
        String number = null;
        if (level.equals(GuanDanLevel.Friend.ordinal()))
            number = this.generateNumber(new DoudizhuNumberTester(this.doudizhuMapper));
        else
            number = "practice";
        log.info("玩家(ID：{})创建斗地主游戏(房号：{}", playerId, number);
        GameDoudizhu entity = new GameDoudizhu();
        entity.setNumber(number);
        entity.setLevel(level);
        entity.setRuleConfig(resolveDoudizhuRuleConfig(json));
        return entity;
    }

    private void createDoudizhu(GameDoudizhu entity, String venueId) {
        entity.setVenueId(venueId);
        this.doudizhuMapper.insert(entity);
    }

    private GameGuanDan checkCreateGuanDan(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer level = jsonObject.getInteger("level");
        if (level == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "未指定房间类型");
        if (!(level.equals(GuanDanLevel.Practice.ordinal()) || level.equals(GuanDanLevel.Friend.ordinal())))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "房间类型错误");
        String number = null;
        if (level.equals(GuanDanLevel.Friend.ordinal()))
            number = this.generateNumber(new GuanDanNumberTester(this.guanDanMapper));
        else
            number = "practice";
        log.info("玩家(ID：{})创建经典掼蛋游戏(房号：{}", playerId, number);
        GameGuanDan entity = new GameGuanDan();
        entity.setNumber(number);
        entity.setLevel(level);
        entity.setRuleConfig(resolveGuandanRuleConfig(json));
        return entity;
    }

    private void createGuanDan(GameGuanDan entity, String venueId) {
        entity.setVenueId(venueId);
        this.guanDanMapper.insert(entity);
    }

    private GameTaojiangMahjong checkCreateTaojiangMahjong(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer level = jsonObject.getInteger("level");
        if (level == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "未指定房间类型");
        if (!(level.equals(GuanDanLevel.Practice.ordinal()) || level.equals(GuanDanLevel.Friend.ordinal())))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "房间类型错误");
        String number = null;
        if (level.equals(GuanDanLevel.Friend.ordinal()))
            number = this.generateNumber(new TaojiangMahjongNumberTester(this.taojiangMahjongMapper));
        else
            number = "practice";
        log.info("玩家(ID：{})创建桃江麻将游戏(房号：{}", playerId, number);
        GameTaojiangMahjong entity = new GameTaojiangMahjong();
        entity.setNumber(number);
        entity.setLevel(level);
        entity.setRuleConfig(resolveTaojiangRuleConfig(json));
        return entity;
    }

    private void createTaojiangMahjong(GameTaojiangMahjong entity, String venueId) {
        entity.setVenueId(venueId);
        this.taojiangMahjongMapper.insert(entity);
    }

    private GameHongzhongMahjong checkCreateHongzhongMahjong(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer level = jsonObject.getInteger("level");
        if (level == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "未指定房间类型");
        if (!(level.equals(GuanDanLevel.Practice.ordinal()) || level.equals(GuanDanLevel.Friend.ordinal())))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "房间类型错误");
        String number = null;
        if (level.equals(GuanDanLevel.Friend.ordinal()))
            number = this.generateNumber(new HongzhongMahjongNumberTester(this.hongzhongMahjongMapper));
        else
            number = "practice";
        log.info("玩家(ID：{})创建红中麻将游戏(房号：{}", playerId, number);
        GameHongzhongMahjong entity = new GameHongzhongMahjong();
        entity.setNumber(number);
        entity.setLevel(level);
        entity.setRuleConfig(resolveHongzhongRuleConfig(json));
        return entity;
    }

    private void createHongzhongMahjong(GameHongzhongMahjong entity, String venueId) {
        entity.setVenueId(venueId);
        this.hongzhongMahjongMapper.insert(entity);
    }

    private GamePaodekuai checkCreatePaodekuai(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer level = jsonObject.getInteger("level");
        if (level == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "未指定房间类型");
        if (!(level.equals(GuanDanLevel.Practice.ordinal()) || level.equals(GuanDanLevel.Friend.ordinal())))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "房间类型错误");
        String number = null;
        if (level.equals(GuanDanLevel.Friend.ordinal()))
            number = this.generateNumber(new PaodekuaiNumberTester(this.paodekuaiMapper));
        else
            number = "practice";
        log.info("玩家(ID：{})创建跑得快游戏(房号：{}", playerId, number);
        GamePaodekuai entity = new GamePaodekuai();
        entity.setNumber(number);
        entity.setLevel(level);
        entity.setRuleConfig(resolvePaodekuaiRuleConfig(json));
        return entity;
    }

    private void createPaodekuai(GamePaodekuai entity, String venueId) {
        entity.setVenueId(venueId);
        this.paodekuaiMapper.insert(entity);
    }

    private GameChangshaMahjong checkCreateChangshaMahjong(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer level = jsonObject.getInteger("level");
        if (level == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "未指定房间类型");
        if (!(level.equals(GuanDanLevel.Practice.ordinal()) || level.equals(GuanDanLevel.Friend.ordinal())))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "房间类型错误");
        String number = null;
        if (level.equals(GuanDanLevel.Friend.ordinal()))
            number = this.generateNumber(new ChangshaMahjongNumberTester(this.changshaMahjongMapper));
        else
            number = "practice";
        log.info("玩家(ID：{})创建长沙麻将游戏(房号：{}", playerId, number);
        GameChangshaMahjong entity = new GameChangshaMahjong();
        entity.setNumber(number);
        entity.setLevel(level);
        entity.setRuleConfig(resolveChangshaRuleConfig(json));
        return entity;
    }

    private void createChangshaMahjong(GameChangshaMahjong entity, String venueId) {
        entity.setVenueId(venueId);
        this.changshaMahjongMapper.insert(entity);
    }

    private GameYiyangWaihuzi checkCreateYiyangWaihuzi(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer level = jsonObject.getInteger("level");
        if (level == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "未指定房间类型");
        if (!(level.equals(GuanDanLevel.Practice.ordinal()) || level.equals(GuanDanLevel.Friend.ordinal())))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "房间类型错误");
        String number = null;
        if (level.equals(GuanDanLevel.Friend.ordinal()))
            number = this.generateNumber(new YiyangWaihuziNumberTester(this.yiyangWaihuziMapper));
        else
            number = "practice";
        log.info("玩家(ID：{})创建益阳歪胡子游戏(房号：{}", playerId, number);
        GameYiyangWaihuzi entity = new GameYiyangWaihuzi();
        entity.setNumber(number);
        entity.setLevel(level);
        entity.setRuleConfig(resolveRuleConfig(json));
        return entity;
    }

    private void createYiyangWaihuzi(GameYiyangWaihuzi entity, String venueId) {
        entity.setVenueId(venueId);
        this.yiyangWaihuziMapper.insert(entity);
    }

    private GameYuanjiangQianfen checkCreateYuanjiangQianfen(String playerId, String json) {
        JSONObject jsonObject = JSONObject.parseObject(json);
        if (jsonObject == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "Required parameters missing");
        Integer level = jsonObject.getInteger("level");
        if (level == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "未指定房间类型");
        if (!(level.equals(GuanDanLevel.Practice.ordinal()) || level.equals(GuanDanLevel.Friend.ordinal())))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "房间类型错误");
        String number = null;
        if (level.equals(GuanDanLevel.Friend.ordinal()))
            number = this.generateNumber(new YuanjiangQianfenNumberTester(this.yuanjiangQianfenMapper));
        else
            number = "practice";
        log.info("玩家(ID：{})创建沅江千分游戏(房号：{}", playerId, number);
        GameYuanjiangQianfen entity = new GameYuanjiangQianfen();
        entity.setNumber(number);
        entity.setLevel(level);
        entity.setRuleConfig(resolveRuleConfig(json));
        return entity;
    }

    private void createYuanjiangQianfen(GameYuanjiangQianfen entity, String venueId) {
        entity.setVenueId(venueId);
        this.yuanjiangQianfenMapper.insert(entity);
    }

    @Override
    public void createDumbGame(DeferredResult<ResponseEntity<AjaxResult>> result) {
        CreateGameDTO dto = new CreateGameDTO();
        dto.setGameType(NiuMaConstants.GAME_TYPE_DUMB);
        this.createGame(result, dto);
    }

    @Override
    public void createGame(DeferredResult<ResponseEntity<AjaxResult> > result, CreateGameDTO dto) {
        try {
            Integer gameType = dto.getGameType();
            if (!(gameType.equals(NiuMaConstants.GAME_TYPE_DUMB) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_MAHJONG) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_NIU_NIU_100) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_BI_JI) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_LACKEY) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_GUAN_DAN) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI) ||
                    gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN)))
                throw new ForbiddenException(NiuMaCodeEnum.GAME_TYPE_ERROR.getCode(), "Unsupported game type");
            LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
            String playerId = null;
            if (player != null)
                playerId = player.getId();
            MqCommandDeferred actionDeferred = this.checkBeforeEnter(playerId, null, (playerIdIn, venueIdIn) -> {
                // 创建游戏
                String venueId = createGame(dto.getGameType(), playerIdIn, dto.getBase64());
                // 响应进入新创建的场地
                responseEnterVenue(result, playerIdIn, venueId);
            });
            if (actionDeferred != null) {
                // 等待服务器处理离开场地命令
                actionDeferred.setAction(NiuMaConstants.ACTION_CREATE_GAME);
                actionDeferred.setGameType(gameType);
                actionDeferred.setBase64(dto.getBase64());
                actionDeferred.setResult(result);
            }
        } catch (HttpException ex) {
            responseHttpException(result, ex);
        }
    }

    /**
     * 阻塞获取Redis锁
     * @param lockKey 锁键名
     */
    private void blockRedisLock(String lockKey) {
        Long lock = null;
        int count = 0;
        while (true) {
            lock = this.redisPrimitive.incr(lockKey, 1L);
            if (lock == null)
                throw new InternalServerException(ResultCodeEnum.REDIS_ACCESS_ERROR);
            if (lock.equals(1L))
                break;
            else {
                if (count > 9) {
                    String errMsg = "Get redis lock error: try count over limit";
                    log.error(errMsg);
                    throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), errMsg);
                }
                count++;
                try {
                    Thread.sleep(100L);
                } catch (Exception ex) {
                    log.error("Thread sleep error: {}", ex.getMessage());
                    throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), ex.getMessage());
                }
            }
        }
        this.redisPrimitive.expire(lockKey, 1L, TimeUnit.SECONDS);
    }

    private String assignVenue2Server(String venueId) {
        String lockKey = NiuMaRedisKeys.VENUE_ASSIGN_LOCK + venueId;
        blockRedisLock(lockKey);
        String mapKey = NiuMaRedisKeys.VENUE_SERVER_MAP + venueId;
        String serverId = this.redisPrimitive.get(mapKey);
        if (StringUtils.isNotEmpty(serverId)) {
            this.redisPrimitive.delete(lockKey);
            return serverId;
        }
        Set<String> serverIds = this.redisPrimitive.getSet(NiuMaRedisKeys.VENUE_SERVER_SET);
        if ((serverIds != null) && !serverIds.isEmpty()) {
            Integer minCount = -1;
            Integer playerCount = 0;
            Long nowTime = System.currentTimeMillis();
            Long timestamp = null;
            Long delta = 0L;
            nowTime /= 1000L;
            for (String tmp : serverIds) {
                String redisKey = NiuMaRedisKeys.SERVER_KEEP_ALIVE + tmp;
                timestamp = this.redisPrimitive.getLong(redisKey);
                if (timestamp == null)
                    continue;
                delta = nowTime - timestamp;
                if (delta > 30L)
                    continue;   // 最近30秒都没有更新，说明服务器可能离线了
                redisKey = NiuMaRedisKeys.SERVER_PLAYER_COUNT + tmp;
                playerCount = this.redisPrimitive.getInt(redisKey);
                if (playerCount == null)
                    playerCount = 0;
                if ((minCount < 0) || (minCount > playerCount)) {
                    serverId = tmp;
                    minCount = playerCount;
                }
            }
            if (StringUtils.isNotEmpty(serverId))
                this.redisPrimitive.set(mapKey, serverId);
        }
        this.redisPrimitive.delete(lockKey);
        return serverId;
    }

    /**
     * 从Redis中获取服务器地址
     * @param serverId 服务器id
     * @return 服务器地址
     */
    private String getServerAddress(String serverId) {
        String redisKey = NiuMaRedisKeys.SERVER_ACCESS_ADDRESS + serverId;
        String address = this.redisPrimitive.get(redisKey);
        if (StringUtils.isEmpty(address)) {
            String errMsg = String.format("Can not find the access address of server with id: %s", serverId);
            throw new InternalServerException(NiuMaCodeEnum.SERVER_INACCESSIBLE.getCode(), errMsg);
        }
        return address;
    }

    /**
     * 从Redis中获取服务器的websocket地址
     * @param serverId 服务器id
     * @return 服务器websocket地址
     */
    private String getServerWSAddress(String serverId) {
        String redisKey = NiuMaRedisKeys.SERVER_WS_ADDRESS + serverId;
        String wsAddress = this.redisPrimitive.get(redisKey);
        if (StringUtils.isEmpty(wsAddress)) {
            String errMsg = String.format("Can not find the websocket address of server with id: %s", serverId);
            throw new InternalServerException(NiuMaCodeEnum.SERVER_INACCESSIBLE.getCode(), errMsg);
        }
        return wsAddress;
    }

    private void responseEnterVenue(DeferredResult<ResponseEntity<AjaxResult> > result, String playerId, String venueId) {
        // 分配游戏到服务器
        String serverId = assignVenue2Server(venueId);
        if (StringUtils.isEmpty(serverId))
            throw new InternalServerException(NiuMaCodeEnum.SERVER_LIST_EMPTY);
        // 设置当前授权进入的场地
        String redisKey = NiuMaRedisKeys.PLAYER_ENTER_DATA + playerId;
        PlayerEnter enterData = new PlayerEnter();
        enterData.setAuthorizedTime(System.currentTimeMillis());
        enterData.setAuthorizedVenue(venueId);
        this.redisCache.setCacheObject(redisKey, enterData);
        redisKey = NiuMaRedisKeys.PLAYER_AUTHORIZED_VENUE + playerId;
        this.redisPrimitive.set(redisKey, venueId);
        // 返回响应HTTP请求
        String address = getServerAddress(serverId);
        String wsAddress = getServerWSAddress(serverId);
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("address", address);
        ajax.put("wsAddress", wsAddress);
        ajax.put("venueId", venueId);
        result.setResult(ResponseEntity.ok(ajax));
    }

    @Override
    public void enter(DeferredResult<ResponseEntity<AjaxResult>> result, EnterDTO dto) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        String playerId = null;
        if (player != null)
            playerId = player.getId();
        try {
            Venue entity = this.venueMapper.selectById(dto.getVenueId());
            if (entity == null)
                throw new NotFoundException(NiuMaCodeEnum.VENUE_NOT_EXIST);
            if (!dto.getGameType().equals(entity.getGameType()))
                throw new ForbiddenException(NiuMaCodeEnum.GAME_TYPE_ERROR);
            if (!entity.getStatus().equals(0))
                throw new ForbiddenException(NiuMaCodeEnum.GAME_STATUS_ERROR);
            MqCommandDeferred actionDeferred = this.checkBeforeEnter(playerId, dto.getVenueId(), (playerIdIn, venueIdIn) -> {
                assertEnoughCarryScoreForVenue(playerIdIn, entity);
                // 响应进入指定场地
                responseEnterVenue(result, playerIdIn, venueIdIn);
            });
            if (actionDeferred != null) {
                // 等待服务器处理离开场地命令
                actionDeferred.setAction(NiuMaConstants.ACTION_ENTER_GAME);
                // 离开成功后进入指定场地
                actionDeferred.setVenueId(dto.getVenueId());
                actionDeferred.setGameType(dto.getGameType());
                actionDeferred.setResult(result);
            }
        } catch (HttpException ex) {
            responseHttpException(result, ex);
        }
    }

    @Override
    public void enterNumber(DeferredResult<ResponseEntity<AjaxResult>> result, EnterNumberDTO dto) {
        String venueId = null;
        Integer gameType = dto.getGameType();
        if (gameType.equals(NiuMaConstants.GAME_TYPE_MAHJONG))
            venueId = this.mahjongMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_BI_JI))
            venueId = this.biJiMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_LACKEY))
            venueId = this.lackeyMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_NIU_NIU_100))
            venueId = this.niu100Mapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU))
            venueId = this.doudizhuMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_GUAN_DAN))
            venueId = this.guanDanMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG))
            venueId = this.taojiangMahjongMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG))
            venueId = this.hongzhongMahjongMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI))
            venueId = this.paodekuaiMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG))
            venueId = this.changshaMahjongMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI))
            venueId = this.yiyangWaihuziMapper.getIdByNumber(dto.getNumber());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN))
            venueId = this.yuanjiangQianfenMapper.getIdByNumber(dto.getNumber());
        if (StringUtils.isEmpty(venueId)) {
            AjaxResult ajax = new AjaxResult();
            ajax.put(AjaxResult.CODE_TAG, NiuMaCodeEnum.VENUE_NOT_EXIST.getCode());
            ajax.put(AjaxResult.MSG_TAG, NiuMaCodeEnum.VENUE_NOT_EXIST.getDesc());
            result.setResult(new ResponseEntity<>(ajax, HttpStatus.NOT_FOUND));
            return;
        }
        EnterDTO tmp = new EnterDTO();
        tmp.setVenueId(venueId);
        tmp.setGameType(gameType);
        this.enter(result, tmp);
    }

    private int getDistrictPlayerLimit(Integer districtId) {
        int ret = 0;
        if (districtId.equals(NiuMaConstants.DISTRICT_LACKEY_BEGINNER) ||
            districtId.equals(NiuMaConstants.DISTRICT_LACKEY_MODERATE) ||
            districtId.equals(NiuMaConstants.DISTRICT_LACKEY_ADVANCED) ||
            districtId.equals(NiuMaConstants.DISTRICT_LACKEY_MASTER))
            ret = 5;
        else if (districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_BEGINNER) ||
                districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_MODERATE) ||
                districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_ADVANCED) ||
                districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_MASTER))
            ret = 4;
        // 桃江麻将 2人
        else if (isTaojiangDistrict(districtId))
            ret = 2;
        // 红中麻将 4人
        else if (isHongzhongDistrict(districtId))
            ret = 4;
        // 长沙麻将 4人
        else if (isChangshaDistrict(districtId))
            ret = 4;
        // 跑得快 2人
        else if (isPaodekuaiDistrict(districtId))
            ret = 2;
        // 歪胡子 2人
        else if (isWaihuziDistrict(districtId))
            ret = 2;
        // 沅江千分 4人
        else if (isQianfenDistrict(districtId))
            ret = 4;
        // 斗地主 2人
        else if (isDoudizhuDistrict(districtId))
            ret = 2;
        return ret;
    }

    private void responseEnterDistrict(DeferredResult<ResponseEntity<AjaxResult> > result, String playerId, Integer districtId) {
        try {
            this.responseEnterDistrictImpl(result, playerId, districtId);
        } catch (HttpException ex) {
            responseHttpException(result, ex);
        } catch (Exception ex) {
            log.error("Response enter district error: {}", ex.getMessage());
            AjaxResult ajax = AjaxResult.error(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), ex.getMessage());
            result.setResult(new ResponseEntity<>(ajax, HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void responseEnterDistrictImpl(DeferredResult<ResponseEntity<AjaxResult> > result, String playerId, Integer districtId) {
        /**
     * 分配场地策略：
         * a、从Redis中获取指定区域(districtId)的未满场地列表NFL，并按玩家人数从多到少排列，划分NFL中玩家数量大于的前部分为NFL1，玩家数量为0的后部分为NFL2
         * b、从Redis中获取当前玩家5分钟内进入过的场地轨迹记录表TM(超过5分钟的轨迹点删除)
         * c、从头到尾遍历NFL1中的每个场地，以便获得一个授权场地AV：
         * c1、若场地在TM中则跳过
         * c2、否则从Redis中读取该场地的授权时间表ATL，并删除超过10秒钟的授权记录
         * c3、若当前玩家包含在该场地的ATL中，则可再次授权当前玩家进入该场地，该场地即为授权场地AV
         * c4、否则判断授权人数是否已满，若已满则检测下一个场地，跳到步骤c1，
         * c5、否则可授权当前玩家进入该场地，该场地即为授权场地AV
         * d、若能获得授权场地AV，退出函数并响应返回AV所在的服务器地址
         * e、否则清空TM（只要授权玩家数量为0的场地就清空轨迹记录），若NFL2为空，则新建一个场地并加入到NFL2
         * f、从NFL2中获取第一个场地作为授权场地AV
         * g、退出函数并响应返回AV所在的服务器地址
         */
        District district = this.districtMapper.selectById(districtId);
        if (district == null)
            throw new NotFoundException(NiuMaCodeEnum.DISTRICT_NOT_EXIST.getCode(), "指定区域不存在");
        assertEnoughCarryScoreForDistrict(playerId, districtId, district);
        String notFullKey = NiuMaRedisKeys.DISTRICT_NOT_FULL_VENUES + districtId.toString();
        Map<String, String> notFullMap = this.redisPrimitive.getMap(notFullKey);
        List<String> notFullVenues = null;
        int emptyIndex = -1;
        if ((notFullMap != null) && !notFullMap.isEmpty()) {
            notFullVenues = new ArrayList<>(notFullMap.size());
            notFullVenues.addAll(notFullMap.keySet());
            Collections.sort(notFullVenues, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    try {
                        Integer count1 = Integer.parseInt(notFullMap.get(o1));
                        Integer count2 = Integer.parseInt(notFullMap.get(o2));
                        if (count1 < count2)
                            return 1;
                        else if (count1 > count2)
                            return -1;
                        else
                            return 0;
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                }
            });
            Integer count = 0;
            for (int i = 0; i < notFullVenues.size(); i++) {
                try {
                    String venueId = notFullVenues.get(i);
                    count = Integer.parseInt(notFullMap.get(venueId));
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (count.equals(0)) {
                    emptyIndex = i;
                    break;
                }
            }
        }
        Long nowTime = System.currentTimeMillis();
        Long delta = 0L;
        Long tmpTime = 0L;
        List<String> removeKeys = new ArrayList<>();
        String trackKey = NiuMaRedisKeys.DISTRICT_PLAYER_TRACK;
        trackKey = trackKey.replace("{0}", districtId.toString());
        trackKey = trackKey.replace("{1}", playerId);
        Map<String, String> trackMap = this.redisPrimitive.getMap(trackKey);
        if (trackMap != null) {
            for (Map.Entry<String, String> entry : trackMap.entrySet()) {
                try {
                    tmpTime = Long.parseLong(entry.getValue());
                } catch (NumberFormatException ex) {
                    removeKeys.add(entry.getKey());
                    continue;
                }
                delta = nowTime - tmpTime;
                // 超过5分钟
                if (delta > 300000L)
                    removeKeys.add(entry.getKey());
            }
            for (String venueId : removeKeys) {
                this.redisPrimitive.hDelete(trackKey, venueId);
                trackMap.remove(venueId);
            }
        }
        String authorizedVenueId = null;
        if (notFullVenues != null) {
            int maxPlayerNum = this.getDistrictPlayerLimit(districtId);
            int loop = 0;
            boolean test = false;
            Integer count = 0;
            Long lock = 0L;
            String venueId = null;
            String authorizedKey = null;
            String lockKey = null;
            Map<String, String> authorizedMap = null;
            for (int i = 0; i < notFullVenues.size(); i++) {
                venueId = notFullVenues.get(i);
                if (((emptyIndex < 0) || (i < emptyIndex)) && (trackMap != null) && trackMap.containsKey(venueId))
                    continue;
                // 检查场地在数据库中的状态，跳过已结束或异常的场地
                Venue venueEntity = this.venueMapper.selectById(venueId);
                if (venueEntity == null || !venueEntity.getStatus().equals(0)) {
                    // 场地不存在或已结束，从 Redis 中移除
                    this.redisPrimitive.hDelete(notFullKey, venueId);
                    // 清理该场地的授权记录
                    String cleanupAuthKey = NiuMaRedisKeys.DISTRICT_AUTHORIZED_TIMES;
                    cleanupAuthKey = cleanupAuthKey.replace("{0}", districtId.toString());
                    cleanupAuthKey = cleanupAuthKey.replace("{1}", venueId);
                    this.redisPrimitive.delete(cleanupAuthKey);
                    continue;
                }
                lockKey = NiuMaRedisKeys.DISTRICT_AUTHORIZED_LOCK;
                lockKey = lockKey.replace("{0}", districtId.toString());
                lockKey = lockKey.replace("{1}", venueId);
                loop = 0;
                test = false;
                do {
                    lock = this.redisPrimitive.incr(lockKey, 1L);
                    if (lock.equals(1L))
                        break;
                    try {
                        Thread.sleep(100L);
                    } catch (Exception ex) {
                        log.error("Thread sleep error: {}", ex.getMessage());
                    }
                    loop++;
                    if (loop > 99) {
                        test = true;
                        break;
                    }
                } while (true);
                if (test)
                    continue;
                this.redisPrimitive.expire(lockKey, 2L);
                authorizedKey = NiuMaRedisKeys.DISTRICT_AUTHORIZED_TIMES;
                authorizedKey = authorizedKey.replace("{0}", districtId.toString());
                authorizedKey = authorizedKey.replace("{1}", venueId);
                authorizedMap = this.redisPrimitive.getMap(authorizedKey);
                if (authorizedMap != null) {
                    removeKeys.clear();
                    for (Map.Entry<String, String> entry : authorizedMap.entrySet()) {
                        try {
                            tmpTime = Long.parseLong(entry.getValue());
                        } catch (NumberFormatException ex) {
                            removeKeys.add(entry.getKey());
                            continue;
                        }
                        delta = nowTime - tmpTime;
                        // 超过10秒钟
                        if (delta > 10000L)
                            removeKeys.add(entry.getKey());
                    }
                    for (String tmpKey : removeKeys) {
                        this.redisPrimitive.hDelete(authorizedKey, tmpKey);
                        authorizedMap.remove(tmpKey);
                    }
                }
                if ((authorizedMap == null) || authorizedMap.containsKey(playerId) || (authorizedMap.size() < maxPlayerNum)) {
                    // 无授权记录，可授权
                    // 已授权，可再次授权
                    // 授权人数未满可授权
                    authorizedVenueId = venueId;
                    this.redisPrimitive.hSet(authorizedKey, playerId, nowTime.toString());
                    this.redisPrimitive.delete(lockKey);
                    if ((emptyIndex > -1) && (i >= emptyIndex)) {
                        // 清空轨迹记录
                        this.redisPrimitive.delete(trackKey);
                    }
                    break;
                }
                this.redisPrimitive.delete(lockKey);
            }
        }
        if (StringUtils.isNotEmpty(authorizedVenueId)) {
            this.responseEnterVenue(result, playerId, authorizedVenueId);
            return;
        }
        // 清空轨迹记录
        this.redisPrimitive.delete(trackKey);
        // 创建新的区域内场地
        authorizedVenueId = this.createDistrictVenue(districtId);
        String registerKey = NiuMaRedisKeys.DISTRICT_VENUE_REGISTER + districtId.toString();
        this.redisPrimitive.hSet(registerKey, authorizedVenueId, nowTime.toString());
        this.redisPrimitive.hSet(notFullKey, authorizedVenueId, "0");
        String authorizedKey = NiuMaRedisKeys.DISTRICT_AUTHORIZED_TIMES;
        authorizedKey = authorizedKey.replace("{0}", districtId.toString());
        authorizedKey = authorizedKey.replace("{1}", authorizedVenueId);
        this.redisPrimitive.hSet(authorizedKey, playerId, nowTime.toString());
        this.responseEnterVenue(result, playerId, authorizedVenueId);
    }

    private String createDistrictVenue(Integer districtId) {
        String venueId = null;
        Venue venue = new Venue();
        venue.setOwnerId("0000000000");
        venue.setDistrictId(districtId);
        venue.setStatus(0);
        venue.setCreateTime(LocalDateTime.now());
        if (districtId.equals(NiuMaConstants.DISTRICT_LACKEY_BEGINNER) ||
            districtId.equals(NiuMaConstants.DISTRICT_LACKEY_MODERATE) ||
            districtId.equals(NiuMaConstants.DISTRICT_LACKEY_ADVANCED) ||
            districtId.equals(NiuMaConstants.DISTRICT_LACKEY_MASTER)) {
            venueId = generateVenueId();
            venue.setId(venueId);
            venue.setGameType(NiuMaConstants.GAME_TYPE_LACKEY);
            this.venueMapper.insert(venue);
            String number = "dist-" + districtId.toString();
            GameLackey entity = new GameLackey();
            entity.setNumber(number);
            entity.setVenueId(venueId);
            if (districtId.equals(NiuMaConstants.DISTRICT_LACKEY_BEGINNER)) {
                entity.setLevel(LackeyRoomLevel.Beginner.ordinal());
                entity.setMode(0);
                entity.setDiZhu(100);
            } else if (districtId.equals(NiuMaConstants.DISTRICT_LACKEY_MODERATE)) {
                entity.setLevel(LackeyRoomLevel.Moderate.ordinal());
                entity.setMode(0);
                entity.setDiZhu(200);
            } else if (districtId.equals(NiuMaConstants.DISTRICT_LACKEY_ADVANCED)) {
                entity.setLevel(LackeyRoomLevel.Advanced.ordinal());
                entity.setMode(1);
                entity.setDiZhu(500);
            } else if (districtId.equals(NiuMaConstants.DISTRICT_LACKEY_MASTER)) {
                entity.setLevel(LackeyRoomLevel.Master.ordinal());
                entity.setMode(1);
                entity.setDiZhu(1000);
            }
            this.lackeyMapper.insert(entity);
        } else if (districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_BEGINNER) ||
                districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_MODERATE) ||
                districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_ADVANCED) ||
                districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_MASTER)) {
            venueId = generateVenueId();
            venue.setId(venueId);
            venue.setGameType(NiuMaConstants.GAME_TYPE_GUAN_DAN);
            this.venueMapper.insert(venue);
            String number = "dist-" + districtId.toString();
            GameGuanDan entity = new GameGuanDan();
            entity.setVenueId(venueId);
            entity.setNumber(number);
            if (districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_BEGINNER)) {
                entity.setLevel(GuanDanLevel.Beginner.ordinal());
            } else if (districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_MODERATE)) {
                entity.setLevel(GuanDanLevel.Moderate.ordinal());
            } else if (districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_ADVANCED)) {
                entity.setLevel(GuanDanLevel.Advanced.ordinal());
            } else if (districtId.equals(NiuMaConstants.DISTRICT_GUAN_DAN_MASTER)) {
                entity.setLevel(GuanDanLevel.Master.ordinal());
            }
            entity.setRuleConfig(buildGuandanDistrictRuleConfig(resolveGuandanBaseScore(districtId), 8));
            this.guanDanMapper.insert(entity);
        }
        // 桃江麻将 districts (9-16)
        else if (isTaojiangDistrict(districtId)) {
            venueId = generateVenueId();
            venue.setId(venueId);
            venue.setGameType(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG);
            this.venueMapper.insert(venue);
            String number = "dist-" + districtId.toString();
            GameTaojiangMahjong entity = new GameTaojiangMahjong();
            entity.setNumber(number);
            entity.setVenueId(venueId);
            entity.setLevel(GuanDanLevel.Beginner.ordinal());
            entity.setRuleConfig(buildTaojiangDistrictRuleConfig(resolveTaojiangBaseScore(districtId), resolveTaojiangRoundCount(districtId)));
            this.taojiangMahjongMapper.insert(entity);
        }
        // 红中麻将 districts
        else if (isHongzhongDistrict(districtId)) {
            venueId = generateVenueId();
            venue.setId(venueId);
            venue.setGameType(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG);
            this.venueMapper.insert(venue);
            String number = "dist-" + districtId.toString();
            GameHongzhongMahjong entity = new GameHongzhongMahjong();
            entity.setNumber(number);
            entity.setVenueId(venueId);
            entity.setLevel(GuanDanLevel.Beginner.ordinal());
            entity.setRuleConfig(buildHongzhongDistrictRuleConfig(resolveHongzhongBaseScore(districtId),
                    resolveHongzhongRoundCount(districtId)));
            this.hongzhongMahjongMapper.insert(entity);
        }
        // 长沙麻将 districts
        else if (isChangshaDistrict(districtId)) {
            venueId = generateVenueId();
            venue.setId(venueId);
            venue.setGameType(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG);
            this.venueMapper.insert(venue);
            String number = "dist-" + districtId.toString();
            GameChangshaMahjong entity = new GameChangshaMahjong();
            entity.setNumber(number);
            entity.setVenueId(venueId);
            entity.setLevel(GuanDanLevel.Beginner.ordinal());
            entity.setRuleConfig(buildChangshaDistrictRuleConfig(resolveChangshaBaseScore(districtId),
                    resolveChangshaRoundCount(districtId)));
            this.changshaMahjongMapper.insert(entity);
        }
        // 跑得快 districts
        else if (isPaodekuaiDistrict(districtId)) {
            venueId = generateVenueId();
            venue.setId(venueId);
            venue.setGameType(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI);
            this.venueMapper.insert(venue);
            String number = "dist-" + districtId.toString();
            GamePaodekuai entity = new GamePaodekuai();
            entity.setNumber(number);
            entity.setVenueId(venueId);
            entity.setLevel(GuanDanLevel.Beginner.ordinal());
            entity.setRuleConfig(buildPaodekuaiDistrictRuleConfig(resolvePaodekuaiBaseScore(districtId),
                    resolvePaodekuaiRoundCount(districtId)));
            this.paodekuaiMapper.insert(entity);
        }
        // 歪胡子 districts (29-32)
        else if (isWaihuziDistrict(districtId)) {
            venueId = generateVenueId();
            venue.setId(venueId);
            venue.setGameType(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI);
            this.venueMapper.insert(venue);
            String number = "dist-" + districtId.toString();
            GameYiyangWaihuzi entity = new GameYiyangWaihuzi();
            entity.setNumber(number);
            entity.setVenueId(venueId);
            entity.setLevel(GuanDanLevel.Beginner.ordinal());
            entity.setRuleConfig(buildDistrictRuleConfig(resolveWaihuziBaseScore(districtId), 8));
            this.yiyangWaihuziMapper.insert(entity);
        }
        // 沅江千分 districts (33-36)
        else if (isQianfenDistrict(districtId)) {
            venueId = generateVenueId();
            venue.setId(venueId);
            venue.setGameType(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN);
            this.venueMapper.insert(venue);
            String number = "dist-" + districtId.toString();
            GameYuanjiangQianfen entity = new GameYuanjiangQianfen();
            entity.setNumber(number);
            entity.setVenueId(venueId);
            entity.setLevel(GuanDanLevel.Beginner.ordinal());
            entity.setRuleConfig(buildDistrictRuleConfig(resolveQianfenBaseScore(districtId), 8));
            this.yuanjiangQianfenMapper.insert(entity);
        }
        // 斗地主 districts (37-40)
        else if (isDoudizhuDistrict(districtId)) {
            venueId = generateVenueId();
            venue.setId(venueId);
            venue.setGameType(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU);
            this.venueMapper.insert(venue);
            String number = "dist-" + districtId.toString();
            GameDoudizhu entity = new GameDoudizhu();
            entity.setNumber(number);
            entity.setVenueId(venueId);
            entity.setLevel(GuanDanLevel.Beginner.ordinal());
            entity.setRuleConfig(buildDoudizhuDistrictRuleConfig(resolveDoudizhuBaseScore(districtId), 8));
            this.doudizhuMapper.insert(entity);
        }
        return venueId;
    }

    // ==================== District 判断辅助方法 ====================

    private boolean isTaojiangDistrict(int id) {
        return id >= NiuMaConstants.DISTRICT_TAOJIANG_B5_R1 && id <= NiuMaConstants.DISTRICT_TAOJIANG_B20_R8;
    }
    private boolean isHongzhongDistrict(int id) {
        return isKnownDistrict(id,
                NiuMaConstants.DISTRICT_HONGZHONG_B5_R1,
                NiuMaConstants.DISTRICT_HONGZHONG_B10_R1,
                NiuMaConstants.DISTRICT_HONGZHONG_B25_R1,
                NiuMaConstants.DISTRICT_HONGZHONG_B1_R8,
                NiuMaConstants.DISTRICT_HONGZHONG_B2_R8,
                NiuMaConstants.DISTRICT_HONGZHONG_B5_R8,
                NiuMaConstants.DISTRICT_HONGZHONG_B10_R8,
                NiuMaConstants.DISTRICT_HONGZHONG_B20_R8);
    }
    private boolean isChangshaDistrict(int id) {
        return isKnownDistrict(id,
                NiuMaConstants.DISTRICT_CHANGSHA_B5_R1,
                NiuMaConstants.DISTRICT_CHANGSHA_B10_R1,
                NiuMaConstants.DISTRICT_CHANGSHA_B25_R1,
                NiuMaConstants.DISTRICT_CHANGSHA_B1_R8,
                NiuMaConstants.DISTRICT_CHANGSHA_B2_R8,
                NiuMaConstants.DISTRICT_CHANGSHA_B5_R8,
                NiuMaConstants.DISTRICT_CHANGSHA_B10_R8,
                NiuMaConstants.DISTRICT_CHANGSHA_B20_R8);
    }
    private boolean isPaodekuaiDistrict(int id) {
        return isKnownDistrict(id,
                NiuMaConstants.DISTRICT_PAO_DE_KUAI_B5_R1,
                NiuMaConstants.DISTRICT_PAO_DE_KUAI_B10_R1,
                NiuMaConstants.DISTRICT_PAO_DE_KUAI_B25_R1,
                NiuMaConstants.DISTRICT_PAO_DE_KUAI_B1_R8,
                NiuMaConstants.DISTRICT_PAO_DE_KUAI_B2_R8,
                NiuMaConstants.DISTRICT_PAO_DE_KUAI_B5_R8,
                NiuMaConstants.DISTRICT_PAO_DE_KUAI_B10_R8,
                NiuMaConstants.DISTRICT_PAO_DE_KUAI_B20_R8);
    }
    private boolean isWaihuziDistrict(int id) {
        return id >= NiuMaConstants.DISTRICT_WAIHUZI_B1_R8 && id <= NiuMaConstants.DISTRICT_WAIHUZI_B10_R8;
    }
    private boolean isQianfenDistrict(int id) {
        return id >= NiuMaConstants.DISTRICT_QIANFEN_B1_R8 && id <= NiuMaConstants.DISTRICT_QIANFEN_B10_R8;
    }
    private boolean isDoudizhuDistrict(int id) {
        return id >= NiuMaConstants.DISTRICT_DOU_DI_ZHU_B1_R8 && id <= NiuMaConstants.DISTRICT_DOU_DI_ZHU_B10_R8;
    }

    private boolean isKnownDistrict(int id, int... supportedIds) {
        for (int supportedId : supportedIds) {
            if (id == supportedId) {
                return true;
            }
        }
        return false;
    }

    /** 构造 district 场地的 ruleConfig JSON */
    private String buildDistrictRuleConfig(int baseScore, int roundCount) {
        JSONObject rule = new JSONObject();
        rule.put("level", 3);
        rule.put("base_score", baseScore);
        rule.put("round_count", roundCount);
        rule.put("max_score", 0);
        putRoomFee(rule, resolveDefaultRoomFee(baseScore));
        return rule.toJSONString();
    }

    /** 构造桃江麻将 district 场地的 ruleConfig JSON */
    private String buildTaojiangDistrictRuleConfig(int baseScore, int roundCount) {
        JSONObject rule = new JSONObject();
        rule.put("level", 3);
        fillTaojiangRuleDefaults(rule, baseScore, roundCount);
        return rule.toJSONString();
    }

    /** 构造红中麻将 district 场地的 ruleConfig JSON */
    private String buildHongzhongDistrictRuleConfig(int baseScore, int roundCount) {
        JSONObject rule = new JSONObject();
        rule.put("level", 3);
        fillHongzhongRuleDefaults(rule, baseScore, roundCount, 2);
        return rule.toJSONString();
    }

    /** 构造长沙麻将 district 场地的 ruleConfig JSON */
    private String buildChangshaDistrictRuleConfig(int baseScore, int roundCount) {
        JSONObject rule = new JSONObject();
        rule.put("level", 3);
        fillChangshaRuleDefaults(rule, baseScore, roundCount);
        return rule.toJSONString();
    }

    private String buildDoudizhuDistrictRuleConfig(int baseScore, int roundCount) {
        JSONObject rule = new JSONObject();
        rule.put("level", 3);
        rule.put("base_score", baseScore);
        rule.put("round_count", roundCount);
        rule.put("max_score", 0);
        rule.put("player_count", 2);
        rule.put("remove_three_and_four", false);
        rule.put("hand_card_count", DOUDIZHU_HAND_CARD_COUNT);
        rule.put("bottom_card_count", DOUDIZHU_BOTTOM_CARD_COUNT);
        rule.put("auto_play_timeout", DOUDIZHU_AUTO_PLAY_TIMEOUT);
        putRoomFee(rule, resolveDefaultRoomFee(baseScore));
        return rule.toJSONString();
    }

    private String buildGuandanDistrictRuleConfig(int baseScore, int roundCount) {
        JSONObject rule = new JSONObject();
        rule.put("level", 3);
        rule.put("base_score", baseScore);
        rule.put("round_count", roundCount);
        rule.put("player_count", 4);
        putRoomFee(rule, resolveDefaultRoomFee(baseScore));
        return rule.toJSONString();
    }

    private String buildPaodekuaiDistrictRuleConfig(int baseScore, int roundCount) {
        JSONObject rule = new JSONObject();
        rule.put("level", 3);
        fillPaodekuaiRuleDefaults(rule, baseScore, roundCount);
        return rule.toJSONString();
    }

    // ==================== District -> baseScore/roundCount 映射 ====================

    private int resolveTaojiangBaseScore(int id) {
        if (id == NiuMaConstants.DISTRICT_TAOJIANG_B5_R1 || id == NiuMaConstants.DISTRICT_TAOJIANG_B5_R8) return 5;
        if (id == NiuMaConstants.DISTRICT_TAOJIANG_B10_R1 || id == NiuMaConstants.DISTRICT_TAOJIANG_B10_R8) return 10;
        if (id == NiuMaConstants.DISTRICT_TAOJIANG_B25_R1) return 25;
        if (id == NiuMaConstants.DISTRICT_TAOJIANG_B2_R8) return 2;
        if (id == NiuMaConstants.DISTRICT_TAOJIANG_B20_R8) return 20;
        return 1;
    }
    private int resolveTaojiangRoundCount(int id) {
        if (id == NiuMaConstants.DISTRICT_TAOJIANG_B5_R1 ||
                id == NiuMaConstants.DISTRICT_TAOJIANG_B10_R1 ||
                id == NiuMaConstants.DISTRICT_TAOJIANG_B25_R1) return 1;
        return 8;
    }
    private int resolveHongzhongBaseScore(int id) {
        if (id == NiuMaConstants.DISTRICT_HONGZHONG_B5_R1 || id == NiuMaConstants.DISTRICT_HONGZHONG_B5_R8) return 5;
        if (id == NiuMaConstants.DISTRICT_HONGZHONG_B10_R1 || id == NiuMaConstants.DISTRICT_HONGZHONG_B10_R8) return 10;
        if (id == NiuMaConstants.DISTRICT_HONGZHONG_B25_R1) return 25;
        if (id == NiuMaConstants.DISTRICT_HONGZHONG_B2_R8) return 2;
        if (id == NiuMaConstants.DISTRICT_HONGZHONG_B20_R8) return 20;
        return 1;
    }
    private int resolveHongzhongRoundCount(int id) {
        if (id == NiuMaConstants.DISTRICT_HONGZHONG_B5_R1 ||
                id == NiuMaConstants.DISTRICT_HONGZHONG_B10_R1 ||
                id == NiuMaConstants.DISTRICT_HONGZHONG_B25_R1) return 1;
        return 8;
    }
    private int resolveChangshaBaseScore(int id) {
        if (id == NiuMaConstants.DISTRICT_CHANGSHA_B5_R1 || id == NiuMaConstants.DISTRICT_CHANGSHA_B5_R8) return 5;
        if (id == NiuMaConstants.DISTRICT_CHANGSHA_B10_R1 || id == NiuMaConstants.DISTRICT_CHANGSHA_B10_R8) return 10;
        if (id == NiuMaConstants.DISTRICT_CHANGSHA_B25_R1) return 25;
        if (id == NiuMaConstants.DISTRICT_CHANGSHA_B2_R8) return 2;
        if (id == NiuMaConstants.DISTRICT_CHANGSHA_B20_R8) return 20;
        return 1;
    }
    private int resolveChangshaRoundCount(int id) {
        if (id == NiuMaConstants.DISTRICT_CHANGSHA_B5_R1 ||
                id == NiuMaConstants.DISTRICT_CHANGSHA_B10_R1 ||
                id == NiuMaConstants.DISTRICT_CHANGSHA_B25_R1) return 1;
        return 8;
    }
    private int resolvePaodekuaiBaseScore(int id) {
        if (id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B5_R1 || id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B5_R8) return 5;
        if (id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B10_R1 || id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B10_R8) return 10;
        if (id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B25_R1) return 25;
        if (id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B2_R8) return 2;
        if (id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B20_R8) return 20;
        return 1;
    }
    private int resolvePaodekuaiRoundCount(int id) {
        if (id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B5_R1 ||
                id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B10_R1 ||
                id == NiuMaConstants.DISTRICT_PAO_DE_KUAI_B25_R1) return 1;
        return 8;
    }
    private int resolveWaihuziBaseScore(int id) {
        if (id == NiuMaConstants.DISTRICT_WAIHUZI_B1_R8) return 1;
        if (id == NiuMaConstants.DISTRICT_WAIHUZI_B2_R8) return 2;
        if (id == NiuMaConstants.DISTRICT_WAIHUZI_B5_R8) return 5;
        return 10;
    }
    private int resolveQianfenBaseScore(int id) {
        if (id == NiuMaConstants.DISTRICT_QIANFEN_B1_R8) return 1;
        if (id == NiuMaConstants.DISTRICT_QIANFEN_B2_R8) return 2;
        if (id == NiuMaConstants.DISTRICT_QIANFEN_B5_R8) return 5;
        return 10;
    }
    private int resolveDoudizhuBaseScore(int id) {
        if (id == NiuMaConstants.DISTRICT_DOU_DI_ZHU_B1_R8) return 1;
        if (id == NiuMaConstants.DISTRICT_DOU_DI_ZHU_B2_R8) return 2;
        if (id == NiuMaConstants.DISTRICT_DOU_DI_ZHU_B5_R8) return 5;
        return 10;
    }

    private int resolveGuandanBaseScore(int id) {
        if (id == NiuMaConstants.DISTRICT_GUAN_DAN_MODERATE) return 5;
        if (id == NiuMaConstants.DISTRICT_GUAN_DAN_ADVANCED) return 10;
        if (id == NiuMaConstants.DISTRICT_GUAN_DAN_MASTER) return 20;
        return 1;
    }

    @Override
    public void enterDistrict(DeferredResult<ResponseEntity<AjaxResult>> result, Integer districtId) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        District entity = this.districtMapper.selectById(districtId);
        if (entity == null) {
            AjaxResult ajax = new AjaxResult();
            ajax.put(AjaxResult.CODE_TAG, NiuMaCodeEnum.DISTRICT_NOT_EXIST.getCode());
            ajax.put(AjaxResult.MSG_TAG, "指定区域不存在");
            result.setResult(new ResponseEntity<>(ajax, HttpStatus.NOT_FOUND));
            return;
        }
        Long diamondNeed = entity.getDiamondNeed();
        if (diamondNeed != null && diamondNeed > 0L) {
            // 扣钻模式
            Long diamond = this.capitalMapper.getDiamond(player.getId());
            if ((diamond == null) || (diamond < diamondNeed)) {
                StringBuilder sb = new StringBuilder();
                sb.append("钻石不足，最低需要");
                sb.append(diamondNeed);
                sb.append("枚钻石");
                throw new ForbiddenException(NiuMaCodeEnum.DIAMOND_INSUFFICIENT_ERROR.getCode(), sb.toString());
            }
        }
        String venueId = districtId.toString();
        MqCommandDeferred actionDeferred = this.checkBeforeEnter(player.getId(), venueId, (playerIdIn, venueIdIn) -> {
            // 响应进入指定区域
            responseEnterDistrict(result, playerIdIn, districtId);
        });
        if (actionDeferred != null) {
            // 等待服务器处理离开场地命令
            actionDeferred.setAction(NiuMaConstants.ACTION_ENTER_DISTRICT);
            // 离开成功后进入指定区域
            actionDeferred.setVenueId(venueId);
            actionDeferred.setResult(result);
        }
    }

    @Override
    public AjaxResult getDistrictPlayerCount(Integer districtId) {
        LambdaQueryWrapper<District> query = Wrappers.lambdaQuery();
        query.eq(District::getId, districtId);
        Integer count = this.districtMapper.selectCount(query);
        if (count == null || count < 1)
            throw new NotFoundException(NiuMaCodeEnum.DISTRICT_NOT_EXIST.getCode(), "指定区域不存在");
        String countKey = NiuMaRedisKeys.DISTRICT_PLAYER_COUNT + districtId.toString();
        Map<String, String> countMap = this.redisPrimitive.getMap(countKey);
        boolean test = false;
        Long nowTime = System.currentTimeMillis();
        Long tmpTime = 0L;
        Long delta = 0L;
        Integer playerCount = 0;
        if (countMap != null) {
            String time = this.redisPrimitive.hGet(countKey, "time");
            if (StringUtils.isNotEmpty(time)) {
                try {
                    tmpTime = Long.parseLong(time);
                    delta = nowTime - tmpTime;
                    if (delta < 5000L)  // 每5秒全局统计更新一次
                        test = true;
                } catch (NumberFormatException ex) {}
            }
            if (test) {
                String text = this.redisPrimitive.hGet(countKey, "playerCount");
                if (StringUtils.isNotEmpty(text)) {
                    try {
                        playerCount = Integer.parseInt(text);
                        AjaxResult ajax = AjaxResult.successEx();
                        ajax.put("playerCount", playerCount);
                        return ajax;
                    } catch (NumberFormatException ex) {}
                }
            }
        }
        String registerKey = NiuMaRedisKeys.DISTRICT_VENUE_REGISTER + districtId.toString();
        Map<String, String> registerMap = this.redisPrimitive.getMap(registerKey);
        if (registerMap != null) {
            for (Map.Entry<String, String> entry : registerMap.entrySet()) {
                test = false;
                try {
                    tmpTime = Long.parseLong(entry.getValue());
                    delta = nowTime - tmpTime;
                    if (delta > 30000L)
                        test = true;    // 超30秒不刷新
                } catch (Exception ex) {
                    test = true;
                }
                if (test)
                    this.redisPrimitive.hDelete(registerKey, entry.getKey());
                else {
                    String tmpKey = NiuMaRedisKeys.VENUE_PLAYER_COUNT + entry.getKey();
                    String text = this.redisPrimitive.get(tmpKey);
                    try {
                        count = Integer.parseInt(text);
                        playerCount += count;
                    } catch (NumberFormatException ex) { }
                }
            }
        }
        this.redisPrimitive.hSet(countKey, "playerCount", playerCount.toString());
        this.redisPrimitive.hSet(countKey, "time", nowTime.toString());
                AjaxResult ajax = AjaxResult.successEx();
        ajax.put("playerCount", playerCount);
        return ajax;
    }

    @Override
    public AjaxResult getDistrictVenues(Integer districtId) {
        District entity = this.districtMapper.selectById(districtId);
        if (entity == null)
            throw new NotFoundException(NiuMaCodeEnum.DISTRICT_NOT_EXIST.getCode(), "指定区域不存在");
        int maxPlayerNum = this.getDistrictPlayerLimit(districtId);
        // 从 Redis 读取区域内未满场地列表（venueId -> playerCount）
        String notFullKey = NiuMaRedisKeys.DISTRICT_NOT_FULL_VENUES + districtId.toString();
        Map<String, String> notFullMap = this.redisPrimitive.getMap(notFullKey);
        List<Map<String, Object>> venues = new ArrayList<>();
        if (notFullMap != null) {
            for (Map.Entry<String, String> entry : notFullMap.entrySet()) {
                String venueId = entry.getKey();
                int playerCount = 0;
                try {
                    playerCount = Integer.parseInt(entry.getValue());
                } catch (NumberFormatException ex) {
                    continue;
                }
                // 只返回有空位的房间（playerCount < maxPlayerNum）
                if (playerCount >= maxPlayerNum)
                    continue;
                // 校验场地状态是否正常
                Venue venueEntity = this.venueMapper.selectById(venueId);
                if (venueEntity == null || !venueEntity.getStatus().equals(0))
                    continue;
                Map<String, Object> item = new HashMap<>();
                item.put("venueId", venueId);
                item.put("districtId", districtId);
                item.put("gameType", venueEntity.getGameType());
                item.put("number", getDistrictVenueNumber(venueEntity));
                item.put("playerCount", playerCount);
                item.put("maxPlayerNums", maxPlayerNum);
                item.put("baseScore", resolveDistrictBaseScore(districtId));
                item.put("roundCount", resolveDistrictRoundCount(districtId));
                item.put("minCarryScore", resolveMinCarryScoreForDistrict(districtId, entity));
                venues.add(item);
            }
        }
        // 按人数从多到少排序（优先展示快满的房间）
        venues.sort((a, b) -> Integer.compare(
            (int) b.get("playerCount"), (int) a.get("playerCount")));
        AjaxResult result = AjaxResult.successEx();
        result.put("items", venues);
        result.put("maxPlayerNums", maxPlayerNum);
        return result;
    }

    private String getDistrictVenueNumber(Venue venue) {
        if (venue == null || StringUtils.isEmpty(venue.getId()) || venue.getGameType() == null)
            return null;
        Integer gameType = venue.getGameType();
        String venueId = venue.getId();
        if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU))
            return this.doudizhuMapper.getNumber(venueId);
        if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG))
            return this.taojiangMahjongMapper.getNumber(venueId);
        if (gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG))
            return this.hongzhongMahjongMapper.getNumber(venueId);
        if (gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG))
            return this.changshaMahjongMapper.getNumber(venueId);
        if (gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI))
            return this.paodekuaiMapper.getNumber(venueId);
        if (gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI))
            return this.yiyangWaihuziMapper.getNumber(venueId);
        if (gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN))
            return this.yuanjiangQianfenMapper.getNumber(venueId);
        if (gameType.equals(NiuMaConstants.GAME_TYPE_GUAN_DAN))
            return this.guanDanMapper.getNumber(venueId);
        if (gameType.equals(NiuMaConstants.GAME_TYPE_LACKEY))
            return this.lackeyMapper.getNumber(venueId);
        return venueId;
    }

    private int resolveDistrictBaseScore(Integer districtId) {
        if (districtId == null) return 0;
        if (isTaojiangDistrict(districtId)) return resolveTaojiangBaseScore(districtId);
        if (isHongzhongDistrict(districtId)) return resolveHongzhongBaseScore(districtId);
        if (isChangshaDistrict(districtId)) return resolveChangshaBaseScore(districtId);
        if (isPaodekuaiDistrict(districtId)) return resolvePaodekuaiBaseScore(districtId);
        if (isWaihuziDistrict(districtId)) return resolveWaihuziBaseScore(districtId);
        if (isQianfenDistrict(districtId)) return resolveQianfenBaseScore(districtId);
        if (isDoudizhuDistrict(districtId)) return resolveDoudizhuBaseScore(districtId);
        return 0;
    }

    private int resolveDistrictRoundCount(Integer districtId) {
        if (districtId == null) return 0;
        if (isTaojiangDistrict(districtId)) return resolveTaojiangRoundCount(districtId);
        return 8;
    }

    @Override
    public void consume(MqMessage msg) {
        String json = null;
        try {
            json = new String(Base64.decode(msg.getMsgPack()), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            log.error("Parse message error: {}", ex.getMessage());
            return;
        }
        if ("CommandResult".equals(msg.getMsgType())) {
            MqCommandResult result = this.jsonUtils.convertToObj(json, MqCommandResult.class);
            handleCommandResult(result);
        } else if ("WalletChangeEvent".equals(msg.getMsgType())) {
            walletChangeEventProcessor.process(json);
        }
    }

    private void handleCommandResult(MqCommandResult result) {
        MqCommandDeferred cmd = getCommandDeferred(result.getCommandId());
        if (cmd == null)
            return;
        DeferredResult<ResponseEntity<AjaxResult> > deferredResult = cmd.getResult();
        // 异步命令完成，释放进入场地锁
        String lockKey = NiuMaRedisKeys.PLAYER_ENTER_LOCK + cmd.getPlayerId();
        this.redisPrimitive.delete(lockKey);
        int action = cmd.getAction();
        Integer districtId = null;
        if (action == NiuMaConstants.ACTION_ENTER_DISTRICT) {
            try {
                districtId = Integer.parseInt(cmd.getVenueId());
            } catch (NumberFormatException ex) {
                AjaxResult ajax = AjaxResult.error(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), ex.getMessage());
                deferredResult.setResult(new ResponseEntity<>(ajax, HttpStatus.INTERNAL_SERVER_ERROR));
                return;
            }
        }
        if ((result.getResult() != null) && !(result.getResult().equals(0))) {
            if (action == NiuMaConstants.ACTION_ENTER_DISTRICT) {
                // 进入区域，离开当前所在的场地失败，判断当前所在的场地是否属于要进入的区域，若是则立即重新进入当前所在的场地
                Integer tmpId = this.venueMapper.getDistrictId(cmd.getCurrentVenue());
                if ((tmpId != null) && (tmpId.equals(districtId))) {
                    responseEnterVenue(deferredResult, cmd.getPlayerId(), cmd.getCurrentVenue());
                    return;
                }
            }
            // 返回错误
            AjaxResult ajax = new AjaxResult();
            ajax.put(AjaxResult.CODE_TAG, NiuMaCodeEnum.DEFERRED_COMMAND_FAILED.getCode());
            if (StringUtils.isNotEmpty(result.getMessage()))
                ajax.put(AjaxResult.MSG_TAG, result.getMessage());
            deferredResult.setResult(new ResponseEntity<>(ajax, HttpStatus.INTERNAL_SERVER_ERROR));
            return;
        }
        if (action == NiuMaConstants.ACTION_CREATE_GAME) {
            try {
                // 创建游戏
                String venueId = createGame(cmd.getGameType(), cmd.getPlayerId(), cmd.getBase64());
                // 响应进入新创建的场地
                responseEnterVenue(deferredResult, cmd.getPlayerId(), venueId);
            } catch (HttpException ex) {
                AjaxResult ajax = new AjaxResult();
                if (StringUtils.isNotEmpty(ex.getCode()))
                    ajax.put(AjaxResult.CODE_TAG, ex.getCode());
                if (StringUtils.isNotEmpty(ex.getMessage()))
                    ajax.put(AjaxResult.MSG_TAG, ex.getMessage());
                deferredResult.setResult(new ResponseEntity<>(ajax, ex.getStatus()));
            }
        } else if (action == NiuMaConstants.ACTION_ENTER_GAME) {
            String playerId = cmd.getPlayerId();
            String venueId = cmd.getVenueId();
            try {
                Venue venue = this.venueMapper.selectById(venueId);
                if (venue == null)
                    throw new NotFoundException(NiuMaCodeEnum.VENUE_NOT_EXIST);
                assertEnoughCarryScoreForVenue(playerId, venue);
                // 响应进入指定场地
                responseEnterVenue(deferredResult, playerId, venueId);
            } catch (HttpException ex) {
                responseHttpException(deferredResult, ex);
            }
        } else if (action == NiuMaConstants.ACTION_ENTER_DISTRICT) {
            String playerId = cmd.getPlayerId();
            // 响应进入指定区域
            responseEnterDistrict(deferredResult, playerId, districtId);
        }
    }

    private MqCommandDeferred getCommandDeferred(String commandId) {
        MqCommandDeferred cmd = null;
        try {
            this.lock.lock();
            if (this.commandDeferredMap.containsKey(commandId)) {
                cmd = this.commandDeferredMap.get(commandId);
                this.commandDeferredMap.remove(commandId);
            }
        } finally {
            this.lock.unlock();
        }
        return cmd;
    }

    /**
     * 每100毫秒秒执行一次
     */
    @Scheduled(fixedRate = 100)
    public void fixedRateTask() {
        MqCommandDeferred cmd = this.getTimeoutCommandDeferred();
        if (cmd == null)
            return;
        AjaxResult ajax = new AjaxResult();
        ajax.put(AjaxResult.CODE_TAG, NiuMaCodeEnum.DEFERRED_COMMAND_FAILED.getCode());
        ajax.put(AjaxResult.MSG_TAG, "Deferred command timeout");
        DeferredResult<ResponseEntity<AjaxResult> > deferredResult = cmd.getResult();
        deferredResult.setResult(new ResponseEntity<>(ajax, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private MqCommandDeferred getTimeoutCommandDeferred() {
        MqCommandDeferred cmd = null;
        try {
            this.lock.lock();
            while (!(this.commandDeferredMap.isEmpty() || this.commandDeferredSequence.isEmpty())) {
                String commandId = this.commandDeferredSequence.getFirst();
                cmd = this.commandDeferredMap.get(commandId);
                if (cmd == null) {
                    this.commandDeferredSequence.pop();
                    continue;
                }
                Long nowTime = System.currentTimeMillis();
                Long delta = nowTime - cmd.getCreateTime();
                if (delta > 3000L) {
                    // 若超过3秒仍未返回，则认为命令超时
                    this.commandDeferredMap.remove(commandId);
                    this.commandDeferredSequence.pop();
                } else {
                    // 最先创建的异步命令尚未超时，直接返回null
                    cmd = null;
                }
                break;
            }
        } finally {
            this.lock.unlock();
        }
        return cmd;
    }

    @Override
    public PageResult<MahjongRecordDTO> getMahjongRecord(PageBody dto) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        PageResult<MahjongRecordDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        LocalDateTime cutoff = getRecordRetentionCutoff();
        Integer totalNum = this.mahjongMapper.countRecord(player.getId(), cutoff);
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<MahjongRecord> records = this.mahjongMapper.getRecords(player.getId(), cutoff, offset, dto.getPageSize());
        if ((records == null) || records.isEmpty())
            return result;
        List<MahjongRecordDTO> dtos = new ArrayList<>();
        Map<String, String> numberMap = new HashMap<>();
        Map<String, PlayerBaseDTO> playerMap = new HashMap<>();
        List<String> playerIds = new ArrayList<>();
        List<PlayerBaseDTO> playerInfos = null;
        List<Integer> scores = null;
        List<Long> winGolds = null;
        String number = null;
        PlayerBaseDTO pbd = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
        for (MahjongRecord record : records) {
            MahjongRecordDTO tmp = new MahjongRecordDTO();
            tmp.setId(record.getId());
            tmp.setVenueId(record.getVenueId());
            if (numberMap.containsKey(record.getVenueId()))
                number = numberMap.get(record.getVenueId());
            else {
                number = this.mahjongMapper.getNumber(record.getVenueId());
                numberMap.put(record.getVenueId(), number);
            }
            tmp.setNumber(number);
            tmp.setRoundNo(record.getRoundNo());
            tmp.setBanker(record.getBanker());
            playerIds.clear();
            playerIds.add(record.getPlayerId0());
            playerIds.add(record.getPlayerId1());
            playerIds.add(record.getPlayerId2());
            playerIds.add(record.getPlayerId3());
            playerInfos = new ArrayList<>();
            for (String playerId : playerIds) {
                if (playerMap.containsKey(playerId))
                    pbd = playerMap.get(playerId);
                else {
                    pbd = this.playerMapper.getBaseInfo(playerId);
                    playerMap.put(playerId, pbd);
                }
                playerInfos.add(pbd);
            }
            tmp.setPlayers(playerInfos);
            scores = new ArrayList<>();
            scores.add(record.getScore0());
            scores.add(record.getScore1());
            scores.add(record.getScore2());
            scores.add(record.getScore3());
            tmp.setScores(scores);
            winGolds = new ArrayList<>();
            winGolds.add(record.getWinGold0());
            winGolds.add(record.getWinGold1());
            winGolds.add(record.getWinGold2());
            winGolds.add(record.getWinGold3());
            tmp.setWinGolds(winGolds);
            fillRecordRetention(tmp, record.getTime());
            if (record.getTime() != null)
                tmp.setTime(record.getTime().format(formatter));
            dtos.add(tmp);
        }
        result.setRecords(dtos);
        return result;
    }

    @Override
    public AjaxResult getMahjongPlayback(Long id) {
        if (id == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "游戏记录id不能为空");
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        MahjongRecord record = this.mahjongMapper.getRecord(id);
        if (record == null)
            throw new NotFoundException(NiuMaCodeEnum.MAHJONG_RECORD_NOT_EXIST);
        if (isRecordExpired(record.getTime())) {
            AjaxResult ajax = AjaxResult.successEx();
            ajax.put("hasReplay", false);
            ajax.put("retentionDays", gameRecordRetentionService.getRetentionDays());
            ajax.put("msg", "牌局记录已超过追溯期");
            return ajax;
        }
        LambdaQueryWrapper<GameMahjong> query = Wrappers.lambdaQuery();
        query.eq(GameMahjong::getVenueId, record.getVenueId());
        GameMahjong entity = this.mahjongMapper.selectOne(query);
        if (entity == null)
            throw new NotFoundException(NiuMaCodeEnum.MAHJONG_RECORD_NOT_EXIST);
        String playerId = player.getId();
        if (!(playerId.equals(record.getPlayerId0()) ||
              playerId.equals(record.getPlayerId1()) ||
              playerId.equals(record.getPlayerId2()) ||
              playerId.equals(record.getPlayerId3())))
            throw new ForbiddenException(ResultCodeEnum.FORBIDDEN.getCode(), "No permission to access the specified record");
        MahjongPlaybackDTO dto = new MahjongPlaybackDTO();
        dto.setVenueId(record.getVenueId());
        dto.setNumber(entity.getNumber());
        dto.setMode(entity.getMode());
        dto.setDiZhu(entity.getDiZhu());
        dto.setConfig(entity.getRule());
        dto.setRoundNo(record.getRoundNo());
        dto.setBanker(record.getBanker());
        List<String> playerIds = new ArrayList<>();
        playerIds.add(record.getPlayerId0());
        playerIds.add(record.getPlayerId1());
        playerIds.add(record.getPlayerId2());
        playerIds.add(record.getPlayerId3());
        List<PlayerBaseDTO> playerInfos = new ArrayList<>();
        for (String tmpId : playerIds) {
            PlayerBaseDTO pbd = this.playerMapper.getBaseInfo(tmpId);
            playerInfos.add(pbd);
        }
        dto.setPlayers(playerInfos);
        String playback = this.mahjongMapper.getPlayback(id);
        if (StringUtils.isEmpty(playback)) {
            dto.setHasReplay(false);
        } else {
            dto.setHasReplay(true);
        }
        dto.setRetentionDays(gameRecordRetentionService.getRetentionDays());
        dto.setExpireTime(formatRecordExpireTime(record.getTime()));
        dto.setFormat("msgpack");
        dto.setCodec("zlib+base64");
        dto.setBase64(playback);
        if (record.getTime() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
            dto.setTime(record.getTime().format(formatter));
        }
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("data", dto);
        return ajax;
    }

    @Override
    public AjaxResult getBiJiPublicRooms() {
        List<RoomItemDTO> items = this.biJiMapper.getPublicRooms();
        return this.getPublicRooms(items, 6);
    }

    @Override
    public PageResult<LackeyRoundDTO> getLackeyRecord(PageBody dto) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        PageResult<LackeyRoundDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        LocalDateTime cutoff = getRecordRetentionCutoff();
        Integer totalNum = this.lackeyMapper.countRound(player.getId(), cutoff);
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<Long> roundIds = this.lackeyMapper.getRoundIds(player.getId(), cutoff, offset, dto.getPageSize());
        if (roundIds == null || roundIds.isEmpty())
            return result;
        List<LackeyRoundDTO> records = new ArrayList<>(roundIds.size());
        for (Long roundId : roundIds) {
            LackeyRoundDTO item = this.lackeyMapper.getRoundInfo(roundId);
            if (item == null)
                continue;
            String time = item.getTime();
            if (StringUtils.isNotEmpty(time)) {
                int pos1 = time.indexOf('-');
                int pos2 = time.lastIndexOf(':');
                if (pos1 != -1 && pos2 != -1) {
                    time = time.substring(pos1 + 1, pos2);
                    item.setTime(time);
                }
            }
            List<LackeyRoundPlayerDTO> players = this.lackeyMapper.getRoundPlayers(roundId);
            item.setPlayers(players);
            records.add(item);
        }
        result.setRecords(records);
        return result;
    }

    @Override
    public AjaxResult getNiu100PublicRooms() {
        List<RoomItemDTO> items = this.niu100Mapper.getPublicRooms();
        return this.getPublicRooms(items, null);
    }

    private AjaxResult getPublicRooms(List<RoomItemDTO> items, Integer maxPlayerNums) {
        AjaxResult ajax = AjaxResult.successEx();
        if (items != null) {
            Map<String, PlayerBaseDTO> playerMap = new HashMap<>();
            for (RoomItemDTO item : items) {
                PlayerBaseDTO info = playerMap.get(item.getOwnerId());
                if (info == null) {
                    info = this.playerMapper.getBaseInfo(item.getOwnerId());
                    playerMap.put(item.getOwnerId(), info);
                }
                item.setOwnerName(info.getNickname());
                item.setOwnerHeadUrl(info.getHeadUrl());
                String redisKey = NiuMaRedisKeys.VENUE_PLAYER_COUNT + item.getVenueId();
                Integer playerCount = this.redisPrimitive.getInt(redisKey);
                if (playerCount == null)
                    playerCount = 0;
                item.setPlayerCount(playerCount);
                item.setMaxPlayerNums(maxPlayerNums);
            }
            // 按人数降序排序
            Collections.sort(items, new Comparator<RoomItemDTO>() {
                @Override
                public int compare(RoomItemDTO item1, RoomItemDTO item2) {
                    return Integer.compare(item2.getPlayerCount(), item1.getPlayerCount());
                }
            });
        }
        else
            items = new ArrayList<>();
        ajax.put("items", items);
        return ajax;
    }

    @Override
    public PageResult<GameRoomDTO> getMahjong(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_MAHJONG);
    }

    @Override
    public PageResult<GameRoomDTO> getBiJi(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_BI_JI);
    }

    @Override
    public PageResult<GameRoomDTO> getLackey(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_LACKEY);
    }

    @Override
    public PageResult<GameRoomDTO> getNiu100(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_NIU_NIU_100);
    }

    @Override
    public PageResult<GameRoomDTO> getDoudizhu(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_DOU_DI_ZHU);
    }

    @Override
    public PageResult<GameRoomDTO> getTaojiangMahjong(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG);
    }

    @Override
    public PageResult<GameRoomDTO> getHongzhongMahjong(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG);
    }

    @Override
    public PageResult<GameRoomDTO> getPaodekuai(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_PAO_DE_KUAI);
    }

    @Override
    public PageResult<GameRoomDTO> getChangshaMahjong(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG);
    }

    @Override
    public PageResult<GameRoomDTO> getYiyangWaihuzi(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI);
    }

    @Override
    public PageResult<GameRoomDTO> getYuanjiangQianfen(GameRoomReqDTO dto) {
        return this.getRooms(dto, NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN);
    }

    private PageResult<GameRoomDTO> getRooms(GameRoomReqDTO dto, Integer gameType) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        String venueId = CommonUtils.processKeyword(dto.getVenueId());
        String ownerId = CommonUtils.processKeyword(dto.getOwnerId());
        String number = CommonUtils.processKeyword(dto.getNumber());
        String startTime = null;
        String endTime = null;
        if (StringUtils.isNotEmpty(dto.getCreateTimeStart()))
            startTime = dto.getCreateTimeStart();
        if (StringUtils.isNotEmpty(dto.getCreateTimeEnd()))
            endTime = dto.getCreateTimeEnd();
        PageResult<GameRoomDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        Integer totalNum = 0;
        if (gameType.equals(NiuMaConstants.GAME_TYPE_MAHJONG))
            totalNum = this.mahjongMapper.countRoom(venueId, ownerId, number, startTime, endTime);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_BI_JI))
            totalNum = this.biJiMapper.countRoom(venueId, ownerId, number, startTime, endTime);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_LACKEY))
            totalNum = this.lackeyMapper.countRoom(venueId, ownerId, number, startTime, endTime);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_NIU_NIU_100))
            totalNum = this.niu100Mapper.countRoom(venueId, ownerId, number, startTime, endTime);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU))
            totalNum = this.doudizhuMapper.countRoom(venueId, ownerId, number, startTime, endTime);
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG))
            totalNum = this.taojiangMahjongMapper.countRoom(venueId, ownerId, number, startTime, endTime);
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<GameRoomDTO> records = null;
        if (gameType.equals(NiuMaConstants.GAME_TYPE_MAHJONG))
            records = this.mahjongMapper.getRooms(venueId, ownerId, number, startTime, endTime, offset, dto.getPageSize());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_BI_JI))
            records = this.biJiMapper.getRooms(venueId, ownerId, number, startTime, endTime, offset, dto.getPageSize());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_LACKEY))
            records = this.lackeyMapper.getRooms(venueId, ownerId, number, startTime, endTime, offset, dto.getPageSize());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_NIU_NIU_100))
            records = this.niu100Mapper.getRooms(venueId, ownerId, number, startTime, endTime, offset, dto.getPageSize());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU))
            records = this.doudizhuMapper.getRooms(venueId, ownerId, number, startTime, endTime, offset, dto.getPageSize());
        else if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG))
            records = this.taojiangMahjongMapper.getRooms(venueId, ownerId, number, startTime, endTime, offset, dto.getPageSize());
        if (records != null) {
            Map<String, String> ownerMap = new HashMap<>();
            for (GameRoomDTO item : records) {
                String ownerName = null;
                if (ownerMap.containsKey(item.getOwnerId()))
                    ownerName = ownerMap.get(item.getOwnerId());
                else {
                    ownerName = this.playerMapper.getNickname(item.getOwnerId());
                    if (StringUtils.isNotEmpty(ownerName))
                        ownerMap.put(item.getOwnerId(), ownerName);
                }
                item.setOwnerName(ownerName);
            }
        }
        result.setRecords(records);
        return result;
    }

    @Override
    public PageResult<GameRecordDTO> getTaojiangMahjongRecord(PageBody dto) {
        return getRegionalGameRecord(dto, NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG, "桃江麻将", 4,
                gameRegionalRecordMapper::countTaojiangMahjongRecord,
                gameRegionalRecordMapper::getTaojiangMahjongRecords,
                taojiangMahjongMapper::getNumber);
    }

    @Override
    public AjaxResult getTaojiangMahjongPlayback(Long id) {
        return getRegionalGamePlayback(id, NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG, "桃江麻将", 4,
                gameRegionalRecordMapper::getTaojiangMahjongRecord,
                gameRegionalRecordMapper::getTaojiangMahjongPlayback,
                taojiangMahjongMapper::getNumber);
    }

    @Override
    public PageResult<GameRecordDTO> getHongzhongMahjongRecord(PageBody dto) {
        return getRegionalGameRecord(dto, NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG, "红中麻将", 4,
                gameRegionalRecordMapper::countHongzhongMahjongRecord,
                gameRegionalRecordMapper::getHongzhongMahjongRecords,
                hongzhongMahjongMapper::getNumber);
    }

    @Override
    public AjaxResult getHongzhongMahjongPlayback(Long id) {
        return getRegionalGamePlayback(id, NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG, "红中麻将", 4,
                gameRegionalRecordMapper::getHongzhongMahjongRecord,
                gameRegionalRecordMapper::getHongzhongMahjongPlayback,
                hongzhongMahjongMapper::getNumber);
    }

    @Override
    public PageResult<GameRecordDTO> getPaodekuaiRecord(PageBody dto) {
        return getRegionalGameRecord(dto, NiuMaConstants.GAME_TYPE_PAO_DE_KUAI, "跑得快", 2,
                gameRegionalRecordMapper::countPaodekuaiRecord,
                gameRegionalRecordMapper::getPaodekuaiRecords,
                paodekuaiMapper::getNumber);
    }

    @Override
    public AjaxResult getPaodekuaiPlayback(Long id) {
        return getRegionalGamePlayback(id, NiuMaConstants.GAME_TYPE_PAO_DE_KUAI, "跑得快", 2,
                gameRegionalRecordMapper::getPaodekuaiRecord,
                gameRegionalRecordMapper::getPaodekuaiPlayback,
                paodekuaiMapper::getNumber);
    }

    @Override
    public PageResult<GameRecordDTO> getChangshaMahjongRecord(PageBody dto) {
        return getRegionalGameRecord(dto, NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG, "长沙麻将", 4,
                gameRegionalRecordMapper::countChangshaMahjongRecord,
                gameRegionalRecordMapper::getChangshaMahjongRecords,
                changshaMahjongMapper::getNumber);
    }

    @Override
    public AjaxResult getChangshaMahjongPlayback(Long id) {
        return getRegionalGamePlayback(id, NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG, "长沙麻将", 4,
                gameRegionalRecordMapper::getChangshaMahjongRecord,
                gameRegionalRecordMapper::getChangshaMahjongPlayback,
                changshaMahjongMapper::getNumber);
    }

    private PageResult<GameRecordDTO> getRegionalGameRecord(PageBody dto,
                                                            Integer gameType,
                                                            String gameName,
                                                            int playerCount,
                                                            RegionalRecordCounter counter,
                                                            RegionalRecordPager pager,
                                                            RegionalNumberGetter numberGetter) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        PageResult<GameRecordDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        LocalDateTime cutoff = getRecordRetentionCutoff();
        Integer totalNum = counter.count(player.getId(), cutoff);
        if (totalNum == null)
            totalNum = 0;
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<GameRegionalRecord> records = pager.get(player.getId(), cutoff, offset, dto.getPageSize());
        if ((records == null) || records.isEmpty())
            return result;
        List<GameRecordDTO> dtos = new ArrayList<>();
        Map<String, String> numberMap = new HashMap<>();
        Map<String, PlayerBaseDTO> playerMap = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
        for (GameRegionalRecord record : records) {
            GameRecordDTO tmp = buildRegionalRecordDTO(record, gameType, gameName, playerCount, numberGetter,
                    numberMap, playerMap);
            if (record.getTime() != null)
                tmp.setTime(record.getTime().format(formatter));
            dtos.add(tmp);
        }
        result.setRecords(dtos);
        return result;
    }

    private AjaxResult getRegionalGamePlayback(Long id,
                                               Integer gameType,
                                               String gameName,
                                               int playerCount,
                                               RegionalRecordGetter recordGetter,
                                               RegionalPlaybackGetter playbackGetter,
                                               RegionalNumberGetter numberGetter) {
        if (id == null)
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "游戏记录id不能为空");
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        GameRegionalRecord record = recordGetter.get(id);
        if (record == null)
            throw new NotFoundException(NiuMaCodeEnum.MAHJONG_RECORD_NOT_EXIST);
        if (!isRegionalRecordParticipant(player.getId(), record, playerCount))
            throw new ForbiddenException(ResultCodeEnum.FORBIDDEN.getCode(), "No permission to access the specified record");

        Map<String, String> numberMap = new HashMap<>();
        Map<String, PlayerBaseDTO> playerMap = new HashMap<>();
        GameRecordDTO recordDto = buildRegionalRecordDTO(record, gameType, gameName, playerCount, numberGetter,
                numberMap, playerMap);
        GameRecordPlaybackDTO playbackDto = new GameRecordPlaybackDTO();
        playbackDto.setGameType(recordDto.getGameType());
        playbackDto.setGameName(recordDto.getGameName());
        playbackDto.setVenueId(recordDto.getVenueId());
        playbackDto.setNumber(recordDto.getNumber());
        playbackDto.setRoundNo(recordDto.getRoundNo());
        playbackDto.setBanker(recordDto.getBanker());
        playbackDto.setPlayers(recordDto.getPlayers());
        playbackDto.setScores(recordDto.getScores());
        playbackDto.setWinGolds(recordDto.getWinGolds());
        playbackDto.setRetentionDays(gameRecordRetentionService.getRetentionDays());
        playbackDto.setExpireTime(recordDto.getExpireTime());
        playbackDto.setFormat("msgpack");
        playbackDto.setCodec("zlib+base64");
        if (record.getTime() != null)
            playbackDto.setTime(record.getTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));

        if (isRecordExpired(record.getTime())) {
            playbackDto.setHasReplay(false);
            AjaxResult ajax = AjaxResult.successEx();
            ajax.put("hasReplay", false);
            ajax.put("retentionDays", playbackDto.getRetentionDays());
            ajax.put("expireTime", playbackDto.getExpireTime());
            ajax.put("format", playbackDto.getFormat());
            ajax.put("codec", playbackDto.getCodec());
            ajax.put("msg", "牌局记录已超过追溯期");
            ajax.put("data", playbackDto);
            return ajax;
        }

        String playback = playbackGetter.get(id);
        playbackDto.setHasReplay(StringUtils.isNotEmpty(playback));
        playbackDto.setBase64(playback);
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("hasReplay", playbackDto.getHasReplay());
        ajax.put("retentionDays", playbackDto.getRetentionDays());
        ajax.put("expireTime", playbackDto.getExpireTime());
        ajax.put("format", playbackDto.getFormat());
        ajax.put("codec", playbackDto.getCodec());
        if (!playbackDto.getHasReplay())
            ajax.put("msg", "牌局回放数据不存在");
        ajax.put("data", playbackDto);
        return ajax;
    }

    private GameRecordDTO buildRegionalRecordDTO(GameRegionalRecord record,
                                                 Integer gameType,
                                                 String gameName,
                                                 int playerCount,
                                                 RegionalNumberGetter numberGetter,
                                                 Map<String, String> numberMap,
                                                 Map<String, PlayerBaseDTO> playerMap) {
        GameRecordDTO dto = new GameRecordDTO();
        dto.setId(record.getId());
        dto.setGameType(gameType);
        dto.setGameName(gameName);
        dto.setVenueId(record.getVenueId());
        String number = numberMap.get(record.getVenueId());
        if (!numberMap.containsKey(record.getVenueId())) {
            number = numberGetter.get(record.getVenueId());
            numberMap.put(record.getVenueId(), number);
        }
        dto.setNumber(number);
        dto.setRoundNo(record.getRoundNo());
        dto.setBanker(record.getBanker());
        dto.setPlayers(getRegionalRecordPlayers(record, playerCount, playerMap));
        dto.setScores(getRegionalRecordScores(record, playerCount));
        dto.setWinGolds(getRegionalRecordWinGolds(record, playerCount));
        fillRecordRetention(dto, record.getTime());
        return dto;
    }

    private List<PlayerBaseDTO> getRegionalRecordPlayers(GameRegionalRecord record,
                                                         int playerCount,
                                                         Map<String, PlayerBaseDTO> playerMap) {
        List<PlayerBaseDTO> players = new ArrayList<>();
        List<String> playerIds = getRegionalPlayerIds(record, playerCount);
        PlayerBaseDTO pbd;
        for (String playerId : playerIds) {
            if (StringUtils.isEmpty(playerId)) {
                players.add(null);
                continue;
            }
            if (playerMap.containsKey(playerId))
                pbd = playerMap.get(playerId);
            else {
                pbd = this.playerMapper.getBaseInfo(playerId);
                playerMap.put(playerId, pbd);
            }
            players.add(pbd);
        }
        return players;
    }

    private List<String> getRegionalPlayerIds(GameRegionalRecord record, int playerCount) {
        List<String> playerIds = new ArrayList<>();
        playerIds.add(record.getPlayerId0());
        playerIds.add(record.getPlayerId1());
        if (playerCount > 2)
            playerIds.add(record.getPlayerId2());
        if (playerCount > 3)
            playerIds.add(record.getPlayerId3());
        return playerIds;
    }

    private List<Integer> getRegionalRecordScores(GameRegionalRecord record, int playerCount) {
        List<Integer> scores = new ArrayList<>();
        scores.add(record.getScore0());
        scores.add(record.getScore1());
        if (playerCount > 2)
            scores.add(record.getScore2());
        if (playerCount > 3)
            scores.add(record.getScore3());
        return scores;
    }

    private List<Long> getRegionalRecordWinGolds(GameRegionalRecord record, int playerCount) {
        List<Long> winGolds = new ArrayList<>();
        winGolds.add(record.getWinGold0());
        winGolds.add(record.getWinGold1());
        if (playerCount > 2)
            winGolds.add(record.getWinGold2());
        if (playerCount > 3)
            winGolds.add(record.getWinGold3());
        return winGolds;
    }

    private boolean isRegionalRecordParticipant(String playerId, GameRegionalRecord record, int playerCount) {
        if (playerId == null)
            return false;
        for (String tmpId : getRegionalPlayerIds(record, playerCount)) {
            if (playerId.equals(tmpId))
                return true;
        }
        return false;
    }

    private LocalDateTime getRecordRetentionCutoff() {
        return LocalDateTime.now().minusDays(gameRecordRetentionService.getRetentionDays());
    }

    private boolean isRecordExpired(LocalDateTime recordTime) {
        return recordTime == null || recordTime.isBefore(getRecordRetentionCutoff());
    }

    private String formatRecordExpireTime(LocalDateTime recordTime) {
        if (recordTime == null)
            return null;
        return recordTime.plusDays(gameRecordRetentionService.getRetentionDays())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private void fillRecordRetention(MahjongRecordDTO dto, LocalDateTime recordTime) {
        dto.setHasReplay(!isRecordExpired(recordTime));
        dto.setExpireTime(formatRecordExpireTime(recordTime));
    }

    private void fillRecordRetention(TaojiangMahjongRecordDTO dto, LocalDateTime recordTime) {
        dto.setHasReplay(!isRecordExpired(recordTime));
        dto.setExpireTime(formatRecordExpireTime(recordTime));
    }

    private void fillRecordRetention(GameRecordDTO dto, LocalDateTime recordTime) {
        dto.setHasReplay(!isRecordExpired(recordTime));
        dto.setExpireTime(formatRecordExpireTime(recordTime));
    }
}
