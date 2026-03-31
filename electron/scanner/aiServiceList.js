/**
 * 比赛限定 AI 服务清单（客户端仅识别此清单）。
 */

'use strict';

const AI_SERVICES = [
  {
    name: '阿里通义系列',
    aliases: ['通义千问', '通义', 'qwen'],
    domains: ['qianwen.aliyun.com', 'tongyi.aliyun.com', 'dashscope.aliyuncs.com'],
    processNames: [],
    category: 'chat',
    riskLevel: 'high',
    description: '阿里通义系列模型服务',
  },
  {
    name: '百度文心系列',
    aliases: ['文心一言', '文心'],
    domains: ['yiyan.baidu.com', 'qianfan.baidubce.com', 'aip.baidubce.com'],
    processNames: [],
    category: 'chat',
    riskLevel: 'high',
    description: '百度文心系列模型服务',
  },
  {
    name: 'DeepSeek',
    aliases: ['deepseek'],
    domains: ['deepseek.com', 'chat.deepseek.com', 'api.deepseek.com'],
    processNames: ['deepseek'],
    category: 'chat',
    riskLevel: 'high',
    description: 'DeepSeek 系列模型服务',
  },
  {
    name: '稿定设计',
    aliases: ['gaoding'],
    domains: ['gaoding.com', 'www.gaoding.com'],
    processNames: ['gaoding'],
    category: 'image',
    riskLevel: 'medium',
    description: '稿定设计智能设计服务',
  },
  {
    name: '和鲸 ModelWhale',
    aliases: ['modelwhale', 'hejing'],
    domains: ['modelwhale.com', 'www.modelwhale.com'],
    processNames: ['modelwhale'],
    category: 'platform',
    riskLevel: 'medium',
    description: '和鲸 ModelWhale 平台',
  },
  {
    name: '即梦',
    aliases: ['jimeng'],
    domains: ['jimeng.jianying.com', 'jimeng.com'],
    processNames: ['jimeng'],
    category: 'image',
    riskLevel: 'medium',
    description: '即梦内容生成服务',
  },
  {
    name: '豆包 AI',
    aliases: ['豆包', 'doubao'],
    domains: ['doubao.com', 'www.doubao.com', 'ark.cn-beijing.volces.com'],
    processNames: ['doubao'],
    category: 'chat',
    riskLevel: 'high',
    description: '豆包 AI 服务',
  },
  {
    name: '科大讯飞星火',
    aliases: ['讯飞星火', 'spark'],
    domains: ['xinghuo.xfyun.cn', 'spark-api.xf-yun.cn'],
    processNames: ['spark'],
    category: 'chat',
    riskLevel: 'high',
    description: '讯飞星火系列模型服务',
  },
  {
    name: 'Kimi',
    aliases: ['kimi', 'moonshot'],
    domains: ['kimi.moonshot.cn', 'moonshot.cn', 'api.moonshot.cn'],
    processNames: ['kimi'],
    category: 'chat',
    riskLevel: 'high',
    description: 'Kimi 系列模型服务',
  },
  {
    name: '腾讯混元系列',
    aliases: ['混元', 'hunyuan'],
    domains: ['hunyuan.tencent.com', 'yuanqi.tencent.com'],
    processNames: [],
    category: 'chat',
    riskLevel: 'high',
    description: '腾讯混元系列模型服务',
  },
  {
    name: '智谱 AI',
    aliases: ['智谱', 'chatglm', 'bigmodel'],
    domains: ['chatglm.cn', 'bigmodel.cn', 'open.bigmodel.cn'],
    processNames: ['chatglm'],
    category: 'chat',
    riskLevel: 'high',
    description: '智谱 AI 系列模型服务',
  },
];

module.exports = { AI_SERVICES };
