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

package com.microservice.framework.ai.harness.memory;

/**
 * 不依赖具体模型分词器的近似 Token 估算器。
 *
 * <p>该估算只用于上下文预算和保护阈值，不用于计费。中文按两个字符约一个 Token，
 * 其他可见字符按四个字符约一个 Token；JSON 结构会额外增加安全余量。</p>
 *
 * @author xiao1
 * @since 2026-07
 */
public final class ApproximateTokenEstimator {
    
    private static final double JSON_SAFETY_RATIO = 1.2D;
    
    /**
     * 估算文本占用的 Token 数。
     *
     * @param text 待估算文本
     * @return 近似 Token 数
     */
    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjkCharacters = 0;
        int otherCharacters = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (isCjk(codePoint)) {
                cjkCharacters++;
            } else {
                otherCharacters++;
            }
        }
        int estimated = divideRoundingUp(cjkCharacters, 2) + divideRoundingUp(otherCharacters, 4);
        if (looksLikeJson(text)) {
            estimated = (int) Math.ceil(estimated * JSON_SAFETY_RATIO);
        }
        return Math.max(1, estimated);
    }
    
    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
    
    private boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    private int divideRoundingUp(int value, int divisor) {
        return value == 0 ? 0 : (value + divisor - 1) / divisor;
    }
}
