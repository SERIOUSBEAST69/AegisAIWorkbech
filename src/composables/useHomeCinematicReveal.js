import { ref } from 'vue';
import gsap from 'gsap';
import CustomEase from 'gsap/CustomEase';

gsap.registerPlugin(CustomEase);
CustomEase.create('hop', '0.9, 0, 0.1, 1');
CustomEase.create('glide', '0.8, 0, 0.2, 1');

export function useHomeCinematicReveal({ stageRef, heroRef, motionTier, prefersReducedMotion, routePathRef }) {
  const cinematicRevealPending = ref(false);
  const cinematicRevealRunning = ref(false);
  const cinematicRevealDone = ref(false);
  const revealOverlayVisible = ref(false);
  const revealMobileLite = ref(false);
  const revealAwaitingClick = ref(false);
  const revealOverlayRef = ref(null);
  const revealBackdropRef = ref(null);
  const revealPreloaderRef = ref(null);
  const revealCoreRef = ref(null);
  const revealLogoBtnRef = ref(null);
  const revealTrackRef = ref(null);
  const revealProgressRef = ref(null);
  let homeRevealTimeline = null;
  let homeRevealOutroTimeline = null;
  let homeTitleRiseTimeline = null;
  let homeRevealBootFinished = false;
  let revealFailSafeTimer = null;
  let revealEnterRequested = false;
  let revealEnteredByClick = false;
  let revealRingTween = null;
  let revealRingStarted = false;
  let revealRingCompleted = false;
  const ringSweepDuration = 0.42;

  function sortLeftToRight(nodes = []) {
    return [...nodes].sort((a, b) => {
      const rectA = a?.getBoundingClientRect?.();
      const rectB = b?.getBoundingClientRect?.();
      const leftA = Number(rectA?.left || 0);
      const leftB = Number(rectB?.left || 0);
      const topA = Number(rectA?.top || 0);
      const topB = Number(rectB?.top || 0);
      if (Math.abs(leftA - leftB) > 6) {
        return leftA - leftB;
      }
      return topA - topB;
    });
  }

  function sortTopLeft(nodes = []) {
    return [...nodes].sort((a, b) => {
      const rectA = a?.getBoundingClientRect?.();
      const rectB = b?.getBoundingClientRect?.();
      const topA = Number(rectA?.top || 0);
      const topB = Number(rectB?.top || 0);
      const leftA = Number(rectA?.left || 0);
      const leftB = Number(rectB?.left || 0);
      if (Math.abs(topA - topB) > 6) {
        return topA - topB;
      }
      return leftA - leftB;
    });
  }

  function shouldUseHomeReveal(routePath) {
    const path = String(routePath || '').trim();
    const pendingFlag = sessionStorage.getItem('aegis.home.reveal.pending') === '1';
    const forcedFlag = sessionStorage.getItem('aegis.home.reveal.force') === '1';
    const transitionFromLogin = sessionStorage.getItem('aegis.transition.origin') === 'login';
    const onHomePath = path === '/' || path === '/home';
    return onHomePath && (pendingFlag || forcedFlag || transitionFromLogin);
  }

  function getRevealTargets() {
    if (!stageRef.value) {
      return { stats: [], ai: [], governance: [] };
    }
    return {
      stats: Array.from(stageRef.value.querySelectorAll('.phase-stats')),
      ai: Array.from(stageRef.value.querySelectorAll('.phase-ai')),
      governance: Array.from(stageRef.value.querySelectorAll('.phase-governance')),
    };
  }

  function completeHomeReveal() {
    if (revealFailSafeTimer) {
      window.clearTimeout(revealFailSafeTimer);
      revealFailSafeTimer = null;
    }
    const groups = getRevealTargets();
    cinematicRevealRunning.value = false;
    cinematicRevealPending.value = false;
    revealAwaitingClick.value = false;
    cinematicRevealDone.value = true;
    revealOverlayVisible.value = false;
    revealEnterRequested = false;
    sessionStorage.removeItem('aegis.home.reveal.pending');
    sessionStorage.removeItem('aegis.home.reveal.force');
    const all = [...groups.stats, ...groups.ai, ...groups.governance];
    if (all.length) {
      gsap.set(all, { clearProps: 'opacity,transform,filter,pointerEvents' });
    }
  }

  function playTitleRiseAfterEnter() {
    const heroTitle = stageRef.value?.querySelector('.hero-headline');
    if (!heroTitle) {
      return;
    }
    const heroCharMasks = sortTopLeft(Array.from(heroTitle.querySelectorAll('.hero-char-mask')));
    const heroChars = sortTopLeft(Array.from(heroTitle.querySelectorAll('.hero-char')));
    if (!heroCharMasks.length || !heroChars.length) {
      return;
    }

    if (homeTitleRiseTimeline) {
      homeTitleRiseTimeline.kill();
      homeTitleRiseTimeline = null;
    }

    gsap.killTweensOf([heroTitle, ...heroCharMasks, ...heroChars]);
    gsap.set(heroTitle, { opacity: 1, filter: 'blur(0px)' });
    gsap.set(heroCharMasks, { opacity: 0, y: 62, filter: 'blur(3px)' });
    gsap.set(heroChars, { opacity: 0, filter: 'blur(2px)' });

    homeTitleRiseTimeline = gsap.timeline({
      delay: 0.12,
      onComplete: () => {
        homeTitleRiseTimeline = null;
      },
      onInterrupt: () => {
        homeTitleRiseTimeline = null;
      },
    });

    homeTitleRiseTimeline
      .to(heroCharMasks, {
        opacity: 1,
        y: 0,
        filter: 'blur(0px)',
        duration: 0.84,
        stagger: 0.07,
        ease: 'power2.out',
      }, 0)
      .to(heroChars, {
        opacity: 1,
        filter: 'blur(0px)',
        duration: 0.5,
        stagger: 0.062,
        ease: 'power2.out',
      }, 0.18);
  }

  function finalizeRevealAfterClick() {
    completeHomeReveal();
    window.requestAnimationFrame(() => {
      playTitleRiseAfterEnter();
    });
    revealEnteredByClick = false;
  }

  function revealMainScene() {
    if (!cinematicRevealPending.value || cinematicRevealDone.value) {
      return;
    }
    const groups = getRevealTargets();
    const stats = groups.stats;
    const ai = groups.ai;
    const governance = groups.governance;
    const heroEyebrow = stageRef.value?.querySelector('.hero-copy .eyebrow');
    const heroTitle = stageRef.value?.querySelector('.hero-headline');
    const heroCharMasks = heroTitle ? sortTopLeft(Array.from(heroTitle.querySelectorAll('.hero-char-mask'))) : [];
    const heroChars = heroTitle ? sortTopLeft(Array.from(heroTitle.querySelectorAll('.hero-char'))) : [];
      const heroSubline = stageRef.value?.querySelector('.hero-side-subline');
    const heroTags = sortLeftToRight(Array.from(stageRef.value?.querySelectorAll('.scene-tag') || []));
    const heroOperator = stageRef.value?.querySelector('.operator-ribbon');
    const heroQuickChips = sortLeftToRight(Array.from(stageRef.value?.querySelectorAll('.hero-quick-row span') || []));
    const statsCards = sortLeftToRight(Array.from(stageRef.value?.querySelectorAll('.phase-stats .stat-card') || []));
    const governanceItems = sortLeftToRight(Array.from(stageRef.value?.querySelectorAll('.phase-governance .trace-card, .phase-governance .trace-module-item, .phase-governance .pulse-card, .phase-governance .pulse-signal-item, .phase-governance .chart-card, .phase-governance .module-entry-item, .phase-governance .event-item') || []));

    if (homeRevealOutroTimeline) {
      homeRevealOutroTimeline.kill();
      homeRevealOutroTimeline = null;
    }

    const isLite = revealMobileLite.value;

    // Keep hero container visible; animate title characters directly to avoid parent-child motion overlap.
    gsap.set(heroRef.value, { opacity: 1, y: 0, filter: 'blur(0px)', scale: 1 });
    gsap.set(heroEyebrow, { opacity: 0, y: 8, filter: 'blur(3px)' });
    gsap.set(heroTitle, { opacity: 0, filter: 'blur(4px)' });
    gsap.set(heroCharMasks, { opacity: 0, y: 46, filter: 'blur(3px)' });
    gsap.set(heroChars, { opacity: 0, filter: 'blur(2px)' });
    gsap.set(heroSubline, { opacity: 0, y: 10, filter: 'blur(3px)' });
    gsap.set(heroTags, { opacity: 0, y: 8, filter: 'blur(2px)' });
    gsap.set(heroOperator, { opacity: 0, y: 10, filter: 'blur(2px)' });
    gsap.set(heroQuickChips, { opacity: 0, y: 8, filter: 'blur(2px)' });
    gsap.set(stats, { opacity: 0, y: 22, filter: 'blur(4px)', pointerEvents: 'none' });
    gsap.set(statsCards, { opacity: 0, y: 18, filter: 'blur(3px)' });
    gsap.set(ai, { opacity: 0, y: 24, filter: 'blur(4px)', pointerEvents: 'none' });
    gsap.set(governance, { opacity: 0, y: 26, filter: 'blur(4px)', pointerEvents: 'none' });
    gsap.set(governanceItems, { opacity: 0, y: 20, filter: 'blur(3px)' });

    homeRevealOutroTimeline = gsap.timeline({
      onComplete: () => {
        homeRevealOutroTimeline = null;
        completeHomeReveal();
        if (revealEnteredByClick) {
          revealEnteredByClick = false;
          window.requestAnimationFrame(() => {
            playTitleRiseAfterEnter();
          });
        }
      },
      onInterrupt: () => {
        homeRevealOutroTimeline = null;
        completeHomeReveal();
        if (revealEnteredByClick) {
          revealEnteredByClick = false;
          window.requestAnimationFrame(() => {
            playTitleRiseAfterEnter();
          });
        }
      },
    });

    if (isLite) {
      homeRevealOutroTimeline
        .to(revealLogoBtnRef.value, { scale: 1.08, duration: 0.22, ease: 'power2.out' }, 0)
        .to(revealLogoBtnRef.value, { scale: 0, duration: 0.44, ease: 'hop' }, 0.18)
        .to(revealPreloaderRef.value, { clipPath: 'polygon(0% 0%, 100% 0%, 100% 0%, 0% 0%)', duration: 0.62, ease: 'hop' }, 0.2)
        .to(revealBackdropRef.value, { clipPath: 'polygon(0% 100%, 100% 100%, 100% 100%, 0% 100%)', duration: 0.54, ease: 'hop' }, 0.2)
        .to(heroEyebrow, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.16, ease: 'power1.out' }, 0.39)
        .to(heroSubline, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.16, ease: 'power1.out' }, 0.46)
        .to(heroTags, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.14, stagger: 0.016, ease: 'power1.out' }, 0.48)
        .to(heroOperator, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.15, ease: 'power1.out' }, 0.5)
        .to(heroQuickChips, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.12, stagger: 0.018, ease: 'power1.out' }, 0.52)
        .to(stats, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.14, ease: 'power1.out' }, 0.52)
        .to(statsCards, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.12, stagger: 0.02, ease: 'power1.out' }, 0.54)
        .to(ai, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.14, ease: 'power1.out' }, 0.58)
        .to(governance, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.14, ease: 'power1.out' }, 0.62)
        .to(governanceItems, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.12, stagger: 0.014, ease: 'power1.out' }, 0.64);
      return;
    }

    homeRevealOutroTimeline
      .to(revealLogoBtnRef.value, { scale: 1.1, duration: 0.35, ease: 'power1.out' }, 0)
      .to(revealLogoBtnRef.value, { scale: 0, duration: 0.75, ease: 'hop' }, 0.62)
      .to(revealPreloaderRef.value, { clipPath: 'polygon(0% 0%, 100% 0%, 100% 0%, 0% 0%)', duration: 1.25, ease: 'hop' }, 0.86)
      .to(revealBackdropRef.value, { clipPath: 'polygon(0% 100%, 100% 100%, 100% 100%, 0% 100%)', duration: 1, ease: 'hop' }, 0.86)
      .to(heroEyebrow, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.22, ease: 'power2.out' }, 1.08)
      .to(heroSubline, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.24, ease: 'power2.out' }, 1.44)
      .to(heroTags, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.24, stagger: 0.03, ease: 'power2.out' }, 1.48)
      .to(heroOperator, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.24, ease: 'power2.out' }, 1.52)
      .to(heroQuickChips, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.2, stagger: 0.03, ease: 'power2.out' }, 1.56)
      .to(stats, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.18, ease: 'power2.out' }, 1.6)
      .to(statsCards, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.2, stagger: 0.05, ease: 'power2.out' }, 1.64)
      .to(ai, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.26, ease: 'power2.out' }, 1.82)
      .to(governance, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.22, ease: 'power2.out' }, 1.96)
      .to(governanceItems, { opacity: 1, y: 0, filter: 'blur(0px)', duration: 0.2, stagger: 0.03, ease: 'power2.out' }, 2.02);
  }

  function skipHomeReveal() {
    if (!cinematicRevealPending.value || cinematicRevealDone.value) {
      return;
    }
    revealEnteredByClick = true;
    revealAwaitingClick.value = false;
    revealLogoBtnRef.value?.blur?.();
    revealEnterRequested = true;
    if (homeRevealTimeline) {
      homeRevealTimeline.kill();
      homeRevealTimeline = null;
    }
    if (homeRevealOutroTimeline) {
      homeRevealOutroTimeline.kill();
      homeRevealOutroTimeline = null;
    }
    if (revealRingTween) {
      revealRingTween.kill();
      revealRingTween = null;
    }
    finalizeRevealAfterClick();
  }

  function startRevealRingSweep() {
    const trackNode = revealTrackRef.value;
    const progressNode = revealProgressRef.value;
    if (!trackNode || !progressNode || revealRingStarted || revealRingCompleted) {
      return;
    }
    revealRingStarted = true;
    revealAwaitingClick.value = false;

    if (revealRingTween) {
      revealRingTween.kill();
      revealRingTween = null;
    }

    gsap.killTweensOf(revealLogoBtnRef.value);

    revealRingTween = gsap.timeline({
      onComplete: () => {
        revealRingTween = null;
        revealRingCompleted = true;
        revealAwaitingClick.value = true;
        gsap.to(revealLogoBtnRef.value, {
          scale: 1.03,
          duration: 0.9,
          ease: 'sine.inOut',
          yoyo: true,
          repeat: -1,
        });
      },
      onInterrupt: () => {
        revealRingTween = null;
      },
    });

    revealRingTween
      .to(trackNode, {
        strokeDashoffset: 0,
        duration: ringSweepDuration,
        ease: 'none',
      }, 0)
      .to('.home-pbc-svg-strokes svg', {
        rotation: 360,
        duration: ringSweepDuration,
        ease: 'none',
        transformOrigin: '50% 50%',
      }, 0)
      .to(progressNode, {
        strokeDashoffset: 0,
        duration: ringSweepDuration,
        ease: 'none',
      }, 0);
  }

  function handleRevealLogoHover() {
    if (!cinematicRevealRunning.value || !revealOverlayVisible.value) {
      return;
    }
    if (!homeRevealBootFinished && homeRevealTimeline) {
      homeRevealTimeline.progress(1);
    }
    startRevealRingSweep();
  }

  function playHomeRevealTimeline() {
    if (!cinematicRevealPending.value || cinematicRevealDone.value) {
      return;
    }
    revealRingStarted = false;
    revealRingCompleted = false;
    if (revealRingTween) {
      revealRingTween.kill();
      revealRingTween = null;
    }
    const groups = getRevealTargets();
    const stats = groups.stats;
    const ai = groups.ai;
    const governance = groups.governance;
    const heroEyebrow = stageRef.value?.querySelector('.hero-copy .eyebrow');
    const heroTitle = stageRef.value?.querySelector('.hero-headline');
    const heroChars = heroTitle ? sortTopLeft(Array.from(heroTitle.querySelectorAll('.hero-char'))) : [];
      const heroSubline = stageRef.value?.querySelector('.hero-side-subline');
    const heroTags = sortLeftToRight(Array.from(stageRef.value?.querySelectorAll('.scene-tag') || []));
    const heroOperator = stageRef.value?.querySelector('.operator-ribbon');
    const heroQuickChips = sortLeftToRight(Array.from(stageRef.value?.querySelectorAll('.hero-quick-row span') || []));
    const statsCards = sortLeftToRight(Array.from(stageRef.value?.querySelectorAll('.phase-stats .stat-card') || []));
    const governanceItems = sortLeftToRight(Array.from(stageRef.value?.querySelectorAll('.phase-governance .trace-card, .phase-governance .trace-module-item, .phase-governance .pulse-card, .phase-governance .pulse-signal-item, .phase-governance .chart-card, .phase-governance .module-entry-item, .phase-governance .event-item') || []));

    if (!stats.length && !ai.length && !governance.length) {
      completeHomeReveal();
      return;
    }

    const isMobile = window.innerWidth <= 900;
    const useLite = isMobile || prefersReducedMotion.value || motionTier.value === 'low';
    revealMobileLite.value = useLite;
    cinematicRevealRunning.value = true;
    revealOverlayVisible.value = true;
    revealAwaitingClick.value = false;
    homeRevealBootFinished = false;
    revealEnterRequested = false;
    revealEnteredByClick = false;

    if (revealFailSafeTimer) {
      window.clearTimeout(revealFailSafeTimer);
      revealFailSafeTimer = null;
    }
    // Fail-safe: never auto-enter before click; only force UI into clickable ready state.
    revealFailSafeTimer = window.setTimeout(() => {
      if (cinematicRevealRunning.value && !revealAwaitingClick.value && !revealEnterRequested) {
        if (homeRevealTimeline) {
          homeRevealTimeline.progress(1);
        } else {
          revealAwaitingClick.value = true;
        }
      }
    }, 8000);

    if (homeRevealTimeline) {
      homeRevealTimeline.kill();
      homeRevealTimeline = null;
    }
    if (homeRevealOutroTimeline) {
      homeRevealOutroTimeline.kill();
      homeRevealOutroTimeline = null;
    }

    gsap.set(revealOverlayRef.value, { opacity: 1, pointerEvents: 'auto' });
    gsap.set(revealBackdropRef.value, {
      opacity: 1,
      clipPath: 'polygon(0% 0%, 100% 0%, 100% 100%, 0% 100%)',
    });
    gsap.set(revealPreloaderRef.value, {
      opacity: 1,
      clipPath: 'polygon(0% 0%, 100% 0%, 100% 100%, 0% 100%)',
    });
    gsap.set(revealCoreRef.value, { opacity: 1, scale: useLite ? 0.96 : 0.9, y: useLite ? 8 : 14 });
    gsap.set(revealLogoBtnRef.value, { scale: useLite ? 0.98 : 0.92, rotation: 0 });

    const preloaderLines = Array.from(revealOverlayRef.value?.querySelectorAll('.home-preloader-line') || []);
    gsap.set(preloaderLines, { yPercent: 100 });

    const trackNode = revealTrackRef.value;
    const progressNode = revealProgressRef.value;
    let svgPathLength = 0;
    if (trackNode && progressNode && typeof trackNode.getTotalLength === 'function') {
      svgPathLength = trackNode.getTotalLength();
      gsap.set([trackNode, progressNode], {
        strokeDasharray: svgPathLength,
        strokeDashoffset: svgPathLength,
      });
      gsap.set('.home-pbc-svg-strokes svg', { rotation: 0, transformOrigin: '50% 50%' });
    }

    const tl = gsap.timeline({
      onComplete: () => {
        homeRevealTimeline = null;
        homeRevealBootFinished = true;
        if (revealEnterRequested) {
          revealEnterRequested = false;
          revealAwaitingClick.value = false;
          gsap.killTweensOf(revealLogoBtnRef.value);
          revealMainScene();
          return;
        }
        revealAwaitingClick.value = false;
      },
      onInterrupt: () => {
        homeRevealTimeline = null;
        homeRevealBootFinished = true;
        if (revealEnterRequested) {
          revealEnterRequested = false;
          revealAwaitingClick.value = false;
          gsap.killTweensOf(revealLogoBtnRef.value);
          revealMainScene();
          return;
        }
        revealAwaitingClick.value = false;
      },
    });

    if (useLite) {
      tl.to(preloaderLines, {
        yPercent: 0,
        duration: 0.42,
        ease: 'power2.out',
        stagger: 0.04,
      }, 0.04)
        .to(revealCoreRef.value, { opacity: 1, scale: 1, y: 0, duration: 0.22, ease: 'power2.out' }, 0)
        .to(revealLogoBtnRef.value, { scale: 1, rotation: 0, duration: 0.24, ease: 'power2.out' }, 0.02);
    } else {
      tl.to(preloaderLines, {
        yPercent: 0,
        duration: 0.75,
        ease: 'power3.out',
        stagger: 0.1,
      }, 0);

      tl.to(revealCoreRef.value, { opacity: 1, scale: 1, y: 0, duration: 0.42, ease: 'expo.out' }, '<')
        .to(revealLogoBtnRef.value, { scale: 1, rotation: 0, duration: 0.44, ease: 'power3.out' }, '<')
    }

    homeRevealTimeline = tl;
  }

  function playEntryScene() {
    if (!stageRef.value) return;
    const blocks = sortLeftToRight(Array.from(stageRef.value.querySelectorAll('.scene-block')));
    const cinematicEntry = sessionStorage.getItem('aegis.transition.origin') === 'login';
    const routePath = routePathRef?.value || '';

    if (!cinematicRevealPending.value && cinematicEntry && shouldUseHomeReveal(routePath)) {
      cinematicRevealPending.value = true;
      revealOverlayVisible.value = true;
    }

    sessionStorage.removeItem('aegis.transition.origin');

    if (cinematicRevealPending.value) {
      gsap.set(blocks, { clearProps: 'opacity,transform,filter,pointerEvents' });
      playHomeRevealTimeline();
      return;
    }

    if (cinematicEntry || prefersReducedMotion.value || motionTier.value === 'low') {
      gsap.set(blocks, { opacity: 1, y: 0 });
      return;
    }

    const duration = motionTier.value === 'high' ? 0.28 : 0.2;
    const stagger = motionTier.value === 'high' ? 0.045 : 0.03;
    gsap.set(blocks, { opacity: 0, y: 8 });
    gsap.to(heroRef.value, { opacity: 1, y: 0, duration, ease: 'power1.out' });
    gsap.to(sortLeftToRight(blocks.filter(block => block !== heroRef.value)), {
      opacity: 1,
      y: 0,
      duration: Math.max(0.14, duration - 0.08),
      stagger,
      ease: 'power1.out',
    });
  }

  function initHomeRevealPending() {
    const routePath = routePathRef?.value || '';
    const revealRequested = shouldUseHomeReveal(routePath);
    if (revealRequested) {
      cinematicRevealPending.value = true;
      revealOverlayVisible.value = true;
      revealAwaitingClick.value = false;
    }
  }

  function disposeHomeReveal() {
    if (revealFailSafeTimer) {
      window.clearTimeout(revealFailSafeTimer);
      revealFailSafeTimer = null;
    }
    if (homeRevealTimeline) {
      homeRevealTimeline.kill();
      homeRevealTimeline = null;
    }
    if (revealRingTween) {
      revealRingTween.kill();
      revealRingTween = null;
    }
    revealRingStarted = false;
    revealRingCompleted = false;
    if (homeRevealOutroTimeline) {
      homeRevealOutroTimeline.kill();
      homeRevealOutroTimeline = null;
    }
    if (homeTitleRiseTimeline) {
      homeTitleRiseTimeline.kill();
      homeTitleRiseTimeline = null;
    }
    homeRevealBootFinished = false;
    revealEnteredByClick = false;
  }

  return {
    cinematicRevealPending,
    cinematicRevealRunning,
    revealOverlayVisible,
    revealMobileLite,
    revealAwaitingClick,
    revealOverlayRef,
    revealBackdropRef,
    revealPreloaderRef,
    revealCoreRef,
    revealLogoBtnRef,
    revealTrackRef,
    revealProgressRef,
    handleRevealLogoHover,
    skipHomeReveal,
    playEntryScene,
    initHomeRevealPending,
    disposeHomeReveal,
  };
}
