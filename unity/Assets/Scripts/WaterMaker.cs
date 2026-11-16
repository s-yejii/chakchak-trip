// 강물 만들기: 지형 격자에서 물인 칸들을 물 높이에 평평한 네모로 깔고, 잔물결 그림(노멀 맵)을 입힘
// 웹이 물 칸의 땅을 물 높이보다 낮춰서 보내므로(강바닥), 물 면이 땅에 묻히지 않음
// 물 높이는 물마다 다름 (한강 · 호수): WaterPrep이 칸마다 정해 둔 값(data.water.levels)을 씀
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

public static class WaterMaker
{
    private const int RIPPLE_PX = 128;  // 잔물결 그림 한 변(픽셀)
    public const float RIPPLE_M = 40f;  // 잔물결 그림 한 장이 덮는 물 면(m)
    private const float RIPPLE_STRENGTH = 9f;    // 물결 기울기 세기 (옆 점과의 높이 차이에 곱함. 크면 물이 거칠어 보임)

    // 네 귀퉁이 중 하나라도 물에 잠긴 격자 칸마다 네모 하나. 물이 없으면 null
    public static Mesh BuildMesh(MapData data)
    {
        if (!HasWater(data)) return null;
        int count = data.gridCount;
        float step = data.sizeM / (count - 1);
        float half = data.sizeM / 2;
        List<Vector3> vertices = new List<Vector3>();
        List<Vector2> uv = new List<Vector2>();
        List<int> triangles = new List<int>();
        for (int row = 0; row < count - 1; row++)
        {
            for (int column = 0; column < count - 1; column++)
            {
                if (!TryWetQuad(data, row, column, out float level)) continue; // out: 그 물의 물 높이를 level에 담아 줌
                float x = -half + column * step;
                float z = -half + row * step;
                int first = vertices.Count;
                AddCorner(vertices, uv, x, z, level);
                AddCorner(vertices, uv, x, z + step, level);
                AddCorner(vertices, uv, x + step, z + step, level);
                AddCorner(vertices, uv, x + step, z, level);
                triangles.Add(first); triangles.Add(first + 1); triangles.Add(first + 2);
                triangles.Add(first); triangles.Add(first + 2); triangles.Add(first + 3);
            }
        }
        if (vertices.Count == 0) return null;

        Mesh mesh = new Mesh();
        mesh.indexFormat = IndexFormat.UInt32; // 꼭짓점이 6만 5천 개를 넘을 수 있어서
        mesh.SetVertices(vertices);
        mesh.SetUVs(0, uv);
        mesh.SetTriangles(triangles, 0);
        mesh.RecalculateNormals();
        mesh.RecalculateTangents(); // 잔물결 그림(노멀 맵)을 쓰려면 필요
        return mesh;
    }

    // 꼭짓점 하나. 그림 좌표는 m ÷ 그림 한 장 크기 (그림이 RIPPLE_M마다 되풀이됨)
    private static void AddCorner(List<Vector3> vertices, List<Vector2> uv, float x, float z, float level)
    {
        vertices.Add(new Vector3(x, level, z));
        uv.Add(new Vector2(x, z) / RIPPLE_M);
    }

    // 이 지도에 물 자료가 있는지
    public static bool HasWater(MapData data)
    {
        return data.water != null && data.water.cells != null && data.water.cells.Length == data.heights.Length;
    }

    // 격자 (row, column) 칸이 물인지 (지도 밖은 물이 아님)
    public static bool IsWaterCell(MapData data, int row, int column)
    {
        if (row < 0 || column < 0 || row >= data.gridCount || column >= data.gridCount) return false;
        return data.water.cells[row * data.gridCount + column] == 1;
    }

    // 이 네모 칸(남서 귀퉁이가 row, column)의 네 귀퉁이 중 물에 잠긴 점이 있는지 + 그 물의 물 높이
    //   전에는 물 칸에서 2칸 떨어진 곳까지 물 면을 깔았는데, 호수보다 낮은 옆 땅 위로 네모난 물이 번져 보였음
    private static bool TryWetQuad(MapData data, int row, int column, out float level)
    {
        bool isWet = false;
        level = data.water.level;
        for (int r = row; r <= row + 1; r++)
        {
            for (int c = column; c <= column + 1; c++)
            {
                if (!TryWetPoint(data, r, c, out float pointLevel)) continue;
                isWet = true;
                level = Mathf.Max(level, pointLevel);
            }
        }
        return isWet;
    }

    // 격자 점 하나가 물에 잠겼는지: 물 칸이거나, 물 칸 바로 옆인데 땅이 그 물 높이보다 낮은 점 (강가를 다듬어 물 밑으로 내려간 땅)
    private static bool TryWetPoint(MapData data, int row, int column, out float level)
    {
        level = data.water.level;
        if (row < 0 || column < 0 || row >= data.gridCount || column >= data.gridCount) return false;
        int cell = row * data.gridCount + column;
        if (data.water.cells[cell] == 1)
        {
            level = LevelOfCell(data, cell);
            return true;
        }
        bool hasWaterNear = false;
        for (int r = row - 1; r <= row + 1; r++)
        {
            for (int c = column - 1; c <= column + 1; c++)
            {
                if (!IsWaterCell(data, r, c)) continue;
                hasWaterNear = true;
                level = Mathf.Max(level, LevelOfCell(data, r * data.gridCount + c));
            }
        }
        return hasWaterNear && data.heights[cell] < level;
    }

    // 미터 좌표(x, z)에서 가장 가까운 격자 칸 번호 (지도 밖이면 가장자리 칸)
    public static int CellAt(MapData data, float x, float z)
    {
        float step = data.sizeM / (data.gridCount - 1);
        int column = Mathf.Clamp(Mathf.RoundToInt((x + data.sizeM / 2) / step), 0, data.gridCount - 1);
        int row = Mathf.Clamp(Mathf.RoundToInt((z + data.sizeM / 2) / step), 0, data.gridCount - 1);
        return row * data.gridCount + column;
    }

    // 미터 좌표(x, z)가 물 칸인지 (가장 가까운 격자 점으로 봄)
    public static bool IsWaterAt(MapData data, float x, float z)
    {
        if (!HasWater(data)) return false;
        float step = data.sizeM / (data.gridCount - 1);
        int column = Mathf.RoundToInt((x + data.sizeM / 2) / step);
        int row = Mathf.RoundToInt((z + data.sizeM / 2) / step);
        return IsWaterCell(data, row, column);
    }

    // 미터 좌표(x, z) 자리의 물 높이 (호수는 한강보다 높음). 물이 없는 지도면 0
    public static float LevelAt(MapData data, float x, float z)
    {
        if (!HasWater(data)) return 0;
        return LevelOfCell(data, CellAt(data, x, z));
    }

    // 이 칸의 물 높이: WaterPrep이 물마다 정해 둔 값, 아직 없으면 자료의 물 높이
    private static float LevelOfCell(MapData data, int cell)
    {
        return data.water.levels != null ? data.water.levels[cell] : data.water.level;
    }

    // 잔물결 그림: 점마다 "물 면이 어느 쪽으로 기울었나"를 색으로 적은 그림(노멀 맵). 빛이 이걸 보고 반짝임을 만듦
    //   물결 높이 = 크기가 다른 PerlinNoise 네 겹 (불규칙해서 줄무늬처럼 안 보임). 기울기 = 옆 점과의 높이 차이
    //   그림 끝과 처음이 이어지게, 높이를 네 귀퉁이 값과 거리 비율로 섞어서 만듦 (구름 그림과 같은 방법)
    public static Texture2D RippleNormalMap()
    {
        float[] heights = new float[RIPPLE_PX * RIPPLE_PX];
        for (int y = 0; y < RIPPLE_PX; y++)
        {
            for (int x = 0; x < RIPPLE_PX; x++)
            {
                float u = (float)x / RIPPLE_PX;
                float v = (float)y / RIPPLE_PX;
                heights[y * RIPPLE_PX + x] = WaveHeight(u, v) * (1 - u) * (1 - v) + WaveHeight(u - 1, v) * u * (1 - v)
                                           + WaveHeight(u, v - 1) * (1 - u) * v + WaveHeight(u - 1, v - 1) * u * v;
            }
        }
        Texture2D texture = new Texture2D(RIPPLE_PX, RIPPLE_PX, TextureFormat.RGBA32, true, true); // 마지막 true: 색이 아니라 방향 값이라 그대로 저장
        Color32[] pixels = new Color32[RIPPLE_PX * RIPPLE_PX];
        for (int y = 0; y < RIPPLE_PX; y++)
        {
            for (int x = 0; x < RIPPLE_PX; x++)
            {
                float slopeX = heights[y * RIPPLE_PX + (x + 1) % RIPPLE_PX] - heights[y * RIPPLE_PX + (x + RIPPLE_PX - 1) % RIPPLE_PX];
                float slopeY = heights[((y + 1) % RIPPLE_PX) * RIPPLE_PX + x] - heights[((y + RIPPLE_PX - 1) % RIPPLE_PX) * RIPPLE_PX + x];
                Vector3 normal = new Vector3(-slopeX * RIPPLE_STRENGTH, -slopeY * RIPPLE_STRENGTH, 1).normalized;
                pixels[y * RIPPLE_PX + x] = new Color32((byte)((normal.x * 0.5f + 0.5f) * 255), (byte)((normal.y * 0.5f + 0.5f) * 255), (byte)((normal.z * 0.5f + 0.5f) * 255), 255);
            }
        }
        texture.SetPixels32(pixels);
        texture.Apply();
        return texture;
    }

    // 물결 높이 (0~1쯤): 큰 물결 + 작은 물결을 세기를 줄여 가며 네 번 겹침. 가로로 조금 늘여서 바람 방향 느낌
    private static float WaveHeight(float u, float v)
    {
        float sum = 0;
        float scale = 6f;
        float weight = 0.5f;
        for (int octave = 0; octave < 4; octave++)
        {
            sum += Mathf.PerlinNoise(u * scale * 0.6f + 31.7f, v * scale + 11.3f) * weight;
            scale *= 2;
            weight /= 2;
        }
        return sum;
    }
}
