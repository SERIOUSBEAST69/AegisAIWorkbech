"""
生成 ML 分类器训练数据
目标：1000 条，7 个标签，每类 ~143 条
格式：{"samples": [{"text": "...", "label": "..."}, ...]}
"""
import json
import random
import string
from pathlib import Path

random.seed(42)

# ── 辅助函数 ──────────────────────────────────────────────

def rand_digits(n):
    return "".join(random.choices(string.digits, k=n))

def rand_choice(lst):
    return random.choice(lst)

# ── 各类别生成器 ──────────────────────────────────────────

# 1. id_card  —— 18 位身份证
PROVINCES = [
    "110101","120102","130103","140104","150105",
    "210106","220107","230108","310109","320110",
    "330111","340112","350113","360114","370115",
    "410116","420117","430118","440119","450120",
    "460121","500122","510123","520124","530125",
    "540126","610127","620128","630129","640130","650131",
]
def gen_id_card():
    area = rand_choice(PROVINCES)
    year = random.randint(1950, 2005)
    month = str(random.randint(1, 12)).zfill(2)
    day = str(random.randint(1, 28)).zfill(2)
    seq = rand_digits(3)
    checksum = rand_choice("0123456789X")
    num = f"{area}{year}{month}{day}{seq}{checksum}"
    prefixes = [
        "{}",
        "身份证号：{}",
        "证件号码 {}",
        "ID号：{}",
        "身份证：{}",
        "请核对证件号 {}",
        "客户身份证 {}",
        "本人身份证号为 {}",
        "核验证件：{}",
        "证件信息：{}",
        "居民身份证：{}",
        "实名认证身份证号 {}",
        "提交材料：身份证号 {}",
        "证件号 {}，请核实",
        "身份核验：{}",
        "入职材料 - 身份证 {}",
        "合同签署方身份证号：{}",
        "账户实名认证：{}",
        "核身证件：{}，有效期至 {}年".format("{}", random.randint(2025, 2035)),
        "用户 ID 证件：{}",
    ]
    tpl = rand_choice(prefixes)
    return tpl.format(num) if tpl.count("{}") == 1 else tpl.format(num)

# 2. bank_card  —— 16~19 位银行卡
BANK_PREFIXES = [
    "6222","6228","6214","6226","6217",
    "6212","6227","6216","6219","6232",
    "4000","5200","3782","3714","6011",
]
def gen_bank_card():
    prefix = rand_choice(BANK_PREFIXES)
    length = random.choice([16, 17, 18, 19])
    remaining = length - len(prefix)
    num = prefix + rand_digits(remaining)
    prefixes = [
        "{}",
        "银行卡号 {}",
        "卡号：{}",
        "账号: {}",
        "储蓄卡 {}",
        "借记卡卡号：{}",
        "信用卡号 {}",
        "收款账户 {}",
        "付款卡号：{}",
        "绑定银行卡 {}",
        "提现到账号 {}",
        "转账账户：{}",
        "银联卡 {}",
        "开户行账号：{}",
        "财务报销账户 {}",
        "结算卡号 {}",
        "付款账户信息：{}",
        "公司对公账户 {}",
        "客户绑卡：{}",
        "收款方卡号 {}",
    ]
    return rand_choice(prefixes).format(num)

# 3. phone  —— 11 位手机号
PHONE_HEADS = [
    "130","131","132","133","134","135","136","137","138","139",
    "150","151","152","153","155","156","157","158","159",
    "170","171","173","175","176","177","178",
    "180","181","182","183","184","185","186","187","188","189",
    "191","193","196","197","198","199",
]
def gen_phone():
    num = rand_choice(PHONE_HEADS) + rand_digits(8)
    prefixes = [
        "{}",
        "联系方式：{}",
        "手机号 {}",
        "电话:{}",
        "请拨打 {} 联系我",
        "手机：{}",
        "联系电话 {}",
        "客服热线 {}",
        "紧急联系人手机：{}",
        "预留手机号 {}",
        "验证手机 {}",
        "绑定手机号：{}",
        "用户手机 {}",
        "短信通知：{}",
        "注册手机号 {}",
        "联系人：{} (手机)",
        "收件人电话：{}",
        "投诉热线：{}",
        "紧急联系：{}",
        "主叫号码：{}",
        "被叫号码 {}",
        "客户手机号码：{}",
    ]
    return rand_choice(prefixes).format(num)

# 4. email
DOMAINS = [
    "gmail.com","163.com","qq.com","126.com","outlook.com",
    "hotmail.com","sina.com","foxmail.com","company.cn",
    "aegis.io","data-gov.net","example.org","corp.com",
    "university.edu.cn","hospital.org","finance.gov.cn",
]
FIRST_NAMES = [
    "zhang","li","wang","liu","chen","yang","huang","zhao","wu","zhou",
    "sun","zhu","lin","guo","he","ma","luo","liang","song","tang",
    "xiao","han","cao","xu","deng","wei","peng","dong","xie","yu",
]
def gen_email():
    user = rand_choice(FIRST_NAMES) + rand_choice([
        str(random.randint(1, 999)),
        "_" + rand_choice(FIRST_NAMES),
        "." + rand_choice(FIRST_NAMES),
        str(random.randint(1990, 2005)),
        "_work","_hr","_biz",
    ])
    domain = rand_choice(DOMAINS)
    addr = f"{user}@{domain}"
    prefixes = [
        "{}",
        "邮箱：{}",
        "Email: {}",
        "请发至 {}",
        "联系邮件 {}",
        "电子邮箱 {}",
        "工作邮箱：{}",
        "注册邮箱 {}",
        "通知邮件地址：{}",
        "发票接收邮箱 {}",
        "账户绑定邮箱：{}",
        "合同抄送 {}",
        "用户邮件：{}",
        "客户邮箱 {}",
        "联系方式（邮件）：{}",
        "系统邮件：{}",
        "报告收件人：{}",
        "邮件通知：{}",
        "请回复至 {}",
        "HR 联系邮箱 {}",
    ]
    return rand_choice(prefixes).format(addr)

# 5. address
PROVINCES_CN = ["北京","上海","广东","浙江","江苏","四川","湖南","湖北","福建","山东",
                 "河南","河北","陕西","辽宁","安徽","云南","重庆","天津","黑龙江","吉林"]
CITIES = {
    "北京": ["朝阳区","海淀区","丰台区","东城区","西城区"],
    "上海": ["浦东新区","静安区","徐汇区","长宁区","黄浦区"],
    "广东": ["广州市天河区","深圳市南山区","深圳市福田区","东莞市","佛山市"],
    "浙江": ["杭州市西湖区","杭州市余杭区","宁波市海曙区","温州市鹿城区","绍兴市"],
    "江苏": ["南京市鼓楼区","苏州市工业园区","无锡市滨湖区","常州市新北区","徐州市"],
    "四川": ["成都市锦江区","成都市高新区","绵阳市","德阳市","泸州市"],
    "湖南": ["长沙市岳麓区","长沙市开福区","株洲市","湘潭市","衡阳市"],
    "湖北": ["武汉市武昌区","武汉市洪山区","宜昌市","荆州市","襄阳市"],
    "福建": ["福州市鼓楼区","厦门市思明区","泉州市丰泽区","漳州市","莆田市"],
    "山东": ["济南市历下区","青岛市市南区","烟台市","潍坊市","淄博市"],
    "河南": ["郑州市金水区","洛阳市涧西区","开封市","南阳市","新乡市"],
    "河北": ["石家庄市桥西区","唐山市路南区","保定市","廊坊市","邯郸市"],
    "陕西": ["西安市雁塔区","西安市碑林区","咸阳市","宝鸡市","榆林市"],
    "辽宁": ["沈阳市和平区","大连市中山区","鞍山市","抚顺市","锦州市"],
    "安徽": ["合肥市庐阳区","芜湖市镜湖区","蚌埠市","阜阳市","安庆市"],
    "云南": ["昆明市盘龙区","昆明市官渡区","大理市","丽江市","玉溪市"],
    "重庆": ["渝中区","江北区","沙坪坝区","南岸区","渝北区"],
    "天津": ["和平区","河西区","南开区","河东区","红桥区"],
    "黑龙江": ["哈尔滨市道里区","齐齐哈尔市","牡丹江市","佳木斯市","大庆市"],
    "吉林": ["长春市朝阳区","长春市宽城区","吉林市","四平市","延边州"],
}
ROADS = ["中山路","建国路","人民路","解放路","科技路","创业路","文化路","工业路",
         "长安街","滨江大道","天府大道","金融街","高新路","学府路","北京路"]
def gen_address():
    prov = rand_choice(PROVINCES_CN)
    city_district = rand_choice(CITIES[prov])
    road = rand_choice(ROADS)
    num = random.randint(1, 999)
    suffixes = [
        f"{num}号",
        f"{num}号{random.randint(1,30)}楼",
        f"{num}号{random.randint(1,10)}单元{random.randint(101,3001)}室",
        f"{num}栋{random.randint(1,30)}层",
        f"{num}号院",
    ]
    base = f"{prov}{city_district}{road}{rand_choice(suffixes)}"
    prefixes = [
        "{}",
        "住址：{}",
        "收货地址：{}",
        "通讯地址 {}",
        "注册地址：{}",
        "办公地址 {}",
        "快递寄至：{}",
        "联系地址：{}",
        "户籍地址 {}",
        "发票地址：{}",
        "发货地址 {}",
        "企业注册地：{}",
        "用户地址：{}",
        "工作单位地址：{}",
        "邮寄地址 {}",
        "家庭住址：{}",
        "合同签署地址 {}",
        "档案地址 {}",
        "当前住所：{}",
        "送达地址：{}",
    ]
    return rand_choice(prefixes).format(base)

# 6. name  —— 中文姓名
SURNAMES = [
    "张","李","王","刘","陈","杨","黄","赵","吴","周",
    "孙","朱","林","郭","何","马","罗","梁","宋","唐",
    "萧","韩","曹","许","邓","魏","彭","董","谢","余",
    "吕","苏","叶","程","蒋","史","钱","方","卢","冯",
    "丁","江","薛","潘","范","沈","谭","白","侯","邹",
]
GIVEN_NAMES = [
    "伟","芳","娜","秀英","敏","静","丽","强","磊","洋",
    "艳","勇","军","杰","娟","涛","明","超","秀兰","霞",
    "平","刚","桂英","华","玲","辉","莹","燕","云","建国",
    "志远","雨萌","子轩","梓涵","浩然","欣怡","嘉怡","若曦","梦瑶","天佑",
    "晓明","向阳","海燕","大海","红梅","建军","玉兰","爱国","文革","建华",
]
def gen_name():
    name = rand_choice(SURNAMES) + rand_choice(GIVEN_NAMES)
    suffixes = [
        "", "先生", "女士", "小姐", "老师", "经理", "主任", "总监", "工程师", "博士",
        "医生", "律师", "会计", "同学", "同志",
    ]
    name_with_title = name + rand_choice(suffixes)
    prefixes = [
        "{}",
        "姓名：{}",
        "客户姓名：{}",
        "联系人 {}",
        "申请人：{}",
        "用户昵称 {}",
        "投诉人：{}",
        "患者姓名 {}",
        "学生姓名：{}",
        "员工：{}",
        "责任人：{}",
        "经办人 {}",
        "授权人：{}",
        "被保险人：{}",
        "合同甲方：{}",
        "签字人 {}",
        "担保人：{}",
        "收件人：{}",
        "申报人 {}",
        "操作员：{}",
    ]
    return rand_choice(prefixes).format(name_with_title)

# 7. unknown  —— 非敏感文本
UNKNOWN_TEMPLATES = [
    "2023年度合规报告摘要",
    "风险评分：{}分，{}风险",
    "系统日志 2024-{}-{} {}:{}:{}",
    "合同编号 HT-2024-{}",
    "订单号：ORD-{}-{}",
    "设备 ID：DEV-{}",
    "IP地址访问记录：{}",
    "操作类型：{}",
    "模块：{}管理",
    "状态：{}",
    "审批流程第{}步已完成",
    "数据备份任务 [{}] 执行成功",
    "安全扫描完成，发现 {} 个高危漏洞",
    "用户登录失败次数：{}",
    "最近操作时间：2024-{}-{}",
    "系统版本 v{}.{}.{}",
    "CPU 使用率 {}%，内存 {}%",
    "日志级别：{}",
    "告警 ID：ALERT-{}",
    "策略版本：v{}",
    "合规检查通过率：{}%",
    "数据资产分类：{}",
    "扫描任务进度：{}%",
    "审计日志记录数：{}",
    "风险事件已处理 {} 件",
    "季度合规报告已生成",
    "AI 模型调用次数本月 {} 次",
    "权限申请已于 {} 分钟前批准",
    "系统健康检查全部通过",
    "配置更新成功，重启生效",
    "数据库连接池空闲连接数：{}",
    "服务响应时间：{}ms",
    "任务队列积压：{}条",
    "本次扫描耗时 {} 秒",
    "文件完整性校验通过",
    "用户组 {} 权限已更新",
    "报告已导出至 /reports/{}.pdf",
    "定时任务 {} 已触发",
    "邮件通知已发送 {} 封",
    "访问控制规则匹配成功",
]

RISK_LEVELS = ["低","中","高","极高","极低"]
OPERATIONS = ["查询","修改","删除","导出","导入","审核","授权","撤销","锁定","解锁"]
STATUS_VALS = ["已完成","处理中","待审核","已关闭","已归档","已驳回"]
MODULES = ["用户","权限","数据资产","合规策略","风险","审计","告警","模型","扫描任务"]
LOG_LEVELS = ["INFO","WARN","ERROR","DEBUG","FATAL","TRACE"]

def gen_unknown():
    tpl = rand_choice(UNKNOWN_TEMPLATES)
    # fill in all {} placeholders
    filled = ""
    i = 0
    while "{}" in tpl:
        choices = [
            str(random.randint(1, 100)),
            rand_choice(RISK_LEVELS),
            rand_choice(OPERATIONS),
            rand_choice(STATUS_VALS),
            rand_choice(MODULES),
            rand_choice(LOG_LEVELS),
            str(random.randint(1, 12)).zfill(2),
            str(random.randint(1, 28)).zfill(2),
            str(random.randint(0, 23)).zfill(2),
            str(random.randint(0, 59)).zfill(2),
            rand_digits(6),
            f"{random.randint(0,9)}.{random.randint(0,9)}",
        ]
        tpl = tpl.replace("{}", rand_choice(choices), 1)
        i += 1
        if i > 10:
            break
    return tpl

# ── 主生成逻辑 ────────────────────────────────────────────

GENERATORS = {
    "id_card":   (gen_id_card,   150),
    "bank_card": (gen_bank_card, 140),
    "phone":     (gen_phone,     160),
    "email":     (gen_email,     140),
    "address":   (gen_address,   150),
    "name":      (gen_name,      140),
    "unknown":   (gen_unknown,   120),
}
# total = 1000

samples = []
for label, (fn, count) in GENERATORS.items():
    for _ in range(count):
        samples.append({"text": fn(), "label": label})

random.shuffle(samples)

output = {"samples": samples}
out_path = Path(__file__).parent / "training_samples.json"
out_path.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")

print(f"✅ 生成完成：{len(samples)} 条样本 → {out_path}")
from collections import Counter
cnt = Counter(s["label"] for s in samples)
for label, n in sorted(cnt.items()):
    print(f"  {label:12s}: {n} 条")
