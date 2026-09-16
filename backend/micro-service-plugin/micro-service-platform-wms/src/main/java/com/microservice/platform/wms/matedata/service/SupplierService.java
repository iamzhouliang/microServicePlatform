package com.microservice.platform.wms.matedata.service;

import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.wms.matedata.domain.entity.Supplier;
import com.microservice.platform.wms.matedata.domain.req.SupplierSaveReq;

/**
 * <p>
 * 供应商 服务类
 * </p>
 *
 * @author ddCat
 * @since 2024-07-01
 */
public interface SupplierService extends SuperService<Supplier> {


    /**
     * 添加供应商
     *
     * @param req req
     */
    void create(SupplierSaveReq req);

    /**
     * 修改供应商
     *
     * @param id  供应商ID
     * @param req 供应商信息
     */
    void modify(Long id, SupplierSaveReq req);

}
