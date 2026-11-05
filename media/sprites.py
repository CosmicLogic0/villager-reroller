from PIL import Image
def hexc(h): h=h.lstrip('#'); return (int(h[0:2],16),int(h[2:4],16),int(h[4:6],16),255)
def sprite(rows,pal,name,scale):
    h=len(rows); w=max(len(r) for r in rows)
    im=Image.new('RGBA',(w,h),(0,0,0,0)); px=im.load()
    for y,r in enumerate(rows):
        for x,ch in enumerate(r):
            if ch in pal: px[x,y]=hexc(pal[ch])
    im.resize((w*scale,h*scale),Image.NEAREST).save(name)
# Minecraft-style health heart (9x9)
heart=[".kkk.kkk.",
       "kwwrkrrrk",
       "kwrrrrrrk",
       "krrrrrrrk",
       ".krrrrrdk",
       "..krrrdk.",
       "...krdk..",
       "....kk..."]
sprite(heart,{'k':'#1a0505','r':'#e02525','d':'#9c1010','w':'#ffb0b0'},'heart.png',20)
em=["...ooo...","..olwlo..",".olmmmdo.","olmlmmmdo","olmmmmmdo","ommmmmddo","ommmmdddo",".odmdddo.","..oddoo..","...ooo..."]
sprite(em,{'o':'#053d1c','d':'#078a3a','m':'#17c95c','l':'#7af2a8','w':'#e6fff0'},'emerald.png',3)
