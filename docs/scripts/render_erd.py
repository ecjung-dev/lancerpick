"""Render the reviewed DDL as PNG and SVG; no database connection required."""
from pathlib import Path
import heapq
import html
import re
from PIL import Image, ImageDraw, ImageFont

DOCS = Path(__file__).resolve().parents[1]
OUT = DOCS / "images"
SQL = DOCS / "sql" / "V1__init.sql"
W, H, GRID = 4000, 3320, 20
FONT = "C:/Windows/Fonts/malgun.ttf"
MONO = "C:/Windows/Fonts/consola.ttf"
TABLE_W, HEAD, ROW = 640, 76, 29
PLACEMENT = {
    "categories": (80, 280), "seller_profiles": (880, 280),
    "members": (1680, 280), "wallets": (2480, 280), "wallet_entries": (3280, 280),
    "products": (80, 1000), "product_moderation_logs": (880, 1000),
    "orders": (1680, 1000), "payments": (2480, 1000), "settlements": (3280, 1000),
    "order_snapshots": (1680, 1700),
    "reviews": (80, 2120), "revision_requests": (880, 2120),
    "deliveries": (1680, 2120), "order_histories": (2480, 2120),
    "cancellation_requests": (3280, 2120),
}
LABELS = {
    "members": "회원", "seller_profiles": "판매자 프로필", "categories": "카테고리",
    "products": "상품", "orders": "주문 · 가격 · 소요일 · 진행", "order_histories": "주문 이력",
    "order_snapshots": "주문 당시 상품 · 조건 · 요구사항",
    "deliveries": "납품", "revision_requests": "수정 요청", "cancellation_requests": "취소 요청",
    "wallets": "구매 적립금 지갑", "payments": "주문 결제 · 환불", "wallet_entries": "적립금 원장",
    "settlements": "판매 수익 · 정산", "reviews": "후기", "product_moderation_logs": "상품 운영 이력",
}
GROUP = {
    "members": "A", "product_moderation_logs": "A", "seller_profiles": "B", "categories": "B",
    "products": "B", "reviews": "B", "orders": "C", "order_snapshots": "C", "order_histories": "C", "deliveries": "C",
    "revision_requests": "C", "cancellation_requests": "C", "wallets": "D", "wallet_entries": "D",
    "payments": "D", "settlements": "D",
}
COLORS = {"A": "#475569", "B": "#087f8c", "C": "#3158b5", "D": "#8852ad"}


def parse():
    tables, edges = {}, []
    sql = SQL.read_text(encoding="utf-8")
    for match in re.finditer(r"(?ms)^CREATE TABLE (\w+) \(\n(.*?)^\) ENGINE=", sql):
        name, body = match[1], match[2]
        cols = []
        for col in re.finditer(r"(?m)^    (\w+) (BIGINT|INT|BOOLEAN|VARCHAR\(\d+\)|CHAR\(\d+\)|TEXT|DATETIME\(6\)) (.+)$", body):
            cols.append({"name": col[1], "type": col[2], "required": "NOT NULL" in col[3],
                         "auto": "AUTO_INCREMENT" in col[3]})
        pk = re.search(r"PRIMARY KEY \(([^)]+)\)", body)[1].split(", ")
        unique = [u.split(", ") for u in re.findall(r"UNIQUE KEY \w+ \(([^)]+)\)", body)]
        fks = re.findall(r"FOREIGN KEY \((\w+)\) REFERENCES (\w+) \((\w+)\)", body)
        tables[name] = {"cols": cols, "pk": pk, "unique": unique, "fks": fks}
        for local, parent, remote in fks:
            edges.append((name, local, parent, remote))
    assert set(tables) == set(PLACEMENT), "Update the diagram layout for every SQL table"
    for child, local, parent, remote in edges:
        assert any(c["name"] == local for c in tables[child]["cols"])
        assert any(c["name"] == remote for c in tables[parent]["cols"])
    return tables, edges


class Canvas:
    def __init__(self):
        self.image = Image.new("RGB", (W, H), "#f4f7fb")
        self.draw = ImageDraw.Draw(self.image)
        self.svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
                    '<rect width="100%" height="100%" fill="#f4f7fb"/>']

    def rect(self, box, fill, outline=None):
        self.draw.rectangle(box, fill=fill, outline=outline, width=1)
        x, y, xx, yy = box
        self.svg.append(f'<rect x="{x}" y="{y}" width="{xx-x}" height="{yy-y}" fill="{fill}" stroke="{outline or fill}"/>')

    def text(self, xy, value, size=20, color="#15233b", mono=False):
        font = ImageFont.truetype(MONO if mono else FONT, size)
        self.draw.text(xy, value, fill=color, font=font, anchor="lt")
        family = "Consolas, monospace" if mono else "Malgun Gothic, sans-serif"
        x, y = xy
        self.svg.append(f'<text x="{x}" y="{y}" dominant-baseline="text-before-edge" font-family="{family}" font-size="{size}" fill="{color}">{html.escape(value)}</text>')

    def line(self, points, color, width=2, dashed=False):
        if dashed:
            for a, b in zip(points, points[1:]):
                length = abs(b[0]-a[0]) + abs(b[1]-a[1])
                for start in range(0, int(length), 14):
                    t, tt = start/max(length, 1), min(start+8, length)/max(length, 1)
                    self.draw.line([(a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t),
                                    (a[0]+(b[0]-a[0])*tt, a[1]+(b[1]-a[1])*tt)], fill=color, width=width)
        else:
            self.draw.line(points, fill=color, width=width, joint="curve")
        path = " ".join(f"{x},{y}" for x, y in points)
        dash = ' stroke-dasharray="8 6"' if dashed else ""
        self.svg.append(f'<polyline points="{path}" fill="none" stroke="{color}" stroke-width="{width}"{dash}/>')

    def triangle(self, points, color):
        self.draw.polygon(points, fill=color)
        self.svg.append('<polygon points="' + " ".join(f"{x},{y}" for x,y in points) + f'" fill="{color}"/>')


def render():
    tables, edges = parse()
    canvas = Canvas()
    canvas.rect((0, 0, W, 185), "#13223d")
    canvas.text((80, 32), "LancerPick | 물리 ERD", 46, "#ffffff")
    column_count = sum(len(t["cols"]) for t in tables.values())
    canvas.text((80, 100), f"MySQL 8.4  ·  {len(tables)} tables  ·  {column_count} columns  ·  {len(edges)} foreign keys  |  설계 검토용 · 실제 DB 적용 미검증", 24, "#d8e3f6")
    canvas.text((80, 204), "PK 기본키   FK 외래키   U1/U2 복합 UNIQUE 묶음   NN 필수   ? NULL 허용   AI 자동 증가", 24)
    canvas.text((80, 244), "관계선: 자식 FK → 부모 키  |  선 번호는 아래 FK 목록과 대응  |  점선은 NULL 허용 FK", 20, "#566880")
    rects = {}
    for name, table in tables.items():
        x, y = PLACEMENT[name]
        height = HEAD + ROW * len(table["cols"]) + 16 + len(table["unique"]) * 25
        rects[name] = (x, y, x+TABLE_W, y+height)

    blocked = set()
    for x, y, xx, yy in rects.values():
        for gx in range((x-10)//GRID, (xx+10)//GRID+1):
            for gy in range((y-10)//GRID, (yy+10)//GRID+1):
                blocked.add((gx, gy))
    usage = {}

    def port(name, column, side):
        x, y, xx, _ = rects[name]
        index = next(i for i,c in enumerate(tables[name]["cols"]) if c["name"] == column)
        py = y + HEAD + index*ROW + ROW//2
        px = x if side == "left" else xx
        outer = px-40 if side == "left" else px+40
        return (px, py), (round(outer/GRID), round(py/GRID))

    def route(start, goal):
        heap = [(0, 0, start, None)]
        costs, previous = {start: 0}, {}
        while heap:
            _, cost, cell, direction = heapq.heappop(heap)
            if cell == goal:
                result = [cell]
                while cell != start:
                    cell = previous[cell]
                    result.append(cell)
                return list(reversed(result))
            if cost != costs.get(cell):
                continue
            for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                nxt = cell[0]+dx, cell[1]+dy
                if nxt in blocked or not (1 <= nxt[0] < W//GRID-1 and 14 <= nxt[1] < 140):
                    continue
                step = 1 + usage.get(nxt,0)*1.9 + (0.3 if direction and direction != (dx,dy) else 0)
                new = cost+step
                if new < costs.get(nxt,float("inf")):
                    costs[nxt], previous[nxt] = new, cell
                    heuristic = abs(nxt[0]-goal[0])+abs(nxt[1]-goal[1])
                    heapq.heappush(heap,(new+heuristic,new,nxt,(dx,dy)))
        raise RuntimeError("No obstacle-free FK route")

    for number, (child, local, parent, remote) in enumerate(edges,1):
        choices = []
        for a in ("left","right"):
            for b in ("left","right"):
                cp, start = port(child,local,a)
                pp, goal = port(parent,remote,b)
                choices.append((abs(start[0]-goal[0])+abs(start[1]-goal[1]),a,b,cp,start,pp,goal))
        _, a, b, cp, start, pp, goal = min(choices)
        path = route(start,goal)
        for point in path:
            usage[point] = usage.get(point,0)+1
        grid_points = [(xx*GRID,yy*GRID) for xx,yy in path]
        points = [cp,(grid_points[0][0],cp[1])] + grid_points + [(grid_points[-1][0],pp[1]),pp]
        color = COLORS[GROUP[child]]
        nullable = not next(c for c in tables[child]["cols"] if c["name"]==local)["required"]
        canvas.line(points,color,3,nullable)
        direction = 1 if b == "left" else -1
        canvas.triangle([pp,(pp[0]-direction*12,pp[1]-6),(pp[0]-direction*12,pp[1]+6)],color)
        lx = cp[0]-37 if a == "left" else cp[0]+5
        canvas.rect((lx-2,cp[1]-23,lx+29,cp[1]-3),"#f4f7fb")
        canvas.text((lx,cp[1]-22),f"{number:02d}",16,color,True)

    for name, table in tables.items():
        x,y,xx,yy = rects[name]
        color = COLORS[GROUP[name]]
        canvas.rect((x,y,xx,yy),"#ffffff","#cad4e2")
        canvas.rect((x,y,xx,y+HEAD),color)
        canvas.text((x+16,y+10),name,26,"#ffffff",True)
        canvas.text((x+16,y+45),f"{LABELS[name]}  ·  {GROUP[name]}",19,"#ffffff")
        fk_names={fk[0] for fk in table["fks"]}
        for index,col in enumerate(table["cols"]):
            row_y=y+HEAD+index*ROW
            if index%2==0:
                canvas.rect((x+1,row_y,xx-1,row_y+ROW),"#f2f5fa")
            flags=[]
            if col["name"] in table["pk"]: flags.append("PK")
            if col["name"] in fk_names: flags.append("FK")
            for ui, unique in enumerate(table["unique"],1):
                if col["name"] in unique: flags.append(f"U{ui}")
            canvas.text((x+12,row_y+5),col["name"],19,mono=True)
            canvas.text((x+322,row_y+5),col["type"],18,"#55667f",True)
            canvas.text((x+490,row_y+5)," ".join(flags),15,color,True)
            canvas.text((xx-50,row_y+5),"AI" if col["auto"] else "NN" if col["required"] else "?",16,"#55667f",True)
        foot_y=y+HEAD+len(table["cols"])*ROW+5
        for ui, unique in enumerate(table["unique"],1):
            canvas.text((x+12,foot_y),f"U{ui}: " + ", ".join(unique),15,"#566880",True)
            foot_y+=25

    canvas.rect((60,2810,W-60,H-40),"#ffffff","#d5ddea")
    canvas.text((86,2830),"FOREIGN KEY MAP · DDL에 실제 정의된 관계",28)
    canvas.text((86,2875),"부모 1개에 대한 자식 수: 0..1 = FK 자체가 PK/단일 UNIQUE인 경우, 그 외 0..N. 자식의 부모는 NN이면 1, ?이면 0..1.",20,"#566880")
    legend_rows = (len(edges) + 1) // 2
    for index,(child,local,parent,remote) in enumerate(edges):
        x=90+(index//legend_rows)*1940
        y=2920+(index%legend_rows)*24
        unique = [local] in tables[child]["unique"] or tables[child]["pk"] == [local]
        cardinality = "0..1" if unique else "0..N"
        line=f"{index+1:02d}  {child}.{local}  ->  {parent}.{remote}   [{cardinality}]"
        canvas.text((x,y),line,18,COLORS[GROUP[child]],True)
    canvas.text((86,3225),"주의: 주문별 스냅샷·회원별 지갑 필수 생성, 취소 대기 1건, 최초 납품 대상 검증·금액 원자성은 서비스에서도 보장합니다.",21,"#566880")
    canvas.text((86,3263),"SOURCE: docs/sql/V1__init.sql  |  2026-10-29  |  CHECK·기본값·일반 인덱스의 상세 구문은 SQL 원본을 확인하세요.",19,"#566880")
    OUT.mkdir(exist_ok=True)
    canvas.image.save(OUT/"lancerpick-physical-erd.png",optimize=True)
    (OUT/"lancerpick-physical-erd.svg").write_text("\n".join(canvas.svg+["</svg>"]),encoding="utf-8")
    preview=canvas.image.copy()
    preview.thumbnail((1800,1800))
    preview.save(OUT/"lancerpick-physical-erd-preview.png",optimize=True)
    print(f"Rendered {len(tables)} tables / {column_count} columns / {len(edges)} FK edges: PNG 4000x3320 + SVG + preview")


if __name__ == "__main__":
    render()
