from PIL import Image
import random
random.seed(7)
N=32
img=Image.new('RGBA',(N,N),(0,0,0,0))
px=img.load()
def hexc(h,a=255):
    h=h.lstrip('#'); return (int(h[0:2],16),int(h[2:4],16),int(h[4:6],16),a)
def dark(c,f): return (int(c[0]*f),int(c[1]*f),int(c[2]*f),c[3])
# --- bookshelf background (two 16x16 tiles each way), darkened so the lectern pops
plank=[hexc('#a3834f'),hexc('#8c6d3d'),hexc('#7a5c30')]
spines=['#9b2f2f','#2f4f9b','#2f7a3e','#7a4e2c','#6a2f8a','#a8862b']
for ty in (0,16):
    for tx in (0,16):
        # books: two shelves of 6 rows each (rows 1-6 and 9-14)
        for shelf in (1,9):
            x=1
            while x<15:
                w=random.choice([1,2,2])
                col=hexc(random.choice(spines))
                h=random.choice([6,6,5])
                for xx in range(x,min(x+w,15)):
                    for yy in range(shelf+6-h,shelf+6):
                        c=col if yy>shelf+6-h else dark(col,1.25) if False else tuple(min(255,int(v*1.3)) for v in col[:3])+(255,)
                        px[tx+xx,ty+yy]=c
                for yy in range(shelf,shelf+6-h):
                    if 0<=x<15: pass
                x+=w
                # gap
                for yy in range(shelf,shelf+6):
                    if x<15 and px[tx+x,ty+yy][3]==0: px[tx+x,ty+yy]=hexc('#2a1b0e')
                x+=1
            for xx in range(1,15):
                for yy in range(shelf,shelf+6):
                    if px[tx+xx,ty+yy][3]==0: px[tx+xx,ty+yy]=hexc('#2a1b0e')
        # planks: rows 0,7,8,15 and side columns 0,15
        for i in range(16):
            for yy in (0,7,8,15):
                px[tx+i,ty+yy]=plank[(i+yy)%3 if yy in (0,8) else 2]
            px[tx+0,ty+i]=plank[1]; px[tx+15,ty+i]=plank[2]
# darken the whole background
for y in range(N):
    for x in range(N):
        px[x,y]=dark(px[x,y],0.5)
# --- lectern (front view, Minecraft item style with a dark outline)
OUT=hexc('#24160a'); OAK=hexc('#c29a5e'); OAKL=hexc('#dcb878'); OAKD=hexc('#8f6a38'); OAKDD=hexc('#6b4c25')
PAGE=hexc('#f4ead0'); PAGED=hexc('#d8c69c'); INK=hexc('#a0845a'); COVER=hexc('#6b3a1e'); SPINE=hexc('#4a250f')
shape={}
def put(x,y,c): shape[(x,y)]=c
# base y 24..27
for x in range(7,25):
    put(x,24,OAKL); put(x,25,OAK); put(x,26,OAK); put(x,27,OAKD)
# post y 15..23
for y in range(15,24):
    for x in range(13,19):
        put(x,y,OAKL if x==13 else OAKD if x>=17 else OAK)
# desk board y 12..14
for x in range(5,27):
    put(x,12,OAKL); put(x,13,OAK); put(x,14,OAKDD)
# book y 6..11: cover underneath, pages, spine in the middle
for x in range(6,26):
    put(x,11,COVER)
for y in range(6,11):
    for x in range(7,25):
        put(x,y,PAGE if y<10 else PAGED)
for y in range(6,12):
    put(15,y,SPINE); put(16,y,SPINE)
for y in (7,9):
    for x in list(range(9,14))+list(range(18,23)):
        put(x,y,INK)
# page curl highlights
put(7,6,PAGED); put(24,6,PAGED)
for (x,y),c in shape.items(): px[x,y]=c
# outline: any transparent-or-background pixel next to a shape pixel becomes OUT
for y in range(N):
    for x in range(N):
        if (x,y) in shape: continue
        if any((x+dx,y+dy) in shape for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))):
            px[x,y]=OUT
# --- emerald (bottom right), the classic item shape
E={'o':hexc('#053d1c'),'d':hexc('#078a3a'),'m':hexc('#17c95c'),'l':hexc('#7af2a8'),'w':hexc('#e6fff0')}
em=["...ooo...",
    "..olwlo..",
    ".olmmmdo.",
    "olmlmmmdo",
    "olmmmmmdo",
    "ommmmmddo",
    "ommmmddd o".replace(' ',''),
    ".odmdddo.",
    "..oddoo..",
    "...ooo..."]
ox,oy=22,20
for j,row in enumerate(em):
    for i,ch in enumerate(row):
        if ch in E and 0<=ox+i<N and 0<=oy+j<N: px[ox+i,oy+j]=E[ch]
# --- enchant sparkles near the book
for (x,y,c) in [(5,3,'#e9d5ff'),(4,3,'#b98cff'),(6,3,'#b98cff'),(5,2,'#b98cff'),(5,4,'#b98cff'),
                (26,4,'#ffffff'),(27,2,'#e9d5ff'),(20,2,'#e9d5ff')]:
    px[x,y]=hexc(c)
# --- tile edge: dark frame and clipped (rounded) corners
for i in range(N):
    for (x,y) in ((i,0),(i,N-1),(0,i),(N-1,i)):
        px[x,y]=hexc('#1a0f06')
for (x,y) in [(0,0),(1,0),(0,1),(N-1,0),(N-2,0),(N-1,1),(0,N-1),(1,N-1),(0,N-2),(N-1,N-1),(N-2,N-1),(N-1,N-2)]:
    px[x,y]=(0,0,0,0)
for (x,y) in [(1,1),(N-2,1),(1,N-2),(N-2,N-2)]:
    px[x,y]=hexc('#1a0f06')
img.resize((512,512),Image.NEAREST).save('logo.png'); img.resize((512,512),Image.NEAREST).save('logo512.png')
img.resize((128,128),Image.NEAREST).save('logo_128.png')
img.save('logo_32.png')
