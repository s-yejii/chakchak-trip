// 버스 정류장 (11/19): 웹이 지도(OpenStreetMap) 정류장 자리를 넘겨 주면 가까운 찻길 가장자리에
//   ① 바닥: 노란 정차 구역 표시 (버스가 서는 차로 칸, 안쪽 선은 점선)
//   ② 인도: 승차대 (지붕 · 뒷유리 · 옆유리 · 의자) + 정류장 표지판 기둥
// 왜: 거리 바닥이 밋밋했고, 버스를 타는 구간에서 정류장이 안 보여서 어디서 탔는지 알 수 없었음
// 어떻게: 정류장 점에서 가장 가까운 찻길 조각을 찾아 그 방향·폭에 맞춰 놓음 (모양은 PropMaker 상자로)
using System.Collections.Generic;
using UnityEngine;

public static class BusStopMaker
{
    private const float FIND_ROAD_M = 30f;       // 정류장에서 이 거리 안의 찻길만 봄 (더 멀면 엉뚱한 길에 붙음)
    private const float BUS_ROAD_MIN_M = 7f;     // 이 폭 이상인 찻길에만 (버스는 큰길로 다님. 좁은 옆길에 붙으면 횡단보도와 겹쳤음)
    private const float NEAR_ROUTE_M = 400f;     // 걸은 길에서 이 안의 정류장만 그림 (먼 곳은 안 보임)
    private const float SAME_STOP_M = 15f;       // 이보다 가까운 정류장 둘은 하나로 봄 (지도 자료에 같은 정류장이 두 번 있을 때)
    private const float BAY_LENGTH_M = 16f;      // 노란 정차 구역 길이 (버스 한 대 11m + 앞뒤 여유)
    private const float BAY_WIDTH_M = 3f;        // 정차 구역 폭 (차로 하나)
    private const float PAINT_M = 0.18f;         // 노란 선 굵기
    private const float DASH_M = 1.5f;           // 점선 한 칸 길이 (빈 칸도 같은 길이)
    private const float ROAD_UP_M = 0.4f;        // 찻길이 땅에서 뜬 높이 (RetroMap ROAD_LIFT_M과 같게)
    private const float WIDTH_UP = 0.004f;       // 넓은 찻길은 폭 × 이만큼 더 뜸 (RoadMaker WIDTH_LIFT와 같게)
    private const float PAINT_UP_M = 0.05f;      // 노란 선을 찻길 바닥보다 이만큼 위에 (차선 0.03m보다 위)
    private const float CURB_M = 0.15f;          // 인도가 찻길보다 높은 정도
    private const float SURFACE_SAMPLE_M = 3f;   // 바닥 높이를 잴 때 동서남북으로 이만큼 떨어진 점도 봄 (RoadMaker와 같게)
    private const float SHELTER_LENGTH_M = 4.5f; // 승차대 길이 (길 방향)
    private const float SHELTER_DEPTH_M = 1.5f;  // 승차대 깊이 (길과 직각)
    private const float SHELTER_HEIGHT_M = 2.6f; // 지붕 높이
    private const float SHELTER_GAP_M = 1.4f;    // 연석에서 승차대 가운데까지
    private const float GLASS_M = 0.06f;         // 유리 두께

    // 색: 화면 효과(ACES)를 지나면 진해져서 실제보다 조금 옅게 (처음 값은 주황·짙은 청록 상자로 보였음)
    private static readonly Color YELLOW = new Color(0.95f, 0.82f, 0.30f);
    private static readonly Color ROOF = new Color(0.55f, 0.60f, 0.62f);
    private static readonly Color GLASS = new Color(0.82f, 0.90f, 0.93f);
    private static readonly Color FRAME = new Color(0.30f, 0.32f, 0.34f);
    private static readonly Color BENCH = new Color(0.58f, 0.40f, 0.22f);
    private static readonly Color SIGN_BLUE = new Color(0.08f, 0.33f, 0.66f);
    private static readonly Color SIGN_WHITE = new Color(0.95f, 0.95f, 0.95f);

    // 가장 가까운 찻길 조각 (찾은 자리 · 길 방향 · 폭 · 한쪽 방향 길인지)
    private class RoadSpot
    {
        public Vector2 at;
        public Vector2 direction;
        public float width;
        public bool isOneWay;
        public float distance;
    }

    // 지도 자료의 정류장을 모두 그림 (RetroMap이 소품을 모을 때 부름)
    public static void Add(MapData data, Ground ground, PropMaker props, RouteArea area)
    {
        if (data.busStops == null || data.roads == null) return;
        List<Vector2> placed = new List<Vector2>(); // 이미 그린 정류장 (겹치지 않게)
        List<Vector2> crosswalks = CrosswalkPoints(data.areas); // 횡단보도 꼭짓점 (한 번만 모아 둠)
        foreach (BusStopData stop in data.busStops)
        {
            Vector2 point = new Vector2(stop.x, stop.z);
            if (!area.IsNear(point.x, point.y, NEAR_ROUTE_M)) continue;
            if (IsAlreadyPlaced(placed, point)) continue;
            RoadSpot spot = FindNearestCarRoad(data.roads, point);
            if (spot == null) continue;
            placed.Add(point);
            AvoidCrosswalks(crosswalks, spot);
            AddOne(ground, props, point, spot);
        }
    }

    private static bool IsAlreadyPlaced(List<Vector2> placed, Vector2 point)
    {
        foreach (Vector2 other in placed)
        {
            if (Vector2.Distance(other, point) < SAME_STOP_M) return true;
        }
        return false;
    }

    // 정류장 점에서 FIND_ROAD_M 안의 가장 가까운 찻길 조각. 없으면 null (다리 위 찻길은 뺌)
    private static RoadSpot FindNearestCarRoad(RoadData[] roads, Vector2 point)
    {
        RoadSpot best = null;
        foreach (RoadData road in roads)
        {
            if (road.kind != 0 || road.isBridge || road.points == null || road.width < BUS_ROAD_MIN_M) continue;
            for (int i = 0; i + 3 < road.points.Length; i += 2)
            {
                Vector2 a = new Vector2(road.points[i], road.points[i + 1]);
                Vector2 b = new Vector2(road.points[i + 2], road.points[i + 3]);
                if (Vector2.Distance(a, b) < 0.5f) continue;
                Vector2 closest = ClosestOnSegment(a, b, point);
                float distance = Vector2.Distance(closest, point);
                if (distance > FIND_ROAD_M || (best != null && distance >= best.distance)) continue;
                best = new RoadSpot { at = closest, direction = (b - a).normalized, width = road.width, isOneWay = road.isOneWay, distance = distance };
            }
        }
        return best;
    }

    // 정차 구역이 횡단보도에 걸치면 길을 따라 앞이나 뒤로 옮김 (실제로도 정류장은 횡단보도를 피해 둠)
    //   앞(+)·뒤(−)로 SHIFT_STEP_M씩 옮겨 보며, 정차 구역 둘레 CROSSWALK_CLEAR_M 안에 횡단보도 점이 없는 첫 자리로
    private const float CROSSWALK_CLEAR_M = 12f;
    private const float SHIFT_STEP_M = 6f;
    private const int SHIFT_TRIES = 4; // 6 · 12 · 18 · 24m까지
    private static void AvoidCrosswalks(List<Vector2> crosswalks, RoadSpot spot)
    {
        if (!IsNearCrosswalk(crosswalks, spot.at)) return;
        for (int i = 1; i <= SHIFT_TRIES; i++)
        {
            for (int sign = 1; sign >= -1; sign -= 2)
            {
                Vector2 moved = spot.at + spot.direction * (sign * i * SHIFT_STEP_M);
                if (IsNearCrosswalk(crosswalks, moved)) continue;
                spot.at = moved;
                return;
            }
        }
    }

    // 이 자리에서 CROSSWALK_CLEAR_M + 정차 구역 반 길이 안에 횡단보도 면의 꼭짓점이 있는지
    private static bool IsNearCrosswalk(List<Vector2> crosswalks, Vector2 at)
    {
        float reach = CROSSWALK_CLEAR_M + BAY_LENGTH_M / 2 - SHIFT_STEP_M; // 정차 구역 가운데에서 이 안 (끝에서 6m쯤 여유)
        foreach (Vector2 point in crosswalks)
        {
            if (Vector2.Distance(at, point) < reach) return true;
        }
        return false;
    }

    // 횡단보도 면(종류 2)의 꼭짓점 전부. 자료가 없으면 빈 목록
    private static List<Vector2> CrosswalkPoints(AreaData[] areas)
    {
        List<Vector2> list = new List<Vector2>();
        if (areas == null) return list;
        foreach (AreaData area in areas)
        {
            if (area.kind != 2 || area.points == null) continue;
            for (int i = 0; i + 1 < area.points.Length; i += 2) list.Add(new Vector2(area.points[i], area.points[i + 1]));
        }
        return list;
    }

    // 선분 a–b 위에서 point와 가장 가까운 점
    private static Vector2 ClosestOnSegment(Vector2 a, Vector2 b, Vector2 point)
    {
        Vector2 ab = b - a;
        float rate = Mathf.Clamp01(Vector2.Dot(point - a, ab) / ab.sqrMagnitude); // 0 = a 쪽 끝, 1 = b 쪽 끝
        return a + ab * rate;
    }

    // 정류장 하나: 정류장이 길의 어느 쪽에 있는지 정하고, 그쪽 가장자리에 정차 구역 + 인도에 승차대
    private static void AddOne(Ground ground, PropMaker props, Vector2 point, RoadSpot spot)
    {
        Vector2 along = spot.direction;
        Vector2 left = new Vector2(-along.y, along.x); // 길 방향의 왼쪽
        float side = Vector2.Dot(point - spot.at, left) >= 0 ? 1 : -1;
        // 정류장 점이 길 가운데(1m 안)에 찍혀 있거나 한쪽 방향 길이면: 차는 오른쪽으로 다니니 오른쪽 가장자리
        if (spot.isOneWay || Mathf.Abs(Vector2.Dot(point - spot.at, left)) < 1f) side = -1;
        Vector2 outward = left * side; // 길 가운데 → 정류장 쪽
        float half = spot.width / 2;
        float roadTop = SurfaceGround(ground, spot.at) + ROAD_UP_M + spot.width * WIDTH_UP;
        AddBay(props, spot.at + outward * (half - BAY_WIDTH_M / 2), along, outward, roadTop + PAINT_UP_M);
        AddShelter(props, spot.at + outward * (half + SHELTER_GAP_M), along, outward, roadTop + CURB_M);
    }

    // 노란 정차 구역: 네모 테두리. 연석 쪽 긴 변과 양 끝은 실선, 차로 쪽 긴 변은 점선
    private static void AddBay(PropMaker props, Vector2 center, Vector2 along, Vector2 outward, float y)
    {
        float yaw = StreetProps.YawOf(along);
        Vector2 inner = center - outward * (BAY_WIDTH_M / 2); // 차로 쪽 긴 변
        Vector2 curb = center + outward * (BAY_WIDTH_M / 2);  // 연석 쪽 긴 변
        props.AddBox(At(curb, y), new Vector3(BAY_LENGTH_M, 0.02f, PAINT_M), yaw, YELLOW);
        for (int end = -1; end <= 1; end += 2) // 양 끝 짧은 변
        {
            props.AddBox(At(center + along * (end * BAY_LENGTH_M / 2), y), new Vector3(PAINT_M, 0.02f, BAY_WIDTH_M), yaw, YELLOW);
        }
        int dashCount = Mathf.FloorToInt(BAY_LENGTH_M / (DASH_M * 2)); // 16m → 5칸
        for (int i = 0; i < dashCount; i++)
        {
            float from = -BAY_LENGTH_M / 2 + DASH_M / 2 + i * DASH_M * 2; // 이 점선 칸의 가운데 (구역 가운데부터 m)
            props.AddBox(At(inner + along * (from + DASH_M / 2), y), new Vector3(DASH_M, 0.02f, PAINT_M), yaw, YELLOW);
        }
    }

    // 승차대: 지붕 + 뒷유리(찻길 반대쪽) + 양옆 유리 + 기둥 둘 + 의자. 표지판 기둥은 길 앞쪽 끝에
    private static void AddShelter(PropMaker props, Vector2 center, Vector2 along, Vector2 outward, float floor)
    {
        float yaw = StreetProps.YawOf(along);
        Vector2 back = center + outward * (SHELTER_DEPTH_M / 2);
        float glassHeight = SHELTER_HEIGHT_M - 0.9f; // 유리는 위쪽 0.7m를 비움 → 지붕만 떠 보여서 "막힌 상자"가 아니라 승차대로 보임
        props.AddBox(At(center, floor + SHELTER_HEIGHT_M), new Vector3(SHELTER_LENGTH_M + 0.3f, 0.06f, SHELTER_DEPTH_M + 0.3f), yaw, ROOF); // 얇은 지붕
        props.AddBox(At(back, floor + 0.2f + glassHeight / 2), new Vector3(SHELTER_LENGTH_M, glassHeight, GLASS_M), yaw, GLASS);              // 뒷유리
        for (int end = -1; end <= 1; end += 2)
        {
            Vector2 sideCenter = center + along * (end * SHELTER_LENGTH_M / 2) + outward * (SHELTER_DEPTH_M / 4);
            props.AddBox(At(sideCenter, floor + 0.2f + glassHeight / 2), new Vector3(GLASS_M, glassHeight, SHELTER_DEPTH_M / 2), yaw, GLASS); // 옆유리 (뒤쪽 절반만)
            props.AddBox(At(back + along * (end * SHELTER_LENGTH_M / 2), floor + SHELTER_HEIGHT_M / 2), new Vector3(0.1f, SHELTER_HEIGHT_M, 0.1f), yaw, FRAME); // 기둥
        }
        props.AddBox(At(back - outward * 0.35f, floor + 0.45f), new Vector3(SHELTER_LENGTH_M * 0.6f, 0.08f, 0.45f), yaw, BENCH); // 의자
        // 표지판: 기둥 + 길 방향을 바라보는 파란 판(가운데 흰 띠)
        Vector2 pole = center + along * (SHELTER_LENGTH_M / 2 + 1.2f) - outward * (SHELTER_GAP_M - 0.5f);
        props.AddBox(At(pole, floor + 1.6f), new Vector3(0.1f, 3.2f, 0.1f), yaw, FRAME);
        props.AddBox(At(pole, floor + 2.75f), new Vector3(0.06f, 0.8f, 0.7f), yaw, SIGN_BLUE);
        props.AddBox(At(pole + along * 0.04f, floor + 2.85f), new Vector3(0.02f, 0.18f, 0.6f), yaw, SIGN_WHITE);
    }

    // 바닥 높이: 가운데 + 동서남북 SURFACE_SAMPLE_M 점 중 가장 높은 땅 (RoadMaker.SurfaceGround와 같은 방법 → 찻길과 같은 높이)
    private static float SurfaceGround(Ground ground, Vector2 point)
    {
        float top = ground.HeightAt(point.x, point.y);
        top = Mathf.Max(top, ground.HeightAt(point.x + SURFACE_SAMPLE_M, point.y));
        top = Mathf.Max(top, ground.HeightAt(point.x - SURFACE_SAMPLE_M, point.y));
        top = Mathf.Max(top, ground.HeightAt(point.x, point.y + SURFACE_SAMPLE_M));
        top = Mathf.Max(top, ground.HeightAt(point.x, point.y - SURFACE_SAMPLE_M));
        return top;
    }

    private static Vector3 At(Vector2 point, float y)
    {
        return new Vector3(point.x, y, point.y);
    }
}
