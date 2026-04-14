import { Camera, Mesh, Plane, Program, Renderer, Texture, Transform } from 'ogl';
import { useEffect, useMemo, useRef } from 'react';

import './CircularGallery.css';

function debounce(func, wait) {
  let timeout;
  return function wrapped(...args) {
    clearTimeout(timeout);
    timeout = setTimeout(() => func.apply(this, args), wait);
  };
}

function lerp(p1, p2, t) {
  return p1 + (p2 - p1) * t;
}

function autoBind(instance) {
  const proto = Object.getPrototypeOf(instance);
  Object.getOwnPropertyNames(proto).forEach(key => {
    if (key !== 'constructor' && typeof instance[key] === 'function') {
      instance[key] = instance[key].bind(instance);
    }
  });
}

function createTextTexture(gl, text, font = 'bold 26px monospace', color = '#ffffff') {
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  context.font = font;
  const metrics = context.measureText(text);
  const textWidth = Math.ceil(metrics.width);
  const fontSizeMatch = String(font).match(/(\d+)px/);
  const fontSize = fontSizeMatch ? Number(fontSizeMatch[1]) : 26;
  const textHeight = Math.ceil(fontSize * 1.35);
  canvas.width = textWidth + 24;
  canvas.height = textHeight + 24;
  context.font = font;
  context.fillStyle = color;
  context.textBaseline = 'middle';
  context.textAlign = 'center';
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.fillText(text, canvas.width / 2, canvas.height / 2);
  const texture = new Texture(gl, { generateMipmaps: false });
  texture.image = canvas;
  return { texture, width: canvas.width, height: canvas.height };
}

class Title {
  constructor({ gl, plane, renderer, text, textColor = '#dbeafe', font = 'bold 24px monospace' }) {
    autoBind(this);
    this.gl = gl;
    this.plane = plane;
    this.renderer = renderer;
    this.text = text;
    this.textColor = textColor;
    this.font = font;
    this.createMesh();
  }

  createMesh() {
    const { texture, width, height } = createTextTexture(this.gl, this.text, this.font, this.textColor);
    const geometry = new Plane(this.gl);
    const program = new Program(this.gl, {
      vertex: `
        attribute vec3 position;
        attribute vec2 uv;
        uniform mat4 modelViewMatrix;
        uniform mat4 projectionMatrix;
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragment: `
        precision highp float;
        uniform sampler2D tMap;
        varying vec2 vUv;
        void main() {
          vec4 color = texture2D(tMap, vUv);
          if (color.a < 0.1) discard;
          gl_FragColor = color;
        }
      `,
      uniforms: { tMap: { value: texture } },
      transparent: true,
    });
    this.mesh = new Mesh(this.gl, { geometry, program });
    const aspect = width / height;
    const textHeight = this.plane.scale.y * 0.16;
    const textWidth = textHeight * aspect;
    this.mesh.scale.set(textWidth, textHeight, 1);
    this.mesh.position.y = -this.plane.scale.y * 0.5 - textHeight * 0.6 - 0.05;
    this.mesh.setParent(this.plane);
  }
}

class Media {
  constructor({
    geometry,
    gl,
    image,
    index,
    length,
    renderer,
    scene,
    screen,
    text,
    viewport,
    bend,
    textColor,
    borderRadius = 0,
    font,
    itemKey,
    isAlert,
    showTitles,
  }) {
    this.extra = 0;
    this.geometry = geometry;
    this.gl = gl;
    this.image = image;
    this.index = index;
    this.length = length;
    this.renderer = renderer;
    this.scene = scene;
    this.screen = screen;
    this.text = text;
    this.viewport = viewport;
    this.bend = bend;
    this.textColor = textColor;
    this.borderRadius = borderRadius;
    this.font = font;
    this.itemKey = itemKey;
    this.isAlert = isAlert;
    this.showTitles = showTitles !== false;
    this.createShader();
    this.createMesh();
    if (this.showTitles) {
      this.createTitle();
    }
    this.onResize();
  }

  createShader() {
    const texture = new Texture(this.gl, { generateMipmaps: true });
    this.program = new Program(this.gl, {
      depthTest: false,
      depthWrite: false,
      vertex: `
        precision highp float;
        attribute vec3 position;
        attribute vec2 uv;
        uniform mat4 modelViewMatrix;
        uniform mat4 projectionMatrix;
        varying vec2 vUv;
        void main() {
          vUv = uv;
          vec3 p = position;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.0);
        }
      `,
      fragment: `
        precision highp float;
        uniform vec2 uImageSizes;
        uniform vec2 uPlaneSizes;
        uniform sampler2D tMap;
        uniform float uBorderRadius;
        uniform float uAlert;
        uniform float uTime;
        varying vec2 vUv;

        float roundedBoxSDF(vec2 p, vec2 b, float r) {
          vec2 d = abs(p) - b;
          return length(max(d, vec2(0.0))) + min(max(d.x, d.y), 0.0) - r;
        }

        void main() {
          vec2 ratio = vec2(
            min((uPlaneSizes.x / uPlaneSizes.y) / (uImageSizes.x / uImageSizes.y), 1.0),
            min((uPlaneSizes.y / uPlaneSizes.x) / (uImageSizes.y / uImageSizes.x), 1.0)
          );
          vec2 uv = vec2(
            vUv.x * ratio.x + (1.0 - ratio.x) * 0.5,
            vUv.y * ratio.y + (1.0 - ratio.y) * 0.5
          );

          vec4 baseColor = texture2D(tMap, uv);
          float d = roundedBoxSDF(vUv - 0.5, vec2(0.5 - uBorderRadius), uBorderRadius);
          float edgeSmooth = 0.002;
          float alpha = 1.0 - smoothstep(-edgeSmooth, edgeSmooth, d);

          float border = smoothstep(0.08, 0.0, abs(d));
          vec3 alertGlow = vec3(1.0, 0.16, 0.2) * border * uAlert * 0.85;

          vec2 p = vUv - 0.5;
          float radius = length(p);
          float pulseWave = fract(uTime * 0.38);
          float pulseRing = 1.0 - smoothstep(0.0, 0.03, abs(radius - (0.16 + pulseWave * 0.52)));
          float pulseMask = smoothstep(0.48, 0.08, radius);
          vec3 pulseGlow = vec3(1.0, 0.12, 0.18) * pulseRing * pulseMask * uAlert * 0.62;

          vec3 color = baseColor.rgb + alertGlow + pulseGlow;

          gl_FragColor = vec4(color, baseColor.a * alpha);
        }
      `,
      uniforms: {
        tMap: { value: texture },
        uPlaneSizes: { value: [0, 0] },
        uImageSizes: { value: [0, 0] },
        uBorderRadius: { value: this.borderRadius },
        uAlert: { value: this.isAlert ? 1 : 0 },
        uTime: { value: 0 },
      },
      transparent: true,
    });

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.src = this.image;
    img.onload = () => {
      texture.image = img;
      this.program.uniforms.uImageSizes.value = [img.naturalWidth, img.naturalHeight];
    };
  }

  setAlert(active) {
    this.isAlert = !!active;
    if (this.program?.uniforms?.uAlert) {
      this.program.uniforms.uAlert.value = this.isAlert ? 1 : 0;
    }
  }

  setTime(timeValue) {
    if (this.program?.uniforms?.uTime) {
      this.program.uniforms.uTime.value = timeValue;
    }
  }

  createMesh() {
    this.plane = new Mesh(this.gl, {
      geometry: this.geometry,
      program: this.program,
    });
    this.plane.setParent(this.scene);
  }

  createTitle() {
    this.title = new Title({
      gl: this.gl,
      plane: this.plane,
      renderer: this.renderer,
      text: this.text,
      textColor: this.textColor,
      font: this.font,
    });
  }

  update(scroll, direction) {
    this.plane.position.x = this.x - scroll.current - this.extra;

    const x = this.plane.position.x;
    const H = this.viewport.width / 2;

    if (this.bend === 0) {
      this.plane.position.y = 0;
      this.plane.rotation.z = 0;
    } else {
      const B_abs = Math.abs(this.bend);
      const R = (H * H + B_abs * B_abs) / (2 * B_abs);
      const effectiveX = Math.min(Math.abs(x), H);
      const arc = R - Math.sqrt(R * R - effectiveX * effectiveX);

      if (this.bend > 0) {
        this.plane.position.y = -arc;
        this.plane.rotation.z = -Math.sign(x) * Math.asin(effectiveX / R);
      } else {
        this.plane.position.y = arc;
        this.plane.rotation.z = Math.sign(x) * Math.asin(effectiveX / R);
      }
    }

    const planeOffset = this.plane.scale.x / 2;
    const viewportOffset = this.viewport.width / 2;
    this.isBefore = this.plane.position.x + planeOffset < -viewportOffset;
    this.isAfter = this.plane.position.x - planeOffset > viewportOffset;

    if (direction === 'right' && this.isBefore) {
      this.extra -= this.widthTotal;
      this.isBefore = this.isAfter = false;
    }
    if (direction === 'left' && this.isAfter) {
      this.extra += this.widthTotal;
      this.isBefore = this.isAfter = false;
    }
  }

  onResize({ screen, viewport } = {}) {
    if (screen) this.screen = screen;
    if (viewport) this.viewport = viewport;

    this.scale = this.screen.height / 1500;
    this.plane.scale.y = (this.viewport.height * (1080 * this.scale)) / this.screen.height;
    this.plane.scale.x = (this.viewport.width * (860 * this.scale)) / this.screen.width;
    this.plane.program.uniforms.uPlaneSizes.value = [this.plane.scale.x, this.plane.scale.y];

    this.padding = 2;
    this.width = this.plane.scale.x + this.padding;
    this.widthTotal = this.width * this.length;
    this.x = this.width * this.index;
  }
}

class App {
  constructor(container, {
    items,
    bend,
    textColor = '#ffffff',
    borderRadius = 0,
    font = 'bold 30px Figtree',
    scrollSpeed = 2,
    scrollEase = 0.05,
    activeKey = '',
    showTitles = true,
  } = {}) {
    document.documentElement.classList.remove('no-js');
    this.container = container;
    this.scrollSpeed = scrollSpeed;
    this.scroll = { ease: scrollEase, current: 0, target: 0, last: 0 };
    this.activeKey = String(activeKey || '');
    this.onCheckDebounce = debounce(this.onCheck, 200);
    this.createRenderer();
    this.createCamera();
    this.createScene();
    this.onResize();
    this.createGeometry();
    this.createMedias(items, bend, textColor, borderRadius, font, showTitles);
    this.update();
    this.addEventListeners();
  }

  createRenderer() {
    this.renderer = new Renderer({
      alpha: true,
      antialias: true,
      dpr: Math.min(window.devicePixelRatio || 1, 2),
    });
    this.gl = this.renderer.gl;
    this.gl.clearColor(0, 0, 0, 0);
    this.container.appendChild(this.gl.canvas);
  }

  createCamera() {
    this.camera = new Camera(this.gl);
    this.camera.fov = 45;
    this.camera.position.z = 20;
  }

  createScene() {
    this.scene = new Transform();
  }

  createGeometry() {
    this.planeGeometry = new Plane(this.gl, {
      heightSegments: 50,
      widthSegments: 100,
    });
  }

  createMedias(items, bend = 1, textColor, borderRadius, font, showTitles = true) {
    const defaultItems = [
      { image: '/favicon.svg', text: 'EMPLOYEE 01', key: 'demo-1', isAlert: false },
      { image: '/favicon.svg', text: 'EMPLOYEE 02', key: 'demo-2', isAlert: false },
      { image: '/favicon.svg', text: 'EMPLOYEE 03', key: 'demo-3', isAlert: false },
      { image: '/favicon.svg', text: 'EMPLOYEE 04', key: 'demo-4', isAlert: false },
      { image: '/favicon.svg', text: 'EMPLOYEE 05', key: 'demo-5', isAlert: false },
      { image: '/favicon.svg', text: 'EMPLOYEE 06', key: 'demo-6', isAlert: false },
    ];

    const sourceItems = items && items.length ? items : defaultItems;
    this.mediasImages = sourceItems.concat(sourceItems);

    this.medias = this.mediasImages.map((data, index) => {
      return new Media({
        geometry: this.planeGeometry,
        gl: this.gl,
        image: data.image,
        index,
        length: this.mediasImages.length,
        renderer: this.renderer,
        scene: this.scene,
        screen: this.screen,
        text: data.text,
        viewport: this.viewport,
        bend,
        textColor,
        borderRadius,
        font,
        itemKey: data.key,
        isAlert: !!data.isAlert,
        showTitles,
      });
    });
  }

  updateActiveKey(activeKey) {
    this.activeKey = String(activeKey || '');
    if (!this.medias || !this.medias.length) return;
    for (const media of this.medias) {
      media.setAlert(this.activeKey && media.itemKey === this.activeKey);
    }
  }

  onTouchDown(e) {
    this.isDown = true;
    this.scroll.position = this.scroll.current;
    this.start = e.touches ? e.touches[0].clientX : e.clientX;
  }

  onTouchMove(e) {
    if (!this.isDown) return;
    const x = e.touches ? e.touches[0].clientX : e.clientX;
    const distance = (this.start - x) * (this.scrollSpeed * 0.025);
    this.scroll.target = this.scroll.position + distance;
  }

  onTouchUp() {
    this.isDown = false;
    this.onCheck();
  }

  onWheel(e) {
    const delta = e.deltaY || e.wheelDelta || e.detail;
    this.scroll.target += (delta > 0 ? this.scrollSpeed : -this.scrollSpeed) * 0.2;
    this.onCheckDebounce();
  }

  onCheck() {
    if (!this.medias || !this.medias[0]) return;
    const width = this.medias[0].width;
    const itemIndex = Math.round(Math.abs(this.scroll.target) / width);
    const item = width * itemIndex;
    this.scroll.target = this.scroll.target < 0 ? -item : item;
  }

  onResize() {
    this.screen = {
      width: this.container.clientWidth,
      height: this.container.clientHeight,
    };
    this.renderer.setSize(this.screen.width, this.screen.height);
    this.camera.perspective({ aspect: this.screen.width / this.screen.height });

    const fov = (this.camera.fov * Math.PI) / 180;
    const height = 2 * Math.tan(fov / 2) * this.camera.position.z;
    const width = height * this.camera.aspect;
    this.viewport = { width, height };

    if (this.medias) {
      this.medias.forEach(media => media.onResize({ screen: this.screen, viewport: this.viewport }));
    }
  }

  update() {
    this.scroll.current = lerp(this.scroll.current, this.scroll.target, this.scroll.ease);
    const direction = this.scroll.current > this.scroll.last ? 'right' : 'left';
    const now = performance.now() * 0.001;

    if (this.medias) {
      this.medias.forEach(media => {
        media.setTime(now);
        media.update(this.scroll, direction);
      });
    }

    this.renderer.render({ scene: this.scene, camera: this.camera });
    this.scroll.last = this.scroll.current;
    this.raf = window.requestAnimationFrame(this.update.bind(this));
  }

  addEventListeners() {
    this.boundOnResize = this.onResize.bind(this);
    this.boundOnWheel = this.onWheel.bind(this);
    this.boundOnTouchDown = this.onTouchDown.bind(this);
    this.boundOnTouchMove = this.onTouchMove.bind(this);
    this.boundOnTouchUp = this.onTouchUp.bind(this);

    window.addEventListener('resize', this.boundOnResize);
    window.addEventListener('mousewheel', this.boundOnWheel);
    window.addEventListener('wheel', this.boundOnWheel);
    window.addEventListener('mousedown', this.boundOnTouchDown);
    window.addEventListener('mousemove', this.boundOnTouchMove);
    window.addEventListener('mouseup', this.boundOnTouchUp);
    window.addEventListener('touchstart', this.boundOnTouchDown);
    window.addEventListener('touchmove', this.boundOnTouchMove);
    window.addEventListener('touchend', this.boundOnTouchUp);
  }

  destroy() {
    window.cancelAnimationFrame(this.raf);
    window.removeEventListener('resize', this.boundOnResize);
    window.removeEventListener('mousewheel', this.boundOnWheel);
    window.removeEventListener('wheel', this.boundOnWheel);
    window.removeEventListener('mousedown', this.boundOnTouchDown);
    window.removeEventListener('mousemove', this.boundOnTouchMove);
    window.removeEventListener('mouseup', this.boundOnTouchUp);
    window.removeEventListener('touchstart', this.boundOnTouchDown);
    window.removeEventListener('touchmove', this.boundOnTouchMove);
    window.removeEventListener('touchend', this.boundOnTouchUp);

    if (this.renderer && this.renderer.gl && this.renderer.gl.canvas.parentNode) {
      this.renderer.gl.canvas.parentNode.removeChild(this.renderer.gl.canvas);
    }
  }
}

function buildDataImage(item) {
  const canvas = document.createElement('canvas');
  canvas.width = 1160;
  canvas.height = 800;
  const ctx = canvas.getContext('2d');

  const grd = ctx.createLinearGradient(0, 0, canvas.width, canvas.height);
  grd.addColorStop(0, '#0a1e39');
  grd.addColorStop(1, item.isAlert ? '#5c1026' : '#163b67');
  ctx.fillStyle = grd;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  const flare = ctx.createRadialGradient(canvas.width * 0.24, canvas.height * 0.2, 30, canvas.width * 0.24, canvas.height * 0.2, 280);
  flare.addColorStop(0, item.isAlert ? 'rgba(255, 122, 146, 0.42)' : 'rgba(120, 196, 255, 0.46)');
  flare.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = flare;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  ctx.strokeStyle = item.isAlert ? 'rgba(255,68,91,0.9)' : 'rgba(130,196,255,0.75)';
  ctx.lineWidth = 10;
  ctx.strokeRect(16, 16, canvas.width - 32, canvas.height - 32);

  const centerX = canvas.width / 2;
  drawFittedText(ctx, item.employeeName || item.text || 'EMPLOYEE', centerX, 118, 1060, 62, 34, 1.04, '#e5f0ff', 700, 2, 'center');

  drawFittedText(ctx, `部门: ${item.department || '-'}`, centerX, 228, 980, 38, 26, 1.12, '#a8c5ea', 600, 2, 'center');
  drawFittedText(ctx, `身份: ${item.role || '-'}`, centerX, 276, 980, 38, 26, 1.12, '#a8c5ea', 600, 2, 'center');
  drawFittedText(ctx, `账号: ${item.username || '-'}`, centerX, 324, 980, 38, 26, 1.12, '#a8c5ea', 600, 2, 'center');

  drawFittedText(ctx, `风险: ${item.risk || 'low'}`, centerX, 420, 980, 48, 30, 1.08, item.isAlert ? '#ff667f' : '#65c5ff', 700, 1, 'center');

  drawFittedText(ctx, item.badge || 'AI COMPLIANCE CARD', centerX, 492, 1060, 38, 26, 1.1, '#d6e8ff', 600, 2, 'center');

  ctx.strokeStyle = item.isAlert ? 'rgba(255, 120, 146, 0.54)' : 'rgba(144, 210, 255, 0.48)';
  ctx.lineWidth = 2;
  for (let i = 0; i < 5; i += 1) {
    const y = 560 + i * 28;
    ctx.beginPath();
    ctx.moveTo(48, y);
    ctx.lineTo(canvas.width - 52, y);
    ctx.stroke();
  }

  return canvas.toDataURL('image/png');
}

function drawFittedText(ctx, text, x, y, maxWidth, startSize, minSize, lineHeightScale, color, weight, maxLines, align = 'left') {
  const value = String(text || '').trim();
  if (!value) return;
  let size = startSize;
  let lines = [];

  while (size >= minSize) {
    ctx.font = `${weight} ${size}px Segoe UI`;
    lines = wrapText(ctx, value, maxWidth, maxLines);
    const widest = lines.reduce((max, line) => Math.max(max, ctx.measureText(line).width), 0);
    if (widest <= maxWidth || size === minSize) break;
    size -= 2;
  }

  ctx.fillStyle = color;
  ctx.textBaseline = 'top';
  ctx.textAlign = align === 'center' ? 'center' : 'left';
  lines.forEach((line, idx) => {
    ctx.fillText(line, x, y + idx * size * lineHeightScale);
  });
}

function wrapText(ctx, text, maxWidth, maxLines = 2) {
  const words = String(text || '').split(/\s+/).filter(Boolean);
  if (words.length <= 1) {
    const single = words[0] || '';
    if (ctx.measureText(single).width <= maxWidth) return [single];
    const chars = [...single];
    const lines = [];
    let current = '';
    chars.forEach(char => {
      const test = `${current}${char}`;
      if (ctx.measureText(test).width <= maxWidth) {
        current = test;
        return;
      }
      if (current) lines.push(current);
      current = char;
    });
    if (current) lines.push(current);
    return lines.slice(0, maxLines);
  }

  const lines = [];
  let current = '';
  words.forEach(word => {
    const test = current ? `${current} ${word}` : word;
    if (ctx.measureText(test).width <= maxWidth) {
      current = test;
    } else {
      if (current) lines.push(current);
      current = word;
    }
  });
  if (current) lines.push(current);
  return lines.slice(0, maxLines);
}

function normalizeItems(items = [], activeKey = '') {
  const safe = Array.isArray(items) ? items : [];
  const next = safe.map((item, idx) => {
    const key = String(item?.key || item?.employeeId || item?.username || `emp-${idx}`);
    const isAlert = activeKey && key === String(activeKey);
    return {
      key,
      text: item?.text || `${item?.employeeName || item?.username || 'Employee'} · ${item?.department || '-'} · ${item?.role || '-'}`,
      image: item?.image || buildDataImage({ ...item, isAlert }),
      isAlert,
    };
  });

  if (!activeKey) return next;
  const activeIndex = next.findIndex(item => item.key === String(activeKey));
  if (activeIndex <= 0) return next;
  const [active] = next.splice(activeIndex, 1);
  next.unshift(active);
  return next;
}

export default function CircularGallery({
  items,
  activeKey = '',
  bend = 1,
  textColor = '#ffffff',
  borderRadius = 0.05,
  font = 'bold 30px Figtree',
  scrollSpeed = 2,
  scrollEase = 0.05,
  showTitles = true,
}) {
  const containerRef = useRef(null);
  const appRef = useRef(null);
  const normalizedItems = useMemo(() => normalizeItems(items, activeKey), [items, activeKey]);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const app = new App(containerRef.current, {
      items: normalizedItems,
      bend,
      textColor,
      borderRadius,
      font,
      scrollSpeed,
      scrollEase,
      activeKey,
      showTitles,
    });
    appRef.current = app;

    return () => {
      app.destroy();
      appRef.current = null;
    };
  }, [normalizedItems, bend, textColor, borderRadius, font, scrollSpeed, scrollEase, activeKey, showTitles]);

  return <div className="circular-gallery" ref={containerRef} />;
}
