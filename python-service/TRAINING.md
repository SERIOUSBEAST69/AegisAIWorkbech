# AI 模型训练指南 – 必读

本文档说明 AegisAI Python 服务中每个 AI 组件的**真实实现方式、已知局限，以及你必须亲自完成的工作**。

---

## 一、当前 AI 组件诚实说明

### 1. 敏感数据分类器

**现状**

| 组件 | 是否经过训练 | 训练数据 | 当前准确率 |
|------|------------|---------|----------|
| 正则规则（regex） | 否（硬编码规则） | 无 | 结构化字段 ~95%，混合文本 ~40% |
| ML 分类器（逻辑回归） | **是** | 37 条内置合成样本 | 内置基准 ~93%（仅限内置数据集） |
| BERT 零样本 | **否（未微调）** | 无（零样本） | 未实测，低于正则 |

**关键问题**
- ML 分类器的 37 条训练样本是**合成的、手写的**，不代表真实企业数据分布。
- BERT 使用的是 `bert-base-chinese` 原始权重，未针对敏感数据识别任务进行微调。
  这意味着 BERT 组件实际上是**学术包装**，而非真正的深度学习优势。
- 正则匹配与 ML 分类器在结构化字段上效果相当；ML 的真正优势在于**上下文包裹文本**。

---

### 2. LSTM 风险预测

**现状**
- 每次调用 `/predict/risk` 时**从零训练**一个新 LSTM（无预训练权重）。
- 输入数据来自调用方传入的短序列（通常 7–30 个点），而非数据库历史。
- 没有离线训练，没有真实历史数据支撑。

**关键问题**
- "用过去 7 天数据预测明天"在数学上成立，但置信度极低。
- 在竞赛中若被问"你的 LSTM 在什么数据上训练的、误差多少"，
  目前唯一正直的回答是：**在本次请求传入的短序列上即时拟合，验证集 MAE/RMSE 见返回字段**。

---

## 二、你必须亲自做的事

### 任务 A：为 ML 分类器收集真实标注数据（最优先）

**目标**：每个敏感类别至少 50 条真实数据样本。

**操作步骤**：
1. 从你们的数据库或日志中导出包含个人信息的字段样本（需脱敏处理后再标注）。
2. 按如下格式整理为 JSON：
   ```json
   {
     "samples": [
       {"text": "客户身份证号：410101199001011234", "label": "id_card"},
       {"text": "手机 13800138000", "label": "phone"},
       {"text": "收货地址：北京市朝阳区XX路88号", "label": "address"}
     ]
   }
   ```
3. 调用训练接口：
   ```bash
   curl -X POST http://localhost:5000/train \
     -H "Content-Type: application/json" \
     -d @your_samples.json
   ```
4. 查看返回的 `train_accuracy`；目标：真实数据集上 ≥ 90%。
5. 调用 `GET /metrics` 查看更新后的基准对比。

**预期效果**：在真实企业数据上，ML 分类器准确率应超过纯正则 10–20 个百分点（尤其在混合文本场景）。

---

### 任务 B：BERT 微调（可选，竞赛加分项）

如果你想将 BERT 从"零样本"升级为"真正微调"，需要：

**前置条件**
- GPU（推荐 NVIDIA RTX 3060 或更高，或使用 Colab/AutoDL）
- 每个类别 ≥ 200 条标注样本（共 7 类，约 1400 条）
- 约 2–4 小时训练时间

**步骤概述**

```bash
# 1. 安装额外依赖
pip install accelerate datasets

# 2. 准备数据集（参考 Hugging Face datasets 格式）
# 将标注数据转换为 CSV：text,label

# 3. 编写微调脚本（参考 transformers 官方示例）
# transformers/examples/pytorch/text-classification/run_glue.py
# 替换模型：bert-base-chinese
# 任务：sequence classification，7 个类别

# 4. 保存微调后的模型权重到 ./models/bert_finetuned/

# 5. 修改 app.py 中 classify_text 函数：
#    - 加载微调模型（AutoModelForSequenceClassification）
#    - 用微调模型替换零样本余弦相似度逻辑
#    - 在 /metrics 端点标注 fine_tuned: true
```

**竞赛汇报建议**：
- 汇报微调模型在测试集（独立于训练集的 20% 数据）上的精确率/召回率/F1。
- 与正则基线的对比：在混合文本（如"我的手机是 138XXXX"）上提升多少？
- 说明你自己标注了多少条数据、用了多少 GPU 时间。

---

### 任务 C：LSTM 接入真实历史数据

**目标**：让 LSTM 基于真实历史风险事件数据进行预测。

**步骤**：
1. 在后端 Java 服务中添加一个定时任务（例如每天凌晨 2 点），
   从 `risk_event` 表查询过去 90 天的每日事件计数：
   ```sql
   SELECT DATE(created_at) AS day, COUNT(*) AS cnt
   FROM risk_event
   WHERE created_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
   GROUP BY day
   ORDER BY day;
   ```
2. 将查询结果（90 个数字的列表）作为 `series` 字段调用 `/predict/risk`。
3. 在前端 Home.vue 的"明日风险预估"模块展示 `forecast[0]`，
   并展示 `mae` 和 `rmse` 作为置信度指标。

**评估标准**：当 `mae` 稳定在 `< 2.0`（即每日预测误差不超过 2 起事件），
LSTM 才具备真正的实用价值。如果误差更大，可尝试增加历史数据长度或改用 Prophet。

---

## 三、对评委的诚实声明

本系统 AI 功能的**真实技术深度**：

1. **已实现**：特征工程 + 逻辑回归的可训练分类器，有明确的训练/测试评估框架。
2. **已实现**：LSTM 时序预测，附带验证集 MAE/RMSE，代码可复现。
3. **尚未完成**：BERT 微调（需要 GPU 和真实标注数据）。
4. **尚未完成**：LSTM 接入真实历史数据库（需要后端定时任务）。

**系统的真正技术优势**在于：
- 正则 + ML 分类器的可扩展混合架构，支持增量训练。
- 明确的精度评估和基准对比（GET /metrics）。
- 模型诚实性文档（即本文件）。

---

## 四、快速验证（无需真实数据）

```bash
# 启动服务（mock 模式，不需要 BERT）
BERT_MOCK=true python app.py

# 查看模型信息和基准测试
curl http://localhost:5000/metrics

# 测试分类
curl -X POST http://localhost:5000/predict \
  -H "Content-Type: application/json" \
  -d '{"text": "身份证号：410101199001011234"}'

# 测试风险预测（返回 MAE/RMSE）
curl -X POST http://localhost:5000/predict/risk \
  -H "Content-Type: application/json" \
  -d '{"series": [3,5,4,7,6,8,5,9,7,6,8,10,9,11,8,12,10,9]}'
```
