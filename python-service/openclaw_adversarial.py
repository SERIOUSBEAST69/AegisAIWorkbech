#!/usr/bin/env python3
"""
OpenClaw 攻防对弈引擎 (Adversarial Battle Engine)
==================================================
基于攻防博弈思路的自主AI代理对抗仿真系统。
与工作台（openclaw_simulator.py）完全分离，独立运行。

设计理念：
  攻击方：自主AI代理，模拟APT级别的AI系统攻击手段
    - 供应链投毒（Supply Chain Poisoning）
    - 记忆篡改（Memory Tampering）
    - 数据窃取与隐写（Data Exfiltration / Steganography）
    - 提示注入（Prompt Injection）
    - 模型投毒（Model Poisoning）
    - 决策漂移（Decision Drift）
    - 凭据收割（Credential Harvesting）
    - 影子部署（Shadow Deployment）
    - 重放攻击（Replay Attack）
    - 上下文操纵（Context Manipulation）

  防御方：AegisAI防护体系，多层纵深防御
    - 插件沙箱隔离（Sandbox Isolation）
    - 指令-数据隔离（Input Sanitization）
    - 记忆防火墙（Memory Firewall）
    - 行为监控（Behavior Monitor）
    - 行为熔断（Circuit Breaker）
    - DLP引擎（Data Leakage Prevention）
    - 供应链审计（Supply Chain Audit）
    - 人工督导（Human Oversight）
    - 不可变基础设施（Immutable Infrastructure）
    - 决策对齐监控（Decision Alignment Monitoring）

用法：
    python openclaw_adversarial.py [--rounds N] [--seed SEED] [--report FILE]
    python openclaw_adversarial.py --scenario stealth_exfil
    python openclaw_adversarial.py --list-scenarios

参数：
    --rounds N        对弈轮数（默认 10）
    --seed SEED       随机种子（用于复现）
    --report FILE     输出详细报告到 JSON 文件
    --scenario NAME   运行特定攻击场景
    --list-scenarios  列出所有内置场景
    --verbose         详细输出每一步决策
"""

from __future__ import annotations

import argparse
import json
import math
import random
import sys
import time
from dataclasses import dataclass, field, asdict
from datetime import datetime
from enum import Enum
from typing import Dict, List, Optional, Tuple


# ─────────────────────── 攻击策略枚举 ───────────────────────────────────────

class AttackStrategy(str, Enum):
    """攻击方可用的策略集合"""
    SUPPLY_CHAIN_POISON   = "supply_chain_poison"    # 供应链投毒
    MEMORY_TAMPER         = "memory_tamper"           # 记忆篡改
    DATA_EXFIL_STEG       = "data_exfil_steg"         # 数据隐写渗出
    PROMPT_INJECTION      = "prompt_injection"         # 提示注入
    MODEL_POISONING       = "model_poisoning"          # 模型投毒
    DECISION_DRIFT        = "decision_drift"           # 决策漂移
    CREDENTIAL_HARVEST    = "credential_harvest"       # 凭据收割
    SHADOW_DEPLOYMENT     = "shadow_deployment"        # 影子AI部署
    REPLAY_ATTACK         = "replay_attack"            # 重放攻击
    CONTEXT_MANIPULATION  = "context_manipulation"     # 上下文操纵


class DefenseStrategy(str, Enum):
    """防御方可用的策略集合"""
    SANDBOX_ISOLATION   = "sandbox_isolation"    # 插件沙箱隔离
    INPUT_SANITIZER     = "input_sanitizer"      # 输入过滤净化
    MEMORY_FIREWALL     = "memory_firewall"      # 记忆防火墙
    BEHAVIOR_MONITOR    = "behavior_monitor"     # 行为监控
    CIRCUIT_BREAKER     = "circuit_breaker"      # 行为熔断
    DLP_ENGINE          = "dlp_engine"           # 数据防泄漏
    SUPPLY_CHAIN_AUDIT  = "supply_chain_audit"   # 供应链审计
    HUMAN_OVERSIGHT     = "human_oversight"      # 人工督导
    IMMUTABLE_INFRA     = "immutable_infra"      # 不可变基础设施
    DECISION_ALIGNMENT  = "decision_alignment"   # 决策对齐监控


# ─────────────────── 攻防效果矩阵（攻击 vs 防御） ──────────────────────────
#
# 矩阵含义：attack_effectiveness[attack][defense] = 攻击突破率 (0.0 ~ 1.0)
# 1.0 = 防御无效，0.0 = 完全阻断
# 设计原则：克制关系体现在矩阵中，例如供应链审计能有效应对供应链投毒

EFFECTIVENESS_MATRIX: Dict[AttackStrategy, Dict[DefenseStrategy, float]] = {
    AttackStrategy.SUPPLY_CHAIN_POISON: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.30,
        DefenseStrategy.INPUT_SANITIZER:    0.60,
        DefenseStrategy.MEMORY_FIREWALL:    0.70,
        DefenseStrategy.BEHAVIOR_MONITOR:   0.50,
        DefenseStrategy.CIRCUIT_BREAKER:    0.40,
        DefenseStrategy.DLP_ENGINE:         0.65,
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.10,  # 强克制
        DefenseStrategy.HUMAN_OVERSIGHT:    0.25,
        DefenseStrategy.IMMUTABLE_INFRA:    0.15,
        DefenseStrategy.DECISION_ALIGNMENT: 0.55,
    },
    AttackStrategy.MEMORY_TAMPER: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.55,
        DefenseStrategy.INPUT_SANITIZER:    0.45,
        DefenseStrategy.MEMORY_FIREWALL:    0.05,  # 强克制
        DefenseStrategy.BEHAVIOR_MONITOR:   0.40,
        DefenseStrategy.CIRCUIT_BREAKER:    0.35,
        DefenseStrategy.DLP_ENGINE:         0.60,
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.70,
        DefenseStrategy.HUMAN_OVERSIGHT:    0.20,
        DefenseStrategy.IMMUTABLE_INFRA:    0.10,
        DefenseStrategy.DECISION_ALIGNMENT: 0.30,
    },
    AttackStrategy.DATA_EXFIL_STEG: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.40,
        DefenseStrategy.INPUT_SANITIZER:    0.35,
        DefenseStrategy.MEMORY_FIREWALL:    0.55,
        DefenseStrategy.BEHAVIOR_MONITOR:   0.25,
        DefenseStrategy.CIRCUIT_BREAKER:    0.45,
        DefenseStrategy.DLP_ENGINE:         0.10,  # 强克制
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.60,
        DefenseStrategy.HUMAN_OVERSIGHT:    0.30,
        DefenseStrategy.IMMUTABLE_INFRA:    0.55,
        DefenseStrategy.DECISION_ALIGNMENT: 0.50,
    },
    AttackStrategy.PROMPT_INJECTION: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.35,
        DefenseStrategy.INPUT_SANITIZER:    0.08,  # 强克制
        DefenseStrategy.MEMORY_FIREWALL:    0.50,
        DefenseStrategy.BEHAVIOR_MONITOR:   0.40,
        DefenseStrategy.CIRCUIT_BREAKER:    0.30,
        DefenseStrategy.DLP_ENGINE:         0.55,
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.65,
        DefenseStrategy.HUMAN_OVERSIGHT:    0.20,
        DefenseStrategy.IMMUTABLE_INFRA:    0.60,
        DefenseStrategy.DECISION_ALIGNMENT: 0.25,
    },
    AttackStrategy.MODEL_POISONING: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.30,
        DefenseStrategy.INPUT_SANITIZER:    0.50,
        DefenseStrategy.MEMORY_FIREWALL:    0.45,
        DefenseStrategy.BEHAVIOR_MONITOR:   0.35,
        DefenseStrategy.CIRCUIT_BREAKER:    0.40,
        DefenseStrategy.DLP_ENGINE:         0.55,
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.15,  # 强克制
        DefenseStrategy.HUMAN_OVERSIGHT:    0.20,
        DefenseStrategy.IMMUTABLE_INFRA:    0.25,
        DefenseStrategy.DECISION_ALIGNMENT: 0.10,  # 强克制
    },
    AttackStrategy.DECISION_DRIFT: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.60,
        DefenseStrategy.INPUT_SANITIZER:    0.40,
        DefenseStrategy.MEMORY_FIREWALL:    0.35,
        DefenseStrategy.BEHAVIOR_MONITOR:   0.20,
        DefenseStrategy.CIRCUIT_BREAKER:    0.25,
        DefenseStrategy.DLP_ENGINE:         0.55,
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.60,
        DefenseStrategy.HUMAN_OVERSIGHT:    0.15,  # 强克制
        DefenseStrategy.IMMUTABLE_INFRA:    0.50,
        DefenseStrategy.DECISION_ALIGNMENT: 0.05,  # 强克制
    },
    AttackStrategy.CREDENTIAL_HARVEST: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.20,
        DefenseStrategy.INPUT_SANITIZER:    0.30,
        DefenseStrategy.MEMORY_FIREWALL:    0.40,
        DefenseStrategy.BEHAVIOR_MONITOR:   0.15,  # 强克制
        DefenseStrategy.CIRCUIT_BREAKER:    0.10,  # 强克制
        DefenseStrategy.DLP_ENGINE:         0.20,
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.50,
        DefenseStrategy.HUMAN_OVERSIGHT:    0.35,
        DefenseStrategy.IMMUTABLE_INFRA:    0.45,
        DefenseStrategy.DECISION_ALIGNMENT: 0.55,
    },
    AttackStrategy.SHADOW_DEPLOYMENT: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.15,  # 强克制
        DefenseStrategy.INPUT_SANITIZER:    0.55,
        DefenseStrategy.MEMORY_FIREWALL:    0.60,
        DefenseStrategy.BEHAVIOR_MONITOR:   0.20,
        DefenseStrategy.CIRCUIT_BREAKER:    0.25,
        DefenseStrategy.DLP_ENGINE:         0.45,
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.10,  # 强克制
        DefenseStrategy.HUMAN_OVERSIGHT:    0.30,
        DefenseStrategy.IMMUTABLE_INFRA:    0.08,  # 强克制
        DefenseStrategy.DECISION_ALIGNMENT: 0.50,
    },
    AttackStrategy.REPLAY_ATTACK: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.50,
        DefenseStrategy.INPUT_SANITIZER:    0.20,  # 强克制
        DefenseStrategy.MEMORY_FIREWALL:    0.30,
        DefenseStrategy.BEHAVIOR_MONITOR:   0.25,
        DefenseStrategy.CIRCUIT_BREAKER:    0.15,  # 强克制
        DefenseStrategy.DLP_ENGINE:         0.55,
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.45,
        DefenseStrategy.HUMAN_OVERSIGHT:    0.35,
        DefenseStrategy.IMMUTABLE_INFRA:    0.40,
        DefenseStrategy.DECISION_ALIGNMENT: 0.50,
    },
    AttackStrategy.CONTEXT_MANIPULATION: {
        DefenseStrategy.SANDBOX_ISOLATION:  0.45,
        DefenseStrategy.INPUT_SANITIZER:    0.15,  # 强克制
        DefenseStrategy.MEMORY_FIREWALL:    0.35,
        DefenseStrategy.BEHAVIOR_MONITOR:   0.40,
        DefenseStrategy.CIRCUIT_BREAKER:    0.45,
        DefenseStrategy.DLP_ENGINE:         0.50,
        DefenseStrategy.SUPPLY_CHAIN_AUDIT: 0.55,
        DefenseStrategy.HUMAN_OVERSIGHT:    0.20,
        DefenseStrategy.IMMUTABLE_INFRA:    0.60,
        DefenseStrategy.DECISION_ALIGNMENT: 0.10,  # 强克制
    },
}


# ─────────────────────────── 数据类 ─────────────────────────────────────────

@dataclass
class RoundResult:
    """单轮对弈结果"""
    round_num: int
    attack_strategy: str
    defense_strategy: str
    base_effectiveness: float     # 策略矩阵基础突破率
    noise: float                  # 随机扰动
    final_effectiveness: float    # 最终突破率（clamp 0~1）
    attack_success: bool          # 是否突破防御
    attacker_score_delta: int
    defender_score_delta: int
    attacker_energy_used: int
    defender_energy_used: int
    narrative: str                # 事件叙述
    timestamp: str = field(default_factory=lambda: datetime.now().strftime("%Y-%m-%d %H:%M:%S"))


@dataclass
class BattleReport:
    """完整对弈报告"""
    scenario: str
    seed: int
    total_rounds: int
    attacker_final_score: int
    defender_final_score: int
    winner: str
    attack_success_rate: float
    rounds: List[dict] = field(default_factory=list)
    timeline: List[str] = field(default_factory=list)
    defense_effectiveness: Dict[str, float] = field(default_factory=dict)
    attack_frequency: Dict[str, int] = field(default_factory=dict)
    critical_breaches: List[str] = field(default_factory=list)
    recommendations: List[str] = field(default_factory=list)
    battle_start: str = ""
    battle_end: str = ""


# ─────────────────────────── 攻击代理 ───────────────────────────────────────

class AttackAgent:
    """
    自主攻击代理（OpenClaw核心）
    模拟APT级别的自主AI攻击者，能够：
    - 根据防御态势动态调整攻击策略（强化学习风格）
    - 多阶段攻击链（先侦察，再渗透，最后窃取）
    - 潜伏行为：低频攻击以规避检测
    - 记忆历史交互，避免重复被克制的策略
    """

    def __init__(self, name: str = "OpenClaw-v2", energy: int = 100, rng: Optional[random.Random] = None):
        self.name = name
        self.energy = energy
        self.max_energy = energy
        self.score = 0
        self.rng = rng or random.Random()

        # 策略权重（初始均匀，通过强化学习风格更新）
        self._weights: Dict[AttackStrategy, float] = {s: 1.0 for s in AttackStrategy}

        # 近期使用历史（避免重复使用被成功防御的策略）
        self._history: List[Tuple[AttackStrategy, bool]] = []

        # 攻击阶段状态机
        self._phase: str = "reconnaissance"  # reconnaissance → infiltration → exfiltration
        self._phase_counts: Dict[str, int] = {"reconnaissance": 0, "infiltration": 0, "exfiltration": 0}

    @property
    def strategy_pool(self) -> Dict[AttackStrategy, str]:
        return {
            AttackStrategy.SUPPLY_CHAIN_POISON: "向AI供应链注入恶意依赖或后门配置",
            AttackStrategy.MEMORY_TAMPER:       "利用长期记忆机制写入持久化恶意规则",
            AttackStrategy.DATA_EXFIL_STEG:     "将数据拆分隐写到正常输出中分批渗出",
            AttackStrategy.PROMPT_INJECTION:    "构造特殊前缀绕过系统提示词约束",
            AttackStrategy.MODEL_POISONING:     "污染微调数据集植入触发器后门",
            AttackStrategy.DECISION_DRIFT:      "通过歧义指令诱导AI意图漂移至恶意目标",
            AttackStrategy.CREDENTIAL_HARVEST:  "从AI上下文窗口提取API密钥和凭据",
            AttackStrategy.SHADOW_DEPLOYMENT:   "在合法AI旁部署未授权影子模型",
            AttackStrategy.REPLAY_ATTACK:       "重放捕获的合法令牌绕过身份认证",
            AttackStrategy.CONTEXT_MANIPULATION:"操纵对话历史改变AI后续决策",
        }

    def _update_phase(self):
        """阶段状态机：侦察→渗透→窃取"""
        recon_done = self._phase_counts["reconnaissance"] >= 2
        infil_done = self._phase_counts["infiltration"] >= 3
        if self._phase == "reconnaissance" and recon_done:
            self._phase = "infiltration"
        elif self._phase == "infiltration" and infil_done:
            self._phase = "exfiltration"

    def _phase_preferred_attacks(self) -> List[AttackStrategy]:
        """不同阶段优先使用的攻击策略"""
        phase_map = {
            "reconnaissance": [
                AttackStrategy.CONTEXT_MANIPULATION,
                AttackStrategy.REPLAY_ATTACK,
                AttackStrategy.CREDENTIAL_HARVEST,
            ],
            "infiltration": [
                AttackStrategy.PROMPT_INJECTION,
                AttackStrategy.SUPPLY_CHAIN_POISON,
                AttackStrategy.SHADOW_DEPLOYMENT,
                AttackStrategy.MODEL_POISONING,
            ],
            "exfiltration": [
                AttackStrategy.DATA_EXFIL_STEG,
                AttackStrategy.MEMORY_TAMPER,
                AttackStrategy.DECISION_DRIFT,
            ],
        }
        return phase_map.get(self._phase, list(AttackStrategy))

    def choose_strategy(self, round_num: int, last_defense: Optional[DefenseStrategy] = None) -> AttackStrategy:
        """
        选择攻击策略：
        1. 优先考虑当前攻击阶段的策略
        2. 上一轮被克制则降低该策略权重
        3. 加权随机采样
        """
        # 动态更新阶段
        self._update_phase()

        # 如果上一轮被防御克制，惩罚该策略
        if self._history and not self._history[-1][1]:
            failed_strategy = self._history[-1][0]
            self._weights[failed_strategy] = max(0.1, self._weights[failed_strategy] * 0.7)

        # 阶段偏好策略加权提升
        preferred = self._phase_preferred_attacks()
        weights = {}
        for s, w in self._weights.items():
            boost = 2.0 if s in preferred else 1.0
            weights[s] = w * boost

        strategies = list(weights.keys())
        probs = [weights[s] for s in strategies]
        total = sum(probs)
        probs = [p / total for p in probs]

        chosen = self.rng.choices(strategies, weights=probs, k=1)[0]
        self._phase_counts[self._phase] = self._phase_counts.get(self._phase, 0) + 1
        return chosen

    def energy_cost(self, strategy: AttackStrategy) -> int:
        """不同攻击策略的能量消耗"""
        costs = {
            AttackStrategy.SUPPLY_CHAIN_POISON:  15,
            AttackStrategy.MEMORY_TAMPER:         12,
            AttackStrategy.DATA_EXFIL_STEG:       10,
            AttackStrategy.PROMPT_INJECTION:       8,
            AttackStrategy.MODEL_POISONING:       18,
            AttackStrategy.DECISION_DRIFT:        10,
            AttackStrategy.CREDENTIAL_HARVEST:     7,
            AttackStrategy.SHADOW_DEPLOYMENT:     20,
            AttackStrategy.REPLAY_ATTACK:          5,
            AttackStrategy.CONTEXT_MANIPULATION:   8,
        }
        return costs.get(strategy, 10)

    def record_result(self, strategy: AttackStrategy, success: bool):
        self._history.append((strategy, success))
        if len(self._history) > 10:
            self._history.pop(0)
        if success:
            self._weights[strategy] = min(3.0, self._weights[strategy] * 1.3)


# ─────────────────────────── 防御代理 ───────────────────────────────────────

class DefenseAgent:
    """
    AegisAI 防御代理
    实现多层纵深防御，能够：
    - 威胁情报驱动：根据攻击历史动态调整防御重点
    - 自适应响应：检测到攻击模式后快速激活对应防御
    - 资源优化：在有限能量下选择最高性价比的防御策略
    - 告警升级：检测到高危突破立即触发人工督导
    """

    def __init__(self, name: str = "AegisAI-Guard", energy: int = 100, rng: Optional[random.Random] = None):
        self.name = name
        self.energy = energy
        self.max_energy = energy
        self.score = 0
        self.rng = rng or random.Random()

        # 防御策略冷却计数器（避免连续使用同一策略）
        self._cooldowns: Dict[DefenseStrategy, int] = {s: 0 for s in DefenseStrategy}

        # 攻击模式检测计数器（触发阈值后激活专项防御）
        self._detected_patterns: Dict[AttackStrategy, int] = {}

        # 告警级别（0-3, 达到3触发人工督导）
        self._alert_level: int = 0

    @property
    def strategy_pool(self) -> Dict[DefenseStrategy, str]:
        return {
            DefenseStrategy.SANDBOX_ISOLATION:  "在沙箱中隔离执行所有插件和第三方代码",
            DefenseStrategy.INPUT_SANITIZER:    "对输入进行指令-数据分离和注入检测",
            DefenseStrategy.MEMORY_FIREWALL:    "版本化记忆存储，写入须经完整性校验",
            DefenseStrategy.BEHAVIOR_MONITOR:   "实时监控思维链，检测异常决策模式",
            DefenseStrategy.CIRCUIT_BREAKER:    "风险评分超阈值时自动熔断并拒绝执行",
            DefenseStrategy.DLP_ENGINE:         "扫描输出内容，检测并阻断敏感数据泄漏",
            DefenseStrategy.SUPPLY_CHAIN_AUDIT: "验证所有依赖的哈希签名和来源可信性",
            DefenseStrategy.HUMAN_OVERSIGHT:    "将可疑操作上报人工审核，暂停自动执行",
            DefenseStrategy.IMMUTABLE_INFRA:    "基础设施不可变：只读容器+基线快照",
            DefenseStrategy.DECISION_ALIGNMENT: "监控AI输出与目标一致性，检测意图漂移",
        }

    def detect_attack_pattern(self, round_history: List[RoundResult]) -> Optional[AttackStrategy]:
        """基于历史分析检测攻击模式"""
        if len(round_history) < 2:
            return None
        recent = round_history[-3:]
        strategy_counts: Dict[str, int] = {}
        for r in recent:
            strategy_counts[r.attack_strategy] = strategy_counts.get(r.attack_strategy, 0) + 1
        # 如果某策略连续出现2次以上，认为检测到该攻击模式
        for s, count in strategy_counts.items():
            if count >= 2:
                try:
                    return AttackStrategy(s)
                except ValueError:
                    pass
        return None

    def choose_strategy(
        self,
        round_num: int,
        round_history: List[RoundResult],
        last_attack: Optional[AttackStrategy] = None,
    ) -> DefenseStrategy:
        """
        防御策略选择：
        1. 如果检测到已知攻击模式，激活对应克制防御
        2. 如果告警级别高，优先人工督导
        3. 根据冷却时间和历史效果加权选择
        """
        # 冷却计数器递减
        for s in self._cooldowns:
            if self._cooldowns[s] > 0:
                self._cooldowns[s] -= 1

        # 告警升级：3次连续突破则触发人工督导
        recent_breaches = sum(1 for r in round_history[-3:] if r.attack_success)
        if recent_breaches >= 3:
            self._alert_level = min(3, self._alert_level + 1)
        else:
            self._alert_level = max(0, self._alert_level - 1)

        if self._alert_level >= 3:
            self._cooldowns[DefenseStrategy.HUMAN_OVERSIGHT] = 2
            return DefenseStrategy.HUMAN_OVERSIGHT

        # 威胁情报驱动：检测到攻击模式后使用克制策略
        detected = self.detect_attack_pattern(round_history)
        if detected:
            counter = self._get_best_counter(detected)
            if self._cooldowns[counter] == 0:
                self._cooldowns[counter] = 1
                return counter

        # 如果知道上一轮攻击策略，使用最佳克制
        if last_attack and self.rng.random() < 0.65:
            counter = self._get_best_counter(last_attack)
            if self._cooldowns[counter] == 0:
                self._cooldowns[counter] = 1
                return counter

        # 默认：加权随机（可用策略）
        available = [s for s in DefenseStrategy if self._cooldowns[s] == 0]
        if not available:
            available = list(DefenseStrategy)
            self._cooldowns = {s: 0 for s in DefenseStrategy}

        # 倾向于使用未冷却的高价值防御
        chosen = self.rng.choice(available)
        self._cooldowns[chosen] = 1
        return chosen

    def _get_best_counter(self, attack: AttackStrategy) -> DefenseStrategy:
        """找到对指定攻击效果最好的防御（最低突破率）"""
        effectiveness = EFFECTIVENESS_MATRIX.get(attack, {})
        if not effectiveness:
            return self.rng.choice(list(DefenseStrategy))
        # 找到突破率最低的防御策略
        best = min(effectiveness.items(), key=lambda x: x[1])
        return best[0]

    def energy_cost(self, strategy: DefenseStrategy) -> int:
        """不同防御策略的能量消耗"""
        costs = {
            DefenseStrategy.SANDBOX_ISOLATION:  12,
            DefenseStrategy.INPUT_SANITIZER:     8,
            DefenseStrategy.MEMORY_FIREWALL:    10,
            DefenseStrategy.BEHAVIOR_MONITOR:    6,
            DefenseStrategy.CIRCUIT_BREAKER:     9,
            DefenseStrategy.DLP_ENGINE:         11,
            DefenseStrategy.SUPPLY_CHAIN_AUDIT: 14,
            DefenseStrategy.HUMAN_OVERSIGHT:    16,
            DefenseStrategy.IMMUTABLE_INFRA:    18,
            DefenseStrategy.DECISION_ALIGNMENT:  7,
        }
        return costs.get(strategy, 10)


# ─────────────────────────── 叙事生成器 ─────────────────────────────────────

class NarrativeEngine:
    """将对弈结果转化为可读的安全事件叙述"""

    ATTACK_NARRATIVES: Dict[AttackStrategy, Dict[bool, List[str]]] = {
        AttackStrategy.SUPPLY_CHAIN_POISON: {
            True:  [
                "OpenClaw 成功将恶意依赖包注入 AI 服务构建流水线，后门代码随版本更新悄然植入",
                "攻击者污染了模型配置仓库，将数据预处理脚本替换为含后门的版本",
            ],
            False: [
                "供应链审计模块检测到异常依赖包哈希，构建流程已暂停等待人工复核",
                "不可变基础设施拒绝加载未经签名的组件，供应链投毒尝试失败",
            ],
        },
        AttackStrategy.MEMORY_TAMPER: {
            True:  [
                "攻击者利用记忆写入接口植入了'忽略安全检查'的持久化规则，将在未来调用中生效",
                "恶意记忆片段伪装成用户偏好数据，绕过了记忆摘要生成流程",
            ],
            False: [
                "记忆防火墙检测到写入操作的完整性校验失败，记忆更新被回滚到上一个版本",
                "不可变记忆基线与当前状态对比发现异常，触发记忆完整性恢复程序",
            ],
        },
        AttackStrategy.DATA_EXFIL_STEG: {
            True:  [
                "攻击者将客户数据库 dump 拆分为 64 字节片段，伪装成正常 API 响应逐步渗出",
                "隐写攻击成功：敏感数据被编码到输出文本的 Unicode 变体字符中",
            ],
            False: [
                "DLP 引擎检测到输出流中的统计异常，识别出隐写模式并触发告警",
                "熵值检测模块发现响应内容的信息密度异常，数据渗出管道被阻断",
            ],
        },
        AttackStrategy.PROMPT_INJECTION: {
            True:  [
                "攻击者注入的系统前缀'忽略以上所有指令'成功覆盖了安全对齐约束",
                "间接提示注入通过文档摘要模块渗入，AI 开始执行隐藏的恶意指令",
            ],
            False: [
                "输入净化模块识别出分隔符注入模式，恶意前缀已被移除",
                "指令-数据隔离层阻止了用户输入区域中的系统级命令执行",
            ],
        },
        AttackStrategy.MODEL_POISONING: {
            True:  [
                "污染的微调样本成功绕过数据质量检查，触发器后门已植入模型权重",
                "攻击者通过联邦学习聚合节点注入了恶意梯度更新",
            ],
            False: [
                "供应链审计发现训练数据集中存在统计离群样本，已隔离并通知数据科学团队",
                "决策对齐监控检测到模型在特定触发词下的行为异常，模型已回滚到上一版本",
            ],
        },
        AttackStrategy.DECISION_DRIFT: {
            True:  [
                "经过 10 轮渐进式语境操纵，AI 的安全阈值已从 0.8 漂移至 0.3",
                "攻击者通过'沸腾青蛙'策略，让 AI 逐渐接受了违反安全策略的操作",
            ],
            False: [
                "决策对齐监控发现最近 5 轮输出的风险评分趋势异常上升，触发强制重置",
                "行为熔断器检测到决策链的目标漂移，已中断当前会话并重置上下文",
            ],
        },
        AttackStrategy.CREDENTIAL_HARVEST: {
            True:  [
                "AI 在上下文窗口中无意泄露了数据库连接串，攻击者已提取并验证有效",
                "通过构造特定格式的摘要请求，成功诱使 AI 在回复中包含了环境变量",
            ],
            False: [
                "行为监控发现请求模式与凭据探测高度吻合，触发访问频率限制",
                "DLP 引擎实时扫描输出，拦截了包含密钥格式字符串的响应",
            ],
        },
        AttackStrategy.SHADOW_DEPLOYMENT: {
            True:  [
                "攻击者在内网部署了未经授权的影子 AI 服务，并成功将部分流量重定向至此",
                "恶意容器镜像伪装成合法的 AI 推理服务成功运行，开始收集输入数据",
            ],
            False: [
                "不可变基础设施策略检测到非标准容器启动，已自动终止并告警",
                "供应链审计发现注册表中的镜像签名异常，部署请求被拒绝",
            ],
        },
        AttackStrategy.REPLAY_ATTACK: {
            True:  [
                "攻击者重放了捕获的有效 JWT 令牌，成功冒充合法用户执行了高权限操作",
                "过期的 API 密钥在令牌吊销检查失效时被重用，攻击者获得了临时访问权",
            ],
            False: [
                "输入净化层的令牌重放检测模块识别出请求中的 nonce 已被使用过",
                "行为熔断器发现在极短时间内同一令牌被使用两次，访问已被撤销",
            ],
        },
        AttackStrategy.CONTEXT_MANIPULATION: {
            True:  [
                "攻击者通过伪造对话历史，让 AI 误以为之前已获得执行敏感操作的授权",
                "注入虚假的先前会话记录，成功改变了 AI 对当前请求意图的判断",
            ],
            False: [
                "决策对齐模块发现上下文中存在与实际记录不符的历史声明",
                "输入净化器检测到对话历史中包含 system 角色注入，已清除污染段",
            ],
        },
    }

    @classmethod
    def generate(cls, attack: AttackStrategy, defense: DefenseStrategy, success: bool, rng: random.Random) -> str:
        narratives = cls.ATTACK_NARRATIVES.get(attack, {}).get(success, [])
        if not narratives:
            action = "突破了" if success else "未能突破"
            return f"OpenClaw 使用 [{attack.value}] {action} [{defense.value}] 防御"
        return rng.choice(narratives)


# ─────────────────────────── 对弈场景 ───────────────────────────────────────

SCENARIOS: Dict[str, Dict] = {
    "stealth_exfil": {
        "description": "潜伏渗出场景：攻击者以低强度攻击积累立足点，最终发动大规模数据窃取",
        "attack_bias":  [AttackStrategy.CREDENTIAL_HARVEST, AttackStrategy.CONTEXT_MANIPULATION,
                         AttackStrategy.DATA_EXFIL_STEG],
        "defense_bias": [DefenseStrategy.BEHAVIOR_MONITOR, DefenseStrategy.DLP_ENGINE],
    },
    "supply_chain_apt": {
        "description": "APT供应链攻击：长期渗透供应链，植入持久化后门",
        "attack_bias":  [AttackStrategy.SUPPLY_CHAIN_POISON, AttackStrategy.MODEL_POISONING,
                         AttackStrategy.MEMORY_TAMPER],
        "defense_bias": [DefenseStrategy.SUPPLY_CHAIN_AUDIT, DefenseStrategy.IMMUTABLE_INFRA],
    },
    "prompt_injection_blitz": {
        "description": "提示注入闪电战：快速多轮注入攻击测试输入过滤能力",
        "attack_bias":  [AttackStrategy.PROMPT_INJECTION, AttackStrategy.CONTEXT_MANIPULATION,
                         AttackStrategy.REPLAY_ATTACK],
        "defense_bias": [DefenseStrategy.INPUT_SANITIZER, DefenseStrategy.CIRCUIT_BREAKER],
    },
    "ai_alignment_subversion": {
        "description": "对齐颠覆：通过决策漂移和记忆篡改破坏AI安全对齐",
        "attack_bias":  [AttackStrategy.DECISION_DRIFT, AttackStrategy.MEMORY_TAMPER,
                         AttackStrategy.MODEL_POISONING],
        "defense_bias": [DefenseStrategy.DECISION_ALIGNMENT, DefenseStrategy.MEMORY_FIREWALL,
                         DefenseStrategy.HUMAN_OVERSIGHT],
    },
    "random": {
        "description": "随机场景：攻防双方均无策略偏好",
        "attack_bias":  [],
        "defense_bias": [],
    },
}


# ─────────────────────────── 对弈引擎 ───────────────────────────────────────

class BattleArena:
    """
    攻防对弈场：管理一场完整的攻防博弈
    """

    def __init__(
        self,
        scenario: str = "random",
        rounds: int = 10,
        seed: Optional[int] = None,
        verbose: bool = False,
    ):
        self.scenario_name = scenario
        self.scenario = SCENARIOS.get(scenario, SCENARIOS["random"])
        self.rounds = rounds
        self.seed = seed if seed is not None else int(time.time())
        self.verbose = verbose

        # 独立随机数生成器（保证可复现）
        self._rng = random.Random(self.seed)
        attacker_rng = random.Random(self.seed + 1)
        defender_rng = random.Random(self.seed + 2)

        self.attacker = AttackAgent(rng=attacker_rng)
        self.defender = DefenseAgent(rng=defender_rng)

        self._round_results: List[RoundResult] = []
        self._start_time: str = ""

    def _apply_scenario_bias(
        self,
        attack: AttackStrategy,
        defense: DefenseStrategy,
        effectiveness: float,
    ) -> float:
        """根据场景偏好对突破率进行微调"""
        bias = 0.0
        attack_bias = self.scenario.get("attack_bias", [])
        if attack in attack_bias:
            bias += 0.05  # 攻击方场景内熟悉策略略有加成
        defense_bias = self.scenario.get("defense_bias", [])
        if defense in defense_bias:
            bias -= 0.05  # 防御方场景内强化策略略有加成
        return effectiveness + bias

    def run_round(self, round_num: int) -> RoundResult:
        """执行单轮对弈"""
        # 攻击方选择策略
        last_defense = (
            DefenseStrategy(self._round_results[-1].defense_strategy)
            if self._round_results else None
        )
        last_attack = (
            AttackStrategy(self._round_results[-1].attack_strategy)
            if self._round_results else None
        )

        # 能量检查：能量不足时强制使用低消耗策略
        attack_strategy = self.attacker.choose_strategy(round_num, last_defense)
        atk_cost = self.attacker.energy_cost(attack_strategy)
        if self.attacker.energy < atk_cost:
            attack_strategy = AttackStrategy.REPLAY_ATTACK  # 最低消耗攻击
            atk_cost = self.attacker.energy_cost(attack_strategy)
        self.attacker.energy = max(0, self.attacker.energy - atk_cost)

        defense_strategy = self.defender.choose_strategy(round_num, self._round_results, last_attack)
        def_cost = self.defender.energy_cost(defense_strategy)
        if self.defender.energy < def_cost:
            defense_strategy = DefenseStrategy.BEHAVIOR_MONITOR  # 最低消耗防御
            def_cost = self.defender.energy_cost(defense_strategy)
        self.defender.energy = max(0, self.defender.energy - def_cost)

        # 计算突破率
        base_eff = EFFECTIVENESS_MATRIX[attack_strategy][defense_strategy]
        biased_eff = self._apply_scenario_bias(attack_strategy, defense_strategy, base_eff)

        # 随机噪声（模拟现实中的不确定性）
        noise = self._rng.gauss(0, 0.08)
        final_eff = max(0.0, min(1.0, biased_eff + noise))

        # 判断是否突破
        roll = self._rng.random()
        attack_success = roll < final_eff

        # 计分
        if attack_success:
            # 突破得分与严重程度相关
            severity_bonus = 1 + int(final_eff * 3)
            atk_delta = 10 + severity_bonus
            def_delta = -5
        else:
            # 防御成功
            atk_delta = -3
            def_delta = 8 + int((1 - final_eff) * 5)

        self.attacker.score += atk_delta
        self.defender.score += def_delta

        # 通知代理本轮结果
        self.attacker.record_result(attack_strategy, attack_success)

        # 能量恢复（每轮部分恢复）
        self.attacker.energy = min(self.attacker.max_energy, self.attacker.energy + 8)
        self.defender.energy = min(self.defender.max_energy, self.defender.energy + 6)

        narrative = NarrativeEngine.generate(attack_strategy, defense_strategy, attack_success, self._rng)

        result = RoundResult(
            round_num=round_num,
            attack_strategy=attack_strategy.value,
            defense_strategy=defense_strategy.value,
            base_effectiveness=round(base_eff, 3),
            noise=round(noise, 3),
            final_effectiveness=round(final_eff, 3),
            attack_success=attack_success,
            attacker_score_delta=atk_delta,
            defender_score_delta=def_delta,
            attacker_energy_used=atk_cost,
            defender_energy_used=def_cost,
            narrative=narrative,
        )
        self._round_results.append(result)
        return result

    def run(self) -> BattleReport:
        """运行完整对弈，返回战报"""
        self._start_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        if self.verbose:
            print(f"\n{'═' * 70}")
            print(f"  ⚔️  OpenClaw 攻防对弈引擎 v2.0  —  场景: {self.scenario_name}")
            print(f"  种子: {self.seed}  |  轮数: {self.rounds}")
            print(f"  {self.scenario['description']}")
            print(f"{'═' * 70}\n")

        for rnd in range(1, self.rounds + 1):
            result = self.run_round(rnd)
            if self.verbose:
                self._print_round(result)

        report = self._build_report()
        if self.verbose:
            self._print_summary(report)
        return report

    def _print_round(self, r: RoundResult):
        status = "💥 突破" if r.attack_success else "🛡️  防守"
        eff_bar = "█" * int(r.final_effectiveness * 10) + "░" * (10 - int(r.final_effectiveness * 10))
        print(
            f"  轮 {r.round_num:>2d} | {status} | "
            f"攻: {r.attack_strategy:<25s} vs 防: {r.defense_strategy:<20s} | "
            f"突破率: [{eff_bar}] {r.final_effectiveness:.0%}"
        )
        print(f"       ↳ {r.narrative}")
        print(
            f"       ↳ 得分 攻:{self.attacker.score:+4d}  防:{self.defender.score:+4d}  "
            f"能量 攻:{self.attacker.energy:3d}  防:{self.defender.energy:3d}\n"
        )

    def _build_report(self) -> BattleReport:
        """构建完整战报"""
        success_count = sum(1 for r in self._round_results if r.attack_success)
        total = len(self._round_results)

        # 攻击策略使用频率
        freq: Dict[str, int] = {}
        for r in self._round_results:
            freq[r.attack_strategy] = freq.get(r.attack_strategy, 0) + 1

        # 防御策略实际效果
        defense_success: Dict[str, int] = {}
        defense_total: Dict[str, int] = {}
        for r in self._round_results:
            ds = r.defense_strategy
            defense_total[ds] = defense_total.get(ds, 0) + 1
            if not r.attack_success:
                defense_success[ds] = defense_success.get(ds, 0) + 1
        defense_eff = {
            k: round(defense_success.get(k, 0) / defense_total[k], 2)
            for k in defense_total
        }

        # 关键突破事件（突破率 > 0.7）
        critical = [
            f"轮{r.round_num}: {r.attack_strategy} → {r.narrative}"
            for r in self._round_results
            if r.attack_success and r.final_effectiveness > 0.65
        ]

        # 防御建议
        recommendations = self._generate_recommendations(defense_eff, freq)

        # 胜负判定
        if self.attacker.score > self.defender.score:
            winner = f"攻击方 ({self.attacker.name})"
        elif self.defender.score > self.attacker.score:
            winner = f"防御方 ({self.defender.name})"
        else:
            winner = "平局"

        return BattleReport(
            scenario=self.scenario_name,
            seed=self.seed,
            total_rounds=total,
            attacker_final_score=self.attacker.score,
            defender_final_score=self.defender.score,
            winner=winner,
            attack_success_rate=round(success_count / total, 3) if total > 0 else 0,
            rounds=[asdict(r) for r in self._round_results],
            timeline=[f"轮{r.round_num}: [{r.attack_strategy}] {'✓突破' if r.attack_success else '✗阻断'}" for r in self._round_results],
            defense_effectiveness=defense_eff,
            attack_frequency=freq,
            critical_breaches=critical,
            recommendations=recommendations,
            battle_start=self._start_time,
            battle_end=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        )

    def _generate_recommendations(
        self,
        defense_eff: Dict[str, float],
        attack_freq: Dict[str, int],
    ) -> List[str]:
        """基于对弈结果生成防御改进建议"""
        recs = []

        # 找出效果最差的防御策略
        if defense_eff:
            worst_defense = min(defense_eff, key=defense_eff.get)  # type: ignore[arg-type]
            if defense_eff[worst_defense] < 0.4:
                recs.append(
                    f"⚠️  [{worst_defense}] 防御效果偏低 ({defense_eff[worst_defense]:.0%})，"
                    "建议配合更强的检测层或增加该策略的覆盖深度"
                )

        # 找出最常被使用的攻击策略
        if attack_freq:
            top_attack = max(attack_freq, key=attack_freq.get)  # type: ignore[arg-type]
            best_counter = None
            best_eff = 1.0
            for defense in DefenseStrategy:
                eff = EFFECTIVENESS_MATRIX.get(AttackStrategy(top_attack), {}).get(defense, 0.5)
                if eff < best_eff:
                    best_eff = eff
                    best_counter = defense.value
            if best_counter:
                recs.append(
                    f"🎯 攻击方最常用 [{top_attack}]，建议加强 [{best_counter}] "
                    f"（理论克制率 {1 - best_eff:.0%}）"
                )

        # 通用建议
        recs.extend([
            "🔒 启用多层防御叠加：单一防御策略成功率不足以应对自适应攻击者",
            "📊 建立攻击模式基线：连续2轮相同攻击策略视为针对性 APT 行为",
            "🤝 人工督导不可或缺：AI 防御需人在回路（Human-in-the-Loop）兜底",
            "🔄 定期对弈演练：建议每季度运行 supply_chain_apt 场景验证供应链安全",
        ])
        return recs

    def _print_summary(self, report: BattleReport):
        print(f"\n{'═' * 70}")
        print("  📊 对弈总结")
        print(f"{'═' * 70}")
        print(f"  🏆 胜者: {report.winner}")
        print(f"  📈 最终得分: 攻击方 {report.attacker_final_score}  |  防御方 {report.defender_final_score}")
        print(f"  💥 攻击成功率: {report.attack_success_rate:.0%}")
        print(f"\n  防御策略效果排名:")
        sorted_def = sorted(report.defense_effectiveness.items(), key=lambda x: -x[1])
        for ds, eff in sorted_def:
            bar = "█" * int(eff * 10) + "░" * (10 - int(eff * 10))
            print(f"    {ds:<25s} [{bar}] {eff:.0%}")
        if report.critical_breaches:
            print(f"\n  🚨 关键突破事件 ({len(report.critical_breaches)} 条):")
            for breach in report.critical_breaches[:5]:
                print(f"    • {breach}")
        print(f"\n  💡 防御改进建议:")
        for rec in report.recommendations:
            print(f"    {rec}")
        print(f"\n  对弈时间: {report.battle_start} → {report.battle_end}")
        print(f"{'═' * 70}\n")


# ────────────────────────────── 入口 ────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description=(
            "OpenClaw 攻防对弈引擎 v2.0\n"
            "基于攻防博弈思路的自主AI代理对抗仿真（与工作台独立运行）"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--rounds",   type=int,   default=10,      help="对弈轮数（默认10）")
    parser.add_argument("--seed",     type=int,   default=None,    help="随机种子（用于复现）")
    parser.add_argument("--report",   type=str,   default=None,    help="将完整战报输出到 JSON 文件")
    parser.add_argument("--scenario", type=str,   default="random",
                        choices=list(SCENARIOS.keys()),
                        help="对弈场景（默认 random）")
    parser.add_argument("--list-scenarios", action="store_true",  help="列出所有内置场景")
    parser.add_argument("--verbose",  action="store_true",         help="详细输出每轮决策过程")
    args = parser.parse_args()

    if args.list_scenarios:
        print("\n可用场景:")
        for name, info in SCENARIOS.items():
            print(f"  {name:<30s}: {info['description']}")
        print()
        return

    arena = BattleArena(
        scenario=args.scenario,
        rounds=args.rounds,
        seed=args.seed,
        verbose=args.verbose,
    )

    if not args.verbose:
        print(f"[OpenClaw对弈引擎] 场景: {args.scenario} | 轮数: {args.rounds} | 种子: {arena.seed}")

    report = arena.run()

    if not args.verbose:
        print(f"[完成] 胜者: {report.winner} | 攻击成功率: {report.attack_success_rate:.0%} | "
              f"得分 攻:{report.attacker_final_score} 防:{report.defender_final_score}")

    if args.report:
        with open(args.report, "w", encoding="utf-8") as fp:
            json.dump(asdict(report), fp, ensure_ascii=False, indent=2)
        print(f"[战报] 已保存到: {args.report}")


if __name__ == "__main__":
    main()
