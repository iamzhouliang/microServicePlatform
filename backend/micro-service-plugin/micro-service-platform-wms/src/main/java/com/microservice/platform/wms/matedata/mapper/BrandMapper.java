package com.microservice.platform.wms.matedata.mapper;

import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.wms.matedata.domain.entity.Brand;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * 品牌表 Mapper 接口
 * </p>
 *
 * @author ddCat
 * @since 2024-06-18
 */
@Repository
public interface BrandMapper extends SuperMapper<Brand> {

}
