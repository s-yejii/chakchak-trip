// 경로 재생: 여행 시각에 맞춰 "걷는 사람" 표시(배 구간에서는 배)를 경로 위로 움직이고, 원하면 카메라가 따라감
//   Space : 재생 / 멈춤
//   F : 따라가기 켜기 / 끄기 (끄면 CameraMove로 자유롭게 움직임)
//   V : 따라가기 중 보는 방법 바꾸기 — 뒤에서 내려다보기 ↔ 내비게이션(사람 눈높이, 배 위에서는 갑판 눈높이)
// 웹에서는 페이지가 Play · Pause · Seek을 불러 조종할 예정
using UnityEngine;

public class RoutePlayer : MonoBehaviour
{
    private const float MINUTES_PER_SECOND = 4f; // 재생 빠르기: 1초에 여행 시간 4분 (8시간 여행 = 2분)
    private const float WALKER_HEIGHT_M = 1.2f;  // 걷는 사람 표시의 가운데 높이
    private const float EYE_M = 2.4f;            // 내비게이션에서 눈높이 (땅에서). 경로 선이 땅에서 0.75m 위라 그 위로 사람 눈높이 1.6m
                                                 //   (1.6이었을 때는 눈이 경로 선 바로 위에 있어서 선이 화면 아래를 다 덮었음)
    private const float FOLLOW_SMOOTH = 3f;      // 카메라가 따라붙는 빠르기 (클수록 바로 붙음)
    private const float TURN_SMOOTH = 1.2f;      // 내비게이션에서 고개를 돌리는 빠르기 (11/19 멀미 피드백: 2 → 1.2)
    private const float CAMERA_SMOOTH_TIME = 1.2f; // 위에서 보기: 카메라가 목표 자리까지 가는 데 걸리는 시간(초). 천천히 출발·멈춤 (11/19)
    private const float LOOK_SMOOTH = 1.5f;      // 위에서 보기: 사람 쪽으로 고개를 돌리는 빠르기 (11/19: 6 → 1.5)
    private const float WEB_SMOOTH_SECONDS = 0.15f; // 웹이 보낸 재생 시각을 이 시간 동안 부드럽게 따라감 (웹 화면이 잠깐 버벅여도 걷는 사람이 덜컥거리지 않게, 11/19)
    private const float WEB_JUMP_MINUTES = 30f;  // 웹 시각이 이보다 멀리 건너뛰면(장소 누르기·막대 끌기) 부드럽게 말고 바로 옮김
    private const float STAY_MOVE_M = 30f;       // 두 점 사이를 이보다 적게 움직였으면 "머문 것"
    private const float STAY_LONG_MINUTES = 20f; // 이보다 오래 머문 구간은 빨리 감기
    private const float STAY_NIGHT_MINUTES = 180f; // 이보다 오래(밤잠 등)면 훨씬 빨리
    private const float FAST = 12f;              // 오래 머문 구간 빨리 감기 배수 (점심 80분 → 약 1.7초)
    private const float FASTER = 80f;            // 밤잠 빨리 감기 배수 (14시간 → 약 2.6초)
    private const float BUS_EYE_M = 2.6f;        // 내비게이션에서 버스 안 눈높이
    private const float TOP_TURN_DEGREES = 0f;   // 위에서 보기: 카메라가 1초에 돌 수 있는 최대 각도. 11/19 멀미 피드백: 20 → 0 = 방향 고정 (화면이 돌면 가장 어지러움)
    private const float NAV_TURN_DEGREES = 20f;  // 내비: 1초에 돌 수 있는 최대 각도 (11/19: 35 → 20)
    private const float ZOOM_STEP = 1.2f;        // 가까이·멀리 한 번에 거리를 이만큼 곱하거나 나눔
    private const float ZOOM_MIN = 0.3f;         // 가장 가까이 (기본 거리의 30%)
    private const float ZOOM_MAX = 6f;           // 가장 멀리 (기본 거리의 6배. 3배였을 때는 잠실 롯데타워 옆에서 더 물러날 수 없었음)
    private const float ROOF_CLEAR_M = 25f;      // 위에서 보기: 카메라를 둘레 가장 높은 지붕보다 이만큼 위에 (지붕 장식 높이 포함)
    private const int SIGHT_CHECKS = 10;         // 위에서 보기: 카메라와 사람 사이를 이만큼 나눠서 건물에 가리는지 확인
    private const int SIGHT_TRIES = 4;           // 가리면 카메라를 사람 머리 위쪽으로 당겨 보는 횟수
    private const float SIGHT_BACK_RATE = 0.6f;  // 한 번 당길 때 뒤로 떨어진 거리를 이 배수로 줄이고
    private const float SIGHT_UP_RATE = 1.25f;   //   높이는 이 배수로 올림 (더 가파르게 내려다봄)
    private const float SIGHT_SKIP_M = 25f;      // 사람 바로 둘레 이 거리는 안 따짐 (사람이 들어가 있는 건물 자체는 어쩔 수 없어서)
    private const float ROOF_CLEAR_MAX_M = 150f; // 카메라를 지붕 위로 올릴 때 지붕 높이는 이만큼까지만 따짐
                                                 //   (롯데월드타워 500m 위로 올라가면 사람이 점처럼 작아짐. 타워에 가리는지는 IsSightBlocked가 따로 봄)
    // 따라갈 때 카메라 자리 { 뒤로 물러난 거리, 높이 }: 걷기 · 택시 · 배 · 지하철(땅속이라 높이 올라가 건너뜀) · 버스
    //   "위에서"는 동네가 한눈에 보이게 높이·멀리 (내비 = 사람 눈높이와 확실히 다르게)
    //   11/19 멀미 피드백: 더 높이·더 가파르게 (높을수록 땅이 화면에서 천천히 흐름). 걷기 90·75 → 110·120
    private static readonly Vector2[] FOLLOW_BACK_UP = { new Vector2(110, 120), new Vector2(130, 130), new Vector2(160, 120), new Vector2(220, 170), new Vector2(130, 130) };

    private Ground ground;
    private BuildingFinder buildings; // 카메라 둘레 가장 높은 건물을 물어봄
    private BridgeMaker bridges;      // 다리를 건널 때 다리 위 높이를 물어봄 (없을 수 있음)
    private RouteData route;
    private float waterLevel;
    private Transform walker;   // 걷는 사람 표시
    private Transform boat;     // 배 (배 구간이 없으면 null)
    private Transform bus;      // 버스 (버스 구간이 없으면 null)
    private Transform taxi;     // 택시 (택시 구간이 없으면 null)
    private SkyClock skyClock;  // 시각에 맞춰 하늘·밤 불빛을 바꿈 (없을 수 있음)
    private int firstBoatPoint = -1; // 배를 처음 탄 점 번호
    private int lastBoatPoint = -1;  // 배에서 내린 점 번호
    private float minute;       // 지금 재생 중인 시각 (여행 시작부터 몇 분)
    private int mode;           // 지금 어떻게 움직이는 중인지 (RouteData.WALK · RIDE(택시) · BOAT · SUBWAY · BUS)
    private bool isPlaying;
    private bool isFollowing;
    private bool isNavigation;  // 따라가기 중 사람 눈높이로 보기
    private int startClock;     // 경로 0분이 첫날 몇 시 몇 분인지 (화면 시계용)
    private int segment;        // 지금 몇 번째 점과 다음 점 사이인지
    private float cameraYaw;    // 카메라가 바라보는 방향(도). 가는 쪽으로 천천히 따라 돎
    private Vector3 cameraVelocity; // SmoothDamp가 기억하는 카메라 속도 (그래야 다음 프레임에 부드럽게 이어짐)
    private float webMinute = -1;   // 웹이 마지막으로 보낸 재생 시각 (경로 0분부터, 없으면 -1). Update가 이 시각으로 부드럽게 따라감
    private float zoom = 1;     // 위에서 보기 거리 배수 (1 = 기본)
    private bool isWebDriven;   // 웹 회고 화면이 재생을 조종하는지 (그러면 화면 시계는 웹이 보여 주니 숨김)

    // 경로를 받아 걷는 사람 표시(와 배)를 출발점에 세움 (RetroMap이 부름)
    public void Show(Ground ground, RouteData route, float waterLevel, int startClock, BuildingFinder buildings, BridgeMaker bridges)
    {
        this.buildings = buildings;
        this.bridges = bridges;
        this.startClock = startClock;
        this.ground = ground;
        this.route = route;
        this.waterLevel = waterLevel;
        skyClock = GetComponent<SkyClock>();
        if (walker != null) DestroyImmediate(walker.gameObject);
        if (boat != null) DestroyImmediate(boat.gameObject);
        if (bus != null) DestroyImmediate(bus.gameObject);
        if (taxi != null) DestroyImmediate(taxi.gameObject);

        // "지금 여기" 표시: 마스코트 트리피 그림 (11/20, 전에는 회색 알약 모양). 그림 판은 늘 카메라를 마주 봄
        GameObject body = new GameObject("Walker");
        body.transform.SetParent(transform, false);
        Trippy.Create(body.transform, WALKER_HEIGHT_M);
        walker = body.transform;
        MakeBoat();
        bus = MakeVehicle("Bus", RouteData.BUS, StreetProps.BuildBus());
        taxi = MakeVehicle("Taxi", RouteData.RIDE, StreetProps.BuildTaxi());
        Seek(0);
        LookAtStart();
        cameraYaw = walker.eulerAngles.y;
        ShowSubwayLines(!isNavigation); // 새 지도도 지금 시점(내비면 숨김)에 맞춤
    }

    // 처음 열었을 때 카메라가 출발점을 비스듬히 내려다보게 (여행이 어디서 시작하는지 바로 보이게)
    private void LookAtStart()
    {
        if (Camera.main == null) return;
        Camera.main.transform.position = walker.position + new Vector3(-220, 180, -260);
        Camera.main.transform.LookAt(walker.position);
    }

    // 배를 탄 구간이 있으면 배 한 척을 만듦
    private void MakeBoat()
    {
        firstBoatPoint = -1;
        for (int i = 0; i < route.minutes.Length - 1; i++)
        {
            if (route.ModeAt(i) != RouteData.BOAT) continue;
            if (firstBoatPoint < 0) firstBoatPoint = i;
            lastBoatPoint = i + 1;
        }
        if (firstBoatPoint < 0) return;

        GameObject item = new GameObject("Boat");
        item.transform.SetParent(transform, false);
        item.AddComponent<MeshFilter>().sharedMesh = BoatMaker.BuildBoat();
        item.AddComponent<MeshRenderer>().sharedMaterial = Palette.Get("Props");
        boat = item.transform;
    }

    // 경로에 그 이동 방법(mode) 구간이 있으면 탈것 하나를 만듦 (없으면 null)
    private Transform MakeVehicle(string vehicleName, int vehicleMode, Mesh mesh)
    {
        bool hasMode = false;
        for (int i = 0; i < route.minutes.Length - 1; i++)
        {
            if (route.ModeAt(i) == vehicleMode) hasMode = true;
        }
        if (!hasMode) return null;
        GameObject item = new GameObject(vehicleName);
        item.transform.SetParent(transform, false);
        item.AddComponent<MeshFilter>().sharedMesh = mesh;
        item.AddComponent<MeshRenderer>().sharedMaterial = Palette.Get("Props");
        return item.transform;
    }

    public void Play() { isPlaying = true; }

    // ---------- 웹 회고 화면이 부름 (재생은 웹이 하고, 3D는 웹이 정한 시각·시점을 그대로 보여 줌)

    // 웹의 재생 시각으로 옮김. 첫날 0시부터 몇 분째인지 (예: 1일차 11:30 = 690, 2일차 12:10 = 1440 + 730)
    public void SeekClock(float minutesFromFirstMidnight)
    {
        isPlaying = false; // 3D 혼자 재생하지 않음 (속도는 웹의 배속 버튼이 정함)
        isWebDriven = true;
        float target = minutesFromFirstMidnight - startClock;
        // 처음이거나 멀리 건너뛰면 바로 옮기고, 아니면 Update가 부드럽게 따라감
        if (webMinute < 0 || Mathf.Abs(target - minute) > WEB_JUMP_MINUTES) Seek(target);
        webMinute = target;
    }

    // 웹의 [위에서 / 내비] 버튼: "top" = 뒤쪽 위에서 따라감, "street" = 사람 눈높이 내비게이션
    public void SetView(string view)
    {
        isNavigation = view == "street";
        SetFollowing(true);
        ShowSubwayLines(!isNavigation);
    }

    // 지하철 무지개 선 보이기·숨기기: 내비(사람 눈높이)에서는 머리 위로 지나가는 큰 선이 거슬려서 숨김
    private void ShowSubwayLines(bool isOn)
    {
        Transform lines = transform.Find("RouteLines");
        if (lines == null) return;
        foreach (Transform line in lines)
        {
            if (line.name == "SubwayLine") line.gameObject.SetActive(isOn);
        }
    }

    // 웹의 [＋ / －] 버튼·마우스 휠: 1 = 가까이, -1 = 멀리 (위에서 보기 거리만 바뀜)
    public void Zoom(float direction)
    {
        zoom = Mathf.Clamp(zoom * Mathf.Pow(ZOOM_STEP, -direction), ZOOM_MIN, ZOOM_MAX); // Pow(1.2, -1) = 1 / 1.2 → 가까이
    }

    // 웹의 [따라가기] 체크: 1 = 켬, 0 = 끔 (끄면 마우스·키보드로 자유롭게 둘러봄)
    //   웹(SendMessage)이 숫자를 넘기면 float로 오므로 float로 받음
    public void SetFollow(float isOn)
    {
        SetFollowing(isOn > 0.5f);
    }

    public void Pause() { isPlaying = false; }

    // 재생 시각을 옮기고 걷는 사람(과 배)을 그 자리에 놓음
    public void Seek(float toMinute)
    {
        minute = Mathf.Clamp(toMinute, 0, route.minutes[route.minutes.Length - 1]);
        PlaceWalker();
        if (skyClock != null) skyClock.SetMinute(minute);
    }

    private void Update()
    {
        if (route == null) return;
        // 유니티 자체 키는 혼자 볼 때만. 웹이 조종할 때 스페이스바를 받으면 웹은 멈추는데 3D만 혼자 달려서 화면이 튀었음 (11/19)
        if (!isWebDriven && Input.GetKeyDown(KeyCode.Space)) isPlaying = !isPlaying;
        if (!isWebDriven && Input.GetKeyDown(KeyCode.F)) SetFollowing(!isFollowing);
        if (!isWebDriven && Input.GetKeyDown(KeyCode.V))
        {
            isNavigation = !isNavigation;
            if (!isFollowing) SetFollowing(true);
            ShowSubwayLines(!isNavigation);
        }

        if (isWebDriven && webMinute >= 0 && webMinute != minute)
        {
            // 남은 차이를 매 프레임 조금씩 줄임 (Exp: 화면이 느려도 빨라도 0.15초쯤에 거의 따라잡게)
            Seek(Mathf.Lerp(minute, webMinute, 1 - Mathf.Exp(-Time.deltaTime / WEB_SMOOTH_SECONDS)));
        }
        if (isPlaying)
        {
            Seek(minute + Time.deltaTime * MINUTES_PER_SECOND * SpeedHere());
            if (minute >= route.minutes[route.minutes.Length - 1]) isPlaying = false; // 끝까지 가면 멈춤
        }
        if (!isFollowing) return;
        if (!isNavigation && Input.mouseScrollDelta.y != 0) Zoom(Mathf.Sign(Input.mouseScrollDelta.y)); // 휠을 위로 = 가까이
        if (isNavigation && mode != RouteData.SUBWAY) LookLikeNavigation(); // 지하철은 땅속이라 위에서 봄
        else FollowFromBehind();
    }

    // 지금 구간의 재생 배수: 한자리에 오래 머문 구간(식사·밤잠)은 빨리 감음
    private float SpeedHere()
    {
        float span = route.minutes[segment + 1] - route.minutes[segment];
        bool isStaying = Vector2.Distance(route.PointAt(segment), route.PointAt(segment + 1)) < STAY_MOVE_M;
        if (!isStaying || span < STAY_LONG_MINUTES) return 1;
        return span >= STAY_NIGHT_MINUTES ? FASTER : FAST;
    }

    // 화면 왼쪽 위 시계: "DAY 1  17:15" (OnGUI: 유니티가 화면에 글자를 그릴 때 부르는 함수)
    private void OnGUI()
    {
        if (route == null || isWebDriven) return;
        int clock = startClock + Mathf.FloorToInt(minute);
        int day = clock / (24 * 60) + 1;
        int hour = clock % (24 * 60) / 60;
        string text = "DAY " + day + "  " + hour.ToString("00") + ":" + (clock % 60).ToString("00"); // 영어로: 웹(WebGL) 기본 글꼴에 한글이 없어서
        GUIStyle style = new GUIStyle(GUI.skin.label) { fontSize = 28, fontStyle = FontStyle.Bold };
        style.normal.textColor = Color.white;
        GUI.Label(new Rect(24, 18, 400, 50), text, style);
    }

    // 따라가기를 켜면 자유 이동(CameraMove)을 끄고, 끄면 다시 켬
    private void SetFollowing(bool isOn)
    {
        isFollowing = isOn;
        CameraMove free = Camera.main.GetComponent<CameraMove>();
        if (free != null) free.enabled = !isOn;
    }

    // 지금 시각이 경로의 몇 번째 점과 다음 점 사이인지 찾아, 그 사이를 시간 비율만큼 간 자리에 놓음
    // 예) 10분 점과 14분 점 사이에서 지금이 11분이면 → 두 점 사이를 25% 간 자리
    private void PlaceWalker()
    {
        int i = 0;
        while (i < route.minutes.Length - 2 && route.minutes[i + 1] <= minute) i++;

        Vector2 from = route.PointAt(i);
        Vector2 to = route.PointAt(i + 1);
        float span = route.minutes[i + 1] - route.minutes[i];
        Vector2 at = Vector2.Lerp(from, to, span > 0 ? (minute - route.minutes[i]) / span : 0);
        mode = route.ModeAt(i);
        segment = i;

        Vector2 heading = to - from;
        walker.position = new Vector3(at.x, SurfaceAt(at, heading) + WALKER_HEIGHT_M, at.y);
        if (heading.magnitude > 1) walker.rotation = Quaternion.LookRotation(new Vector3(heading.x, 0, heading.y)); // 가는 쪽을 바라봄 (머무는 중이면 그대로)
        // 배·버스·택시 안이나 땅속(지하철)에서는 숨김. 내비(사람 눈높이)도 숨김: 카메라가 마스코트 몸 안에 있어서 모자·깃발이 화면을 가림
        walker.gameObject.SetActive(mode == RouteData.WALK && !isNavigation);
        PlaceBoat(i, at, heading);
        PlaceVehicle(bus, RouteData.BUS, at, heading);
        PlaceVehicle(taxi, RouteData.RIDE, at, heading);
    }

    // 배: 배 구간이면 지금 자리로, 아니면 타기 전엔 타는 곳에, 내린 뒤엔 내린 곳에 세워 둠
    private void PlaceBoat(int i, Vector2 at, Vector2 heading)
    {
        if (boat == null) return;
        if (mode != RouteData.BOAT)
        {
            bool isBefore = i < firstBoatPoint;
            int onWater = isBefore ? firstBoatPoint + 1 : lastBoatPoint - 1; // 강가 점 바로 옆의 물 위 점
            int beside = isBefore ? onWater + 1 : onWater - 1;               // 그 옆 점 (배가 향할 쪽을 정하려고)
            at = route.PointAt(onWater);
            heading = isBefore ? route.PointAt(beside) - at : at - route.PointAt(beside);
        }
        boat.position = new Vector3(at.x, waterLevel, at.y);
        if (heading.magnitude > 1) boat.rotation = Quaternion.Euler(0, StreetProps.YawOf(heading), 0); // 배 모양은 뱃머리가 +x라 이 각도로 돌림
    }

    // 버스·택시: 그 구간을 지날 때만 지금 자리에 보이고, 아니면 숨김
    private void PlaceVehicle(Transform vehicle, int vehicleMode, Vector2 at, Vector2 heading)
    {
        if (vehicle == null) return;
        bool isRiding = mode == vehicleMode;
        vehicle.gameObject.SetActive(isRiding);
        if (!isRiding) return;
        vehicle.position = new Vector3(at.x, SurfaceAt(at, heading) + 0.3f, at.y);
        if (heading.magnitude > 1) vehicle.rotation = Quaternion.Euler(0, StreetProps.YawOf(heading), 0); // 모양은 앞이 +x
    }

    // 땅 높이. 그 자리를 같은 방향으로 건너는 다리가 있으면 다리 위 높이
    private float SurfaceAt(Vector2 at, Vector2 heading)
    {
        if (bridges != null && bridges.TryDeckHeight(at, heading, out float deck)) return deck;
        return ground.HeightAt(at.x, at.y);
    }

    // 뒤쪽 위에서 부드럽게 따라가며 내려다봄 (탈것 구간은 훨씬 높이 올라가 동네를 건너뜀)
    private void FollowFromBehind()
    {
        Transform eye = Camera.main.transform;
        Transform target = mode == RouteData.BOAT ? boat : walker;
        Vector3 forward = mode == RouteData.BOAT ? boat.right : walker.forward; // 배는 뱃머리가 +x(right) 쪽
        forward = SmoothForward(forward, TOP_TURN_DEGREES);
        Vector3 wanted = FollowPosition(target.position, forward);
        // SmoothDamp: 자동차처럼 천천히 출발해서 천천히 멈춤 (Lerp는 매 프레임 남은 거리의 일정 비율이라 출발이 확 튐 → 멀미)
        eye.position = Vector3.SmoothDamp(eye.position, wanted, ref cameraVelocity, CAMERA_SMOOTH_TIME);
        eye.rotation = Quaternion.Slerp(eye.rotation, Quaternion.LookRotation(target.position + Vector3.up * 2 - eye.position), Time.deltaTime * LOOK_SMOOTH);
    }

    // 위에서 보기 카메라가 있을 자리: 사람(at)의 뒤쪽 위
    //   그 사이에 높은 건물이 있어 사람이 가리면, 안 가릴 때까지 카메라를 사람 머리 위쪽으로 당김 (뒤로는 덜, 위로는 더)
    //   예) 걷기 기본은 뒤 90m · 위 75m → 한 번 당기면 뒤 54m · 위 94m → 두 번이면 뒤 32m · 위 117m
    //   왜: 명동처럼 높은 건물 사이에서는 화면 절반이 건물 벽으로 가려 사람이 안 보였음
    private Vector3 FollowPosition(Vector3 at, Vector3 forward)
    {
        Vector2 backUp = FOLLOW_BACK_UP[mode] * zoom;
        Vector3 wanted = at - forward * backUp.x + Vector3.up * backUp.y;
        for (int tryCount = 0; tryCount < SIGHT_TRIES && IsSightBlocked(wanted, at); tryCount++)
        {
            backUp = new Vector2(backUp.x * SIGHT_BACK_RATE, backUp.y * SIGHT_UP_RATE);
            wanted = at - forward * backUp.x + Vector3.up * backUp.y;
        }
        float tallest = Mathf.Min(buildings.TallestNear(wanted.x, wanted.z), ROOF_CLEAR_MAX_M);
        float roof = ground.HeightAt(wanted.x, wanted.z) + tallest;
        wanted.y = Mathf.Max(wanted.y, roof + ROOF_CLEAR_M); // 카메라가 언덕·높은 건물 속으로 들어가지 않게
        return wanted;
    }

    // 카메라(eye)에서 사람(at)까지 그은 선이 건물 지붕보다 낮게 지나는 곳이 있는지
    //   선을 SIGHT_CHECKS 조각으로 나눠 조각마다 "그 자리 선의 높이 < 그 칸에서 가장 높은 지붕"인지 봄
    private bool IsSightBlocked(Vector3 eye, Vector3 at)
    {
        for (int k = 1; k < SIGHT_CHECKS; k++)
        {
            Vector3 point = Vector3.Lerp(at, eye, (float)k / SIGHT_CHECKS);
            if (Vector2.Distance(new Vector2(point.x, point.z), new Vector2(at.x, at.z)) < SIGHT_SKIP_M) continue;
            float roof = ground.HeightAt(point.x, point.z) + buildings.TallestAt(point.x, point.z);
            if (point.y < roof) return true;
        }
        return false;
    }

    // (캡처 도구용) 지금 시각의 카메라 자리로 바로 옮김. 평소에는 부드럽게 따라붙느라 여러 장면이 걸려서 한 장만 찍을 수 없음
    //   isStreet: true = 내비(사람 눈높이), false = 위에서 보기
    public void SnapCamera(bool isStreet)
    {
        Transform eye = Camera.main.transform;
        Transform target = mode == RouteData.BOAT ? boat : walker;
        Vector3 heading = mode == RouteData.BOAT ? boat.right : walker.forward;
        cameraYaw = Mathf.Atan2(heading.x, heading.z) * Mathf.Rad2Deg;
        Vector3 forward = Quaternion.Euler(0, cameraYaw, 0) * Vector3.forward;
        if (isStreet)
        {
            eye.position = NavigationPosition(forward);
            eye.rotation = Quaternion.LookRotation(forward);
            return;
        }
        eye.position = FollowPosition(target.position, forward);
        eye.LookAt(target.position + Vector3.up * 2);
    }

    // 내비게이션: 걷는 중이면 사람 눈높이, 배 위면 위층 갑판 눈높이에서 가는 쪽을 봄
    private void LookLikeNavigation()
    {
        Transform eye = Camera.main.transform;
        bool isOnBoat = mode == RouteData.BOAT;
        Vector3 forward = SmoothForward(isOnBoat ? boat.right : walker.forward, NAV_TURN_DEGREES);
        Vector3 wanted = NavigationPosition(forward);
        eye.position = Vector3.Lerp(eye.position, wanted, Time.deltaTime * FOLLOW_SMOOTH * 2);
        eye.rotation = Quaternion.Slerp(eye.rotation, Quaternion.LookRotation(forward), Time.deltaTime * TURN_SMOOTH);
    }

    // 내비 카메라가 있을 자리: 걷는 중이면 사람 눈높이, 배 위면 앞쪽 창가
    private Vector3 NavigationPosition(Vector3 forward)
    {
        if (mode == RouteData.BOAT) return boat.position + Vector3.up * BoatMaker.DECK_EYE_M + forward * (BoatMaker.LENGTH_M * 0.35f); // 앞쪽 창가 (뒤에 앉으면 배 앞부분에 가려서)
        float eyeHeight = mode == RouteData.BUS ? BUS_EYE_M : EYE_M; // 버스는 조금 높게
        return new Vector3(walker.position.x, walker.position.y - WALKER_HEIGHT_M + eyeHeight, walker.position.z);
    }

    // 카메라 방향을 가는 쪽(heading)으로 1초에 maxTurn도까지만 돌림 → 머문 곳에서 GPS가 흔들려도 화면이 빙빙 돌지 않음
    private Vector3 SmoothForward(Vector3 heading, float maxTurn)
    {
        heading.y = 0;
        if (heading.sqrMagnitude > 0.01f)
        {
            float wanted = Mathf.Atan2(heading.x, heading.z) * Mathf.Rad2Deg; // 가는 쪽 각도 (북 = 0도)
            cameraYaw = Mathf.MoveTowardsAngle(cameraYaw, wanted, maxTurn * Time.deltaTime); // 359도→1도처럼 한 바퀴 넘어도 짧은 쪽으로
        }
        return Quaternion.Euler(0, cameraYaw, 0) * Vector3.forward;
    }
}
