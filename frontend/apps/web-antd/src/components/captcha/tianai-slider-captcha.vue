<script setup lang="ts">
/**
 * tianai-captcha 滑块验证码组件
 * 基于 https://gitee.com/dromara/tianai-captcha 封装
 */
import type {
  CaptchaGenerateResponse,
  TianaiSliderCaptchaProps,
  TrackPoint,
} from './types';

import { computed, onMounted, reactive, ref } from 'vue';

import { ReloadOutlined } from '@ant-design/icons-vue';
import { Spin } from 'ant-design-vue';

import { checkCaptcha, generateCaptcha } from './api';

const props = withDefaults(defineProps<TianaiSliderCaptchaProps>(), {
  width: 320,
  height: 160,
  principal: '',
});

const emit = defineEmits<{
  fail: [];
  success: [captchaKey: string];
}>();

/** 验证码数据 */
const captchaData = ref<CaptchaGenerateResponse | null>(null);

/** 组件状态 */
const state = reactive({
  loading: false,
  dragging: false,
  isPassing: false,
  showTip: false,
  tipText: '',
  tipType: '' as '' | 'error' | 'success',
});

/** 滑块位置（缩放后的） */
const sliderLeft = ref(0);

/** 轨迹记录 */
const trackList = ref<TrackPoint[]>([]);
const startTime = ref(0);
const startX = ref(0);
const startY = ref(0);

/** 计算缩放比例 */
const scale = computed(() => {
  if (!captchaData.value) return 1;
  return props.width / captchaData.value.backgroundImageWidth;
});

/** 缩放后的显示高度 */
const displayHeight = computed(() => {
  if (!captchaData.value) return props.height;
  return Math.round(captchaData.value.backgroundImageHeight * scale.value);
});

/** 缩放后的滑块宽度 */
const sliderWidth = computed(() => {
  if (!captchaData.value) return 40;
  return Math.round(captchaData.value.templateImageWidth * scale.value);
});

/** 计算滑块Y位置（缩放后） */
const sliderTop = computed(() => {
  if (!captchaData.value) return 0;
  const data = captchaData.value.data;
  if (data?.randomY !== undefined) {
    return Math.round(data.randomY * scale.value);
  }
  if ((captchaData.value as any).randomY !== undefined) {
    return Math.round((captchaData.value as any).randomY * scale.value);
  }
  // 全高滑块
  const bgHeight = captchaData.value.backgroundImageHeight;
  const sliderHeight = captchaData.value.templateImageHeight;
  if (sliderHeight >= bgHeight) {
    return 0;
  }
  return Math.round(((bgHeight - sliderHeight) / 2) * scale.value);
});

/** 滑动条最大距离（缩放后） */
const maxSlideDistance = computed(() => {
  if (!captchaData.value) return props.width - 40;
  const originalMax =
    captchaData.value.backgroundImageWidth -
    captchaData.value.templateImageWidth;
  return Math.round(originalMax * scale.value);
});

/** 处理base64图片 */
function formatBase64Image(base64: string): string {
  if (!base64) return '';
  if (base64.startsWith('data:')) {
    return base64;
  }
  return `data:image/png;base64,${base64}`;
}

/** 获取验证码 */
async function fetchCaptcha() {
  state.loading = true;
  state.isPassing = false;
  state.showTip = false;
  sliderLeft.value = 0;
  trackList.value = [];

  try {
    const res = await generateCaptcha();
    captchaData.value = res;
  } catch {
    state.showTip = true;
    state.tipText = '获取验证码失败，点击重试';
    state.tipType = 'error';
  } finally {
    state.loading = false;
  }
}

/** 获取事件坐标 */
function getEventPosition(e: MouseEvent | TouchEvent) {
  if ('touches' in e && e.touches[0]) {
    return { x: e.touches[0].clientX, y: e.touches[0].clientY };
  }
  return { x: (e as MouseEvent).clientX, y: (e as MouseEvent).clientY };
}

/** 开始拖动 */
function handleDragStart(e: MouseEvent | TouchEvent) {
  if (state.isPassing || state.loading || !captchaData.value) return;

  const pos = getEventPosition(e);
  state.dragging = true;
  state.showTip = false;
  startX.value = pos.x;
  startY.value = pos.y;
  startTime.value = Date.now();
  trackList.value = [{ x: 0, y: 0, t: 0, type: 'down' }];
}

/** 拖动中 */
function handleDragMove(e: MouseEvent | TouchEvent) {
  if (!state.dragging || !captchaData.value) return;

  const pos = getEventPosition(e);
  let moveX = pos.x - startX.value;
  moveX = Math.max(0, Math.min(moveX, maxSlideDistance.value));
  sliderLeft.value = moveX;

  // 记录原始坐标（需要还原缩放）
  trackList.value.push({
    x: Math.round(moveX / scale.value),
    y: Math.round((pos.y - startY.value) / scale.value),
    t: Date.now() - startTime.value,
    type: 'move',
  });
}

/** 拖动结束 */
async function handleDragEnd() {
  if (!state.dragging || !captchaData.value) return;
  state.dragging = false;

  if (sliderLeft.value < 10) {
    return;
  }

  const endTime = Date.now();
  // 还原到原始坐标
  const originalLeft = Math.round(sliderLeft.value / scale.value);
  const originalTop = Math.round(sliderTop.value / scale.value);

  trackList.value.push({
    x: originalLeft,
    y: 0,
    t: endTime - startTime.value,
    type: 'up',
  });

  const params = {
    id: captchaData.value.id,
    principal: props.principal,
    captchaTrack: {
      bgImageWidth: captchaData.value.backgroundImageWidth,
      bgImageHeight: captchaData.value.backgroundImageHeight,
      templateImageWidth: captchaData.value.templateImageWidth,
      templateImageHeight: captchaData.value.templateImageHeight,
      startTime: startTime.value,
      stopTime: endTime,
      left: originalLeft,
      top: originalTop,
      trackList: trackList.value,
      data: null,
    },
  };

  try {
    const res = await checkCaptcha(params);
    if (res?.tmpToken) {
      state.isPassing = true;
      state.showTip = true;
      state.tipText = '验证成功';
      state.tipType = 'success';
      emit('success', res.tmpToken);
    } else {
      handleVerifyFail();
    }
  } catch {
    handleVerifyFail();
  }
}

/** 验证失败处理 */
function handleVerifyFail() {
  state.showTip = true;
  state.tipText = '验证失败，请重试';
  state.tipType = 'error';
  emit('fail');
  setTimeout(() => {
    fetchCaptcha();
  }, 1000);
}

/** 刷新验证码 */
function refresh() {
  fetchCaptcha();
}

onMounted(() => {
  fetchCaptcha();
});

defineExpose({ refresh });
</script>

<template>
  <div class="tianai-captcha" :style="{ width: `${props.width}px` }">
    <!-- 图片区域 -->
    <div class="captcha-image-wrapper">
      <Spin :spinning="state.loading">
        <div
          class="captcha-image"
          :style="{
            width: `${props.width}px`,
            height: `${displayHeight}px`,
          }"
        >
          <!-- 背景图 -->
          <img
            v-if="captchaData?.backgroundImage"
            :src="formatBase64Image(captchaData.backgroundImage)"
            class="background-image"
            draggable="false"
          />
          <!-- 滑块图 -->
          <img
            v-if="captchaData?.templateImage"
            :src="formatBase64Image(captchaData.templateImage)"
            :style="{
              left: `${sliderLeft}px`,
              top: `${sliderTop}px`,
              width: `${sliderWidth}px`,
              height: `${displayHeight}px`,
            }"
            class="slider-image"
            draggable="false"
          />
          <!-- 提示信息 -->
          <div v-if="state.showTip" class="tip" :class="[state.tipType]">
            {{ state.tipText }}
          </div>
        </div>
      </Spin>
      <!-- 刷新按钮 -->
      <div
        class="refresh-btn"
        :class="{ spinning: state.loading }"
        title="刷新验证码"
        @click="refresh"
      >
        <ReloadOutlined />
      </div>
    </div>

    <!-- 滑动条 -->
    <div
      class="slider-bar"
      :style="{ width: `${props.width}px` }"
      @mousemove="handleDragMove"
      @mouseup="handleDragEnd"
      @mouseleave="handleDragEnd"
      @touchmove.prevent="handleDragMove"
      @touchend="handleDragEnd"
    >
      <div class="slider-track">
        <div
          class="slider-progress"
          :class="{ success: state.isPassing }"
          :style="{ width: `${sliderLeft + 20}px` }"
        ></div>
        <span v-if="!state.dragging && !state.isPassing" class="slider-text">
          向右拖动滑块完成验证
        </span>
        <span v-if="state.isPassing" class="slider-text success"> ✓ </span>
      </div>
      <div
        class="slider-btn"
        :class="{ dragging: state.dragging, success: state.isPassing }"
        :style="{ left: `${sliderLeft}px` }"
        @mousedown="handleDragStart"
        @touchstart.prevent="handleDragStart"
      >
        <span v-if="!state.isPassing">→</span>
        <span v-else>✓</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tianai-captcha {
  user-select: none;
}

.captcha-image-wrapper {
  position: relative;
  margin-bottom: 12px;
}

.captcha-image {
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8eb 100%);
  border-radius: 12px;
  box-shadow: 0 4px 12px rgb(0 0 0 / 8%);
}

.background-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 12px;
}

.slider-image {
  position: absolute;
  pointer-events: none;
  object-fit: cover;
  filter: drop-shadow(2px 2px 4px rgb(0 0 0 / 30%));
}

.tip {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  text-align: center;
  backdrop-filter: blur(4px);
}

.tip.success {
  background: linear-gradient(
    135deg,
    rgb(82 196 26 / 95%) 0%,
    rgb(56 158 13 / 95%) 100%
  );
}

.tip.error {
  background: linear-gradient(
    135deg,
    rgb(255 77 79 / 95%) 0%,
    rgb(207 19 34 / 95%) 100%
  );
}

.refresh-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 14px;
  color: #6b7280;
  cursor: pointer;
  background: rgb(255 255 255 / 95%);
  border-radius: 50%;
  box-shadow: 0 2px 8px rgb(0 0 0 / 12%);
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.refresh-btn:hover {
  color: #1890ff;
  background: #fff;
  box-shadow: 0 4px 12px rgb(24 144 255 / 25%);
  transform: rotate(180deg) scale(1.1);
}

.refresh-btn.spinning {
  animation: spin 0.8s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.slider-bar {
  position: relative;
  height: 44px;
}

.slider-track {
  position: relative;
  height: 44px;
  overflow: hidden;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  box-shadow: inset 0 2px 4px rgb(0 0 0 / 4%);
}

.slider-progress {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  border-radius: 22px 0 0 22px;
  transition: background 0.3s ease;
}

.slider-progress.success {
  background: linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%);
}

.slider-text {
  position: absolute;
  width: 100%;
  font-size: 13px;
  line-height: 42px;
  color: #94a3b8;
  text-align: center;
  letter-spacing: 1px;
  transition: all 0.3s ease;
}

.slider-text.success {
  font-size: 20px;
  font-weight: 600;
  color: #22c55e;
}

.slider-btn {
  position: absolute;
  top: 2px;
  left: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 16px;
  color: #64748b;
  cursor: grab;
  background: linear-gradient(135deg, #fff 0%, #f8fafc 100%);
  border: none;
  border-radius: 50%;
  box-shadow:
    0 2px 8px rgb(0 0 0 / 10%),
    0 1px 2px rgb(0 0 0 / 6%);
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.slider-btn:hover {
  color: #1890ff;
  background: linear-gradient(135deg, #fff 0%, #eff6ff 100%);
  box-shadow:
    0 4px 12px rgb(24 144 255 / 20%),
    0 2px 4px rgb(24 144 255 / 10%);
  transform: scale(1.05);
}

.slider-btn.dragging {
  color: #1890ff;
  cursor: grabbing;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  box-shadow: 0 4px 16px rgb(24 144 255 / 30%);
  transform: scale(1.08);
}

.slider-btn.success {
  color: #22c55e;
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
  box-shadow: 0 4px 12px rgb(34 197 94 / 25%);
}
</style>
