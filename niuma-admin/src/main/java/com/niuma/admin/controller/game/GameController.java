package com.niuma.admin.controller.game;

import com.niuma.admin.dto.*;
import com.niuma.admin.service.IGameService;
import com.niuma.common.constant.ResultCodeEnum;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import javax.validation.Valid;
import java.math.BigDecimal;

/**
 * 游戏相关控制器
 * @author wujian
 * @email 393817707@qq.com
 * @date 2024.09.02
 */
@RestController
@RequestMapping("/player/game")
public class GameController {
    @Autowired
    private IGameService gameService;

    /**
     * 创建游戏
     */
    @PostMapping("/create")
    public DeferredResult<ResponseEntity<AjaxResult>> create(@RequestBody @Valid CreateGameDTO dto) {
        DeferredResult<ResponseEntity<AjaxResult> > result = new DeferredResult<>();
        try {
            this.gameService.createGame(result, dto);
        } catch (Exception ex) {
            ex.printStackTrace();
            AjaxResult ajax = new AjaxResult();
            ajax.put(AjaxResult.CODE_TAG, ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode());
            ajax.put(AjaxResult.MSG_TAG, ex.getMessage());
            result.setResult(new ResponseEntity<>(ajax, HttpStatus.INTERNAL_SERVER_ERROR));
        }
        return result;
    }

    /**
     * 创建空游戏
     */
    @PostMapping("/create/dumb")
    public DeferredResult<ResponseEntity<AjaxResult>> createDumb() {
        DeferredResult<ResponseEntity<AjaxResult> > result = new DeferredResult<>();
        try {
            this.gameService.createDumbGame(result);
        } catch (Exception ex) {
            ex.printStackTrace();
            AjaxResult ajax = new AjaxResult();
            ajax.put(AjaxResult.CODE_TAG, ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode());
            ajax.put(AjaxResult.MSG_TAG, ex.getMessage());
            result.setResult(new ResponseEntity<>(ajax, HttpStatus.INTERNAL_SERVER_ERROR));
        }
        return result;
    }

    /**
     * 进入游戏
     * @param dto 请求体
     */
    @PostMapping("/enter")
    public DeferredResult<ResponseEntity<AjaxResult>> enter(@RequestBody @Valid EnterDTO dto) {
        DeferredResult<ResponseEntity<AjaxResult> > result = new DeferredResult<>();
        try {
            this.gameService.enter(result, dto);
        } catch (Exception ex) {
            ex.printStackTrace();
            AjaxResult ajax = new AjaxResult();
            ajax.put(AjaxResult.CODE_TAG, ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode());
            ajax.put(AjaxResult.MSG_TAG, ex.getMessage());
            result.setResult(new ResponseEntity<>(ajax, HttpStatus.INTERNAL_SERVER_ERROR));
        }
        return result;
    }

    /**
     * 通过编号进入游戏
     * @param dto 请求体
     */
    @PostMapping("/enter/number")
    public DeferredResult<ResponseEntity<AjaxResult>> enterNumber(@RequestBody @Valid EnterNumberDTO dto) {
        DeferredResult<ResponseEntity<AjaxResult> > result = new DeferredResult<>();
        try {
            this.gameService.enterNumber(result, dto);
        } catch (Exception ex) {
            ex.printStackTrace();
            AjaxResult ajax = new AjaxResult();
            ajax.put(AjaxResult.CODE_TAG, ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode());
            ajax.put(AjaxResult.MSG_TAG, ex.getMessage());
            result.setResult(new ResponseEntity<>(ajax, HttpStatus.INTERNAL_SERVER_ERROR));
        }
        return result;
    }

    /**
     * 进入区域
     * @param districtId 区域id
     */
    @PostMapping("/enter/district")
    public DeferredResult<ResponseEntity<AjaxResult>> enterDistrict(@RequestParam("districtId") Integer districtId,
                                                                    @RequestParam(value = "carryScore", required = false) BigDecimal carryScore) {
        DeferredResult<ResponseEntity<AjaxResult> > result = new DeferredResult<>();
        try {
            this.gameService.enterDistrict(result, districtId, carryScore);
        } catch (Exception ex) {
            ex.printStackTrace();
            AjaxResult ajax = new AjaxResult();
            ajax.put(AjaxResult.CODE_TAG, ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode());
            ajax.put(AjaxResult.MSG_TAG, ex.getMessage());
            result.setResult(new ResponseEntity<>(ajax, HttpStatus.INTERNAL_SERVER_ERROR));
        }
        return result;
    }

    /**
     * 查询区域内当前玩家数量
     * @param districtId 区域id
     */
    @GetMapping("/district/player/count")
    public AjaxResult getDistrictPlayerCount(@RequestParam("districtId") Integer districtId) {
        return this.gameService.getDistrictPlayerCount(districtId);
    }

    /**
     * 查询区域内可加入的房间列表（选桌）
     * @param districtId 区域id
     */
    @GetMapping("/district/venues")
    public AjaxResult getDistrictVenues(@RequestParam("districtId") Integer districtId) {
        return this.gameService.getDistrictVenues(districtId);
    }

    /**
     * 查询麻将游戏记录
     * @param dto 请求体
     */
    @PostMapping("/mahjong/record")
    public PageResult<MahjongRecordDTO> getMahjongRecord(@RequestBody @Valid PageBody dto) {
        return this.gameService.getMahjongRecord(dto);
    }

    /**
     * 查询麻将游戏回放
     * @param id 游戏记录id
     */
    @GetMapping("/mahjong/playback")
    public AjaxResult getMahjongPlayback(@RequestParam("id") Long id) {
        return this.gameService.getMahjongPlayback(id);
    }

    /**
     * 查询比鸡游戏公开房列表
     */
    @GetMapping("/bi-ji/public")
    public AjaxResult getBiJiPublicRooms() {
        return this.gameService.getBiJiPublicRooms();
    }

    /**
     * 查询逮狗腿游戏记录
     * @param dto 请求体
     */
    @PostMapping("/lackey/record")
    public PageResult<LackeyRoundDTO> getLackeyRecord(@RequestBody @Valid PageBody dto) {
        return this.gameService.getLackeyRecord(dto);
    }

    /**
     * 查询比鸡游戏公开房列表
     */
    @GetMapping("/niu100/public")
    public AjaxResult getNiu100PublicRooms() {
        return this.gameService.getNiu100PublicRooms();
    }

    /**
     * 查询桃江麻将游戏记录
     * @param dto 请求体
     */
    @PostMapping("/taojiang-mahjong/record")
    public PageResult<GameRecordDTO> getTaojiangMahjongRecord(@RequestBody @Valid PageBody dto) {
        return this.gameService.getTaojiangMahjongRecord(dto);
    }

    /**
     * 查询桃江麻将游戏回放
     * @param id 游戏记录id
     */
    @GetMapping("/taojiang-mahjong/playback")
    public AjaxResult getTaojiangMahjongPlayback(@RequestParam("id") Long id) {
        return this.gameService.getTaojiangMahjongPlayback(id);
    }

    /**
     * 查询红中麻将游戏记录
     * @param dto 请求体
     */
    @PostMapping("/hongzhong-mahjong/record")
    public PageResult<GameRecordDTO> getHongzhongMahjongRecord(@RequestBody @Valid PageBody dto) {
        return this.gameService.getHongzhongMahjongRecord(dto);
    }

    /**
     * 查询红中麻将游戏回放
     * @param id 游戏记录id
     */
    @GetMapping("/hongzhong-mahjong/playback")
    public AjaxResult getHongzhongMahjongPlayback(@RequestParam("id") Long id) {
        return this.gameService.getHongzhongMahjongPlayback(id);
    }

    /**
     * 查询跑得快游戏记录
     * @param dto 请求体
     */
    @PostMapping("/paodekuai/record")
    public PageResult<GameRecordDTO> getPaodekuaiRecord(@RequestBody @Valid PageBody dto) {
        return this.gameService.getPaodekuaiRecord(dto);
    }

    /**
     * 查询跑得快游戏回放
     * @param id 游戏记录id
     */
    @GetMapping("/paodekuai/playback")
    public AjaxResult getPaodekuaiPlayback(@RequestParam("id") Long id) {
        return this.gameService.getPaodekuaiPlayback(id);
    }

    /**
     * 查询长沙麻将游戏记录
     * @param dto 请求体
     */
    @PostMapping("/changsha-mahjong/record")
    public PageResult<GameRecordDTO> getChangshaMahjongRecord(@RequestBody @Valid PageBody dto) {
        return this.gameService.getChangshaMahjongRecord(dto);
    }

    /**
     * 查询长沙麻将游戏回放
     * @param id 游戏记录id
     */
    @GetMapping("/changsha-mahjong/playback")
    public AjaxResult getChangshaMahjongPlayback(@RequestParam("id") Long id) {
        return this.gameService.getChangshaMahjongPlayback(id);
    }
}
