// 물 자료 다듬기: 웹이 준 "물 칸"은 30m 격자라 거칠어서, 땅을 만들기 전에 세 가지를 고쳐 둠 (RetroMap이 Prepare를 한 번 부름)
//   1) 다리가 아닌 길이 지나가는 물 칸은 땅으로 되돌림 (호수 사이 둑길이 물에 잠기지 않게)
//   2) 물마다 물 높이를 따로 정함 (호수는 한강보다 높은 곳에 있음)
//   3) 강가를 매끈하게 (격자 계단 모양 → 비스듬한 선)
using System.Collections.Generic;
using UnityEngine;

public static class WaterPrep
{
    private const float ROAD_STEP_M = 10f;        // 길이 물 칸 위를 지나는지 이 간격마다 확인
    private const float ROAD_FILL_MAX_M = 150f;   // 다리가 아닌 길이 물 위를 이보다 길게 지나면 그대로 둠 (자료가 이상한 길 때문에 강을 통째로 메우지 않게)
    private const int FILL_PASSES = 4;            // 되돌린 땅의 높이를 둘레 땅에서 받아 오는 것을 되풀이하는 횟수
    private const float FILL_ABOVE_WATER_M = 1f;  // 둘레에 땅이 없어 높이를 못 받은 칸: 물 높이 + 이만큼
    private const float LAKE_BELOW_BANK_M = 1.5f; // 호수 물 높이 = 둘레에서 가장 낮은 땅보다 이만큼 아래
    private const float BANK_M = 1.5f;            // 강가 띠에서 땅이 물 면보다 가장 높이 올라오는 높이 (물 쪽은 그만큼 깊어짐)
    private const float SHORE_CELLS = 9f;         // 강가를 다듬을 때 보는 칸 수 (자기 + 둘레 8칸)

    // 물 자료 다듬기 세 가지를 차례로. 땅(Ground)을 만들기 전에 불러야 건물·길도 바뀐 높이 위에 놓임
    public static void Prepare(MapData data)
    {
        if (!WaterMaker.HasWater(data)) return;
        bool[] isRoadLand = FillLandUnderRoads(data);
        SetBodyLevels(data);
        SmoothShore(data, isRoadLand);
    }

    // ---------- 1) 다리가 아닌 길이 지나가는 물 칸 → 땅
    //   왜: 석촌호수 가운데 둑길처럼 좁은 땅이 물 칸으로 잡혀서, 길이 호수 바닥에 깔려 물에 잠기고 다리와 높이가 어긋났음
    //   다리가 아닌 길은 실제로 땅 위에 있으니 그 칸은 땅이 맞음
    // 돌려줌: 칸마다 "길 때문에 땅으로 되돌린 칸인지" (3번에서 이 칸은 다시 낮추지 않으려고)
    private static bool[] FillLandUnderRoads(MapData data)
    {
        bool[] isRoadLand = new bool[data.heights.Length];
        List<int> filled = new List<int>();
        foreach (RoadData road in data.roads)
        {
            if (road.isBridge) continue;
            List<int> wet = WaterCellsUnder(data, road, out float wetLength); // out: 물 위를 지난 길이를 wetLength에 담아 줌
            if (wetLength > ROAD_FILL_MAX_M) continue;
            foreach (int cell in wet)
            {
                if (isRoadLand[cell]) continue; // 다른 길이 이미 되돌린 칸
                data.water.cells[cell] = 0;
                isRoadLand[cell] = true;
                filled.Add(cell);
            }
        }
        RaiseFilledLand(data, filled, isRoadLand);
        return isRoadLand;
    }

    // 이 길이 지나가는 물 칸 번호들 + 물 위를 지난 길이(m)
    private static List<int> WaterCellsUnder(MapData data, RoadData road, out float wetLength)
    {
        List<int> wet = new List<int>();
        wetLength = 0;
        for (int i = 0; i + 3 < road.points.Length; i += 2)
        {
            Vector2 from = new Vector2(road.points[i], road.points[i + 1]);
            Vector2 to = new Vector2(road.points[i + 2], road.points[i + 3]);
            int pieces = Mathf.Max(1, Mathf.CeilToInt(Vector2.Distance(from, to) / ROAD_STEP_M));
            for (int piece = 0; piece <= pieces; piece++)
            {
                Vector2 point = Vector2.Lerp(from, to, piece / (float)pieces);
                if (!WaterMaker.IsWaterAt(data, point.x, point.y)) continue;
                int cell = WaterMaker.CellAt(data, point.x, point.y);
                if (!wet.Contains(cell)) wet.Add(cell);
                if (piece > 0) wetLength += Vector2.Distance(from, to) / pieces;
            }
        }
        return wet;
    }

    // 되돌린 땅의 높이: 웹이 강바닥으로 낮춰 보낸 칸이라 원래 높이를 모름 → 둘레 땅 칸들의 평균으로
    //   되돌린 칸끼리 붙어 있으면 바깥 칸부터 정해지고, 다음 번에 안쪽 칸이 그 값을 받음 (그래서 몇 번 되풀이)
    private static void RaiseFilledLand(MapData data, List<int> filled, bool[] isRoadLand)
    {
        int count = data.gridCount;
        bool[] isWaiting = new bool[data.heights.Length]; // 아직 높이를 못 정한 칸
        foreach (int cell in filled) isWaiting[cell] = true;
        for (int pass = 0; pass < FILL_PASSES; pass++)
        {
            foreach (int cell in filled)
            {
                if (!isWaiting[cell]) continue;
                float sum = 0;
                int landCount = 0;
                for (int r = cell / count - 1; r <= cell / count + 1; r++)
                {
                    for (int c = cell % count - 1; c <= cell % count + 1; c++)
                    {
                        if (r < 0 || c < 0 || r >= count || c >= count) continue;
                        int near = r * count + c;
                        if (data.water.cells[near] == 1 || isWaiting[near]) continue; // 물이거나 아직 못 정한 칸은 빼고
                        sum += data.heights[near];
                        landCount++;
                    }
                }
                if (landCount == 0) continue;
                data.heights[cell] = sum / landCount;
                isWaiting[cell] = false;
            }
        }
        foreach (int cell in filled)
        {
            if (isWaiting[cell]) data.heights[cell] = data.water.level + FILL_ABOVE_WATER_M;
        }
    }

    // ---------- 2) 물마다 물 높이를 따로
    //   왜: 자료의 물 높이는 하나(한강)뿐이라, 높은 땅에 있는 석촌호수가 10m 깊이 구덩이에 들어가 있었음
    //   가장 큰 물(한강)은 자료의 물 높이 그대로, 나머지(호수·연못)는 둘레에서 가장 낮은 땅보다 LAKE_BELOW_BANK_M 아래
    //   예) 호수 둘레 땅이 15.7 ~ 19m → 물 높이 15.7 − 1.5 = 14.2m (전에는 8.6m)
    private static void SetBodyLevels(MapData data)
    {
        int total = data.heights.Length;
        data.water.levels = new float[total];
        for (int i = 0; i < total; i++) data.water.levels[i] = data.water.level;

        bool[] isSeen = new bool[total];
        List<List<int>> bodies = new List<List<int>>(); // 물 하나하나 = 서로 이어진 물 칸들
        int largest = 0;
        for (int start = 0; start < total; start++)
        {
            if (data.water.cells[start] != 1 || isSeen[start]) continue;
            bodies.Add(CollectBody(data, start, isSeen));
            if (bodies[bodies.Count - 1].Count > bodies[largest].Count) largest = bodies.Count - 1;
        }
        for (int b = 0; b < bodies.Count; b++)
        {
            if (b == largest) continue; // 가장 큰 물은 그대로
            float level = Mathf.Max(data.water.level, LowestBank(data, bodies[b]) - LAKE_BELOW_BANK_M);
            foreach (int cell in bodies[b])
            {
                data.heights[cell] += level - data.water.level; // 바닥도 물 높이만큼 같이 올림 (물 깊이는 그대로)
                data.water.levels[cell] = level;
            }
        }
    }

    // start 칸에서 시작해 이어진 물 칸을 전부 모음: 찾은 칸의 옆 칸을 보고, 물이면 목록 끝에 더해 가며 끝까지
    private static List<int> CollectBody(MapData data, int start, bool[] isSeen)
    {
        int count = data.gridCount;
        List<int> cells = new List<int>();
        cells.Add(start);
        isSeen[start] = true;
        for (int k = 0; k < cells.Count; k++) // 목록이 늘어나는 동안 계속 돎
        {
            for (int r = cells[k] / count - 1; r <= cells[k] / count + 1; r++)
            {
                for (int c = cells[k] % count - 1; c <= cells[k] % count + 1; c++)
                {
                    if (!WaterMaker.IsWaterCell(data, r, c) || isSeen[r * count + c]) continue;
                    isSeen[r * count + c] = true;
                    cells.Add(r * count + c);
                }
            }
        }
        return cells;
    }

    // 이 물에 닿은 땅 칸 중 가장 낮은 높이 (닿은 땅이 없으면 물 높이가 안 바뀌는 값)
    private static float LowestBank(MapData data, List<int> body)
    {
        int count = data.gridCount;
        float lowest = float.MaxValue;
        foreach (int cell in body)
        {
            for (int r = cell / count - 1; r <= cell / count + 1; r++)
            {
                for (int c = cell % count - 1; c <= cell % count + 1; c++)
                {
                    if (r < 0 || c < 0 || r >= count || c >= count) continue;
                    if (data.water.cells[r * count + c] == 0) lowest = Mathf.Min(lowest, data.heights[r * count + c]);
                }
            }
        }
        return lowest == float.MaxValue ? data.water.level + LAKE_BELOW_BANK_M : lowest;
    }

    // ---------- 3) 강가를 매끈하게
    //   물 칸과 땅 칸이 만나는 띠(한 칸 폭)의 높이를 "둘레 9칸 중 물 칸 비율"로 다시 정함
    //   왜: 격자가 30m인데 물 칸과 땅 칸 사이가 낭떠러지라, 강가 선이 격자 계단을 그대로 따라가 톱니처럼 보였음
    //   물 비율이 절반인 자리가 딱 물 높이 → 강가 선이 계단 사이를 비스듬히 지나감
    //   예) 물 높이 8.6m: 물이 1칸뿐인 점 8.6 + 1.2 = 9.8m / 물이 6칸인 점 8.6 − 0.5 = 8.1m (물속)
    //   길 때문에 되돌린 땅(isRoadLand)은 건드리지 않음: 양쪽이 물인 좁은 둑길이라 물 비율이 높아서 다시 잠김
    private static void SmoothShore(MapData data, bool[] isRoadLand)
    {
        int count = data.gridCount;
        float[] result = (float[])data.heights.Clone(); // 사본에 적음 (고치는 중에 이웃 값이 바뀌지 않게)
        for (int row = 0; row < count; row++)
        {
            for (int column = 0; column < count; column++)
            {
                if (isRoadLand[row * count + column]) continue;
                float waterShare = WaterShare(data, row, column, out float level);
                if (waterShare <= 0 || waterShare >= 1) continue; // 둘레가 전부 땅이거나 전부 물 → 그대로
                result[row * count + column] = level + (0.5f - waterShare) * 2 * BANK_M;
            }
        }
        data.heights = result;
    }

    // 이 점과 둘레 8칸 중 물 칸의 비율 (0~1) + 그 물의 물 높이(level)
    private static float WaterShare(MapData data, int row, int column, out float level)
    {
        int water = 0;
        level = data.water.level;
        for (int r = row - 1; r <= row + 1; r++)
        {
            for (int c = column - 1; c <= column + 1; c++)
            {
                if (!WaterMaker.IsWaterCell(data, r, c)) continue;
                water++;
                level = Mathf.Max(level, data.water.levels[r * data.gridCount + c]);
            }
        }
        return water / SHORE_CELLS;
    }
}
