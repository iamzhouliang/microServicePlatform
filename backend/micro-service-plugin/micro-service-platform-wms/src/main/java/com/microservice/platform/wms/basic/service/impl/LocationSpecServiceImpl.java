package com.microservice.platform.wms.basic.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.wms.basic.domain.entity.LocationSpec;
import com.microservice.platform.wms.basic.domain.req.LocationSpecSaveReq;
import com.microservice.platform.wms.basic.mapper.LocationSpecMapper;
import com.microservice.platform.wms.basic.service.LocationSpecService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 储位规格表 服务实现类
 * </p>
 *
 * @author ddCat
 * @since 2024-06-17
 */
@Service
public class LocationSpecServiceImpl extends SuperServiceImpl<LocationSpecMapper, LocationSpec> implements LocationSpecService {


    @Override
    public void create(LocationSpecSaveReq req) {
        Long count = this.baseMapper.selectCount(LocationSpec::getCode, req.getCode());
        if (count != null && count > 0) {
            throw CheckedException.badRequest("该储位规格编号已存在");
        }
        var bean = BeanUtil.toBean(req, LocationSpec.class);
        this.baseMapper.insert(bean);

    }

    @Override
    public void modify(Long id, LocationSpecSaveReq req) {
        Long count = this.baseMapper.selectCount(Wraps.<LocationSpec>lbQ().ne(LocationSpec::getId, id).eq(LocationSpec::getCode, req.getCode()));
        if (count != null && count > 0) {
            throw CheckedException.badRequest("该储位规格编号已存在");
        }
        var bean = BeanUtilPlus.toBean(id, req, LocationSpec.class);
        this.baseMapper.updateById(bean);
    }
}
