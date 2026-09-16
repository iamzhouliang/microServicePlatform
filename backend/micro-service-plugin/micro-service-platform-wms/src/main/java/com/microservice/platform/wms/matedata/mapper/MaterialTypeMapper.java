package com.microservice.platform.wms.matedata.mapper;

import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.wms.matedata.domain.entity.MaterialCategory;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * 物料类型表 Mapper 接口
 * </p>
 *
 * @author ddCat
 * @since 2024-06-18
 */
@Repository
public interface MaterialTypeMapper extends SuperMapper<MaterialCategory> {

}
