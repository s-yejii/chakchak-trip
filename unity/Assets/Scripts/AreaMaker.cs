// 실제 도로 면·보도 면 깔기: 수치지형도(1:1,000)의 면을 그대로 바닥에 그림 (자료는 웹이 data.areas로 넘겨 줌)
// 왜 만들었나: 길을 "가운데 선 + 어림한 폭"으로만 그리면 넓은 보행 거리·광장·교차로 모양이 안 나와서,
//   명동·잠실 같은 곳이 길 없는 빈 보도블록 바닥으로 보였음
// 어떻게: 면의 꼭짓점마다 "그 자리 땅 높이 + 조금"에 점을 놓고, 안쪽은 삼각형으로 나눔 (건물 옥상과 같은 귀 자르기)
//   찻길 띠(RoadMaker, 땅에서 0.4m쯤)보다 아래에 깔아서, 차선은 그 위에 그대로 보임
//   횡단보도 면은 면을 그대로 칠하지 않고 흰 줄 여러 개로 바꿔서 찻길 띠 위에 그림
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

public class AreaMaker
{
    public const int ROAD = 0;      // 도로 면 (자료의 kind와 같은 번호)
    public const int SIDEWALK = 1;  // 보도 면
    public const int CROSSWALK = 2; // 횡단보도 면
    private const int MATERIAL_COUNT = 3;
    // 종류별로 땅에서 띄우는 높이: 보도는 도로보다 13cm 위(연석 높이쯤), 횡단보도 줄은 찻길 띠(0.4~0.5m)보다 위
    private static readonly float[] LIFT_M = { 0.25f, 0.38f, 0.56f };
    private const float STRIPE_M = 0.5f; // 횡단보도 흰 줄 폭 (줄 사이 간격도 같음)
    private const float MAX_EDGE_M = 12f; // 삼각형 한 변이 이보다 길면 잘게 나눔 (지형 격자 한 칸 20~30m보다 촘촘하게)

    private readonly Ground ground;
    private readonly List<Vector3> vertices = new List<Vector3>();
    private readonly List<Vector2> uv = new List<Vector2>();
    private readonly List<int>[] triangles = new List<int>[MATERIAL_COUNT]; // 재질(종류)마다 삼각형 목록

    public AreaMaker(Ground ground)
    {
        this.ground = ground;
        for (int i = 0; i < MATERIAL_COUNT; i++) triangles[i] = new List<int>();
    }

    // 면 하나 더하기. 모르는 종류는 건너뜀
    public void Add(AreaData area)
    {
        if (area.kind < 0 || area.kind >= MATERIAL_COUNT) return;
        List<Vector2> ring = BuildingMaker.ToRing(area.points); // 점 목록으로 바꾸고 시계 반대 방향으로 맞춤
        if (ring.Count < 3) return;
        if (area.kind == CROSSWALK)
        {
            AddCrosswalk(ring);
            return;
        }

        List<int> left = new List<int>(); // 아직 안 잘린 점 번호
        for (int i = 0; i < ring.Count; i++) left.Add(i);
        while (left.Count > 3)
        {
            int ear = BuildingMaker.FindEar(ring, left);
            if (ear < 0) break; // 모양이 꼬인 면 → 남은 부분은 포기
            AddFineTriangle(area.kind, ring[left[(ear + left.Count - 1) % left.Count]], ring[left[(ear + 1) % left.Count]], ring[left[ear]]);
            left.RemoveAt(ear);
        }
        if (left.Count == 3) AddFineTriangle(area.kind, ring[left[0]], ring[left[2]], ring[left[1]]);
    }

    // 삼각형 하나를 넣되, 변이 MAX_EDGE_M보다 길면 가장 긴 변의 가운데에서 둘로 나눠 다시 넣음 (충분히 작아질 때까지)
    //   왜: 넓은 도로 면은 꼭짓점이 몇 개뿐이라 삼각형 하나가 100m씩 됨. 높이는 꼭짓점에서만 땅에 맞추니
    //       삼각형 가운데는 땅의 굴곡을 못 따라가 군데군데 땅에 묻혔음 (잠실 사거리 바닥이 깨져 보였음)
    //   예) 변이 40m · 30m · 25m인 삼각형 → 40m 변의 가운데에서 둘로 → 그 둘도 가장 긴 변이 12m 이하가 될 때까지 되풀이
    //   같은 변은 옆 삼각형에서도 같은 가운데 점에서 나뉘어서, 이음매에 틈이 안 생김
    private void AddFineTriangle(int kind, Vector2 a, Vector2 b, Vector2 c)
    {
        float ab = Vector2.Distance(a, b);
        float bc = Vector2.Distance(b, c);
        float ca = Vector2.Distance(c, a);
        float longest = Mathf.Max(ab, Mathf.Max(bc, ca));
        if (longest <= MAX_EDGE_M)
        {
            int first = vertices.Count;
            AddPoint(a, kind);
            AddPoint(b, kind);
            AddPoint(c, kind);
            triangles[kind].Add(first); triangles[kind].Add(first + 1); triangles[kind].Add(first + 2);
            return;
        }
        if (longest == ab)      // a–b 변을 나눔: (a, 가운데, c) + (가운데, b, c). 도는 방향은 그대로
        {
            Vector2 middle = (a + b) / 2;
            AddFineTriangle(kind, a, middle, c);
            AddFineTriangle(kind, middle, b, c);
        }
        else if (longest == bc) // b–c 변을 나눔
        {
            Vector2 middle = (b + c) / 2;
            AddFineTriangle(kind, a, b, middle);
            AddFineTriangle(kind, a, middle, c);
        }
        else                    // c–a 변을 나눔
        {
            Vector2 middle = (c + a) / 2;
            AddFineTriangle(kind, a, b, middle);
            AddFineTriangle(kind, middle, b, c);
        }
    }

    // 횡단보도 면 → 흰 줄들. 면은 네모이고 긴 변이 사람이 건너는 방향
    //   긴 변 두 개(a→b 와 d→c)를 같은 비율로 짚어 가며, 그 사이를 잇는 줄을 1m마다(줄 0.5m + 빈 곳 0.5m) 놓음
    //   예) 긴 변 12m → 줄 12개. 3번째 줄은 긴 변의 2.25m ~ 2.75m 자리
    private void AddCrosswalk(List<Vector2> ring)
    {
        if (ring.Count != 4) return; // 네모가 아닌 드문 모양은 건너뜀
        int start = Vector2.Distance(ring[0], ring[1]) >= Vector2.Distance(ring[1], ring[2]) ? 0 : 1; // 0→1 변이 짧으면 한 칸 돌려서 긴 변부터
        Vector2 a = ring[start];
        Vector2 b = ring[(start + 1) % 4];
        Vector2 c = ring[(start + 2) % 4];
        Vector2 d = ring[(start + 3) % 4];
        float length = Vector2.Distance(a, b);
        int count = Mathf.FloorToInt(length / (STRIPE_M * 2));
        for (int i = 0; i < count; i++)
        {
            float from = (i * 2 + 0.5f) * STRIPE_M / length; // 이 줄이 시작하는 자리 (긴 변에서의 비율 0~1)
            float to = from + STRIPE_M / length;
            int first = vertices.Count;
            AddPoint(Vector2.Lerp(a, b, from), CROSSWALK);
            AddPoint(Vector2.Lerp(a, b, to), CROSSWALK);
            AddPoint(Vector2.Lerp(d, c, to), CROSSWALK);
            AddPoint(Vector2.Lerp(d, c, from), CROSSWALK);
            AddTriangle(triangles[CROSSWALK], first, 0, 1, 2);
            AddTriangle(triangles[CROSSWALK], first, 0, 2, 3);
        }
    }

    // 꼭짓점 하나: 그 자리 땅 높이 + 종류별 높이. 그림 좌표 = 실제 위치(m) (재질이 "몇 m마다 그림 한 장"으로 되풀이함)
    private void AddPoint(Vector2 point, int kind)
    {
        vertices.Add(new Vector3(point.x, ground.HeightAt(point.x, point.y) + LIFT_M[kind], point.y));
        uv.Add(point);
    }

    private static void AddTriangle(List<int> list, int first, int a, int b, int c)
    {
        list.Add(first + a); list.Add(first + c); list.Add(first + b); // 위에서 봤을 때 보이는 방향
    }

    // 지금까지 더한 면 전부를 Mesh 하나로. 재질 순서: 0 도로 · 1 보도 · 2 횡단보도 흰 줄
    public Mesh BuildMesh()
    {
        Mesh mesh = new Mesh();
        mesh.indexFormat = IndexFormat.UInt32; // 꼭짓점이 6만 5천 개를 넘을 수 있어서
        mesh.SetVertices(vertices);
        mesh.SetUVs(0, uv);
        mesh.subMeshCount = MATERIAL_COUNT;
        for (int i = 0; i < MATERIAL_COUNT; i++) mesh.SetTriangles(triangles[i], i);
        mesh.RecalculateNormals();
        mesh.RecalculateTangents(); // 바닥 사진의 굴곡(노멀 맵)이 빛을 바르게 받으려면 필요
        return mesh;
    }
}
