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

# 复制应用代码（包含风险评级、训练、对抗模拟相关脚本）
COPY app.py gen_behavior_data.py train_anomaly.py openclaw_adversarial.py openclaw_simulator.py ./
COPY ai_risk_data.json ./

# 若已构建好的模型文件存在，复制进镜像（可选：也可在容器启动后训练）
# COPY models/ ./models/

# 若未提供预训练模型，在构建阶段生成合成数据并训练模型
# 注意：在 CI 环境中可以跳过此步骤，通过 docker-compose 的 command 字段覆盖
RUN python gen_behavior_data.py && python train_anomaly.py

# 暴露端口
EXPOSE 5000

# 启动命令（模型已在构建时训练，直接启动服务即可）
CMD ["python", "app.py"]