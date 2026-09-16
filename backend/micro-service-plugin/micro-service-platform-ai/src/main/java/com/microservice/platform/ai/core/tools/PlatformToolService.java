/*
 * Copyright (c) 2023 MICRO-SERVICE-PLATFORM Authors. All Rights Reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microservice.platform.ai.core.tools;

import com.microservice.framework.ai.core.annotation.AiTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

/**
 * 平台功能工具服务
 * 提供平台基础功能的 AI 工具集成
 *
 * @return 处理结果
 * @author xJh
 * @since 2025/12/06
 */
@Service
@AiTool(
        name = "平台基础工具集",
        description = "提供菜单查询、系统状态检测等基础运维能力",
        icon = "ant-design:tool-outlined")
public class PlatformToolService {

    public static final String QUERY_MENU = "platform_query_menu";
    public static final String ANALYZE_TEXT = "platform_analyze_text";
    public static final String LIST_FEATURES = "platform_list_features";

    @Tool(name = QUERY_MENU, value = "查询当前平台的菜单结构")
    public String getMenu() {
        return "当前平台的菜单有：" + "菜单1, 菜单2, 菜单3";
    }

    /**
     * 文本长度统计
     * @param text 文本内容
     * @return 处理结果
     */
    @Tool(name = ANALYZE_TEXT, value = "统计文本的字符数、单词数和行数")
    public String analyzeText(@P("要分析的文本") String text) {
        if (text == null || text.isEmpty()) {
            return "请提供要分析的文本内容";
        }

        int length = text.length();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        int lines = text.split("\n").length;

        return String.format("""
                📊 文本分析结果：
                • 字符数：%d
                • 单词数：%d
                • 行数：%d
                • 首字符：%s
                • 末字符：%s
                """,
                length,
                words,
                lines,
                length > 0 ? text.charAt(0) : "无",
                length > 0 ? text.charAt(length - 1) : "无");
    }

    @Tool(name = LIST_FEATURES, value = "获取平台助手支持的基础功能列表")
    public String getPlatformFeatures() {
        return """
                我具备以下平台功能：
                • 菜单查询 - 查看平台菜单结构
                • 用户管理 - 用户信息查询和管理
                • 系统状态 - 获取平台运行状态
                • 操作日志 - 查看系统操作记录
                """;
    }
}
