# 基础镜像
FROM python:3.11-slim

# 配置国内源（全局生效）
ENV PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
ENV PIP_TRUSTED_HOST=mirrors.aliyun.com

# 设置工作目录
WORKDIR /app

# 升级 pip
RUN pip install --upgrade pip

# 复制依赖文件并安装（添加 PyTorch CPU 索引）
COPY requirements.txt ./
RUN pip install --no-cache-dir --default-timeout=1000 \
    --extra-index-url https://download.pytorch.org/whl/cpu \
    -r requirements.txt

# 复制应用代码（包含风险评级、训练、MLOps、对抗模拟相关脚本）
COPY app.py gen_behavior_data.py train_anomaly.py train_offline.py finetune_bert.py ./
COPY data_factory.py model_release_manager.py mlops_registry.py ./
COPY openclaw_adversarial.py openclaw_simulator.py ./
COPY ai_risk_data.json ./

# 若已构建好的模型文件存在，复制进镜像（可选：也可在容器启动后训练）
# COPY models/ ./models/

# 模型在容器启动时按需自动训练；构建阶段只安装依赖与应用代码，避免长时间卡在镜像构建。

# 暴露端口
EXPOSE 5000

# 启动命令（模型已在构建时训练，直接启动服务即可）
CMD ["python", "app.py"]