"""
test_classifier.py — 分类器功能成熟度测试套件
================================================
测试三个难度层级：
  Level 1 - Easy   : 纯结构化字段（正则能识别）
  Level 2 - Medium : 关键词包裹的混合文本（ML 的真正优势）
  Level 3 - Hard   : 短姓名/歧义文本（ML 挑战区）

通过标准（交付级别）：
  - Level 1 准确率 ≥ 100%
  - Level 2 准确率 ≥ 90%
  - Level 3 准确率 ≥ 70%
  - 整体 Macro-F1  ≥ 0.88
  - /metrics 端点返回 test_accuracy 字段（有真实评估数据）

用法：
    # 启动服务
    cd python-service
    BERT_MOCK=true python app.py

    # 新开终端运行测试
    python test_classifier.py

    # 或指定地址
    python test_classifier.py --url http://localhost:5000
"""
import argparse
import json
import sys
import collections
import urllib.request
import urllib.error

# ── 测试用例 ──────────────────────────────────────────────────────────────────
LEVEL1_CASES = [
    # 纯结构化字段，正则应直接命中
    ("410101199001011234",    "id_card",   "纯身份证号"),
    ("6222026200000832021",   "bank_card", "纯银行卡号"),
    ("13800138000",           "phone",     "纯手机号"),
    ("user@example.com",     "email",     "纯邮箱"),
    ("北京市朝阳区建国路88号", "address",   "纯地址"),
    ("6228480033812345678",   "bank_card", "19位银行卡"),
    ("18912345678",           "phone",     "18x手机号"),
    ("zhang@company.cn",     "email",     "企业邮箱"),
    ("320110198904064567",    "id_card",   "无前缀身份证"),
    ("上海市浦东新区陆家嘴金融路100号", "address", "上海地址"),
]

LEVEL2_CASES = [
    # 关键词包裹——纯正则未必能处理，ML 上下文特征优势区
    ("身份证号：440101200003150022",    "id_card",   "身份证号前缀"),
    ("证件号码 350203198807160079",     "id_card",   "证件号码前缀"),
    ("银行卡号 6228480033800000000",    "bank_card", "银行卡号前缀"),
    ("付款卡号：4000123456789012",      "bank_card", "付款卡号前缀"),
    ("联系方式：15912345678",           "phone",     "联系方式前缀"),
    ("手机：18600000001",               "phone",     "手机前缀"),
    ("请拨打 19912345678 联系我",       "phone",     "手机号嵌入句中"),
    ("邮箱：zhangsan@company.org",     "email",     "邮箱前缀"),
    ("请发至 admin@data-gov.net",      "email",     "请发至前缀"),
    ("住址：浙江省杭州市西湖区文三路477号", "address", "住址前缀"),
    ("收货地址：广东省深圳市南山区科技园南路10号", "address", "收货地址前缀"),
    ("客户姓名：张伟",                  "name",      "客户姓名前缀"),
    ("联系人 王芳女士",                  "name",      "联系人前缀"),
    ("2023年度合规报告摘要",            "unknown",   "报告文本"),
    ("风险评分：87分，高风险",           "unknown",   "风险评分文本"),
    ("系统日志 2024-01-15 10:32:11",   "unknown",   "日志文本"),
    ("合同编号 HT-2024-001",            "unknown",   "合同编号文本"),
    ("审批流程第3步已完成",              "unknown",   "流程文本"),
]

LEVEL3_CASES = [
    # 困难：短姓名、歧义、无明显特征
    ("张伟",           "name",    "2字姓名"),
    ("李明先生",        "name",    "带称谓姓名"),
    ("赵磊",           "name",    "2字姓名无上下文"),
    ("申请人：陈静",    "name",    "申请人前缀短名"),
    ("实名认证：440101200003150022", "id_card", "实名认证前缀"),
    ("提现到账号 6214850012345678",  "bank_card", "提现到账号"),
    ("发票邮箱 finance@hospital.org", "email", "发票邮箱前缀"),
    ("工作单位：湖北省武汉市洪山区珞瑜路1037号", "address", "工作单位前缀"),
    ("CPU 使用率 68%，内存 72%",    "unknown", "系统指标文本"),
    ("服务响应时间：145ms",          "unknown", "性能指标文本"),
]

ALL_CASES = [
    (text, label, "L1:" + desc, 1) for text, label, desc in LEVEL1_CASES
] + [
    (text, label, "L2:" + desc, 2) for text, label, desc in LEVEL2_CASES
] + [
    (text, label, "L3:" + desc, 3) for text, label, desc in LEVEL3_CASES
]


def call_predict(base_url: str, text: str):
    url = f"{base_url}/predict"
    data = json.dumps({"text": text}).encode("utf-8")
    req = urllib.request.Request(
        url, data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read())


def call_metrics(base_url: str):
    url = f"{base_url}/metrics"
    with urllib.request.urlopen(url, timeout=10) as resp:
        return json.loads(resp.read())


def call_train(base_url: str, samples_path: str):
    url = f"{base_url}/train"
    with open(samples_path, encoding="utf-8") as f:
        data = f.read().encode("utf-8")
    req = urllib.request.Request(
        url, data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())


def run_tests(base_url: str, do_train: bool, samples_path: str):
    print(f"\n🔗 测试目标：{base_url}")
    print("=" * 60)

    # ── 步骤 0：可选先训练 ──────────────────────────────────────────────────
    if do_train:
        print(f"\n⏳ 正在调用 POST /train (数据文件: {samples_path})...")
        try:
            result = call_train(base_url, samples_path)
            print(f"   训练完成：{json.dumps(result, ensure_ascii=False, indent=4)}")
        except Exception as e:
            print(f"   [WARN] /train 调用失败: {e}")

    # ── 步骤 1：逐条预测测试 ────────────────────────────────────────────────
    print(f"\n{'─'*60}")
    print("  📝 逐条分类测试")
    print(f"{'─'*60}")

    results_by_level = collections.defaultdict(lambda: {"pass": 0, "fail": 0, "cases": []})
    all_true, all_pred = [], []

    for text, expected, desc, level in ALL_CASES:
        try:
            resp = call_predict(base_url, text)
            predicted = resp.get("label", "")
            score = resp.get("score", 0)
            ok = predicted == expected
            symbol = "✅" if ok else "❌"
            all_true.append(expected)
            all_pred.append(predicted)
            key = f"L{level}"
            if ok:
                results_by_level[key]["pass"] += 1
            else:
                results_by_level[key]["fail"] += 1
                results_by_level[key]["cases"].append(
                    f"  [{desc}] 预期={expected} 实际={predicted} score={score}"
                )
            print(f"  {symbol} [{desc}]")
            if not ok:
                print(f"       ↳ 预期: {expected}  实际: {predicted}  score: {score}")
        except Exception as e:
            print(f"  ⚠️  [{desc}] 请求失败: {e}")
            results_by_level[f"L{level}"]["fail"] += 1

    # ── 步骤 2：分级汇总 ────────────────────────────────────────────────────
    print(f"\n{'─'*60}")
    print("  📊 分级准确率汇总")
    print(f"{'─'*60}")

    thresholds = {"L1": 1.00, "L2": 0.90, "L3": 0.70}
    level_names = {"L1": "Easy   (结构化)", "L2": "Medium (关键词包裹)", "L3": "Hard   (歧义/短名)"}
    overall_pass = True

    for key in ["L1", "L2", "L3"]:
        r = results_by_level[key]
        total = r["pass"] + r["fail"]
        acc = r["pass"] / total if total else 0
        threshold = thresholds[key]
        ok = acc >= threshold
        if not ok:
            overall_pass = False
        symbol = "✅" if ok else "❌"
        print(f"  {symbol}  {level_names[key]:22s}  {r['pass']}/{total}  ({acc*100:.0f}%)  要求≥{threshold*100:.0f}%")
        for fail_case in r["cases"]:
            print(f"    ⚠️ {fail_case}")

    # Macro-F1 on all test cases
    from sklearn.metrics import f1_score, classification_report as cr
    if all_true:
        macro_f1 = f1_score(all_true, all_pred, average="macro", zero_division=0)
        f1_ok = macro_f1 >= 0.88
        if not f1_ok:
            overall_pass = False
        symbol = "✅" if f1_ok else "❌"
        print(f"  {symbol}  Macro-F1 (全部{len(all_true)}条)  {macro_f1:.4f}  要求≥0.88")

    # ── 步骤 3：/metrics 检查 ───────────────────────────────────────────────
    print(f"\n{'─'*60}")
    print("  🔍 /metrics 端点检查")
    print(f"{'─'*60}")
    try:
        m = call_metrics(base_url)
        ml_stack = next((s for s in m.get("classifier_stack", []) if s["name"] == "ml_classifier"), {})
        bench_acc = ml_stack.get("benchmark_accuracy", 0)
        last = ml_stack.get("last_train_metrics", {})

        bench_ok = bench_acc >= 0.90
        has_test_acc = "test_accuracy" in last
        has_per_class = "per_class" in last

        symbol = "✅" if bench_ok else "❌"
        print(f"  {symbol}  Benchmark 准确率: {bench_acc:.4f}  (30条内置用例, 要求≥0.90)")
        symbol = "✅" if has_test_acc else "⚠️ "
        print(f"  {symbol}  last_train_metrics.test_accuracy 字段存在: {has_test_acc}")
        symbol = "✅" if has_per_class else "⚠️ "
        print(f"  {symbol}  per_class F1 字段存在: {has_per_class}")

        if last:
            print(f"\n  📈 上次训练评估结果:")
            for k, v in last.items():
                if k != "per_class":
                    print(f"     {k}: {v}")
            if "per_class" in last:
                print(f"\n  📊 逐类别 F1:")
                for label, vals in last["per_class"].items():
                    f1 = vals.get("f1", 0)
                    ok = f1 >= 0.85
                    s = "✅" if ok else "⚠️ "
                    print(f"     {s}  {label:12s}  F1={f1:.4f}  P={vals.get('precision',0):.4f}  R={vals.get('recall',0):.4f}")

        if not bench_ok:
            overall_pass = False
    except Exception as e:
        print(f"  ❌ /metrics 调用失败: {e}")
        overall_pass = False

    # ── 最终结论 ────────────────────────────────────────────────────────────
    print(f"\n{'='*60}")
    if overall_pass:
        print("  🎉 所有检查项通过，分类器已达到交付级别！")
    else:
        print("  ⚠️  存在未通过项，建议执行以下操作：")
        print("     1. 确保已用 1000 条数据训练：POST /train 或 python train_offline.py")
        print("     2. 对 F1 < 0.85 的类别补充标注样本（尤其是 name / unknown）")
        print("     3. 如有 GPU，参考 TRAINING.md 进行 BERT 微调以覆盖 Hard 用例")
    print(f"{'='*60}\n")
    return overall_pass


def main():
    parser = argparse.ArgumentParser(description="分类器功能成熟度测试")
    parser.add_argument("--url", default="http://localhost:5000", help="服务地址")
    parser.add_argument("--train", action="store_true", help="测试前先调用 /train")
    parser.add_argument("--samples", default="training_samples.json", help="训练数据文件路径")
    args = parser.parse_args()

    # Check service availability
    try:
        health_url = f"{args.url}/health"
        with urllib.request.urlopen(health_url, timeout=5):
            pass
    except Exception:
        print(f"\n❌ 无法连接到服务 {args.url}")
        print("   请先启动服务：BERT_MOCK=true python app.py\n")
        sys.exit(1)

    success = run_tests(args.url, do_train=args.train, samples_path=args.samples)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
