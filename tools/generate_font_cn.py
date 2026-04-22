#!/usr/bin/env python3
"""Generate font_cn.png for zelda3 Chinese language support.

Creates a bitmap font image with 16x16 pixel CJK characters arranged in a grid.
The image uses palette mode with 4 colors (matching 2BPP SNES format):
  0 = transparent, 1 = color1, 2 = color2, 3 = color3

Layout:
  - 32 characters per row
  - First 112 entries: Latin/symbol characters (from US font, reused in encode_font_cn)
  - Remaining entries: 1118 CJK characters at 16x16

This script only generates the CJK portion. The US font portion is handled
by encode_font_cn() which reads from the existing US font.
"""

import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'assets'))

from PIL import Image, ImageFont, ImageDraw

# The sorted CJK characters from dialogue_cn.txt
CJK_CHARS = list(
  '一七万三上下不与专且世东丢两严丧个中丰临为主丽举久么义之乌乎也书买乱了予争事于井亚些亡交亮亲人什仅仇今从他付代令以们件价任份仿企伏伐休众优伙会伟传伤伪伴伸似但位低住体何佛作你使侬侵便保信倒候借值假做停健像儿兄充先光克免兔入全公共关兴兵其具内再冒军冰冲决冻冽准减凛凝几凭出击分切划创利别到制刺刻削前剑剩副力办功加务动助努励劲劳势勇勉勤勾匙匠匿十千升午半华卓卖卜占卡卢卦卫印危即却厅历厌厚原去又及友双反发叔取受变口古另只叫召可史右号吃各合吉同名后向吗吞吧听吱吵吸吹呀呃呆告呢呣周呱味呵呼命和咒咔咕咯咳咻品哇哈响哎哟哥哦哪哼唉唔唯商啊啦喂善喔喜喝喷嗯嘛嘟嘣嘿噜噢器噬囚四回因团园困围固国图土圣在地场坏坐块型埋城基堂堕堡堵塔塘塞墓墙增士壮声处备复外多夜够大天太夫失头夺奇奖套奥女奸她好如妖妙妨姆始姑娘子字存孙孤学孩宁它守安完定宜宝实宠客室宫害家容密寒察对寻封射将小少尔尝就尽局居屋展山岁岩工左巧巨巫差己已布帆师希带帮常干平年并幸幻广应底店度座康开异弄弓引弟张弥弱弹强归当形影役彻彼往征待很得御徽心必忍志忘忙忠快念怀态怎怕怜思急怪总恍恐恢息恶情惊惑惚惜惠惨惫想意愚感愿慢慧憾懂戏成我或战房所扇手才打扔托扩扭扰找承把抓投抗抚护报抱抵押担拉拔拖招拜拥择拯拾拿持指按挑挖挡挥挺捉捕换据掉掌掘接控推掩掳掷提握搜搞搬携摆摸撞擅操攀攒支收改攻放故效敌救教敢散数整文斗料斩斯新方施旁旋族无既旦早时昆明易星映是晚晨普晶智暖暗暴曦曲更曾替最月有朋服望期木未本术机村杖束条来板极林枚果架某查标树样根格案桑桩梭棒森棵楚模横次欢欺歉止正此步武死残段殿毁每比毫氏民气水永求汇池汪沉沙没沧河治沼泉泊法泥注泳泽洒洞活派流测浪海消涡深清渐渗游湖湛源满漂漠漩漫澈激瀑火灭灯灵灼灾炉炎炸点烂烈烦烧热焰然照熊熬燃爆爱父爷爽片牌牢物牲特牺犯状独猜猴王玩环现珍珠球理瓶甚生用由甲电界留疑疲病痛登白百的益盗盘目直相盾看真眨眼着睛睡瞎瞧矗知石破砸确碍碎碑碰礼祝神祠祭禁福离种科秒秘称移稀稳穴究穷空穿突立站竟章笛第笼等答策算管箭箱篷类籽粉精糊系索紧红约纪纯纳纷纹终经绑结绕给绝统继绩续绿缉缩罐网罗罢罩置美群耀老者而耐耗聊聚肉肠股肯胆胖胜能脉脚脱脸腐自至舞般良艰色花若英荡药莱获莽菇萨落蓄蓝蕴藏蘑虚虫蚀蛰蜂蜜蠢血行街衣补衷被装裔西要覆见视觉角解触言计认让议记讲许论设访证诅识诉试诚话诞该误说请诺读谁谋谎谓谢谣象贝负贤败贪贵费贺贼资赌赏赐赚赢赤赫走赶起趁超越趟趣足跑跟路跳踪蹦蹼身躲转轻较辈输辛辞边达过迎运近返还这进远连迷追退送逃选透逐途通逛逝速造遇遍道遗遥遭避那邪部都酷酿释里重野量金钥钩钱钻铁铛铠铲银铺锁锋错锤键锯锻镖镜长门闪闭问闯闲间闻防阴阻阿附陆降限院除险陪陶随隐隔难雄集雨雪雷雾需震霉露非靠面鞋音顶顺须顾预领颗题风飞饰馆首马驭驾骁骑骗骚骨骷髅高鬼魂魔鱼鲁鸟鸣麻黄黑齐龟'
)

# CN punctuation characters (positions 96-111 in the alphabet)
CN_PUNCT = ['，', '。', '！', '？', '…', '：', '、', '—', '（', '）', '《', '》', '\u201c', '\u201d', '\u2018', '\u2019']

CHARS_PER_ROW = 32
CELL_SIZE = 16
FONT_PATH_DEFAULT = '/System/Library/Fonts/STHeiti Medium.ttc'
FONT_SIZE_DEFAULT = 12

# Fusion Pixel 12px - pixel-perfect bitmap font for retro games (SIL OFL license)
FONT_PATH_PIXEL = os.path.join(os.path.dirname(__file__), '..', 'tables', 'Fusion-pixel-12px-zh_cn.otf')
FONT_SIZE_PIXEL = 12


def generate_font_cn(output_path):
  """Generate the CJK font image.

  The image contains CN punctuation (16 chars) + CJK chars (1118) = 1134 chars total.
  These map to alphabet positions 96-111 (punct) and CJK indices 0-1117.
  """
  all_chars = CN_PUNCT + CJK_CHARS
  num_chars = len(all_chars)
  num_rows = (num_chars + CHARS_PER_ROW - 1) // CHARS_PER_ROW

  img_w = CHARS_PER_ROW * CELL_SIZE
  img_h = num_rows * CELL_SIZE

  # Create grayscale image, render, then threshold to 1-bit
  img = Image.new('L', (img_w, img_h), 0)
  draw = ImageDraw.Draw(img)

  # Use pixel font if available, fall back to system font
  if os.path.exists(FONT_PATH_PIXEL):
    font = ImageFont.truetype(FONT_PATH_PIXEL, FONT_SIZE_PIXEL)
    print(f'Using pixel font: {FONT_PATH_PIXEL}')
  else:
    font = ImageFont.truetype(FONT_PATH_DEFAULT, FONT_SIZE_DEFAULT)
    print(f'Using system font: {FONT_PATH_DEFAULT}')

  for i, ch in enumerate(all_chars):
    col = i % CHARS_PER_ROW
    row = i // CHARS_PER_ROW
    x = col * CELL_SIZE
    y = row * CELL_SIZE

    # Get bounding box to center the character
    bbox = font.getbbox(ch)
    char_w = bbox[2] - bbox[0]
    char_h = bbox[3] - bbox[1]

    # Center horizontally, align to top with small offset
    dx = (CELL_SIZE - char_w) // 2 - bbox[0]
    dy = (CELL_SIZE - char_h) // 2 - bbox[1]

    # --- 特殊修改：将 '（' 贴图向左移动 5 像素 ---
    if ch == '（':
      dx -= 5

    draw.text((x + dx, y + dy), ch, fill=255, font=font)

  # Threshold to binary body mask, then dilate to create outline
  pixels = img.load()

  # Step 1: Create binary body mask
  body = [[pixels[px, py] > 80 for px in range(img_w)] for py in range(img_h)]

  # Step 2: Dilate body by 1 pixel to get outline region
  dilated = [[False] * img_w for _ in range(img_h)]
  for py in range(img_h):
    for px in range(img_w):
      if body[py][px]:
        for dy in range(-1, 2):
          for dx in range(-1, 2):
            ny, nx = py + dy, px + dx
            if 0 <= ny < img_h and 0 <= nx < img_w:
              dilated[ny][nx] = True

  # Step 3: Assign colors — body=2 (white), outline only=1 (blue)
  palette_img = Image.new('P', (img_w, img_h))
  palette_pixels = palette_img.load()

  for py in range(img_h):
    for px in range(img_w):
      if body[py][px]:
        palette_pixels[px, py] = 2  # White body
      elif dilated[py][px]:
        palette_pixels[px, py] = 1  # Blue outline
      else:
        palette_pixels[px, py] = 0  # Transparent

  # Set a simple palette (similar to existing font PNGs)
  pal = [0] * 768
  # Color 0: transparent (background gray)
  pal[0], pal[1], pal[2] = 192, 192, 192
  # Color 1: light
  pal[3], pal[4], pal[5] = 128, 128, 128
  # Color 2: medium
  pal[6], pal[7], pal[8] = 64, 64, 64
  # Color 3: full
  pal[9], pal[10], pal[11] = 0, 0, 0
  palette_img.putpalette(pal)

  palette_img.save(output_path)
  print(f'Generated {output_path}: {img_w}x{img_h}, {num_chars} characters ({len(CN_PUNCT)} punct + {len(CJK_CHARS)} CJK)')


if __name__ == '__main__':
  output = os.path.join(os.path.dirname(__file__), '..', 'tables', 'font_cn.png')
  if len(sys.argv) > 1:
    output = sys.argv[1]
  generate_font_cn(output)
