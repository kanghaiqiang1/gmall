package com.atguigu.gmall.list.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.service.ListService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Controller
public class ListController {

    @Reference
    private ListService listService;
    @Reference
    private ManageService manageService;

    @RequestMapping("list.html")
    public String getList(SkuLsParams skuLsParams, HttpServletRequest request) {
        //分页    设置每页显示条数
        skuLsParams.setPageSize(2);

        SkuLsResult skuLsResult = listService.search(skuLsParams);
        // 获取sku属性值列表
        List<SkuLsInfo> skuLsInfoList = skuLsResult.getSkuLsInfoList();
        // 从结果中取出平台属性值列表
        List<String> attrValueIdList = skuLsResult.getAttrValueIdList();
        List<BaseAttrInfo> baseAttrInfoList = manageService.getBaseAttrInfoList(attrValueIdList);

        // 已选的属性值列表
        String urlParam = makeUrlParam(skuLsParams);
        ArrayList<BaseAttrValue> baseAttrValueList = new ArrayList<>();

        //itco  点击平台属性值后对应的平台属性消失
        for (Iterator<BaseAttrInfo> iterator = baseAttrInfoList.iterator(); iterator.hasNext(); ) {
            BaseAttrInfo baseAttrInfo = iterator.next();
            List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
            for (BaseAttrValue baseAttrValue : attrValueList) {
                //如果查询条件中存在平台属性值id，判断平台属性值id与数据库中平台属性值id是否一致
                if (skuLsParams.getValueId() != null && skuLsParams.getValueId().length > 0) {
                    for (String valueId : skuLsParams.getValueId()) {
                        if (valueId.equals(baseAttrValue.getId())) {
                            iterator.remove();
                            // 构造面包屑列表
                            BaseAttrValue baseAttrValueSelected = new BaseAttrValue();
                            baseAttrValueSelected.setValueName(baseAttrInfo.getAttrName() + ":" + baseAttrValue.getValueName());
                            String param = makeUrlParam(skuLsParams, valueId);
                            baseAttrValueSelected.setUrlParam(param);
                            baseAttrValueList.add(baseAttrValueSelected);
                        }
                    }
                }
            }
        }

        request.setAttribute("totalPages", skuLsResult.getTotalPages());
        request.setAttribute("pageNo", skuLsParams.getPageNo());
        request.setAttribute("baseAttrValueList", baseAttrValueList);
        request.setAttribute("keyword", skuLsParams.getKeyword());
        request.setAttribute("urlParam", urlParam);
        request.setAttribute("skuLsInfoList", skuLsInfoList);
        request.setAttribute("baseAttrInfoList", baseAttrInfoList);

        return "list";
    }

    private String makeUrlParam(SkuLsParams skuLsParams, String... excludeValueIds) {
        String urlParam = "";
        if (skuLsParams.getCatalog3Id() != null && skuLsParams.getCatalog3Id().length() > 0) {
            urlParam += "catalog3Id=" + skuLsParams.getCatalog3Id();
        }
        if (skuLsParams.getKeyword() != null && skuLsParams.getKeyword().length() > 0) {
            urlParam += "keyword=" + skuLsParams.getKeyword();
        }
        // 构造属性参数
        if (skuLsParams.getValueId() != null && skuLsParams.getValueId().length > 0) {
            for (String valueId : skuLsParams.getValueId()) {
                //面包屑   如果筛选条件中有值则此条平台属性不再显示，跳出当前循环
                if (excludeValueIds != null && excludeValueIds.length > 0) {
                    String excludeValueId = excludeValueIds[0];
                    if (excludeValueId.equals(valueId)) {
                        continue;
                    }
                }
                if (urlParam != null && urlParam.length() > 0) {
                    urlParam += "&";
                }
                urlParam += "valueId=" + valueId;
            }
        }
        return urlParam;
    }

}
