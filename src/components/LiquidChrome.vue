<template>
  <div ref="containerRef" class="liquid-chrome-container" :class="{ 'is-static': !interactive }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import { Renderer, Program, Mesh, Triangle } from 'ogl';

// 接受外部传入的配置
const props = defineProps({
  baseColor: { type: Array, default: () => [0.02, 0.04, 0.08] }, // 默认深黑海蓝主题
  speed: { type: Number, default: 0.2 },
  amplitude: { type: Number, default: 0.3 },
  frequencyX: { type: Number, default: 3.0 },
  frequencyY: { type: Number, default: 3.0 },
  interactive: { type: Boolean, default: true },
  staticTimeOffset: { type: Number, default: 450.0 } // 静态时的精美固定帧
});

const containerRef = ref(null);
let renderer, gl, animationId;

onMounted(() => {
  if (!containerRef.value) return;

  const container = containerRef.value;
  renderer = new Renderer({ antialias: true, alpha: true, dpr: 1 });
  gl = renderer.gl;
  gl.clearColor(0, 0, 0, 0); // 透明底色，以便与网页融合

  const vertexShader = `
    attribute vec2 position;
    attribute vec2 uv;
    varying vec2 vUv;
    void main() {
      vUv = uv;
      gl_Position = vec4(position, 0.0, 1.0);
    }
  `;

  // 根据用户的 React 原型修改的着色器，融入金/蓝暗色系
  const fragmentShader = `
    precision highp float;
    uniform float uTime;
    uniform vec3 uResolution;
    uniform vec3 uBaseColor;
    uniform float uAmplitude;
    uniform float uFrequencyX;
    uniform float uFrequencyY;
    uniform vec2 uMouse;
    varying vec2 vUv;

    vec4 renderImage(vec2 uvCoord) {
        vec2 fragCoord = uvCoord * uResolution.xy;
        vec2 uv = (2.0 * fragCoord - uResolution.xy) / min(uResolution.x, uResolution.y);

        for (float i = 1.0; i < 10.0; i++){
            uv.x += uAmplitude / i * cos(i * uFrequencyX * uv.y + uTime + uMouse.x * 2.5);
            uv.y += uAmplitude / i * cos(i * uFrequencyY * uv.x + uTime + uMouse.y * 2.5);
        }

        vec2 diff = (uvCoord - uMouse);
        float dist = length(diff);
        float falloff = exp(-dist * 15.0); // 提升衰减，更克制
        float ripple = sin(12.0 * dist - uTime * 2.0) * 0.02;
        uv += (diff / (dist + 0.0001)) * ripple * falloff;

        // 【主题定制】放弃原有的单纯除以 sin (会导致刺眼纯白白斑)
        // 使用暗流涌动的混色模式
        float wave = abs(sin(uTime * 0.5 - uv.y - uv.x));
        
        // 提取 Aegis 主题色彩
        vec3 darkBlue = vec3(0.015, 0.03, 0.06); 
        vec3 cyanEdge = vec3(0.1, 0.25, 0.4);
        vec3 goldShine = vec3(0.6, 0.4, 0.1);

        // 柔和过渡
        vec3 baseTonal = mix(cyanEdge, darkBlue, smoothstep(0.0, 0.8, wave));
        
        // 限制亮度最高阀值，达到"简约不刺眼"的需求
        vec3 finalColor = uBaseColor + baseTonal;
        
        // 鼠标处的流体交互闪金光
        finalColor += goldShine * falloff * 0.2;

        return vec4(finalColor, 1.0);
    }

    void main() {
        vec4 col = vec4(0.0);
        int samples = 0;
        // 简化的多重采样抗锯齿
        for (int i = 0; i <= 1; i++){
            for (int j = 0; j <= 1; j++){
                vec2 offset = vec2(float(i), float(j)) * (0.5 / min(uResolution.x, uResolution.y));
                col += renderImage(vUv + offset);
                samples++;
            }
        }
        gl_FragColor = col / float(samples);
    }
  `;

  const geometry = new Triangle(gl);
  const program = new Program(gl, {
    vertex: vertexShader,
    fragment: fragmentShader,
    transparent: true,
    uniforms: {
      uTime: { value: props.interactive ? 0 : props.staticTimeOffset },
      uResolution: { value: new Float32Array([1, 1, 1]) },
      uBaseColor: { value: new Float32Array(props.baseColor) },
      uAmplitude: { value: props.amplitude },
      uFrequencyX: { value: props.frequencyX },
      uFrequencyY: { value: props.frequencyY },
      uMouse: { value: new Float32Array([0.5, 0.5]) }
    }
  });
  const mesh = new Mesh(gl, { geometry, program });

  function resize() {
    if(!container) return;
    renderer.setSize(container.offsetWidth, container.offsetHeight);
    const resUniform = program.uniforms.uResolution.value;
    resUniform[0] = gl.canvas.width;
    resUniform[1] = gl.canvas.height;
    resUniform[2] = gl.canvas.width / Math.max(gl.canvas.height, 1);
    
    // 如果是静态页面，需要手动渲染一次当前尺寸
    if (!props.interactive) {
      renderer.render({ scene: mesh });
    }
  }
  
  window.addEventListener('resize', resize);
  setTimeout(resize, 0); // 初始化撑树宽

  function handleMouseMove(event) {
    const rect = container.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    const y = 1 - (event.clientY - rect.top) / rect.height;
    const mouseUniform = program.uniforms.uMouse.value;
    // 缓动鼠标跟随
    mouseUniform[0] += (x - mouseUniform[0]) * 0.08;
    mouseUniform[1] += (y - mouseUniform[1]) * 0.08;
  }

  function handleTouchMove(event) {
    if (event.touches.length > 0) {
      const touch = event.touches[0];
      const rect = container.getBoundingClientRect();
      const x = (touch.clientX - rect.left) / rect.width;
      const y = 1 - (touch.clientY - rect.top) / rect.height;
      const mouseUniform = program.uniforms.uMouse.value;
      mouseUniform[0] += (x - mouseUniform[0]) * 0.1;
      mouseUniform[1] += (y - mouseUniform[1]) * 0.1;
    }
  }

  if (props.interactive) {
    container.addEventListener('mousemove', handleMouseMove);
    container.addEventListener('touchmove', handleTouchMove, { passive: true });
  }

  function update(t) {
    animationId = requestAnimationFrame(update);
    // 实时更新时间变量
    program.uniforms.uTime.value = (t * 0.001 * props.speed) + (props.interactive ? 0 : props.staticTimeOffset);
    renderer.render({ scene: mesh });
  }

  if (props.interactive) {
    animationId = requestAnimationFrame(update);
  } else {
    // 如果不开启动态，仅渲染一帧极美的静态图
    renderer.render({ scene: mesh });
  }

  container.appendChild(gl.canvas);
});

onBeforeUnmount(() => {
  if (animationId) cancelAnimationFrame(animationId);
  window.removeEventListener('resize', () => {});
  if (gl && gl.canvas && gl.canvas.parentElement) {
    gl.canvas.parentElement.removeChild(gl.canvas);
  }
  if (gl) gl.getExtension('WEBGL_lose_context')?.loseContext();
});
</script>

<style scoped>
.liquid-chrome-container {
  width: 100%;
  height: 100%;
  position: fixed;
  top: 0;
  left: 0;
  z-index: -1;
  pointer-events: none; /* 防止挡住其他元素 */
  overflow: hidden;
}

/* 静态降噪，增加企业级克制感 */
.is-static {
  opacity: 0.6; 
  filter: blur(1px);
}

.liquid-chrome-container :deep(canvas) {
  width: 100% !important;
  height: 100% !important;
  display: block;
}
</style>