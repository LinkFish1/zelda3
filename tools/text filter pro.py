import sys
if len(sys.argv) > 1:
    txtPath = sys.argv[1]
else:
    txtPath = input("please input txtPath: ")

with open(txtPath, 'r', encoding='utf-8')as f:
    txt=f.read()
    chars = []
    for c in txt:
            chars.append(c)
    charsStr = "".join(set(chars))
    sorted_content = sorted(charsStr, key=lambda x: ord(x))
charsPath = txtPath+"_chars.txt"
with open(charsPath, 'w', encoding='utf-8')as f:
    f.write(''.join(sorted_content))
