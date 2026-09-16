import type { ConversationMessageRep } from '../api';

import { defHttp } from '#/api/request';

// ==================== 类型定义 ====================

/** 智能体详情 */
export interface AgentDetail {
  id: number;
  name: string;
  avatar: string;
  description: string;
  aiSystemMessage?: string;
  chatModelId?: number;
  kbId?: number;
  tools?: string[];
  createTime?: string;
}

/** 智能体列表项（前端展示模型） */
export interface ChatAgent {
  id: number | string;
  name: string;
  avatar?: string;
  description?: string;
  status?: number | string;
  createTime?: string;
  [key: string]: any;
}

/** 会话信息 */
export interface Conversation {
  id: string;
  title: string;
  agentId?: number;
  lastMessage?: string;
  messageCount?: number;
  isPinned?: boolean;
  createTime: string;
}

/** 创建会话请求 */
export interface CreateConversationReq {
  title: string;
  agentId?: number;
  type: number;
}

/** 更新会话请求 */
export interface UpdateConversationReq {
  title?: string;
}

// ==================== API 接口 ====================

/** 获取智能体分页列表 */
export function getAgentPage(params: { current: number; size: number }) {
  return defHttp.post<{ records: AgentDetail[]; total: number }>(
    '/ai/chat-agents/page',
    params,
  );
}

/** 获取智能体详情 */
export function getAgentDetail(id: number) {
  return defHttp.get<AgentDetail>(`/ai/chat-agents/${id}/detail`);
}

/** 获取智能体的会话列表 */
export function getConversationsByAgent(agentId: number) {
  return defHttp.post<{ records: Conversation[]; total: number }>(
    '/ai/conversations/page',
    { agentId, current: 1, size: 1000 },
  );
}

/** 获取会话消息列表 */
export function getConversationMessages(conversationId: string) {
  return defHttp.get<ConversationMessageRep[]>(
    `/ai/conversations/${conversationId}/messages`,
  );
}

/** 创建会话响应 */
export interface CreateConversationRep {
  id: string;
}

/** 创建会话 */
export function createConversation(data: CreateConversationReq) {
  return defHttp.post<CreateConversationRep>('/ai/conversations/create', data);
}

/** 更新会话 */
export function updateConversation(id: string, data: UpdateConversationReq) {
  return defHttp.put(`/ai/conversations/${id}/modify`, data);
}

/** 删除会话 */
export function deleteConversation(id: string) {
  return defHttp.delete(`/ai/conversations/${id}`);
}
