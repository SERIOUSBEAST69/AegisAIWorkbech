/* eslint-disable react/no-unknown-property */
import * as THREE from 'three';
import { useRef, useState, useEffect, memo } from 'react';
import { Canvas, createPortal, useFrame, useThree } from '@react-three/fiber';
import {
  useFBO,
  useGLTF,
  useScroll,
  Image,
  Scroll,
  Preload,
  ScrollControls,
  MeshTransmissionMaterial,
  Text,
} from '@react-three/drei';
import { Renderer, Program, Mesh, Triangle } from 'ogl';
import { easing } from 'maath';

const SCORE_GUIDE_CARDS = [
  {
    id: 'dimension-guide-overview',
    kind: 'dimension-guide',
    icon: '📊',
    name: '评分维度总览',
    provider: '治理基线说明',
    category: 'governance',
    risk_level: 'medium',
    total_risk_score: 50,
    description: '风险评级由模型固有风险、隐私暴露、调用量、失败率、延时五个维度联合计算。',
    tags: ['评分维度', '治理基线', '可解释'],
  },
  {
    id: 'dimension-guide-base',
    kind: 'dimension-guide',
    icon: '🧱',
    name: '模型固有风险',
    provider: '稳定性基线',
    category: 'base_risk',
    risk_level: 'high',
    total_risk_score: 78,
    description: '依据模型风险分级与稳定性基线。',
    tags: ['模型基线', '稳定性', '固有风险'],
  },
  {
    id: 'dimension-guide-privacy',
    kind: 'dimension-guide',
    icon: '🔐',
    name: '隐私暴露风险',
    provider: '服务特征评估',
    category: 'privacy_exposure',
    risk_level: 'high',
    total_risk_score: 74,
    description: '依据模型服务特征评估隐私暴露面。',
    tags: ['隐私', '数据暴露', '服务特征'],
  },
  {
    id: 'dimension-guide-usage',
    kind: 'dimension-guide',
    icon: '📈',
    name: '调用量风险',
    provider: '敞口评估',
    category: 'usage_volume',
    risk_level: 'medium',
    total_risk_score: 62,
    description: '调用越高，风险敞口越大。',
    tags: ['调用量', '风险敞口', '业务热度'],
  },
  {
    id: 'dimension-guide-failure',
    kind: 'dimension-guide',
    icon: '🧯',
    name: '失败率风险',
    provider: '异常概率评估',
    category: 'failure_rate',
    risk_level: 'medium',
    total_risk_score: 66,
    description: '失败率越高，异常行为概率越高。',
    tags: ['失败率', '异常行为', '可靠性'],
  },
  {
    id: 'dimension-guide-latency',
    kind: 'dimension-guide',
    icon: '⏱',
    name: '延时风险',
    provider: '链路时延评估',
    category: 'latency',
    risk_level: 'medium',
    total_risk_score: 59,
    description: '高延时可能触发超时重试与泄露放大。',
    tags: ['延时', '重试放大', '链路安全'],
  },
];

const deckShellStyle = {
  position: 'relative',
  width: '100%',
  height: '100%',
  overflow: 'hidden',
  background: 'transparent',
};

const deckCanvasStyle = {
  position: 'absolute',
  inset: 0,
  zIndex: 2,
  background: 'radial-gradient(circle at 18% 12%, rgba(124, 190, 255, 0.12), transparent 42%), radial-gradient(circle at 84% 86%, rgba(93, 146, 232, 0.1), transparent 40%)',
};

const deckTitleWrapStyle = {
  position: 'absolute',
  top: 0,
  left: 0,
  right: 0,
  height: '100%',
  display: 'flex',
  alignItems: 'flex-start',
  justifyContent: 'center',
  pointerEvents: 'none',
  zIndex: 4,
};

const deckTitleInnerStyle = {
  position: 'absolute',
  left: '50%',
  top: '10px',
  transformOrigin: '50% 0%',
  pointerEvents: 'none',
};

const deckTitleStyle = {
  padding: '0',
  borderRadius: '0',
  border: 'none',
  background: 'linear-gradient(180deg, #ffffff 0%, #d9ecff 48%, #89c0ff 100%)',
  WebkitBackgroundClip: 'text',
  backgroundClip: 'text',
  WebkitTextFillColor: 'transparent',
  color: 'transparent',
  fontSize: '20px',
  fontWeight: 800,
  letterSpacing: '0.18em',
  textTransform: 'uppercase',
  textShadow: '0 12px 28px rgba(7, 20, 38, 0.36), 0 0 20px rgba(111, 182, 255, 0.22)',
  fontFamily: 'Space Grotesk, Sora, Segoe UI Variable Display, Bahnschrift, Microsoft YaHei UI, sans-serif',
  whiteSpace: 'nowrap',
};

const hexToRgb = hex => {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  if (!result) return [0.47, 0.71, 1.0];
  return [parseInt(result[1], 16) / 255, parseInt(result[2], 16) / 255, parseInt(result[3], 16) / 255];
};

const plasmaVertex = `#version 300 es
precision highp float;
in vec2 position;
in vec2 uv;
out vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = vec4(position, 0.0, 1.0);
}
`;

const plasmaFragment = `#version 300 es
precision highp float;
uniform vec2 iResolution;
uniform float iTime;
uniform vec3 uCustomColor;
uniform float uSpeed;
uniform float uScale;
uniform float uOpacity;
uniform vec2 uMouse;
uniform float uMouseInteractive;
out vec4 fragColor;

void mainImage(out vec4 o, vec2 C) {
  vec2 center = iResolution.xy * 0.5;
  C = (C - center) / uScale + center;

  vec2 mouseOffset = (uMouse - center) * 0.00018;
  C += mouseOffset * length(C - center) * step(0.5, uMouseInteractive);

  float i, d, z, T = iTime * uSpeed;
  vec3 O, p, S;

  for (vec2 r = iResolution.xy, Q; ++i < 58.; O += o.w / d * o.xyz) {
    p = z * normalize(vec3(C - .5 * r, r.y));
    p.z -= 4.;
    S = p;
    d = p.y - T;

    p.x += .4 * (1. + p.y) * sin(d + p.x * .1) * cos(.34 * d + p.x * .05);
    Q = p.xz *= mat2(cos(p.y + vec4(0, 11, 33, 0) - T));
    z += d = abs(sqrt(length(Q * Q)) - .25 * (5. + S.y)) / 3. + 8e-4;
    o = 1. + sin(S.y + p.z * .5 + S.z - length(S - p) + vec4(2, 1, 0, 8));
  }

  o.xyz = tanh(O / 1e4);
}

bool finite1(float x){ return !(isnan(x) || isinf(x)); }
vec3 sanitize(vec3 c){
  return vec3(
    finite1(c.r) ? c.r : 0.0,
    finite1(c.g) ? c.g : 0.0,
    finite1(c.b) ? c.b : 0.0
  );
}

void main() {
  vec4 o = vec4(0.0);
  mainImage(o, gl_FragCoord.xy);
  vec3 rgb = sanitize(o.rgb);
  float intensity = (rgb.r + rgb.g + rgb.b) / 3.0;
  vec3 finalColor = mix(rgb, intensity * uCustomColor, 0.86);
  float alpha = length(rgb) * uOpacity;
  fragColor = vec4(finalColor, alpha);
}
`;

export default function FluidGlass({ mode = 'lens', lensProps = {}, barProps = {}, cubeProps = {}, cards = [] }) {
  const Wrapper = mode === 'bar' ? Bar : mode === 'cube' ? Cube : Lens;
  const rawOverrides = mode === 'bar' ? barProps : mode === 'cube' ? cubeProps : lensProps;
  const safeCards = Array.isArray(cards) ? cards.filter(Boolean) : [];
  const sceneCards = [...SCORE_GUIDE_CARDS, ...safeCards];
  const deckPages = Math.max(3, Math.ceil(sceneCards.length / 4) + 1);

  const {
    navItems = (safeCards.length
      ? safeCards.map(item => ({ label: item.name || 'AI Service', link: '' }))
      : [
        { label: 'Home', link: '' },
        { label: 'About', link: '' },
        { label: 'Contact', link: '' },
      ]),
    ...modeProps
  } = rawOverrides;

  return (
    <div style={deckShellStyle}>
      <PlasmaBackground
        color="#5f9fff"
        speed={0.62}
        scale={1.04}
        opacity={0.95}
        mouseInteractive
      />
      <div style={deckCanvasStyle}>
        <Canvas camera={{ position: [0, 0, 20], fov: 15 }} gl={{ alpha: true }}>
          <ScrollControls damping={0.2} pages={deckPages} distance={0.4}>
            {mode === 'bar' && <NavItems items={navItems} />}
            <Wrapper modeProps={modeProps}>
              <Scroll>
                <Images cards={sceneCards} />
              </Scroll>
              <Scroll html>
                <FloatingDeckTitle />
              </Scroll>
              <Preload />
            </Wrapper>
          </ScrollControls>
        </Canvas>
      </div>
    </div>
  );
}

function FloatingDeckTitle() {
  const data = useScroll();
  const titleRef = useRef(null);

  useFrame(() => {
    if (!titleRef.current) return;
    const progress = Math.max(0, Math.min(1, data.offset * 1.8));
    const scale = 1.15 - progress * 0.1;
    const translateY = 8 + progress * 120;
    const opacity = 1 - Math.max(0, (data.offset - 0.58) * 2.6);
    titleRef.current.style.transform = `translate3d(-50%, ${translateY}px, 0) scale(${scale})`;
    titleRef.current.style.opacity = String(Math.max(0, Math.min(1, opacity)));
  });

  return (
    <div style={deckTitleWrapStyle}>
      <div ref={titleRef} style={{ ...deckTitleInnerStyle, ...deckTitleStyle }}>
        AI服务评级
      </div>
    </div>
  );
}

const ModeWrapper = memo(function ModeWrapper({
  children,
  glb,
  geometryKey,
  lockToBottom = false,
  followPointer = true,
  modeProps = {},
  ...props
}) {
  const ref = useRef();
  const { nodes } = useGLTF(glb);
  const buffer = useFBO();
  const { viewport: vp } = useThree();
  const [scene] = useState(() => new THREE.Scene());
  const geoWidthRef = useRef(1);

  useEffect(() => {
    const geo = nodes?.[geometryKey]?.geometry;
    if (!geo) return;
    geo.computeBoundingBox();
    geoWidthRef.current = geo.boundingBox?.max?.x - geo.boundingBox?.min?.x || 1;
  }, [nodes, geometryKey]);

  useFrame((state, delta) => {
    if (!ref.current) return;
    const { gl, viewport, pointer, camera } = state;
    const v = viewport.getCurrentViewport(camera, [0, 0, 15]);

    const destX = followPointer ? (pointer.x * v.width) / 2 : 0;
    const destY = lockToBottom ? -v.height / 2 + 0.2 : followPointer ? (pointer.y * v.height) / 2 : 0;
    easing.damp3(ref.current.position, [destX, destY, 15], 0.15, delta);

    if (modeProps.scale == null) {
      const maxWorld = v.width * 0.9;
      const desired = maxWorld / geoWidthRef.current;
      ref.current.scale.setScalar(Math.min(0.15, desired));
    }

    gl.setRenderTarget(buffer);
    gl.render(scene, camera);
    gl.setRenderTarget(null);

  });

  const { scale, ior, thickness, anisotropy, chromaticAberration, ...extraMat } = modeProps;

  return (
    <>
      {createPortal(children, scene)}
      <mesh scale={[vp.width, vp.height, 1]}>
        <planeGeometry />
        <meshBasicMaterial map={buffer.texture} transparent />
      </mesh>
      <mesh ref={ref} scale={scale ?? 0.15} rotation-x={Math.PI / 2} geometry={nodes?.[geometryKey]?.geometry} {...props}>
        <MeshTransmissionMaterial
          buffer={buffer.texture}
          ior={ior ?? 1.15}
          thickness={thickness ?? 5}
          anisotropy={anisotropy ?? 0.01}
          chromaticAberration={chromaticAberration ?? 0.1}
          {...extraMat}
        />
      </mesh>
    </>
  );
});

function Lens({ modeProps, ...p }) {
  return <ModeWrapper glb="/assets/3d/lens.glb" geometryKey="Cylinder" followPointer modeProps={modeProps} {...p} />;
}

function Cube({ modeProps, ...p }) {
  return <ModeWrapper glb="/assets/3d/cube.glb" geometryKey="Cube" followPointer modeProps={modeProps} {...p} />;
}

function Bar({ modeProps = {}, ...p }) {
  const defaultMat = {
    transmission: 1,
    roughness: 0,
    thickness: 10,
    ior: 1.15,
    color: '#ffffff',
    attenuationColor: '#ffffff',
    attenuationDistance: 0.25,
  };

  return (
    <ModeWrapper
      glb="/assets/3d/bar.glb"
      geometryKey="Cube"
      lockToBottom
      followPointer={false}
      modeProps={{ ...defaultMat, ...modeProps }}
      {...p}
    />
  );
}

function NavItems({ items }) {
  const group = useRef();
  const { viewport, camera } = useThree();

  const DEVICE = {
    mobile: { max: 639, spacing: 0.2, fontSize: 0.035 },
    tablet: { max: 1023, spacing: 0.24, fontSize: 0.035 },
    desktop: { max: Infinity, spacing: 0.3, fontSize: 0.035 },
  };
  const getDevice = () => {
    const w = window.innerWidth;
    return w <= DEVICE.mobile.max ? 'mobile' : w <= DEVICE.tablet.max ? 'tablet' : 'desktop';
  };

  const [device, setDevice] = useState(getDevice());

  useEffect(() => {
    const onResize = () => setDevice(getDevice());
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const { spacing, fontSize } = DEVICE[device];

  useFrame(() => {
    if (!group.current) return;
    const v = viewport.getCurrentViewport(camera, [0, 0, 15]);
    group.current.position.set(0, -v.height / 2 + 0.2, 15.1);

    group.current.children.forEach((child, i) => {
      child.position.x = (i - (items.length - 1) / 2) * spacing;
    });
  });

  const handleNavigate = link => {
    if (!link) return;
    link.startsWith('#') ? (window.location.hash = link) : (window.location.href = link);
  };

  return (
    <group ref={group} renderOrder={10}>
      {items.map(({ label, link }) => (
        <Text
          key={label}
          fontSize={fontSize}
          color="white"
          anchorX="center"
          anchorY="middle"
          depthWrite={false}
          outlineWidth={0}
          outlineBlur="20%"
          outlineColor="#000"
          outlineOpacity={0.5}
          depthTest={false}
          renderOrder={10}
          onClick={e => {
            e.stopPropagation();
            handleNavigate(link);
          }}
          onPointerOver={() => {
            document.body.style.cursor = 'pointer';
          }}
          onPointerOut={() => {
            document.body.style.cursor = 'auto';
          }}
        >
          {label}
        </Text>
      ))}
    </group>
  );
}

function Images({ cards }) {
  const group = useRef();
  const data = useScroll();
  const { height } = useThree(s => s.viewport);

  const sceneCards = Array.isArray(cards) ? cards.filter(Boolean) : [];
  const displayCount = sceneCards.length;
  const slots = createImageSlots(displayCount, height);

  useFrame(() => {
    if (!group.current || !group.current.children?.length) return;
    const children = group.current.children;
    for (let i = 0; i < children.length; i += 1) {
      const node = children[i];
      if (!node?.material || typeof node.material.zoom !== 'number') continue;
      const band = (i % 6) / 6;
      const span = 1 / 3;
      const zoom = 1 + data.range(band, span) * 0.09;
      node.material.zoom = zoom;
      const baseY = Number(node.userData?.baseY || 0);
      const drift = Number(node.userData?.drift || 0);
      const cinematicLift = data.offset * (2.9 + (drift % 4) * 0.14);
      node.position.y = baseY + cinematicLift;
    }
  });

  const serviceUrls = sceneCards.map((item, index) => {
    return buildAiArt(item, index);
  });
  const pickUrl = index => serviceUrls[index] || '/favicon.svg';

  return (
    <group ref={group}>
      {slots.map((slot, index) => (
        <Image
          key={`img-${index}`}
          position={slot.position}
          scale={slot.scale}
          url={pickUrl(index)}
          userData={{ baseY: slot.position[1], drift: index }}
        />
      ))}
    </group>
  );
}

function createImageSlots(count, height) {
  if (!count) return [];
  const columns = count <= 6 ? 3 : 4;
  const cardWidth = 1.74;
  const cardHeight = 2.38;
  const stepX = columns === 3 ? 2.14 : 1.92;
  const stepY = Math.max(1.36, height * 0.52);
  const topStart = -2.62;
  const slots = [];
  for (let i = 0; i < count; i += 1) {
    const row = Math.floor(i / columns);
    const col = i % columns;
    const waveX = Math.sin(i * 1.17) * 0.24 + (row % 2 === 0 ? 0.15 : -0.12);
    const waveY = Math.cos(i * 0.83) * 0.12;
    const waveZ = Math.sin(i * 0.71) * 0.42;
    const x = (col - (columns - 1) / 2) * stepX + waveX;
    const y = topStart - (row * stepY) + waveY;
    const z = 7.5 + waveZ + row * 0.18;
    const sx = cardWidth + Math.sin(i * 0.49) * 0.07;
    const sy = cardHeight + Math.cos(i * 0.37) * 0.1;
    slots.push({ position: [x, y, z], scale: [sx, sy, 1] });
  }
  return slots;
}

function buildAiArt(card, index) {
  if (String(card?.kind || '').toLowerCase() === 'dimension-guide') {
    return buildGuideCardSvg(card, index);
  }

  return buildServiceCardSvg(card, index);
}

function buildServiceCardSvg(card, index) {
  const name = String(card?.name || `AI-${index + 1}`).trim();
  const provider = String(card?.provider || 'Unknown Provider').trim();
  const risk = String(card?.risk_level || card?.riskLevel || 'medium').toLowerCase();
  const category = String(card?.category || 'general').toLowerCase();
  const score = Number(card?.total_risk_score || card?.riskScore || 0);
  const tags = Array.isArray(card?.tags)
    ? card.tags.filter(Boolean).map(tag => String(tag).trim()).slice(0, 3)
    : [];
  const description = String(card?.description || 'No description').trim();
  const scoreRows = card?.scores && typeof card.scores === 'object'
    ? Object.entries(card.scores)
      .slice(0, 3)
      .map(([key, value]) => {
        const dim = value && typeof value === 'object' ? value : {};
        const dimValue = Number(dim.value || 0);
        const dimMax = Math.max(1, Number(dim.max || 100));
        return `${prettyDimKey(key)} ${Math.round((dimValue / dimMax) * 100)}%`;
      })
    : [];

  const systemPalette = {
    high: ['#0b2748', '#102741'],
    medium: ['#0a233d', '#112a46'],
    low: ['#0a2841', '#10304f'],
  };
  const accent = riskAccent(risk);
  const categoryHue = category.includes('chat') || category.includes('llm') ? '#6acbff'
    : category.includes('vision') || category.includes('image') ? '#8ea4ff'
      : category.includes('coding') || category.includes('code') ? '#78e8d2'
        : '#9fc8ff';
  const [bgA, bgB] = systemPalette[risk] || systemPalette.medium;
  const riskText = risk === 'high' ? '高风险' : risk === 'low' ? '低风险' : '中风险';
  const cardIcon = pickCardIcon(card, index);
  const nameLines = splitSvgLines(name, 13, 3);
  const providerLines = splitSvgLines(provider, 22, 2);
  const descLines = splitSvgLines(description, 26, 3);
  const tagLine = tags.length ? tags.join(' | ') : 'No tags';
  const metricLines = scoreRows.length ? scoreRows : [`Category ${category || 'general'}`];
  const scoreBarWidth = Math.max(40, Math.min(760, Math.round((Math.max(0, Math.min(100, score)) / 100) * 760)));
  const metricText = splitSvgLines(metricLines.join('  ·  '), 42, 2);

  const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1000 1400'>
<defs>
<linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>
<stop offset='0%' stop-color='${bgA}'/>
<stop offset='100%' stop-color='${bgB}'/>
</linearGradient>
<linearGradient id='fg' x1='0' y1='0' x2='0' y2='1'>
<stop offset='0%' stop-color='#f9fcff'/>
<stop offset='100%' stop-color='#d7e8ff'/>
</linearGradient>
<linearGradient id='ag' x1='0' y1='0' x2='1' y2='1'>
<stop offset='0%' stop-color='${accent}' stop-opacity='0.92'/>
<stop offset='100%' stop-color='${categoryHue}' stop-opacity='0.84'/>
</linearGradient>
</defs>
<rect width='1000' height='1400' fill='url(#g)'/>
<rect x='0' y='0' width='1000' height='1400' fill='url(#ag)' opacity='0.11'/>
<circle cx='804' cy='232' r='248' fill='${categoryHue}' opacity='0.24'/>
<circle cx='196' cy='1206' r='288' fill='${accent}' opacity='0.16'/>
<path d='M62 172 C240 62, 468 282, 936 102' stroke='rgba(188,225,255,0.22)' stroke-width='3' fill='none'/>
<path d='M74 244 C292 122, 514 362, 948 184' stroke='rgba(148,205,255,0.22)' stroke-width='2' fill='none'/>
<path d='M82 316 H918 M82 384 H918 M82 452 H918' stroke='rgba(176,215,255,0.12)' stroke-width='1'/>
<rect x='58' y='58' width='884' height='1284' rx='48' fill='rgba(4,15,34,0.34)' stroke='rgba(167,212,255,.34)'/>
<rect x='80' y='82' width='840' height='1240' rx='36' fill='none' stroke='rgba(197,229,255,0.24)'/>
<circle cx='856' cy='154' r='54' fill='rgba(10,29,52,0.66)' stroke='rgba(208,234,255,.46)'/>
<text x='856' y='172' text-anchor='middle' fill='rgba(244,250,255,.96)' font-size='46' font-family='Segoe UI Emoji, Segoe UI Symbol, Segoe UI, Microsoft YaHei UI, sans-serif'>${escapeXml(cardIcon)}</text>
<text x='102' y='148' fill='rgba(238,248,255,.9)' font-size='30' font-family='Segoe UI, Microsoft YaHei UI, sans-serif' letter-spacing='2'>AI SERVICE PROFILE</text>
${renderSvgLines(nameLines, 102, 242, 70, 64, 'url(#fg)', '780')}
${renderSvgLines(providerLines, 102, 450, 44, 36, 'rgba(241,248,255,.92)', '580')}
<text x='102' y='552' fill='rgba(230,243,255,.94)' font-size='38' font-family='Segoe UI, Microsoft YaHei UI, sans-serif'>${escapeXml(category || 'general')} · ${escapeXml(riskText)}</text>
<text x='102' y='650' fill='rgba(235,246,255,.92)' font-size='50' font-family='Segoe UI, Microsoft YaHei UI, sans-serif'>Risk Score</text>
<rect x='102' y='628' width='760' height='22' rx='11' fill='rgba(179,214,255,0.22)'/>
<rect x='102' y='628' width='${scoreBarWidth}' height='22' rx='11' fill='url(#ag)'/>
<text x='878' y='648' text-anchor='end' fill='rgba(244,250,255,.96)' font-size='64' font-family='Segoe UI, Microsoft YaHei UI, sans-serif' font-weight='780'>${Math.max(0, Math.min(100, Math.round(score)))}</text>
${renderSvgLines(metricText, 102, 756, 46, 34, 'rgba(228,242,255,.95)', '620')}
${renderSvgLines(descLines, 102, 928, 46, 34, 'rgba(214,234,255,.92)', '540')}
<rect x='102' y='1148' width='796' height='78' rx='18' fill='rgba(9,24,44,0.52)' stroke='rgba(179,219,255,.3)'/>
<text x='122' y='1198' fill='rgba(225,240,255,.94)' font-size='32' font-family='Segoe UI, Microsoft YaHei UI, sans-serif'>${escapeXml(tagLine)}</text>
</svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

function buildGuideCardSvg(card, index) {
  const name = String(card?.name || '评分维度').trim();
  const provider = String(card?.provider || '治理说明').trim();
  const description = String(card?.description || '').trim();
  const tags = Array.isArray(card?.tags) ? card.tags.filter(Boolean).map(tag => String(tag).trim()) : [];
  const icon = pickCardIcon(card, index);
  const nameLines = splitSvgLines(name, 14, 2);
  const descLines = splitSvgLines(description, 28, 3);
  const providerLines = splitSvgLines(provider, 18, 2);
  const tagLine = tags.join('  ·  ') || '评分维度  ·  风险解释';
  const accents = ['#6bc7ff', '#8f9eff', '#73e4cc', '#f6ba6e', '#87b9ff', '#a6d4ff'];
  const tint = accents[index % accents.length];

  const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1000 1400'>
<defs>
<linearGradient id='bg' x1='0' y1='0' x2='1' y2='1'>
<stop offset='0%' stop-color='#153860'/>
<stop offset='100%' stop-color='#0f2948'/>
</linearGradient>
<linearGradient id='halo' x1='0' y1='0' x2='1' y2='1'>
<stop offset='0%' stop-color='${tint}' stop-opacity='0.95'/>
<stop offset='100%' stop-color='#d5ecff' stop-opacity='0.82'/>
</linearGradient>
</defs>
<rect width='1000' height='1400' fill='url(#bg)'/>
<rect x='0' y='0' width='1000' height='1400' fill='url(#halo)' opacity='0.09'/>
<rect x='62' y='62' width='876' height='1276' rx='52' fill='rgba(8,23,43,0.45)' stroke='rgba(174,216,255,0.36)'/>
<rect x='92' y='92' width='816' height='1216' rx='42' fill='none' stroke='rgba(203,230,255,0.28)'/>
<path d='M108 258 H892 M108 324 H892 M108 390 H892 M108 456 H892' stroke='rgba(157,205,255,0.18)' stroke-width='1.5'/>
<path d='M188 112 V1288 M302 112 V1288 M416 112 V1288 M530 112 V1288 M644 112 V1288 M758 112 V1288' stroke='rgba(157,205,255,0.12)' stroke-width='1'/>
<circle cx='846' cy='166' r='62' fill='rgba(11,35,63,0.78)' stroke='rgba(203,232,255,.52)'/>
<text x='846' y='188' text-anchor='middle' fill='rgba(244,250,255,.96)' font-size='52' font-family='Segoe UI Emoji, Segoe UI Symbol, Segoe UI, Microsoft YaHei UI, sans-serif'>${escapeXml(icon)}</text>
<text x='116' y='154' fill='rgba(231,245,255,.9)' font-size='30' font-family='Segoe UI, Microsoft YaHei UI, sans-serif' letter-spacing='2'>RISK DIMENSION GUIDE</text>
${renderSvgLines(nameLines, 116, 250, 70, 62, 'rgba(244,251,255,.98)', '760')}
${renderSvgLines(providerLines, 116, 408, 44, 34, 'rgba(219,236,255,.9)', '620')}
${renderSvgLines(descLines, 116, 560, 50, 36, 'rgba(225,241,255,.95)', '560')}
<rect x='116' y='1116' width='768' height='84' rx='20' fill='rgba(8,27,49,0.54)' stroke='rgba(179,219,255,.32)'/>
<text x='142' y='1171' fill='rgba(225,241,255,.92)' font-size='30' font-family='Segoe UI, Microsoft YaHei UI, sans-serif'>${escapeXml(tagLine)}</text>
</svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

function splitSvgLines(text, maxChars, maxLines) {
  const content = String(text || '').trim();
  if (!content) return ['-'];
  const lines = [];
  let cursor = 0;
  while (cursor < content.length && lines.length < maxLines) {
    const next = content.slice(cursor, cursor + maxChars);
    if (next.length < maxChars || cursor + maxChars >= content.length) {
      lines.push(next);
      cursor += next.length;
      break;
    }
    let cut = Math.max(next.lastIndexOf(' '), next.lastIndexOf('-'));
    if (cut < Math.floor(maxChars * 0.45)) cut = maxChars;
    lines.push(content.slice(cursor, cursor + cut).trim());
    cursor += cut;
    while (content[cursor] === ' ') cursor += 1;
  }
  if (cursor < content.length && lines.length) {
    const last = lines.length - 1;
    lines[last] = `${lines[last].replace(/[. ]+$/g, '')}...`;
  }
  return lines;
}

function renderSvgLines(lines, x, startY, lineHeight, fontSize, fill, weight) {
  return lines
    .map((line, idx) => `<text x='${x}' y='${startY + idx * lineHeight}' fill='${fill}' font-size='${fontSize}' font-family='Segoe UI, Microsoft YaHei UI, sans-serif' font-weight='${weight}'>${escapeXml(line)}</text>`)
    .join('');
}

function prettyDimKey(key) {
  const map = {
    base_risk: '模型固有风险',
    privacy_exposure: '隐私暴露',
    usage_volume: '调用量',
    failure_rate: '失败率',
    latency: '延时',
  };
  return map[String(key || '').toLowerCase()] || String(key || 'dimension');
}

function riskAccent(level) {
  const table = {
    high: '#f87171',
    medium: '#f59e0b',
    low: '#34d399',
  };
  return table[String(level || '').toLowerCase()] || '#74b0ff';
}

function pickCardIcon(card, index) {
  const explicit = String(card?.icon || '').trim();
  if (explicit) return explicit;

  const name = String(card?.name || '').toLowerCase();
  const category = String(card?.category || '').toLowerCase();
  const risk = String(card?.risk_level || card?.riskLevel || '').toLowerCase();

  if (name.includes('deepseek')) return '🧠';
  if (category.includes('chat') || category.includes('llm')) return '💬';
  if (category.includes('coding') || category.includes('code')) return '🧩';
  if (category.includes('vision') || category.includes('image')) return '🖼';
  if (category.includes('search')) return '🧭';
  if (risk === 'high') return '⚠';
  if (risk === 'low') return '✅';

  const fallback = ['🛡', '📡', '🛰', '🔍', '⚙'];
  return fallback[index % fallback.length];
}

function escapeXml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function PlasmaBackground({ color = '#6fb7ff', speed = 0.6, scale = 1.08, opacity = 0.78, mouseInteractive = true }) {
  const containerRef = useRef(null);

  useEffect(() => {
    const containerEl = containerRef.current;
    if (!containerEl) return undefined;

    const renderer = new Renderer({
      webgl: 2,
      alpha: true,
      antialias: false,
      dpr: Math.min(window.devicePixelRatio || 1, 2),
    });
    const gl = renderer.gl;
    const canvas = gl.canvas;
    canvas.style.display = 'block';
    canvas.style.width = '100%';
    canvas.style.height = '100%';
    canvas.style.pointerEvents = 'none';
    containerEl.appendChild(canvas);

    const program = new Program(gl, {
      vertex: plasmaVertex,
      fragment: plasmaFragment,
      uniforms: {
        iTime: { value: 0 },
        iResolution: { value: new Float32Array([1, 1]) },
        uCustomColor: { value: new Float32Array(hexToRgb(color)) },
        uSpeed: { value: speed * 0.4 },
        uScale: { value: scale },
        uOpacity: { value: opacity },
        uMouse: { value: new Float32Array([0, 0]) },
        uMouseInteractive: { value: mouseInteractive ? 1.0 : 0.0 },
      },
    });

    const mesh = new Mesh(gl, { geometry: new Triangle(gl), program });

    const onMouseMove = e => {
      if (!mouseInteractive) return;
      const rect = containerEl.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      const u = program.uniforms.uMouse.value;
      u[0] = x;
      u[1] = y;
    };

    if (mouseInteractive) {
      window.addEventListener('mousemove', onMouseMove, { passive: true });
    }

    const setSize = () => {
      const rect = containerEl.getBoundingClientRect();
      const width = Math.max(1, Math.floor(rect.width));
      const height = Math.max(1, Math.floor(rect.height));
      renderer.setSize(width, height);
      const res = program.uniforms.iResolution.value;
      res[0] = gl.drawingBufferWidth;
      res[1] = gl.drawingBufferHeight;
    };

    const ro = new ResizeObserver(setSize);
    ro.observe(containerEl);
    setSize();

    const t0 = performance.now();
    let raf = 0;

    const loop = now => {
      program.uniforms.iTime.value = (now - t0) * 0.001;
      renderer.render({ scene: mesh });
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);

    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
      if (mouseInteractive) {
        window.removeEventListener('mousemove', onMouseMove);
      }
      if (containerEl.contains(canvas)) {
        containerEl.removeChild(canvas);
      }
    };
  }, [color, speed, scale, opacity, mouseInteractive]);

  return (
    <div
      ref={containerRef}
      style={{
        position: 'absolute',
        inset: 0,
        zIndex: 1,
        overflow: 'hidden',
      }}
    />
  );
}
