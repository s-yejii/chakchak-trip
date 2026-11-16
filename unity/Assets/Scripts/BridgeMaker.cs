// 한강 다리: 지도 자료의 "다리" 길 중 물 위를 지나는 것을 물 위로 띄워 그림
//   물 위 = 물 높이 + DECK_M, 땅 위 = 땅 높이 → 그 사이는 경사로처럼 천천히 오르내림
//   상판 + 양쪽 난간 + 물속까지 내려가는 교각 (색칠한 상자, PropMaker)
//   다리 끝이 땅 위면 그 끝은 땅 높이로 내려와서 땅 위 길과 이어짐
//   밤 조명: 난간 아래 조명 줄 + 가로등 (lights에 따로 모아 빛나는 재질로 그림)
//   다리 위 높이도 기억해 둠 → 다리를 건넌 경로 선·걷는 사람·탈것을 다리 위에 놓음 (땅에 두면 다리 밑에 숨어서)
using System.Collections.Generic;
using UnityEngine;

public class BridgeMaker
{
    private const float DECK_M = 12f;          // 물 위 상판 높이 (한강 다리는 물에서 10~15m)
    private const float STEP_M = 15f;          // 다리 선을 이 간격으로 잘게 나눔 (높이를 점마다 정하려고)
    private const float RAMP_SLOPE = 0.05f;    // 경사로 기울기 (1m 갈 때 5cm 오르내림)
    private const float PILLAR_GAP_M = 60f;    // 교각 사이 거리
    private const float DECK_THICK_M = 1.6f;   // 상판 두께
    private const float RAIL_M = 1.1f;         // 난간 높이
    private const float END_SLOPE = 0.08f;     // 땅 위 다리 끝에서 오르는 기울기 (1m에 8cm)
    private const float WATER_CLEAR_M = 2f;    // 상판은 물보다 적어도 이만큼 위
    private const float LAMP_GAP_M = 32f;      // 다리 가로등 간격
    private const float LAMP_POLE_M = 6f;      // 다리 가로등 높이
    private const float PILLAR_RADIUS_M = 1.6f; // 교각 기둥 반지름
    private const float LIGHT_LINE_M = 0.45f;  // 난간 아래 조명 줄 굵기 (멀리서도 보이게)
    private const float DECK_CELL_M = 20f;     // 다리 조각을 이 크기 칸에 나눠 담음 (가까운 다리만 빨리 찾으려고)
    private const float SAME_WAY = 0.8f;       // 가는 방향과 다리 방향이 이만큼(cos, 약 37도 안) 나란해야 "다리 위를 건넘" (다리 밑을 지나는 길과 구별)
    private const float SURFACE_UP_M = 0.05f;  // 찻길 면은 상판 선보다 이만큼 위
    private const float GPS_REACH_M = 4f;      // GPS는 몇 m씩 어긋나서, 다리 가장자리에서 이만큼 밖까지도 다리 위로 봄
    private static readonly Color DECK = new Color(0.40f, 0.45f, 0.50f); // 짙은 청회색 상판 (동호대교 로드뷰)
    private static readonly Color ASPHALT = new Color(0.68f, 0.69f, 0.71f); // 찻길 면: 화면 색 보정(ACES)을 거치면 꽤 어두워져서 밝게 (0.3이면 새까맣게 보임)
    private static readonly Color PILLAR = new Color(0.66f, 0.65f, 0.62f);
    private static readonly Color RAIL = new Color(0.80f, 0.80f, 0.78f);

    // ---------- 다리마다 다른 모양 (사진을 보고 정함). 번호는 MapData.cs의 bridgeStyle
    private const int DONGHO = 1;    // 동호대교: 상판 위로 물결 모양 주황 트러스, 파란 상판 옆면
    private const int SEONGSU = 2;   // 성수대교: 상판 아래 붉은 트러스 (교각 쪽이 깊고 가운데가 얕음), 교각 간격이 넓음
    private const int YEONGDONG = 3; // 영동대교: 파란 상판 옆면, 촘촘한 교각
    private const int CHEONGDAM = 4; // 청담대교: 2층 (아래층 지하철), 교각 위 V자 받침
    private const int JAMSIL = 5;    // 잠실대교: 교각 사이 아치 받침
    private static readonly float[] PILLAR_GAPS = { PILLAR_GAP_M, 80f, 120f, 40f, 75f, 45f }; // 번호별 교각 사이 거리
    private static readonly Color[] DECK_COLORS =
    {
        DECK, new Color(0.20f, 0.42f, 0.78f), new Color(0.72f, 0.26f, 0.16f), new Color(0.22f, 0.44f, 0.78f), new Color(0.72f, 0.69f, 0.62f), new Color(0.74f, 0.74f, 0.70f),
    };
    private static readonly Color TRUSS_ORANGE = new Color(1.00f, 0.72f, 0.24f); // 동호대교 트러스 (화면 색 보정을 거치면 붉어져서 노란 쪽으로 잡음)
    private static readonly Color TRUSS_RED = new Color(0.78f, 0.28f, 0.16f);    // 성수대교 트러스
    private static readonly Color CONCRETE = new Color(0.74f, 0.72f, 0.66f);     // 청담·잠실대교 콘크리트
    private const float TRUSS_BEAM_M = 0.6f;       // 트러스 막대 굵기
    private const float LOWER_DECK_DOWN_M = 7f;    // 청담대교 아래층이 위층보다 낮은 정도

    private readonly MapData data;
    private readonly Ground ground;
    private readonly PropMaker props;
    private readonly PropMaker lights; // 밤에 빛나는 부분 (조명 줄·가로등 등)

    // 다리 상판 한 조각: 가운데 선 from → to, 그 두 점의 높이, 폭 절반
    private struct DeckPiece
    {
        public Vector2 from;
        public Vector2 to;
        public float fromHeight;
        public float toHeight;
        public float halfWidth;
    }
    private readonly Dictionary<Vector2Int, List<DeckPiece>> decks = new Dictionary<Vector2Int, List<DeckPiece>>(); // 칸 → 그 칸에 걸친 다리 조각들

    public BridgeMaker(MapData data, Ground ground, PropMaker props, PropMaker lights)
    {
        this.data = data;
        this.ground = ground;
        this.props = props;
        this.lights = lights;
    }

    // 이 길이 물 위를 지나는 다리인지 (아니면 보통 길로 그림)
    public bool IsOverWater(RoadData road)
    {
        if (!road.isBridge) return false;
        foreach (Vector2 point in Densify(road.points)) // 양 끝만 강가에 있는 다리도 있어서, 잘게 나눈 점으로 봄
        {
            if (WaterMaker.IsWaterAt(data, point.x, point.y)) return true;
        }
        return false;
    }

    // 다리 하나
    public void Add(RoadData road)
    {
        List<Vector2> line = Densify(road.points);
        float[] heights = DeckHeights(line);
        int style = Mathf.Clamp(road.bridgeStyle, 0, PILLAR_GAPS.Length - 1);
        float pillarGap = PILLAR_GAPS[style];
        float sinceLastPillar = pillarGap / 2;
        float sinceLastLamp = LAMP_GAP_M / 2;
        for (int i = 0; i + 1 < line.Count; i++)
        {
            Vector3 from = new Vector3(line[i].x, heights[i], line[i].y);
            Vector3 to = new Vector3(line[i + 1].x, heights[i + 1], line[i + 1].y);
            bool isSpanOverWater = WaterMaker.IsWaterAt(data, line[i].x, line[i].y) || WaterMaker.IsWaterAt(data, line[i + 1].x, line[i + 1].y);
            if (isSpanOverWater) RememberDeck(line[i], line[i + 1], heights[i], heights[i + 1], road.width / 2); // 물 위 부분만 (땅 위 진입로 밑을 걸어간 길이 다리 위로 올라가지 않게)
            props.AddBeam(from - Vector3.up * DECK_THICK_M / 2, to - Vector3.up * DECK_THICK_M / 2, road.width + 1, DECK_THICK_M, DECK_COLORS[style]); // 상판 (옆면 색이 다리마다 다름)
            props.AddBeam(from + Vector3.up * 0.02f, to + Vector3.up * 0.02f, road.width, 0.05f, ASPHALT);                            // 찻길 면
            AddRails(from, to, road.width);
            sinceLastLamp += Vector2.Distance(line[i], line[i + 1]);
            if (sinceLastLamp >= LAMP_GAP_M)
            {
                AddLamps(from, to, road.width);
                sinceLastLamp = 0;
            }

            float stepLength = Vector2.Distance(line[i], line[i + 1]);
            // 다리마다 다른 부분: 교각에서 얼마나 왔는지(0 = 교각, 1 = 다음 교각)로 트러스 높이를 정함
            if (isSpanOverWater) AddStyleParts(style, from, to, road.width, sinceLastPillar / pillarGap, (sinceLastPillar + stepLength) / pillarGap);
            sinceLastPillar += stepLength;
            bool isOverWater = WaterMaker.IsWaterAt(data, line[i].x, line[i].y);
            if (isOverWater && sinceLastPillar >= pillarGap)
            {
                AddPillar(from, to - from, road.width);
                if (style == CHEONGDAM) AddVStruts(from, to - from, road.width);
                sinceLastPillar = 0;
            }
        }
    }

    // 다리 한 조각(from → to)에 그 다리만의 부분을 더함. rateFrom · rateTo: 이 조각의 양 끝이 교각 사이 어디쯤인지 (0 = 교각, 1 = 다음 교각)
    private void AddStyleParts(int style, Vector3 from, Vector3 to, float width, float rateFrom, float rateTo)
    {
        if (style == DONGHO) AddTruss(from, to, width, 4 + 11 * Hump(rateFrom), 4 + 11 * Hump(rateTo), TRUSS_ORANGE, true);      // 위로: 교각 사이 가운데가 가장 높은 물결. 가운데 쪽 한 줄만
        if (style == SEONGSU) AddTruss(from, to, width, -(2 + 7 * (1 - Hump(rateFrom))), -(2 + 7 * (1 - Hump(rateTo))), TRUSS_RED); // 아래로: 교각 쪽이 깊음
        if (style == JAMSIL) AddTruss(from, to, width, -(1 + 4 * (1 - Hump(rateFrom))), -(1 + 4 * (1 - Hump(rateTo))), CONCRETE);  // 아래로 얕은 아치
        if (style == CHEONGDAM) // 아래층 (지하철): 위층보다 좁고 낮게
        {
            Vector3 down = Vector3.up * (LOWER_DECK_DOWN_M + DECK_THICK_M / 2);
            props.AddBeam(from - down, to - down, width * 0.5f, DECK_THICK_M, CONCRETE);
        }
    }

    // 교각 사이에서의 자리(0~1) → 0(교각) ~ 1(가운데) ~ 0(다음 교각)으로 부드럽게 오르내리는 값
    //   예) 0.5(딱 가운데) → 1, 0.25 → 0.71
    private static float Hump(float rate)
    {
        return Mathf.Abs(Mathf.Sin(Mathf.PI * rate));
    }

    // 트러스 한 칸 (다리 양옆에 하나씩): 상판에서 heightFrom · heightTo만큼 떨어진 곳을 잇는 긴 막대 + 세로 막대 + 비스듬한 막대
    //   높이가 +면 상판 위(동호대교), −면 상판 아래(성수대교)
    //   isLeftOnly: 가는 방향의 왼쪽(가운데 분리대 쪽)에만 세움. 동호대교는 트러스가 다리 가운데(지하철 선로 둘레)에만 있어서
    private void AddTruss(Vector3 from, Vector3 to, float width, float heightFrom, float heightTo, Color color, bool isLeftOnly = false)
    {
        Vector3 across = Vector3.Cross(Vector3.up, (to - from).normalized) * (width / 2); // 다리 옆쪽 (왼쪽·오른쪽 가장자리까지)
        float start = heightFrom < 0 ? -DECK_THICK_M : RAIL_M; // 아래 트러스는 상판 밑에서, 위 트러스는 난간 위에서 시작
        for (int side = -1; side <= 1; side += 2) // −1 = 왼쪽, +1 = 오른쪽
        {
            if (isLeftOnly && side > 0) continue;
            Vector3 deckFrom = from + across * side + Vector3.up * start;
            Vector3 deckTo = to + across * side + Vector3.up * start;
            Vector3 chordFrom = from + across * side + Vector3.up * heightFrom;
            Vector3 chordTo = to + across * side + Vector3.up * heightTo;
            props.AddBeam(chordFrom, chordTo, TRUSS_BEAM_M, TRUSS_BEAM_M, color); // 긴 막대 (트러스의 바깥 줄)
            props.AddBeam(deckFrom, chordFrom, TRUSS_BEAM_M, TRUSS_BEAM_M, color); // 세로 막대
            props.AddBeam(deckFrom, chordTo, TRUSS_BEAM_M, TRUSS_BEAM_M, color);   // 비스듬한 막대
        }
    }

    // 청담대교 V자 받침: 교각 꼭대기(아래층 높이)에서 앞뒤로 벌어져 위층을 받침
    private void AddVStruts(Vector3 top, Vector3 along, float width)
    {
        Vector3 forward = new Vector3(along.x, 0, along.z).normalized * 18;       // 앞뒤로 18m 벌어짐
        Vector3 across = new Vector3(-along.z, 0, along.x).normalized * (width / 4);
        Vector3 low = top - Vector3.up * (LOWER_DECK_DOWN_M + DECK_THICK_M);
        Vector3 high = top - Vector3.up * DECK_THICK_M;
        for (int side = -1; side <= 1; side += 2)
        {
            props.AddBeam(low + across * side, high + across * side + forward, 1.4f, 1.4f, CONCRETE);
            props.AddBeam(low + across * side, high + across * side - forward, 1.4f, 1.4f, CONCRETE);
        }
    }

    // 이 자리를 heading 쪽으로 건너는 다리가 있으면 true와 그 찻길 면 높이 (out: 결과를 height에 담아 줌)
    //   heading이 다리와 나란하지 않으면(다리 밑을 가로지름) false
    public bool TryDeckHeight(Vector2 point, Vector2 heading, out float height)
    {
        height = 0;
        if (heading.magnitude < 0.1f) return false; // 가만히 있으면 방향을 몰라서 땅으로 봄
        if (!decks.TryGetValue(CellOf(point), out List<DeckPiece> list)) return false;
        foreach (DeckPiece piece in list)
        {
            Vector2 along = piece.to - piece.from;
            if (Mathf.Abs(Vector2.Dot(along.normalized, heading.normalized)) < SAME_WAY) continue;
            float rate = Mathf.Clamp01(Vector2.Dot(point - piece.from, along) / Mathf.Max(along.sqrMagnitude, 0.0001f)); // 조각 위 가장 가까운 곳 (0 = from, 1 = to)
            if (Vector2.Distance(point, piece.from + along * rate) > piece.halfWidth + GPS_REACH_M) continue;
            height = Mathf.Lerp(piece.fromHeight, piece.toHeight, rate) + SURFACE_UP_M;
            return true;
        }
        return false;
    }

    // 상판 한 조각을 기억해 둠: 조각을 감싸는 네모(+폭 절반)가 걸치는 칸마다
    private void RememberDeck(Vector2 from, Vector2 to, float fromHeight, float toHeight, float halfWidth)
    {
        DeckPiece piece = new DeckPiece { from = from, to = to, fromHeight = fromHeight, toHeight = toHeight, halfWidth = halfWidth };
        Vector2Int low = CellOf(Vector2.Min(from, to) - Vector2.one * halfWidth);
        Vector2Int high = CellOf(Vector2.Max(from, to) + Vector2.one * halfWidth);
        for (int x = low.x; x <= high.x; x++)
        {
            for (int z = low.y; z <= high.y; z++)
            {
                Vector2Int cell = new Vector2Int(x, z);
                if (!decks.ContainsKey(cell)) decks[cell] = new List<DeckPiece>();
                decks[cell].Add(piece);
            }
        }
    }

    private static Vector2Int CellOf(Vector2 point)
    {
        return new Vector2Int(Mathf.FloorToInt(point.x / DECK_CELL_M), Mathf.FloorToInt(point.y / DECK_CELL_M));
    }

    // 점 사이가 STEP_M보다 멀면 사이에 점을 넣음 (지도 자료의 다리는 양 끝 두 점뿐인 경우가 많아서)
    private static List<Vector2> Densify(float[] points)
    {
        List<Vector2> line = new List<Vector2>();
        for (int i = 0; i + 3 < points.Length; i += 2)
        {
            Vector2 a = new Vector2(points[i], points[i + 1]);
            Vector2 b = new Vector2(points[i + 2], points[i + 3]);
            int pieces = Mathf.Max(1, Mathf.CeilToInt(Vector2.Distance(a, b) / STEP_M));
            for (int k = 0; k < pieces; k++) line.Add(Vector2.Lerp(a, b, (float)k / pieces));
        }
        line.Add(new Vector2(points[points.Length - 2], points[points.Length - 1]));
        return line;
    }

    // 점마다 상판 높이: 물 위는 물 높이 + DECK_M, 땅 위는 땅 높이. 그다음 앞뒤로 훑으며 경사로를 만듦
    //   (높은 점에서 RAMP_SLOPE보다 가파르게 내려가지 않게 → 다리 끝에서 땅까지 천천히 내려옴)
    private float[] DeckHeights(List<Vector2> line)
    {
        float[] heights = new float[line.Count];
        for (int i = 0; i < line.Count; i++)
        {
            bool isOverWater = WaterMaker.IsWaterAt(data, line[i].x, line[i].y);
            heights[i] = isOverWater ? DeckOverWater(line[i]) : ground.HeightAt(line[i].x, line[i].y) + 0.5f;
        }
        for (int i = 1; i < line.Count; i++) heights[i] = Mathf.Max(heights[i], heights[i - 1] - Vector2.Distance(line[i], line[i - 1]) * RAMP_SLOPE);
        for (int i = line.Count - 2; i >= 0; i--) heights[i] = Mathf.Max(heights[i], heights[i + 1] - Vector2.Distance(line[i], line[i + 1]) * RAMP_SLOPE);
        LowerLandEnds(line, heights);
        return heights;
    }

    // 물 위 점의 상판 높이: 한강(자료의 물 높이 그대로인 큰 물)은 물에서 DECK_M, 호수·연못 다리는 낮게 WATER_CLEAR_M
    //   왜: 호수 다리는 길과 거의 같은 높이로 건너감. 한강 다리처럼 12m 띄우면 양쪽 길과 끊김
    private float DeckOverWater(Vector2 point)
    {
        float level = WaterMaker.LevelAt(data, point.x, point.y);
        bool isMainWater = level <= data.water.level + 0.01f;
        return level + (isMainWater ? DECK_M : WATER_CLEAR_M);
    }

    // 다리 끝이 땅 위면 그 끝은 땅 높이로 내리고, 끝에서 END_SLOPE로 오르게 함 (공중에서 끊기지 않고 땅 위 길과 이어지게)
    //   예) 땅 끝에서 50m 떨어진 점은 많아야 "끝 높이 + 4m"
    private void LowerLandEnds(List<Vector2> line, float[] heights)
    {
        int last = line.Count - 1;
        bool isFirstOnLand = !WaterMaker.IsWaterAt(data, line[0].x, line[0].y);
        bool isLastOnLand = !WaterMaker.IsWaterAt(data, line[last].x, line[last].y);
        float firstHeight = ground.HeightAt(line[0].x, line[0].y) + 0.5f;
        float lastHeight = ground.HeightAt(line[last].x, line[last].y) + 0.5f;
        float fromFirst = 0;
        float[] fromLast = new float[line.Count]; // 끝에서 거꾸로 잰 거리
        for (int i = last - 1; i >= 0; i--) fromLast[i] = fromLast[i + 1] + Vector2.Distance(line[i], line[i + 1]);
        for (int i = 0; i <= last; i++)
        {
            if (i > 0) fromFirst += Vector2.Distance(line[i], line[i - 1]);
            if (isFirstOnLand) heights[i] = Mathf.Min(heights[i], firstHeight + fromFirst * END_SLOPE);
            if (isLastOnLand) heights[i] = Mathf.Min(heights[i], lastHeight + fromLast[i] * END_SLOPE);
            heights[i] = Mathf.Max(heights[i], ground.HeightAt(line[i].x, line[i].y) + 0.5f); // 땅 밑으로는 안 내려감
            if (WaterMaker.IsWaterAt(data, line[i].x, line[i].y)) heights[i] = Mathf.Max(heights[i], WaterMaker.LevelAt(data, line[i].x, line[i].y) + WATER_CLEAR_M);
        }
    }

    // 상판 양쪽 가장자리의 난간
    private void AddRails(Vector3 from, Vector3 to, float width)
    {
        Vector3 along = (to - from).normalized;
        Vector3 side = new Vector3(-along.z, 0, along.x).normalized * (width / 2 + 0.3f);
        Vector3 up = Vector3.up * RAIL_M / 2;
        props.AddBeam(from + side + up, to + side + up, 0.35f, RAIL_M, RAIL);
        props.AddBeam(from - side + up, to - side + up, 0.35f, RAIL_M, RAIL);
        Vector3 outside = side.normalized * 0.25f; // 난간 바깥 아래쪽 조명 줄 (밤에 다리 모양이 선으로 보이게)
        Vector3 below = Vector3.down * (DECK_THICK_M + 0.1f);
        lights.AddBeam(from + side + outside + below, to + side + outside + below, LIGHT_LINE_M, LIGHT_LINE_M, Color.white);
        lights.AddBeam(from - side - outside + below, to - side - outside + below, LIGHT_LINE_M, LIGHT_LINE_M, Color.white);
    }

    // 다리 양쪽 가로등 한 쌍: 기둥은 보통 소품, 등은 빛나는 소품
    private void AddLamps(Vector3 from, Vector3 to, float width)
    {
        Vector3 along = (to - from).normalized;
        Vector3 side = new Vector3(-along.z, 0, along.x).normalized * (width / 2 + 0.3f);
        for (int sign = -1; sign <= 1; sign += 2)
        {
            Vector3 foot = from + side * sign;
            Vector3 top = foot + Vector3.up * LAMP_POLE_M;
            props.AddBeam(foot, top, 0.2f, 0.2f, RAIL);
            lights.AddBox(top - side.normalized * sign * 0.8f, new Vector3(1.4f, 0.3f, 0.5f), Quaternion.LookRotation(side), Color.white);
        }
    }

    // 교각 하나: 상판 아래에서 강바닥까지. 다리 폭 쪽으로 길쭉하게 (along = 다리가 놓인 방향)
    private void AddPillar(Vector3 top, Vector3 along, float width)
    {
        float bottom = ground.HeightAt(top.x, top.z) - 1;
        float height = top.y - DECK_THICK_M - bottom;
        // 동호대교 로드뷰처럼 굵은 기둥 두 개가 짝지어 섬 (다리 폭의 양쪽 1/4 자리). 기둥 = 팔각 단면을 세운 것
        Vector3 across = new Vector3(-along.z, 0, along.x).normalized * (width / 4);
        for (int side = -1; side <= 1; side += 2)
        {
            Vector3 foot = new Vector3(top.x, 0, top.z) + across * side;
            props.AddPrism(Octagon(foot, PILLAR_RADIUS_M), bottom, bottom + height, PILLAR);
        }
        props.AddBeam(new Vector3(top.x, 0, top.z) - across * 1.4f + Vector3.up * (bottom + height - 0.8f), new Vector3(top.x, 0, top.z) + across * 1.4f + Vector3.up * (bottom + height - 0.8f), 2.2f, 1.6f, PILLAR); // 기둥 머리를 잇는 보
    }

    // 가운데 foot, 반지름 radius인 팔각형 (위에서 본 모양, x·z)
    private static Vector2[] Octagon(Vector3 foot, float radius)
    {
        Vector2[] points = new Vector2[8];
        for (int k = 0; k < 8; k++)
        {
            float angle = (22.5f + k * 45) * Mathf.Deg2Rad;
            points[k] = new Vector2(foot.x + Mathf.Cos(angle) * radius, foot.z + Mathf.Sin(angle) * radius);
        }
        return points;
    }
}
