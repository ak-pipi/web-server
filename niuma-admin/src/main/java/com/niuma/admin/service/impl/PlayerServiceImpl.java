package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.constant.NiuMaCodeEnum;
import com.niuma.admin.constant.NiuMaConstants;
import com.niuma.admin.constant.NiuMaRedisKeys;
import com.niuma.admin.dto.*;
import com.niuma.admin.entity.Agency;
import com.niuma.admin.entity.Capital;
import com.niuma.admin.entity.Player;
import com.niuma.admin.entity.Venue;
import com.niuma.admin.entity.Robot;
import com.niuma.admin.factory.PlayerAsyncFactory;
import com.niuma.admin.mapper.*;
import com.niuma.admin.service.ICapitalService;
import com.niuma.admin.service.IAgencyManageService;
import com.niuma.admin.service.IPlayerService;
import com.niuma.admin.utils.WebSocketAddressUtils;
import com.niuma.common.constant.CacheConstants;
import com.niuma.common.constant.Constants;
import com.niuma.common.constant.ResultCodeEnum;
import com.niuma.common.constant.UserConstants;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.domain.entity.SysUser;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.core.redis.RedisCache;
import com.niuma.common.core.redis.RedisPrimitive;
import com.niuma.common.page.PageResult;
import com.niuma.common.exception.http.*;
import com.niuma.common.exception.user.*;
import com.niuma.common.utils.AesUtil;
import com.niuma.common.utils.CommonUtils;
import com.niuma.common.utils.PlayerSecurityUtils;
import com.niuma.common.utils.StringUtils;
import com.niuma.common.utils.ip.IpUtils;
import com.niuma.framework.manager.AsyncManager;
import com.niuma.framework.web.service.PlayerTokenService;
import com.niuma.system.mapper.SysUserMapper;
import com.niuma.system.service.ISysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class PlayerServiceImpl extends ServiceImpl<PlayerMapper, Player> implements IPlayerService {
    private static final String SUPER_ADMIN_PLAYER_ID = "888888";
    private static final int PLAYER_ID_MIN = 100000;
    private static final int PLAYER_ID_MAX = 999999;
    private static final int MONEY_SCALE = 1;

    @Autowired
    private PlayerTokenService playerTokenService;

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
    private ISysConfigService configService;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private PlayerAsyncFactory playerAsyncFactory;

    @Autowired
    private ICapitalService capitalService;

    @Autowired
    private IAgencyManageService agencyManageService;

    @Autowired
    private AgencyMapper agencyMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Resource
    private VenueMapper venueMapper;

    @Resource
    private RobotMapper robotMapper;

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

    @Override
    public AjaxResult login(PlayerLoginDTO dto) {
        String name = AesUtil.decrypt(dto.getName());
        String password = AesUtil.decrypt(dto.getPassword());
        if (StringUtils.isEmpty(name) || StringUtils.isEmpty(password))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "账号或密码错误");
        this.validateCaptcha(dto.getCode(), dto.getUuid());

        Player entity = resolveLoginPlayer(name, password);
        ensurePlayerNotActive(entity.getId());
        if (CommonUtils.predicate(entity.getBanned())) {
            throw new ForbiddenException("玩家账号已被封禁");
        }
        if ((entity == null) || CommonUtils.predicate(entity.getDelFlag()))
            throw new NotFoundException(NiuMaCodeEnum.PLAYER_NOT_EXIST);

        // 生成token
        LoginPlayer player = new LoginPlayer();
        player.setId(entity.getId());
        player.setName(entity.getName());
        player.setNickName(entity.getNickname());
        player.setUuid(dto.getUuid());
        this.playerTokenService.setLoginPlayer(player);
        String token = Constants.TOKEN_PREFIX + this.playerTokenService.createToken(player);
        this.baseMapper.updateLogin(entity.getId(), IpUtils.getIpAddr());
        AsyncManager.me().execute(this.playerAsyncFactory.recordPlayerLogin(entity.getId(), entity.getNickname()));
        // 生成消息密钥
        String secret = CommonUtils.generatePassword(6);
        String secretKey = NiuMaRedisKeys.PLAYER_MESSAGE_SECRET + player.getId();
        this.redisPrimitive.set(secretKey, secret);
        // 设置响应字段
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("secret", secret);
        ajax.put("token", token);
        return ajax;
    }

    private Player resolveLoginPlayer(String name, String password) {
        Player player = findPlayerByName(name);
        SysUser sysUser = this.sysUserMapper.selectUserByUserName(name);
        boolean superAdminAccount = isEnabledSuperAdmin(sysUser);

        if (player == null) {
            if (!superAdminAccount) {
                throw new NotFoundException(NiuMaCodeEnum.PLAYER_NOT_EXIST);
            }
            if (!passwordMatches(password, sysUser.getPassword())) {
                throw new UnauthorizedException(NiuMaCodeEnum.PLAYER_BAD_CREDENTIALS);
            }
            return ensureSuperAdminPlayer(sysUser);
        }

        if (CommonUtils.predicate(player.getDelFlag())) {
            throw new NotFoundException(NiuMaCodeEnum.PLAYER_NOT_EXIST);
        }
        if (passwordMatches(password, player.getPassword())) {
            if (superAdminAccount) {
                ensureSuperAdminPlayerState(player, sysUser);
            }
            return player;
        }
        if (superAdminAccount && passwordMatches(password, sysUser.getPassword())) {
            return ensureSuperAdminPlayer(sysUser);
        }
        throw new UnauthorizedException(NiuMaCodeEnum.PLAYER_BAD_CREDENTIALS);
    }

    private Player findPlayerByName(String name) {
        LambdaQueryWrapper<Player> query = Wrappers.lambdaQuery();
        query.eq(Player::getName, name).last("LIMIT 1");
        return this.getOne(query);
    }

    private boolean passwordMatches(String rawPassword, String encodedPassword) {
        return StringUtils.isNotEmpty(encodedPassword) && this.bCryptPasswordEncoder.matches(rawPassword, encodedPassword);
    }

    private boolean isEnabledSuperAdmin(SysUser user) {
        return user != null
                && SysUser.isAdmin(user.getUserId())
                && (StringUtils.isEmpty(user.getStatus()) || UserConstants.NORMAL.equals(user.getStatus()))
                && !"2".equals(user.getDelFlag());
    }

    private Player ensureSuperAdminPlayer(SysUser sysUser) {
        Player player = findPlayerByName(sysUser.getUserName());
        if (player == null || CommonUtils.predicate(player.getDelFlag())) {
            Player fixed = this.baseMapper.selectById(SUPER_ADMIN_PLAYER_ID);
            if (fixed != null && !CommonUtils.predicate(fixed.getDelFlag())) {
                player = fixed;
            }
        }
        if (player == null || CommonUtils.predicate(player.getDelFlag())) {
            player = new Player();
            player.setId(SUPER_ADMIN_PLAYER_ID);
            player.setName(sysUser.getUserName());
            player.setPassword(sysUser.getPassword());
            player.setNickname(StringUtils.isNotEmpty(sysUser.getNickName()) ? sysUser.getNickName() : "超级管理员");
            player.setSex(parseSysUserSex(sysUser.getSex()));
            player.setAvatar(sysUser.getAvatar());
            this.baseMapper.addPlayer(player);
        }
        ensureSuperAdminPlayerState(player, sysUser);
        return player;
    }

    private void ensureSuperAdminPlayerState(Player player, SysUser sysUser) {
        if (player == null || StringUtils.isEmpty(player.getId())) {
            return;
        }
        if (StringUtils.isEmpty(player.getName()) && sysUser != null && StringUtils.isNotEmpty(sysUser.getUserName())) {
            player.setName(sysUser.getUserName());
        }
        if (StringUtils.isEmpty(player.getNickname()) && sysUser != null && StringUtils.isNotEmpty(sysUser.getNickName())) {
            player.setNickname(sysUser.getNickName());
        }
        if (StringUtils.isEmpty(player.getPassword()) && sysUser != null && StringUtils.isNotEmpty(sysUser.getPassword())) {
            player.setPassword(sysUser.getPassword());
        }
        player.setBanned(0);
        player.setDelFlag(0);
        this.updateById(player);
        ensureCapital(player.getId());
        ensureSuperAdminAgency(player.getId(), sysUser != null ? sysUser.getUserId() : 1L);
    }

    private int parseSysUserSex(String sex) {
        if ("0".equals(sex)) {
            return 1;
        }
        if ("1".equals(sex)) {
            return 2;
        }
        return 0;
    }

    private void ensureCapital(String playerId) {
        if (this.capitalService.getById(playerId) != null) {
            return;
        }
        Capital capital = new Capital();
        capital.setPlayerId(playerId);
        capital.setGold(money(0L));
        capital.setDeposit(money(0L));
        capital.setDiamond(0L);
        capital.setVersion(1L);
        this.capitalService.save(capital);
    }

    private void ensureSuperAdminAgency(String playerId, Long userId) {
        Agency agency = this.agencyMapper.selectOne(Wrappers.lambdaQuery(Agency.class)
                .eq(Agency::getPlayerId, playerId)
                .last("LIMIT 1"));
        if (agency == null) {
            agency = new Agency();
            agency.setPlayerId(playerId);
            agency.setSuperiorId(Agency.ROOT_PLAYER_ID);
            agency.setLevel(1);
            agency.setAgentType(Agency.TYPE_LEVEL_ONE);
            agency.setDepth(1);
            agency.setPath("/" + Agency.ROOT_PLAYER_ID + "/" + playerId + "/");
            agency.setCommissionRateBp(10000);
            agency.setJuniorCount(0);
            agency.setTotalReward(0L);
            agency.setStatus(Agency.STATUS_NORMAL);
            agency.setCreatedByUserId(userId);
            agency.setCreatedByPlayerId(Agency.ROOT_PLAYER_ID);
            this.agencyMapper.insert(agency);
        } else {
            agency.setSuperiorId(Agency.ROOT_PLAYER_ID);
            agency.setLevel(1);
            agency.setAgentType(Agency.TYPE_LEVEL_ONE);
            agency.setDepth(1);
            agency.setPath("/" + Agency.ROOT_PLAYER_ID + "/" + playerId + "/");
            agency.setCommissionRateBp(10000);
            agency.setStatus(Agency.STATUS_NORMAL);
            this.agencyMapper.updateById(agency);
        }
        this.agencyManageService.ensureDefaultInviteCode(playerId, "super-admin-login");
    }

    private void ensurePlayerNotActive(String playerId) {
        String redisKey = CacheConstants.PLAYER_ACTIVE_KEY + playerId;
        Long activeTime = this.redisCache.getCacheObject(redisKey);
        if (activeTime == null) {
            return;
        }
        Long timestamp = System.currentTimeMillis();
        timestamp /= 1000L;
        Long delta = timestamp - activeTime;
        if (delta < 30L) {
            throw new ForbiddenException(NiuMaCodeEnum.PLAYER_LOGINED);
        }
    }

    /**
     * 校验验证码
     *
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    private void validateCaptcha(String code, String uuid) {
        boolean captchaEnabled = this.configService.selectCaptchaEnabled();
        if (captchaEnabled) {
            String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
            String captcha = this.redisCache.getCacheObject(verifyKey);
            if (captcha == null)
                throw new CaptchaExpireException();
            this.redisCache.deleteObject(verifyKey);
            if (!code.equalsIgnoreCase(captcha))
                throw new CaptchaException();
        }
    }

    @Override
    public AjaxResult logout() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        this.playerTokenService.delLoginPlayer(player.getId());
        String redisKey = NiuMaRedisKeys.PLAYER_MESSAGE_SECRET + player.getId();
        this.redisPrimitive.delete(redisKey);
        redisKey = CacheConstants.PLAYER_ACTIVE_KEY + player.getId();
        this.redisCache.deleteObject(redisKey);
        return AjaxResult.successEx();
    }

    private static class PlayerTester implements CommonUtils.DuplicateTester {
        private PlayerMapper mapper;

        public PlayerTester(PlayerMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public boolean testDuplicate(String code) {
            LambdaQueryWrapper<Player> query = Wrappers.lambdaQuery();
            query.eq(Player::getId, code);
            Integer count = this.mapper.selectCount(query);
            return CommonUtils.predicate(count);
        }
    }

    private String generatePlayerId() {
        Random rand = new Random();
        for (int i = 0; i < 256; i++) {
            String playerId = String.valueOf(PLAYER_ID_MIN + rand.nextInt(PLAYER_ID_MAX - PLAYER_ID_MIN + 1));
            if (!SUPER_ADMIN_PLAYER_ID.equals(playerId) && this.baseMapper.selectById(playerId) == null) {
                return playerId;
            }
        }
        throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), "Generate player id failed");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult register(RegisterDTO dto) {
        String name = AesUtil.decrypt(dto.getName());
        String password = AesUtil.decrypt(dto.getPassword());
        if (StringUtils.isEmpty(name) || StringUtils.isEmpty(password))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "账号或密码错误");
        if (dto.getSex() == null)
            dto.setSex(0);
        String id = this.baseMapper.getIdByName(name);
        if (StringUtils.isNotEmpty(id))
            throw new NotFoundException(NiuMaCodeEnum.PLAYER_REGISTER_ERROR.getCode(), "账号已存在");
        log.info("Player register, name: {}, password: {}, nickname: {}", name, password, dto.getNickname());
        password = this.bCryptPasswordEncoder.encode(password);
        Player entity = new Player();
        entity.setId(generatePlayerId());
        entity.setName(name);
        entity.setPassword(password);
        entity.setNickname(dto.getNickname());
        entity.setSex(dto.getSex());
        // 随机分配一个头像
        Integer count = this.baseMapper.countHeadImageUrls();
        Random rand = new Random();
        Integer imgId = rand.nextInt(count);
        imgId += 1;
        String url = this.baseMapper.getHeadImageUrl(imgId);
        entity.setAvatar(url);
        this.baseMapper.addPlayer(entity);
        // 添加资产
        Capital capital = new Capital();
        capital.setPlayerId(entity.getId());
        capital.setGold(money(0L));
        capital.setDeposit(money(0L));
        capital.setDiamond(0L);
        capital.setVersion(1L);
        this.capitalService.save(capital);
        return AjaxResult.successEx();
    }

    @Override
    public AjaxResult refreshMessageSecret() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        String redisKey = NiuMaRedisKeys.PLAYER_MESSAGE_SECRET + player.getId();
        String secret = this.redisPrimitive.get(redisKey);
        if (StringUtils.isEmpty(secret)) {
            secret = CommonUtils.generatePassword(6);
            this.redisPrimitive.set(redisKey, secret);
        }
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("secret", secret);
        return ajax;
    }

    @Override
    public AjaxResult heartbeat() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        Long nowTime = System.currentTimeMillis();
        nowTime /= 1000L;
        this.baseMapper.updateHeartbeat(player.getId(), nowTime);

        AjaxResult ajax = AjaxResult.successEx();
        Capital capital = this.capitalService.getCapital(player.getId());
        ajax.put("gold", capital.getGold());
        ajax.put("deposit", capital.getDeposit());
        ajax.put("diamond", capital.getDiamond());
        Player entity = this.baseMapper.selectById(player.getId());
        if (entity != null) {
            putPlayerPermissionFields(ajax, entity, player.getId());
        }
        String venueId = this.redisPrimitive.get(NiuMaRedisKeys.PLAYER_CURRENT_VENUE + player.getId());
        if (StringUtils.isEmpty(venueId)) {
            ajax.put("inRoom", false);
            return ajax;
        }

        ajax.put("inRoom", true);
        ajax.put("venueId", venueId);

        Venue venue = this.venueMapper.selectById(venueId);
        if (venue != null && venue.getGameType() != null) {
            ajax.put("gameType", venue.getGameType());
        }

        String serverId = this.redisPrimitive.get(NiuMaRedisKeys.VENUE_SERVER_MAP + venueId);
        if (StringUtils.isNotEmpty(serverId)) {
            ajax.put("serverId", serverId);
            String wsAddress = this.redisPrimitive.get(NiuMaRedisKeys.SERVER_WS_ADDRESS + serverId);
            if (StringUtils.isNotEmpty(wsAddress)) {
                ajax.put("wsAddress", WebSocketAddressUtils.ensureSecure(wsAddress));
            }
        }

        return ajax;
    }

    @Override
    public AjaxResult getInfo() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        Player entity = this.baseMapper.selectById(player.getId());
        if (entity == null)
            throw new InternalServerException("Current player entity is null, this is unexpected");
        Capital capital = this.capitalService.getCapital(player.getId());
        // 读取或生成消息密钥（登录时不轮换，避免与游戏服 Redis 不同步）
        String redisKey = NiuMaRedisKeys.PLAYER_MESSAGE_SECRET + player.getId();
        String secret = this.redisPrimitive.get(redisKey);
        if (StringUtils.isEmpty(secret)) {
            secret = CommonUtils.generatePassword(6);
            this.redisPrimitive.set(redisKey, secret);
        }
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("secret", secret);
        ajax.put("playerId", player.getId());
        ajax.put("name", entity.getName());
        ajax.put("nickname", entity.getNickname());
        ajax.put("phone", entity.getPhone());
        ajax.put("sex", entity.getSex());
        ajax.put("avatar", entity.getAvatar());
        ajax.put("agencyId", entity.getAgencyId());
        ajax.put("gold", capital.getGold());
        ajax.put("deposit", capital.getDeposit());
        ajax.put("diamond", capital.getDiamond());
        putPlayerPermissionFields(ajax, entity, player.getId());
        return ajax;
    }

    private void putPlayerPermissionFields(AjaxResult ajax, Player entity, String playerId) {
        Integer isAgency =  this.agencyMapper.isAgency(playerId);
        boolean agencyFlag = CommonUtils.predicate(isAgency);
        boolean superAdmin = isSuperAdminPlayer(entity);
        boolean bound = StringUtils.isNotEmpty(entity.getAgencyId());
        ajax.put("isAgency", isAgency);
        ajax.put("isSuperAdmin", superAdmin ? 1 : 0);
        ajax.put("isBound", bound ? 1 : 0);
        ajax.put("canCreateRoom", (superAdmin || agencyFlag || bound) ? 1 : 0);
        ajax.put("canAgencyManage", (superAdmin || agencyFlag) ? 1 : 0);
    }

    private boolean isSuperAdminPlayer(Player player) {
        if (player == null || StringUtils.isEmpty(player.getName())) {
            return false;
        }
        SysUser sysUser = this.sysUserMapper.selectUserByUserName(player.getName());
        return isEnabledSuperAdmin(sysUser);
    }

    @Override
    public AjaxResult getPersonalData() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        Player entity = this.baseMapper.selectById(player.getId());
        if (entity == null)
            throw new InternalServerException("Current player entity is null, this is unexpected");
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("loginIp", entity.getLoginIp());
        ajax.put("loginDate", entity.getLoginDate());
        if (StringUtils.isNotEmpty(entity.getAgencyId())) {
            ajax.put("agencyId", entity.getAgencyId());
            String nickname = this.baseMapper.getNickname(entity.getAgencyId());
            ajax.put("agencyName", nickname);
        }
        return ajax;
    }

    @Override
    public AjaxResult location(LocationDTO dto) {
        return AjaxResult.successEx();
    }

    @Override
    public PageResult<PlayerDTO> getPlayer(PlayerReqDTO dto) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        String playerId = CommonUtils.processKeyword(dto.getPlayerId());
        String nickname = CommonUtils.processKeyword(dto.getNickname());
        Integer online = dto.getOnline();
        Long heartbeat = System.currentTimeMillis();
        heartbeat /= 1000L;
        // 30秒之前为界限
        heartbeat -= 30L;
        if (online != null) {
            if (CommonUtils.predicate(online))
                online = 1;
            else
                online = 0;
        }
        PageResult<PlayerDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        Integer totalNum = this.baseMapper.countPlayer(playerId, nickname, online, heartbeat);
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<PlayerDTO> records = this.baseMapper.getPage(playerId, nickname, online, heartbeat, offset, dto.getPageSize());
        if (records != null) {
            String redisKey = null;
            Integer gameType = null;
            for (PlayerDTO item : records) {
                redisKey = NiuMaRedisKeys.PLAYER_CURRENT_VENUE + item.getPlayerId();
                String venueId = this.redisPrimitive.get(redisKey);
                if (StringUtils.isNotEmpty(venueId))
                    gameType = this.venueMapper.getGameType(venueId);
                else
                    gameType = null;
                if (gameType != null) {
                    String gameRoom = resolveGameRoom(gameType, venueId);
                    if (StringUtils.isNotEmpty(gameRoom)) {
                        item.setGameRoom(gameRoom);
                    }
                }
                if (online == null) {
                    if ((item.getHeartbeat() == null) || (item.getHeartbeat() < heartbeat))
                        item.setOnline(0);
                    else
                        item.setOnline(1);
                } else {
                    item.setOnline(online);
                }
                if (StringUtils.isNotEmpty(item.getAgencyId())) {
                    nickname = this.baseMapper.getNickname(item.getAgencyId());
                    if (nickname != null) {
                        nickname = nickname + "(" + item.getAgencyId() + ")";
                        item.setAgency(nickname);
                    }
                }
            }
        }
        result.setRecords(records);
        return result;
    }

    private String resolveGameRoom(Integer gameType, String venueId) {
        if (gameType == null || StringUtils.isEmpty(venueId))
            return null;
        String name = null;
        String number = null;
        if (gameType.equals(NiuMaConstants.GAME_TYPE_DOU_DI_ZHU)) {
            name = "斗地主";
            number = this.doudizhuMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_GUAN_DAN)) {
            name = "掼蛋";
            number = this.guanDanMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_TAOJIANG_MAHJONG)) {
            name = "桃江麻将";
            number = this.taojiangMahjongMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_HONGZHONG_MAHJONG)) {
            name = "红中麻将";
            number = this.hongzhongMahjongMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_PAO_DE_KUAI)) {
            name = "跑得快";
            number = this.paodekuaiMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_CHANGSHA_MAHJONG)) {
            name = "长沙麻将";
            number = this.changshaMahjongMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_YIYANG_WAI_HU_ZI)) {
            name = "益阳歪胡子";
            number = this.yiyangWaihuziMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_YUANJIANG_QIAN_FEN)) {
            name = "沅江千分";
            number = this.yuanjiangQianfenMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_MAHJONG)) {
            name = "标准麻将";
            number = this.mahjongMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_BI_JI)) {
            name = "六安比鸡";
            number = this.biJiMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_LACKEY)) {
            name = "逮狗腿";
            number = this.lackeyMapper.getNumber(venueId);
        } else if (gameType.equals(NiuMaConstants.GAME_TYPE_NIU_NIU_100)) {
            name = "百人牛牛";
            number = this.niu100Mapper.getNumber(venueId);
        }
        if (StringUtils.isEmpty(name))
            return venueId;
        return StringUtils.isNotEmpty(number) ? name + "(" + number + ")" : name + "(" + venueId + ")";
    }

    @Override
    public void banPlayer(String playerId) {
        Player entity = this.baseMapper.selectById(playerId);
        if (entity == null)
            throw new NotFoundException(NiuMaCodeEnum.PLAYER_NOT_EXIST.getCode(), "指定玩家不存在");
        if (CommonUtils.predicate(entity.getBanned()))
            this.baseMapper.setBanned(playerId, 0);
        else {
            this.baseMapper.setBanned(playerId, 1);
            this.playerTokenService.delLoginPlayer(playerId);
            String redisKey = NiuMaRedisKeys.PLAYER_MESSAGE_SECRET + playerId;
            this.redisPrimitive.delete(redisKey);
            redisKey = CacheConstants.PLAYER_ACTIVE_KEY + playerId;
            this.redisCache.deleteObject(redisKey);
        }
    }

    @Override
    public AjaxResult createRobots(String text) {
        if (StringUtils.isEmpty(text))
            throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "请输入昵称，多个昵称用英文逗号分隔");
        Random rand = new Random();
        int number = 0;
        Integer count = this.baseMapper.countHeadImageUrls();
        Integer imgId = null;
        StringBuilder sb = new StringBuilder();
        String[] nicknames = text.split(",");
        Player entity = null;
        Capital capital = null;
        for (int i = 0; i < nicknames.length; i++) {
            String nickname = nicknames[i];
            if (StringUtils.isEmpty(nickname))
                continue;
            if (nickname.length() > 20)
                continue;
            entity = new Player();
            entity.setId(generatePlayerId());
            try {
                Robot tmp = new Robot();
                tmp.setPlayerId(entity.getId());
                this.robotMapper.insert(tmp);
                entity.setName(String.format("Arobot_%04d", tmp.getId()));
            } catch (Exception ex) {
                continue;
            }
            entity.setPassword(this.bCryptPasswordEncoder.encode(CommonUtils.generatePassword(8)));
            entity.setNickname(nickname);
            // 创建随机电话号码
            number = rand.nextInt(70);
            number += 130;
            sb.setLength(0);
            sb.append(number);
            for (int j = 0; j < 8; j++) {
                number = rand.nextInt(10);
                sb.append(number);
            }
            entity.setPhone(sb.toString());
            entity.setSex(rand.nextInt(2) + 1);
            imgId = rand.nextInt(count);
            entity.setAvatar(this.baseMapper.getHeadImageUrl(imgId));
            this.save(entity);
            // 添加资产
            capital = new Capital();
            capital.setPlayerId(entity.getId());
            capital.setGold(money(0L));
            capital.setDeposit(money(0L));
            capital.setDiamond(0L);
            capital.setVersion(1L);
            this.capitalService.save(capital);
        }
        return AjaxResult.successEx();
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
