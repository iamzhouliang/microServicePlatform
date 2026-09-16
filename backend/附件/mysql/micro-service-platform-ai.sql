/*
 Navicat Premium Data Transfer

 Source Server         : localhost-docker
 Source Server Type    : MySQL
 Source Server Version : 80200 (8.2.0)
 Source Host           : localhost:3306
 Source Schema         : micro-service-platform-ai

 Target Server Type    : MySQL
 Target Server Version : 80200 (8.2.0)
 File Encoding         : 65001

 Date: 28/12/2025 21:23:34
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_agent
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent`;
CREATE TABLE `ai_agent` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                            `user_id` bigint DEFAULT NULL COMMENT 'userID',
                            `name` varchar(100) DEFAULT NULL COMMENT '智能体名称',
                            `kb_id` bigint DEFAULT NULL COMMENT '知识库ID',
                            `model_id` bigint DEFAULT NULL COMMENT '绑定的模型ID',
                            `temperature` decimal(3,2) DEFAULT '0.70' COMMENT '发散程度',
                            `description` varchar(255) DEFAULT NULL COMMENT '智能体描述',
                            `role_prompt` text COMMENT '角色设定(System Prompt)',
                            `toolset_ids` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '启用的 Toolset 标识，逗号分隔',
                            `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                            `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Agent 头像',
                            `system_prompt` varchar(255) DEFAULT NULL COMMENT '预设系统提示词',
                            `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                            `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                            `create_name` varchar(255) DEFAULT NULL COMMENT '创建人名称',
                            `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                            `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                            `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
                            `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2004778922172108802 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体配置';

-- ----------------------------
-- Table structure for ai_agent_kb_rel
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent_kb_rel`;
CREATE TABLE `ai_agent_kb_rel` (
                                   `agent_id` bigint NOT NULL,
                                   `kb_id` bigint NOT NULL,
                                   `priority` int DEFAULT '0',
                                   PRIMARY KEY (`agent_id`,`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体-知识库关联表';

-- ----------------------------
-- Table structure for ai_conversation
-- ----------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
                                   `id` bigint NOT NULL COMMENT 'ID',
                                   `title` varchar(255) DEFAULT NULL COMMENT '会话名称',
                                   `user_id` bigint DEFAULT NULL COMMENT '归属人',
                                   `agent_id` bigint DEFAULT NULL COMMENT '智能体ID',
                                   `type` tinyint DEFAULT NULL COMMENT '对话类型：1-普通对话 2-通用智能体对话 3-平台智能体 4-知识库对话 5-图片生成',
                                   `last_message` text COMMENT '最后一条消息内容',
                                   `message_count` int DEFAULT NULL COMMENT '消息数量',
                                   `pinned` tinyint(1) DEFAULT NULL COMMENT '是否置顶',
                                   `knowledge_base_ids` varchar(255) DEFAULT NULL COMMENT '关联的知识库ids',
                                   `tenant_id` bigint DEFAULT NULL,
                                   `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                                   `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                                   `create_name` varchar(255) DEFAULT NULL COMMENT '创建人名称',
                                   `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                   `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                                   `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
                                   `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                   PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话信息';

-- ----------------------------
-- Table structure for ai_conversation_turn
-- ----------------------------
DROP TABLE IF EXISTS `ai_conversation_turn`;
CREATE TABLE `ai_conversation_turn` (
                                        `id` bigint NOT NULL COMMENT '主键ID',
                                        `conversation_id` bigint NOT NULL COMMENT '会话ID，关联 ai_conversation',
                                        `previous_turn_id` bigint DEFAULT NULL COMMENT '上一轮交互ID（用于重试、分叉、上下文回溯）',
                                        `user_id` bigint DEFAULT NULL COMMENT '用户ID',
                                        `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                        `role` varchar(32) NOT NULL COMMENT '消息角色（SYSTEM / USER / ASSISTANT / TOOL / OBSERVATION）',
                                        `user_input` longtext COMMENT '用户原始输入内容',
                                        `final_prompt` longtext COMMENT '最终发送给模型的 Prompt',
                                        `model_output` longtext COMMENT '模型原始输出内容',
                                        `display_content` longtext COMMENT '最终展示给用户的内容',
                                        `thinking_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '模型思考内容',
                                        `model_provider` varchar(64) DEFAULT NULL COMMENT '模型提供商（openai / deepseek / qwen 等）',
                                        `model_name` varchar(128) DEFAULT NULL COMMENT '模型名称（如 gpt-4.1 / deepseek-r1）',
                                        `input_tokens` int DEFAULT NULL COMMENT '输入 Token 数（最终 Prompt）',
                                        `output_tokens` int DEFAULT NULL COMMENT '输出 Token 数',
                                        `inference_latency_ms` bigint DEFAULT NULL COMMENT '模型推理耗时（毫秒）',
                                        `reasoning_summary` longtext COMMENT '模型推理摘要信息（不存完整思维链）',
                                        `user_feedback` int DEFAULT NULL,
                                        `feedback_remark` varchar(255) DEFAULT NULL,
                                        `trace_id` varchar(64) DEFAULT NULL COMMENT '全链路追踪ID',
                                        `variables` json DEFAULT NULL COMMENT '扩展属性（JSON，如模型参数、调用配置）',
                                        `sequence_num` int NOT NULL COMMENT '会话内顺序号',
                                        `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',
                                        `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                                        `create_name` varchar(255) DEFAULT NULL COMMENT '创建人名称',
                                        `create_time` datetime NOT NULL COMMENT '创建时间',
                                        `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                                        `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
                                        `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                        PRIMARY KEY (`id`),
                                        KEY `idx_conv_seq` (`conversation_id`,`sequence_num`),
                                        KEY `idx_trace_id` (`trace_id`),
                                        KEY `idx_user_time` (`user_id`,`create_time`),
                                        KEY `idx_tenant_time_user_role` (`tenant_id`,`create_time`,`user_id`,`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 会话单轮交互记录（Turn 级）';

-- ----------------------------
-- Table structure for ai_conversation_summary
-- ----------------------------
DROP TABLE IF EXISTS `ai_conversation_summary`;
CREATE TABLE `ai_conversation_summary` (
                                           `id` bigint NOT NULL COMMENT '主键ID',
                                           `conversation_id` bigint NOT NULL COMMENT '会话ID',
                                           `user_id` bigint DEFAULT NULL COMMENT '用户ID',
                                           `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                           `summary_content` text NOT NULL COMMENT '摘要内容',
                                           `covered_until_sequence_num` int NOT NULL DEFAULT '0' COMMENT '摘要覆盖到的会话内序号',
                                           `model_name` varchar(128) DEFAULT NULL COMMENT '摘要模型名称',
                                           `version` int NOT NULL DEFAULT '1' COMMENT '摘要版本',
                                           `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',
                                           `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                                           `create_name` varchar(255) DEFAULT NULL COMMENT '创建人名称',
                                           `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                           `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                                           `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
                                           `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                           PRIMARY KEY (`id`),
                                           UNIQUE KEY `uk_conversation_summary_conversation` (`conversation_id`),
                                           KEY `idx_conversation_summary_user` (`user_id`),
                                           KEY `idx_conversation_summary_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI会话摘要';

-- ----------------------------
-- Table structure for ai_knowledge_base
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_base`;
CREATE TABLE `ai_knowledge_base` (
                                     `id` bigint NOT NULL COMMENT 'ID',
                                     `name` varchar(255) DEFAULT NULL COMMENT '知识库名称',
                                     `collection_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'default_coll' COMMENT '向量集合名称',
                                     `chat_model_id` bigint NOT NULL COMMENT '对话模型ID',
                                     `embed_model_id` bigint DEFAULT NULL COMMENT 'Embedding模型ID',
                                     `rerank_model_id` bigint DEFAULT NULL,
                                     `score_threshold` decimal(10,2) DEFAULT NULL COMMENT '相似度阈值 (0.0-1.0)',
                                     `top_k` int DEFAULT NULL COMMENT '单次召回数量(TopK)',
                                     `chunk_size` int DEFAULT NULL COMMENT '分片大小 (Token)',
                                     `chunk_overlap` int DEFAULT NULL COMMENT '分片重叠 (Token)',
                                     `version` int DEFAULT NULL COMMENT '版本号',
                                     `metadata` json DEFAULT NULL COMMENT '元数据',
                                     `enable_graph` tinyint(1) DEFAULT NULL COMMENT '是否启用知识图谱',
                                     `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '知识库描述',
                                     `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                     `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                                     `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                                     `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '创建人名称',
                                     `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                     `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                                     `last_modify_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最后修改人名称',
                                     `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                     PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库';

-- ----------------------------
-- Table structure for ai_knowledge_chunk
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_chunk`;
-- 说明：与实体 KnowledgeChunk 对齐；补 chunk_type/content_hash/start_position/end_position/tenant_id，chunk_idx->chunk_index。
CREATE TABLE `ai_knowledge_chunk` (
                                      `id` bigint NOT NULL COMMENT 'ID',
                                      `kb_id` bigint DEFAULT NULL COMMENT '所属知识库ID',
                                      `item_id` bigint DEFAULT NULL COMMENT '关联的知识条目ID',
                                      `chunk_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '分片类型: TEXT/QUESTION/ANSWER/FULL_QA',
                                      `content` longtext COMMENT '分片内容，用于embedding的文本',
                                      `content_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '内容哈希值',
                                      `vector_ref` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '外部向量库引用（如 milvus:12345）',
                                      `chunk_index` int DEFAULT '0' COMMENT '分片序号',
                                      `start_position` int DEFAULT NULL COMMENT '在原文中的起始位置',
                                      `end_position` int DEFAULT NULL COMMENT '在原文中的结束位置',
                                      `metadata` json DEFAULT NULL COMMENT '分片元数据',
                                      `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                      `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                                      `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                                      `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '创建人名称',
                                      `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                      `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                                      `last_modify_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最后修改人名称',
                                      `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_item_id` (`item_id`),
                                      KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识分片';

-- ----------------------------
-- Table structure for ai_knowledge_item
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_item`;
-- 说明：与实体 KnowledgeItem 对齐；type/status 以 String 枚举 code 存储；
--       补齐 question/answer/content_type/file_path/vectorized/graphed/version/tenant_id 列。
CREATE TABLE `ai_knowledge_item` (
                                     `id` bigint NOT NULL COMMENT 'ID',
                                     `kb_id` bigint DEFAULT NULL COMMENT '所属知识库ID',
                                     `type` varchar(32) NOT NULL COMMENT '类型: DOCUMENT/QA_PAIR/STRUCTURED/TEXT_SNIPPET',
                                     `title` varchar(255) DEFAULT NULL COMMENT '标题（文档标题或FAQ的展示文本）',
                                     `question` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '问题（仅问答对类型有效）',
                                     `answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '答案（仅问答对类型有效）',
                                     `content` longtext COMMENT '原始内容（用于分片与向量化）',
                                     `content_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '内容类型（pdf/text/html 等）',
                                     `file_path` varchar(500) DEFAULT NULL COMMENT '文件路径',
                                     `file_size` bigint DEFAULT NULL COMMENT '文件大小，仅对文档类有效',
                                     `content_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '内容哈希，用于去重与变更检测',
                                     `vectorized` tinyint(1) DEFAULT '0' COMMENT '是否已向量化',
                                     `graphed` tinyint(1) DEFAULT '0' COMMENT '是否已图谱化',
                                     `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PROCESSING/PROCESSED/FAILED',
                                     `metadata` json DEFAULT NULL COMMENT '扩展元数据（JSON）',
                                     `version` int DEFAULT '0' COMMENT '乐观锁版本',
                                     `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                     `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                                     `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                                     `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '创建人名称',
                                     `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                     `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                                     `last_modify_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最后修改人名称',
                                     `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                     PRIMARY KEY (`id`),
                                     KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识条目';

-- ----------------------------
-- Table structure for ai_mcp_server
-- ----------------------------
DROP TABLE IF EXISTS `ai_mcp_server`;
CREATE TABLE `ai_mcp_server` (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                 `name` varchar(100) DEFAULT NULL COMMENT '服务名称',
                                 `command` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'STDIO命令',
                                 `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '服务地址',
                                 `env` json DEFAULT NULL,
                                 `tool_governance` json DEFAULT NULL COMMENT '按原始 Tool 名配置的显式治理策略',
                                 `enabled_environments` varchar(500) DEFAULT NULL COMMENT '允许启用 Toolset 的环境，逗号分隔',
                                 `required_permissions` varchar(1000) DEFAULT NULL COMMENT '启用 Toolset 所需权限，逗号分隔',
                                 `args` text COMMENT '环境变量',
                                 `status` tinyint DEFAULT NULL COMMENT '状态',
                                 `type` varchar(10) DEFAULT NULL COMMENT '服务类型',
                                 `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                 `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                                 `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                                 `create_name` varchar(255) DEFAULT NULL COMMENT '创建人名称',
                                 `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                 `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                                 `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
                                 `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                 PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2004865314956963843 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Mcp配置';

-- ----------------------------
-- Table structure for ai_model
-- ----------------------------
DROP TABLE IF EXISTS `ai_model`;
CREATE TABLE `ai_model` (
                            `id` bigint NOT NULL COMMENT 'ID',
                            `provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提供商 (openai, deepseek)',
                            `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '模型类型',
                            `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '模型名称',
                            `api_key` varchar(500) DEFAULT NULL COMMENT 'API密钥',
                            `base_url` varchar(500) DEFAULT NULL COMMENT '基础URL',
                            `status` bit(1) DEFAULT b'1' COMMENT '启用/停用',
                            `tenant_id` json DEFAULT NULL COMMENT '租户ID',
                            `variables` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '参数配置',
                            `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                            `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                            `create_name` varchar(255) DEFAULT NULL COMMENT '创建人名称',
                            `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                            `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                            `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
                            `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多模态模型配置';

-- ----------------------------
-- Table structure for ai_kb_vectorization_task
-- 说明：与实体 VectorizationTask(@TableName ai_kb_vectorization_task) 对齐；
--       task_type/status 以 String 枚举 code 存储（PENDING/PROCESSING/COMPLETED/FAILED）。
-- ----------------------------
DROP TABLE IF EXISTS `ai_kb_vectorization_task`;
CREATE TABLE `ai_kb_vectorization_task` (
                                  `id` bigint NOT NULL COMMENT 'ID',
                                  `task_id` varchar(64) DEFAULT NULL COMMENT '任务ID（业务唯一标识）',
                                  `kb_id` bigint DEFAULT NULL COMMENT '知识库ID',
                                  `item_id` bigint DEFAULT NULL COMMENT '知识条目ID',
                                  `task_type` varchar(32) DEFAULT NULL COMMENT '任务类型: KNOWLEDGE_ITEM/BATCH/DOCUMENT/FAQ/STRUCTURED',
                                  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PROCESSING/COMPLETED/FAILED',
                                  `progress` int DEFAULT '0' COMMENT '处理进度(0-100)',
                                  `vector_ids` json DEFAULT NULL COMMENT '结果向量ID列表(JSON)',
                                  `error_message` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '错误信息',
                                  `token_usage` int DEFAULT NULL COMMENT '消耗的Token数',
                                  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                                  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                                  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '创建人名称',
                                  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                  `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                                  `last_modify_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最后修改人名称',
                                  `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_task_id` (`task_id`),
                                  KEY `idx_item_id` (`item_id`),
                                  KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='向量化任务';

-- ----------------------------
-- Table structure for ai_vector_metadata
-- 说明：与实体 VectorMetadata(@TableName ai_vector_metadata) 对齐；此前 DDL 缺失该表。
-- ----------------------------
DROP TABLE IF EXISTS `ai_vector_metadata`;
CREATE TABLE `ai_vector_metadata` (
                                  `id` bigint NOT NULL COMMENT 'ID',
                                  `vector_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '向量ID（向量库中唯一标识）',
                                  `kb_id` bigint DEFAULT NULL COMMENT '所属知识库ID',
                                  `item_id` bigint DEFAULT NULL COMMENT '关联知识条目ID',
                                  `chunk_id` bigint DEFAULT NULL COMMENT '关联知识分片ID',
                                  `chunk_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '分片类型: TEXT/QUESTION/ANSWER/FULL_QA',
                                  `collection_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '向量库集合名称',
                                  `text_content` longtext COMMENT '文本内容',
                                  `text_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '文本哈希值',
                                  `similarity_score` double DEFAULT NULL COMMENT '相似度分数',
                                  `metadata` json DEFAULT NULL COMMENT '扩展元数据(JSON)',
                                  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                                  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                                  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '创建人名称',
                                  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                  `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                                  `last_modify_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最后修改人名称',
                                  `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_vector_id` (`vector_id`),
                                  KEY `idx_item_id` (`item_id`),
                                  KEY `idx_kb_id` (`kb_id`),
                                  KEY `idx_chunk_id` (`chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='向量元数据';

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- Table structure for ai_workflow
-- ----------------------------
DROP TABLE IF EXISTS `ai_workflow`;
CREATE TABLE `ai_workflow`  (
                                `id` bigint NOT NULL COMMENT '主键ID',
                                `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作流名称',
                                `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '工作流描述',
                                `graph` json NOT NULL COMMENT '工作流图定义(JSON格式，包含nodes和edges)',
                                `input_variables` json NULL COMMENT '输入变量定义(JSON数组)',
                                `output_variables` json NULL COMMENT '输出变量定义(JSON数组)',
                                `current_version` int NOT NULL DEFAULT 1 COMMENT '当前版本号',
                                `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿, PUBLISHED-已发布, ARCHIVED-已归档',
                                `user_id` bigint NULL DEFAULT NULL COMMENT '所属用户ID',
                                `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID',
                                `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
                                `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
                                `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人名称',
                                `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                `last_modify_by` bigint NULL DEFAULT NULL COMMENT '最后修改人ID',
                                `last_modify_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最后修改人名称',
                                `last_modify_time` datetime NULL DEFAULT NULL COMMENT '最后修改时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                INDEX `idx_workflow_user`(`user_id` ASC) USING BTREE,
                                INDEX `idx_workflow_tenant`(`tenant_id` ASC) USING BTREE,
                                INDEX `idx_workflow_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI工作流定义' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_workflow_api_key
-- ----------------------------
DROP TABLE IF EXISTS `ai_workflow_api_key`;
CREATE TABLE `ai_workflow_api_key`  (
                                        `id` bigint NOT NULL COMMENT '主键ID',
                                        `workflow_id` bigint NOT NULL COMMENT '关联的工作流ID',
                                        `api_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'API Key（sk-wf-xxx 格式）',
                                        `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '备注名称',
                                        `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-启用, DISABLED-禁用',
                                        `rate_limit` int NOT NULL DEFAULT 0 COMMENT '每秒请求限制（QPS），0 表示不限制',
                                        `expire_time` datetime NULL DEFAULT NULL COMMENT '过期时间，NULL 表示永不过期',
                                        `last_used_time` datetime NULL DEFAULT NULL COMMENT '最后使用时间',
                                        `total_calls` bigint NOT NULL DEFAULT 0 COMMENT '累计调用次数',
                                        `tenant_id` bigint NULL DEFAULT NULL COMMENT '所属租户ID',
                                        `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
                                        `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
                                        `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人名称',
                                        `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                        `last_modify_by` bigint NULL DEFAULT NULL COMMENT '最后修改人ID',
                                        `last_modify_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最后修改人名称',
                                        `last_modify_time` datetime NULL DEFAULT NULL COMMENT '最后修改时间',
                                        PRIMARY KEY (`id`) USING BTREE,
                                        UNIQUE INDEX `uk_api_key`(`api_key` ASC) USING BTREE,
                                        INDEX `idx_workflow_id`(`workflow_id` ASC) USING BTREE,
                                        INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE,
                                        INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工作流 API Key（第三方访问凭证）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_workflow_execution
-- ----------------------------
DROP TABLE IF EXISTS `ai_workflow_execution`;
CREATE TABLE `ai_workflow_execution`  (
                                          `id` bigint NOT NULL COMMENT '主键ID',
                                          `execution_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '执行ID(UUID)',
                                          `workflow_id` bigint NOT NULL COMMENT '工作流ID',
                                          `workflow_version` int NOT NULL COMMENT '执行时的工作流版本',
                                          `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '执行状态: PENDING-等待中, RUNNING-执行中, COMPLETED-已完成, FAILED-失败, PAUSED-已暂停, CANCELLED-已取消',
                                          `inputs` json NULL COMMENT '输入参数(JSON格式)',
                                          `outputs` json NULL COMMENT '输出结果(JSON格式)',
                                          `snapshot` json NULL COMMENT '执行快照(用于断点续传)',
                                          `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
                                          `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
                                          `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
                                          `duration` bigint NULL DEFAULT NULL COMMENT '执行耗时(毫秒)',
                                          `user_id` bigint NULL DEFAULT NULL COMMENT '执行用户ID',
                                          `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID',
                                          `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
                                          `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
                                          `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人名称',
                                          `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                          `last_modify_by` bigint NULL DEFAULT NULL COMMENT '最后修改人ID',
                                          `last_modify_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最后修改人名称',
                                          `last_modify_time` datetime NULL DEFAULT NULL COMMENT '最后修改时间',
                                          `node_states` json NULL COMMENT '节点执行状态(各节点的执行详情)',
                                          `input_tokens` bigint NULL DEFAULT NULL COMMENT '输入Token数',
                                          `output_tokens` bigint NULL DEFAULT NULL COMMENT '输出Token数',
                                          `total_tokens` bigint NULL DEFAULT NULL COMMENT '总Token数',
                                          `llm_call_count` int NULL DEFAULT NULL COMMENT 'LLM调用次数',
                                          PRIMARY KEY (`id`) USING BTREE,
                                          UNIQUE INDEX `uk_execution_id`(`execution_id` ASC) USING BTREE,
                                          INDEX `idx_execution_workflow`(`workflow_id` ASC) USING BTREE,
                                          INDEX `idx_execution_user`(`user_id` ASC) USING BTREE,
                                          INDEX `idx_execution_tenant`(`tenant_id` ASC) USING BTREE,
                                          INDEX `idx_workflow_tenant_end_user`(`tenant_id` ASC, `end_time` ASC, `user_id` ASC) USING BTREE,
                                          INDEX `idx_execution_status`(`status` ASC) USING BTREE,
                                          INDEX `idx_execution_time`(`start_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI工作流执行记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_workflow_permission
-- ----------------------------
DROP TABLE IF EXISTS `ai_workflow_permission`;
CREATE TABLE `ai_workflow_permission`  (
                                           `id` bigint NOT NULL COMMENT '主键ID',
                                           `workflow_id` bigint NOT NULL COMMENT '工作流ID',
                                           `user_id` bigint NOT NULL COMMENT '用户ID',
                                           `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色: OWNER-所有者, EDITOR-编辑者, VIEWER-查看者',
                                           `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                           PRIMARY KEY (`id`) USING BTREE,
                                           UNIQUE INDEX `uk_workflow_user`(`workflow_id` ASC, `user_id` ASC) USING BTREE,
                                           INDEX `idx_permission_workflow`(`workflow_id` ASC) USING BTREE,
                                           INDEX `idx_permission_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI工作流权限' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_workflow_template
-- ----------------------------
DROP TABLE IF EXISTS `ai_workflow_template`;
CREATE TABLE `ai_workflow_template`  (
                                         `id` bigint NOT NULL COMMENT '主键ID',
                                         `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
                                         `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板描述',
                                         `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板分类: rag-RAG问答, summary-文档摘要, extraction-数据提取, conversation-多轮对话, generation-内容生成',
                                         `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板图标',
                                         `graph` json NOT NULL COMMENT '工作流图定义(JSON格式)',
                                         `built_in` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否内置模板',
                                         `user_id` bigint NULL DEFAULT NULL COMMENT '创建用户ID(自定义模板)',
                                         `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID',
                                         `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
                                         `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
                                         `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人名称',
                                         `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                         `last_modify_by` bigint NULL DEFAULT NULL COMMENT '最后修改人ID',
                                         `last_modify_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最后修改人名称',
                                         `last_modify_time` datetime NULL DEFAULT NULL COMMENT '最后修改时间',
                                         PRIMARY KEY (`id`) USING BTREE,
                                         INDEX `idx_template_category`(`category` ASC) USING BTREE,
                                         INDEX `idx_template_builtin`(`built_in` ASC) USING BTREE,
                                         INDEX `idx_template_user`(`user_id` ASC) USING BTREE,
                                         INDEX `idx_template_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI工作流模板' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_workflow_version
-- ----------------------------
DROP TABLE IF EXISTS `ai_workflow_version`;
CREATE TABLE `ai_workflow_version`  (
                                        `id` bigint NOT NULL COMMENT '主键ID',
                                        `workflow_id` bigint NOT NULL COMMENT '工作流ID',
                                        `version` int NOT NULL COMMENT '版本号',
                                        `graph_snapshot` json NOT NULL COMMENT '工作流图快照(JSON格式)',
                                        `change_log` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '变更说明',
                                        `published` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已发布',
                                        `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
                                        `created_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                        PRIMARY KEY (`id`) USING BTREE,
                                        UNIQUE INDEX `uk_workflow_version`(`workflow_id` ASC, `version` ASC) USING BTREE,
                                        INDEX `idx_version_workflow`(`workflow_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI工作流版本历史' ROW_FORMAT = Dynamic;

-- ----------------------------
-- LangChain4j 原生 Harness 治理表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_harness_operation` (
    `id` bigint NOT NULL COMMENT '主键 ID',
    `operation_id` varchar(128) NOT NULL COMMENT '单次受治理写操作标识',
    `actor_user_id` bigint NOT NULL COMMENT '原操作人用户 ID',
    `conversation_id` bigint NOT NULL COMMENT '所属会话 ID',
    `initial_turn_id` varchar(128) NOT NULL COMMENT '首次发起用户轮标识',
    `toolset_id` varchar(255) NOT NULL COMMENT '工具所属工具集标识',
    `tool_identity` varchar(512) NOT NULL COMMENT '工具精确身份，格式为 domain:tool@version',
    `tool_call_id` varchar(128) NOT NULL COMMENT 'LangChain4j 工具调用 ID',
    `arguments_digest` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工具参数 SHA-256 摘要',
    `idempotency_key` varchar(255) NOT NULL COMMENT '租户内幂等键',
    `status` varchar(32) NOT NULL COMMENT '受治理操作状态',
    `confirmation_required` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否需要跨用户轮确认',
    `approval_required` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否需要独立审批',
    `approval_target` varchar(500) DEFAULT NULL COMMENT '脱敏审批目标',
    `approval_change` varchar(1000) DEFAULT NULL COMMENT '脱敏关键变更',
    `approval_risk` varchar(500) DEFAULT NULL COMMENT '风险说明',
    `approval_expires_at` datetime(3) DEFAULT NULL COMMENT '审批截止时间',
    `fencing_token` bigint NOT NULL DEFAULT 0 COMMENT '执行隔离令牌',
    `lease_owner` varchar(255) DEFAULT NULL COMMENT '执行租约持有者',
    `lease_expires_at` datetime(3) DEFAULT NULL COMMENT '执行租约过期时间',
    `confirmed_turn_id` varchar(128) DEFAULT NULL COMMENT '完成确认的用户轮标识',
    `approved_by` bigint DEFAULT NULL COMMENT '独立审批人用户 ID',
    `result_text` longtext DEFAULT NULL COMMENT '安全投影后的工具结果',
    `error_message` varchar(2000) DEFAULT NULL COMMENT '中文失败原因',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` bigint NOT NULL COMMENT '所属租户 ID',
    `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `create_by` bigint DEFAULT NULL COMMENT '创建人 ID',
    `create_name` varchar(255) DEFAULT NULL COMMENT '创建人姓名',
    `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人 ID',
    `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人姓名',
    `last_modify_time` datetime(3) NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_harness_operation_tenant_operation` (`tenant_id`, `operation_id`),
    UNIQUE KEY `uk_harness_operation_tenant_idempotency` (`tenant_id`, `idempotency_key`),
    KEY `idx_harness_operation_pending` (`tenant_id`, `actor_user_id`, `conversation_id`, `status`, `last_modify_time`),
    KEY `idx_harness_operation_lease` (`status`, `lease_expires_at`),
    KEY `idx_harness_operation_toolset` (`tenant_id`, `toolset_id`, `last_modify_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='LangChain4j 受治理工具操作记录';

CREATE TABLE IF NOT EXISTS `ai_skill_activation` (
    `id` bigint NOT NULL COMMENT '主键 ID',
    `conversation_id` bigint NOT NULL COMMENT '所属会话 ID',
    `turn_id` varchar(128) NOT NULL COMMENT '激活技能的用户轮标识',
    `skill_code` varchar(100) NOT NULL COMMENT '模型可见技能代码',
    `skill_version` varchar(50) NOT NULL COMMENT '已发布技能版本',
    `content_digest` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '技能说明文件 SHA-256 摘要',
    `user_id` bigint NOT NULL COMMENT '激活用户 ID',
    `tenant_id` bigint NOT NULL COMMENT '所属租户 ID',
    `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `create_by` bigint DEFAULT NULL COMMENT '创建人 ID',
    `create_name` varchar(255) DEFAULT NULL COMMENT '创建人姓名',
    `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人 ID',
    `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人姓名',
    `last_modify_time` datetime(3) DEFAULT NULL COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_activation_turn_code` (`tenant_id`, `conversation_id`, `turn_id`, `skill_code`),
    KEY `idx_skill_activation_revision` (`tenant_id`, `skill_code`, `skill_version`, `content_digest`),
    KEY `idx_skill_activation_conversation` (`tenant_id`, `conversation_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='LangChain4j 官方技能激活台账';

-- ----------------------------
-- Table structure for ai_skill
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_skill` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `name` varchar(100) NOT NULL COMMENT '技能名称',
    `code` varchar(100) NOT NULL COMMENT '技能编码',
    `description` varchar(500) DEFAULT NULL COMMENT '技能描述',
    `category` varchar(50) DEFAULT NULL COMMENT '技能分类',
    `version` varchar(50) NOT NULL DEFAULT '1.0.0' COMMENT '技能稳定版本',
    `requires_tools` varchar(2000) DEFAULT NULL COMMENT '精确工具标识列表，逗号分隔',
    `requires_toolsets` varchar(2000) DEFAULT NULL COMMENT '依赖的 Toolset 标识，逗号分隔',
    `content_digest` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'SKILL.md UTF-8 正文 SHA-256 摘要',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标',
    `tags` varchar(500) DEFAULT NULL COMMENT '标签列表，逗号分隔',
    `skill_path` varchar(255) NOT NULL COMMENT '技能目录相对路径',
    `skill_file` varchar(255) NOT NULL COMMENT 'SKILL.md相对路径',
    `resource_count` int NOT NULL DEFAULT '0' COMMENT '资源文件数量',
    `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
    `published` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否发布',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',
    `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
    `create_name` varchar(255) DEFAULT NULL COMMENT '创建人名称',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
    `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
    `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_code_version` (`code`, `version`),
    KEY `idx_skill_category` (`category`),
    KEY `idx_skill_status` (`status`),
    KEY `idx_skill_published` (`published`),
    KEY `idx_skill_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI技能配置';

