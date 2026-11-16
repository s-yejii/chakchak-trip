"""
⑤ 수치지형도(1:1,000, 국토지리정보원) SHP → 유니티 3D 회고용 바닥 파일 (frontend/public/geo/ground/{칸}.json)  (11/16 박석진)

왜: 길을 지도 타일의 가운데 선 + 어림한 폭으로만 그려서, 명동·잠실처럼 넓은 보행 거리가 빈 보도블록 바닥으로 나왔음
    → 실제 도로 면·보도 면·횡단보도 면을 그대로 바닥에 깔려고 만듦

- 받은 자료: 국토정보플랫폼에서 구마다 받은 2MAP1000_SHP_서울_○○구.zip (그 안에 도엽 zip, 그 안에 레이어 SHP)
- 쓰는 레이어 (전부 면): N1A_A0010000 도로 면 · N1A_A0033320 보도 면 · N1A_A0043325 횡단보도 면
- 한 도형 = [종류, x, y, x, y, …]  종류: 0 도로 · 1 보도 · 2 횡단보도
- 좌표와 칸 나누기는 건물 파일과 같음 (make_buildings_seoul.py): EPSG:5186 → 경위도, 0.01도 칸, 칸 모서리에서 백만분의 1도
- zip은 풀지 않고 그대로 읽음 (도엽이 수백 개라 풀면 파일이 수만 개가 됨)

실행: python db/geo/make_ground_seoul.py <zip들이 있는 폴더>
"""
import glob
import io
import json
import math
import os
import struct
import sys
import zipfile

from make_buildings_seoul import CELL_DEG, UNIT, to_lnglat  # 좌표 바꾸는 식과 칸 크기는 건물 파일과 같은 것을 씀

OUT_DIR = os.path.join(os.path.dirname(__file__), '..', '..', 'frontend', 'public', 'geo', 'ground')
LAYERS = {'N1A_A0010000': 0, 'N1A_A0033320': 1, 'N1A_A0043325': 2}  # 레이어 이름 → 종류
MIN_AREA_M2 = 1.0  # 이보다 작은 조각은 뺌 (그려도 안 보임)


def read_rings(shp_bytes):
    """SHP 파일 내용에서 면마다 바깥 고리 [(x, y), …]를 차례로 돌려줌"""
    rings = []
    pos = 100  # 앞 100바이트는 파일 머리
    while pos < len(shp_bytes):
        length = struct.unpack('>i', shp_bytes[pos + 4:pos + 8])[0] * 2
        body = shp_bytes[pos + 8:pos + 8 + length]
        pos += 8 + length
        if struct.unpack('<i', body[:4])[0] != 5:  # 5 = 면(Polygon)
            continue
        num_parts, num_points = struct.unpack('<2i', body[36:44])
        start = 44 + 4 * num_parts
        end = struct.unpack('<i', body[48:52])[0] if num_parts > 1 else num_points  # 첫 고리(바깥)만
        rings.append([struct.unpack('<2d', body[start + 16 * i:start + 16 * i + 16]) for i in range(end)])
    return rings


def area_of(ring):
    """고리의 넓이(㎡). 신발끈 공식"""
    total = 0
    for i in range(len(ring) - 1):
        total += ring[i][0] * ring[i + 1][1] - ring[i + 1][0] * ring[i][1]
    return abs(total) / 2


def to_item(ring, kind):
    """고리 → (칸 이름, [종류, x, y, …]). 닫는 점(첫 점과 같은 마지막 점)은 뺌"""
    points = [to_lnglat(x, y) for x, y in ring[:-1]]
    cell_x = math.floor(points[0][0] / CELL_DEG)
    cell_y = math.floor(points[0][1] / CELL_DEG)
    item = [kind]
    for lng, lat in points:
        item.append(round((lng - cell_x * CELL_DEG) * UNIT))
        item.append(round((lat - cell_y * CELL_DEG) * UNIT))
    return '%d_%d' % (cell_x, cell_y), item


def add_sheet(cells, counts, sheet_zip):
    """도엽 zip 하나에서 쓰는 레이어를 읽어 cells(칸 이름 → 도형 목록)에 더함"""
    for name in sheet_zip.namelist():
        layer = os.path.basename(name)[:-4].upper()
        if not name.upper().endswith('.SHP') or layer not in LAYERS:
            continue
        kind = LAYERS[layer]
        for ring in read_rings(sheet_zip.read(name)):
            if len(ring) < 4 or area_of(ring) < MIN_AREA_M2:
                continue
            cell, item = to_item(ring, kind)
            cells.setdefault(cell, []).append(item)
            counts[kind] += 1


def main(folder):
    cells = {}
    counts = [0, 0, 0]
    seen = set()  # 이미 읽은 도엽 (구 경계에 걸친 도엽은 두 구의 zip에 다 들어 있어서, 두 번 넣지 않게)
    for gu_path in sorted(glob.glob(os.path.join(folder, '2MAP1000_SHP_*.zip'))):
        with zipfile.ZipFile(gu_path) as gu_zip:
            for sheet_name in gu_zip.namelist():
                if not sheet_name.lower().endswith('.zip') or sheet_name in seen:
                    continue
                seen.add(sheet_name)
                add_sheet(cells, counts, zipfile.ZipFile(io.BytesIO(gu_zip.read(sheet_name))))
        print(os.path.basename(gu_path), '→ 도로', counts[0], '· 보도', counts[1], '· 횡단보도', counts[2])
    os.makedirs(OUT_DIR, exist_ok=True)
    total = 0
    for name, items in cells.items():
        path = os.path.join(OUT_DIR, name + '.json')
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(items, f, separators=(',', ':'))
        total += os.path.getsize(path)
    print('파일', len(cells), '개 ·', round(total / 1e6, 1), 'MB')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main(sys.argv[1])
