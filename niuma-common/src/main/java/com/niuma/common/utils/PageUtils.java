package com.niuma.common.utils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.niuma.common.core.page.PageDomain;

import com.niuma.common.core.page.TableDataInfo;
import com.niuma.common.core.page.TableSupport;
import com.niuma.common.utils.sql.SqlUtil;

import java.util.List;

/**
 * 分页工具类
 * 
 * @author ruoyi
 */
public class PageUtils extends PageHelper
{
    /**
     * 设置请求分页数据
     */
    public static void startPage()
    {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();
        String orderBy = SqlUtil.escapeOrderBySql(pageDomain.getOrderBy());
        Boolean reasonable = pageDomain.getReasonable();
        PageHelper.startPage(pageNum, pageSize, orderBy).setReasonable(reasonable);
    }

    /**
     * 清理分页的线程变量
     */
    public static void clearPage()
    {
        PageHelper.clearPage();
    }

    /**
     * 响应请求分页数据（Service层可用）
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static TableDataInfo getDataTable(List<?> list)
    {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(200);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());
        return rspData;
    }
}
