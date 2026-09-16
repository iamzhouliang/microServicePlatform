import { defHttp } from '#/api/request';

export function PageList(data: any) {
  return defHttp.request('/wms/scan_orders/flow/page', {
    method: 'post',
    data,
  });
}
