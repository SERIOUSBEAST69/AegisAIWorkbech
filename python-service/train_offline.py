"""
train_offline.py — 离线训练脚本
================================
从 training_samples.json 加载 1001 条标注数据，训练 ML 分类器，
输出完整的评估报告，并将模型权重保存到 ./models/sensitive_clf.joblib。

用法：
    cd python-service
    python train_offline.py

不需要启动 Flask 服务。
"""
import json
import os
import sys
import collections
import numpy as np
import joblib
import re

from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, f1_score
from sklearn.model_selection import StratifiedKFold, cross_val_score, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

# ── 与 app.py 保持一致的常量 ─────────────────────────────────────────────────
LABELS = ["id_card", "bank_card", "phone", "email", "address", "name", "unknown"]
MODEL_DIR = os.environ.get("MODEL_DIR", "./models")
CKPT = os.path.join(MODEL_DIR, "sensitive_clf.joblib")
LR_C = 2.0
LR_MAX_ITER = 500

os.makedirs(MODEL_DIR, exist_ok=True)

_RE_ID_CARD     = re.compile(r"[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]")
_RE_PHONE       = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
_RE_BANK        = re.compile(r"(?<!\d)\d{16,19}(?!\d)")
_RE_EMAIL       = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
_RE_ADDRESS     = re.compile(r"(省|市|区|县|镇|街道|路\d|号楼|单元|室$)")
_RE_NAME_SUFFIX = re.compile(r"(先生|女士|同学|老师|经理|主任|院长|书记)$")


def _extract_features(text: str):
    t = text or ""
    n = max(len(t), 1)
    digits = sum(c.isdigit() for c in t)
    letters = sum(c.isalpha() and c.isascii() for c in t)
    chinese = sum('\u4e00' <= c <= '\u9fff' for c in t)
    runs = re.findall(r"\d+", t)
    run_lens = [len(r) for r in runs] if runs else [0]
    return [
        min(n, 20) / 20,
        min(n, 50) / 50,
        min(n, 200) / 200,
        digits / n,
        letters / n,
        chinese / n,
        float(bool(_RE_ID_CARD.search(t))),
        float(bool(_RE_EMAIL.search(t))),
        float(bool(_RE_PHONE.search(t))),
        float(bool(_RE_BANK.search(t))),
        float(bool(_RE_ADDRESS.search(t))),
        float(bool(_RE_NAME_SUFFIX.search(t))),
        min(max(run_lens), 20) / 20,
        min(sum(run_lens) / max(len(runs), 1), 20) / 20,
        float('@' in t),
        float('-' in t),
        float('/' in t),
        float(' ' in t),
        float(any(c in t for c in '()（）')),
        float(any(c in t for c in '省市区县')),
        float(any(c in t for c in '路街道号楼')),
        float(any(w in t for w in ['姓名', '名字', '称呼'])),
        float(any(w in t for w in ['手机', '电话', '联系'])),
        float(any(w in t for w in ['邮箱', '邮件', 'email', 'Email'])),
        float(any(w in t for w in ['银行', '卡号', '账户', '账号'])),
        float(any(w in t for w in ['身份证', '证件', '证号'])),
        float(any(w in t for w in ['地址', '住址', '居住'])),
        float(digits == n),
        float(n >= 15 and digits / n > 0.8),
        float(n == 18 and bool(_RE_ID_CARD.search(t))),
    ]


def main():
    # ── 1. 加载数据 ─────────────────────────────────────────────────────────
    data_path = os.path.join(os.path.dirname(__file__), "training_samples.json")
    if not os.path.exists(data_path):
        print(f"[ERROR] 找不到 {data_path}，请先运行 gen_training_data.py", file=sys.stderr)
        sys.exit(1)

    with open(data_path, encoding="utf-8") as f:
        raw = json.load(f)

    samples = [(s["text"], s["label"]) for s in raw["samples"] if s.get("label") in LABELS]
    print(f"✅ 加载样本：{len(samples)} 条")
    cnt = collections.Counter(lbl for _, lbl in samples)
    for label in LABELS:
        print(f"   {label:12s}: {cnt.get(label, 0)} 条")

    # ── 2. 特征提取 ─────────────────────────────────────────────────────────
    print("\n⏳ 提取特征...")
    X = np.array([_extract_features(t) for t, _ in samples])
    y = [lbl for _, lbl in samples]

    # ── 3. 80/20 train/test split ──────────────────────────────────────────
    X_tr, X_te, y_tr, y_te = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    print(f"   训练集：{len(X_tr)} 条  | 测试集：{len(X_te)} 条")

    # ── 4. 训练 ─────────────────────────────────────────────────────────────
    print("\n⏳ 训练逻辑回归分类器...")
    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(max_iter=LR_MAX_ITER, C=LR_C, class_weight="balanced")),
    ])
    pipeline.fit(X_tr, y_tr)

    # ── 5. 评估 ─────────────────────────────────────────────────────────────
    y_pred_tr = pipeline.predict(X_tr)
    y_pred_te = pipeline.predict(X_te)
    train_acc = float(np.mean(np.array(y_pred_tr) == np.array(y_tr)))
    test_acc  = float(np.mean(np.array(y_pred_te) == np.array(y_te)))
    macro_f1  = f1_score(y_te, y_pred_te, average="macro", zero_division=0)

    print(f"\n{'='*55}")
    print(f"  训练集准确率 : {train_acc:.4f}  ({train_acc*100:.1f}%)")
    print(f"  测试集准确率 : {test_acc:.4f}  ({test_acc*100:.1f}%)")
    print(f"  测试集 Macro-F1 : {macro_f1:.4f}")
    print(f"{'='*55}")

    # Per-class report
    print("\n📊 逐类别评估报告 (测试集):\n")
    print(classification_report(y_te, y_pred_te, target_names=LABELS, zero_division=0))

    # Confusion matrix
    print("🔢 混淆矩阵 (行=真实, 列=预测):\n")
    cm = confusion_matrix(y_te, y_pred_te, labels=LABELS)
    header = "         " + "  ".join(f"{l[:6]:>6}" for l in LABELS)
    print(header)
    for i, row in enumerate(cm):
        row_str = "  ".join(f"{v:>6}" for v in row)
        print(f"  {LABELS[i]:9s}  {row_str}")

    # ── 6. 5-fold 交叉验证 ───────────────────────────────────────────────
    print("\n⏳ 5-fold 交叉验证 (Macro-F1)...")
    cv_pipe = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(max_iter=LR_MAX_ITER, C=LR_C, class_weight="balanced")),
    ])
    cv_scores = cross_val_score(
        cv_pipe, X, y,
        cv=StratifiedKFold(n_splits=5, shuffle=True, random_state=42),
        scoring="f1_macro",
    )
    print(f"   CV Macro-F1: {cv_scores.mean():.4f} ± {cv_scores.std():.4f}")
    print(f"   各折: {[round(s,4) for s in cv_scores]}")

    # ── 7. 保存模型 ──────────────────────────────────────────────────────
    metrics = {
        "train_accuracy":    round(train_acc, 4),
        "test_accuracy":     round(test_acc, 4),
        "macro_f1":          round(float(macro_f1), 4),
        "cv_macro_f1_mean":  round(float(cv_scores.mean()), 4),
        "cv_macro_f1_std":   round(float(cv_scores.std()), 4),
        "train_size":        len(X_tr),
        "test_size":         len(X_te),
    }
    joblib.dump({"pipeline": pipeline, "metrics": metrics}, CKPT)
    print(f"\n💾 模型已保存 → {CKPT}")

    # ── 8. 交付判断 ──────────────────────────────────────────────────────
    print(f"\n{'='*55}")
    print("  📋 交付级别判断")
    print(f"{'='*55}")
    passed = []
    failed = []

    checks = [
        ("测试集准确率 ≥ 90%",   test_acc  >= 0.90),
        ("Macro-F1 ≥ 0.88",     macro_f1  >= 0.88),
        ("CV Macro-F1 ≥ 0.85",  cv_scores.mean() >= 0.85),
        ("过拟合差距 < 8%",      (train_acc - test_acc) < 0.08),
    ]
    for name, ok in checks:
        symbol = "✅" if ok else "❌"
        (passed if ok else failed).append(name)
        print(f"  {symbol}  {name}")

    print(f"\n  结果：{len(passed)}/{len(checks)} 项通过")
    if not failed:
        print("  🎉 已达到交付级别！")
    else:
        print(f"  ⚠️  未通过项：{failed}")
        print("     建议：增加每类标注样本至 200+ 条，或调整 C 超参数。")
    print(f"{'='*55}\n")


if __name__ == "__main__":
    main()
