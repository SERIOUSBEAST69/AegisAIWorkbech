#!/usr/bin/env python3
"""
AegisAI – BERT Fine-tuning Script
===================================
GPU-accelerated fine-tuning of bert-base-chinese for sensitive-data classification.

Requirements:
    pip install transformers datasets accelerate scikit-learn torch

Usage:
    # Train on synthetic seed data (quick smoke-test, no GPU required):
    python finetune_bert.py --mode seed --output ./models/bert_finetuned

    # Train on a real labeled JSON file (200 samples/class recommended):
    python finetune_bert.py --data ./labeled_data.json --output ./models/bert_finetuned

    # Evaluate an existing fine-tuned checkpoint:
    python finetune_bert.py --evaluate --model-dir ./models/bert_finetuned

JSON data format:
    {"samples": [{"text": "...", "label": "id_card"}, ...]}
    Valid labels: id_card, bank_card, phone, email, address, name, unknown

Output
------
  ./models/bert_finetuned/         – Hugging Face model directory
  ./models/bert_finetuned/metrics.json – Precision/Recall/F1 per class + macro avg
"""
from __future__ import annotations

import argparse
import json
import os
import random
import sys
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
import torch
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split
from torch.utils.data import DataLoader, Dataset
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    EarlyStoppingCallback,
    Trainer,
    TrainingArguments,
    set_seed,
)

# ── Configuration ─────────────────────────────────────────────────────────────
BASE_MODEL = os.environ.get("BERT_MODEL", "bert-base-chinese")
LABELS: List[str] = ["id_card", "bank_card", "phone", "email", "address", "name", "unknown"]
LABEL2ID: Dict[str, int] = {l: i for i, l in enumerate(LABELS)}
ID2LABEL: Dict[int, str] = {i: l for l, i in LABEL2ID.items()}

MAX_LENGTH = 128
SEED = 42
EPOCHS = 5
BATCH_SIZE = 16
LEARNING_RATE = 2e-5
WARMUP_RATIO = 0.1
WEIGHT_DECAY = 0.01

# ── Synthetic seed data ──────────────────────────────────────────────────────
# These are seed samples for bootstrapping/testing the fine-tuning pipeline.
# Production training requires ≥ 200 samples/class. To expand:
#   1. Add real annotated data to each _*_samples list below, OR
#   2. Pass a CSV/JSONL file via --data_path argument to this script.
_SEED_SAMPLES: List[Tuple[str, str]] = []

# id_card: 30 seed samples (expand to ≥ 200 for production)
_id_card_samples = [
    "身份证号：410101199001011234",
    "证件号码 350203198807160079",
    "ID: 320102196801210016",
    "请提供身份证: 440101200003150022",
    "110101199003077777",
    "用户身份证: 330302198607154321",
    "身份证号码是320123197006284567",
    "顾客证件号码：420101198512032345",
    "客户的ID证号是510104199207013456",
    "企业法人身份证：310101196503021234",
    "办理业务请出示身份证号：610101200010104567",
    "认证身份: 210102198904025678",
    "请核对证件号码: 130102199508036789",
    "身份证: 430101199612040987",
    "证件: 650101197805028765",
    "360101197005042345",
    "140101198303051234",
    "450101200106062345",
    "520101199707073456",
    "370101199208084567",
    "230101199809095678",
    "410101198001016789",
    "630101200302020987",
    "身份证号01: 820101197504231234",
    "身份证号02: 460101199602032345",
    "身份证号03: 350101198703043456",
    "证件字段：340101199904054567",
    "核验证件：290101200505065678",
    "填写证件号：810101196806076789",
    "员工证件：710101197607080987",
]
for s in _id_card_samples:
    _SEED_SAMPLES.append((s, "id_card"))

# bank_card: 30 seed samples (expand to ≥ 200 for production)
_bank_card_samples = [
    "6222026200000832021",
    "银行卡号 6228480033800000000",
    "卡号：6214850000000000",
    "账号: 6226090000000001",
    "储蓄卡 6228450000000000000",
    "信用卡号: 4111111111111111",
    "银行账户：6212261302048829412",
    "绑定银行卡: 6225760021582350",
    "存折账号：6205050006241607697",
    "借记卡: 6222081012001782519",
    "工商银行卡: 6212260201002765432",
    "中国银行卡号: 6013820800004456789",
    "农业银行: 9559982001528888888",
    "建设银行: 6217003820000000001",
    "交通银行: 6222600260001234567",
    "招商银行: 6214830126001234567",
    "中信银行卡: 6217920011234567890",
    "浦发银行: 6228480426001234567",
    "民生银行: 6226220311234567890",
    "光大银行卡号: 6259650311234567890",
    "账户信息: 6225768411234567890",
    "付款卡: 6217581811234567890",
    "还款卡: 6222026111234567890",
    "绑卡: 6228480011234567890",
    "开卡信息: 6214851011234567890",
    "6230580011234567890",
    "6253861511234567890",
    "6228461011234567890",
    "6217773011234567890",
    "6239659511234567890",
]
for s in _bank_card_samples:
    _SEED_SAMPLES.append((s, "bank_card"))

# phone: 30 seed samples (expand to ≥ 200 for production)
_phone_samples = [
    "13800138000",
    "联系方式：15912345678",
    "手机号 18600000001",
    "电话:17712345678",
    "请拨打 19912345678 联系我",
    "手机: 13612345678",
    "联系电话：14712345678",
    "移动号码: 15812345678",
    "号码：13312345678",
    "请致电: 18012345678",
    "电话号码: 17012345678",
    "客服手机: 13112345678",
    "备用电话：16912345678",
    "紧急联系: 19112345678",
    "手机号码 13412345678",
    "拨打 15512345678 进行预约",
    "联系人电话：18512345678",
    "业务手机号: 17312345678",
    "本人手机: 13512345678",
    "亲手机: 14012345678",
    "用户手机: 14512345678",
    "顾客手机: 17812345678",
    "法人手机: 16312345678",
    "联系方式：16612345678",
    "请回电: 19312345678",
    "手机13912345678",
    "Tel: 15012345678",
    "Phone: 16512345678",
    "mobile: 17512345678",
    "call: 13712345678",
]
for s in _phone_samples:
    _SEED_SAMPLES.append((s, "phone"))

# email: 30 seed samples (expand to ≥ 200 for production)
_email_samples = [
    "user@example.com",
    "邮箱：zhangsan@company.org",
    "Email: test.user+tag@sub.domain.cn",
    "请发至 hello.world@aegis.io",
    "联系邮件 admin@data-gov.net",
    "电子邮件: support@trustai.com",
    "发邮件给 ceo@startup.io",
    "工作邮箱: hr@corp.cn",
    "邮件地址: info@gov.cn",
    "请邮件联系: legal@lawfirm.cn",
    "学校邮箱: student@edu.cn",
    "邮箱地址是 sales@vendor.com",
    "联系 marketing@agency.co",
    "partner@alliance.org",
    "contact@service.net",
    "privacy@platform.cn",
    "gdpr@company.eu",
    "dpo@enterprise.com",
    "security@bank.com",
    "compliance@finance.cn",
    "audit@government.cn",
    "report@media.com",
    "feedback@app.io",
    "news@portal.cn",
    "invest@fund.cn",
    "it@tech.com",
    "dev@startup.ai",
    "ops@cloud.cn",
    "data@analytics.cn",
    "研究员邮箱: researcher@lab.edu.cn",
]
for s in _email_samples:
    _SEED_SAMPLES.append((s, "email"))

# address: 30 seed samples (expand to ≥ 200 for production)
_address_samples = [
    "北京市朝阳区建国路88号",
    "上海市浦东新区陆家嘴金融贸易区1号",
    "广东省深圳市南山区科技园南路",
    "住址：浙江省杭州市西湖区文三路477号",
    "江苏省南京市鼓楼区中山路123号3单元402室",
    "四川省成都市武侯区天府大道100号",
    "湖北省武汉市武昌区中南路1号",
    "山东省青岛市市南区香港中路18号",
    "辽宁省沈阳市和平区中华路1号",
    "陕西省西安市雁塔区高新路18号",
    "河南省郑州市金水区花园路1号",
    "湖南省长沙市岳麓区枫林路19号",
    "安徽省合肥市庐阳区长江中路1号",
    "福建省福州市台江区五一中路82号",
    "重庆市渝中区解放碑商业区1号",
    "天津市和平区南京路66号",
    "贵州省贵阳市云岩区中山西路1号",
    "云南省昆明市五华区人民中路19号",
    "广西壮族自治区南宁市青秀区民族大道68号",
    "黑龙江省哈尔滨市道里区中央大街1号",
    "吉林省长春市朝阳区人民大街9号",
    "内蒙古自治区呼和浩特市回民区中山路1号",
    "甘肃省兰州市城关区庆阳路20号",
    "新疆维吾尔自治区乌鲁木齐市天山区人民路1号",
    "住所：山西省太原市迎泽区迎泽大街1号",
    "实际地址：河北省石家庄市长安区中山路87号",
    "收件地址：海南省海口市龙华区海秀路1号",
    "户籍地址：宁夏回族自治区银川市兴庆区解放东街1号",
    "居住地：江西省南昌市东湖区阳明路12号",
    "通讯地址：西藏自治区拉萨市城关区林廓路1号",
]
for s in _address_samples:
    _SEED_SAMPLES.append((s, "address"))

# name: 30 samples
_name_samples = [
    "张伟",
    "李明先生",
    "王芳女士",
    "客户姓名：赵磊",
    "联系人 陈静老师",
    "负责人: 刘强",
    "姓名：吴秀英",
    "经理：孙超",
    "法人代表：周建国",
    "签名人：郑晓华",
    "甲方代表: 冯志远",
    "乙方负责人：蒋文博",
    "本人: 何家豪",
    "当事人：林晓明",
    "报告人：余雯婷",
    "申请人：曾德胜",
    "委托人：谢志强",
    "被告：黄建平",
    "原告代理人：梁淑华",
    "投诉人：邓国强",
    "用户昵称：小明同学",
    "真实姓名：陆梦洁",
    "完整姓名：侯雅芳",
    "姓名信息：范丹丹",
    "作者：方志远",
    "调查对象：唐建华",
    "教师：龙春燕",
    "院长：史文明",
    "书记：杨国胜",
    "主任：高晓华",
]
for s in _name_samples:
    _SEED_SAMPLES.append((s, "name"))

# unknown: 30 samples
_unknown_samples = [
    "2023年度合规报告摘要",
    "风险评分：87分，中等风险",
    "数据治理中心第三季度审计",
    "系统日志 2024-01-15 10:32:11 INFO",
    "合同编号 HT-2024-001",
    "产品名称：智能数据安全网关",
    "描述：用户行为分析模块初始化完成",
    "会议记录：治理委员会第五次例会",
    "版本号: v2.3.1-beta",
    "错误码: ERR_401_UNAUTHORIZED",
    "操作记录：导出数据报表",
    "备注：定期审计任务完成",
    "项目名称：AegisAI 智能合规平台",
    "状态: 处理中",
    "优先级: P0",
    "工单号: WO-20240315-001",
    "标签: 数据安全, AI治理",
    "策略说明：访问控制矩阵更新",
    "审批意见：同意申请",
    "分类结果: 未知类型",
    "评分: 92.5",
    "频率: 每日一次",
    "来源: 内部审计系统",
    "格式: JSON",
    "算法: SHA-256",
    "操作系统: Linux 5.4",
    "服务名称: user-service",
    "端口: 8080",
    "协议: HTTPS",
    "数据量: 1.2GB",
]
for s in _unknown_samples:
    _SEED_SAMPLES.append((s, "unknown"))


# ── Dataset ────────────────────────────────────────────────────────────────────
class SensitiveDataset(Dataset):
    def __init__(self, texts: List[str], labels: List[int], tokenizer, max_length: int):
        self.encodings = tokenizer(texts, truncation=True, padding="max_length",
                                   max_length=max_length, return_tensors="pt")
        self.labels = torch.tensor(labels, dtype=torch.long)

    def __len__(self):
        return len(self.labels)

    def __getitem__(self, idx):
        item = {k: v[idx] for k, v in self.encodings.items()}
        item["labels"] = self.labels[idx]
        return item


def compute_metrics(eval_pred):
    logits, labels = eval_pred
    preds = np.argmax(logits, axis=-1)
    report = classification_report(labels, preds,
                                   target_names=LABELS,
                                   output_dict=True,
                                   zero_division=0)
    return {
        "accuracy":      report["accuracy"],
        "macro_f1":      report["macro avg"]["f1-score"],
        "macro_precision": report["macro avg"]["precision"],
        "macro_recall":  report["macro avg"]["recall"],
    }


def load_samples_from_json(path: str) -> List[Tuple[str, str]]:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    samples = data.get("samples", [])
    result = []
    for item in samples:
        text = item.get("text", "").strip()
        label = item.get("label", "unknown").strip()
        if text and label in LABEL2ID:
            result.append((text, label))
    return result


def finetune(samples: List[Tuple[str, str]], output_dir: str) -> Dict:
    set_seed(SEED)
    random.shuffle(samples)

    texts = [t for t, _ in samples]
    label_ids = [LABEL2ID[l] for _, l in samples]

    x_train, x_val, y_train, y_val = train_test_split(
        texts, label_ids, test_size=0.2, random_state=SEED, stratify=label_ids
    )

    print(f"[finetune] train={len(x_train)}, val={len(x_val)}, classes={len(LABELS)}")
    print(f"[finetune] Base model: {BASE_MODEL}")
    print(f"[finetune] Device: {'GPU (' + torch.cuda.get_device_name(0) + ')' if torch.cuda.is_available() else 'CPU'}")

    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)
    model = AutoModelForSequenceClassification.from_pretrained(
        BASE_MODEL,
        num_labels=len(LABELS),
        id2label=ID2LABEL,
        label2id=LABEL2ID,
        ignore_mismatched_sizes=True,
    )

    train_dataset = SensitiveDataset(x_train, y_train, tokenizer, MAX_LENGTH)
    val_dataset   = SensitiveDataset(x_val,   y_val,   tokenizer, MAX_LENGTH)

    fp16 = torch.cuda.is_available()
    training_args = TrainingArguments(
        output_dir=output_dir,
        num_train_epochs=EPOCHS,
        per_device_train_batch_size=BATCH_SIZE,
        per_device_eval_batch_size=BATCH_SIZE,
        learning_rate=LEARNING_RATE,
        warmup_ratio=WARMUP_RATIO,
        weight_decay=WEIGHT_DECAY,
        fp16=fp16,
        eval_strategy="epoch",
        save_strategy="epoch",
        load_best_model_at_end=True,
        metric_for_best_model="macro_f1",
        logging_steps=10,
        report_to=[],
        seed=SEED,
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=val_dataset,
        compute_metrics=compute_metrics,
        callbacks=[EarlyStoppingCallback(early_stopping_patience=2)],
    )

    trainer.train()
    eval_result = trainer.evaluate()
    print(f"[finetune] Final metrics: {eval_result}")

    # Save model and tokenizer
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    trainer.save_model(output_dir)
    tokenizer.save_pretrained(output_dir)

    # Save metrics
    metrics_path = Path(output_dir) / "metrics.json"
    preds_output = trainer.predict(val_dataset)
    preds = np.argmax(preds_output.predictions, axis=-1)
    report = classification_report(y_val, preds,
                                   target_names=LABELS,
                                   output_dict=True,
                                   zero_division=0)
    with open(metrics_path, "w", encoding="utf-8") as f:
        json.dump({
            "base_model": BASE_MODEL,
            "train_samples": len(x_train),
            "val_samples": len(x_val),
            "eval_accuracy": report["accuracy"],
            "macro_f1": report["macro avg"]["f1-score"],
            "macro_precision": report["macro avg"]["precision"],
            "macro_recall": report["macro avg"]["recall"],
            "per_class": {
                lbl: report.get(lbl, {}) for lbl in LABELS
            },
            "fine_tuned": True,
        }, f, ensure_ascii=False, indent=2)
    print(f"[finetune] Metrics saved to {metrics_path}")
    return report


def evaluate_checkpoint(model_dir: str):
    """Evaluate an existing fine-tuned checkpoint on the seed data."""
    print(f"[evaluate] Loading model from {model_dir}")
    tokenizer = AutoTokenizer.from_pretrained(model_dir)
    model = AutoModelForSequenceClassification.from_pretrained(model_dir)
    model.eval()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device)

    texts = [t for t, _ in _SEED_SAMPLES]
    label_ids = [LABEL2ID[l] for _, l in _SEED_SAMPLES]
    dataset = SensitiveDataset(texts, label_ids, tokenizer, MAX_LENGTH)
    loader = DataLoader(dataset, batch_size=32)

    all_preds = []
    with torch.no_grad():
        for batch in loader:
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            token_type_ids = batch.get("token_type_ids")
            if token_type_ids is not None:
                token_type_ids = token_type_ids.to(device)
                outputs = model(input_ids=input_ids,
                                attention_mask=attention_mask,
                                token_type_ids=token_type_ids)
            else:
                outputs = model(input_ids=input_ids, attention_mask=attention_mask)
            preds = torch.argmax(outputs.logits, dim=-1).cpu().numpy()
            all_preds.extend(preds.tolist())

    print(classification_report(label_ids, all_preds, target_names=LABELS, zero_division=0))


def main():
    parser = argparse.ArgumentParser(description="AegisAI BERT fine-tuning")
    parser.add_argument("--mode", choices=["seed", "data"], default="seed",
                        help="Training mode: 'seed' = built-in samples; 'data' = external JSON file")
    parser.add_argument("--data", type=str, default=None,
                        help="Path to labeled JSON file (required when --mode data)")
    parser.add_argument("--output", type=str, default="./models/bert_finetuned",
                        help="Output directory for fine-tuned model")
    parser.add_argument("--evaluate", action="store_true",
                        help="Only evaluate an existing checkpoint (--model-dir required)")
    parser.add_argument("--model-dir", type=str, default="./models/bert_finetuned",
                        help="Model directory for evaluation")
    args = parser.parse_args()

    if args.evaluate:
        evaluate_checkpoint(args.model_dir)
        return

    if args.mode == "seed":
        samples = list(_SEED_SAMPLES)
        print(f"[main] Using {len(samples)} synthetic seed samples")
    else:
        if not args.data:
            print("[ERROR] --data path is required when --mode data", file=sys.stderr)
            sys.exit(1)
        extra = load_samples_from_json(args.data)
        if not extra:
            print("[ERROR] No valid samples found in data file", file=sys.stderr)
            sys.exit(1)
        # Combine seed + real data (seed prevents class collapse on small datasets)
        samples = list(_SEED_SAMPLES) + extra
        print(f"[main] Using {len(extra)} real samples + {len(_SEED_SAMPLES)} seed samples = {len(samples)} total")

    per_class = {}
    for text, label in samples:
        per_class[label] = per_class.get(label, 0) + 1
    print(f"[main] Class distribution: {per_class}")

    finetune(samples, args.output)
    print(f"[main] Fine-tuned model saved to: {args.output}")
    print("[main] To use in production: set MODEL_DIR env var and restart the Flask service.")
    print("[main] The service will auto-detect bert_finetuned/ and switch to the fine-tuned classifier.")


if __name__ == "__main__":
    main()
