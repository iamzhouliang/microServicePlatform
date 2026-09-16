package com.microservice.platform.wms.basic.service;

import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.wms.basic.domain.entity.Aisle;
import com.microservice.platform.wms.basic.domain.req.AisleSaveReq;

/**
 * <p>
 * 巷道表 服务类
 * </p>
 *
 * @author ddCat
 * @since 2024-06-17
 */
public interface AisleService extends SuperService<Aisle> {


    /**
     * 创建数据
     *
     * @param req req
     */
    void create(AisleSaveReq req);

    /**
     * 修改巷道
     *
     * @param id  主键id
     * @param req 巷道信息
     */
    void modify(Long id, AisleSaveReq req);
}
